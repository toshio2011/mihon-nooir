import dev.nooir.desktop.ui.OfflineSaveJob
import dev.nooir.desktop.ui.OfflineJobState
import dev.nooir.desktop.ui.OfflineSaveQueue
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.UUID

fun main() {
    val folder = Files.createTempDirectory("mihonnooir-offline-queue-test-")
    try {
        val queue = OfflineSaveQueue(folder)
        val endpoint = "http://127.0.0.1:4567"
        check(queue.list().isEmpty())
        check(runCatching { OfflineSaveQueue.portOf("https://example.org:4567") }.isFailure)
        check(runCatching { OfflineSaveQueue.portOf("http://127.0.0.1:4567/path") }.isFailure)
        check(runCatching { OfflineSaveQueue.portOf("http://user:pass@127.0.0.1:4567") }.isFailure)
        check(runCatching { OfflineSaveQueue.portOf("http://127.0.0.1:4567/?token=abc") }.isFailure)
        check(OfflineSaveQueue.portOf("http://localhost:4567") == 4567)

        val jobs = queue.enqueue(endpoint, 7, listOf(11, 12))
        check(jobs.size == 2 && jobs.all { it.state == OfflineJobState.PENDING })
        check(queue.enqueue(endpoint, 7, listOf(11)).single().id == jobs[0].id) {
            "Re-adding the same pending chapter must not duplicate the network operation"
        }
        check(queue.list().size == 2)
        val fromDisk = OfflineSaveQueue(folder)
        check(fromDisk.list().size == 2) { "Jobs must persist across store instances" }
        check(fromDisk.claimNext("http://127.0.0.1:8001") == null) {
            "Never claim jobs for an unrelated localhost server"
        }
        val first = checkNotNull(queue.claimNext(endpoint))
        check(first.chapterId == 11 && first.state == OfflineJobState.RUNNING)
        check(first.attempts == 1 && queue.list().first().state == OfflineJobState.RUNNING)
        check(runCatching { queue.cancel(first.id) }.isFailure) {
            "Cannot cancel and delete a running job during its in-flight network request"
        }
        queue.complete(first.id)
        check(queue.list().first().state == OfflineJobState.COMPLETED)
        check(queue.claimNext(endpoint)?.chapterId == 12)
        queue.fail(jobs[1].id, "Test failure")
        check(queue.list()[1].state == OfflineJobState.FAILED)
        check(queue.list()[1].error == "Test failure")
        queue.retry(jobs[1].id)
        val retry = checkNotNull(queue.claimNext(endpoint))
        check(retry.attempts == 2)
        queue.complete(retry.id)
        check(queue.claimNext(endpoint) == null)
        check(queue.enqueue(endpoint, 7, listOf(11, 12)).map { it.id } ==
            listOf(jobs[0].id, jobs[1].id)) { "Completed chapters should not be imported twice" }

        val interrupt = queue.enqueue(endpoint, 8, listOf(31)).single()
        val saved = folder.resolve("offline-save-queue-v1.tsv")
        val raw = Files.readString(saved, StandardCharsets.UTF_8)
        // Simulate a previous application's lost process ownership by
        // creating a persisted RUNNING state for a job this JVM never claimed.
        check(raw.contains("\tPENDING\t0\t"))
        Files.writeString(saved, raw.replace(
            "${interrupt.id}\t4567\t8\t31\tPENDING\t0\t",
            "${interrupt.id}\t4567\t8\t31\tRUNNING\t0\t"),
            StandardCharsets.UTF_8)
        check(queue.list().last().state == OfflineJobState.INTERRUPTED)
        check(queue.claimNext(endpoint) == null) { "Interrupted jobs must never auto-resume" }
        queue.retry(interrupt.id)
        check(queue.claimNext(endpoint)?.chapterId == 31)
        queue.complete(interrupt.id)

        val canceled = queue.enqueue(endpoint, 9, listOf(40)).single()
        queue.cancel(canceled.id)
        check(queue.list().last().state == OfflineJobState.CANCELED)
        check(runCatching { queue.retry(canceled.id) }.isFailure)
        check(queue.claimNext(endpoint) == null)
        check(runCatching { queue.enqueue(endpoint, 9, listOf(1, 1)) }.isFailure)
        check(runCatching { queue.enqueue(endpoint, 9, emptyList()) }.isFailure)
        check(runCatching { queue.enqueue(endpoint, 9, (1..21).toList()) }.isFailure)

        // A full 500-slot queue is recoverable without deleting chapter CBZs
        // or erasing failed, interrupted or in-flight copy attempts.
        val large = Files.createTempDirectory("mihonnooir-offline-capacity-")
        try {
            val bounded = OfflineSaveQueue(large)
            val marker = large.resolve("keep-existing-chapter.cbz")
            Files.writeString(marker, "Unrelated saved comic must survive cleanup")
            for (start in 1..500 step 20) {
                bounded.enqueue(endpoint, 23, (start..start + 19).toList())
            }
            check(bounded.list().size == OfflineSaveQueue.MAX_JOBS)
            check(runCatching { bounded.enqueue(endpoint, 23, listOf(501)) }.isFailure)
            val completed = checkNotNull(bounded.claimNext(endpoint))
            bounded.complete(completed.id)
            val failed = checkNotNull(bounded.claimNext(endpoint))
            bounded.fail(failed.id, "Simulated local save failure")
            val running = checkNotNull(bounded.claimNext(endpoint))
            val canceledJob = bounded.list().single { it.chapterId == 500 }
            bounded.cancel(canceledJob.id)
            check(bounded.forgetFinished("http://localhost:8001") == 0) {
                "Cleanup must never touch a different Suwayomi port"
            }
            val deleted = bounded.forgetFinished(endpoint)
            check(deleted == 2) { "Only completed/canceled records may be cleared" }
            val kept = OfflineSaveQueue(large).list()
            check(kept.size == 498)
            check(kept.any { it.id == failed.id && it.state == OfflineJobState.FAILED })
            check(kept.any { it.id == running.id && it.state == OfflineJobState.RUNNING })
            check(kept.none { it.id == completed.id || it.id == canceledJob.id })
            check(bounded.forgetFinished(endpoint) == 0) { "Cleanup must be idempotent" }
            check(Files.readString(marker) == "Unrelated saved comic must survive cleanup")
            bounded.enqueue(endpoint, 23, listOf(501, 502))
            check(OfflineSaveQueue(large).list().size == 500) {
                "New jobs can use freed slots across a restart"
            }
            check(runCatching { bounded.enqueue(endpoint, 23, listOf(503)) }.isFailure)
            check(OfflineSaveQueue(large).list().size == 500)
            bounded.fail(running.id, "Stopped test operation")
        } finally {
            large.toFile().deleteRecursively()
        }

        // Invalid files fail closed rather than being reset or overwritten.
        Files.writeString(saved, "UNKNOWN_VERSION\n", StandardCharsets.UTF_8)
        check(runCatching { queue.list() }.isFailure)
        check(Files.readString(saved, StandardCharsets.UTF_8) == "UNKNOWN_VERSION\n")
        println("Windows offline job queue passed: consent-only enqueue, persistence, completion, failed retry, interrupted recovery and cancel")
    } finally {
        folder.toFile().deleteRecursively()
    }
}
