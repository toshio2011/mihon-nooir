package dev.nooir.bridge

/**
 * Platform-neutral, OCR-free last resort for unusually long *confirmed* panels.
 *
 * Clean three-pixel-wide corridors remain the primary split path. When the user
 * explicitly requests substantial overlap, one *mostly clean* corridor with
 * a small localized crossing may also be used, repeating both sides around
 * that cut to protect artwork. Solid art/no trustworthy seam stays whole.
 *
 * The caller supplies its own bitmap adapter. Android and desktop therefore
 * use identical geometry and RTL order without decoding into another bitmap.
 */
object PanelSafeSlicePlanner {
    private const val MAX_FRAMES = 32
    private const val MAX_OVERLAP_PERCENT = 30

    /**
     * Only a tiny amount of visible ink means a page/region might be a sparse
     * manga frame or thin rules. Preserve the entire region rather than
     * interpreting white corridors as extra screens. Never delete art.
     *
     * Shared by the Android Panel-by-Panel fallback and this safe slicer so
     * both routes use the identical threshold, including alpha blending.
     * Sampling is bounded to approximately 180 x 180 original pixels.
     */
    fun isSparse(panel: PageRegion, inkAt: (Int, Int) -> Boolean): Boolean {
        require(panel.width > 0 && panel.height > 0)
        val stepX = maxOf(1, (panel.width.toLong() + 179) / 180).toInt()
        val stepY = maxOf(1, (panel.height.toLong() + 179) / 180).toInt()
        var ink = 0
        var samples = 0
        for (y in panel.top until panel.bottom step stepY)
            for (x in panel.left until panel.right step stepX) {
                samples++
                if (inkAt(x, y)) ink++
            }
        return samples > 0 && ink.toLong() * 100 < samples.toLong() * 3
    }

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

        // Share the sparse-art rule with the Android pre-detection fallback:
        // an entire ruled page must not bypass this protection merely because
        // panel detection returned a single-page fallback.
        if (isSparse(panel, inkAt)) return listOf(panel)

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

        // Opt-in for imperfect-but-obvious corridor crossings. A handful of
        // characters or inked edges inside an otherwise pale separation must
        // not automatically collapse a long illustrated page to one screen.
        // Never use this path if requested overlap is below 8%: protection
        // for both halves would be too small.
        fun protectedInterruptedCorridor(axis: Int): Boolean {
            if (axis - 2 <= start || axis + 2 >= end) return false
            val crossSpan = if (tall) panel.width else panel.height
            var ink = 0
            val samples = crossSpan.toLong() * 5L
            for (shift in -2..2) {
                val line = axis + shift
                if (tall) {
                    for (x in panel.left until panel.right) {
                        if (inkAt(x, line)) ink++
                        if (ink.toLong() * 100L > samples * 20L) return false
                    }
                } else {
                    for (y in panel.top until panel.bottom) {
                        if (inkAt(line, y)) ink++
                        if (ink.toLong() * 100L > samples * 20L) return false
                    }
                }
            }
            // Do not use a speculative seam through continuous or blank art:
            // only local interruptions of a near-empty, otherwise bright band.
            return ink > 0
        }

        val slices = mutableListOf<PageRegion>()
        // A whole panel may have enough ink to pass the original sparse guard,
        // while one clean corridor still leaves a nearly empty magnified page.
        // Never drop that region: coalesce all remaining source pixels into the
        // previous illustrated slice, or retain the whole original panel.
        fun coalesceSparseTail(): List<PageRegion> {
            if (slices.isEmpty()) return listOf(panel)
            val previous = slices.last()
            slices[slices.lastIndex] = if (tall) previous.copy(bottom = end)
                else previous.copy(right = end)
            return if (wide && rightToLeft) slices.reversed() else slices
        }
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
            // No usable seam near the tail. Earlier cuts were verified and
            // remain safe: keep them, then emit the entire unsplit remainder.
            // A first-cut failure still returns the original whole panel.
            if (low > high) break

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
            // Only after the proven-clean search fails may substantial user
            // overlap permit a mostly pale corridor with short crossing ink.
            // Strict/zero-overlap jobs retain their historical no-ink rule.
            var interrupted = false
            if (boundary == null && overlap >= 8) {
                for (distance in 0..furthest) {
                    val before = ideal - distance
                    val after = ideal + distance
                    val choices = if (distance == 0) listOf(before)
                        else listOf(before, after)
                    val candidate = choices.firstOrNull { axis ->
                        axis in low..high && protectedInterruptedCorridor(axis)
                    }
                    if (candidate != null) {
                        boundary = candidate
                        interrupted = true
                        break
                    }
                }
            }
            // Do not undo earlier clean, proven cuts just because a later
            // portion is continuous art with no trustworthy separator.
            // The final remainder stays whole, without losing any pixels.
            if (boundary == null) break
            val cut = boundary
            // When the divider contains any artwork, repeat a generous band
            // AFTER the nominal cut as well as BEFORE the next slice. Merely
            // moving the next cursor backwards would leave the FIRST view's
            // crossing balloon/art edge visibly truncated.
            val repeated = (cut - cursor).toLong() * overlap / 100
            val afterPad = if (interrupted)
                maxOf(8, minOf(segment * 8 / 100, repeated.toInt()))
                    .coerceAtMost(end - cut)
            else 0
            val first = if (tall) PageRegion(panel.left, cursor, panel.right, cut + afterPad)
                        else PageRegion(cursor, panel.top, cut + afterPad, panel.bottom)
            if (interrupted) {
                // Refuse isolated sparse panels that would become almost blank
                // magnified viewports. Preserve original art instead.
                val remainder = if (tall) PageRegion(panel.left, cut, panel.right, end)
                                else PageRegion(cut, panel.top, end, panel.bottom)
                if (isSparse(first, inkAt) || isSparse(remainder, inkAt))
                    break // Keep previous cuts; group the unsafe remainder.
            }
            // A clean seam can also isolate a sparse title/border fragment.
            // Protect it just like an interrupted seam, without throwing away
            // any previously validated dense regions.
            if (isSparse(first, inkAt)) return coalesceSparseTail()
            slices += first
            cursor = (cut - repeated.toInt()).coerceAtLeast(cursor + 1)
        }

        val tail = if (tall) PageRegion(panel.left, cursor, panel.right, end)
                   else PageRegion(cursor, panel.top, end, panel.bottom)
        if (isSparse(tail, inkAt)) return coalesceSparseTail()
        slices += tail
        return if (wide && rightToLeft) slices.reversed() else slices
    }
}
