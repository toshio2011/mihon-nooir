package dev.nooir.desktop.ui

import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Base64
import java.util.UUID

/**
 * Windows-only durable requests to Suwayomi's own chapter download queue.
 *
 * Adding work NEVER sends a GraphQL mutation. A user must explicitly press
 * "Submit downloads". Once Suwayomi accepts a request, we only monitor
 * isDownloaded; no false byte percentage or unsupported server cancellation.
 * Interrupted jobs cannot be silently re-submitted after a crash.
 */
internal enum class RemoteChapterState {
    PENDING, SUBMITTING, WAITING, DOWNLOADED, FAILED, INTERRUPTED, CANCELED
}

internal data class RemoteChapterJob(
    val id: String,
    val port: Int,
    val mangaId: Int,
    val chapterId: Int,
    val state: RemoteChapterState,
    val attempts: Int = 0,
    val error: String = "",
) {
    val endpoint get() = "http://127.0.0.1:$port"
}

internal class RemoteChapterQueue(
    private val folder: Path = Path.of(System.getProperty("user.home"), ".mihonnooir"),
) {
    companion object {
        private const val HEADER = "MIHONNOOIR_REMOTE_CHAPTER_QUEUE_V1"
        private const val MAX_BYTES = 512_000L
        const val MAX_JOBS = 500
        private val lock = Any()
        // This process can continue managing a job it owns. A future process
        // does not know the ownership and sees persisted in-flight as interrupted.
        private val owned = mutableSetOf<String>()
        private val enc = Base64.getUrlEncoder().withoutPadding()
        private val dec = Base64.getUrlDecoder()

        fun portOf(endpoint: String): Int {
            val uri = URI(endpoint)
            require(uri.scheme == "http" && uri.host?.lowercase() in
                setOf("localhost", "127.0.0.1", "::1") &&
                uri.port in 1..65535 && uri.rawUserInfo == null &&
                uri.rawQuery == null && uri.rawFragment == null &&
                (uri.rawPath.isNullOrEmpty() || uri.rawPath == "/")) {
                "Remote downloads require a localhost-only HTTP Suwayomi URL and port"
            }
            return uri.port
        }
    }

    private val index = folder.resolve("remote-chapter-queue-v1.tsv")

    private fun load(): MutableList<RemoteChapterJob> {
        if (!Files.exists(index)) return mutableListOf()
        require(Files.isRegularFile(index) && Files.size(index) <= MAX_BYTES) {
            "Remote queue index is unsafe; the original was preserved"
        }
        val lines = Files.readAllLines(index, StandardCharsets.UTF_8)
        require(lines.firstOrNull() == HEADER && lines.size <= MAX_JOBS + 1) {
            "Unknown remote queue format; the original was preserved"
        }
        val jobs = lines.drop(1).map { line ->
            val p = line.split('\t')
            require(p.size == 7) { "Damaged remote job record; original preserved" }
            val raw = RemoteChapterState.valueOf(p[4])
            val id = p[0]
            val error = String(dec.decode(p[6]), StandardCharsets.UTF_8)
            val job = RemoteChapterJob(id, p[1].toInt(), p[2].toInt(),
                p[3].toInt(), if (raw in setOf(RemoteChapterState.SUBMITTING,
                    RemoteChapterState.WAITING) && id !in owned)
                    RemoteChapterState.INTERRUPTED else raw, p[5].toInt(), error)
            require(job.id.matches(Regex("[a-f0-9-]{36}")) &&
                job.port in 1..65535 && job.mangaId > 0 && job.chapterId > 0 &&
                job.attempts in 0..1000 && job.error.length <= 250) {
                "Invalid remote queue record; original preserved"
            }
            job
        }
        require(jobs.map { it.id }.distinct().size == jobs.size) {
            "Duplicate remote job IDs; original preserved"
        }
        return jobs.toMutableList()
    }

    private fun save(jobs: List<RemoteChapterJob>) {
        require(jobs.size <= MAX_JOBS)
        Files.createDirectories(folder)
        val serialized = buildString {
            append(HEADER).append('\n')
            for (job in jobs) {
                append(job.id).append('\t').append(job.port).append('\t')
                    .append(job.mangaId).append('\t').append(job.chapterId)
                    .append('\t').append(job.state.name).append('\t')
                    .append(job.attempts).append('\t')
                    .append(enc.encodeToString(
                        job.error.take(250).toByteArray(StandardCharsets.UTF_8)))
                    .append('\n')
            }
        }
        require(serialized.toByteArray(StandardCharsets.UTF_8).size <= MAX_BYTES)
        val temporary = folder.resolve(".remote-chapters-${UUID.randomUUID()}.tmp")
        try {
            Files.writeString(temporary, serialized, StandardCharsets.UTF_8)
            try {
                Files.move(temporary, index, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, index, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    fun list(): List<RemoteChapterJob> = synchronized(lock) { load().toList() }

    /** User-selection only; no source contact. */
    fun add(endpoint: String, mangaId: Int, chapterIds: List<Int>): List<RemoteChapterJob> =
        synchronized(lock) {
            val port = portOf(endpoint)
            require(mangaId > 0 && chapterIds.size in 1..20 &&
                chapterIds.all { it > 0 } && chapterIds.distinct().size == chapterIds.size) {
                "Choose 1 to 20 distinct chapters"
            }
            val jobs = load()
            val selected = mutableListOf<RemoteChapterJob>()
            for (id in chapterIds) {
                val existing = jobs.firstOrNull {
                    it.port == port && it.mangaId == mangaId && it.chapterId == id &&
                        it.state != RemoteChapterState.CANCELED
                }
                if (existing != null) selected += existing else {
                    require(jobs.size < MAX_JOBS) { "Remote queue has reached 500 entries" }
                    val new = RemoteChapterJob(UUID.randomUUID().toString(),
                        port, mangaId, id, RemoteChapterState.PENDING)
                    jobs += new
                    selected += new
                }
            }
            save(jobs)
            selected
        }

    /** Claim a PENDING request only on an explicit user action. */
    fun claim(endpoint: String): RemoteChapterJob? = synchronized(lock) {
        val port = portOf(endpoint)
        val jobs = load()
        val pos = jobs.indexOfFirst { it.port == port &&
            it.state == RemoteChapterState.PENDING }
        if (pos < 0) null else {
            val claimed = jobs[pos].copy(state = RemoteChapterState.SUBMITTING,
                attempts = (jobs[pos].attempts + 1).coerceAtMost(1000), error = "")
            jobs[pos] = claimed
            save(jobs)
            owned.add(claimed.id)
            claimed
        }
    }

    /** Before enqueue is called, a stop cannot have reached the manga source. */
    fun stoppedBeforeSubmission(id: String) = change(id,
        setOf(RemoteChapterState.SUBMITTING), RemoteChapterState.PENDING)
    fun submitted(id: String) = change(id,
        setOf(RemoteChapterState.SUBMITTING), RemoteChapterState.WAITING)
    fun completed(id: String) = change(id,
        setOf(RemoteChapterState.SUBMITTING, RemoteChapterState.WAITING,
            RemoteChapterState.INTERRUPTED), RemoteChapterState.DOWNLOADED)
    fun failed(id: String, message: String) = change(id,
        setOf(RemoteChapterState.SUBMITTING, RemoteChapterState.WAITING),
        RemoteChapterState.FAILED, message.take(250))
    fun interrupted(id: String) = change(id,
        setOf(RemoteChapterState.SUBMITTING, RemoteChapterState.WAITING),
        RemoteChapterState.INTERRUPTED,
        "Monitoring stopped. Suwayomi may still be downloading.")
    /** Resume *monitoring* only: never automatically enqueue a second download. */
    fun resumeMonitoring(id: String) = change(id,
        setOf(RemoteChapterState.INTERRUPTED, RemoteChapterState.FAILED),
        RemoteChapterState.WAITING)
    fun cancelPending(id: String) = change(id,
        setOf(RemoteChapterState.PENDING), RemoteChapterState.CANCELED)

    private fun change(id: String, allowed: Set<RemoteChapterState>,
        state: RemoteChapterState, message: String = "") = synchronized(lock) {
        val jobs = load()
        val pos = jobs.indexOfFirst { it.id == id }
        require(pos >= 0 && jobs[pos].state in allowed) {
            "Cannot update this remote job: its state may have changed"
        }
        jobs[pos] = jobs[pos].copy(state = state, error = message)
        save(jobs)
        if (state !in setOf(RemoteChapterState.SUBMITTING,
                RemoteChapterState.WAITING)) owned.remove(id)
        else owned.add(id)
    }
}
