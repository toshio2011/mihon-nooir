import dev.nooir.bridge.PageRegion
import dev.nooir.bridge.PanelDetectionCore

/** Independently authored illustrations-only synthetic geometry; no manga. */
private fun texturedPage(width: Int, height: Int): ByteArray {
    val image = ByteArray(width * height)
    for (y in 0 until height) for (x in 0 until width) {
        // Dense varied marks without any 1px safe full-width gutter.
        image[y * width + x] =
            (if ((x * 13 + y * 31) % 97 < 34) 66 else 248).toByte()
    }
    return image
}

private fun covered(regions: List<PageRegion>, width: Int, height: Int) {
    check(regions.all { it.left >= 0 && it.top >= 0 && it.right <= width &&
        it.bottom <= height && it.width > 0 && it.height > 0 })
    for (y in 0 until height step 7) for (x in 0 until width step 7) {
        check(regions.any { x in it.left until it.right && y in it.top until it.bottom }) {
            "The fallback lost original source pixel ($x,$y): $regions"
        }
    }
}

fun main() {
    val width = 560
    val height = 900
    val textured = texturedPage(width, height)

    // A truly unsplittable but strongly illustrated portrait page is
    // readable as three intentionally overlapping story views.
    val trace = mutableListOf<dev.nooir.bridge.PanelDebugEvent>()
    val result = PanelDetectionCore.detect(width, height, textured, true) {
        trace += it
    }
    check(!result.usedFullPageFallback && result.regions.size == 3 &&
        result.reason.contains("overlapping reading rows")) {
        "All detectors should fail before protected rows: $result"
    }
    val expected = listOf(
        PageRegion(0, 0, width, 360),
        PageRegion(0, 270, width, 630),
        PageRegion(0, 540, width, 900),
    )
    check(result.regions == expected) { "The 10% overlap is mandatory: $result" }
    covered(result.regions, width, height)
    check(trace.any { it.stage == "page" && it.decision == "accept" &&
        it.detail.contains("overlapping reading rows") })
    check(PanelDetectionCore.detect(width, height, textured, false).regions == expected)

    val scaled = PanelDetectionCore.sourceRegions(
        result, width, height, 1120, 1800, paddingPercent = 0)
    covered(scaled, 1120, 1800)
    check(scaled.zipWithNext().all { (a,b) -> a.bottom > b.top }) {
        "Padding must not erase the deliberate overlap: $scaled"
    }

    // A clean, continuous vertical separator inside an overlap window
    // may produce two safe side-by-side views, RTL or LTR. NO recursive
    // dialogue fragments are allowed.
    val clean = textured.copyOf()
    for (y in 0 until height) for (x in 270 until 290)
        clean[y * width + x] = 255.toByte()
    val viewsRtl = PanelDetectionCore.recoverOverlappingRowViews(
        width, height, clean, true)
    val viewsLtr = PanelDetectionCore.recoverOverlappingRowViews(
        width, height, clean, false)
    check(viewsRtl != null && viewsRtl.size == 6) { "Missing inner columns: $viewsRtl" }
    check(viewsLtr != null && viewsLtr.size == 6) { "Missing LTR views: $viewsLtr" }
    check(viewsRtl.zipWithNext().filterIndexed { i, _ -> i % 2 == 0 }
        .all { (a, b) -> a.left > b.left })
    check(viewsLtr.zipWithNext().filterIndexed { i, _ -> i % 2 == 0 }
        .all { (a, b) -> a.left < b.left })
    covered(viewsRtl, width, height)
    covered(viewsLtr, width, height)

    // No real artwork in any band = do not invent manga story rows.
    for (shade in listOf(0, 100, 225, 255)) {
        val solid = ByteArray(width * height) { shade.toByte() }
        check(PanelDetectionCore.recoverOverlappingRowViews(
            width, height, solid, true) == null) {
            "Uniform solid/splash background cannot be force-cut"
        }
    }
    // Dark cover image with one illustrated title region still retains
    // the original page; a mostly-dark band triggers the splash guard.
    val cover = ByteArray(width * height) { 20.toByte() }
    for (y in 12 until 160) for (x in 40 until width-40)
        cover[y*width+x] = (if ((x+y)%5==0) 255 else 20).toByte()
    check(PanelDetectionCore.recoverOverlappingRowViews(
        width,height,cover,true) == null)

    // Text-heavy page protection is implemented before this fallback.
    val printed = ByteArray(width * height) { 255.toByte() }
    for (line in 0 until 24) {
        val start = 180 + line * 24
        for (y in start until start+9) for (x in 46 until width-46)
            printed[y*width+x] = 88.toByte()
    }
    val prose = PanelDetectionCore.detect(width,height,printed,true)
    check(prose.preserveSingleFrame && prose.regions.size == 1) {
        "Do not turn prose paragraph lines into overlapping picture rows: $prose"
    }

    // A panel page which already has validated conventional boundaries
    // MUST NOT invoke the last-resort overlap mechanism.
    val grid = ByteArray(width*height) { 255.toByte() }
    for ((top,bottom) in listOf(10 to 278,310 to 580,615 to 887)) {
        for (y in top until bottom) {
            for (x in 12 until 262) grid[y*width+x]=75.toByte()
            for (x in 297 until 546) grid[y*width+x]=95.toByte()
        }
    }
    val ordinary = PanelDetectionCore.detect(width,height,grid,true)
    check(!ordinary.reason.contains("overlapping reading rows") &&
        ordinary.regions.size >= 3) {
        "Validated story panels must keep their normal detector: $ordinary"
    }
    // Explicit long/webtoon page is outside the guard.
    check(PanelDetectionCore.recoverOverlappingRowViews(200,900,
        texturedPage(200,900),true)==null)

    println("Last-resort overlapping rows passed: full coverage, RTL/LTR, safe columns, protected content")
}
