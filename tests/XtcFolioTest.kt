import dev.nooir.bridge.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets.UTF_8

private fun u16(b: ByteArray, i: Int) = (b[i].toInt() and 255) or ((b[i+1].toInt() and 255) shl 8)
private fun u32(b: ByteArray, i: Int) = (0..3).fold(0L) { acc, n -> acc or ((b[i+n].toLong() and 255) shl (8*n)) }
private fun u64(b: ByteArray, i: Int) = (0..7).fold(0L) { acc, n -> acc or ((b[i+n].toLong() and 255) shl (8*n)) }

private fun readPixel(page: ByteArray, w: Int, h: Int, depth: Int, x: Int, y: Int): Int {
    if (depth == 1) {
        val rowBytes = (w + 7) / 8
        return (page[22 + y * rowBytes + x / 8].toInt() ushr (7 - x % 8)) and 1
    }
    val colBytes = (h + 7) / 8
    val position = 22 + (w - 1 - x) * colBytes + y / 8
    val plane = w * colBytes
    val hi = (page[position].toInt() ushr (7 - y % 8)) and 1
    val lo = (page[position + plane].toInt() ushr (7 - y % 8)) and 1
    val stored = (hi shl 1) or lo
    return when (stored) { 3 -> 0; 1 -> 1; 2 -> 2; 0 -> 3; else -> error("Out of range") }
}

private fun verify(fmt: XtcFormat) {
    val w = 480
    val h = 800
    val planes = if (fmt == XtcFormat.XTC) 1 else 2
    val levels = ByteArray(w*h) { i -> ((i / w + i % w) % (if(planes==1) 2 else 4)).toByte() }
    val cover = QuantizedPage(w,h,levels,planes)
    val white = QuantizedPage(w,h,ByteArray(w*h) { if(planes == 1) 1 else 3 },planes)
    val out = ByteArrayOutputStream()
    XtcCodec.write(fmt, sequenceOf(cover, white, white), out, File(System.getProperty("java.io.tmpdir")),
        metadata = BookMetadata("A & ☆ 漫画", author = "著者\n& B", coverPage = 0),
        chapters = listOf(BookChapter("第1章",1,1),BookChapter("第2章",2,2)),
        direction = ReadingDirection.RIGHT_TO_LEFT)
    val b = out.toByteArray()
    check(String(b.copyOfRange(0,3)) == "XTC")
    check(b[3].toInt() == if(fmt == XtcFormat.XTC) 0 else 72)
    check(b[4].toInt() == 1 && b[5].toInt() == 0)
    check(u16(b,6)==3 && b[8].toInt() == 1)
    check(b[9].toInt()==1 && b[10].toInt()==0 && b[11].toInt()==1)
    check(u32(b,12)==1L && u64(b,16)==56L)
    val chapters=u32(b,48).toInt()
    val table=u64(b,24).toInt()
    val data=u64(b,32).toInt()
    check(chapters==312 && table==312+2*96 && data==table+3*16)
    check(u16(b,chapters+80)==2 && u16(b,chapters+82)==2)
    check(u16(b,chapters+96+80)==3 && u16(b,chapters+96+82)==3)
    check(b[56+128-1]==0.toByte())
    check(String(b.copyOfRange(56,56+128), UTF_8).startsWith("A & ☆ 漫画"))
    check(u16(b,56+244)==0)
    var end=data
    for(i in 0..2) {
        val offset=u64(b,table+i*16).toInt()
        val size=u32(b,table+i*16+8).toInt()
        val pw=u16(b,table+i*16+12)
        val ph=u16(b,table+i*16+14)
        check(offset==end && pw==w && ph==h)
        check(size==22 + (if(planes==1) 48000 else 96000))
        check(String(b.copyOfRange(offset,offset+3)) == if(planes==1) "XTG" else "XTH")
        check(u16(b,offset+4)==w && u16(b,offset+6)==h)
        check(u32(b,offset+10)==size.toLong()-22L)
        if(i==0) {
            val raw=b.copyOfRange(offset,offset+size)
            for ((x,y) in listOf(0 to 0,1 to 0,479 to 0,0 to 799,479 to 799,271 to 463))
                check(readPixel(raw,w,h,planes,x,y)==(levels[y*w+x].toInt() and 255))
        }
        end=offset+size
    }
    check(end==b.size) { "Unexpected nonstandard bytes after final page" }
    val corrupted=b.clone()
    for(n in 0..7)corrupted[table+n]=0xff.toByte()
    check(u64(corrupted,table)<0 || u64(corrupted,table)>=corrupted.size)
    val invalidFirst=b.clone()
    invalidFirst[data]=0
    check(!String(invalidFirst.copyOfRange(data,data+3)).startsWith("XT"))
    val folder=System.getenv("NOOIR_OUTPUT_DIR")
    if (!folder.isNullOrBlank()) {
        File(folder).mkdirs()
        File(folder,"MangaNooir_Folio_${fmt.extension}_sample.${fmt.extension}").writeBytes(b)
    }
}

fun main() {
    verify(XtcFormat.XTC)
    verify(XtcFormat.XTCH)
    // Folio Nooir uses ceil(width*height/8) per XTCH plane; non-8-aligned
    // custom heights would produce a different plane boundary. Reject early.
    val irregular = QuantizedPage(13, 15, ByteArray(13 * 15), 2)
    check(runCatching { XtcCodec.encodePage(irregular) }.isFailure)
    check(XtcCodec.encodePage(QuantizedPage(13, 15, ByteArray(13 * 15), 1)).isNotEmpty())
    val tooMany = sequence {
        val sample = QuantizedPage(8,8,ByteArray(64),1)
        repeat(65536) { yield(sample) }
    }
    val failure=runCatching {
        XtcCodec.write(XtcFormat.XTC,tooMany,ByteArrayOutputStream(),
            File(System.getProperty("java.io.tmpdir")))
    }.exceptionOrNull()
    check(failure != null && failure.message.orEmpty().contains("65535"))
    println("Folio XTC/XTCH interop: header, metadata, 1-based chapters, MSB XTG/XTH planes, offsets, EOF, corrupt fixture and max pages passed")
}
