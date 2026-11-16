package dev.nooir.desktop.ui

/**
 * A bounded rolling viewport over the original reading order.
 *
 * The reader never needs to decode a whole chapter for continuous scrolling:
 * a single rolling group has at most three source pages in memory.
 */
internal object WebtoonWindow {
    const val MAX_DECODED_PAGES = 3
    const val MAX_DECODED_PIXELS = 36_000_000L

    fun indices(pageCount: Int, start: Int, capacity: Int = MAX_DECODED_PAGES): List<Int> {
        require(pageCount in 1..5_000 && start in 0 until pageCount)
        require(capacity in 1..MAX_DECODED_PAGES)
        return (start until minOf(pageCount, start + capacity)).toList()
    }

    fun nextStart(pageCount: Int, currentStart: Int, loaded: Int): Int? {
        require(currentStart in 0 until pageCount && loaded in 1..MAX_DECODED_PAGES)
        return if (currentStart + loaded < pageCount) currentStart + 1 else null
    }

    fun previousStart(currentStart: Int): Int? =
        if (currentStart > 0) currentStart - 1 else null

    fun atOffset(start: Int, slotHeights: List<Int>, offsetY: Int): Int {
        require(start >= 0 && slotHeights.isNotEmpty() && slotHeights.all { it >= 1 })
        var offset = offsetY.coerceAtLeast(0)
        for ((index, height) in slotHeights.withIndex()) {
            if (offset < height) return start + index
            offset -= height
        }
        return start + slotHeights.lastIndex
    }
}
