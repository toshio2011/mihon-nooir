import dev.nooir.bridge.ReflowEpubWriter
import dev.nooir.bridge.ReflowMetadata
import java.io.File
import java.util.zip.ZipFile

fun main() {
    val temp = File.createTempFile("nooir-reflow-", ".epub")
    try {
        ReflowEpubWriter.write(temp, ReflowMetadata(
            title = "Test & Diagrams",
            author = "Someone", description = "Mixed book <PDF>",
        )) { epub ->
            val asset = ReflowEpubWriter.ImageAsset(byteArrayOf(1,2,3,4), "png", "image/png")
            val name = epub.addImage(asset)
            check(name == "../images/img00001.png")
            epub.addSection("Chapter 1", "<h1>Chapter 1</h1><p>Select this text</p>" +
                "<figure><img src=\"$name\" alt=\"floor plan\"/></figure><p>More text after illustration</p>")
            epub.addSection("Scanned appendix", "<p>A later chapter</p>")
        }
        ZipFile(temp).use { zip ->
            val first = zip.entries().nextElement()
            check(first.name == "mimetype" && first.method == java.util.zip.ZipEntry.STORED)
            check(String(zip.getInputStream(first).readBytes()) == "application/epub+zip")
            val xml = String(zip.getInputStream(zip.getEntry("OEBPS/text/s00001.xhtml")).readBytes())
            check(xml.contains("Select this text") && xml.contains("floor plan"))
            check(xml.contains("../images/img00001.png"))
            val opf = String(zip.getInputStream(zip.getEntry("OEBPS/package.opf")).readBytes())
            check(opf.contains("text/s00001.xhtml") && opf.contains("text/s00002.xhtml"))
            check(opf.contains("images/img00001.png"))
            check(opf.contains("Test &amp; Diagrams"))
            check(zip.getEntry("OEBPS/nav.xhtml") != null)
            check(zip.getEntry("META-INF/container.xml") != null)
        }
        println("Reflow EPUB: container, metadata, text, images and navigation passed")
    } finally { temp.delete() }
}
