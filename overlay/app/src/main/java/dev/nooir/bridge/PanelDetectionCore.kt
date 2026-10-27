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
        // Row detection is authoritative. Each row is independently eligible
        // for finer panel partitioning; a rejected panel split keeps that row.
        // If no row seam exists, try panel detection on the whole page.
        val rows = detector.partitionRows(whole)
        val output = mutableListOf<PageRegion>()
        var recoveredRows = 0
        for ((index, row) in rows.withIndex()) {
            // Reserve one output slot for every remaining detected row.
            // Extra panel candidates must never force a page-wide fallback.
            val available = MAX_PANELS - output.size - (rows.size - index - 1)
            if (available < 2) {
                output.add(row)
                continue
            }
            val rowDetector = Detector(width, height, grayscale)
            val node = rowDetector.partition(row, 0)
            val candidates = node?.let { PanelReadingOrder.flatten(it, rightToLeft) }
            val valid = candidates != null && candidates.size in 2..available &&
                candidates.all { it.width > 0 && it.height > 0 &&
                    rowDetector.hasContent(it) && !rowDetector.isLikelyBorderFragment(it) } &&
                candidates.none { it.width.toLong() * it.height * 100 <
                    row.width.toLong() * row.height * 2 } &&
                // A hairline slice can pass the area threshold on a wide row.
                // Preserve that row instead of exporting isolated lettering or borders.
                candidates.none { it.width.toLong() * 100 < row.width.toLong() * 8 ||
                    it.height.toLong() * 100 < row.height.toLong() * 8 } &&
                !(candidates.size > 12 && candidates.count {
                    it.width.toLong() * it.height * 100 <
                        row.width.toLong() * row.height * 5
                } >= 4) &&
                coversRegion(candidates, row)
            if (valid) {
                output.addAll(candidates!!)
            } else {
                // The normal recursive candidate tree may over-split one small
                // region and be rejected as a whole. Only *after* that rejection,
                // retry independent vertical seams inside this exact row. A
                // failed retry leaves the existing grouped-row fallback intact.
                val recovered = rowDetector.retryRejectedRowColumns(
                    row, available, rightToLeft,
                )
                if (recovered != null) {
                    output.addAll(recovered)
                    recoveredRows++
                } else output.add(row)
            }
        }
        if (output.size !in 2..MAX_PANELS || !coversEntirePage(output, width, height)) {
            return fallback("no validated rows or panels")
        }
        val reason = if (recoveredRows > 0)
            "row-first partition with rejected-row column recovery"
        else "row-first partition with per-row panel fallback"
        return PanelPlan(output, false, reason)
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
        val detector = Detector(width, height, grayscale)
        val rows = detector.partitionRows(whole)
        if (rows.size !in 2..MAX_PANELS) return fallback("no confident horizontal rows")
        if (rows.any { !detector.hasContent(it) }) return fallback("empty row")
        if (!coversEntirePage(rows, width, height)) return fallback("unsafe row coverage")
        return PanelPlan(rows, false, "validated horizontal white-strip rows")
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
        // Extend both sides of interior cuts to retain border-adjacent dialogue.
        val sharedX = maxOf(2, sourceWidth / 80)
        val sharedY = maxOf(2, sourceHeight / 80)
        return plan.regions.map { r ->
            require(r.left in 0 until r.right && r.right <= thumbWidth &&
                r.top in 0 until r.bottom && r.bottom <= thumbHeight)
            PageRegion(
                ((r.left.toLong() * sourceWidth) / thumbWidth).toInt().minus(if (r.left > 0) maxOf(padX, sharedX) else 0).coerceAtLeast(0),
                ((r.top.toLong() * sourceHeight) / thumbHeight).toInt().minus(if (r.top > 0) maxOf(padY, sharedY) else 0).coerceAtLeast(0),
                ((r.right.toLong() * sourceWidth + thumbWidth - 1) / thumbWidth).toInt().plus(if (r.right < thumbWidth) maxOf(padX, sharedX) else 0).coerceAtMost(sourceWidth),
                ((r.bottom.toLong() * sourceHeight + thumbHeight - 1) / thumbHeight).toInt().plus(if (r.bottom < thumbHeight) maxOf(padY, sharedY) else 0).coerceAtMost(sourceHeight),
            )
        }
    }

    private fun coversRegion(regions: List<PageRegion>, parent: PageRegion): Boolean {
        val area = regions.sumOf { it.width.toLong() * it.height }
        if (area != parent.width.toLong() * parent.height) return false
        return regions.all { it.left >= parent.left && it.top >= parent.top &&
            it.right <= parent.right && it.bottom <= parent.bottom } &&
            regions.indices.all { i -> (i + 1 until regions.size).all { j ->
                val a = regions[i]; val b = regions[j]
                minOf(a.right, b.right) <= maxOf(a.left, b.left) ||
                    minOf(a.bottom, b.bottom) <= maxOf(a.top, b.top)
            } }
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

        /**
         * Row mode deliberately never divides columns. Reuse the same guarded
         * white-strip detector as panel mode, but scan each remaining row
         * independently so short/uneven horizontal gaps are not missed.
         */
        fun hasCleanFullHeightColumn(region: PageRegion): Boolean {
            val axis = bestGutter(region, horizontal = false) ?: return false
            return hasContent(region.copy(right = axis)) &&
                hasContent(region.copy(left = axis))
        }

        /**
         * A *second chance* exclusively for rejected Panel by Panel results.
         *
         * The main recursive detector can reject an entire row if a single
         * proposed child is a tiny border/text fragment. Try vertical columns
         * without recursively splitting their interior artwork into more rows.
         * Only keep a cut when BOTH columns are substantial, contain art spread
         * through their height, and the proposed seam is near-white. Otherwise
         * return null and leave the row grouped, exactly as before.
         *
         * detectRows() never calls this function.
         */
        fun retryRejectedRowColumns(
            row: PageRegion, maximum: Int, rightToLeft: Boolean,
        ): List<PageRegion>? {
            if (maximum < 2 || row.width < 72 || row.height < 40) return null

            fun likelyIllustratedColumn(r: PageRegion): Boolean {
                // Keep conservative dimensions so individual letters, decorative
                // rules and narrow white edge fragments cannot become frames.
                if (r.width.toLong() * 100 < row.width.toLong() * 18 ||
                    r.width.toLong() * r.height * 100 <
                    row.width.toLong() * row.height * 8) return false
                if (!hasContent(r) || isLikelyBorderFragment(r)) return false

                // A one-line caption can have dark pixels on both sides of
                // several vertical word spaces; that does not make it a manga
                // panel. Require ink distributed vertically over the candidate.
                val dx = maxOf(1, r.width / 96)
                val dy = maxOf(1, r.height / 180)
                var first = r.bottom
                var last = r.top
                for (y in r.top until r.bottom step dy) {
                    var ink = 0
                    for (x in r.left until r.right step dx)
                        if ((gray[y * width + x].toInt() and 255) < INK) ink++
                    if (ink >= 3) {
                        first = minOf(first, y)
                        last = maxOf(last, y)
                    }
                }
                return first < last && (last - first).toLong() * 100 >=
                    r.height.toLong() * 48
            }

            fun safeVerticalSeam(r: PageRegion, axis: Int): Boolean {
                if (axis <= r.left + 2 || axis >= r.right - 2) return false
                val edgeBand = maxOf(3, r.height / 16)
                // A horizontal border running across BOTH panels may cross an
                // otherwise completely white vertical gutter right at the top
                // or bottom of a tier. Those pixels are guarded by source-region
                // overlap. Do not mistake an interior balloon tail for a frame.
                val frameRuleCache = IntArray(r.height) { -1 }
                fun fullWidthFrameRule(y: Int): Boolean {
                    val relativeY = y - r.top
                    if (relativeY >= edgeBand && relativeY < r.height - edgeBand) return false
                    if (frameRuleCache[relativeY] >= 0)
                        return frameRuleCache[relativeY] == 1
                    var dark = 0
                    for (x in r.left until r.right)
                        if ((gray[y * width + x].toInt() and 255) < INK) dark++
                    val rule = dark.toLong() * 100 >= r.width.toLong() * 55
                    frameRuleCache[relativeY] = if (rule) 1 else 0
                    return rule
                }
                var inkOnAxis = 0
                for (y in r.top until r.bottom) {
                    fun value(x: Int) = gray[y * width + x].toInt() and 255
                    val center = value(axis)
                    if (center < 235) inkOnAxis++
                    // Retain the strict veto everywhere except an actual
                    // near-edge, full-width frame rule. Isolated dark specks,
                    // captions and thin balloon tails still veto the split.
                    val allowedFrame = center < 220 && fullWidthFrameRule(y)
                    if (center < 220 && !allowedFrame) return false
                    if (!allowedFrame && value(axis - 1) < 180 &&
                        center < 180 && value(axis + 1) < 180) return false
                }
                // Border exceptions are sparse; sustained artwork occupying
                // the divider is still unsafe even near the outer edges.
                return inkOnAxis.toLong() * 100 <= r.height.toLong() * 2
            }

            val columns = mutableListOf(row)
            while (columns.size < maximum) {
                var changed = false
                for (i in columns.indices) {
                    val current = columns[i]
                    if (current.width < 72 || current.height < 40) continue
                    // Expensive imperfect-gutter searches run only when
                    // the preceding, more reliable proposal is rejected.
                    val options = sequence {
                        val clean = bestGutter(current, horizontal = false)
                        if (clean != null) yield(clean)
                        val thin = thinLineGutter(current, horizontal = false)
                        if (thin != null) yield(thin)
                        val partial = partialVerticalGutter(current)
                        if (partial != null) yield(partial)
                        val adaptive = adaptiveGutter(current, horizontal = false)
                        if (adaptive != null) yield(adaptive)
                    }.distinct()
                    val axis = options.firstOrNull { cut ->
                        safeVerticalSeam(current, cut) &&
                            likelyIllustratedColumn(current.copy(right = cut)) &&
                            likelyIllustratedColumn(current.copy(left = cut))
                    } ?: continue
                    columns.removeAt(i)
                    columns.add(i, current.copy(right = axis))
                    columns.add(i + 1, current.copy(left = axis))
                    changed = true
                    break
                }
                if (!changed) break
            }
            // The exact rectangles cover the rejected parent, without
            // cropping or dropping source pixels. Reading order is per-row.
            return columns.takeIf { it.size in 2..maximum }?.let {
                if (rightToLeft) it.reversed() else it
            }
        }

        fun partitionRows(whole: PageRegion): List<PageRegion> {
            val rows = mutableListOf<PageRegion>()
            fun visit(region: PageRegion, depth: Int) {
                if (depth >= 10 || rows.size >= MAX_PANELS) {
                    rows += region
                    return
                }
                // A tempting blank page margin can outrank the real row seam.
                // Validate each candidate before trying the next detector.
                fun valid(axis: Int?): Int? {
                    if (axis == null) return null
                    val upper = region.copy(bottom = axis)
                    val lower = region.copy(top = axis)
                    return axis.takeIf { hasContent(upper) && hasContent(lower) }
                }
                val axis = valid(bestGutter(region, horizontal = true))
                    ?: valid(thinLineGutter(region, horizontal = true))
                    ?: valid(adaptiveGutter(region, horizontal = true))
                if (axis == null) {
                    rows += region
                    return
                }
                val upper = region.copy(bottom = axis)
                val lower = region.copy(top = axis)
                visit(upper, depth + 1)
                visit(lower, depth + 1)
            }
            visit(whole, 0)
            return rows
        }

        fun partition(region: PageRegion, depth: Int): PanelReadingOrder.Node? {
            if (invalid || depth > 10) return null
            // Prefer a genuinely clear gutter in either direction before
            // considering imperfect corridors. Previously an approximate
            // horizontal split could outrank a clean vertical column boundary,
            // leaving multi-column manga grouped or in the wrong hierarchy.
            val cleanHorizontal = bestGutter(region, horizontal = true)
            val cleanVertical = bestGutter(region, horizontal = false)
            val thinHorizontal = if (cleanHorizontal == null) thinLineGutter(region, true) else null
            val thinVertical = if (cleanVertical == null) thinLineGutter(region, false) else null
            // A clear vertical separator is stronger evidence of distinct
            // columns than an imperfect horizontal band (often just whitespace
            // inside a panel). For imperfect gutters in both directions,
            // compare ink contrast instead of always choosing a row first.
            val adaptiveHorizontal = if (cleanHorizontal == null && cleanVertical == null)
                adaptiveGutterCandidate(region, horizontal = true) else null
            val adaptiveVertical = if (cleanHorizontal == null && cleanVertical == null)
                adaptiveGutterCandidate(region, horizontal = false) else null
            // A tempting seam at the outer artwork margin may leave a blank
            // child. Validate each candidate before it can hide a real gutter.
            fun viable(horizontal: Boolean, axis: Int): Boolean {
                val first = if (horizontal) region.copy(bottom = axis) else region.copy(right = axis)
                val second = if (horizontal) region.copy(top = axis) else region.copy(left = axis)
                return hasContent(first) && hasContent(second)
            }
            // Prefer rows when a horizontal seam is trustworthy. A full-height
            // clean column wins over a merely thin/approximate row seam so tall
            // panels spanning multiple tiers remain intact.
            val split = cleanHorizontal?.takeIf { viable(true, it) }?.let { true to it }
                ?: cleanVertical?.takeIf { viable(false, it) }?.let { false to it }
                ?: thinHorizontal?.takeIf { viable(true, it) }?.let { true to it }
                ?: thinVertical?.takeIf { viable(false, it) }?.let { false to it }
                ?: when {
                    // Try a validated thin vertical strip before an uncertain
                    // horizontal band; otherwise panel mode behaves like rows.
                    partialVerticalGutter(region) != null -> false to partialVerticalGutter(region)!!
                    adaptiveHorizontal == null && adaptiveVertical == null -> null
                    adaptiveHorizontal == null -> adaptiveVertical?.let { false to it.first }
                    adaptiveVertical == null -> adaptiveHorizontal.let { true to it.first }
                    adaptiveVertical.second > adaptiveHorizontal.second -> false to adaptiveVertical.first
                    else -> true to adaptiveHorizontal.first
                }
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

        /**
         * Detect narrow vertical panel gutters whose upper/lower ends are
         * interrupted by panel borders or page decoration. This is only used
         * after the full-span detectors fail, and never in row-only mode.
         * Requiring a bright central corridor plus dark content on both sides
         * avoids treating blank page margins as columns.
         */
        private fun partialVerticalGutter(r: PageRegion): Int? {
            if (r.width < 72 || r.height < 48) return null
            val margin = maxOf(8, r.width * 5 / 100)
            val top = r.top + r.height / 8
            val bottom = r.bottom - r.height / 8
            if (bottom <= top) return null
            val band = maxOf(0, r.width / 500)
            fun darkness(x: Int): Int {
                var dark = 0
                var count = 0
                for (y in top until bottom step 2) {
                    if ((gray[y * width + x].toInt() and 255) < 235) dark++
                    count++
                }
                return if (count == 0) 100 else dark * 100 / count
            }
            var best: Int? = null
            var scoreBest = 0
            for (x in r.left + margin until r.right - margin) {
                var corridor = 0
                for (dx in -band..band) corridor += darkness(x + dx)
                corridor /= (band * 2 + 1)
                if (corridor > 7) continue
                val reach = maxOf(5, r.width / 10)
                if (x - reach <= r.left || x + reach >= r.right) continue
                val leftInk = maxOf(darkness(x - reach), darkness(x - maxOf(4, reach / 2)))
                val rightInk = maxOf(darkness(x + reach), darkness(x + reach / 2))
                if (leftInk < 14 || rightInk < 14) continue
                // A real gutter should remain mostly empty over the entire
                // tier. Reject cuts crossing substantial artwork at its ends.
                var fullInk = 0
                for (y in r.top until r.bottom) {
                    if ((gray[y * width + x].toInt() and 255) < 220) fullInk++
                }
                if (fullInk * 100 > r.height * 10) continue
                // Reject a continuous balloon tail or drawn stroke crossing
                // the proposed separator, even when the rest is bright.
                val guard = maxOf(5, r.width / 35)
                var crossing = false
                for (y in r.top until r.bottom step 2) {
                    var run = 0
                    for (dx in -guard..guard) {
                        if ((gray[y * width + x + dx].toInt() and 255) < 180) {
                            run++
                            if (run >= guard + 2) { crossing = true; break }
                        } else run = 0
                    }
                    if (crossing) break
                }
                if (crossing) continue
                val score = minOf(leftInk, rightInk) - corridor
                if (score > scoreBest) {
                    best = x
                    scoreBest = score
                }
            }
            return best
        }

        /**
         * Detect a single long, narrow light seam even when it has no broad
         * white band. Both row and panel modes share this guarded detector.
         * A seam needs dark content on both sides, not just a white page margin.
         */
        private fun thinLineGutter(r: PageRegion, horizontal: Boolean): Int? {
            val length = if (horizontal) r.height else r.width
            val span = if (horizontal) r.width else r.height
            val origin = if (horizontal) r.top else r.left
            val minChild = maxOf(8, (if (horizontal) height else width) * 3 / 100)
            if (length < minChild * 2 + 6 || span < 28) return null
            val step = maxOf(1, span / 350)
            fun darkPercent(axis: Int, threshold: Int): Int {
                var dark = 0
                var total = 0
                var cross = if (horizontal) r.left else r.top
                val limit = if (horizontal) r.right else r.bottom
                while (cross < limit) {
                    val index = if (horizontal) axis * width + cross else cross * width + axis
                    if ((gray[index].toInt() and 255) < threshold) dark++
                    total++
                    cross += step
                }
                return dark * 100 / maxOf(1, total)
            }
            var best: Int? = null
            var bestScore = 0
            val reach = maxOf(4, length / 14)
            for (axis in origin + minChild until origin + length - minChild) {
                // Strong evidence of a long pale seam; tolerate minor ink noise.
                if (darkPercent(axis, 240) > 6 || darkPercent(axis, 180) > 2) continue
                if (axis - reach <= origin || axis + reach >= origin + length) continue
                val before = maxOf(darkPercent(axis - reach, 220),
                    darkPercent(axis - maxOf(2, reach / 2), 220))
                val after = maxOf(darkPercent(axis + reach, 220),
                    darkPercent(axis + maxOf(2, reach / 2), 220))
                if (before < 18 || after < 18) continue
                // A line crossing the seam is potentially a balloon or art.
                // Don't split where an extended ink stroke bridges both sides.
                var bridges = 0
                var longestBridge = 0
                var consecutiveBridges = 0
                var samples = 0
                val radius = maxOf(3, length / 55)
                var cross = if (horizontal) r.left else r.top
                val limit = if (horizontal) r.right else r.bottom
                while (cross < limit) {
                    var run = 0
                    var bridged = false
                    for (offset in -radius..radius) {
                        val pos = axis + offset
                        val index = if (horizontal) pos * width + cross else cross * width + pos
                        if ((gray[index].toInt() and 255) < 180) {
                            run++
                            if (run >= radius + 2) { bridged = true; break }
                        } else run = 0
                    }
                    samples++
                    if (bridged) {
                        bridges++
                        consecutiveBridges++
                        longestBridge = maxOf(longestBridge, consecutiveBridges)
                    } else consecutiveBridges = 0
                    cross += step
                }
                // Short border interruptions are common in scanned manga.
                // Reject broad or sustained bridges, which may be dialogue/art.
                if (bridges * 100 > samples * 3 ||
                    longestBridge * 100 > samples * 2) continue
                val score = minOf(before, after) - darkPercent(axis, 240)
                if (score > bestScore) { bestScore = score; best = axis }
            }
            return best
        }

        private fun bestGutter(r: PageRegion, horizontal: Boolean): Int? {
            val length = if (horizontal) r.height else r.width
            val span = if (horizontal) r.width else r.height
            // Keep a minimum of 15% of the *whole page* on either side.
            val minChild = maxOf(8, (if (horizontal) height else width) * 3 / 100)
            val margin = maxOf(minChild, length * 3 / 100)
            if (length <= margin * 2 + 3 || span < 12) return null
            val minGap = 1 // one-pixel separators are valid at thumbnail resolution
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
                if (axis <= origin || axis + 1 >= origin + length) return true
                for (cross in 0 until span) {
                    val position = if (horizontal) r.left + cross else r.top + cross
                    fun ink(at: Int): Boolean = if (horizontal)
                        (gray[at * width + position].toInt() and 255) < INK
                    else (gray[position * width + at].toInt() and 255) < INK
                    if (ink(axis - 1) && ink(axis) && ink(axis + 1)) return true
                }
                return false
            }
            val safe = (bestStart until bestEnd)
                .filter { it - origin >= minChild && origin + length - it >= minChild }
                .filter { darkCount(it).toLong() * 200 <= span.toLong() && !crossesStroke(it) }
            return safe.minWithOrNull(compareBy<Int> { darkCount(it) }
                .thenBy { kotlin.math.abs(it - midpoint) })
        }

        /**
         * Secondary detector for imperfect/broken gutters. A narrow light corridor
         * can be interrupted by borders or balloon tails. Require the candidate
         * corridor to be substantially lighter than the artwork on BOTH sides;
         * otherwise do not speculate. Recursive partitioning examines children.
         */
        private fun adaptiveGutter(r: PageRegion, horizontal: Boolean): Int? =
            adaptiveGutterCandidate(r, horizontal)?.first

        private fun adaptiveGutterCandidate(r: PageRegion, horizontal: Boolean): Pair<Int, Int>? {
            val length = if (horizontal) r.height else r.width
            val span = if (horizontal) r.width else r.height
            val minChild = maxOf(8, (if (horizontal) height else width) * 3 / 100)
            if (length < minChild * 2 + 8 || span < 24) return null
            val origin = if (horizontal) r.top else r.left
            val step = maxOf(1, span / 300)
            fun inkRatio(axis: Int): Int {
                var dark = 0
                var total = 0
                var cross = if (horizontal) r.left else r.top
                val limit = if (horizontal) r.right else r.bottom
                while (cross < limit) {
                    val index = if (horizontal) axis * width + cross else cross * width + axis
                    if ((gray[index].toInt() and 255) < 235) dark++
                    total++
                    cross += step
                }
                return if (total == 0) 100 else dark * 100 / total
            }
            val window = maxOf(1, length / 320)
            val flank = maxOf(window * 3, length / 18)
            var bestAxis: Int? = null
            var bestScore = 0
            for (axis in origin + minChild + flank until origin + length - minChild - flank) {
                var corridor = 0
                for (offset in -window..window) corridor += inkRatio(axis + offset)
                corridor /= (window * 2 + 1)
                if (corridor > 12) continue
                // Reject continuous dark strokes crossing the proposed cut.
                // A speech-balloon tail can be only one pixel thick along the
                // gutter but dozens of pixels long ACROSS it. Density-based
                // checks miss it, whereas isolated scanner speckles remain safe.
                val bridgeRadius = maxOf(5, length / 35)
                val crossStart = if (horizontal) r.left else r.top
                val crossEnd = if (horizontal) r.right else r.bottom
                var blocked = false
                for (cross in crossStart until crossEnd step step) {
                    var longestRun = 0
                    var run = 0
                    for (position in axis - bridgeRadius..axis + bridgeRadius) {
                        val index = if (horizontal) position * width + cross else cross * width + position
                        if ((gray[index].toInt() and 255) < 180) {
                            run++
                            longestRun = maxOf(longestRun, run)
                        } else run = 0
                    }
                    if (longestRun >= bridgeRadius + 2) {
                        blocked = true
                        break
                    }
                }
                if (blocked) continue
                // Search multiple distances: nested panels often have broad
                // white margins around their frames, hiding ink at one flank.
                val maxReach = minOf(length / 3, minOf(axis - origin - 1, origin + length - axis - 1))
                if (maxReach < flank) continue
                val distances = listOf(flank, maxOf(flank, length / 9),
                    maxOf(flank, length / 6)).filter { it <= maxReach }
                val before = distances.maxOf { distance ->
                    (inkRatio(axis - distance) + inkRatio(axis - distance / 2)) / 2
                }
                val after = distances.maxOf { distance ->
                    (inkRatio(axis + distance) + inkRatio(axis + distance / 2)) / 2
                }
                if (before < 14 || after < 14) continue
                val score = minOf(before, after) - corridor
                if (score > bestScore && score >= 12) {
                    bestScore = score
                    bestAxis = axis
                }
            }
            return bestAxis?.let { it to bestScore }
        }

        /**
         * Reject an isolated thin rule masquerading as a panel. A sparse title
         * or logo can still have meaningful shapes, so only reject regions
         * where nearly all dark pixels occupy a very thin horizontal/vertical
         * band. This applies to Panel by Panel only, after partitioning.
         */
        fun isLikelyBorderFragment(r: PageRegion): Boolean {
            if (r.width < 12 || r.height < 12) return false
            val step = maxOf(1, minOf(r.width, r.height) / 180)
            val cols = IntArray((r.width + step - 1) / step)
            val rows = IntArray((r.height + step - 1) / step)
            var ink = 0
            for (iy in rows.indices) for (ix in cols.indices) {
                val pixel = gray[(r.top + iy * step) * width + r.left + ix * step].toInt() and 255
                if (pixel < INK) { ink++; cols[ix]++; rows[iy]++ }
            }
            val total = cols.size * rows.size
            if (ink == 0 || ink * 100 >= total * 7) return false
            // Concentrated single straight stroke, not a sparse illustration.
            val vertical = cols.maxOrNull() ?: 0
            val horizontal = rows.maxOrNull() ?: 0
            return vertical * 100 >= ink * 85 || horizontal * 100 >= ink * 85
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
