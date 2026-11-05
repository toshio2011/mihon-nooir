import dev.nooir.bridge.PageRegion
import dev.nooir.bridge.PanelDetectionCore

/**
 * Synthetic-only high-contrast manga gutter checks. No actual manga pixels.
 * A thick solid black frame is not enough: require two independently
 * illustrated regions and pale shoulders. Preserve full source coverage.
 */
private fun illustratedPage(withDivider: Boolean = true): Triple<Int, Int, ByteArray> {
    val w = 560
    val h = 900
    // Opaque off-white tones prevent any accidental pure-white gutter.
    val img = ByteArray(w * h)
    for (y in 0 until h) for (x in 0 until w) {
        val ink = ((x / 22 + y / 31) % 4 == 0)
        img[y * w + x] = (if (ink) 75 else 222).toByte()
    }
    // A 20px genuinely solid dark frame between two independently
    // illustrated tiers. Outside its edge the next illustrated tier begins
    // with a mostly light speech-background region, so the original
    // immediate-art >=22% rule cannot accept the bar.
    if (withDivider) for (y in 310 until 330) for (x in 0 until w)
        img[y * w + x] = 26.toByte()
    for (y in 330 until 366) for (x in 0 until w)
        img[y * w + x] = (if (x % 15 == 0) 75 else 222).toByte()
    return Triple(w, h, img)
}

private fun assertFullCoverage(regions: List<PageRegion>, w: Int, h: Int) {
    check(regions.sumOf { it.width.toLong() * it.height } == w.toLong() * h) {
        "No source pixels may be dropped or duplicated by accepted detector regions: $regions"
    }
    for (y in 0 until h step 9) for (x in 0 until w step 7) {
        check(regions.count { x in it.left until it.right && y in it.top until it.bottom } == 1) {
            "Source location ($x,$y) was missing or repeated: $regions"
        }
    }
}

fun main() {
    val (w, h, img) = illustratedPage()
    // This must NOT change strict Row by Row behavior.
    val rowPlan = PanelDetectionCore.detectRows(w, h, img)
    check(rowPlan.usedFullPageFallback) { "Row Only must not gain the new dark-band relaxation: $rowPlan" }

    val events = mutableListOf<dev.nooir.bridge.PanelDebugEvent>()
    val plan = PanelDetectionCore.detect(w, h, img, true) { events += it }
    check(!plan.usedFullPageFallback && plan.regions.size == 2 &&
        plan.reason.contains("strong black framed horizontal divider")) {
        "Panel-by-panel should recover a strongly framed divider: $plan"
    }
    check(plan.regions[0].top == 0 && plan.regions[1].bottom == h &&
        plan.regions[0].bottom == plan.regions[1].top) {
        "A strong divider should preserve top-to-bottom order: ${plan.regions}"
    }
    check(events.any { it.stage == "page" && it.decision == "accept" &&
        it.detail.contains("strong black framed horizontal divider") })
    assertFullCoverage(plan.regions, w, h)
    val ltr = PanelDetectionCore.detect(w, h, img, false)
    check(ltr.regions == plan.regions) { "Horizontal rows have same order in RTL and LTR" }

    // Fine one-pixel art strokes are not panels; a flimsy 1px black line
    // is never sufficient evidence of a safe split.
    val singleStroke = illustratedPage(withDivider = false)
    for (x in 0 until w) singleStroke.third[320 * w + x] = 26.toByte()
    val thinPlan = PanelDetectionCore.detect(w, h, singleStroke.third, true)
    check(thinPlan.reason != plan.reason) { "Hairline should not trigger thick-frame recovery" }

    // A solid black bar separating art and mostly blank paper is NOT
    // automatically an independent second panel.
    val blankTail = img.copyOf()
    for (y in 370 until h) for (x in 0 until w)
        blankTail[y * w + x] = 222.toByte()
    val noSecondPanel = PanelDetectionCore.detect(w, h, blankTail, true)
    check(!noSecondPanel.reason.contains("strong black framed horizontal divider")) {
        "No second illustrated tier: dark rail must not create a false panel"
    }

    println("Black-frame gutter checks passed: recovered one strong seam, no pixel loss, safeguards retained")
}
