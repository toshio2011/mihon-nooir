import com.sun.net.httpserver.HttpServer
import dev.nooir.desktop.suwayomi.SuwayomiClient
import dev.nooir.desktop.ui.RemoteChapterQueue
import dev.nooir.desktop.ui.RemoteChapterRunner
import dev.nooir.desktop.ui.RemoteChapterState
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Contract fixture: no copyrighted chapters, internet or extensions. */
fun main() {
    val root = Files.createTempDirectory("mihonnooir-remote-queue-")
    val served101 = AtomicBoolean(false)
    val served102 = AtomicBoolean(false)
    val served103 = AtomicBoolean(false)
    val served104 = AtomicBoolean(false)
    val submissions = AtomicInteger(0)
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/api/graphql") { ctx ->
        val query = ctx.requestBody.bufferedReader().use { it.readText() }
        val result = when {
            "enqueueChapterDownloads" in query -> {
                check("ids: [101]" in query || "ids: [103]" in query) {
                    "Only explicitly selected chapters should be submitted"
                }
                submissions.incrementAndGet()
                """{"data":{"enqueueChapterDownloads":{"clientMutationId":null}}}"""
            }
            "manga(id: 7)" in query -> {
                val chapters = listOf(
                    101 to served101.get(),
                    102 to served102.get(),
                    103 to served103.get(),
                    104 to served104.get(),
                ).mapIndexed { i, (id, downloaded) ->
                    """{"id":$id,"name":"Chapter $id","chapterNumber":${i + 1}.0,
                        "sourceOrder":${i + 1},"isDownloaded":$downloaded}"""
                }.joinToString(",")
                """{"data":{"manga":{"id":7,"title":"Test","author":"",
                    "chapters":{"nodes":[$chapters]}}}}"""
            }
            else -> """{"errors":[{"message":"Unknown test request"}]}"""
        }
        val data = result.toByteArray(StandardCharsets.UTF_8)
        ctx.responseHeaders.add("Content-Type", "application/json")
        ctx.sendResponseHeaders(200, data.size.toLong())
        ctx.responseBody.use { it.write(data) }
    }
    server.start()
    try {
        val endpoint = "http://127.0.0.1:${server.address.port}"
        val queue = RemoteChapterQueue(root)
        val client = SuwayomiClient(endpoint)
        check(queue.list().isEmpty())
        for (unsafe in listOf("http://example.org:4567",
            "https://127.0.0.1:4567", "http://127.0.0.1:4567/bad",
            "http://user:pass@127.0.0.1:4567", "http://127.0.0.1:4567?token=x")) {
            check(runCatching { RemoteChapterQueue.portOf(unsafe) }.isFailure)
        }
        val jobs = queue.add(endpoint, 7, listOf(101, 102))
        check(jobs.map { it.chapterId } == listOf(101, 102))
        check(queue.add(endpoint, 7, listOf(101)).single().id == jobs[0].id)
        check(submissions.get() == 0) { "Adding requests must never contact Suwayomi" }
        check(RemoteChapterQueue(root).list().size == 2)

        val claimed = checkNotNull(queue.claim(endpoint))
        check(claimed.chapterId == 101 && claimed.state == RemoteChapterState.SUBMITTING)
        val runner = RemoteChapterRunner(queue, client, pollIntervalMs = 0, maxPolls = 5) {
            served101.set(true)
        }
        check(runner.runClaimed(claimed, { false }))
        check(queue.list()[0].state == RemoteChapterState.DOWNLOADED)
        check(submissions.get() == 1) { "One selected chapter must be submitted exactly once" }
        check(queue.add(endpoint, 7, listOf(101)).single().id == claimed.id)

        val second = checkNotNull(queue.claim(endpoint))
        check(second.chapterId == 102)
        check(!runner.runClaimed(second, { true }))
        check(queue.list()[1].state == RemoteChapterState.PENDING) {
            "Stopping before the mutation must leave a safely retryable pending job"
        }
        check(submissions.get() == 1) { "Stop before submission cannot enqueue another chapter" }
        // A later, separately approved claim checks if an external download
        // already finished before deciding to send a new mutation.
        served102.set(true)
        val claimedAgain = checkNotNull(queue.claim(endpoint))
        check(claimedAgain.id == second.id)
        check(runner.runClaimed(claimedAgain, { false }))
        check(queue.list()[1].state == RemoteChapterState.DOWNLOADED)
        check(submissions.get() == 1) {
            "Reapproved claim must not enqueue a chapter already downloaded"
        }

        val third = queue.add(endpoint, 7, listOf(103)).single()
        val claimedThird = checkNotNull(queue.claim(endpoint))
        check(claimedThird.id == third.id)
        val shortRunner = RemoteChapterRunner(queue, client, 0, 1) {}
        check(!shortRunner.runClaimed(claimedThird, { false }))
        check(queue.list().find { it.id == third.id }?.state == RemoteChapterState.FAILED)
        check(queue.add(endpoint, 7, listOf(103)).single().id == third.id)
        queue.resumeMonitoring(third.id)
        served103.set(true)
        check(shortRunner.resumeMonitoring(checkNotNull(
            queue.list().find { it.id == third.id }), { false }))
        check(submissions.get() == 2) {
            "Checking status must never re-submit a timed out download"
        }

        val fourth = queue.add(endpoint, 7, listOf(104)).single()
        val index = root.resolve("remote-chapter-queue-v1.tsv")
        val original = Files.readString(index)
        val oldText = "${fourth.id}\t${server.address.port}\t7\t104\tPENDING\t0\t"
        val newText = "${fourth.id}\t${server.address.port}\t7\t104\tWAITING\t0\t"
        check(original.contains(oldText))
        Files.writeString(index, original.replace(oldText, newText))
        check(RemoteChapterQueue(root).list().last().state == RemoteChapterState.INTERRUPTED)
        check(queue.claim(endpoint) == null) { "Crash must not restart remote source downloads" }
        queue.resumeMonitoring(fourth.id)
        served104.set(true)
        check(runner.resumeMonitoring(checkNotNull(
            queue.list().find { it.id == fourth.id }), { false }))
        check(submissions.get() == 2)

        val canceled = queue.add(endpoint, 8, listOf(555)).single()
        queue.cancelPending(canceled.id)
        check(queue.list().last().state == RemoteChapterState.CANCELED)
        check(runCatching { queue.resumeMonitoring(canceled.id) }.isFailure)
        check(runCatching { queue.add(endpoint, 7, listOf(101, 101)) }.isFailure)
        check(runCatching { queue.add(endpoint, 7, (1..21).toList()) }.isFailure)

        Files.writeString(index, "UNKNOWN\n")
        check(runCatching { queue.list() }.isFailure)
        check(Files.readString(index) == "UNKNOWN\n") { "Damaged index must not be overwritten" }
        println("Windows remote chapter queue passed: opt-in submit, no duplicates, status-only monitoring and interrupted recovery")
    } finally {
        server.stop(0)
        root.toFile().deleteRecursively()
    }
}
