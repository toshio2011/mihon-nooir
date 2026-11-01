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
