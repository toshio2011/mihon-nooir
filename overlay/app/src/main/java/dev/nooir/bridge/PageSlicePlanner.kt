package dev.nooir.bridge

/** Coordinates of a source-image slice (right/bottom are exclusive). */
data class PageRegion(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * Pure geometry for manga pages, double-page spreads and long strips.
 * Uses viewport dimensions rather than target bitmap dimensions: XTC/XTCH
 * landscape content is rotated into the device's portrait framebuffer later.
 */
object PageSlicePlanner {
    fun plan(
        sourceWidth: Int, sourceHeight: Int,
        viewWidth: Int, viewHeight: Int,
        layout: String, splitSpreads: Boolean, rightToLeft: Boolean,
        overlapPercent: Int, orientation: String = "PORTRAIT",
    ): List<PageRegion> {
        require(sourceWidth > 0 && sourceHeight > 0 && viewWidth > 0 && viewHeight > 0)
        val whole = PageRegion(0, 0, sourceWidth, sourceHeight)
        val strip = when (layout) {
            "WEBTOON" -> true
            "PAGED" -> false
            else -> sourceHeight.toLong() > sourceWidth.toLong() * 3L
        }

        // A long strip always scrolls from top to bottom, regardless of the
        // traditional manga reading direction.
        if (strip) return cutVertical(whole, viewWidth, viewHeight, overlapPercent)

        // Split a double-width spread in reading order before applying
        // landscape cuts to either half.
        val pieces = if (splitSpreads &&
            sourceWidth.toLong() * 100L > sourceHeight.toLong() * 135L &&
            sourceWidth >= 2
        ) {
            val mid = sourceWidth / 2
            val left = PageRegion(0, 0, mid, sourceHeight)
            val right = PageRegion(mid, 0, sourceWidth, sourceHeight)
            if (rightToLeft) listOf(right, left) else listOf(left, right)
        } else listOf(whole)

        if (orientation != "LANDSCAPE") return pieces
        return pieces.flatMap { part ->
            // Hold the XTEink sideways, but do NOT shrink a whole portrait
            // manga page down to the sideways screen. Paginate it into
            // overlapping upper/lower strips. Panorama/spread pieces that
            // are already wide enough remain whole.
            if (part.height.toLong() * 100L > part.width.toLong() * 115L)
                cutVertical(part, viewWidth, viewHeight, overlapPercent)
            else listOf(part)
        }
    }

    private fun cutVertical(
        area: PageRegion, viewWidth: Int, viewHeight: Int, overlapPercent: Int,
    ): List<PageRegion> {
        val segment = (area.width.toLong() * viewHeight / viewWidth)
            .coerceIn(1, area.height.toLong()).toInt()
        if (segment >= area.height) return listOf(area)
        val overlap = (segment.toLong() * overlapPercent.coerceIn(0, 50) / 100).toInt()
        val regions = mutableListOf<PageRegion>()
        var top = area.top
        while (top < area.bottom) {
            val bottom = (top.toLong() + segment).coerceAtMost(area.bottom.toLong()).toInt()
            regions += PageRegion(area.left, top, area.right, bottom)
            if (bottom == area.bottom) break
            top = (bottom - overlap).coerceAtLeast(top + 1)
            require(regions.size <= 5000) { "Too many slices: choose fewer pages or reduce overlap" }
        }
        return regions
    }
}
