import dev.nooir.bridge.*
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.util.zip.ZipFile
import javax.imageio.ImageIO
import javax.xml.parsers.DocumentBuilderFactory

private fun checkXml(bytes: ByteArray) {
    val f = DocumentBuilderFactory.newInstance()
    f.isNamespaceAware = true
    f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    f.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
}

private fun image(width: Int, height: Int, black: Boolean): ByteArray {
    val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    for (y in 0 until height) for (x in 0 until width) {
        val c = if (black) ((x + y) % 256) else 255
        img.setRGB(x, y, (c shl 16) or (c shl 8) or c)
    }
    return ByteArrayOutputStream().also { ImageIO.write(img, "png", it) }.toByteArray()
}

fun main() {
    val cover = image(480, 800, true)
    val page = image(480, 800, false)
    val meta = ExtendedMetadata(
        title = "星と & <Book> — 漫画", author = "A & B / 田中", description = "Summary <diagram> & detail 😀",
        language = "ja", identifier = "urn:uuid:12345678-1234-4123-8123-123456789abc", direction = ReadingDirection.RIGHT_TO_LEFT
    )
    val chapters = sequenceOf(
        ArchivePage("Cover", cover, 480, 800, isCover = true),
        ArchivePage("第一章", page, 480, 800),
        ArchivePage("第一章", page, 480, 800),
        ArchivePage("第二章", page, 480, 800),
    )
    val bytes = ByteArrayOutputStream()
    check(BookArchiveWriter.write(ExportFormat.EPUB2, chapters, bytes, meta) == 4)
    val sample = File(System.getProperty("java.io.tmpdir"), "manganooir-epub2-interop.epub")
    sample.writeBytes(bytes.toByteArray())
    try {
        ZipFile(sample).use { zip ->
            val entry = zip.entries().asSequence().toList()
            check(entry[0].name == "mimetype")
            check(entry[0].method == java.util.zip.ZipEntry.STORED)
            check((entry[0].extra?.size ?: 0) == 0)
            fun bytesAt(path: String) = zip.getInputStream(zip.getEntry(path) ?: error("Missing $path")).readBytes()
            check(String(bytesAt("mimetype"), UTF_8) == "application/epub+zip")
            val container = String(bytesAt("META-INF/container.xml"), UTF_8)
            checkXml(bytesAt("META-INF/container.xml"))
            check(container.contains("full-path=\"OEBPS/package.opf\""))
            check(container.contains("application/oebps-package+xml"))
            val opfBytes = bytesAt("OEBPS/package.opf")
            check(opfBytes.size < 512 * 1024)
            checkXml(opfBytes)
            val opf = String(opfBytes, UTF_8)
            check(opf.contains("version=\"2.0\""))
            check(!opf.contains("version=\"3.0\""))
            check(!opf.contains("properties=\"nav\""))
            check(opf.contains("<meta name=\"cover\" content=\"cover-image\"/>"))
            check(opf.contains("id=\"cover-image\" href=\"images/p00001.png\" media-type=\"image/png\""))
            check(opf.contains("<spine toc=\"ncx\">"))
            check(!opf.contains("page-progression-direction"))
            check(opf.contains("type=\"cover\" title=\"Cover\" href=\"text/p00001.xhtml\""))
            check(opf.contains("A &amp; B / 田中"))
            check(opf.contains("Summary &lt;diagram&gt; &amp; detail 😀"))
            check(opf.contains("星と &amp; &lt;Book&gt; — 漫画"))
            val ncx = String(bytesAt("OEBPS/toc.ncx"), UTF_8)
            checkXml(bytesAt("OEBPS/toc.ncx"))
            check(ncx.contains("dtb:uid"))
            check(ncx.contains("text/p00002.xhtml"))
            check(ncx.contains("text/p00004.xhtml"))
            check(ncx.indexOf("第一章") < ncx.indexOf("第二章"))
            for (i in 1..4) {
                val number = "%05d".format(i)
                val xhtml = bytesAt("OEBPS/text/p$number.xhtml")
                checkXml(xhtml)
                check(String(xhtml, UTF_8).contains("../images/p$number.png"))
                val imageData = bytesAt("OEBPS/images/p$number.png")
                val decoded = ImageIO.read(ByteArrayInputStream(imageData)) ?: error("Cannot decode image $i")
                check(decoded.width == 480 && decoded.height == 800)
            }
            check(entry.none { it.name.endsWith("nav.xhtml") })
        }
        // If the real series cover is not supplied, do not fake EPUB 2 cover metadata.
        val missing = ByteArrayOutputStream()
        BookArchiveWriter.write(ExportFormat.EPUB2,
            sequenceOf(ArchivePage("Chapter One", page, 480, 800)), missing,
            meta.copy(author = "", artist = "", description = ""))
        val missingZip = java.util.zip.ZipInputStream(ByteArrayInputStream(missing.toByteArray()))
        var opf = ""
        missingZip.use { zip ->
            while (true) {
                val next = zip.nextEntry ?: break
                if (next.name == "OEBPS/package.opf") opf = String(zip.readBytes(), UTF_8)
            }
        }
        check(!opf.contains("name=\"cover\""))
        check(!opf.contains("<dc:creator>"))
        check(!opf.contains("<dc:description>"))
        check(opf.contains("<dc:title>"))
        // A 6200-page image book is rejected rather than exceeding Folio Nooir's
        // 512 KiB OPF parser cap. Pages stream rather than accumulating in memory.
        val tooMany = sequence {
            for (i in 1..6200) yield(ArchivePage("Chapter", page, 480, 800))
        }
        check(runCatching {
            BookArchiveWriter.write(ExportFormat.EPUB2, tooMany, ByteArrayOutputStream(), meta)
        }.exceptionOrNull()?.message?.contains("512 KiB") == true)
        // Structural test only. A human-readable fixture is emitted
        // separately by ReadableFixturesTest.
    } finally {
        sample.delete()
    }
    println("EPUB 2 interop: ZIP mimetype, OPF 2, NCX, paths, real image decode, metadata, no-cover fallback, >512KiB rejection passed")
}
