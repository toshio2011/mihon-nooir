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
    println("Panel whitespace guard passed: paper margins, edge balloons, sparse pages, narrow panels")
}
