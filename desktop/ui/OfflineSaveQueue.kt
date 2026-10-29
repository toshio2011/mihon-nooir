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
 * Durable Windows-only queue for *explicit* offline CBZ copies of chapters
 * ALREADY downloaded by Suwayomi. Enqueuing never makes a network request.
 * Jobs run only when the user presses Run and can be stopped between chapters.
 */
internal enum class OfflineJobState {
    PENDING, RUNNING, COMPLETED, FAILED, INTERRUPTED, CANCELED
}

internal data class OfflineSaveJob(
    val id: String,
    val port: Int,
    val mangaId: Int,
    val chapterId: Int,
    val state: OfflineJobState,
    val attempts: Int,
    val error: String = "",
) {
    val endpoint: String get() = "http://127.0.0.1:$port"
}

internal class OfflineSaveQueue(
    private val folder: Path = Path.of(System.getProperty("user.home"), ".mihonnooir"),
) {
    companion object {
        const val MAX_JOBS = 500
        private const val HEADER = "MIHONNOOIR_OFFLINE_SAVE_QUEUE_V1"
        private const val MAX_BYTES = 512_000L
        private val gate = Any()
        private val ownedRunningIds = mutableSetOf<String>()
        private val encoder = Base64.getUrlEncoder().withoutPadding()
        private val decoder = Base64.getUrlDecoder()
        private fun enc(text: String) =
            encoder.encodeToString(text.toByteArray(StandardCharsets.UTF_8))
        private fun dec(text: String) =
            String(decoder.decode(text), StandardCharsets.UTF_8)

        fun portOf(base: String): Int {
            val uri = URI.create(base)
            require(uri.scheme.equals("http", true) &&
                uri.host in setOf("127.0.0.1", "localhost", "::1") &&
                uri.port in 1..65535 && uri.userInfo == null &&
                uri.rawQuery == null && uri.rawFragment == null &&
                (uri.rawPath == null || uri.rawPath.isEmpty() || uri.rawPath == "/")) {
                "Offline jobs require a localhost-only Suwayomi URL and explicit port"
            }
            return uri.port
        }
    }

    private val path = folder.resolve("offline-save-queue-v1.tsv")

    private fun read(): MutableList<OfflineSaveJob> {
        if (!Files.exists(path)) return mutableListOf()
        require(Files.isRegularFile(path) && Files.size(path) <= MAX_BYTES) {
            "Offline job index is invalid or too large; original preserved"
        }
        val lines = Files.readAllLines(path, StandardCharsets.UTF_8)
        require(lines.firstOrNull() == HEADER && lines.size <= MAX_JOBS + 1) {
            "Unknown offline queue format; original preserved"
        }
        val jobs = mutableListOf<OfflineSaveJob>()
        for (line in lines.drop(1)) {
            val p = line.split('\t')
            require(p.size == 7) { "Corrupted offline job record; original preserved" }
            val state = OfflineJobState.valueOf(p[4])
            val job = OfflineSaveJob(
                id = p[0],
                port = p[1].toInt(),
                mangaId = p[2].toInt(),
                chapterId = p[3].toInt(),
                state = if (state == OfflineJobState.RUNNING &&
                    p[0] !in ownedRunningIds) OfflineJobState.INTERRUPTED else state,
                attempts = p[5].toInt(),
                error = dec(p[6]),
            )
            require(job.id.matches(Regex("[a-f0-9-]{36}")) &&
                job.port in 1..65535 && job.mangaId > 0 && job.chapterId > 0 &&
                job.attempts in 0..1000 && job.error.length <= 250) {
                "Invalid offline job; original queue preserved"
            }
            jobs += job
        }
        require(jobs.map { it.id }.distinct().size == jobs.size) {
            "Duplicate offline queue jobs; original preserved"
        }
        return jobs
    }

    private fun write(jobs: List<OfflineSaveJob>) {
        require(jobs.size <= MAX_JOBS)
        Files.createDirectories(folder)
        val data = buildString {
            append(HEADER).append('\n')
            for (job in jobs) {
                append(job.id).append('\t')
                    .append(job.port).append('\t')
                    .append(job.mangaId).append('\t')
                    .append(job.chapterId).append('\t')
                    .append(job.state.name).append('\t')
                    .append(job.attempts).append('\t')
                    .append(enc(job.error.take(250))).append('\n')
            }
        }
        require(data.toByteArray(StandardCharsets.UTF_8).size <= MAX_BYTES)
        val temp = folder.resolve(".offline-queue-${UUID.randomUUID()}.tmp")
        try {
            Files.writeString(temp, data, StandardCharsets.UTF_8)
            try {
                Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    fun list(): List<OfflineSaveJob> = synchronized(gate) { read().toList() }

    fun enqueue(endpoint: String, mangaId: Int, chapterIds: List<Int>): List<OfflineSaveJob> =
        synchronized(gate) {
            val port = portOf(endpoint)
            require(mangaId > 0 && chapterIds.size in 1..20 &&
                chapterIds.all { it > 0 } && chapterIds.distinct().size == chapterIds.size) {
                "Choose 1 to 20 distinct chapters for an offline queue batch"
            }
            val jobs = read()
            val submitted = mutableListOf<OfflineSaveJob>()
            for (chapterId in chapterIds) {
                val existing = jobs.find {
                    it.port == port && it.mangaId == mangaId &&
                        it.chapterId == chapterId && it.state in setOf(
                            OfflineJobState.PENDING, OfflineJobState.RUNNING,
                            OfflineJobState.INTERRUPTED, OfflineJobState.COMPLETED)
                }
                if (existing != null) {
                    submitted += existing
                } else {
                    require(jobs.size < MAX_JOBS) { "Offline job queue is full" }
                    val new = OfflineSaveJob(UUID.randomUUID().toString(),
                        port, mangaId, chapterId, OfflineJobState.PENDING, 0)
                    jobs += new
                    submitted += new
                }
            }
            write(jobs)
            submitted
        }

    /** Claims one pending job only after an explicit Run click. */
    fun claimNext(endpoint: String): OfflineSaveJob? = synchronized(gate) {
        val port = portOf(endpoint)
        val jobs = read()
        val i = jobs.indexOfFirst { it.port == port && it.state == OfflineJobState.PENDING }
        if (i < 0) null else {
            val claimed = jobs[i].copy(state = OfflineJobState.RUNNING,
                attempts = (jobs[i].attempts + 1).coerceAtMost(1000), error = "")
            jobs[i] = claimed
            write(jobs)
            ownedRunningIds.add(claimed.id)
            claimed
        }
    }

    fun complete(id: String) = transition(id, setOf(OfflineJobState.RUNNING),
        OfflineJobState.COMPLETED)
    fun fail(id: String, message: String) = transition(id, setOf(OfflineJobState.RUNNING),
        OfflineJobState.FAILED, message.take(250))
    fun retry(id: String) = transition(id,
        setOf(OfflineJobState.FAILED, OfflineJobState.INTERRUPTED), OfflineJobState.PENDING)
    fun cancel(id: String) = transition(id,
        setOf(OfflineJobState.PENDING, OfflineJobState.FAILED, OfflineJobState.INTERRUPTED),
        OfflineJobState.CANCELED)

    /**
     * Storage reads normalize persisted RUNNING to INTERRUPTED. That is safe:
     * a process crash never silently restarts the old network operation.
     * Only the owner holding the job id may complete/fail after its own work.
     */
    private fun transition(id: String, allowed: Set<OfflineJobState>,
        target: OfflineJobState, message: String = "") = synchronized(gate) {
        val jobs = read()
        val i = jobs.indexOfFirst { it.id == id }
        require(i >= 0 && jobs[i].state in allowed) {
            "Offline job is missing or cannot transition to $target"
        }
        jobs[i] = jobs[i].copy(state = target, error = message)
        write(jobs)
        if (target != OfflineJobState.RUNNING) ownedRunningIds.remove(id)
    }
}
