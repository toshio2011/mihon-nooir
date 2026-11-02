import dev.nooir.bridge.PageRegion
import dev.nooir.bridge.PanelWhitespaceCrop

fun main() {
    val frame = PageRegion(0, 0, 400, 800)
    // A Conan-like strip with large top/bottom paper margins. A pale speech
    // balloon outline must count as content; never trim into its coordinates.
    val fitted = PanelWhitespaceCrop.trim(frame) { x, y ->
        x in 54..345 && y in 200..600
    }
    check(fitted.width < frame.width && fitted.height < frame.height) {
        "Whitespace should be reduced only inside the detected panel: $fitted"
    }
    check(fitted.left <= 54 && fitted.right > 345 && fitted.top <= 200 && fitted.bottom > 600) {
        "All sampled panel art / speech bubbles must remain within bounds: $fitted"
    }
    // Hard cap protects against cropping out faint artwork we may not detect.
    check(fitted.left <= 140 && fitted.top <= 280)
    check(fitted.right >= 260 && fitted.bottom >= 520)

    // A one-pixel speech-mark or faint outline between the coarse lattice
    // positions is still content. Never crop it away just to fill the screen.
    val offGridInk = PanelWhitespaceCrop.trim(frame) { x, y ->
        (x in 54..345 && y in 200..600) || (x == 19 && y == 17)
    }
    check(offGridInk == frame) {
        "A small missed edge detail must veto proposed trimming"
    }

    // A panel touching the left and upper page edges must preserve them.
    val edge = PanelWhitespaceCrop.trim(frame) { x, y ->
        x in 0..270 && y in 0..500
    }
    check(edge.left == frame.left && edge.top == frame.top)
    check(edge.right > 270 && edge.bottom > 500)

    // No confident ink / near-blank title cards are never aggressively cropped.
    check(PanelWhitespaceCrop.trim(frame) { _, _ -> false } == frame)
    check(PanelWhitespaceCrop.trim(frame) { x, y -> x == 100 && y == 100 } == frame)

    // A thin standalone panel still deserves a safe aspect improvement.
    val narrow = PageRegion(0, 0, 76, 480)
    val narrowTrim = PanelWhitespaceCrop.trim(narrow) { x, y ->
        x in 12..58 && y in 70..398
    }
    check(narrowTrim.width > 0 && narrowTrim.height > 0)
    check(narrowTrim.left <= 12 && narrowTrim.right > 58)
    check(narrowTrim.top <= 70 && narrowTrim.bottom > 398)

    // Geometry remains bounded for short panels; never create zero dimensions.
    val tiny = PageRegion(0, 0, 14, 15)
    check(PanelWhitespaceCrop.trim(tiny) { _, _ -> true } == tiny)
    // Pipeline regression: confirmed panel cuts with 8% selected overlap
    // should repeat crossing speech/art even AFTER optional whitespace trim.
    // A low-overlap setting is intentionally not enough to contain the entire
    // crossing within both views, demonstrating why the protection matters.
    val confirmed = dev.nooir.bridge.PanelPlan(
        listOf(PageRegion(0, 0, 200, 600), PageRegion(200, 0, 400, 600)),
        usedFullPageFallback = false,
        reason = "two confirmed illustrated panels",
    )
    val paperInk: (Int, Int) -> Boolean = { x, y ->
        (x in 25..174 && y in 40..550 && (x + y) % 5 < 3) ||
            (x in 226..375 && y in 40..550 && (x + y) % 5 < 3) ||
            (x in 186..214 && y in 250..310)
    }
    fun mappedAndTrimmed(selectedOverlap: Int): List<PageRegion> {
        val percent = dev.nooir.bridge.PanelDetectionCore.overlapPaddingPercent(selectedOverlap)
        return dev.nooir.bridge.PanelDetectionCore.sourceRegions(
            confirmed, 400, 600, 400, 600, percent,
        ).map { PanelWhitespaceCrop.trim(it, paperInk) }
    }
    val minimal = mappedAndTrimmed(0)
    val protectedViews = mappedAndTrimmed(8)
    fun contains(r: PageRegion, x: Int, y: Int): Boolean =
        x in r.left until r.right && y in r.top until r.bottom
    check(minimal.size == 2 && protectedViews.size == 2)
    check(!contains(minimal[0], 214, 280) &&
        !contains(minimal[1], 186, 280)) {
        "Baseline minimal padding is not expected to repeat the whole crossing"
    }
    for (y in 250..310 step 5) for (x in 186..214 step 2) {
        check(protectedViews.all { contains(it, x, y) }) {
            "Protected crops must BOTH retain balloon pixels ($x, $y): $protectedViews"
        }
    }
    // Crop whitespaces only when safe: after trimming, every original ink
    // sample must survive in at least one output. Grouped fallbacks remain
    // undamaged and no panel order or source boundary is mutated.
    for (y in 0 until 600 step 2) for (x in 0 until 400 step 2) {
        if (paperInk(x, y)) check(protectedViews.any { contains(it, x, y) }) {
            "Overlap + trim must never lose original art at ($x, $y)"
        }
    }
    check(confirmed.regions == listOf(
        PageRegion(0, 0, 200, 600), PageRegion(200, 0, 400, 600)))
    check(protectedViews[0].left < protectedViews[1].left)

    println("Panel whitespace guard passed: paper margins, edge balloons, sparse pages, narrow panels")
}
