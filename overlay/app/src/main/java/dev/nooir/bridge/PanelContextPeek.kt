package dev.nooir.bridge

/**
 * Opt-in framing *after* the conservative panel detector has produced
 * multiple believable panels. Never creates a split or discards source pixels.
 * Repeats at most a narrow neighboring edge so the panel remains readable
 * without reconstructing the whole source page in one view.
 */
object PanelContextPeek {
    fun expand(
        regions: List<PageRegion>, sourceWidth: Int, sourceHeight: Int,
        percent: Int
    ): List<PageRegion> {
        require(sourceWidth > 0 && sourceHeight > 0)
        if (regions.size < 2 || percent <= 0) return regions
        val pct = percent.coerceIn(0, 8)
        val canvas = PageRegion(0, 0, sourceWidth, sourceHeight)
        fun overlap(a: Int, b: Int, c: Int, d: Int): Int =
            (minOf(b, d) - maxOf(a, c)).coerceAtLeast(0)

        return regions.map { r ->
            require(r.left >= 0 && r.top >= 0 &&
                r.right <= sourceWidth && r.bottom <= sourceHeight)
            val dx = (r.width * pct / 100).coerceAtLeast(1)
            val dy = (r.height * pct / 100).coerceAtLeast(1)
            var left = r.left
            var right = r.right
            var top = r.top
            var bottom = r.bottom
            for (other in regions) {
                if (other === r || other == r) continue
                if (overlap(r.left, r.right, other.left, other.right) * 2 >=
                    minOf(r.width, other.width)) {
                    val gapAbove = r.top - other.bottom
                    val gapBelow = other.top - r.bottom
                    if (gapAbove in -dy..dy) {
                        // Half of the budget can include neighboring artwork;
                        // a large gutter cannot cause a speculative long crop.
                        top = minOf(top, other.bottom - dy / 2)
                    }
                    if (gapBelow in -dy..dy) {
                        bottom = maxOf(bottom, other.top + dy / 2)
                    }
                }
                if (overlap(r.top, r.bottom, other.top, other.bottom) * 2 >=
                    minOf(r.height, other.height)) {
                    val gapLeft = r.left - other.right
                    val gapRight = other.left - r.right
                    if (gapLeft in -dx..dx) left = minOf(left, other.right - dx / 2)
                    if (gapRight in -dx..dx) right = maxOf(right, other.left + dx / 2)
                }
            }
            // Never reveal more than 2 * the selected small peek budget,
            // even with overlapping detector padding or multiple neighbors.
            PageRegion(
                left.coerceAtLeast(maxOf(canvas.left, r.left - dx * 2)),
                top.coerceAtLeast(maxOf(canvas.top, r.top - dy * 2)),
                right.coerceAtMost(minOf(canvas.right, r.right + dx * 2)),
                bottom.coerceAtMost(minOf(canvas.bottom, r.bottom + dy * 2))
            )
        }
    }
}
