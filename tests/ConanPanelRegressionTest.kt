import dev.nooir.bridge.PageRegion
import dev.nooir.bridge.PanelDebugEvent
import dev.nooir.bridge.PanelDetectionCore

/**
 * Synthetic geometry inspired by the three PRIVATE Conan diagnostic traces.
 * Deliberately no copyrighted manga images or OCR text in this repository.
 */
private fun draw(bytes: ByteArray, w: Int, x0: Int, y0: Int, x1: Int, y1: Int, shade: Int) {
    for (y in y0 until y1) for (x in x0 until x1)
        bytes[y * w + x] = shade.toByte()
}

private fun coveredExactly(regions: List<PageRegion>, w: Int, h: Int) {
    check(regions.sumOf { it.width.toLong() * it.height } == w.toLong() * h) {
        "Detector dropped or duplicated source area: $regions"
    }
    for (y in 0 until h step 7) for (x in 0 until w step 5) {
        check(regions.count { x in it.left until it.right && y in it.top until it.bottom } == 1) {
            "Point ($x,$y) is not covered exactly once by $regions"
        }
    }
}

fun main() {
    // A fully synthetic approximation of the Conan-011 geometry:
    // three original tiers; the bottom one has a full-height right comic
    // panel and two independently illustrated, stacked LEFT panels.
    // The actual private scan is never included in the repository.
    val stackedW = 587
    val stackedH = 900
    val stackedGray = ByteArray(stackedW * stackedH) { 255.toByte() }
    val tier = PageRegion(0, 556, stackedW, stackedH)
    val right = PageRegion(297, 556, stackedW, stackedH)
    val left = PageRegion(0, 556, 297, stackedH)
    draw(stackedGray, stackedW, 12, 564, 284, 681, 75)
    draw(stackedGray, stackedW, 12, 704, 284, 890, 90)
    draw(stackedGray, stackedW, 311, 564, 575, 890, 110)
    val rtlColumns = listOf(right, left)
    val expected = listOf(right,
        PageRegion(0, 556, 297, 692), PageRegion(0, 692, 297, 900))
    val stacked = PanelDetectionCore.recoverSingleStackedColumnForTest(
        stackedW, stackedH, stackedGray, tier, rtlColumns)
    check(stacked == expected) {
        "Strict clean nested seam must preserve all three views in RTL: $stacked"
    }
    check(stacked.sumOf { it.width.toLong() * it.height } ==
        tier.width.toLong() * tier.height)
    check(PanelDetectionCore.recoverSingleStackedColumnForTest(
        stackedW, stackedH, stackedGray, tier, listOf(left, right)) ==
        expected.drop(1) + expected.first()) {
        "LTR must keep the upper then lower left panels before right column"
    }
    check(PanelDetectionCore.recoverSingleStackedColumnForTest(
        stackedW, stackedH, stackedGray, tier, rtlColumns, maximum = 2) == null) {
        "Recovery must respect the remaining panel budget"
    }
    // Never infer a cut through a speech balloon, thin art bridge, or an
    // otherwise contiguous panel. No overlap is a substitute for a proven seam.
    val bridged = stackedGray.copyOf()
    draw(bridged, stackedW, 90, 681, 175, 704, 45)
    check(PanelDetectionCore.recoverSingleStackedColumnForTest(
        stackedW, stackedH, bridged, tier, rtlColumns) == null) {
        "Ink crossing the only candidate gutter must veto nested splitting"
    }
    // Two plausible stacked columns are ambiguous. This opt-in recovery
    // refuses to invent a mixed reading order across both columns.
    val bothStacked = stackedGray.copyOf()
    draw(bothStacked, stackedW, 311, 682, 575, 703, 255)
    check(PanelDetectionCore.recoverSingleStackedColumnForTest(
        stackedW, stackedH, bothStacked, tier, rtlColumns) == null) {
        "Only one recovered column may have an independently proven seam"
    }
    val unframed = stackedGray.copyOf()
    draw(unframed, stackedW, 12, 681, 284, 704, 95)
    check(PanelDetectionCore.recoverSingleStackedColumnForTest(
        stackedW, stackedH, unframed, tier, rtlColumns) == null) {
        "Continuous illustrated column must not be subdivided"
    }

    // A. Substantive illustrated columns plus a 42px terminal, ruled,
    // very-low-ink footer. No standalone two-frame footer is permitted.
    // Keep ALL those bottom pixels by attaching to the full-width previous
    // column partition, or safely keep the entire footer if not attachable.
    val fw = 580
    val fh = 900
    val footer = ByteArray(fw * fh) { 255.toByte() }
    draw(footer, fw, 20, 15, 560, 315, 100)
    draw(footer, fw, 18, 344, 272, 850, 85)
    draw(footer, fw, 308, 344, 563, 850, 105)
    // Art reaches down to the separator, where narrow border strokes continue.
    draw(footer, fw, 50, 858, 52, 900, 35)
    draw(footer, fw, 287, 858, 289, 900, 35)
    draw(footer, fw, 536, 858, 538, 900, 35)
    // Synthetic short footer glyphs -- no copyrighted source content.
    for (x in listOf(46, 78, 108, 350, 390, 435)) {
        draw(footer, fw, x, 865, x + 11, 873, 45)
    }
    val footerRowBaseline = PanelDetectionCore.detectRows(fw, fh, footer)
    check(!footerRowBaseline.usedFullPageFallback && footerRowBaseline.regions.size >= 2) {
        "Fixture requires meaningful row separators"
    }
    val footerEvents = mutableListOf<PanelDebugEvent>()
    val footerPlan = PanelDetectionCore.detect(fw, fh, footer, true) { footerEvents.add(it) }
    check(!footerPlan.usedFullPageFallback) { "Footer case lost real panels: ${footerPlan.reason}" }
    check(!footerPlan.preserveSingleFrame) {
        "Existing multi-panel footer join must not disable further valid panels"
    }
    coveredExactly(footerPlan.regions, fw, fh)
    check(footerEvents.any { it.stage == "tier" && it.decision == "absorb" }) {
        "Fixture must exercise actual terminal-footer absorption, not a lucky unsplit row: $footerEvents"
    }
    check(footerPlan.regions.none { it.top >= fh - 60 }) {
        "A 42px border/footer must not become a separate rotated frame: ${footerPlan.regions}"
    }
    check(PanelDetectionCore.detectRows(fw, fh, footer) == footerRowBaseline) {
        "Footer safeguard must not change Row by Row"
    }

    // A2. A terminal row taller than the old 9%-height guard contains
    // ONLY two long, thin border marks. Detected as a substantial enough
    // horizontal tier, it used to become a nearly blank standalone view.
    // Merge its source pixels into the single full-width illustrated region,
    // but never into a separate source page or multiple side-by-side panels.
    val nearW = 480
    val nearH = 800
    val nearBlank = ByteArray(nearW * nearH) { 255.toByte() }
    draw(nearBlank, nearW, 14, 10, 466, 638, 92)
    draw(nearBlank, nearW, 24, 699, 456, 701, 32)
    draw(nearBlank, nearW, 24, 714, 456, 716, 32)
    val nearRows = PanelDetectionCore.detectRows(nearW, nearH, nearBlank)
    check(!nearRows.usedFullPageFallback && nearRows.regions.size == 2) {
        "Fixture must contain an illustrated row and separately admitted quiet rail: ${nearRows.regions}"
    }
    val nearEvents = mutableListOf<PanelDebugEvent>()
    val mergedNear = PanelDetectionCore.detect(nearW, nearH, nearBlank, true) {
        nearEvents.add(it)
    }
    check(!mergedNear.usedFullPageFallback && mergedNear.regions ==
        listOf(PageRegion(0, 0, nearW, nearH)) && mergedNear.preserveSingleFrame) {
        "Near-empty terminal rule must be merged AND protected from downstream slicing: ${mergedNear.regions}"
    }
    check(nearEvents.any { it.stage == "tier" && it.decision == "absorb" &&
        it.detail.contains("near-empty terminal border") }) {
        "Must hit the new conservative terminal-border safeguard: $nearEvents"
    }
    coveredExactly(mergedNear.regions, nearW, nearH)
    check(PanelDetectionCore.detect(nearW, nearH, nearBlank, false).regions ==
        mergedNear.regions) {
        "Merging full-width tiers must not change RTL/LTR reading order"
    }
    check(PanelDetectionCore.detectRows(nearW, nearH, nearBlank) == nearRows) {
        "Independent Row by Row may not inherit Panel-by-Panel coalescing"
    }

    // Text-like small glyphs instead of connected border lines: protect the
    // meaning of a short credit/title band rather than hiding it in artwork.
    val credits = ByteArray(nearW * nearH) { 255.toByte() }
    draw(credits, nearW, 14, 10, 466, 638, 92)
    for (y in listOf(701, 722)) {
        for (x in listOf(20, 62, 104, 146, 188, 230, 272, 314, 356, 398, 440)) {
            draw(credits, nearW, x, y, x + 15, y + 7, 40)
        }
    }
    val creditsPlan = PanelDetectionCore.detect(nearW, nearH, credits, true)
    check(creditsPlan.regions.size >= 2 &&
        creditsPlan.regions.last().top >= 600) {
        "Meaningful standalone title/credits must not be joined to preceding art: ${creditsPlan.regions}"
    }
    check(!creditsPlan.preserveSingleFrame) {
        "Meaningful credits must remain eligible for their normal reading frames"
    }
    coveredExactly(creditsPlan.regions, nearW, nearH)

    // A3. The ORIGINAL shallow-terminal-footer absorption (the earlier
    // Conan path) can also leave exactly one full-width region. Historically
    // that looked like an invalid one-panel plan and fell back into general
    // page slicing. Its "absorb" decision must produce a protected one-frame
    // result, not another set of near-empty reading screens.
    val shortW = 480
    val shortH = 800
    val shortFooter = ByteArray(shortW * shortH) { 255.toByte() }
    draw(shortFooter, shortW, 12, 12, 468, 720, 100)
    draw(shortFooter, shortW, 30, 768, 451, 772, 30)
    val shortRows = PanelDetectionCore.detectRows(shortW, shortH, shortFooter)
    check(!shortRows.usedFullPageFallback && shortRows.regions.size == 2) {
        "Short-footer fixture must expose 2 rows: ${shortRows.regions}"
    }
    val shortEvents = mutableListOf<PanelDebugEvent>()
    val shortPlan = PanelDetectionCore.detect(shortW, shortH, shortFooter, true) {
        shortEvents.add(it)
    }
    check(!shortPlan.usedFullPageFallback && shortPlan.preserveSingleFrame &&
        shortPlan.regions == listOf(PageRegion(0, 0, shortW, shortH))) {
        "Existing shallow-footer absorption must retain one protected frame: $shortPlan"
    }
    check(shortEvents.any { it.stage == "tier" && it.decision == "absorb" &&
        it.detail.contains("terminal ruled footer retained") }) {
        "The old short-footer route, not the later ruled-tail route, must be exercised: $shortEvents"
    }
    coveredExactly(shortPlan.regions, shortW, shortH)
    check(PanelDetectionCore.detect(shortW, shortH, shortFooter, false) == shortPlan)
    check(PanelDetectionCore.detectRows(shortW, shortH, shortFooter) == shortRows) {
        "Existing Row by Row must still have its original two horizontal regions"
    }

    // B. Original panel-by-panel rejected-row recovery fixture. The upper two
    // illustrated columns share a dark bottom border and its 1px antialiased
    // fringe. The fringe is near an established full-width frame rule, NOT an
    // interior balloon. Preserve both RTL panels.
    val bw = 360
    val bh = 600
    val framed = ByteArray(bw * bh) { 255.toByte() }
    draw(framed, bw, 8, 5, 164, 16, 65) // tiny detail that rejects recursive tree
    draw(framed, bw, 8, 42, 164, 395, 95)
    draw(framed, bw, 195, 5, 352, 395, 110)
    draw(framed, bw, 8, 392, 352, 395, 35) // shared horizontal framing
    draw(framed, bw, 150, 395, 205, 396, 200) // a dim anti-alias fringe
    draw(framed, bw, 10, 430, 350, 590, 85)
    val framedRows = PanelDetectionCore.detectRows(bw, bh, framed)
    check(!framedRows.usedFullPageFallback && framedRows.regions.size == 2) {
        "Fixture requires two original horizontal tiers: ${framedRows.regions}"
    }
    val framedPlan = PanelDetectionCore.detect(bw, bh, framed, true)
    check(!framedPlan.usedFullPageFallback && framedPlan.regions.size == 3 &&
        framedPlan.regions.take(2).let { it[0].left > it[1].left }) {
        "Safe near-frame anti-aliasing must not group two upper story columns: ${framedPlan.regions}"
    }
    coveredExactly(framedPlan.regions, bw, bh)
    val unsafe = framed.copyOf()
    // Genuine DARK interior artwork bridges the otherwise pale gutter.
    draw(unsafe, bw, 164, 211, 195, 212, 40)
    val unsafePlan = PanelDetectionCore.detect(bw, bh, unsafe, true)
    check(!unsafePlan.usedFullPageFallback && unsafePlan.regions.size == 2) {
        "A crossing balloon/stroke must still veto column retry: ${unsafePlan.regions}"
    }
    check(PanelDetectionCore.detectRows(bw, bh, framed) == framedRows)

    // C. Two narrow typographic header bands above FOUR rows of real
    // illustrations. Words are separated by vertical whitespace but are not
    // comic panels. Protect ONLY the shallow unframed 3+-piece title tier;
    // separately framed story panels below must remain independently readable.
    val tw = 581
    val th = 900
    val title = ByteArray(tw * th) { 255.toByte() }
    draw(title, tw, 45, 45, 265, 90, 95)
    draw(title, tw, 320, 45, 535, 90, 95)
    for ((x, width) in listOf(25 to 95, 155 to 64, 257 to 85, 390 to 70, 493 to 60)) {
        draw(title, tw, x, 121, x + width, 160, 75)
    }
    for ((top, bottom) in listOf(200 to 334, 363 to 493, 522 to 652, 680 to 818)) {
        draw(title, tw, 25, top, 278, bottom, 95)
        draw(title, tw, 310, top, 556, bottom, 105)
    }
    val titleRows = PanelDetectionCore.detectRows(tw, th, title)
    val titleEvents = mutableListOf<PanelDebugEvent>()
    val titlePlan = PanelDetectionCore.detect(tw, th, title, true) { titleEvents.add(it) }
    check(!titlePlan.usedFullPageFallback) { "Title fixture must retain story art" }
    coveredExactly(titlePlan.regions, tw, th)
    check(titleEvents.any { it.stage == "tier" && it.decision == "keep" &&
        it.detail.contains("shallow unframed headline") }) {
        "Fixture must exercise the title-fragment guard, not a lucky unrelated fallback"
    }
    check(titlePlan.regions.none { it.bottom <= th / 4 && it.width < tw / 3 }) {
        "Shallow top title band became standalone word fragments: ${titlePlan.regions}"
    }
    check(titlePlan.regions.count { it.top >= 180 && it.width < tw * 3 / 4 } >= 6) {
        "Guard may not silently combine ordinary illustrated story panels: ${titlePlan.regions}"
    }
    check(PanelDetectionCore.detectRows(tw, th, title) == titleRows) {
        "Title guard must never add vertical slices to Row by Row mode"
    }

    // All geometry tests are deterministic and diagnostics are observational.
    check(PanelDetectionCore.detect(tw, th, title, true).regions == titlePlan.regions)
    println("Conan regressions passed: terminal footer, framed gutter fringe/balloon, title band, RTL and complete coverage")
}
