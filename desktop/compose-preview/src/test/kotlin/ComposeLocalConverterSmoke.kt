package dev.nooir.desktop.compose

import dev.nooir.bridge.ExportFormat
import dev.nooir.bridge.GrayscalePng
import dev.nooir.bridge.XtcBookPreview
import dev.nooir.bridge.XtcFormat
import dev.nooir.desktop.ui.DesktopTextEpubImport
import dev.nooir.mangabridge.MangaConversionOptions
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import javax.imageio.ImageIO

fun main(args: Array<String>) {
    require(args.size == 1 && args[0].isNotBlank())
    val evidence = File(args[0]).canonicalFile
    require(evidence.isDirectory)
    val stage = Files.createTempDirectory("compose-local-converter").toFile()
    val settings = DesktopTextEpubImport.Settings()
    val opts = MangaConversionOptions().copy(panelMode = "OFF", orientation = "PORTRAIT",
        includeCover = false, renderAlgorithm = "THRESHOLD", rightToLeft = false)
    fun run(input: File, name: String, mode: LocalConversionMode, format: ExportFormat): File {
        val output = evidence.resolve(name + "." + format.extension)
        convertLocalFile(input, output, mode, format, "X4", opts, settings)
        return output
    }
    fun decoded(file: File, format: XtcFormat, name: String, minimum: Int) {
        val book = XtcBookPreview(file)
        check(book.format == format && book.pageCount >= minimum)
        var content = false
        for (i in 0 until book.pageCount) {
            val page = book.page(i)
            check(page.width == 480 && page.height == 800 && page.depth == format.depth)
            if (page.levels.toSet().size > 1) {
                if (!content) evidence.resolve(name + ".png").writeBytes(GrayscalePng.encode(page))
                content = true
            }
        }
        check(content) { "All converted frames are blank" }
        println(file.name + ": " + book.pageCount + " decoded frames")
    }
    fun epub(file: File) {
        ZipFile(file).use { archive ->
            check(archive.getInputStream(archive.getEntry("mimetype")).bufferedReader().use { it.readText() } == "application/epub+zip")
            check(archive.getEntry("META-INF/container.xml") != null)
        }
    }
    try {
        val images = stage.resolve("comic").apply { mkdir() }
        repeat(2) { i ->
            val image = BufferedImage(480,800,BufferedImage.TYPE_INT_RGB)
            val g = image.createGraphics()
            try {
                g.color = Color.WHITE; g.fillRect(0,0,480,800)
                g.color = Color.BLACK; g.fillRect(40 + 60*i,100,200,400)
                g.drawString("Original QA manga page " + (i+1),45,650)
            } finally { g.dispose() }
            check(ImageIO.write(image,"png",images.resolve("page" + i + ".png")))
            image.flush()
        }
        val manga = run(images,"local-comic",LocalConversionMode.MANGA,ExportFormat.XTC)
        decoded(manga,XtcFormat.XTC,"local-comic-preview",2)
        val comicEpub = run(images,"local-comic-archive",LocalConversionMode.MANGA,ExportFormat.EPUB2)
        epub(comicEpub)
        val comicXtch = run(comicEpub,"local-epub-comic",LocalConversionMode.EPUB_IMAGES,ExportFormat.XTCH)
        decoded(comicXtch,XtcFormat.XTCH,"local-epub-comic-preview",2)
        val pdf = stage.resolve("sample.pdf")
        PDDocument().use { doc ->
            repeat(2) { i ->
                val page = PDPage()
                doc.addPage(page)
                PDPageContentStream(doc,page).use { content ->
                    content.beginText()
                    content.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA),18f)
                    content.newLineAtOffset(72f,700f)
                    content.showText("Original QA document page " + (i+1))
                    content.endText()
                }
            }
            doc.save(pdf)
        }
        decoded(run(pdf,"local-pdf",LocalConversionMode.PDF,ExportFormat.XTC),XtcFormat.XTC,"local-pdf-preview",2)
        val companion = run(pdf,"local-pdf-companion",LocalConversionMode.PDF_COMPANION,ExportFormat.EPUB2)
        epub(companion)
        val reflow = run(pdf,"local-pdf-reflow",LocalConversionMode.PDF_REFLOW,ExportFormat.EPUB2)
        epub(reflow)
        decoded(run(reflow,"local-epub-text",LocalConversionMode.EPUB_TEXT,ExportFormat.XTCH),
            XtcFormat.XTCH,"local-epub-text-preview",1)
        check(runCatching { convertLocalFile(images,manga,LocalConversionMode.MANGA,ExportFormat.XTC,"X4",opts,settings) }.isFailure)
        check(!LocalConversionMode.EPUB_TEXT.accepts(ExportFormat.CBZ))
        check(!LocalConversionMode.PDF_REFLOW.accepts(ExportFormat.XTC))
        println("PASS: local manga, PDF, EPUB conversion and decoded output")
    } finally { stage.deleteRecursively() }
}
