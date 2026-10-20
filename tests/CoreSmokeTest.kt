package dev.nooir.bridge

import java.io.ByteArrayOutputStream
import java.io.File

private fun le16(b: ByteArray, i: Int): Int = (b[i].toInt() and 255) or ((b[i+1].toInt() and 255) shl 8)
private fun le64(b: ByteArray, i: Int): Long = (0..7).fold(0L) { a,j -> a or ((b[i+j].toLong() and 255) shl (8*j)) }

fun main() {
    val p = QuantizedPage(10, 2, byteArrayOf(1,0,1,0,1,0,1,0,1,0, 0,0,0,0,0,0,0,0,1,1), 1)
    val encoded = XtcCodec.encodePage(p)
    check(encoded.copyOfRange(0,4).contentEquals(byteArrayOf(88,84,71,0)))
    check(encoded.copyOfRange(22,26).contentEquals(byteArrayOf(0xaa.toByte(),0x80.toByte(),0,0xc0.toByte())))
    val gray = QuantizedPage(2, 8, ByteArray(16) { i -> if (i%2==0) 3 else (i/2 % 4).toByte() }, 2)
    val enc2 = XtcCodec.encodePage(gray)
    check(enc2.copyOfRange(22,26).contentEquals(byteArrayOf(0xaa.toByte(),0,0xcc.toByte(),0)))
    val work = File(System.getProperty("java.io.tmpdir"))
    for ((fmt,page) in listOf(XtcFormat.XTC to p,XtcFormat.XTCH to gray)) {
        val bytes = ByteArrayOutputStream()
        val result = XtcCodec.write(fmt, sequenceOf(page), bytes, work, BookMetadata("A 漫画", author="Test"), listOf(BookChapter("Ch 1",0,0)))
        val book = bytes.toByteArray()
        check(result.pageCount == 1)
        check(book.copyOfRange(0,4).contentEquals(if(fmt == XtcFormat.XTC) byteArrayOf(88,84,67,0) else byteArrayOf(88,84,67,72)))
        check(le16(book,6) == 1)
        check(le64(book,24) == 56L+256+96)
        check(le64(book,32) == 56L+256+96+16)
        check(book[result.fileBytes.toInt()-1] == XtcCodec.encodePage(page).last())
        check(String(book.copyOfRange(56,66)).startsWith("A 漫画"))
    }
    // Optional rich-metadata tail must not change legacy page/chapter offsets.
    for ((fmt, page) in listOf(XtcFormat.XTC to p, XtcFormat.XTCH to gray)) {
        val output = ByteArrayOutputStream()
        val synopsisJson = """{"title":"Test","description":"A manga synopsis and proper cover."}"""
        val result = XtcCodec.write(fmt, sequenceOf(page), output, work,
            BookMetadata("Test", coverPage = 0), richMetadataJson = synopsisJson)
        val data = output.toByteArray()
        val magic = "NOOIRMD1".toByteArray()
        check(data.takeLast(8).toByteArray().contentEquals(magic))
        val n = data.size - 12
        val payloadLength = (0..3).fold(0) { a, i -> a or ((data[n + i].toInt() and 255) shl (8*i)) }
        check(payloadLength == synopsisJson.toByteArray().size)
        check(String(data.copyOfRange(n - payloadLength, n)) == synopsisJson)
        check(le64(data, 32) == 56L + 256L + 16L)
        check(result.fileBytes == data.size.toLong())
        check(data.copyOfRange(le64(data, 32).toInt(), le64(data, 32).toInt() + XtcCodec.encodePage(page).size)
            .contentEquals(XtcCodec.encodePage(page)))
    }
    // Reader cover contract: Folio Nooir renders page zero for XTC/XTCH.
    // A dedicated cover must occupy page zero, while chapter navigation starts at one.
    for ((fmt, first) in listOf(XtcFormat.XTC to p, XtcFormat.XTCH to gray)) {
        val second = QuantizedPage(first.width, first.height,
            first.levels.map { ((if (fmt.depth == 1) 1 else 3) - (it.toInt() and 255)).toByte() }.toByteArray(),
            fmt.depth)
        val output = ByteArrayOutputStream()
        val result = XtcCodec.write(fmt, sequenceOf(first, second), output, work,
            BookMetadata("Cover QA", coverPage = 0),
            chapters = listOf(BookChapter("Chapter 1", 1, 1)))
        val bytes = output.toByteArray()
        val chapterOffset = 56 + 256
        val pageTableOffset = le64(bytes, 24).toInt()
        val firstOffset = le64(bytes, 32).toInt()
        check(result.pageCount == 2)
        check(le16(bytes, 56 + 244) == 0) // metadata coverPage is zero-based
        check(le16(bytes, chapterOffset + 80) == 2) // on-disk 1-based: chapter starts at zero-based page 1
        check(le16(bytes, chapterOffset + 82) == 2)
        check(le64(bytes, pageTableOffset) == firstOffset.toLong())
        check(bytes.copyOfRange(firstOffset, firstOffset + XtcCodec.encodePage(first).size)
            .contentEquals(XtcCodec.encodePage(first)))
        check(!XtcCodec.encodePage(first).contentEquals(XtcCodec.encodePage(second)))
    }
    val white = ByteArray(480*800) { 255.toByte() }
    val q = GrayscaleConverter.dither(480,800,white,1)
    check(q.levels.all { it == 1.toByte() })
    println("All XTC/XTCH codec and dithering smoke tests passed")
}