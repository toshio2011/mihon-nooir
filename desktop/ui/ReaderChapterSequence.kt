package dev.nooir.desktop.ui

import java.io.File

/** Pure chapter navigation for an already-downloaded, user-chosen series. */
internal data class ReaderChapter(val id: Int, val title: String, val file: File)

internal class ReaderChapterSequence(
    val seriesTitle: String,
    val chapters: List<ReaderChapter>,
    val position: Int,
) {
    init {
        require(seriesTitle.isNotBlank() && seriesTitle.length <= 200)
        require(chapters.size in 1..2_000 && position in chapters.indices)
        require(chapters.map { it.id }.distinct().size == chapters.size)
    }

    val current: ReaderChapter get() = chapters[position]

    /** Skip unavailable CBZs without silently downloading or removing entries. */
    fun adjacent(direction: Int): ReaderChapterSequence? {
        require(direction == -1 || direction == 1)
        var index = position + direction
        while (index in chapters.indices) {
            val file = chapters[index].file
            if (file.isFile && file.extension.equals("cbz", ignoreCase = true)) {
                return ReaderChapterSequence(seriesTitle, chapters, index)
            }
            index += direction
        }
        return null
    }
}
