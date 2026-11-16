import dev.nooir.desktop.ui.OfflineJobState
import dev.nooir.desktop.ui.OfflineSaveQueue
import dev.nooir.desktop.ui.RemoteChapterOfflineHandoff
import dev.nooir.desktop.ui.RemoteChapterQueue
import dev.nooir.desktop.ui.RemoteChapterState
import java.nio.file.Files

/** Windows-only, fully offline queue handoff: no source or manga requests. */
fun main() {
    val root = Files.createTempDirectory("mihonnooir-remote-handoff-")
    try {
        val endpoint = "http://localhost:4567/"
        val remote = RemoteChapterQueue(root)
        val offline = OfflineSaveQueue(root)
        val handoff = RemoteChapterOfflineHandoff(remote, offline)
        val first = remote.add(endpoint, 11, listOf(101, 102))
        val other = remote.add(endpoint, 12, listOf(201)).single()
        val pending = remote.add(endpoint, 11, listOf(103)).single()
        for (job in first + other) {
            val claimed = checkNotNull(remote.claim(endpoint))
            check(claimed.id == job.id)
            remote.completed(job.id)
        }
        check(remote.list().count { it.state == RemoteChapterState.DOWNLOADED } == 3)
        check(offline.list().isEmpty())

        // Selection, not all known downloads, determines which CBZ copy jobs
        // are prepared. Mixed series remain grouped and in selected order.
        val selected = listOf(other.id, first[1].id, first[0].id)
        val saved = handoff.queueDownloaded(endpoint, selected)
        check(saved.map { it.mangaId to it.chapterId } ==
            listOf(12 to 201, 11 to 102, 11 to 101))
        check(saved.all { it.state == OfflineJobState.PENDING })
        check(offline.list().size == 3)
        check(OfflineSaveQueue(root).list().size == 3) {
            "Offline CBZ jobs must persist across restarts"
        }
        check(handoff.queueDownloaded(endpoint, selected).map { it.id } ==
            saved.map { it.id }) { "A second click must not duplicate saved jobs" }
        check(offline.list().size == 3)

        for (invalid in listOf(
            emptyList(), listOf(first[0].id, first[0].id),
            listOf(pending.id), listOf("missing"),
            List(21) { "missing-" + it },
        )) {
            check(runCatching { handoff.queueDownloaded(endpoint, invalid) }.isFailure)
        }
        check(runCatching {
            handoff.queueDownloaded("http://127.0.0.1:4568", selected)
        }.isFailure) { "Jobs from a different server must not be copied" }
        check(offline.list().size == 3) {
            "Invalid selection must not enqueue any offline work"
        }
        check(offline.list().all { it.state == OfflineJobState.PENDING }) {
            "Queue handoff must not start CBZ copying"
        }
        println("Remote-to-offline CBZ handoff passed: opt-in, bounds, grouped metadata, dedup, restart")
    } finally {
        root.toFile().deleteRecursively()
    }
}
