package dev.nooir.desktop.ui

/**
 * Pure, platform-neutral reader page grouping.
 * In double-page mode the first page remains alone by default: it is usually
 * a cover or chapter opener. Pairing never reorders the underlying page list.
 */
internal object DesktopReadingLayout {
    fun visibleIndices(pageCount: Int, anchor: Int, twoPage: Boolean, firstAlone: Boolean = true): List<Int> {
        require(pageCount in 1..5_000 && anchor in 0 until pageCount) { "Invalid page position" }
        if (!twoPage) return listOf(anchor)
        if (firstAlone && anchor == 0) return listOf(0)
        val firstPair = if (firstAlone) 1 else 0
        val start = firstPair + ((anchor - firstPair) / 2) * 2
        return (start until minOf(start + 2, pageCount)).toList()
    }

    fun move(pageCount: Int, anchor: Int, direction: Int, twoPage: Boolean, firstAlone: Boolean = true): Int {
        require(direction == -1 || direction == 1) { "Direction must be previous or next" }
        val now = visibleIndices(pageCount, anchor, twoPage, firstAlone)
        // At the last spread, keep its canonical first page. Returning the
        // final physical page would redisplay the SAME spread and make the
        // Next button repeat pages indefinitely (e.g. a two-page book).
        if (direction == 1) {
            return if (now.last() == pageCount - 1) now.first() else now.last() + 1
        }
        if (now.first() == 0) return 0
        // The previous group starts at the previous spread, except for
        // a standalone first page at the beginning of a book.
        val previous = now.first() - 1
        return visibleIndices(pageCount, previous, twoPage, firstAlone).first()
    }

    /** RTL changes the physical position on screen, never manga reading order. */
    fun visualIndices(readingOrderIndices: List<Int>, rightToLeft: Boolean): List<Int> =
        if (rightToLeft) readingOrderIndices.reversed() else readingOrderIndices
}
