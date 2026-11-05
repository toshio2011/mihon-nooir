package dev.nooir.bridge

/**
 * Experimental Android/desktop-export post-processor for readable, safe manga
 * viewports. Operates on *source coordinates* after all ordinary panel
 * detection and safe-corridor slicing, before any output frame is rendered.
 *
 * The XTEink X4 reference is approximately 220 ppi; 5/10 mm are therefore
 * represented by 43/87 pixels when the final rectangle is fitted to a
 * 480x800 viewport. Other presets scale this reference with their shorter
 * display side; that is a readability-equivalent, NOT an assertion about the
 * unknown physical DPI of other devices.
 *
 * No input rectangle is ever discarded: tiny views are coalesced ONLY into
 * directly adjoining, aligned source views. An unmergeable small legitimate
 * frame is kept. Deliberately repeated overlap is never removed.
 */
object PanelReadableSliceRefiner {
    private const val X4_WIDTH = 480
    private const val X4_HARD_PIXELS = 43
    private const val X4_SOFT_PIXELS = 87
    private const val MAX_VIEWS = 32

    /** Return minimum DISPLAY dimension after scale-to-fit, in X4-equivalent px. */
    fun fittedMinimumX4Pixels(slice: PanelFitSlice, portraitWidth: Int,
        portraitHeight: Int): Double {
        require(slice.region.width > 0 && slice.region.height > 0)
        require(portraitWidth > 0 && portraitHeight > 0)
        val vw = if (slice.landscape) maxOf(portraitWidth, portraitHeight)
                 else minOf(portraitWidth, portraitHeight)
        val vh = if (slice.landscape) minOf(portraitWidth, portraitHeight)
                 else maxOf(portraitWidth, portraitHeight)
        val scale = minOf(vw.toDouble() / slice.region.width,
            vh.toDouble() / slice.region.height)
        val displayedMin = minOf(slice.region.width * scale, slice.region.height * scale)
        return displayedMin * X4_WIDTH / minOf(portraitWidth, portraitHeight)
    }

    private fun union(a: PageRegion, b: PageRegion) = PageRegion(
        minOf(a.left, b.left), minOf(a.top, b.top),
        maxOf(a.right, b.right), maxOf(a.bottom, b.bottom),
    )

    private fun compatible(a: PageRegion, b: PageRegion,
        sourceWidth: Int, sourceHeight: Int): Boolean {
        val xTol = maxOf(2, sourceWidth / 40)
        val yTol = maxOf(2, sourceHeight / 40)
        // Same original horizontal tier, adjacent left/right crops.
        val horizontal = kotlin.math.abs(a.top - b.top) <= yTol &&
            kotlin.math.abs(a.bottom - b.bottom) <= yTol &&
            (kotlin.math.abs(a.right - b.left) <= xTol ||
             kotlin.math.abs(b.right - a.left) <= xTol)
        // Same original vertical column, adjacent upper/lower crops.
        val vertical = kotlin.math.abs(a.left - b.left) <= xTol &&
            kotlin.math.abs(a.right - b.right) <= xTol &&
            (kotlin.math.abs(a.bottom - b.top) <= yTol ||
             kotlin.math.abs(b.bottom - a.top) <= yTol)
        return horizontal || vertical
    }

    /**
     * First absorb tiny fragments, THEN split genuinely oversized rows.
     * A source-wide row > 2/3 source height is split into two 56%-high views
     * overlapping over 12% of THAT row. Only on already multi-view pages,
     * and never for narrow/tall genuine panels or a protected sparse view.
     *
     * @param inkAt callback to existing decoded-source bitmap, only used
     *              when judging optional 5–10 mm sparse strips.
     */
    fun refine(
        input: List<PanelFitSlice>,
        sourceWidth: Int, sourceHeight: Int,
        portraitWidth: Int, portraitHeight: Int,
        inkAt: (Int, Int) -> Boolean,
        protectedRegions: List<PageRegion> = emptyList(),
    ): List<PanelFitSlice> {
        require(sourceWidth > 0 && sourceHeight > 0)
        require(portraitWidth > 0 && portraitHeight > 0)
        require(input.isNotEmpty() && input.size <= MAX_VIEWS)
        if (input.size < 2) return input

        val views = input.toMutableList()
        fun protected(region: PageRegion) = protectedRegions.any { it == region }
        var i = 0
        while (i < views.size && views.size >= 2) {
            val item = views[i]
            if (protected(item.region)) { i++; continue }
            val fitted = fittedMinimumX4Pixels(item, portraitWidth, portraitHeight)
            val hard = fitted < X4_HARD_PIXELS
            val soft = fitted < X4_SOFT_PIXELS
            if (!hard && !soft) { i++; continue }

            // 5–10 mm: only merge the almost-empty border/decorative strip.
            // Under 5 mm: merge any aligned view, preserving its entire area.
            val mergeable = hard || PanelSafeSlicePlanner.isSparse(item.region, inkAt)
            if (!mergeable) { i++; continue }

            val neighbors = listOf(i - 1, i + 1).filter { it in views.indices &&
                !protected(views[it].region) &&
                compatible(item.region, views[it].region, sourceWidth, sourceHeight)
            }
            if (neighbors.isEmpty()) { i++; continue } // Never delete unmergeable art.
            val other = neighbors.minByOrNull { n ->
                val combined = union(item.region, views[n].region)
                combined.width.toLong() * combined.height -
                    views[n].region.width.toLong() * views[n].region.height
            } ?: run { i++; continue }
            val merged = union(item.region, views[other].region)
            val refit = PanelFitSlice(merged, PanelAutoFit.landscapeForMaximumFill(
                merged, portraitWidth, portraitHeight))
            val index = minOf(i, other)
            val remove = maxOf(i, other)
            views.removeAt(remove)
            views.removeAt(index)
            views.add(index, refit)
            i = maxOf(0, index - 1) // A second tiny strip may join safely.
        }

        // Protect intentionally whole splash art and the tiny-footer case
        // already absorbed into a single source region. Splitting it again
        // would resurrect exactly the frame we wanted to eliminate.
        if (views.size < 2) return views
        val result = mutableListOf<PanelFitSlice>()
        for (view in views) {
            val p = view.region
            val fullWidthRow = p.width.toLong() * 100 >= sourceWidth.toLong() * 80
            val hugeRow = p.height.toLong() * 3 > sourceHeight.toLong() * 2
            val overlapsOther = views.any { it !== view && intersectArea(p, it.region) > 0 }
            if (!protected(p) && fullWidthRow && hugeRow &&
                !overlapsOther && result.size + (views.size - result.size) < MAX_VIEWS) {
                val firstEnd = p.top + (p.height.toLong() * 56 / 100).toInt()
                val secondStart = p.top + (p.height.toLong() * 44 / 100).toInt()
                val first = p.copy(bottom = firstEnd)
                val second = p.copy(top = secondStart)
                if (first.height > 0 && second.height > 0) {
                    result.add(PanelFitSlice(first, PanelAutoFit.landscapeForMaximumFill(
                        first, portraitWidth, portraitHeight)))
                    result.add(PanelFitSlice(second, PanelAutoFit.landscapeForMaximumFill(
                        second, portraitWidth, portraitHeight)))
                    continue
                }
            }
            result.add(view)
        }
        return if (result.size <= MAX_VIEWS) result else views
    }

    private fun intersectArea(a: PageRegion, b: PageRegion): Long =
        maxOf(0, minOf(a.right, b.right) - maxOf(a.left, b.left)).toLong() *
        maxOf(0, minOf(a.bottom, b.bottom) - maxOf(a.top, b.top))
}
