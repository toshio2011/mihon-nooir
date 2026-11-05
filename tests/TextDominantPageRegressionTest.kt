import dev.nooir.bridge.PageRegion
import dev.nooir.bridge.PanelDetectionCore

private fun rect(img: ByteArray, w: Int, x0: Int, y0: Int, x1: Int, y1: Int, ink: Int) {
    for (y in y0 until y1) for (x in x0 until x1) img[y * w + x] = ink.toByte()
}

fun main() {
    val w = 565
    val h = 900

    // Synthetic illustrated header + 25 paragraphs/printed lines.
    // A clean gutter between lines is NOT a manga panel separation.
    val prose = ByteArray(w * h) { 255.toByte() }
    rect(prose, w, 40, 90, 525, 182, 80)
    for (i in 0 until 25) {
        val y = 220 + i * 22
        rect(prose, w, 52, y, 495, y + 10, 75)
    }
    val trace = mutableListOf<dev.nooir.bridge.PanelDebugEvent>()
    val page = PanelDetectionCore.detect(w, h, prose, true) { trace.add(it) }
    check(page.regions == listOf(PageRegion(0, 0, w, h)))
    check(page.preserveSingleFrame && !page.usedFullPageFallback) {
        "Prose must be one protected frame, not 25 paragraph zooms: $page"
    }
    check(trace.any { it.stage == "page" && it.decision == "keep" &&
        it.detail.contains("text-like horizontal bands") })
    check(PanelDetectionCore.detect(w, h, prose, false) == page) {
        "RTL/LTR cannot affect the single protected source frame"
    }

    // A table-of-contents style page has numerous short lines and up to
    // TWO decorative long header/footer regions. Still protect its contents.
    val contents = ByteArray(w * h) { 255.toByte() }
    rect(contents, w, 30, 92, 530, 152, 80)
    rect(contents, w, 32, 785, 533, 825, 85)
    for (i in 0 until 21) {
        val y = 220 + 25 * i
        rect(contents, w, 45, y, 508, y + 12, 105)
    }
    check(PanelDetectionCore.detect(w, h, contents, true).preserveSingleFrame)

    // A comic with three large illustrated rows is NOT a prose page.
    val comic = ByteArray(w * h) { 255.toByte() }
    for ((from, to) in listOf(18 to 280, 302 to 585, 607 to 880)) {
        rect(comic, w, 22, from, 266, to, 90)
        rect(comic, w, 290, from, 542, to, 110)
    }
    val art = PanelDetectionCore.detect(w, h, comic, true)
    check(!art.preserveSingleFrame && art.regions.size >= 3) {
        "Normal illustrated rows must remain eligible for real splitting: $art"
    }

    // A handful of captions or a narrow text header cannot veto comic art.
    val mixed = comic.copyOf()
    for (i in 0 until 6) {
        rect(mixed, w, 32, 280 + 3*i, 530, 282 + 3*i, 70)
    }
    check(!PanelDetectionCore.detect(w, h, mixed, true).preserveSingleFrame)

    println("Text-rhythm guard passed: prose/contents protected, illustrated panels preserved")
}
