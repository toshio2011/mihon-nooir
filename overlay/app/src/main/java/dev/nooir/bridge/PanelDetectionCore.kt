package dev.nooir.bridge

/**
 * Conservative, OCR-free panel geometry. Runs on a bounded grayscale thumbnail
 * produced by the Android adapter, NOT on full-resolution manga images.
 *
 * This first phase recognizes clear, full-span white gutters. Complex borderless
 * pages, touching panels, uncertain staggered grids and splash pages fall back
 * to the original intact page. Never crop speculative rectangles.
 *
 * Original algorithm; no source copied from Kumiko/Pannello or ML models.
 */
data class PanelPlan(
    val regions: List<PageRegion>,
    val usedFullPageFallback: Boolean,
    val reason: String,
)

object PanelDetectionCore {
    private const val MAX_THUMB_PIXELS = 1_048_576
    private const val MAX_PANELS = 32
    private const val WHITE = 247
    private const val INK = 220

    fun detect(width: Int, height: Int, grayscale: ByteArray, rightToLeft: Boolean): PanelPlan {
        require(width > 0 && height > 0 && width.toLong() * height <= MAX_THUMB_PIXELS) {
            "Panel detection needs a bounded, nonempty thumbnail"
        }
        require(grayscale.size == width * height) { "Grayscale thumbnail dimensions do not match" }
        val whole = PageRegion(0, 0, width, height)
        fun fallback(why: String) = PanelPlan(listOf(whole), true, why)
        // No OCR means we cannot prove that a low-ink region has no lettering.
        // Only trust clear rectangular gutters; uncertain pages stay intact.
        if (height.toLong() > width.toLong() * 3L) return fallback("long strip: preserve existing webtoon slicing")
        // Wide strips can contain multiple framed panels; let the same guarded gutter detector decide.
        val detector = Detector(width, height, grayscale)
        val root = detector.partition(whole, 0) ?: return fallback("ambiguous gutters or too many regions")
        val regions = PanelReadingOrder.flatten(root, rightToLeft)
            ?: return fallback("staggered layout: reading order uncertain")
        if (regions.size < 2) return fallback("single panel or splash page")
        if (regions.size > MAX_PANELS || regions.any { it.width <= 0 || it.height <= 0 }) {
            return fallback("unsafe panel dimensions")
        }
        if (regions.any { !detector.hasContent(it) }) return fallback("empty or tiny panel candidate")
        // The partition covers every pixel of the original thumbnail. Guard
        // padding in the renderer must further overlap edges, not remove them.
        if (!coversEntirePage(regions, width, height)) return fallback("uncovered image area")
        return PanelPlan(regions, false, "validated white-gutter partition")
    }

    /**
     * Independent row-by-row mode: only horizontal, full-width white gutters
     * separate rows. Vertical panel boundaries are intentionally ignored.
     * Uncertain pages remain whole rather than becoming arbitrary strips.
     */
    fun detectRows(width: Int, height: Int, grayscale: ByteArray): PanelPlan {
        require(width > 0 && height > 0 && width.toLong() * height <= MAX_THUMB_PIXELS)
        require(grayscale.size == width * height)
        val whole = PageRegion(0, 0, width, height)
        fun fallback(reason: String) = PanelPlan(listOf(whole), true, reason)
        val minHeight = maxOf(12, height / 12)
        val minGap = maxOf(3, height / 120)
        val rows = mutableListOf<PageRegion>()
        var previous = 0
        var run = -1
        for (y in 0..height) {
            val white = if (y == height) false else {
                var bright = 0
                var dark = 0
                for (x in 0 until width) {
                    val value = grayscale[y * width + x].toInt() and 255
                    if (value >= WHITE) bright++
                    if (value < INK) dark++
                }
                bright.toLong() * 100 >= width.toLong() * 98 &&
                    dark.toLong() * 200 <= width.toLong()
            }
            if (white && run < 0) run = y
            if (!white && run >= 0) {
                val start = run
                val end = y
                run = -1
                if (end - start >= minGap && start - previous >= minHeight &&
                    height - (start + end) / 2 >= minHeight) {
                    val split = (start + end) / 2
                    rows += PageRegion(0, previous, width, split)
                    previous = split
                }
            }
        }
        if (height - previous >= minHeight) rows += PageRegion(0, previous, width, height)
        else return fallback("last row too short")
        if (rows.size !in 2..MAX_PANELS) return fallback("no confident horizontal rows")
        val detector = Detector(width, height, grayscale)
        if (rows.any { !detector.hasContent(it) }) return fallback("empty row")
        return PanelPlan(rows, false, "validated horizontal rows")
    }

    /**
     * Convert bounded-thumbnail rectangles back to source-image coordinates,
     * adding overlapping guard padding. The extra padding protects border art
     * and balloons near a gutter; it never changes the reading order.
     */
    fun sourceRegions(plan: PanelPlan, thumbWidth: Int, thumbHeight: Int,
        sourceWidth: Int, sourceHeight: Int, paddingPercent: Int = 1): List<PageRegion> {
        require(thumbWidth > 0 && thumbHeight > 0 && sourceWidth > 0 && sourceHeight > 0)
        require(paddingPercent in 0..5)
        if (plan.usedFullPageFallback) return listOf(PageRegion(0, 0, sourceWidth, sourceHeight))
        val padX = (sourceWidth.toLong() * paddingPercent / 100).toInt()
        val padY = (sourceHeight.toLong() * paddingPercent / 100).toInt()
        return plan.regions.map { r ->
            require(r.left in 0 until r.right && r.right <= thumbWidth &&
                r.top in 0 until r.bottom && r.bottom <= thumbHeight)
            PageRegion(
                ((r.left.toLong() * sourceWidth) / thumbWidth).toInt().minus(padX).coerceAtLeast(0),
                ((r.top.toLong() * sourceHeight) / thumbHeight).toInt().minus(padY).coerceAtLeast(0),
                ((r.right.toLong() * sourceWidth + thumbWidth - 1) / thumbWidth).toInt().plus(padX).coerceAtMost(sourceWidth),
                ((r.bottom.toLong() * sourceHeight + thumbHeight - 1) / thumbHeight).toInt().plus(padY).coerceAtMost(sourceHeight),
            )
        }
    }

    private fun coversEntirePage(regions: List<PageRegion>, width: Int, height: Int): Boolean {
        // Geometry-only, no bitmap allocation; accepted regions must partition
        // the entire page without losing even white/low-contrast artwork.
        val area = regions.sumOf { it.width.toLong() * it.height }
        if (area != width.toLong() * height) return false
        return regions.all { it.left >= 0 && it.top >= 0 && it.right <= width && it.bottom <= height } &&
            regions.indices.all { i -> (i + 1 until regions.size).all { j ->
                val a = regions[i]; val b = regions[j]
                minOf(a.right, b.right) <= maxOf(a.left, b.left) ||
                    minOf(a.bottom, b.bottom) <= maxOf(a.top, b.top)
            } }
    }

    private class Detector(private val width: Int, private val height: Int, private val gray: ByteArray) {
        private var leaves = 0
        private var invalid = false

        fun partition(region: PageRegion, depth: Int): PanelReadingOrder.Node? {
            if (invalid || depth > 10) return null
            val horizontal = bestGutter(region, horizontal = true)
            val vertical = bestGutter(region, horizontal = false)
            // Across-page horizontal tiers take priority over vertical columns.
            // This preserves manga's top-to-bottom, then right-to-left order.
            val split = horizontal?.let { true to it } ?: vertical?.let { false to it }
            if (split == null) {
                leaves++
                if (leaves > MAX_PANELS) { invalid = true; return null }
                return PanelReadingOrder.Node.Leaf(region)
            }
            val (isHorizontal, mid) = split
            val first = if (isHorizontal) region.copy(bottom = mid) else region.copy(right = mid)
            val second = if (isHorizontal) region.copy(top = mid) else region.copy(left = mid)
            if (!hasContent(first) || !hasContent(second)) {
                leaves++
                if (leaves > MAX_PANELS) { invalid = true; return null }
                return PanelReadingOrder.Node.Leaf(region)
            }
            val a = partition(first, depth + 1) ?: return null
            val b = partition(second, depth + 1) ?: return null
            return PanelReadingOrder.Node.Split(isHorizontal, a, b)
        }

        private fun bestGutter(r: PageRegion, horizontal: Boolean): Int? {
            val length = if (horizontal) r.height else r.width
            val span = if (horizontal) r.width else r.height
            // Keep a minimum of 15% of the *whole page* on either side.
            val minChild = maxOf(8, (if (horizontal) height else width) * 6 / 100)
            val margin = maxOf(minChild, length * 7 / 100)
            if (length <= margin * 2 + 3 || span < 12) return null
            val minGap = maxOf(2, length / 180)
            var bestStart = -1
            var bestEnd = -1
            var runStart = -1
            val origin = if (horizontal) r.top else r.left
            for (axis in (origin + margin) until (origin + length - margin)) {
                var bright = 0
                if (horizontal) {
                    val offset = axis * width
                    for (x in r.left until r.right) {
                        if ((gray[offset + x].toInt() and 255) >= WHITE) bright++
                    }
                } else {
                    for (y in r.top until r.bottom) {
                        if ((gray[y * width + axis].toInt() and 255) >= WHITE) bright++
                    }
                }
                // Permit a few non-white pixels in real manga gutters without guessing cuts.
                val clear = bright.toLong() * 100 >= span.toLong() * 97
                if (clear && runStart < 0) runStart = axis
                if ((!clear || axis == origin + length - margin - 1) && runStart >= 0) {
                    val end = if (clear) axis + 1 else axis
                    if (end - runStart >= minGap && end - runStart > bestEnd - bestStart) {
                        bestStart = runStart
                        bestEnd = end
                    }
                    runStart = -1
                }
            }
            if (bestStart < 0) return null
            // Find the safest axis *within* the gutter rather than requiring
            // three perfectly white lines at its midpoint. Real scanned manga
            // has compression speckles and antialiasing in otherwise clear gaps.
            // Refuse to cut across a sustained ink stroke, but tolerate isolated
            // near-white pixels instead of collapsing an entire page to fallback.
            fun darkCount(axis: Int): Int {
                var dark = 0
                if (horizontal) {
                    for (x in r.left until r.right) {
                        if ((gray[axis * width + x].toInt() and 255) < INK) dark++
                    }
                } else {
                    for (y in r.top until r.bottom) {
                        if ((gray[y * width + axis].toInt() and 255) < INK) dark++
                    }
                }
                return dark
            }
            val midpoint = (bestStart + bestEnd) / 2
            // A continuous dark stroke across neighbouring candidate axes is
            // likely a balloon tail, not compression noise. Isolated dark
            // speckles are acceptable, but never cut through a sustained line.
            fun crossesStroke(axis: Int): Boolean {
                if (axis <= bestStart || axis + 1 >= bestEnd) return true
                for (cross in 0 until span) {
                    val position = if (horizontal) r.left + cross else r.top + cross
                    fun ink(at: Int): Boolean = if (horizontal)
                        (gray[at * width + position].toInt() and 255) < INK
                    else (gray[position * width + at].toInt() and 255) < INK
                    if (ink(axis - 1) && ink(axis) && ink(axis + 1)) return true
                }
                return false
            }
            val safe = (bestStart + 1 until bestEnd - 1)
                .filter { it - origin >= minChild && origin + length - it >= minChild }
                .filter { darkCount(it).toLong() * 200 <= span.toLong() && !crossesStroke(it) }
            return safe.minWithOrNull(compareBy<Int> { darkCount(it) }
                .thenBy { kotlin.math.abs(it - midpoint) })
        }

        fun hasContent(r: PageRegion): Boolean {
            // Sample every ~2 px at normal thumbnail size. Avoid treating
            // blank/near-white blocks as independent panels.
            val step = maxOf(1, minOf(r.width, r.height) / 100)
            var ink = 0
            var samples = 0
            for (y in r.top until r.bottom step step) {
                for (x in r.left until r.right step step) {
                    samples++
                    if ((gray[y * width + x].toInt() and 255) < INK) ink++
                }
            }
            return samples > 0 && ink * 100 >= samples * 2
        }
    }
}

/** Explicit split tree: top-to-bottom tiers, RTL or LTR within each tier. */
object PanelReadingOrder {
    sealed class Node {
        data class Leaf(val region: PageRegion) : Node()
        data class Split(val horizontal: Boolean, val first: Node, val second: Node) : Node()
    }

    fun flatten(node: Node, rightToLeft: Boolean): List<PageRegion>? {
        // Vertical-first partitions are legitimate manga layouts (e.g. two
        // columns with different internal rows). Keep their panels rather than
        // discarding the entire detection as ambiguous. Traverse each column
        // in reading direction, top-to-bottom within that column.
        fun visit(n: Node): List<PageRegion> = when (n) {
            is Node.Leaf -> listOf(n.region)
            is Node.Split -> if (n.horizontal) visit(n.first) + visit(n.second)
                else if (rightToLeft) visit(n.second) + visit(n.first)
                else visit(n.first) + visit(n.second)
        }
        return visit(node)
    }
}
