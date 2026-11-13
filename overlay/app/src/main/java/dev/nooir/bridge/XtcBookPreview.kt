package dev.nooir.bridge

import java.io.File
import java.io.RandomAccessFile

/**
 * Reads pages from a FINISHED XTC/XTCH book, not from the conversion preview
 * pipeline. One page is decoded per request; no whole-book bitmap allocation.
 *
 * This deliberately reads only the shared on-disk format; it does not touch
 * reader firmware, convert media, or modify the source book.
 */
class XtcBookPreview(private val source: File) {
    val format: XtcFormat
    val pageCount: Int
    private val tableOffset: Long
    private val dataOffset: Long

    init {
        RandomAccessFile(source, "r").use { file ->
            require(file.length() >= 56) { "Truncated XTC/XTCH header" }
            val h = ByteArray(56)
            file.readFully(h)
            format = when {
                h[0] == 0x58.toByte() && h[1] == 0x54.toByte() &&
                    h[2] == 0x43.toByte() && h[3] == 0.toByte() -> XtcFormat.XTC
                h[0] == 0x58.toByte() && h[1] == 0x54.toByte() &&
                    h[2] == 0x43.toByte() && h[3] == 0x48.toByte() -> XtcFormat.XTCH
                else -> error("Not an XTC/XTCH book")
            }
            pageCount = u16(h, 6)
            require(pageCount in 1..65535) { "Invalid XTC page count" }
            tableOffset = u64(h, 24)
            dataOffset = u64(h, 32)
            require(tableOffset >= 56 && tableOffset <= file.length()) { "Invalid XTC index" }
            require(dataOffset >= tableOffset + pageCount.toLong() * 16 &&
                dataOffset <= file.length()) { "Truncated XTC index or page data" }
        }
    }

    fun page(index: Int): QuantizedPage {
        require(index in 0 until pageCount) { "Page outside XTC index" }
        RandomAccessFile(source, "r").use { file ->
            file.seek(tableOffset + index * 16L)
            val entry = ByteArray(16)
            file.readFully(entry)
            val pos = u64(entry, 0)
            val length = u32(entry, 8)
            val width = u16(entry, 12)
            val height = u16(entry, 14)
            // A preview must not allocate a hostile or corrupt 4 GiB bitmap.
            val pixels = width.toLong() * height.toLong()
            require(width in 1..8192 && height in 1..8192 && pixels <= 8_000_000L) {
                "Page dimensions exceed safe preview size"
            }
            val plane = width.toLong() * ((height + 7) / 8)
            val expectedData = if (format == XtcFormat.XTC)
                ((width + 7) / 8).toLong() * height else plane * 2
            require(length == expectedData + 22L) { "XTC page byte length mismatch" }
            require(pos >= dataOffset && pos <= file.length() &&
                length <= file.length() - pos) { "XTC page data outside file" }
            file.seek(pos)
            val header = ByteArray(22)
            file.readFully(header)
            val expectedMagic = if (format == XtcFormat.XTC) 0x47 else 0x48
            require(header[0] == 0x58.toByte() && header[1] == 0x54.toByte() &&
                (header[2].toInt() and 255) == expectedMagic && header[3] == 0.toByte()) {
                "Unexpected embedded XTC page format"
            }
            require(u16(header, 4) == width && u16(header, 6) == height &&
                u32(header, 10) == expectedData) { "XTC page header does not match index" }
            val packed = ByteArray(expectedData.toInt())
            file.readFully(packed)
            val levels = ByteArray(pixels.toInt())
            if (format == XtcFormat.XTC) {
                val rowBytes = (width + 7) / 8
                for (y in 0 until height) for (x in 0 until width) {
                    val b = packed[y * rowBytes + x / 8].toInt() and 255
                    levels[y * width + x] = ((b ushr (7 - x % 8)) and 1).toByte()
                }
            } else {
                val bytesPerColumn = (height + 7) / 8
                val planeSize = width * bytesPerColumn
                for (x in 0 until width) for (y in 0 until height) {
                    val at = (width - 1 - x) * bytesPerColumn + y / 8
                    val shift = 7 - y % 8
                    val stored = (((packed[at].toInt() and 255) ushr shift) and 1) * 2 +
                        (((packed[at + planeSize].toInt() and 255) ushr shift) and 1)
                    // Inverse of XtcCodec's compatibility gray mapping [3,1,2,0].
                    levels[y * width + x] = when (stored) {
                        3 -> 0; 1 -> 1; 2 -> 2; else -> 3
                    }.toByte()
                }
            }
            return QuantizedPage(width, height, levels, format.depth)
        }
    }

    private fun u16(b: ByteArray, i: Int): Int =
        (b[i].toInt() and 255) or ((b[i + 1].toInt() and 255) shl 8)

    private fun u32(b: ByteArray, i: Int): Long =
        (0..3).fold(0L) { n, j -> n or ((b[i + j].toLong() and 255) shl (8 * j)) }

    private fun u64(b: ByteArray, i: Int): Long {
        // File offsets outside signed Long are never legitimate on Android/JVM.
        require(b[i + 7].toInt() and 0x80 == 0) { "XTC offset overflow" }
        return (0..7).fold(0L) { n, j -> n or ((b[i + j].toLong() and 255) shl (8 * j)) }
    }
}
