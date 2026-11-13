package dev.nooir.bridge

/** Converts 8-bit luminance into the page levels used by XTC and XTCH.
 * Uses Floyd–Steinberg error diffusion similar to MangaPoint's default preset.
 * '0' means black; highest level means white.
 */
object GrayscaleConverter {
    /** Explicit opt-in alternatives. Legacy jobs retain their boolean dithering behavior. */
    fun quantize(width: Int, height: Int, luminance: ByteArray, depth: Int,
        algorithm: String, legacyDither: Boolean = true): QuantizedPage {
        require(width > 0 && height > 0 && width.toLong() * height == luminance.size.toLong())
        require(depth == 1 || depth == 2)
        val mode = when (algorithm) {
            "ATKINSON", "BAYER_8X8", "THRESHOLD", "FLOYD_STEINBERG" -> algorithm
            else -> if (legacyDither) "FLOYD_STEINBERG" else "THRESHOLD"
        }
        if (mode == "FLOYD_STEINBERG") return dither(width, height, luminance, depth)
        val max = if (depth == 1) 1 else 3
        val output = ByteArray(luminance.size)
        if (mode == "THRESHOLD") {
            for (i in luminance.indices) {
                output[i] = (((luminance[i].toInt() and 255) * max + 127) / 255).toByte()
            }
        } else if (mode == "BAYER_8X8") {
            // Ordered dots retain reproducible manga screentones (no diffuse noise).
            val matrix = intArrayOf(
                0, 48, 12, 60, 3, 51, 15, 63,
                32, 16, 44, 28, 35, 19, 47, 31,
                8, 56, 4, 52, 11, 59, 7, 55,
                40, 24, 36, 20, 43, 27, 39, 23,
                2, 50, 14, 62, 1, 49, 13, 61,
                34, 18, 46, 30, 33, 17, 45, 29,
                10, 58, 6, 54, 9, 57, 5, 53,
                42, 26, 38, 22, 41, 25, 37, 21
            )
            for (y in 0 until height) for (x in 0 until width) {
                val i = y * width + x
                val lum = luminance[i].toInt() and 255
                val threshold = matrix[(y % 8) * 8 + x % 8] + 0.5
                output[i] = if (depth == 1)
                    (if (lum > threshold * 255.0 / 64.0) 1 else 0).toByte()
                else (lum * 3.0 / 255.0 + threshold / 64.0 - 0.5 + 0.5)
                    .toInt().coerceIn(0, max).toByte()
            }
        } else {
            // Atkinson diffuses six one-eighth shares, discarding the final
            // quarter. Three scanlines of bounded scratch, never full image.
            var row = IntArray(width + 4)
            var next = IntArray(width + 4)
            var after = IntArray(width + 4)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val i = y * width + x
                    val brightness = ((luminance[i].toInt() and 255) + row[x + 2]).coerceIn(0, 255)
                    val discrete = ((brightness * max + 127) / 255).coerceIn(0, max)
                    output[i] = discrete.toByte()
                    val error = (brightness - discrete * 255 / max) / 8
                    if (x + 1 < width) row[x + 3] += error
                    if (x + 2 < width) row[x + 4] += error
                    if (y + 1 < height) {
                        if (x > 0) next[x + 1] += error
                        next[x + 2] += error
                        if (x + 1 < width) next[x + 3] += error
                    }
                    if (y + 2 < height) after[x + 2] += error
                }
                val old = row
                row = next
                next = after
                after = old
                after.fill(0)
            }
        }
        return QuantizedPage(width, height, output, depth)
    }

    fun dither(width: Int, height: Int, luminance: ByteArray, depth: Int): QuantizedPage {
        require(width > 0 && height > 0 && width.toLong() * height.toLong() == luminance.size.toLong())
        require(depth == 1 || depth == 2)
        val highest = if (depth == 1) 1 else 3
        val levelOut = ByteArray(luminance.size)
        var thisRow = FloatArray(width + 2)
        var nextRow = FloatArray(width + 2)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = y * width + x
                val brightness = ((luminance[i].toInt() and 255) + thisRow[x + 1]).coerceIn(0f, 255f)
                val discrete = (brightness * highest / 255f + 0.5f).toInt().coerceIn(0, highest)
                levelOut[i] = discrete.toByte()
                val error = brightness - discrete * 255f / highest
                if (x + 1 < width) thisRow[x + 2] += error * 7 / 16f
                if (y + 1 < height) {
                    if (x > 0) nextRow[x] += error * 3 / 16f
                    nextRow[x + 1] += error * 5 / 16f
                    if (x + 1 < width) nextRow[x + 2] += error / 16f
                }
            }
            val old = thisRow
            thisRow = nextRow
            nextRow = old
            nextRow.fill(0f)
        }
        return QuantizedPage(width, height, levelOut, depth)
    }
}