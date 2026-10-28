package dev.nooir.bridge

/**
 * Platform-neutral, OCR-free last resort for unusually long *confirmed* panels.
 *
 * A frame is split only at a three-pixel-wide corridor where every original
 * source pixel is bright, including faint letters between a coarse sampling
 * grid. No seam means no split: fit the entire panel rather than invent a cut.
 *
 * The caller supplies its own bitmap adapter. Android and desktop therefore
 * use identical geometry and RTL order without decoding into another bitmap.
 */
object PanelSafeSlicePlanner {
    private const val MAX_FRAMES = 32
    private const val MAX_OVERLAP_PERCENT = 30

    fun plan(
        panel: PageRegion,
        viewportWidth: Int,
        viewportHeight: Int,
        rightToLeft: Boolean,
        overlapPercent: Int,
        inkAt: (Int, Int) -> Boolean,
    ): List<PageRegion> {
        require(panel.width > 0 && panel.height > 0)
        require(viewportWidth > 0 && viewportHeight > 0)

        val tall = panel.height.toLong() * viewportWidth >
            panel.width.toLong() * viewportHeight * 2
        val wide = panel.width.toLong() * viewportHeight >
            panel.height.toLong() * viewportWidth * 2
        if (!tall && !wide) return listOf(panel)

        // A very wide or tall region can consist almost exclusively of frame
        // rules. Cutting every blank corridor in that region creates multiple
        // almost-white screens from what should be one intact sparse tier.
        // Sample at most ~180 x 180 source positions; on extremely large CBZ
        // pages this cannot turn into millions of getPixel() calls.
        //
        // Use the SAME ink callback as the full-pixel corridor checks. If less
        // than 3% of samples contain ink, prefer fitting the WHOLE source
        // region, retaining faint lines and all source pixels. Illustrated
        // long panels still use the existing safe corridor splitter.
        val stepX = maxOf(1, (panel.width + 179) / 180)
        val stepY = maxOf(1, (panel.height + 179) / 180)
        var ink = 0
        var samples = 0
        for (y in panel.top until panel.bottom step stepY)
            for (x in panel.left until panel.right step stepX) {
                samples++
                if (inkAt(x, y)) ink++
            }
        if (samples > 0 && ink.toLong() * 100 < samples.toLong() * 3)
            return listOf(panel)

        val start = if (tall) panel.top else panel.left
        val end = if (tall) panel.bottom else panel.right
        val segment = (if (tall) panel.width.toLong() * viewportHeight / viewportWidth
                       else panel.height.toLong() * viewportWidth / viewportHeight)
            .coerceIn(1L, (end - start).toLong()).toInt()
        if (segment < 12) return listOf(panel)

        fun cleanCorridor(axis: Int): Boolean {
            if (axis - 1 <= start || axis + 1 >= end) return false
            for (shift in -1..1) {
                val line = axis + shift
                if (tall) {
                    for (x in panel.left until panel.right)
                        if (inkAt(x, line)) return false
                } else {
                    for (y in panel.top until panel.bottom)
                        if (inkAt(line, y)) return false
                }
            }
            return true
        }

        val slices = mutableListOf<PageRegion>()
        var cursor = start
        val overlap = overlapPercent.coerceIn(0, MAX_OVERLAP_PERCENT)
        while (cursor.toLong() + segment < end) {
            if (slices.size >= MAX_FRAMES - 1) return listOf(panel)
            // Don't emit a tiny unreadable tail when an otherwise clean
            // last frame can safely include it without cutting artwork.
            if (end - (cursor + segment) < segment * 35 / 100) break
            val ideal = cursor + segment
            val low = maxOf(cursor + segment * 65 / 100, cursor + 3)
            // With overlap, the next ideal position moves backwards; search
            // far enough ahead to recover the same real white corridor.
            val high = minOf(end - segment * 35 / 100,
                ideal + segment * maxOf(10, overlap + 2) / 100)
            if (low > high) return listOf(panel)

            // Search outward from the ideal frame length. Keep the source
            // intact if there is no unambiguous, truly empty corridor.
            var boundary: Int? = null
            val furthest = maxOf(ideal - low, high - ideal)
            for (distance in 0..furthest) {
                val before = ideal - distance
                val after = ideal + distance
                if (before in low..high && cleanCorridor(before)) {
                    boundary = before
                    break
                }
                if (distance != 0 && after in low..high && cleanCorridor(after)) {
                    boundary = after
                    break
                }
            }
            if (boundary == null) return listOf(panel)
            val cut = boundary
            slices += if (tall) PageRegion(panel.left, cursor, panel.right, cut)
                      else PageRegion(cursor, panel.top, cut, panel.bottom)
            val repeated = (cut - cursor).toLong() * overlap / 100
            cursor = (cut - repeated.toInt()).coerceAtLeast(cursor + 1)
        }

        slices += if (tall) PageRegion(panel.left, cursor, panel.right, end)
                  else PageRegion(cursor, panel.top, end, panel.bottom)
        return if (wide && rightToLeft) slices.reversed() else slices
    }
}
