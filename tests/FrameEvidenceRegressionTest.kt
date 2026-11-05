import dev.nooir.bridge.PanelDetectionCore
import dev.nooir.bridge.PageRegion

/**
 * Independently created grayscale panels, no manga source/pixels copied.
 * Real One Piece thumbnails are private external QA data, NEVER checked in.
 */
private data class Rect(val l: Int, val t: Int, val r: Int, val b: Int)

private fun framedPage(
    w: Int = 560, h: Int = 900,
    background: Int = 255, drawing: Int = 28,
    panels: List<Rect> = listOf(
        Rect(14, 14, 270, 285), Rect(290, 14, 546, 285),
        Rect(14, 315, 270, 585), Rect(290, 315, 546, 585),
        Rect(14, 615, 270, 886), Rect(290, 615, 546, 886),
    ),
): ByteArray {
    val page = ByteArray(w * h) { background.toByte() }
    for (p in panels) {
        for (y in p.t until p.b) for (x in p.l until p.r) {
            // Grid of joined frame/art strokes: one connected panel component
            // with a mostly straight, 2px supporting outer boundary.
            val onFrame = x - p.l < 3 || p.r - x <= 3 ||
                y - p.t < 3 || p.b - y <= 3
            val drawingStroke = (x - p.l) % 13 < 3 ||
                (y - p.t) % 15 < 3
            if (onFrame || drawingStroke) page[y * w + x] = drawing.toByte()
        }
    }
    return page
}
fun main() {
    val w = 560
    val h = 900
    val white = framedPage()
    val rtlTrace = mutableListOf<dev.nooir.bridge.PanelDebugEvent>()
    val rtl = PanelDetectionCore.recoverFrameEvidenceViews(
        w, h, white, rightToLeft = true, trace = rtlTrace::add)
    check(rtl != null && rtl.size == 6) {
        "Must find six independently framed image regions: $rtl"
    }
    val ltr = PanelDetectionCore.recoverFrameEvidenceViews(
        w, h, white, rightToLeft = false)
    check(ltr != null && ltr.size == 6) { "LTR frames missing: $ltr" }
    check(rtl.indices.all { i ->
        rtl[i].width >= 240 && rtl[i].height >= 250
    }) { "Don't promote small speech/text snippets to reading screens" }
    for (i in 0 until 6 step 2) {
        check(rtl[i].left > rtl[i + 1].left) { "Manga order RTL: $rtl" }
        check(ltr[i].left < ltr[i + 1].left) { "Comic order LTR: $ltr" }
    }
    check(rtlTrace.any {
        it.stage == "frame-confidence" && it.decision == "accept"
    })
    // Full-page overview in the exporter independently retains full-page
    // context; component rectangles themselves must contain their ink.
    val bounds = listOf(
        Rect(14, 14, 270, 285), Rect(290, 14, 546, 285),
        Rect(14, 315, 270, 585), Rect(290, 315, 546, 585),
        Rect(14, 615, 270, 886), Rect(290, 615, 546, 886),
    )
    for (target in bounds) {
        check(rtl.any { it.left <= target.l + 3 &&
            it.top <= target.t + 3 &&
            it.right >= target.r - 3 &&
            it.bottom >= target.b - 3 }) {
            "An actual framed artwork area was not preserved: $target"
        }
    }

    // Inverted white-on-black story art must work WITHOUT assuming white
    // paper or a hardcoded black-ink-only foreground.
    val inverted = framedPage(background = 0, drawing = 245)
    check(PanelDetectionCore.recoverFrameEvidenceViews(
        w, h, inverted, true)?.size == 6)

    // One giant outlined splash illustration has only one frame and must NOT
    // be divided just to increase the number of crops.
    val splash = framedPage(panels = listOf(Rect(8, 8, 552, 892)))
    check(PanelDetectionCore.recoverFrameEvidenceViews(
        w, h, splash, true) == null)

    // Even two strong frames are insufficient as new rescue evidence; keep
    // strict existing row detectors and overlap fallback in charge.
    val two = framedPage(panels = listOf(
        Rect(20, 15, 540, 427), Rect(20, 457, 540, 885),
    ))
    check(PanelDetectionCore.recoverFrameEvidenceViews(w, h, two, true) == null)

    for (background in listOf(0, 100, 220, 255)) {
        val empty = ByteArray(w * h) { background.toByte() }
        check(PanelDetectionCore.recoverFrameEvidenceViews(
            w, h, empty, true) == null)
    }

    // Tiled letter-like strokes without intact bounding frames don't get
    // promoted into dozens of tiny panel candidates.
    val lettering = ByteArray(w * h) { 255.toByte() }
    for (line in 0 until 23) {
        val y = 125 + line * 27
        for (x in 36..520 step 42) {
            for (yy in y until y + 9)
                for (xx in x until x + 17)
                    lettering[yy * w + xx] = 35.toByte()
        }
    }
    check(PanelDetectionCore.recoverFrameEvidenceViews(
        w, h, lettering, true) == null)
    val preserved = PanelDetectionCore.detect(w, h, lettering, true)
    check(preserved.regions.size == 1 && preserved.preserveSingleFrame) {
        "Text rhythm protection must execute before frame recovery: $preserved"
    }

    // Verify narrow independent windows are NOT confused with full-image
    // frames and the experimental recovery stays within aspect/dimension
    // limits, leaving explicit webtoon modes unchanged.
    check(PanelDetectionCore.recoverFrameEvidenceViews(
        200, 900, framedPage(w = 200, h = 900,
            panels = listOf(Rect(10, 10, 190, 890))), true) == null)

    println("Frame confidence passed: 6 panels, RTL/LTR, dark background, splash/text veto")
}
