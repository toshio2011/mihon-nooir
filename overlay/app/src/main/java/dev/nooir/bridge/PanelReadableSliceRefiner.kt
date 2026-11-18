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
        // SourceRegions adds roughly 1.25% crop padding to BOTH adjacent
        // regions, so a real neighboring horizontal tier can overlap by
        // ~2.6% of source height (One Piece source 197 / 201: 25 pixels).
        // A bounded 3% side-adjacency tolerance still requires matching
        // opposite edges: unrelated panels cannot be arbitrarily merged.
        val xTol = maxOf(2, sourceWidth * 3 / 100)
        val yTol = maxOf(2, sourceHeight * 3 / 100)
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
     * A successful second pass must cover the ENTIRE original source row,
     * even gutters, so no dialogue or sound effect can silently disappear.
     * Sweep the rectangle boundaries exactly (not a low-res sampling guess).
     */
    internal fun coversSourceRow(outer: PageRegion, parts: List<PageRegion>): Boolean {
        if (parts.isEmpty() || parts.any {
            it.left < outer.left || it.top < outer.top ||
                it.right > outer.right || it.bottom > outer.bottom ||
                it.width <= 0 || it.height <= 0
        }) return false
        val yCuts = (parts.flatMap { listOf(it.top, it.bottom) } +
            listOf(outer.top, outer.bottom)).distinct().sorted()
        for (i in 0 until yCuts.lastIndex) {
            val top = yCuts[i]
            val bottom = yCuts[i + 1]
            if (bottom <= top) continue
            val spans = parts.filter { it.top <= top && it.bottom >= bottom }
                .map { it.left to it.right }.sortedBy { it.first }
            var x = outer.left
            for ((left, right) in spans) {
                if (left > x) return false
                x = maxOf(x, right)
                if (x >= outer.right) break
            }
            if (x < outer.right) return false
        }
        return true
    }

    /**
     * Pixel-only no-OCR signal that one long row is a deliberately framed
     * illustration. Look for a nearly unbroken 4-sided outer border within
     * the first/last six percent of source pixels; do not mistake a dark
     * interior stroke for a safe split point. Conservative false negatives
     * are acceptable because the second-look detector runs FIRST.
     */
    internal fun looksLikeOneFramedPanel(
        p: PageRegion, inkAt: (Int, Int) -> Boolean,
    ): Boolean {
        if (p.width < 120 || p.height < 160) return false
        val xBand = maxOf(3, p.width * 6 / 100)
        val yBand = maxOf(3, p.height * 6 / 100)
        val stepX = maxOf(1, p.width / 120)
        val stepY = maxOf(1, p.height / 120)
        fun vertical(fromLeft: Boolean): Double {
            var hits = 0
            var n = 0
            for (y in p.top until p.bottom step stepY) {
                n++
                var found = false
                for (dx in 0 until xBand step maxOf(1, xBand / 6)) {
                    val x = if (fromLeft) p.left + dx else p.right - dx - 1
                    if (inkAt(x, y)) { found = true; break }
                }
                if (found) hits++
            }
            return if (n > 0) hits.toDouble() / n else 0.0
        }
        fun horizontal(fromTop: Boolean): Double {
            var hits = 0
            var n = 0
            for (x in p.left until p.right step stepX) {
                n++
                var found = false
                for (dy in 0 until yBand step maxOf(1, yBand / 6)) {
                    val y = if (fromTop) p.top + dy else p.bottom - dy - 1
                    if (inkAt(x, y)) { found = true; break }
                }
                if (found) hits++
            }
            return if (n > 0) hits.toDouble() / n else 0.0
        }
        val edges = listOf(vertical(true), vertical(false),
            horizontal(true), horizontal(false))
        return edges.count { it >= 0.76 } == 4
    }

    /**
     * First absorb tiny fragments, then revisit broad rows that still
     * occupy >50% of the source page. The caller gets ONE opportunity to
     * find safe inner panels before any fallback.
     *
     * Unresolved >60%-height rows use two 56%-high views (12% overlap)
     * only without dominant one-panel framing. Rows at 50-60% need the
     * stronger condition of poor final fitted-screen occupancy (<55%).
     * Never force-cut narrow tall panels, previously overlapping diagonal
     * views, protected sparse/art pages or recovered genuine panels.
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
        autoFit: Boolean = true,
        recoverInnerPanels: ((PageRegion) -> List<PageRegion>?)? = null,
        allowSingleEdgeZoom: Boolean = false,
        enlargeOversizedSplits: Boolean = true,
        minimumScreenFillPercent: Int = 55,
        requireZoomGain: Boolean = false,
    ): List<PanelFitSlice> {
        require(sourceWidth > 0 && sourceHeight > 0)
        require(portraitWidth > 0 && portraitHeight > 0)
        require(input.isNotEmpty() && input.size <= MAX_VIEWS)
        // An edge-strip merge may legitimately leave ONE large illustrated
        // source rectangle, but QA9 accidentally interpreted it as a protected
        // splash and suppressed all useful close-ups. This explicit flag is
        // supplied only for that one precise detector state. No ordinary
        // single-page splash, text, or spare image uses this route.
        if (input.size < 2 && !allowSingleEdgeZoom) return input

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
            }
            if (other == null) { i++; continue }
            val merged = union(item.region, views[other].region)
            val refit = PanelFitSlice(merged,
                if (autoFit) PanelAutoFit.landscapeForMaximumFill(
                    merged, portraitWidth, portraitHeight)
                else item.landscape)
            val index = minOf(i, other)
            val remove = maxOf(i, other)
            views.removeAt(remove)
            views.removeAt(index)
            views.add(index, refit)
            i = maxOf(0, index - 1) // A second tiny strip may join safely.
        }

        // Protect intentionally whole splash art and the tiny-footer case
        // already absorbed into one source rectangle. Never recreate the
        // useless tiny view by forcing a new cut through the merged page.
        if (views.size < 2 && !allowSingleEdgeZoom) return views
        val result = mutableListOf<PanelFitSlice>()
        for (view in views) {
            val p = view.region
            val fullWidthRow = p.width.toLong() * 100 >= sourceWidth.toLong() * 80
            val overHalf = p.height.toLong() * 100 > sourceHeight.toLong() * 50
            // Ordinary 1%-padding overlap is expected. Strong overlap means
            // a deliberately repeated slanted/diagonal viewport already.
            val overlapsOther = views.any { it !== view &&
                intersectArea(p, it.region) * 100 >
                    p.width.toLong() * p.height * 5
            }
            if (protected(p) || !fullWidthRow || !overHalf || overlapsOther ||
                result.size + views.size >= MAX_VIEWS) {
                result.add(view)
                continue
            }

            // Recover actual page structure INSIDE the remaining large row,
            // and accept it only when every original pixel is represented
            // by the proposed source rectangles. A half-blank gutter or
            // missing isolated letter is a hard veto, not an ignored 10%.
            val recovered = recoverInnerPanels?.invoke(p)
            if (recovered != null && recovered.size in 2..6 &&
                result.size + recovered.size + views.size <= MAX_VIEWS &&
                coversSourceRow(p, recovered) &&
                recovered.all {
                    it.width.toLong() * 100 >= p.width.toLong() * 16 &&
                    it.height.toLong() * 100 >= p.height.toLong() * 16 &&
                    it.width.toLong() * it.height * 100 >=
                        p.width.toLong() * p.height * 8
                }) {
                for (r in recovered) result.add(PanelFitSlice(r,
                    if (autoFit) PanelAutoFit.landscapeForMaximumFill(
                        r, portraitWidth, portraitHeight)
                    else view.landscape))
                continue
            }

            val hugeRow = p.height.toLong() * 100 > sourceHeight.toLong() * 60
            val viewportW = if (view.landscape) maxOf(portraitWidth, portraitHeight)
                else minOf(portraitWidth, portraitHeight)
            val viewportH = if (view.landscape) minOf(portraitWidth, portraitHeight)
                else maxOf(portraitWidth, portraitHeight)
            val poorFit = PanelAutoFit.occupiedFraction(p, viewportW, viewportH) <
                minimumScreenFillPercent.coerceIn(25,80) / 100.0
            val shouldOverlap = enlargeOversizedSplits && (hugeRow || poorFit)
            // A strong enclosing border indicates one intentionally large
            // manga panel. Preserve its composition rather than halving it.
            if (!shouldOverlap || looksLikeOneFramedPanel(p, inkAt)) {
                result.add(view)
                continue
            }

            val halves = if (requireZoomGain)
                readableOverlap(view, portraitWidth, portraitHeight, autoFit, inkAt)
            else overlapHalves(view, portraitWidth, portraitHeight, autoFit)
            if (halves == null) result.add(view) else result.addAll(halves)
        }
        return if (result.size <= MAX_VIEWS) result else views
    }

    /** Strict row-only zoom: no inner-column detection, no arbitrary whole-page cut. */
    fun refineRows(input: List<PanelFitSlice>, sourceWidth: Int, sourceHeight: Int,
        portraitWidth: Int, portraitHeight: Int, inkAt: (Int, Int) -> Boolean,
        autoFit: Boolean, minimumScreenFillPercent: Int = 50,
        protectedRegions: List<PageRegion> = emptyList()): List<PanelFitSlice> {
        if (input.size !in 2..MAX_VIEWS) return input
        val result = mutableListOf<PanelFitSlice>()
        for ((index, view) in input.withIndex()) {
            val p = view.region
            val w = if (view.landscape) maxOf(portraitWidth, portraitHeight) else minOf(portraitWidth, portraitHeight)
            val h = if (view.landscape) minOf(portraitWidth, portraitHeight) else maxOf(portraitWidth, portraitHeight)
            val poorFit = PanelAutoFit.occupiedFraction(p,w,h) < minimumScreenFillPercent.coerceIn(25,80)/100.0
            val overlaps = input.any { it !== view &&
                intersectArea(p,it.region)*100 > p.width.toLong()*p.height*10 }
            if (p in protectedRegions || p.height.toLong()*2 <= sourceHeight ||
                p.width.toLong()*100 < sourceWidth.toLong()*80 ||
                !poorFit || overlaps || result.size + input.size-index >= MAX_VIEWS ||
                PanelSafeSlicePlanner.isSparse(p,inkAt) || looksLikeOneFramedPanel(p,inkAt)) {
                result.add(view)
                continue
            }
            val halves = readableOverlap(view, portraitWidth, portraitHeight, autoFit, inkAt)
            if (halves != null) result.addAll(halves) else result.add(view)
        }
        return result
    }

    /**
     * Only move an already eligible overlapping cut when a contiguous pale
     * band is substantially quieter than the central band. This is not a
     * second panel detector or OCR. Search the middle 40–60%, keep the exact
     * old overlap width and preserve every source pixel. If uncertain, use
     * the previous centered plan, subject to the same enlargement guards.
     */
    private fun readableOverlap(view: PanelFitSlice, w: Int, h: Int, autoFit: Boolean,
        inkAt: (Int, Int) -> Boolean): List<PanelFitSlice>? {
        val p = view.region
        val centered = overlapHalves(view, w, h, autoFit) ?: return null
        val old = centered.takeIf { hasZoomGain(view, it, w, h, inkAt) }
        // An off-center cut cannot improve the worst half's scale over a
        // centered cut with the same repeated band. Avoid extra pixel work
        // entirely if the old balanced plan would not actually enlarge.
        if (old == null) return null
        if (p.width < 120 || p.height < 160) return old
        val midpoint = p.top + p.height / 2
        val band = (p.height / 120).coerceIn(2, 12)
        val reach = p.height / 10
        val step = maxOf(1, reach / 16)
        val dx = maxOf(1, (p.width.toLong() + 159) / 160).toInt()
        // Five sampled rows for ranking; validate every pixel in every row
        // of up to three contiguous bands (at most 25 rows per band).
        fun inkFraction(y: Int, fullWidth: Boolean): Double {
            var worst = 0.0
            val sample = if (fullWidth) 1 else dx
            val offsets = if (fullWidth) (-band..band).toList()
                else listOf(-band, -band / 2, 0, band / 2, band).distinct()
            for (offset in offsets) {
                var ink = 0
                var n = 0
                for (x in p.left until p.right step sample) {
                    if (inkAt(x, y + offset)) ink++
                    n++
                }
                if (n > 0) worst = maxOf(worst, ink.toDouble() / n)
                if (fullWidth && worst > 0.04) return worst
            }
            return worst
        }
        val centerInk = inkFraction(midpoint, false)
        // Already quiet: do not trade balanced enlargement for a marginal gain.
        if (centerInk <= 0.12) return old
        val candidates = mutableListOf<Pair<Int, Double>>()
        for (y in midpoint - reach..midpoint + reach step step) {
            if (kotlin.math.abs(y - midpoint) < band) continue
            val score = inkFraction(y, false)
            if (score <= 0.04 && centerInk - score >= 0.12)
                candidates.add(y to score)
        }
        // Closest strongly supported quiet band first; deterministic tie break.
        // Full-width validation of at most three proposals limits bitmap work.
        val sorted = candidates.sortedWith(compareBy<Pair<Int, Double>> {
            kotlin.math.abs(it.first - midpoint)
        }.thenBy { it.second }.thenBy { it.first })
        for ((y, _) in sorted.take(3)) {
            if (inkFraction(y, true) > 0.04) continue
            val shifted = overlapHalves(view, w, h, autoFit, y - midpoint) ?: continue
            if (!hasZoomGain(view, shifted, w, h, inkAt)) continue
            if (!coversSourceRow(p, shifted.map { it.region })) continue
            return shifted
        }
        return old
    }

    private fun overlapHalves(view: PanelFitSlice,w: Int,h: Int,autoFit: Boolean,
        shift: Int = 0): List<PanelFitSlice>? {
        val p = view.region
        if (p.height < 16) return null
        val first = p.copy(bottom=p.top+(p.height.toLong()*56/100).toInt()+shift)
        val second = p.copy(top=p.top+(p.height.toLong()*44/100).toInt()+shift)
        if (first.bottom <= second.top || first.height <= 0 || second.height <= 0 ||
            first.bottom > p.bottom || second.top < p.top) return null
        return listOf(first,second).map { PanelFitSlice(it,
            if (autoFit) PanelAutoFit.landscapeForMaximumFill(it,w,h) else view.landscape) }
    }

    private fun hasZoomGain(original: PanelFitSlice, halves: List<PanelFitSlice>,
        w: Int,h: Int,inkAt: (Int,Int)->Boolean): Boolean {
        fun scale(view: PanelFitSlice): Double {
            val vw=if(view.landscape) maxOf(w,h) else minOf(w,h)
            val vh=if(view.landscape) minOf(w,h) else maxOf(w,h)
            return minOf(vw.toDouble()/view.region.width,vh.toDouble()/view.region.height)
        }
        val old=scale(original)
        return halves.all { scale(it)>=old*1.05 && !PanelSafeSlicePlanner.isSparse(it.region,inkAt) }
    }

    private fun intersectArea(a: PageRegion, b: PageRegion): Long =
        maxOf(0, minOf(a.right, b.right) - maxOf(a.left, b.left)).toLong() *
        maxOf(0, minOf(a.bottom, b.bottom) - maxOf(a.top, b.top))
}
