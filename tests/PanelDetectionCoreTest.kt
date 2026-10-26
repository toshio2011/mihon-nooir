import dev.nooir.bridge.PageRegion
import dev.nooir.bridge.PanelDetectionCore

private fun grid(): Triple<Int, Int, ByteArray> {
    val w = 300
    val h = 420
    val pixels = ByteArray(w * h) { 255.toByte() }
    val cols = listOf(0 to 145, 155 to 300)
    val rows = listOf(0 to 132, 144 to 274, 286 to 420)
    for ((row, ys) in rows.withIndex()) for ((col, xs) in cols.withIndex()) {
        val shade = (35 + row * 35 + col * 13).toByte()
        for (y in ys.first + 4 until ys.second - 4)
            for (x in xs.first + 4 until xs.second - 4)
                pixels[y * w + x] = shade
    }
    return Triple(w, h, pixels)
}
private fun centerGray(pixels: ByteArray, width: Int, region: PageRegion): Int {
    val x = (region.left + region.right) / 2
    val y = (region.top + region.bottom) / 2
    return pixels[y * width + x].toInt() and 255
}
fun main() {
    val (width, height, image) = grid()
    val rtl = PanelDetectionCore.detect(width, height, image, true)
    check(!rtl.usedFullPageFallback) { "Clean 2x3 panel grid should be detected: ${rtl.reason}" }
    check(rtl.regions.size == 6) { "Expected 6 panels, got ${rtl.regions.size}" }
    check(rtl.regions.map { centerGray(image, width, it) } == listOf(48, 35, 83, 70, 118, 105)) {
        "RTL must read top tier first, right to left within each tier"
    }
    // Panel mode should reuse row tiers before resolving columns, rather than
    // treating short within-tier vertical gutters as full-page columns.
    val rowFirst = PanelDetectionCore.detect(width, height, image, true)
    check(rowFirst.regions.map { centerGray(image, width, it) } ==
        listOf(48, 35, 83, 70, 118, 105)) {
        "Row-first partition must retain tier-first RTL reading order"
    }
    val rowPlan = PanelDetectionCore.detectRows(width, height, image)
    check(!rowPlan.usedFullPageFallback && rowPlan.regions.size == 3) {
        "Row by Row must keep two columns together in each of three tiers: ${rowPlan.reason}"
    }
    check(rowPlan.regions.all { it.left == 0 && it.right == width })
    check(rowPlan.regions.zipWithNext().all { (a, b) -> a.bottom == b.top })
    // Row mode must tolerate isolated scan speckles in a thin white separator,
    // without accidentally splitting the two columns into separate panels.
    val noisyRows = image.copyOf()
    for (x in 0 until width step 13) noisyRows[138 * width + x] = 185.toByte()
    val noisyRowPlan = PanelDetectionCore.detectRows(width, height, noisyRows)
    check(!noisyRowPlan.usedFullPageFallback && noisyRowPlan.regions.size == 3) {
        "Thin noisy white strips should still separate three horizontal tiers: ${noisyRowPlan.reason}"
    }
    check(noisyRowPlan.regions.all { it.left == 0 && it.right == width })
    val ltr = PanelDetectionCore.detect(width, height, image, false)
    check(ltr.regions.map { centerGray(image, width, it) } == listOf(35, 48, 70, 83, 105, 118))
    val scaled = PanelDetectionCore.sourceRegions(rtl, width, height, 480, 800, 1)
    check(scaled.size == 6 && scaled.all { it.width > 0 && it.height > 0 })
    check(scaled.all { it.left >= 0 && it.top >= 0 && it.right <= 480 && it.bottom <= 800 })
    // Padding must not create gaps; every original source pixel belongs to >= 1 output.
    for (y in 0 until 800 step 3) for (x in 0 until 480 step 3) {
        check(scaled.any { x in it.left until it.right && y in it.top until it.bottom }) {
            "Panel geometry dropped source content at ($x,$y)"
        }
    }
    val full = ByteArray(width * height) { 110.toByte() }
    val splash = PanelDetectionCore.detect(width, height, full, true)
    check(splash.usedFullPageFallback && splash.regions == listOf(PageRegion(0, 0, width, height)))
    val fallback = PanelDetectionCore.sourceRegions(splash, width, height, 480, 800)
    check(fallback == listOf(PageRegion(0, 0, 480, 800)))

    // Staggered columns are real manga layouts: traverse each column in reading order.
    val staggered = ByteArray(width * height) { 255.toByte() }
    for (y in 3 until 145) for (x in 4 until 144) staggered[y * width + x] = 100.toByte()
    for (y in 158 until height - 4) for (x in 4 until 144) staggered[y * width + x] = 100.toByte()
    for (y in 3 until 205) for (x in 156 until width - 4) staggered[y * width + x] = 100.toByte()
    for (y in 218 until height - 4) for (x in 156 until width - 4) staggered[y * width + x] = 100.toByte()
    val uncertain = PanelDetectionCore.detect(width, height, staggered, true)
    check(!uncertain.usedFullPageFallback && uncertain.regions.size == 4) { "Staggered two-column layouts should retain four distinct panels: ${uncertain.reason}" }
    check(uncertain.regions[0].left > uncertain.regions[2].left) { "Right column must precede left column for RTL" }

    // Landscape manga strips with clear framed panels must not be skipped.
    val wideW = 480
    val wideH = 240
    val wideStrip = ByteArray(wideW * wideH) { 255.toByte() }
    for (y in 12 until wideH - 12) {
        for (x in 12 until 225) wideStrip[y * wideW + x] = 75.toByte()
        for (x in 255 until wideW - 12) wideStrip[y * wideW + x] = 95.toByte()
    }
    val widePlan = PanelDetectionCore.detect(wideW, wideH, wideStrip, true)
    check(!widePlan.usedFullPageFallback && widePlan.regions.size == 2) {
        "Wide strip should split into two panels: ${widePlan.reason}"
    }
    check(widePlan.regions[0].left > widePlan.regions[1].left)
    // Dialogue/balloon strokes crossing a gutter must not be sliced in half.
    val balloonAcrossGutter = wideStrip.copyOf()
    for (x in 225 until 255) {
        for (y in 24 until wideH - 24) balloonAcrossGutter[y * wideW + x] = 255.toByte()
    }
    // A narrow black speech-balloon tail runs through the center of the gap.
    for (x in 225 until 255) balloonAcrossGutter[wideH / 2 * wideW + x] = 30.toByte()
    val protected = PanelDetectionCore.detect(wideW, wideH, balloonAcrossGutter, true)
    check(protected.usedFullPageFallback || protected.regions.none { region ->
        region.left > 225 && region.left < 255 || region.right > 225 && region.right < 255
    }) { "Do not split through speech balloon strokes in the gutter" }
    // Scanned gutters contain sparse compression/antialias noise. A detector
    // that demands three perfectly white lines loses real panel boundaries.
    val speckled = wideStrip.copyOf()
    for (x in 225 until 255) {
        // Every gutter column has a different isolated dark speck, so there
        // is no mathematically all-white line anywhere in the gap.
        speckled[(20 + (x * 7) % 190) * wideW + x] = 180.toByte()
    }
    val speckledPlan = PanelDetectionCore.detect(wideW, wideH, speckled, true)
    check(!speckledPlan.usedFullPageFallback && speckledPlan.regions.size == 2) {
        "Isolated gutter noise must not collapse two valid panels into a full page: ${speckledPlan.reason}"
    }
    // Narrow, ink-filled panels are real panels, not errors merely because
    // their shape occupies little of a portrait screen.
    val narrowW = 300
    val narrowH = 420
    val narrow = ByteArray(narrowW * narrowH) { 255.toByte() }
    for (y in 15 until 405) {
        for (x in 5 until 39) narrow[y * narrowW + x] = 80.toByte()
        for (x in 57 until 295) narrow[y * narrowW + x] = 110.toByte()
    }
    val narrowPlan = PanelDetectionCore.detect(narrowW, narrowH, narrow, true)
    check(!narrowPlan.usedFullPageFallback && narrowPlan.regions.size == 2) {
        "Real narrow panel should survive conservative gutter detection: ${narrowPlan.reason}"
    }
    check(narrowPlan.regions.any { it.width < narrowW / 5 }) {
        "Detector must retain a narrow panel instead of rejecting its aspect ratio"
    }
    // Three narrow columns nested inside a horizontal tier must all split.
    val nestedW = 360
    val nestedH = 420
    val nested = ByteArray(nestedW * nestedH) { 255.toByte() }
    for (y in 12 until 192) for (x in 10 until nestedW - 10)
        nested[y * nestedW + x] = 85.toByte()
    for (pair in listOf(10 to 112, 130 to 232, 250 to 350)) {
        for (y in 225 until 408) for (x in pair.first until pair.second)
            nested[y * nestedW + x] = 95.toByte()
    }
    val nestedPlan = PanelDetectionCore.detect(nestedW, nestedH, nested, true)
    check(!nestedPlan.usedFullPageFallback && nestedPlan.regions.size == 4) {
        "Nested three-column tier must produce four panels: ${nestedPlan.reason}, ${nestedPlan.regions}"
    }
    // One-pixel horizontal seam: row and panel modes must both split.
    val thinW = 240
    val thinH = 240
    val thinRow = ByteArray(thinW * thinH) { 255.toByte() }
    for (y in 8 until 119) for (x in 8 until thinW - 8) thinRow[y * thinW + x] = 90.toByte()
    for (y in 120 until thinH - 8) for (x in 8 until thinW - 8) thinRow[y * thinW + x] = 90.toByte()
    val thinRows = PanelDetectionCore.detectRows(thinW, thinH, thinRow)
    check(!thinRows.usedFullPageFallback && thinRows.regions.size == 2) {
        "A one-pixel horizontal seam must separate rows: ${thinRows.reason}"
    }
    val thinPanels = PanelDetectionCore.detect(thinW, thinH, thinRow, true)
    check(!thinPanels.usedFullPageFallback && thinPanels.regions.size == 2) {
        "A one-pixel horizontal seam must separate panels: ${thinPanels.reason}"
    }
    // A full-height column boundary must not be destroyed by row-first
    // splitting of a partial-width horizontal tier.
    val tallW = 300
    val tallH = 420
    val tall = ByteArray(tallW * tallH) { 255.toByte() }
    for (y in 8 until 412) for (x in 8 until 142) tall[y * tallW + x] = 80.toByte()
    for (y in 8 until 196) for (x in 156 until 292) tall[y * tallW + x] = 95.toByte()
    for (y in 210 until 412) for (x in 156 until 292) tall[y * tallW + x] = 105.toByte()
    val tallPlan = PanelDetectionCore.detect(tallW, tallH, tall, true)
    check(!tallPlan.usedFullPageFallback && tallPlan.regions.size == 3) {
        "Tall column and two stacked panels must remain three intact regions: ${tallPlan.reason}"
    }
    check(tallPlan.regions.any { it.height >= tallH * 9 / 10 }) {
        "Row-first must preserve the full-height panel"
    }
    // Thin row seam with a tiny interruption from a panel border.
    val interruptedW = 300
    val interruptedH = 280
    val interrupted = ByteArray(interruptedW * interruptedH) { 255.toByte() }
    for (y in 8 until 139) for (x in 8 until 292)
        interrupted[y * interruptedW + x] = 100.toByte()
    for (y in 141 until 272) for (x in 8 until 292)
        interrupted[y * interruptedW + x] = 110.toByte()
    // A short border fragment crosses the otherwise pale horizontal seam.
    for (x in 145 until 148) for (y in 138 until 142)
        interrupted[y * interruptedW + x] = 60.toByte()
    val interruptedRows = PanelDetectionCore.detectRows(interruptedW, interruptedH, interrupted)
    check(!interruptedRows.usedFullPageFallback && interruptedRows.regions.size == 2) {
        "Short border interruptions must not hide a valid horizontal row seam"
    }
    // A sparse chapter title composed of isolated short ink strokes must not
    // be treated as a reliable grid of panels. Preserve the original page.
    val titleW = 300
    val titleH = 420
    val title = ByteArray(titleW * titleH) { 255.toByte() }
    for (i in 0 until 7) {
        val x0 = 30 + (i % 4) * 62
        val y0 = 65 + (i / 4) * 85
        for (y in y0 until y0 + 12) for (x in x0 until x0 + 22)
            title[y * titleW + x] = 65.toByte()
    }
    val titlePlan = PanelDetectionCore.detect(titleW, titleH, title, true)
    check(titlePlan.usedFullPageFallback) {
        "Sparse lettering must remain whole, not become panels: ${titlePlan.regions}"
    }
    // The independent row detector should not inherit panel-only heuristics.
    check(PanelDetectionCore.detectRows(width, height, image).regions.size == 3)
    // Two meaningful blocks separated by a clean gutter must still split.
    // A decorative one-pixel border in the left block is not a third panel.
    val borderW = 300
    val borderH = 420
    val borderOnly = ByteArray(borderW * borderH) { 255.toByte() }
    for (y in 15 until 405) {
        borderOnly[y * borderW + 40] = 35.toByte()
        for (x in 170 until 287) borderOnly[y * borderW + x] = 90.toByte()
    }
    val borderPlan = PanelDetectionCore.detect(borderW, borderH, borderOnly, true)
    check(borderPlan.usedFullPageFallback) {
        "Border-only region must not become a standalone panel: ${borderPlan.regions}"
    }
    // Do not apply the new border-fragment heuristic to Row by Row.
    check(PanelDetectionCore.detectRows(width, height, image).regions.size == 3)
    // Row-first fallback: a valid row split must survive even when one tier
    // contains only a sparse border that panel validation refuses to split.
    val tierW = 300
    val tierH = 420
    val mixedTiers = ByteArray(tierW * tierH) { 255.toByte() }
    for (y in 12 until 175) for (x in 12 until 288)
        mixedTiers[y * tierW + x] = 90.toByte()
    for (y in 240 until 407) {
        mixedTiers[y * tierW + 38] = 40.toByte()
        for (x in 170 until 288) mixedTiers[y * tierW + x] = 85.toByte()
    }
    val expectedRows = PanelDetectionCore.detectRows(tierW, tierH, mixedTiers)
    val mixedPanels = PanelDetectionCore.detect(tierW, tierH, mixedTiers, true)
    check(!expectedRows.usedFullPageFallback && expectedRows.regions.size >= 2) {
        "Fixture must have independent horizontal rows: ${expectedRows.reason}"
    }
    check(!mixedPanels.usedFullPageFallback && mixedPanels.regions.size >= 2) {
        "A rejected panel must not undo successful row detection: ${mixedPanels.reason}"
    }
    // Thin candidate strips should not undo a valid row split.
    // Two substantial horizontal tiers remain readable as rows even if a
    // tiny dark sliver near a vertical seam tempts finer segmentation.
    val sliverW = 300
    val sliverH = 420
    val sliverPage = ByteArray(sliverW * sliverH) { 255.toByte() }
    for (y in 10 until 175) {
        for (x in 8 until 275) sliverPage[y * sliverW + x] = 95.toByte()
        for (x in 283 until 294) sliverPage[y * sliverW + x] = 55.toByte()
    }
    for (y in 240 until 405) for (x in 10 until 290)
        sliverPage[y * sliverW + x] = 100.toByte()
    val sliverRows = PanelDetectionCore.detectRows(sliverW, sliverH, sliverPage)
    val sliverPanels = PanelDetectionCore.detect(sliverW, sliverH, sliverPage, true)
    check(!sliverRows.usedFullPageFallback && sliverRows.regions.size >= 2)
    check(!sliverPanels.usedFullPageFallback && sliverPanels.regions.size >= 2) {
        "Thin panel candidate must not erase valid row boundaries"
    }
    // Existing WEBTOON and ambiguous wide spreads retain fallback.
    check(PanelDetectionCore.detect(100, 400, ByteArray(40_000) { 80.toByte() }, true).usedFullPageFallback)
    check(PanelDetectionCore.detect(400, 100, ByteArray(40_000) { 80.toByte() }, true).usedFullPageFallback)
    check(runCatching { PanelDetectionCore.detect(0, 20, ByteArray(1), true) }.isFailure)
    check(runCatching { PanelDetectionCore.detect(20, 20, ByteArray(12), true) }.isFailure)
    check(runCatching { PanelDetectionCore.detect(2000, 2000, ByteArray(0), true) }.isFailure)
    check(runCatching { PanelDetectionCore.sourceRegions(rtl, width, height, 480, 800, 6) }.isFailure)
    check(PanelDetectionCore.detect(width, height, image, true).regions == rtl.regions) {
        "Panel ordering must be deterministic across runs"
    }
    println("Automatic panel Phase A passed: 2x3 RTL/LTR, coverage, guard padding, splash, staggered fallback, webtoon, spreads, limits")
}
