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
    // One successfully absorbed terminal ruled strip yields a full source
    // rectangle. The renderer must not then run an independent corridor
    // slicer over it and recreate the same unwanted extra reading screen.
    val preserveSingleFrame: Boolean = false,
)

/** Optional, bounded diagnostic event. No image data or titles are carried here. */
data class PanelDebugEvent(
    val stage: String,
    val region: PageRegion,
    val decision: String,
    val detail: String,
)

object PanelDetectionCore {
    private const val MAX_THUMB_PIXELS = 1_048_576
    private const val MAX_PANELS = 32
    private const val WHITE = 247
    private const val INK = 220

    /**
     * OCR-free fail-safe for a source image with repeated short text-like
     * horizontal bands. Clean paragraph spacing can otherwise look like
     * comic gutters and a row-first + column retry multiplies one prose
     * page into dozens of tiny zoomed fragments.
     *
     * This is intentionally VERY narrow: at least 18 separate narrow ink
     * bands in the central page and at most two tall illustrated bands.
     * Keeping the whole image cannot discard pixels, and the preserve flag
     * also disables the downstream legacy corridor slicer.
     *
     * Never apply this to Row by Row mode or to ordinary comics where large
     * illustrated blocks occupy several vertical rows.
     */
    private fun looksLikeTextDominantPage(width: Int, height: Int, gray: ByteArray): Boolean {
        if (height < 420 || width < 260) return false
        val left = width * 8 / 100
        val right = width * 92 / 100
        val top = height * 9 / 100
        val bottom = height * 94 / 100
        val span = right - left
        if (span < 40) return false
        val shortMin = maxOf(2, height * 3 / 900)
        val shortMax = maxOf(12, height * 22 / 900)
        var runStart = -1
        var narrowBands = 0
        var tallBands = 0
        for (y in top..bottom) {
            // A row with >3.5% black-ish ink in the central 84% width.
            // Integer threshold avoids floating-point work on the X4
            // conversion pipeline; no bitmap/array allocation per row.
            var ink = 0
            if (y < bottom) {
                val base = y * width
                for (x in left until right) {
                    if ((gray[base + x].toInt() and 255) < 160) ink++
                }
            }
            val occupied = y < bottom && ink * 1000 > span * 35
            if (occupied) {
                if (runStart < 0) runStart = y
            } else if (runStart >= 0) {
                val length = y - runStart
                if (length in shortMin..shortMax) narrowBands++
                else if (length > shortMax) tallBands++
                // No need to inspect the remaining pixels once it can no
                // longer be a text-dominant/low-illustration page.
                if (tallBands > 2) return false
                runStart = -1
            }
        }
        return narrowBands >= 18 && tallBands <= 2
    }

    fun detect(width: Int, height: Int, grayscale: ByteArray, rightToLeft: Boolean,
        trace: ((PanelDebugEvent) -> Unit)? = null): PanelPlan {
        require(width > 0 && height > 0 && width.toLong() * height <= MAX_THUMB_PIXELS) {
            "Panel detection needs a bounded, nonempty thumbnail"
        }
        require(grayscale.size == width * height) { "Grayscale thumbnail dimensions do not match" }
        val whole = PageRegion(0, 0, width, height)
        fun fallback(why: String): PanelPlan {
            trace?.invoke(PanelDebugEvent("page", whole, "fallback", why))
            return PanelPlan(listOf(whole), true, why)
        }
        // No OCR means we cannot prove that a low-ink region has no lettering.
        // Only trust clear rectangular gutters; uncertain pages stay intact.
        if (height.toLong() > width.toLong() * 3L) return fallback("long strip: preserve existing webtoon slicing")
        if (looksLikeTextDominantPage(width, height, grayscale)) {
            trace?.invoke(PanelDebugEvent("page", whole, "keep",
                "repeated text-like horizontal bands; do not fragment prose into comic panels"))
            return PanelPlan(listOf(whole), false,
                "text-dominant source kept intact", preserveSingleFrame = true)
        }
        // Wide strips can contain multiple framed panels; let the same guarded gutter detector decide.
        val detector = Detector(width, height, grayscale, trace)
        // Row detection is authoritative, including conservatively verified
        // pale-gray and dark HORIZONTAL separators. Try columns only INSIDE
        // each resulting row; if a row cannot be split safely, retain it.
        // The same recovery validators are already used by Row by Row.
        val rows = detector.partitionRows(whole, allowPaleGrayRecovery = true,
            allowDarkRecovery = true)
        val output = mutableListOf<PageRegion>()
        var recoveredRows = 0
        // The existing short-footer path can also collapse two rows to one.
        // Remember that decision so the later page-count guard doesn't
        // accidentally return the legacy free-form slicer fallback.
        var absorbedShortFooter = false
        for ((index, row) in rows.withIndex()) {
            // Reserve one output slot for every remaining detected row.
            // Extra panel candidates must never force a page-wide fallback.
            val available = MAX_PANELS - output.size - (rows.size - index - 1)
            if (available < 2) {
                trace?.invoke(PanelDebugEvent("tier", row, "keep", "panel limit"))
                output.add(row)
                continue
            }
            val rowDetector = Detector(width, height, grayscale, trace)
            // Conan source 21: a shallow, almost uninked FINAL tier may be
            // merely the foot of the illustrated row above it. Do not promote
            // its vertical rule fragments into independent rotated pages.
            // When the previous tier is a full-width partition of stable
            // side-by-side columns, extend those columns through the footer.
            // This conserves EVERY source pixel; otherwise keep the tier whole.
            if (index == rows.lastIndex && index > 0 &&
                row.bottom == height && rowDetector.isShallowUnframedTier(row) &&
                rowDetector.isLowInkFooter(row)) {
                val previousRow = rows[index - 1]
                val adjacent = output.indices.filter { ix ->
                    val r = output[ix]
                    r.top == previousRow.top && r.bottom == row.top
                }.sortedBy { output[it].left }
                val joinsSafely = adjacent.size in 1..3 &&
                    output[adjacent.first()].left == 0 &&
                    output[adjacent.last()].right == width &&
                    adjacent.zipWithNext().all { (a, b) ->
                        output[a].right == output[b].left
                    }
                if (joinsSafely) {
                    for (ix in adjacent) output[ix] = output[ix].copy(bottom = row.bottom)
                    absorbedShortFooter = true
                    trace?.invoke(PanelDebugEvent("tier", row, "absorb",
                        "terminal ruled footer retained inside preceding illustrated tier"))
                } else {
                    output.add(row)
                    trace?.invoke(PanelDebugEvent("tier", row, "keep",
                        "terminal ruled footer has no safe adjacent full-width partition"))
                }
                continue
            }
            // Sparse terminal footers should be considered for safe absorption
            // BEFORE this generic no-split rule. Otherwise a nearly blank
            // footer is preserved as its own unnecessary reading frame.
            // A whole detected horizontal tier can consist of just a page
            // frame, a few decorative rules, or sparse lettering. Recursively
            // splitting that tier turns thin strokes into standalone viewports
            // and can multiply a single border into several almost-white
            // screens. Preserve the entire tier, including all its pixels;
            // this guard belongs ONLY to Panel by Panel, not detectRows().
            if (rowDetector.isSparseRow(row)) {
                trace?.invoke(PanelDebugEvent("tier", row, "keep", "sparse thumbnail tier"))
                output.add(row)
                continue
            }
            val node = rowDetector.partition(row, 0)
            val candidates = node?.let { PanelReadingOrder.flatten(it, rightToLeft) }
            // Conan source 66: the multi-word title row generated five tiny
            // "panels". A shallow, UNFRAMED top-band with 3+ recursive pieces
            // has insufficient visual evidence to classify word groups as
            // illustrated panels. Keep the band intact; retain real art below.
            if (row.bottom.toLong() * 4 <= height.toLong() &&
                rowDetector.isShallowUnframedTier(row) &&
                candidates != null && candidates.size >= 3) {
                output.add(row)
                trace?.invoke(PanelDebugEvent("tier", row, "keep",
                    "shallow unframed headline candidate fragments"))
                continue
            }
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
                trace?.invoke(PanelDebugEvent("tier", row, "accept",
                    "validated ${candidates!!.size} panel candidates"))
                output.addAll(candidates!!)
            } else {
                // Don't spend time inspecting rejected candidates when the
                // user has not explicitly enabled diagnostics.
                if (trace != null) {
                    val rejected = when {
                        candidates == null -> "recursive partition unavailable"
                        candidates.size !in 2..available -> "candidate count outside permitted range"
                        candidates.any { !rowDetector.hasContent(it) } -> "candidate has insufficient ink"
                        candidates.any { rowDetector.isLikelyBorderFragment(it) } -> "candidate resembles border or text stroke"
                        candidates.any { it.width.toLong() * it.height * 100 <
                            row.width.toLong() * row.height * 2 } -> "candidate below minimum area"
                        candidates.any { it.width.toLong() * 100 < row.width.toLong() * 8 ||
                            it.height.toLong() * 100 < row.height.toLong() * 8 } -> "candidate too narrow or short"
                        candidates.size > 12 -> "excessive small candidate fragments"
                        else -> "candidate coverage invalid"
                    }
                    trace.invoke(PanelDebugEvent("tier", row, "reject", rejected))
                    }
                // The normal recursive candidate tree may over-split one small
                // region and be rejected as a whole. Only *after* that rejection,
                // retry independent vertical seams inside this exact row. A
                // failed retry leaves the existing grouped-row fallback intact.
                val recovered = rowDetector.retryRejectedRowColumns(
                    row, available, rightToLeft,
                ) ?: rowDetector.recoverDarkGutter(row, horizontal = false)?.let { axis ->
                    val left = row.copy(right = axis)
                    val right = row.copy(left = axis)
                    if (rightToLeft) listOf(right, left) else listOf(left, right)
                }
                if (recovered != null) {
                    trace?.invoke(PanelDebugEvent("tier", row, "recover",
                        "accepted ${recovered.size} substantial columns in reading order"))
                    output.addAll(recovered)
                    recoveredRows++
                } else {
                    trace?.invoke(PanelDebugEvent("tier", row, "keep",
                        "no safe alternative column split"))
                    output.add(row)
                }
            }
        }
        // The usual shallow unframed footer absorption above is intentionally
        // strict. A somewhat TALLER terminal strip made of almost nothing but
        // a border may still escape it and become an entire near-white zoom
        // frame. Fold it back ONLY when its immediate predecessor is one
        // illustrated full-width row. Never cross a source-page boundary,
        // merge a title above the story, or duplicate the same footer across
        // independently ordered side-by-side panels.
        var absorbedRuledTail = false
        if (rows.size >= 2 && output.size >= 2) {
            val tail = output.last()
            val before = output[output.lastIndex - 1]
            val precedingRow = rows[rows.lastIndex - 1]
            if (tail == rows.last() &&
                tail.left == 0 && tail.right == width && tail.bottom == height &&
                before.left == 0 && before.right == width &&
                before.top == precedingRow.top && before.bottom == tail.top &&
                !detector.isSparseRow(before) &&
                detector.isVerySparseRuledTail(tail)) {
                output[output.lastIndex - 1] = before.copy(bottom = tail.bottom)
                output.removeAt(output.lastIndex)
                absorbedRuledTail = true
                trace?.invoke(PanelDebugEvent("tier", tail, "absorb",
                    "near-empty terminal border attached to full-width illustrated row"))
            }
        }
        // The two-row case now legitimately produces ONE complete rectangle.
        // Returning a generic fallback would send it back through the legacy
        // free-form slicer and recreate the unwanted near-empty views.
        if ((absorbedRuledTail || absorbedShortFooter) && output.size == 1 &&
            coversEntirePage(output, width, height)) {
            return PanelPlan(output, false, "illustrated row with absorbed terminal border",
                preserveSingleFrame = true)
        }
        if (output.size !in 2..MAX_PANELS || !coversEntirePage(output, width, height)) {
            // Preserve all v1.0.16 row-first, subtitle, title and recovery
            // guards. Only a fully unsplit non-sparse page gets the new path.
            if (output.size == 1 && rows.size == 1 && !detector.isSparseRow(whole)) {
                val recovered = recoverSlantedPaleGutter(width, height, grayscale, rightToLeft)
                if (recovered != null) {
                    val why = "two safe overlapping rectangular views along a slanted pale gutter"
                    trace?.invoke(PanelDebugEvent("page", whole, "accept", why))
                    return PanelPlan(recovered, false, why)
                }
            }
            // Dark *straight* horizontal gutters may separate independent rows.
            // Unlike black frame lines, these need strongly supported artwork
            // on both sides. Never treat a dark strip as proof by itself.
            if (output.size == 1 && rows.size == 1) {
                val axis = detector.recoverDarkGutter(whole, horizontal = true)
                if (axis != null) {
                    val why = "two illustrated rows divided by a validated dark gutter"
                    trace?.invoke(PanelDebugEvent("page", whole, "accept", why))
                    return PanelPlan(
                        listOf(whole.copy(bottom = axis), whole.copy(top = axis)),
                        false, why
                    )
                }
            }
            // One Piece frame 884: rows can be bounded by two *slanted*
            // horizontal frame rails. The normal horizontal row search is
            // intentionally strict and must not invent a flat cut through
            // their artwork. Only recover if all previous detectors failed.
            if (output.size == 1 && rows.size == 1 && !detector.isSparseRow(whole)) {
                val angled = recoverSlantedHorizontalRails(width, height, grayscale)
                if (angled != null) {
                    val why = "strong slanted horizontal frame rails with overlapping protected rows"
                    trace?.invoke(PanelDebugEvent("page", whole, "accept", why))
                    return PanelPlan(angled, false, why)
                }
            }
            return fallback("no validated rows or panels")
        }
        val reason = if (recoveredRows > 0)
            "row-first partition with rejected-row column recovery"
        else "row-first partition with per-row panel fallback"
        trace?.invoke(PanelDebugEvent("page", whole, "accept", reason))
        return PanelPlan(output, false, reason)
    }

    /**
     * Conservative recovery for sloped HORIZONTAL comics rows, not arbitrary
     * panel shapes. A rail must be almost continuous dark ink with bright
     * paper immediately above and below it. Multiple separated rails are
     * permitted, but every resulting interior row needs distributed art.
     *
     * A rectangular crop expands past the full diagonal travel on BOTH
     * sides of the boundary. Thus a character touching the angled frame may
     * be repeated but no original pixels disappear. Called only if the
     * regular row-first, column, pale diagonal and dark straight paths fail.
     * No OCR, image allocation, polygon masks or external library is needed.
     */
    private fun recoverSlantedHorizontalRails(
        width: Int, height: Int, gray: ByteArray,
    ): List<PageRegion>? {
        if (width !in 160..1024 || height !in 200..1024 ||
            height.toLong() > width.toLong() * 3) return null
        data class Rail(val center: Int, val tiltPercent: Int, val score: Int) {
            val halfTravel: Int get() = (width * kotlin.math.abs(tiltPercent) + 199) / 200
        }
        val xStep = maxOf(3, width / 80)
        val candidates = mutableListOf<Rail>()
        // Bounded ~20*170*85 samples on typical X4 input thumbs.
        for (tilt in -20..20 step 2) {
            if (kotlin.math.abs(tilt) < 6) continue
            for (center in height * 18 / 100..height * 82 / 100 step 4) {
                val travel = (width * kotlin.math.abs(tilt) + 199) / 200
                if (center - travel < height * 14 / 100 ||
                    center + travel > height * 86 / 100) continue
                var dark = 0
                var paperBefore = 0
                var paperAfter = 0
                var samples = 0
                for (x in 8 until width - 8 step xStep) {
                    val y = center + (x - width / 2) * tilt / 100
                    if (y - 10 <= 0 || y + 10 >= height) continue
                    fun value(atY: Int): Int = gray[atY * width + x].toInt() and 255
                    if ((-3..3).any { value(y + it) < 115 }) dark++
                    if (value(y - 9) > 242) paperBefore++
                    if (value(y + 9) > 242) paperAfter++
                    samples++
                }
                if (samples < 25 ||
                    dark * 100 < samples * 90 ||
                    paperBefore * 100 < samples * 85 ||
                    paperAfter * 100 < samples * 85) continue
                candidates += Rail(center, tilt, dark * 100 / samples +
                    minOf(paperBefore, paperAfter) * 100 / samples)
            }
        }
        if (candidates.isEmpty()) return null
        val selected = mutableListOf<Rail>()
        val minSeparation = maxOf(35, height / 12)
        for (c in candidates.sortedWith(
            compareByDescending<Rail> { it.score }.thenBy { it.center }
        )) {
            if (selected.none { kotlin.math.abs(it.center - c.center) <= minSeparation })
                selected.add(c)
            if (selected.size >= 5) break
        }
        selected.sortBy { it.center }
        if (selected.isEmpty()) return null

        val guard = maxOf(8, height / 50)
        // Check artwork well outside the rail/overlap envelope. Sparse
        // credits, titles with decorative rules and splash backgrounds must
        // not be promoted into independent reading panels.
        fun illustrated(innerTop: Int, innerBottom: Int): Boolean {
            if (innerBottom - innerTop < height * 10 / 100) return false
            val dy = maxOf(2, (innerBottom - innerTop) / 80)
            val dx = maxOf(2, width / 100)
            val xInk = BooleanArray((width + dx - 1) / dx)
            var rowsWithInk = 0
            var dark = 0
            var total = 0
            var rowIndex = 0
            for (y in innerTop until innerBottom step dy) {
                var rowDark = 0
                var colIndex = 0
                for (x in 0 until width step dx) {
                    total++
                    if ((gray[y * width + x].toInt() and 255) < 220) {
                        dark++
                        rowDark++
                        xInk[colIndex] = true
                    }
                    colIndex++
                }
                if (rowDark > 0) rowsWithInk++
                rowIndex++
            }
            return total > 0 && dark * 100 >= total * 10 &&
                rowsWithInk * 100 >= rowIndex * 45 &&
                xInk.count { it } * 100 >= xInk.size * 40
        }
        var innerTop = 0
        for (rail in selected) {
            val innerBottom = rail.center - rail.halfTravel - guard
            if (!illustrated(innerTop, innerBottom)) return null
            innerTop = rail.center + rail.halfTravel + guard
        }
        if (!illustrated(innerTop, height)) return null

        val result = mutableListOf<PageRegion>()
        var top = 0
        for (rail in selected) {
            val lower = (rail.center + rail.halfTravel + guard).coerceAtMost(height)
            result += PageRegion(0, top, width, lower)
            top = (rail.center - rail.halfTravel - guard).coerceAtLeast(0)
        }
        result += PageRegion(0, top, width, height)
        return if (result.size in 2..6 && result.all {
            it.width > 0 && it.height > height / 10
        } && result.zipWithNext().all { (a, b) ->
            b.top < a.bottom
        }) result else null
    }

    /**
     * Limited recovery for TWO strong illustrated regions separated by a
     * *continuous and slanted* light corridor. This is deliberately NOT a
     * general polygon detector, and does not split black/gray/touching borders.
     *
     * The output rectangles overlap by the full horizontal travel of the
     * slanted gap, plus a safety margin. Their union covers the entire page;
     * no diagonal crop, alpha mask, balloon or original pixel is ever erased.
     *
     * Called ONLY after the strict row-first panel detector has returned one
     * undivided, non-sparse page. Work is bounded to a 1 MP grayscale thumb.
     */
    private fun recoverSlantedPaleGutter(
        width: Int, height: Int, grayscale: ByteArray, rightToLeft: Boolean,
    ): List<PageRegion>? {
        if (width !in 160..1024 || height !in 120..1024 ||
            height.toLong() > width.toLong() * 3) return null
        val minTravel = maxOf(14, width / 12)
        val maxTravel = width / 5
        if (minTravel > maxTravel) return null
        val margin = maxOf(6, width / 25)
        // Sample well OUTSIDE the pale corridor (not inside its white rim).
        // A 12–16 px illustrated gutter needs an adjacent edge probe that
        // reaches both actual panel borders before crediting the candidate.
        val edgeOffset = maxOf(10, width / 25)
        val centerStep = maxOf(2, width / 90)
        val travelStep = maxOf(2, width / 90)
        val yStep = maxOf(1, height / 260)
        fun brightness(x: Int, y: Int): Int = grayscale[y * width + x].toInt() and 255
        data class Candidate(val top: Int, val bottom: Int, val adjacentRows: Int)
        var best: Candidate? = null
        val requiredRows = (height + yStep - 1) / yStep
        // A scanline is eligible only if a *five-pixel-wide* band stays
        // consistently pale (even slightly off-white scan gutters), with
        // dark illustrated borders on BOTH sides.
        // Just a white line inside a dark splash page is insufficient without
        // sustained parallel edges, and a single dark speech-balloon crossing
        // vetoes the candidate completely.
        for (travel in -maxTravel..maxTravel step travelStep) {
            if (kotlin.math.abs(travel) < minTravel) continue
            for (center in width * 30 / 100..width * 70 / 100 step centerStep) {
                val top = center - travel / 2
                val bottom = top + travel
                if (minOf(top, bottom) < width / 5 ||
                    maxOf(top, bottom) > width * 4 / 5) continue
                var adjacent = 0
                var checked = 0
                var safe = true
                for (y in 0 until height step yStep) {
                    val x = top + (travel.toLong() * y / (height - 1)).toInt()
                    if (x - edgeOffset < margin || x + edgeOffset >= width - margin ||
                        (x - 2..x + 2).any { brightness(it, y) < 240 }) {
                        safe = false
                        break
                    }
                    val left = brightness(x - edgeOffset, y) < 215
                    val right = brightness(x + edgeOffset, y) < 215
                    if (left && right) adjacent++
                    checked++
                }
                if (!safe || checked != requiredRows ||
                    adjacent * 100 < checked * 80) continue
                // Distinguish a genuine pair of illustrated panels from
                // empty margins, isolated borders or sparse title lettering.
                // Test ink distribution through the middle of each side.
                var leftArtRows = 0
                var rightArtRows = 0
                for (y in 0 until height step yStep) {
                    val x = top + (travel.toLong() * y / (height - 1)).toInt()
                    var leftInk = 0
                    var rightInk = 0
                    for (col in margin until x - edgeOffset step maxOf(1, width / 90))
                        if (brightness(col, y) < 200) leftInk++
                    for (col in x + edgeOffset + 1 until width - margin step maxOf(1, width / 90))
                        if (brightness(col, y) < 200) rightInk++
                    if (leftInk >= 4) leftArtRows++
                    if (rightInk >= 4) rightArtRows++
                }
                if (leftArtRows * 100 < requiredRows * 65 ||
                    rightArtRows * 100 < requiredRows * 65) continue
                if (best == null || adjacent > best.adjacentRows)
                    best = Candidate(top, bottom, adjacent)
            }
        }
        val chosen = best ?: return null
        val padding = maxOf(4, width / 60)
        val leftEnd = (maxOf(chosen.top, chosen.bottom) + edgeOffset + padding)
            .coerceAtMost(width)
        val rightStart = (minOf(chosen.top, chosen.bottom) - edgeOffset - padding)
            .coerceAtLeast(0)
        // Require useful standalone views, but retain overlap to guarantee
        // every source coordinate survives the slanted boundary.
        if (leftEnd < width / 3 || rightStart > width * 2 / 3 ||
            rightStart >= leftEnd) return null
        val left = PageRegion(0, 0, leftEnd, height)
        val right = PageRegion(rightStart, 0, width, height)
        return if (rightToLeft) listOf(right, left) else listOf(left, right)
    }

    /**
     * Independent row-by-row mode: only horizontal, full-width white gutters
     * separate rows. Vertical panel boundaries are intentionally ignored.
     * Uncertain pages remain whole rather than becoming arbitrary strips.
     */
    fun detectRows(width: Int, height: Int, grayscale: ByteArray,
        trace: ((PanelDebugEvent) -> Unit)? = null): PanelPlan {
        require(width > 0 && height > 0 && width.toLong() * height <= MAX_THUMB_PIXELS)
        require(grayscale.size == width * height)
        val whole = PageRegion(0, 0, width, height)
        fun fallback(reason: String): PanelPlan {
            trace?.invoke(PanelDebugEvent("page", whole, "fallback", reason))
            return PanelPlan(listOf(whole), true, reason)
        }
        val detector = Detector(width, height, grayscale, trace)
        // The row-only mode gets a *second chance* for nonwhite but very
        // pale, consistently empty horizontal bands. Panel-by-Panel keeps
        // its established row-first detector unchanged.
        val rows = detector.partitionRows(whole, allowPaleGrayRecovery = true,
            allowDarkRecovery = true)
        if (rows.size !in 2..MAX_PANELS) return fallback("no confident horizontal rows")
        if (rows.any { !detector.hasContent(it) }) return fallback("empty row")
        if (!coversEntirePage(rows, width, height)) return fallback("unsafe row coverage")
        trace?.invoke(PanelDebugEvent("page", whole, "accept",
            "validated horizontal rows (including conservative pale-gray recovery)"))
        return PanelPlan(rows, false,
            "validated horizontal rows (including conservative pale-gray recovery)")
    }

    /**
     * Convert the user's overlap setting to a bounded detector crop allowance.
     * Existing 1% minimum padding remains in place for zero overlap; an 8%
     * selected overlap gives 4% of the original source on EACH shared side.
     * Total duplicated coverage near a boundary is about 8% of page width
     * or height (subject to source limits), without altering split detection.
     */
    fun overlapPaddingPercent(overlapPercent: Int): Int =
        maxOf(1, (overlapPercent.coerceIn(0, 30) + 1) / 2).coerceAtMost(5)

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

    private class Detector(private val width: Int, private val height: Int, private val gray: ByteArray,
        private val trace: ((PanelDebugEvent) -> Unit)? = null) {
        private var leaves = 0
        private var invalid = false

        /**
         * Row mode deliberately never divides columns. Reuse the same guarded
         * white-strip detector as panel mode, but scan each remaining row
         * independently so short/uneven horizontal gaps are not missed.
         */
        /**
         * A row with fewer than three percent dark thumbnail samples is not
         * sufficient evidence for subdividing its internal page rules.
         *
         * This NEVER removes the row: the caller preserves its complete
         * geometry as one screen (and the usual optional overview still works).
         * Content-rich rows, including genuinely narrow ink-filled panels,
         * continue through the existing detector completely unchanged.
         *
         * Uses a bounded, coarse measurement; in doubtful cases the safer
         * outcome is one intact row, not multiple invented panel frames.
         */
        fun isSparseRow(row: PageRegion): Boolean {
            val step = maxOf(1, minOf(row.width, row.height) / 160)
            var ink = 0
            var samples = 0
            for (y in row.top until row.bottom step step) {
                for (x in row.left until row.right step step) {
                    samples++
                    if ((gray[y * width + x].toInt() and 255) < INK) ink++
                }
            }
            return samples > 0 && ink.toLong() * 100 < samples.toLong() * 3
        }

        /** Only a short unframed tier can be treated as a suspect header/footer.
         * A genuine short comic strip with top/bottom frame rails remains
         * eligible for independent panel splitting. This does not delete ink.
         */
        fun isShallowUnframedTier(row: PageRegion): Boolean {
            if (row.height.toLong() * 100 > height.toLong() * 9 ||
                row.height.toLong() * 100 > row.width.toLong() * 14) return false
            val edge = maxOf(2, minOf(8, row.height / 8))
            fun hasFrameRail(start: Int, end: Int): Boolean {
                for (y in start until end) {
                    var dark = 0
                    for (x in row.left until row.right)
                        if ((gray[y * width + x].toInt() and 255) < INK) dark++
                    if (dark.toLong() * 100 >= row.width.toLong() * 65) return true
                }
                return false
            }
            val topRail = hasFrameRail(row.top, minOf(row.bottom, row.top + edge))
            val bottomRail = hasFrameRail(maxOf(row.top, row.bottom - edge), row.bottom)
            return !(topRail && bottomRail)
        }

        /** The footer merger requires little actual artwork as well as short
         * dimensions. An ink-heavy or framed short story panel stays separate.
         */
        fun isLowInkFooter(row: PageRegion): Boolean {
            var ink = 0
            for (y in row.top until row.bottom)
                for (x in row.left until row.right)
                    if ((gray[y * width + x].toInt() and 255) < INK) ink++
            return ink.toLong() * 100 < row.width.toLong() * row.height * 12
        }

        /**
         * Extremely faint terminal-border/tail classifier. Unlike the
         * 3%-sparse guard, it is allowed to MERGE, so fail closed:
         *  - the candidate must be short relative to both the page and width;
         *  - less than 3% of ALL thumbnail pixels may be ink;
         *  - remaining marks must include one unmistakably long straight
         *    framing stroke, or the strip must be completely blank.
         *
         * This deliberately rejects word-only title/credit strips, broad
         * speech balloons and real illustrated tiers. Every original source
         * coordinate is preserved by the caller's rectangle UNION.
         */
        fun isVerySparseRuledTail(row: PageRegion): Boolean {
            if (row.height < 8 ||
                row.height.toLong() * 100 > height.toLong() * 18 ||
                row.height.toLong() * 100 > row.width.toLong() * 32) return false
            val columnRuns = IntArray(row.width)
            var longestHorizontal = 0
            var longestVertical = 0
            var ink = 0L
            val area = row.width.toLong() * row.height
            for (y in row.top until row.bottom) {
                var horizontalRun = 0
                for (x in row.left until row.right) {
                    if ((gray[y * width + x].toInt() and 255) < INK) {
                        ink++
                        if (ink * 100 >= area * 3) return false
                        horizontalRun++
                        longestHorizontal = maxOf(longestHorizontal, horizontalRun)
                        val column = x - row.left
                        columnRuns[column]++
                        longestVertical = maxOf(longestVertical, columnRuns[column])
                    } else {
                        horizontalRun = 0
                        columnRuns[x - row.left] = 0
                    }
                }
            }
            return ink == 0L ||
                longestHorizontal.toLong() * 100 >= row.width.toLong() * 55 ||
                longestVertical.toLong() * 100 >= row.height.toLong() * 65
        }

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
        /**
         * Return a bounded shortlist of other nearly-white vertical gutters.
         *
         * The usual bestGutter() intentionally returns only the *widest* gap.
         * On manga with a large blank outer margin, that gap can be rejected
         * because its right-hand child has no artwork, while a shorter genuine
         * separator between two panels is never offered to the retry.
         *
         * Inspect each thumbnail column once, group adjoining pale columns,
         * and nominate the least-inked center of each group. The caller must
         * still pass every substantial-art / safe-seam check before splitting.
         * Used only by rejected Panel by Panel rows, never by detectRows().
         */
        private fun alternateCleanVerticalSeams(row: PageRegion): List<Int> {
            val margin = maxOf(8, row.width * 18 / 100)
            if (row.width <= 2 * margin + 3 || row.height < 40) return emptyList()
            data class Gap(val start: Int, val end: Int, val axis: Int)
            val gaps = mutableListOf<Gap>()
            var start = -1
            val paleAxes = mutableListOf<Pair<Int, Int>>()
            fun finish(end: Int) {
                if (start >= 0 && paleAxes.isNotEmpty()) {
                    val midpoint = (start + end - 1) / 2
                    val best = paleAxes.minWithOrNull(
                        compareBy<Pair<Int, Int>> { it.second }
                            .thenBy { kotlin.math.abs(it.first - midpoint) }
                    )!!
                    gaps += Gap(start, end, best.first)
                }
                start = -1
                paleAxes.clear()
            }
            val end = row.right - margin
            for (x in row.left + margin until end) {
                var white = 0
                var dark = 0
                for (y in row.top until row.bottom) {
                    val lightness = gray[y * width + x].toInt() and 255
                    if (lightness >= WHITE) white++
                    if (lightness < INK) dark++
                }
                // This is only a proposal: 97% white and <=2% ink
                // do not grant a split. The strict full-row guard below does.
                val plausible = white.toLong() * 100 >= row.height.toLong() * 97 &&
                    dark.toLong() * 100 <= row.height.toLong() * 2
                if (!plausible) {
                    finish(x)
                    continue
                }
                if (start < 0) start = x
                // Decide at the END of the run, once its true midpoint is
                // known. Ties should choose its center, never its first edge.
                paleAxes += x to dark
            }
            finish(end)
            // Keep the retry bounded, even on pathological checkerboards.
            // The widest proposals come first, but every one is independently
            // checked for artwork on BOTH sides and a truly safe seam.
            return gaps.sortedWith(compareByDescending<Gap> { it.end - it.start }
                .thenBy { it.start }).take(12).map { it.axis }
        }

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
                    // Shared panel frame lines have antialiased fringes:
                    // Conan source 26 had a fully framed horizontal border at
                    // y=452..454, followed by one dim line at y=455. Treat a
                    // 1-2px fringe next to a genuine near-edge, full-width
                    // frame as part of that frame, not a balloon crossing.
                    // All interior strokes still veto the cut. The separate
                    // 2%-of-height ink budget remains unchanged.
                    val nearEdge = y - r.top < edgeBand ||
                        r.bottom - 1 - y < edgeBand
                    val allowedFrame = center < 220 && nearEdge &&
                        (-2..2).any { delta ->
                            val neighbor = y + delta
                            neighbor in r.top until r.bottom && fullWidthFrameRule(neighbor)
                        }
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
                        // Consider alternative interior gutters only when
                        // the original best-gutter proposal fails validation.
                        for (alternate in alternateCleanVerticalSeams(current))
                            yield(alternate)
                        val thin = thinLineGutter(current, horizontal = false)
                        if (thin != null) yield(thin)
                        val partial = partialVerticalGutter(current)
                        if (partial != null) yield(partial)
                        val adaptive = adaptiveGutter(current, horizontal = false)
                        if (adaptive != null) yield(adaptive)
                    }.distinct()
                    // A gutter detector may pick the strongest axis inside
                    // a light strip even when that specific column contains
                    // one scanner speck. A safe neighbour 1–8 pixels away may
                    // keep the same genuine panel boundary. This bounded
                    // search runs ONLY after the normal panel plan was rejected.
                    val radius = minOf(8, maxOf(2, current.width / 75))
                    var axis: Int? = null
                    var attempted = 0
                    var unsafeSeam = 0
                    var leftNotIllustrated = 0
                    var rightNotIllustrated = 0
                    for (proposal in options) {
                        for (distance in 0..radius) {
                            val neighbours = if (distance == 0) listOf(proposal)
                                else listOf(proposal - distance, proposal + distance)
                            for (cut in neighbours) {
                                if (cut <= current.left + 2 || cut >= current.right - 2) continue
                                attempted++
                                if (!safeVerticalSeam(current, cut)) {
                                    unsafeSeam++
                                    continue
                                }
                                if (!likelyIllustratedColumn(current.copy(right = cut))) {
                                    leftNotIllustrated++
                                    continue
                                }
                                if (!likelyIllustratedColumn(current.copy(left = cut))) {
                                    rightNotIllustrated++
                                    continue
                                }
                                axis = cut
                                break
                            }
                            if (axis != null) break
                        }
                        if (axis != null) break
                    }
                    if (axis == null) {
                        trace?.invoke(PanelDebugEvent("column-retry", current, "reject",
                            "attempted=$attempted unsafeSeam=$unsafeSeam " +
                            "leftNotIllustrated=$leftNotIllustrated " +
                            "rightNotIllustrated=$rightNotIllustrated"))
                        continue
                    }
                    val cut = requireNotNull(axis)
                    trace?.invoke(PanelDebugEvent("column-retry", current, "split",
                        "safe vertical seam x=$cut after $attempted attempts"))
                    columns.removeAt(i)
                    columns.add(i, current.copy(right = cut))
                    columns.add(i + 1, current.copy(left = cut))
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

        /**
         * Guarded recovery of a genuinely DARK, straight gutter between
         * independent illustrated regions. A black line by itself is not a
         * divider. It must be a sustained thick dark strip, with short pale
         * margins immediately alongside it and real distributed ink just
         * beyond BOTH margins. Title rules, black splash art, isolated frame
         * strokes, faint captions and large blank paper margins fail closed.
         *
         * The result is an axis, not a crop: sourceRegions() subsequently
         * applies the user's selected overlap on BOTH sides, preserving
         * cross-boundary artwork. Independent Row by Row calls this only
         * horizontally. Panel mode may retry a dark vertical gutter after
         * the established cleaner split/recovery algorithms have failed.
         */
        fun recoverDarkGutter(r: PageRegion, horizontal: Boolean): Int? {
            val length = if (horizontal) r.height else r.width
            val span = if (horizontal) r.width else r.height
            if (length < 120 || span < 110) return null
            val minChild = maxOf(28, length / 5)
            val minBand = maxOf(5, length / 100)
            val maxBand = maxOf(14, length / 14)
            val sampleStep = maxOf(1, span / 160)
            fun sample(axis: Int, across: Int): Int {
                val x = if (horizontal) across else axis
                val y = if (horizontal) axis else across
                return gray[y * width + x].toInt() and 255
            }
            val acrossStart = if (horizontal) r.left else r.top
            val acrossEnd = if (horizontal) r.right else r.bottom
            fun fraction(axis: Int, predicate: (Int) -> Boolean): Int {
                var matches = 0
                var samples = 0
                for (across in acrossStart until acrossEnd step sampleStep) {
                    samples++
                    if (predicate(sample(axis, across))) matches++
                }
                return if (samples == 0) 0 else matches * 100 / samples
            }
            fun hasIndependentIllustration(child: PageRegion): Boolean {
                if (!hasContent(child) || isSparseRow(child)) return false
                // Both ink and paper must be distributed throughout a
                // substantial region, not concentrated in a border or title.
                val dx = maxOf(1, child.width / 85)
                val dy = maxOf(1, child.height / 85)
                var bright = 0
                var dark = 0
                var total = 0
                var richStripes = 0
                var stripes = 0
                if (horizontal) {
                    for (x in child.left + child.width / 8 until
                        child.right - child.width / 8 step dx) {
                        var inkAlong = 0
                        for (y in child.top + child.height / 10 until
                            child.bottom - child.height / 10 step dy) {
                            val v = gray[y * width + x].toInt() and 255
                            if (v < 130) { dark++; inkAlong++ }
                            if (v >= 190) bright++
                            total++
                        }
                        if (inkAlong >= 3) richStripes++
                        stripes++
                    }
                } else {
                    for (y in child.top + child.height / 8 until
                        child.bottom - child.height / 8 step dy) {
                        var inkAlong = 0
                        for (x in child.left + child.width / 10 until
                            child.right - child.width / 10 step dx) {
                            val v = gray[y * width + x].toInt() and 255
                            if (v < 130) { dark++; inkAlong++ }
                            if (v >= 190) bright++
                            total++
                        }
                        if (inkAlong >= 3) richStripes++
                        stripes++
                    }
                }
                return total > 0 && dark * 100 >= total * 12 &&
                    bright * 100 >= total * 15 &&
                    stripes > 0 && richStripes * 100 >= stripes * 55
            }

            data class Band(val start: Int, val end: Int)
            val bands = mutableListOf<Band>()
            var runStart = -1
            val first = r.run { if (horizontal) top else left } + minChild
            val last = r.run { if (horizontal) bottom else right } - minChild
            if (first >= last) return null
            fun finish(end: Int) {
                if (runStart >= 0 && end - runStart in minBand..maxBand)
                    bands += Band(runStart, end)
                runStart = -1
            }
            // Complete cross-span dark strips only. Their *thickness* matters:
            // single black rules are not sufficient split evidence.
            for (axis in first until last) {
                val dark = fraction(axis) { it <= 70 } >= 97
                if (dark && runStart < 0) runStart = axis
                if (!dark && runStart >= 0) finish(axis)
            }
            finish(last)
            // Compare light margins on each side with artwork immediately
            // beyond them; a dark line floating in white paper is rejected.
            val gap = maxOf(3, length / 125)
            val artOffset = maxOf(9, length / 55)
            for (band in bands.sortedByDescending { it.end - it.start }.take(8)) {
                val before = band.start - gap
                val after = band.end + gap
                val artBefore = band.start - artOffset
                val artAfter = band.end + artOffset
                if (artBefore <= first - minChild || artAfter >= last + minChild)
                    continue
                if (fraction(before) { it >= 185 } < 70 ||
                    fraction(after) { it >= 185 } < 70) continue
                if (fraction(artBefore) { it < 135 } < 22 ||
                    fraction(artAfter) { it < 135 } < 22) continue
                val axis = (band.start + band.end) / 2
                val firstPart = if (horizontal) r.copy(bottom = axis)
                    else r.copy(right = axis)
                val secondPart = if (horizontal) r.copy(top = axis)
                    else r.copy(left = axis)
                if (firstPart.width.toLong() * 100 < r.width.toLong() * 18 ||
                    secondPart.width.toLong() * 100 < r.width.toLong() * 18 ||
                    firstPart.height.toLong() * 100 < r.height.toLong() * 18 ||
                    secondPart.height.toLong() * 100 < r.height.toLong() * 18)
                    continue
                if (hasIndependentIllustration(firstPart) &&
                    hasIndependentIllustration(secondPart)) return axis
            }
            return null
        }

        /**
         * Strict Row-by-Row recovery. Only after all existing straight white,
         * thin and adaptive seams fail, look for a broad continuous, very
         * pale-gray band (>=225) with substantial illustration on BOTH sides.
         *
         * No dark-border guessing, text/balloon cutting, vertical partition or
         * new source crop is allowed. A failed recovery returns null, so the
         * caller retains the entire unsplittable tier.
         */
        private fun paleGrayRowSeam(region: PageRegion): Int? {
            if (region.width < 80 || region.height < 84) return null
            val minChild = maxOf(20, region.height / 5)
            val first = region.top + minChild
            val last = region.bottom - minChild
            if (last - first < 8) return null
            val gaps = mutableListOf<Pair<Int, Int>>()
            var start = -1
            // Scan the bounded thumbnail once for continuous pale rows.
            // A dark stroke anywhere across the band vetoes the boundary.
            for (y in first until last) {
                var pale = true
                val base = y * width
                for (x in region.left until region.right) {
                    if ((gray[base + x].toInt() and 255) < 225) {
                        pale = false
                        break
                    }
                }
                if (pale && start < 0) start = y
                if ((!pale || y == last - 1) && start >= 0) {
                    val end = if (pale) y + 1 else y
                    if (end - start >= maxOf(6, region.height / 70))
                        gaps += start to end
                    start = -1
                }
            }
            fun illustratedAcross(r: PageRegion): Boolean {
                if (r.height < 24 || isSparseRow(r) || !hasContent(r)) return false
                val rows = listOf(r.top + r.height / 3, r.top + r.height / 2,
                    r.bottom - r.height / 3)
                val step = maxOf(1, r.width / 200)
                var richRows = 0
                for (y in rows) {
                    var ink = 0
                    var samples = 0
                    for (x in r.left until r.right step step) {
                        samples++
                        if ((gray[y * width + x].toInt() and 255) < 205) ink++
                    }
                    if (ink.toLong() * 100 >= samples.toLong() * 30) richRows++
                }
                return richRows >= 2
            }
            // Prefer the longest trustworthy empty band over incidental white
            // spaces inside a single panel. Its midpoint is safe by construction.
            for ((begin, end) in gaps.sortedByDescending { it.second - it.first }.take(12)) {
                val mid = (begin + end) / 2
                // Require a generous uninterrupted gray corridor around the
                // chosen axis. A short balloon/stroke through the *middle*
                // of an otherwise pale gap can leave two thin pale strips;
                // neither strip alone proves the artwork is safely separated.
                val protect = maxOf(6, region.height / 25)
                if (mid - protect < begin || mid + protect >= end) continue
                val upper = region.copy(bottom = mid)
                val lower = region.copy(top = mid)
                if (illustratedAcross(upper) && illustratedAcross(lower)) return mid
            }
            return null
        }

        fun partitionRows(whole: PageRegion, allowPaleGrayRecovery: Boolean = false,
            allowDarkRecovery: Boolean = false): List<PageRegion> {
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
                val regular = valid(bestGutter(region, horizontal = true))
                    ?: valid(thinLineGutter(region, horizontal = true))
                    ?: valid(adaptiveGutter(region, horizontal = true))
                val pale = if (regular == null && allowPaleGrayRecovery)
                    valid(paleGrayRowSeam(region)) else null
                val dark = if (regular == null && pale == null && allowDarkRecovery)
                    valid(recoverDarkGutter(region, horizontal = true)) else null
                val axis = regular ?: pale ?: dark
                if (axis == null) {
                    trace?.invoke(PanelDebugEvent("row-search", region, "keep",
                        "no full-width horizontal gutter with content on both sides"))
                    rows += region
                    return
                }
                trace?.invoke(PanelDebugEvent("row-search", region, "split",
                    when {
                        regular != null -> "validated horizontal seam at y=$axis"
                        pale != null -> "recovered pale-gray horizontal seam at y=$axis"
                        else -> "recovered dark horizontal gutter at y=$axis"
                    }))
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
                trace?.invoke(PanelDebugEvent("recursive", region, "keep",
                    "no validated clean, thin or adaptive gutter"))
                leaves++
                if (leaves > MAX_PANELS) { invalid = true; return null }
                return PanelReadingOrder.Node.Leaf(region)
            }
            val (isHorizontal, mid) = split
            trace?.invoke(PanelDebugEvent("recursive", region, "split",
                if (isHorizontal) "horizontal candidate y=$mid" else "vertical candidate x=$mid"))
            val first = if (isHorizontal) region.copy(bottom = mid) else region.copy(right = mid)
            val second = if (isHorizontal) region.copy(top = mid) else region.copy(left = mid)
            if (!hasContent(first) || !hasContent(second)) {
                trace?.invoke(PanelDebugEvent("recursive", region, "reject",
                    "candidate would leave an ink-poor child"))
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
