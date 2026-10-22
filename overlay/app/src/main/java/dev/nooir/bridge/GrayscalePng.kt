package dev.nooir.bridge

import java.io.ByteArrayOutputStream

/** Shared grayscale PNG encoder for Android and desktop EPUB/CBZ parity. */
object GrayscalePng {
    fun encode(page: QuantizedPage): ByteArray {
        val width = page.width
        val height = page.height
        val scanlines = ByteArray((width + 1) * height)
        for (y in 0 until height) {
            val row = y * (width + 1)
            scanlines[row] = 0 // PNG filter None
            for (x in 0 until width) {
                val level = page.levels[y * width + x].toInt() and 255
                require(level <= if (page.depth == 1) 1 else 3) { "Invalid grayscale pixel level" }
                scanlines[row + 1 + x] = (if (page.depth == 1) level * 255 else level * 85).toByte()
            }
        }
        val compressed = ByteArrayOutputStream()
        java.util.zip.DeflaterOutputStream(compressed).use { it.write(scanlines) }
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))
        fun chunk(name: String, data: ByteArray) {
            val type = name.toByteArray(Charsets.US_ASCII)
            fun writeInt(v: Int) {
                out.write((v ushr 24) and 255)
                out.write((v ushr 16) and 255)
                out.write((v ushr 8) and 255)
                out.write(v and 255)
            }
            writeInt(data.size)
            out.write(type)
            out.write(data)
            val crc = java.util.zip.CRC32()
            crc.update(type)
            crc.update(data)
            writeInt(crc.value.toInt())
        }
        val ihdr = ByteArray(13)
        for (i in 0..3) {
            ihdr[i] = (width ushr (24 - i * 8)).toByte()
            ihdr[4 + i] = (height ushr (24 - i * 8)).toByte()
        }
        ihdr[8] = 8 // 8 bits per sample
        ihdr[9] = 0 // grayscale, no alpha
        chunk("IHDR", ihdr)
        chunk("IDAT", compressed.toByteArray())
        chunk("IEND", byteArrayOf())
        return out.toByteArray()
    }
}
