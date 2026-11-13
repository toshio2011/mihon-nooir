package dev.nooir.bridge

fun main() {
    val w = 64
    val h = 32
    val gradient = ByteArray(w * h) { i -> ((i % w) * 255 / (w - 1)).toByte() }
    for (depth in 1..2) {
        val max = if (depth == 1) 1 else 3
        for (mode in listOf("FLOYD_STEINBERG", "ATKINSON", "BAYER_8X8", "THRESHOLD")) {
            val page = GrayscaleConverter.quantize(w, h, gradient, depth, mode)
            check(page.depth == depth && page.width == w && page.height == h)
            check(page.levels.all { (it.toInt() and 255) in 0..max })
            check(page.levels.contentEquals(GrayscaleConverter.quantize(w, h, gradient, depth, mode).levels))
        }
        val original = GrayscaleConverter.dither(w, h, gradient, depth)
        val legacy = GrayscaleConverter.quantize(w, h, gradient, depth, "LEGACY")
        check(legacy.levels.contentEquals(original.levels)) {
            "Existing Floyd–Steinberg output changed"
        }
        val thresholdLegacy = GrayscaleConverter.quantize(w, h, gradient, depth,
            "LEGACY", legacyDither = false)
        val thresholdNew = GrayscaleConverter.quantize(w, h, gradient, depth, "THRESHOLD")
        check(thresholdLegacy.levels.contentEquals(thresholdNew.levels))
        val bayer = GrayscaleConverter.quantize(64, 64, ByteArray(4096) { 128.toByte() },
            depth, "BAYER_8X8")
        check(bayer.levels.toSet().size >= 2) { "Ordered dots lost their pattern" }
    }
    for (depth in 1..2) {
        val max = if (depth == 1) 1 else 3
        for (mode in listOf("FLOYD_STEINBERG", "ATKINSON", "BAYER_8X8", "THRESHOLD")) {
            val black = GrayscaleConverter.quantize(8, 8, ByteArray(64), depth, mode)
            val white = GrayscaleConverter.quantize(8, 8, ByteArray(64) { 255.toByte() }, depth, mode)
            check(black.levels.all { it == 0.toByte() })
            check(white.levels.all { it == max.toByte() })
        }
    }
    println("PASS: bounded rendering methods, repeatable Bayer patterns, and unchanged legacy output")
}
