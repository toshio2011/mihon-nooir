import dev.nooir.desktop.ui.RemoteChapterQueue
import dev.nooir.desktop.ui.RemoteChapterState
import java.nio.file.Files

/** Download All is bounded, locally persisted, atomic, deduplicated and opt-in. */
fun main() {
    val root = Files.createTempDirectory("mihonnooir-remote-all-")
    try {
        val endpoint = "http://localhost:4567"
        val queue = RemoteChapterQueue(root)
        val chapters = (1..485).toList()
        check(runCatching { queue.add(endpoint, 7, chapters) }.isFailure) {
            "Legacy selected batch still has a 20-chapter limit"
        }
        check(queue.list().isEmpty())
        val saved = queue.addAllMissing(endpoint, 7, chapters)
        check(saved.size == 485 && saved.all { it.state == RemoteChapterState.PENDING })
        check(queue.list().size == 485)
        check(RemoteChapterQueue(root).list().size == 485) {
            "Download All requests must survive restart"
        }
        check(queue.addAllMissing(endpoint, 7, chapters).map { it.id } ==
            saved.map { it.id }) { "Repeated Download All must not create duplicates" }

        val mixed = queue.addAllMissing(endpoint, 7, listOf(485, 486, 487))
        check(mixed[0].id == saved.last().id && mixed.size == 3)
        check(queue.list().size == 487)
        val before = Files.readAllBytes(root.resolve("remote-chapter-queue-v1.tsv"))
        check(runCatching {
            queue.addAllMissing(endpoint, 7, (488..501).toList())
        }.isFailure) { "Over capacity must fail atomically" }
        check(Files.readAllBytes(root.resolve("remote-chapter-queue-v1.tsv"))
            .contentEquals(before)) { "Oversized batch must preserve the entire index" }

        check(runCatching {
            queue.addAllMissing(endpoint, 7, (1..501).toList())
        }.isFailure) { "Single batch is limited to 500 chapters" }
        check(runCatching {
            queue.addAllMissing(endpoint, 7, listOf(1, 1))
        }.isFailure)
        check(runCatching {
            queue.addAllMissing("http://example.net:4567", 7, listOf(499))
        }.isFailure)
        check(queue.list().all { it.state == RemoteChapterState.PENDING }) {
            "Queue All must not submit any downloads to Suwayomi"
        }
        // Queue capacity can be reclaimed intentionally, without remote
        // requests or deletion of any manga files.
        val toForget = saved.take(2).map { it.id }
        check(runCatching { queue.forgetSafe(listOf("not-a-job")) }.isFailure)
        check(runCatching { queue.forgetSafe(listOf(toForget[0], toForget[0])) }.isFailure)
        val originalCount = queue.list().size
        queue.forgetSafe(toForget)
        check(queue.list().size == originalCount - 2)
        check(queue.list().none { it.id in toForget })
        check(RemoteChapterQueue(root).list().size == originalCount - 2)
        val reclaimed = queue.addAllMissing(endpoint, 7, listOf(488, 489))
        check(reclaimed.size == 2)
        check(queue.list().size == originalCount)
        val locked = checkNotNull(queue.claim(endpoint))
        check(runCatching { queue.forgetSafe(listOf(locked.id)) }.isFailure) {
            "Potentially submitted jobs cannot be forgotten"
        }
        queue.interrupted(locked.id)
        check(runCatching { queue.forgetSafe(listOf(locked.id)) }.isFailure) {
            "Uncertain remote jobs cannot be forgotten"
        }
        println("Windows bounded Download All passed: 20-selected limit, 500 cap, restart, dedup, atomic refusal")
    } finally {
        root.toFile().deleteRecursively()
    }
}
