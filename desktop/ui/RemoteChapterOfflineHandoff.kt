package dev.nooir.desktop.ui

/**
 * Bridges completed Suwayomi download requests into offline CBZ copy jobs.
 *
 * Explicitly user-invoked. It never connects to Suwayomi, starts a source
 * download, fetches a CBZ or runs an offline copy. The Offline jobs screen
 * retains responsibility for a separate Run confirmation.
 */
internal class RemoteChapterOfflineHandoff(
    private val remoteQueue: RemoteChapterQueue = RemoteChapterQueue(),
    private val offlineQueue: OfflineSaveQueue = OfflineSaveQueue(),
) {
    fun queueDownloaded(endpoint: String, selectedIds: List<String>): List<OfflineSaveJob> {
        val port = RemoteChapterQueue.portOf(endpoint)
        require(selectedIds.size in 1..20 && selectedIds.distinct().size == selectedIds.size) {
            "Select 1 to 20 different completed download requests"
        }
        val jobs = remoteQueue.list().associateBy { it.id }
        val selected = selectedIds.map { id ->
            jobs[id] ?: error("Selected download request no longer exists")
        }
        require(selected.all {
            it.port == port && it.state == RemoteChapterState.DOWNLOADED
        }) {
            "Only completed downloads from this Suwayomi server can be queued offline"
        }
        // OfflineSaveQueue deduplicates existing pending/completed copies by
        // endpoint, manga and chapter. Grouping preserves selected order.
        return selected.groupBy { it.mangaId }.flatMap { (mangaId, chapters) ->
            offlineQueue.enqueue(endpoint, mangaId, chapters.map { it.chapterId })
        }
    }
}
