package dev.nooir.bridge

/**
 * Converts one confidently detected panel into one screen unless its aspect
 * ratio would make text unusually small. Extra-long panels are cut into
 * overlapping sections of THAT panel, not combined with adjacent panels.
 *
 * Source coordinates remain integer, inclusive left/top and exclusive right/bottom.
 * No Android dependency: this geometry is shared by all conversion platforms.
 */
object PanelViewportPlanner {
    private const val MAX_SEGMENTS = 128
    private const val ELONGATION_PERCENT = 180L

    fun plan(
        panel: PageRegion,
        viewportWidth: Int,
        viewportHeight: Int,
        rightToLeft: Boolean,
        overlapPercent: Int,
    ): List<PageRegion> {
        require(panel.width > 0 && panel.height > 0)
        require(viewportWidth > 0 && viewportHeight > 0)
        val overlap = overlapPercent.coerceIn(5, 30)
        val tall = panel.height.toLong() * viewportWidth * 100 >
            panel.width.toLong() * viewportHeight * ELONGATION_PERCENT
        val wide = panel.width.toLong() * viewportHeight * 100 >
            panel.height.toLong() * viewportWidth * ELONGATION_PERCENT
        if (!tall && !wide) return listOf(panel)
        if (tall) {
            val segment = (panel.width.toLong() * viewportHeight / viewportWidth)
                .coerceIn(1L, panel.height.toLong()).toInt()
            return cut(panel.top, panel.bottom, segment, overlap).map {
                PageRegion(panel.left, it.first, panel.right, it.second)
            }
        }
        val segment = (panel.height.toLong() * viewportWidth / viewportHeight)
            .coerceIn(1L, panel.width.toLong()).toInt()
        val pieces = cut(panel.left, panel.right, segment, overlap).map {
            PageRegion(it.first, panel.top, it.second, panel.bottom)
        }
        return if (rightToLeft) pieces.reversed() else pieces
    }

    private fun cut(start: Int, end: Int, segment: Int, overlapPercent: Int): List<Pair<Int, Int>> {
        val overlap = (segment.toLong() * overlapPercent / 100).toInt()
        val result = mutableListOf<Pair<Int, Int>>()
        var cursor = start
        while (cursor < end) {
            val next = (cursor.toLong() + segment).coerceAtMost(end.toLong()).toInt()
            result += cursor to next
            if (next == end) break
            require(result.size < MAX_SEGMENTS) {
                "A single manga panel needs too many screens; choose a larger viewport or split this export"
            }
            cursor = (next - overlap).coerceAtLeast(cursor + 1)
        }
        return result
    }
}
