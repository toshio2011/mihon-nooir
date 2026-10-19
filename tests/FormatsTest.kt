import dev.nooir.bridge.*
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import java.io.ByteArrayInputStream

fun contents(bytes: ByteArray): Map<String,ByteArray> {
 val files=linkedMapOf<String,ByteArray>()
 ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
  while (true) {
   val e=z.nextEntry ?: break
   files[e.name]=z.readBytes()
  }
 }
 return files
}
fun main() {
 val m=ExtendedMetadata("A & B <Test>", "Writer", "Artist", "A description <&>", listOf("Action", "Fantasy"), "Publisher", "ja", "Manga Title", "id-123", "https://example.org")
 for (fmt in listOf(ExportFormat.CBZ, ExportFormat.EPUB)) {
  val out=ByteArrayOutputStream()
  val n=BookArchiveWriter.write(fmt, sequenceOf(
    ArchivePage("Chapter 1", byteArrayOf(1,2,3), 480,800),
    ArchivePage("Chapter 2", byteArrayOf(4,5,6), 480,800)
  ),out,m)
  check(n==2)
  val files=contents(out.toByteArray())
  if(fmt==ExportFormat.CBZ) {
   val c=String(files.getValue("ComicInfo.xml"))
   check(c.contains("A &amp; B &lt;Test&gt;")); check(c.contains("Bookmark=\"Chapter 2\"")); check(files.containsKey("nooir-metadata.json"))
  } else {
   check(files.keys.first()=="mimetype"); check(String(files.getValue("mimetype"))=="application/epub+zip")
   val opf=String(files.getValue("OEBPS/package.opf"))
   check(opf.contains("<dc:creator>Writer</dc:creator>")); check(opf.contains("<dc:description>A description &lt;&amp;&gt;</dc:description>")); check(opf.contains("page-progression-direction=\"rtl\""))
   check(String(files.getValue("OEBPS/nav.xhtml")).contains("Chapter 2"))
   check(files.containsKey("META-INF/nooir-metadata.json"))
  }
 }
 check(ExportFormat.AUTO.resolved("X4")==ExportFormat.XTCH)
 check(ExportFormat.AUTO.resolved("Kobo")==ExportFormat.EPUB)
 println("EPUB/CBZ metadata and TOC tests passed")
}