package dev.nooir.desktop.ui

import dev.nooir.bridge.*
import dev.nooir.mangabridge.MangaConversionOptions
import org.apache.pdfbox.Loader
import org.apache.pdfbox.io.IOUtils
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Windows PDF adapter. Original pages are rendered one at a time; no
 * arbitrary source-page cap or OCR. Uses the same output codecs as Android.
 * Text companion mode deliberately keeps each original page image AND its
 * extracted selectable text. It is not an exact layout-preserving reflow.
 */
internal object DesktopPdfImport {
    fun export(input: File, output: File, format: ExportFormat, device: String,
        options: MangaConversionOptions, textCompanion: Boolean = false,
        canceled: () -> Boolean = { false }, progress: (Int, Int) -> Unit = { _, _ -> }): Int {
        val source = input.canonicalFile
        val target = output.canonicalFile
        require(source.isFile && source.extension.equals("pdf", true))
        require(target != source && !target.exists() && target.parentFile.isDirectory)
        val actual = format.resolved(device)
        require(target.extension.equals(actual.extension, true))
        require(!textCompanion || actual in setOf(ExportFormat.EPUB, ExportFormat.EPUB2)) {
            "Selectable text companion requires EPUB output"
        }
        val profile = NooirDeviceProfiles.forId(device)
        val temp = File.createTempFile(".nooir-pdf-", ".tmp", target.parentFile)
        try {
            val written = Loader.loadPDF(source, IOUtils.createTempFileOnlyStreamCache()).use { doc ->
                require(!doc.isEncrypted) { "Encrypted/protected PDFs are not supported" }
                val total = doc.numberOfPages
                require(total > 0) { "Empty PDF" }
                require(actual !in setOf(ExportFormat.XTC, ExportFormat.XTCH) || total <= 65_535) {
                    "XTC/XTCH container permits at most 65,535 pages; choose EPUB or split the source"
                }
                val renderer = PDFRenderer(doc).apply { isSubsamplingAllowed = true }
                val title = source.nameWithoutExtension.take(120).ifBlank { "PDF" }
                val metadata = ExtendedMetadata(title = title,
                    direction = if (options.rightToLeft) ReadingDirection.RIGHT_TO_LEFT else ReadingDirection.LEFT_TO_RIGHT)
                fun checkCancel() { if (canceled()) throw java.util.concurrent.CancellationException("Conversion canceled") }
                if (textCompanion) {
                    val stripper = PDFTextStripper().apply { sortByPosition = true }
                    ReflowEpubWriter.write(temp, ReflowMetadata(title),
                        if (actual == ExportFormat.EPUB2) ReflowEpubFormat.EPUB2 else ReflowEpubFormat.EPUB3) { writer ->
                        for (i in 0 until total) {
                            checkCancel()
                            val image = frame(doc, renderer, i, profile.width, profile.height)
                            val png = try {
                                ByteArrayOutputStream().use { out ->
                                    check(ImageIO.write(image, "png", out))
                                    out.toByteArray()
                                }
                            } finally { image.flush() }
                            val href = writer.addImage(ReflowEpubWriter.ImageAsset(png,"png","image/png"))
                            stripper.startPage = i + 1
                            stripper.endPage = i + 1
                            val text = stripper.getText(doc).trim()
                            val paragraphs = text.lineSequence().filter { it.isNotBlank() }
                                .joinToString("") { "<p>" + ReflowEpubWriter.xml(it) + "</p>" }
                            writer.addSection("Page " + (i+1),
                                "<div><img src=\"$href\" alt=\"Original page ${i+1}\"/></div>" +
                                (if (paragraphs.isEmpty()) "" else "<h2>Extracted text</h2>$paragraphs"))
                            progress(i+1,total)
                        }
                    }
                    total
                } else {
                    val archive = actual in setOf(ExportFormat.EPUB, ExportFormat.EPUB2, ExportFormat.CBZ)
                    val depth = if (actual == ExportFormat.XTC) 1 else 2
                    val pages = sequence {
                        for (i in 0 until total) {
                            checkCancel()
                            val image = frame(doc, renderer, i, profile.width, profile.height)
                            val page = try { quantize(image, depth, options, archive) } finally { image.flush() }
                            yield(page)
                            progress(i+1,total)
                        }
                    }
                    temp.outputStream().buffered().use { out ->
                        if (archive) BookArchiveWriter.write(actual, pages.map { page ->
                            ArchivePage(title, page.colorPng ?: GrayscalePng.encode(page),page.width,page.height)
                        },out,metadata,options.epubPagesPerXhtml)
                        else XtcCodec.write(
                            if (actual == ExportFormat.XTC) XtcFormat.XTC else XtcFormat.XTCH,
                            pages,out,temp.parentFile,metadata = metadata.xtc().copy(coverPage = null),
                            direction = metadata.direction).pageCount
                    }
                }
            }
            require(written > 0)
            if (canceled()) throw java.util.concurrent.CancellationException("Conversion canceled")
            // No replace option; the target may have appeared during conversion.
            Files.move(temp.toPath(), target.toPath())
            return written
        } finally { temp.delete() }
    }

    private fun frame(doc: PDDocument, renderer: PDFRenderer, i: Int, w: Int, h: Int): BufferedImage {
        val page = doc.getPage(i)
        val crop = page.cropBox
        val rotated = page.rotation % 180 != 0
        val pw = if (rotated) crop.height else crop.width
        val ph = if (rotated) crop.width else crop.height
        require(pw.isFinite() && ph.isFinite() && pw > 0 && ph > 0) { "Invalid PDF page dimensions" }
        val scale = min(min(w / pw, h / ph), 2f)
        require(scale.isFinite() && scale > 0)
        val rendered = renderer.renderImage(i,scale,ImageType.RGB)
        try {
            require(rendered.width.toLong()*rendered.height <= 3_100_000L) { "PDF raster exceeded the safe output dimensions" }
            val canvas = BufferedImage(w,h,BufferedImage.TYPE_INT_RGB)
            val g = canvas.createGraphics()
            try {
                g.color = Color.WHITE; g.fillRect(0,0,w,h)
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                val ratio = min(w.toDouble()/rendered.width,h.toDouble()/rendered.height)
                val rw = (rendered.width*ratio).roundToInt().coerceIn(1,w)
                val rh = (rendered.height*ratio).roundToInt().coerceIn(1,h)
                g.drawImage(rendered,(w-rw)/2,(h-rh)/2,rw,rh,null)
            } finally { g.dispose() }
            return canvas
        } finally { rendered.flush() }
    }

    private fun quantize(image: BufferedImage, depth: Int, options: MangaConversionOptions,
        archive: Boolean): QuantizedPage {
        val w = image.width; val h = image.height
        val pixels = image.getRGB(0,0,w,h,null,0,w)
        val gain = options.contrast.coerceIn(0,100) / 50.0
        val luminance = ByteArray(pixels.size) { i ->
            val c = pixels[i]
            val y = (299*((c shr 16) and 255) + 587*((c shr 8) and 255) + 114*(c and 255))/1000
            ((y-128)*gain+128).roundToInt().coerceIn(0,255).toByte()
        }
        val gray = GrayscaleConverter.quantize(w,h,luminance,depth,options.renderAlgorithm,options.dither)
        return if (archive && options.epubColor) {
            val png = ByteArrayOutputStream().use { out ->
                check(ImageIO.write(image,"png",out)); out.toByteArray()
            }
            gray.copy(colorPng = png)
        } else gray
    }
}
