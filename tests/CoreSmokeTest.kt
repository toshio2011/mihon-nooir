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
    val white = ByteArray(480*800) { 255.toByte() }
    val q = GrayscaleConverter.dither(480,800,white,1)
    check(q.levels.all { it == 1.toByte() })
    println("All XTC/XTCH codec and dithering smoke tests passed")
}