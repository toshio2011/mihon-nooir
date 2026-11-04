import dev.nooir.bridge.PageRegion
import dev.nooir.bridge.PanelDetectionCore

/**
 * New art is procedural test geometry, NEVER extracted private manga pixels.
 * Two clear, tilted black frame rails have bright paper on only ONE side
 * plus an interrupted ~40% white flank on the other.
 */
private fun angledArtwork(twoRails: Boolean = true,
    oppositeDirections: Boolean = false, blankBottom: Boolean = false,
): Triple<Int, Int, ByteArray> {
    val w = 560
    val h = 900
    val pixels = ByteArray(w * h) {
        val x = it % w
        val y = it / w
        (if ((x / 16 + y / 17) % 5 == 0) 75 else 245).toByte()
    }
    val rails = if (twoRails) listOf(390 to -16,
        530 to (if (oppositeDirections) 16 else -16)) else listOf(390 to -16)
    for ((center, tilt) in rails) for (x in 0 until w) {
        val y = center + (x - w / 2) * tilt / 100
        for (offset in -3..3) pixels[(y + offset) * w + x] = 25.toByte()
        for (offset in -13..-6) pixels[(y + offset) * w + x] = 255.toByte()
        if (x % 5 <= 1) for (offset in 7..11)
            pixels[(y + offset) * w + x] = 255.toByte()
    }
    if (blankBottom) for (y in 620 until h) for (x in 0 until w)
        pixels[y * w + x] = 245.toByte()
    return Triple(w, h, pixels)
}

private fun fullCoverage(regions: List<PageRegion>, w: Int, h: Int) {
    check(regions.first().top == 0 && regions.last().bottom == h)
    for (y in 0 until h step 7) for (x in 0 until w step 5) {
        check(regions.any { x in it.left until it.right &&
            y in it.top until it.bottom }) {
            "Missing source image pixel at ($x,$y) in $regions"
        }
    }
}

fun main() {
    val (w, h, img) = angledArtwork()
    val pair = PanelDetectionCore.recoverPairedSlantedFramedRows(w, h, img)
    check(pair != null && pair.size == 3) {
        "Two near-parallel thick asymmetric slanted rails should recover 3 overlapping story views: $pair"
    }
    fullCoverage(pair, w, h)
    check(pair[0].bottom > pair[1].top &&
        pair[1].bottom > pair[2].top &&
        pair.all { it.left == 0 && it.right == w }) {
        "Diagonal crop must overlap, never discard the border/art: $pair"
    }

    // Row by Row's existing strict split logic is unchanged.
    val rows = PanelDetectionCore.detectRows(w, h, img)
    check(rows.usedFullPageFallback) { "No new angled inference in independent Row by Row: $rows" }
    val detected = PanelDetectionCore.detect(w, h, img, true)
    check(!detected.usedFullPageFallback && detected.regions.size == 3 &&
        detected.reason.contains("two strong parallel slanted frame rails")) {
        "Combined Panel-by-Panel path must use the new guarded paired recovery: $detected"
    }
    check(detected.regions == pair)
    check(PanelDetectionCore.detect(w, h, img, false).regions == pair) {
        "Full-width tiers must stay in top-to-bottom order in RTL and LTR"
    }

    val (singleW, singleH, single) = angledArtwork(twoRails = false)
    check(PanelDetectionCore.recoverPairedSlantedFramedRows(singleW, singleH, single) == null) {
        "One dramatic stroke must never become several manga panels"
    }
    val (_, _, diverging) = angledArtwork(oppositeDirections = true)
    check(PanelDetectionCore.recoverPairedSlantedFramedRows(w, h, diverging) == null) {
        "Crossing / different-angle action strokes are not parallel frame rails"
    }
    val (_, _, blank) = angledArtwork(blankBottom = true)
    check(PanelDetectionCore.recoverPairedSlantedFramedRows(w, h, blank) == null) {
        "No independently illustrated third zone: no invented panel"
    }
    val solid = ByteArray(w * h) { 70.toByte() }
    check(PanelDetectionCore.recoverPairedSlantedFramedRows(w, h, solid) == null)
    println("Paired slanted rails passed: 3 illustrated views; no lost pixels, lone/art-only stroke veto")
}
