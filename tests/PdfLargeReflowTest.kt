import dev.nooir.bridge.ReflowEpubFormat
import dev.nooir.bridge.ReflowEpubWriter
import dev.nooir.bridge.ReflowMetadata
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipFile
import javax.imageio.ImageIO
import java.awt.image.BufferedImage

/** EPUB container stress using synthetic legal content. Not an Android PDF decoder test. */
fun main() {
    val home = Files.createTempDirectory("nooir-long-pdf-")
    try {
        val sourceImage = BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB)
        val png = ByteArrayOutputStream().also { check(ImageIO.write(sourceImage, "png", it)) }
            .toByteArray()
        for (format in listOf(ReflowEpubFormat.EPUB2, ReflowEpubFormat.EPUB3)) {
            val file = home.resolve(format.name + "-1201-pages.epub").toFile()
            ReflowEpubWriter.write(file,
                ReflowMetadata(title = "Original 1201-page fixture", author = "CI"), format) { book ->
                for (i in 1..1201) {
                    val picture = if (i % 100 == 0) {
                        book.addImage(ReflowEpubWriter.ImageAsset(png, "png", "image/png"))
                    } else null
                    val body = "<p>Original page " + i + " with selectable text.</p>" +
                        (picture?.let { "<div><img src=\"" + it + "\" alt=\"fixture\"/></div>" } ?: "")
                    book.addSection("Page " + i, body)
                }
            }
            ZipFile(file).use { zip ->
                val paths = zip.entries().asSequence().map { it.name }.toList()
                check(paths.count { it.startsWith("OEBPS/text/") && it.endsWith(".xhtml") } == 1201)
                check(paths.contains("OEBPS/text/s01201.xhtml"))
                check(paths.contains("OEBPS/package.opf"))
                check(paths.contains(if (format == ReflowEpubFormat.EPUB2)
                    "OEBPS/toc.ncx" else "OEBPS/nav.xhtml"))
                check(paths.count { it.startsWith("OEBPS/images/") } == 12)
            }
        }
        println("PASS: EPUB2 and EPUB3 stream 1201 original pages without truncation or 600-page cap")
    } finally {
        home.toFile().deleteRecursively()
    }
}
