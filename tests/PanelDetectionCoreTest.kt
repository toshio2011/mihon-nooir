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

    // Staggered columns cannot be sorted safely by naive X/Y; preserve whole page.
    val staggered = ByteArray(width * height) { 255.toByte() }
    for (y in 3 until 145) for (x in 4 until 144) staggered[y * width + x] = 100.toByte()
    for (y in 158 until height - 4) for (x in 4 until 144) staggered[y * width + x] = 100.toByte()
    for (y in 3 until 205) for (x in 156 until width - 4) staggered[y * width + x] = 100.toByte()
    for (y in 218 until height - 4) for (x in 156 until width - 4) staggered[y * width + x] = 100.toByte()
    val uncertain = PanelDetectionCore.detect(width, height, staggered, true)
    check(uncertain.usedFullPageFallback) { "Ambiguous cross-column tiers must not be guessed" }

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
