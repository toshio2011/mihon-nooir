package dev.nooir.bridge

import java.io.File
import java.io.FileOutputStream

fun main() {
    val dir = File(System.getProperty("java.io.tmpdir"), "nooir-real-xtc-preview-" + System.nanoTime())
    check(dir.mkdirs())
    try {
        for (format in XtcFormat.entries) {
            val w = 13
            val h = 16 // non-byte-aligned width; XTCH requires byte-aligned height
            val max = if (format == XtcFormat.XTC) 1 else 3
            val a = QuantizedPage(w, h, ByteArray(w * h) { (it % (max + 1)).toByte() }, format.depth)
            val b = QuantizedPage(w, h, ByteArray(w * h) { ((it * 3 + 1) % (max + 1)).toByte() }, format.depth)
            val out = File(dir, "test.${format.extension}")
            FileOutputStream(out).use { stream ->
                XtcCodec.write(format, sequenceOf(a, b), stream, dir,
                    BookMetadata("A test"), listOf(BookChapter("Chapter", 0, 1)))
            }
            val inspector = XtcBookPreview(out)
            check(inspector.format == format)
            check(inspector.pageCount == 2)
            for ((i, expected) in listOf(a, b).withIndex()) {
                val actual = inspector.page(i)
                check(actual.width == w && actual.height == h && actual.depth == format.depth)
                check(actual.levels.contentEquals(expected.levels)) {
                    "Actual binary ${format.name} page ${i + 1} differs from encoded source"
                }
            }
            check(runCatching { inspector.page(-1) }.isFailure)
            check(runCatching { inspector.page(2) }.isFailure)

            // Disturbed pages and misleading book headers must fail rather than
            // display corrupted or out-of-bounds pixels.
            val corrupt = File(dir, "truncated-${format.extension}")
            out.copyTo(corrupt)
            corrupt.setLengthByRandomAccess(56L)
            check(runCatching { XtcBookPreview(corrupt) }.isFailure)
            val brokenPage = File(dir, "bad-page-${format.extension}")
            out.copyTo(brokenPage)
            java.io.RandomAccessFile(brokenPage, "rw").use { file ->
                file.seek(56 + 256L + 96 + 8) // first page table entry's length
                file.write(byteArrayOf(1, 0, 0, 0))
            }
            check(runCatching { XtcBookPreview(brokenPage).page(0) }.isFailure)
        }
        println("PASS: finished XTC/XTCH binary page preview matches both encoded pages; invalid files rejected")
    } finally { dir.deleteRecursively() }
}

private fun File.setLengthByRandomAccess(size: Long) {
    java.io.RandomAccessFile(this, "rw").use { it.setLength(size) }
}
