import dev.nooir.bridge.*
import java.io.ByteArrayOutputStream
import java.io.File

private fun u32(b: ByteArray, i: Int): Long =
    (0..3).fold(0L) { v, n -> v or ((b[i + n].toLong() and 255) shl (n * 8)) }

private fun checkTrailer(format: XtcFormat) {
    val depth = format.depth
    val page = QuantizedPage(16, 16, ByteArray(256) { (it % (if (depth == 1) 2 else 4)).toByte() }, depth)
    val meta = BookMetadata("Synopsis sample", "MangaNooir")
    val json = """{"title":"Synopsis sample","description":"A story about friendship. 友達","genres":["Adventure"]}"""
    val folder = File(System.getProperty("java.io.tmpdir"))
    fun write(extra: String?): Pair<ByteArray, XtcWriteResult> {
        val output = ByteArrayOutputStream()
        val result = XtcCodec.write(format, sequenceOf(page, page), output, folder,
            metadata = meta, chapters = listOf(BookChapter("Chapter 1", 0, 1)),
            extendedMetadataJson = extra)
        return output.toByteArray() to result
    }
    val (plain, plainResult) = write(null)
    val (extended, extendedResult) = write(json)
    val payload = json.toByteArray(Charsets.UTF_8)
    check(extended.copyOfRange(0, plain.size).contentEquals(plain)) {
        "Enabling extended metadata changed legacy XTC/XTCH bytes"
    }
    check(plainResult.fileBytes == plain.size.toLong())
    check(extendedResult.fileBytes == extended.size.toLong())
    check(extended.size == plain.size + payload.size + 12)
    val footer = extended.size - 12
    check(String(extended.copyOfRange(footer, footer + 8), Charsets.US_ASCII) == "NOOIRM01")
    check(u32(extended, footer + 8) == payload.size.toLong())
    check(extended.copyOfRange(plain.size, footer).contentEquals(payload))
    check(extendedResult.pageCount == 2)
    check(runCatching { write("x".repeat(65537)) }.isFailure)
}

fun main() {
    checkTrailer(XtcFormat.XTC)
    checkTrailer(XtcFormat.XTCH)
    println("XTC/XTCH extended metadata: unchanged legacy bytes, trailer, bounds and sizes passed")
}
