import dev.nooir.bridge.*
import java.io.ByteArrayOutputStream
import java.io.File
fun main() {
  val sections = mutableListOf<BookChapter>()
  val pages = sequence {
    for (chapter in 1..3) {
      val start = (chapter-1)*2
      yield(QuantizedPage(8,8,ByteArray(64){1},1))
      yield(QuantizedPage(8,8,ByteArray(64){1},1))
      sections += BookChapter("Ch $chapter",start,start+1)
    }
  }
  val out=ByteArrayOutputStream()
  val result=XtcCodec.write(XtcFormat.XTC,pages,out,File(System.getProperty("java.io.tmpdir")),
    BookMetadata("Multi-chapter"),chapterResolver={ count ->
       check(count==6); sections.toList()
    })
  check(result.pageCount==6)
  val bytes=out.toByteArray()
  val at=((bytes[48].toInt() and 255) or ((bytes[49].toInt() and 255) shl 8))
  check(at==312)
  // Chapter records start at 56+256, and each occupies 96 bytes.
  for (i in 0..2) {
    val start=312 + i*96
    check(String(bytes.sliceArray(start until start+4)).startsWith("Ch"))
    check((bytes[start+80].toInt() and 255) == i*2)
    check((bytes[start+82].toInt() and 255) == i*2+1)
  }
  println("Dynamic multi-chapter export passed")
}