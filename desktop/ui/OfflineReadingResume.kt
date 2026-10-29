package dev.nooir.desktop.ui

/**
 * Offline-only chapter selection using existing local-book timestamps.
 * Reader page position remains in its own per-file resume preference.
 * Missing CBZs are skipped, never fetched from a source.
 */
internal data class OfflineResumeTarget(
    val series: OfflineSeries,
    val chapter: OfflineSeriesChapter,
    val lastOpenedAt: Long,
) {
    val previouslyRead: Boolean get() = lastOpenedAt > 0L
}

internal object OfflineReadingResume {
    /** Last read available chapter or, for a new series, first available. */
    fun forSeries(series: OfflineSeries, books: List<LocalLibraryBook>): OfflineResumeTarget? {
        val available = series.chapters.filter { it.available }
        if (available.isEmpty()) return null
        val history = books.asSequence().filter { it.available && it.lastOpenedAt > 0L }
            .associate { it.file.canonicalPath to it.lastOpenedAt }
        val candidates = available.map { chapter ->
            OfflineResumeTarget(series, chapter, history[chapter.file.canonicalPath] ?: 0L)
        }
        return candidates.withIndex().filter { it.value.previouslyRead }
            .maxWithOrNull(compareBy<IndexedValue<OfflineResumeTarget>> {
                it.value.lastOpenedAt
            }.thenBy { -it.index })?.value ?: candidates.first()
    }

    /** Do not unexpectedly open unread series when continuing the last book. */
    fun mostRecent(series: List<OfflineSeries>, books: List<LocalLibraryBook>): OfflineResumeTarget? =
        series.mapNotNull { forSeries(it, books) }.filter { it.previouslyRead }
            .maxWithOrNull(compareBy<OfflineResumeTarget> { it.lastOpenedAt }
                .thenBy { -it.series.mangaId })
}
