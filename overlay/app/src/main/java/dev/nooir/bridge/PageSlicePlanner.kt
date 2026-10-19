package dev.nooir.bridge

/** Coordinates of a source-image slice (right/bottom are exclusive). */
data class PageRegion(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * Pure geometry for image pages, double-page spreads and long webtoon strips.
 * Converted bitmap dimensions and rotation are handled separately so the same
 * cuts work for XTC/XTCH, CBZ and fixed-layout EPUB.
 */
object PageSlicePlanner {
    fun plan(
        sourceWidth: Int, sourceHeight: Int,
        viewWidth: Int, viewHeight: Int,
        layout: String, splitSpreads: Boolean, rightToLeft: Boolean,
        overlapPercent: Int,
    ): List<PageRegion> {
        require(sourceWidth > 0 && sourceHeight > 0 && viewWidth > 0 && viewHeight > 0)
        val strip = when (layout) {
            "WEBTOON" -> true
            "PAGED" -> false
            else -> sourceHeight.toLong() > sourceWidth.toLong() * 3L
        }
        if (strip) {
            // Map the source width to one screen width; slice the corresponding
            // source height so none of the manga becomes smaller than necessary.
            val segment = (sourceWidth.toLong() * viewHeight / viewWidth)
                .coerceIn(1, sourceHeight.toLong()).toInt()
            if (segment >= sourceHeight) return listOf(PageRegion(0, 0, sourceWidth, sourceHeight))
            val overlap = (segment.toLong() * overlapPercent.coerceIn(0, 50) / 100).toInt()
            val regions = mutableListOf<PageRegion>()
            var top = 0
            while (top < sourceHeight) {
                val bottom = (top.toLong() + segment).coerceAtMost(sourceHeight.toLong()).toInt()
                regions += PageRegion(0, top, sourceWidth, bottom)
                if (bottom == sourceHeight) break
                top = (bottom - overlap).coerceAtLeast(top + 1)
                require(regions.size <= 5000) { "Too many slices: choose a smaller chapter or reduce overlap" }
            }
            return regions
        }
        if (splitSpreads && sourceWidth.toLong() * 100L > sourceHeight.toLong() * 135L && sourceWidth >= 2) {
            val mid = sourceWidth / 2
            val left = PageRegion(0, 0, mid, sourceHeight)
            val right = PageRegion(mid, 0, sourceWidth, sourceHeight)
            return if (rightToLeft) listOf(right, left) else listOf(left, right)
        }
        return listOf(PageRegion(0, 0, sourceWidth, sourceHeight))
    }
}
