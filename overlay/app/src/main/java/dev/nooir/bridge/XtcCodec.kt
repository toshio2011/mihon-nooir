package dev.nooir.bridge

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

/** Pure JVM XTC/XTCH writer, based on MangaPoint's documented CrossPoint-compatible layout.
 * Designed for use as an export module in an Android Mihon fork.
 * No Android APIs or server required.
 *
 * MIT reference: https://github.com/W1seGit/MangaPoint (converter/xtc).
 */
enum class XtcFormat(val extension: String, val depth: Int) { XTC("xtc", 1), XTCH("xtch", 2) }
enum class ReadingDirection(val code: Int) { LEFT_TO_RIGHT(0), RIGHT_TO_LEFT(1), TOP_TO_BOTTOM(2) }

data class QuantizedPage(val width: Int, val height: Int, val levels: ByteArray, val depth: Int) {
    init {
        require(width in 1..65535 && height in 1..65535) { "Invalid page dimensions" }
        require(width.toLong() * height.toLong() == levels.size.toLong()) { "Pixel count mismatch" }
        require(depth == 1 || depth == 2) { "Only 1-bit or 2-bit output supported" }
    }
}

data class BookMetadata(
    val title: String,
    val author: String = "",
    val publisher: String = "",
    val language: String = "",
    val creationUnixSeconds: Long = 0L,
    val coverPage: Int? = null
)
data class BookChapter(val name: String, val firstPage: Int, val lastPage: Int)

data class XtcWriteResult(val pageCount: Int, val fileBytes: Long)

object XtcCodec {
    private val grayMap = intArrayOf(3, 1, 2, 0)

    fun encodePage(page: QuantizedPage): ByteArray {
        val w = page.width
        val h = page.height
        val depth = page.depth
        val rowLength = (w + 7) / 8
        // Folio Nooir reads XTCH planes as ceil(width*height/8) bytes each,
        // while the column-major writer needs width*ceil(height/8). Those
        // layouts differ for non-8-aligned heights; fail rather than emit a
        // deceptively valid image whose two gray planes will be misread.
        require(depth != 2 || h % 8 == 0) {
            "XTCH height must be divisible by 8 for Folio/CrossPoint-compatible decoding"
        }
        val columnLength = (h + 7) / 8
        val bitmapSize = if (depth == 1) rowLength.toLong() * h else 2L * w * columnLength
        require(bitmapSize <= Int.MAX_VALUE - 22) { "Page too large" }
        val result = ByteArray(22 + bitmapSize.toInt())
        val magic = if (depth == 1) byteArrayOf(0x58, 0x54, 0x47, 0) else byteArrayOf(0x58, 0x54, 0x48, 0)
        magic.copyInto(result, 0)
        setU16(result, 4, w)
        setU16(result, 6, h)
        setU32(result, 10, bitmapSize)
        if (depth == 1) {
            for (y in 0 until h) for (x in 0 until w) {
                val brightness = page.levels[y * w + x].toInt() and 0xff
                require(brightness in 0..1) { "1-bit page level outside 0..1" }
                if (brightness != 0) {
                    val index = 22 + y * rowLength + x / 8
                    result[index] = (result[index].toInt() or (0x80 ushr (x % 8))).toByte()
                }
            }
        } else {
            val planeLength = w * columnLength
            for (x in 0 until w) for (y in 0 until h) {
                val brightness = page.levels[y * w + x].toInt() and 0xff
                require(brightness in 0..3) { "2-bit page level outside 0..3" }
                val stored = grayMap[brightness]
                val position = 22 + (w - 1 - x) * columnLength + y / 8
                val mask = 0x80 ushr (y % 8)
                if ((stored and 2) != 0) result[position] = (result[position].toInt() or mask).toByte()
                if ((stored and 1) != 0) {
                    val lo = position + planeLength
                    result[lo] = (result[lo].toInt() or mask).toByte()
                }
            }
        }
        return result
    }

    /** Streams encoded pages to a spool on disk to avoid accumulating a large manga in RAM.
     * [pages] is consumed once. A new page can be produced and discarded each iteration.
     */
    fun write(
        format: XtcFormat,
        pages: Sequence<QuantizedPage>,
        output: OutputStream,
        spoolDirectory: File,
        metadata: BookMetadata? = null,
        chapters: List<BookChapter> = emptyList(),
        chapterResolver: ((Int) -> List<BookChapter>)? = null,
        direction: ReadingDirection = ReadingDirection.RIGHT_TO_LEFT,
        onPageEncoded: ((Int) -> Unit)? = null,
    ): XtcWriteResult {
        require(spoolDirectory.isDirectory || spoolDirectory.mkdirs()) { "Cannot create spool directory" }
        val temp = File.createTempFile("nooir-pages-", ".bin", spoolDirectory)
        try {
            val entries = ArrayList<Triple<Int, Int, Int>>() // encoded size, width, height
            var dataLength = 0L
            BufferedOutputStream(FileOutputStream(temp)).use { sink ->
                for (page in pages) {
                    require(page.depth == format.depth) { "Page depth does not match $format" }
                    require(entries.size < 65535) { "Maximum 65535 pages" }
                    val encoded = encodePage(page)
                    sink.write(encoded)
                    entries.add(Triple(encoded.size, page.width, page.height))
                    dataLength += encoded.size
                    onPageEncoded?.invoke(entries.size)
                }
            }
            require(entries.isNotEmpty()) { "No manga pages to export" }
            val resolvedChapters = chapterResolver?.invoke(entries.size) ?: chapters
            require(resolvedChapters.size <= 65535) { "Too many chapters" }
            for (c in resolvedChapters) require(c.firstPage in entries.indices && c.lastPage in entries.indices && c.firstPage <= c.lastPage) {
                "Chapter '${c.name}' refers to pages outside the exported book"
            }
            val metaLength = if (metadata != null) 256 else 0
            val chapterLength = resolvedChapters.size.toLong() * 96
            val chapterOffset = 56L + metaLength
            val tableOffset = chapterOffset + chapterLength
            val dataOffset = tableOffset + entries.size * 16L
            require(dataOffset <= 0xffffffffL) { "XTC header too large" }
            require(dataOffset <= Long.MAX_VALUE - dataLength) { "XTC output too large" }
            val header = ByteArray(56)
            val fileMagic = if (format == XtcFormat.XTC) byteArrayOf(0x58, 0x54, 0x43, 0) else byteArrayOf(0x58, 0x54, 0x43, 0x48)
            fileMagic.copyInto(header)
            header[4] = 1 // version major 1, minor 0
            setU16(header, 6, entries.size)
            header[8] = direction.code.toByte()
            header[9] = if (metadata != null) 1 else 0
            header[11] = if (resolvedChapters.isNotEmpty()) 1 else 0
            setU32(header, 12, 1) // currentPage, 1-based
            setU64(header, 16, if (metadata != null) 56 else 0)
            setU64(header, 24, tableOffset)
            setU64(header, 32, dataOffset)
            setU64(header, 40, 0) // thumbnail offset
            setU32(header, 48, if (resolvedChapters.isNotEmpty()) chapterOffset else 0)
            output.write(header)
            if (metadata != null) {
                val meta = ByteArray(256)
                setFixedUtf8(meta, 0, 128, metadata.title)
                setFixedUtf8(meta, 128, 64, metadata.author)
                setFixedUtf8(meta, 192, 32, metadata.publisher)
                setFixedUtf8(meta, 224, 16, metadata.language)
                setU32(meta, 240, metadata.creationUnixSeconds)
                setU16(meta, 244, metadata.coverPage ?: 0xffff)
                setU16(meta, 246, resolvedChapters.size)
                output.write(meta)
            }
            for (c in resolvedChapters) {
                val bytes = ByteArray(96)
                setFixedUtf8(bytes, 0, 80, c.name)
                // Folio Nooir's parser expects 1-based chapter indexes and
                // subtracts 1 during parsing. The zero-based page table and
                // metadata coverPage index stay untouched.
                setU16(bytes, 80, c.firstPage + 1)
                setU16(bytes, 82, c.lastPage + 1)
                output.write(bytes)
            }
            var pos = dataOffset
            for ((size, w, h) in entries) {
                val entry = ByteArray(16)
                setU64(entry, 0, pos)
                setU32(entry, 8, size.toLong())
                setU16(entry, 12, w)
                setU16(entry, 14, h)
                output.write(entry)
                pos += size
            }
            BufferedInputStream(FileInputStream(temp)).use { input -> input.copyTo(output, 64 * 1024) }
            output.flush()
            return XtcWriteResult(entries.size, dataOffset + dataLength)
        } finally {
            temp.delete()
        }
    }

    private fun setFixedUtf8(arr: ByteArray, start: Int, length: Int, s: String) {
        var pos = start
        for (cp in s.codePoints().toArray()) {
            val enc = String(Character.toChars(cp)).toByteArray(StandardCharsets.UTF_8)
            if (pos + enc.size >= start + length) break // reserve terminator
            enc.copyInto(arr, pos)
            pos += enc.size
        }
    }
    private fun setU16(dst: ByteArray, at: Int, num: Int) {
        require(num in 0..0xffff)
        for (i in 0..1) dst[at + i] = (num ushr (i * 8)).toByte()
    }
    private fun setU32(dst: ByteArray, at: Int, num: Long) {
        require(num in 0..0xffffffffL)
        for (i in 0..3) dst[at + i] = (num ushr (i * 8)).toByte()
    }
    private fun setU64(dst: ByteArray, at: Int, num: Long) {
        require(num >= 0)
        for (i in 0..7) dst[at + i] = (num ushr (i * 8)).toByte()
    }
}