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
 // EPUB 3 must not output empty Dublin Core elements (EPUBCheck RSC-005).
 val sparse = ByteArrayOutputStream()
 val sparseMeta = m.copy(title = "", author = "", publisher = "", rights = "", description = "", language = "", genres = emptyList())
 check(BookArchiveWriter.write(ExportFormat.EPUB,
    sequenceOf(ArchivePage("Page", byteArrayOf(1,2,3), 480,800)), sparse, sparseMeta) == 1)
 val sparseOpf = String(contents(sparse.toByteArray()).getValue("OEBPS/package.opf"))
 check(sparseOpf.contains("<dc:title>Untitled</dc:title>"))
 check(sparseOpf.contains("<dc:language>und</dc:language>"))
 for (tag in listOf("creator", "publisher", "rights", "description", "subject")) {
   check(!sparseOpf.contains("<dc:" + tag + ">")) { "Unexpected empty optional EPUB 3 field: " + tag }
 }
 check(ExportFormat.AUTO.resolved("X4")==ExportFormat.XTCH)
 check(ExportFormat.AUTO.resolved("X3")==ExportFormat.XTCH)
 check(ExportFormat.AUTO.resolved("X4_PRO")==ExportFormat.XTCH)
 check(NooirDeviceProfiles.forId("X4_PRO").width==480)
 check(NooirDeviceProfiles.forId("X4_PRO").height==800)
 check(ExportFormat.AUTO.resolved("KINDLE_PW12")==ExportFormat.EPUB)
 check(ExportFormat.AUTO.resolved("KOBO_CLARA_BW")==ExportFormat.CBZ)
 check(ExportFormat.AUTO.resolved("BOOX_PALMA")==ExportFormat.CBZ)
 check(NooirDeviceProfiles.forId("CUSTOM:600x800").width==600)
 check(ExportFormat.AUTO.resolved("CUSTOM:600x800")==ExportFormat.EPUB)
 check(runCatching { ExportFormat.XTCH.resolved("KINDLE_PW12") }.isFailure)
 check(runCatching { ExportFormat.EPUB.resolved("MISSING") }.isFailure)
 check(runCatching { NooirDeviceProfiles.custom(4096,4096) }.isFailure)
 check(runCatching { ExportFormat.XTCH.resolved("CUSTOM:480x801") }.isFailure)
 check(ExportFormat.EPUB2.resolved("CUSTOM:480x801")==ExportFormat.EPUB2)
 check(!NooirDeviceProfiles.forId("KINDLE_PW12").xteinkWifi)
 check(NooirDeviceProfiles.forId("X4").xteinkWifi)
 println("EPUB/CBZ metadata and TOC tests passed")
}