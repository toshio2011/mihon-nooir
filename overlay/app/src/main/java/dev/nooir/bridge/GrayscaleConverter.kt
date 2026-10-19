package dev.nooir.bridge

/** Converts 8-bit luminance into the page levels used by XTC and XTCH.
 * Uses Floyd–Steinberg error diffusion similar to MangaPoint's default preset.
 * '0' means black; highest level means white.
 */
object GrayscaleConverter {
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