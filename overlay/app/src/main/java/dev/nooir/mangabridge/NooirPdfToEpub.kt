package dev.nooir.mangabridge

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import dev.nooir.bridge.ReflowEpubWriter
import dev.nooir.bridge.ReflowMetadata
import dev.nooir.bridge.PdfReflowLines
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Offline PDF-to-EPUB conversion. Preserves extractable text as real XHTML and
 * uses image positions to interleave embedded pictures near text on each page.
 * Image-only/complex vector pages are rendered as pictures to avoid data loss.
 *
 * PDFs do not store a book's original semantic flow: this is best-effort,
 * not a guarantee of identical layout. No OCR and no paid AI dependency.
 */
object NooirPdfToEpub {
    enum class Mode { MIXED, ORIGINAL_PAGES }
    private data class PlacedImage(val y: Float, val jpeg: ByteArray, val coversPage: Boolean)
    private data class Content(val lines: List<PdfReflowLines.Line>, val pictures: List<PlacedImage>,
        val vectorMarks: Int, val hadImageErrors: Boolean)

    /** One PDF page's positioned text/image blocks, using the native PDF text layer. */
    private class PageCollector(private val pageWidth: Float, private val pageHeight: Float) : PDFTextStripper() {
        val lines = mutableListOf<PdfReflowLines.Line>()
        val pictures = mutableListOf<PlacedImage>()
        var vectorMarks: Int = 0
        var hadImageErrors = false

        init { sortByPosition = true }

        override fun writeString(text: String, positions: MutableList<TextPosition>) {
            val clean = text.trim()
            if (clean.isNotEmpty()) {
                val first = positions.firstOrNull()
                lines += PdfReflowLines.Line(
                    clean, first?.xDirAdj ?: 0f, first?.yDirAdj ?: 0f,
                    first?.fontSizeInPt ?: 12f,
                )
            }
        }

        override fun processOperator(operator: Operator, operands: MutableList<COSBase>) {
            if (operator.name in setOf("m", "l", "re", "c", "v", "y", "S", "f", "B")) vectorMarks++
            if (operator.name == "Do" && operands.firstOrNull() is COSName) {
                try {
                    val name = operands[0] as COSName
                    val image = resources?.getXObject(name) as? PDImageXObject
                    if (image != null && pictures.size < 30) {
                        val ctm = graphicsState.currentTransformationMatrix
                        val top = (pageHeight - ctm.translateY - abs(ctm.scaleY)).coerceAtLeast(0f)
                        val bitmap = image.image
                        if (bitmap != null && bitmap.width > 32 && bitmap.height > 32) {
                            // PDImageXObject may cache and reuse this Bitmap; do NOT
                            // recycle it. Store only a bounded JPEG rather than
                            // keeping dozens of native bitmaps alive per page.
                            val jpeg = NooirPdfToEpub.encoded(bitmap)
                            val coversPage = abs(ctm.scaleX) > pageWidth * 0.75f &&
                                abs(ctm.scaleY) > pageHeight * 0.75f
                            if (jpeg.size <= 6 * 1024 * 1024) {
                                pictures += PlacedImage(top, jpeg, coversPage)
                            } else hadImageErrors = true
                        }
                    }
                } catch (_: Exception) { hadImageErrors = true }
            }
            super.processOperator(operator, operands)
        }
    }

    private fun encoded(bitmap: Bitmap, maxWidth: Int = 1600, maxHeight: Int = 2200): ByteArray {
        val shrink = min(1.0, min(maxWidth.toDouble() / bitmap.width, maxHeight.toDouble() / bitmap.height))
        val scaled = if (shrink >= 1.0) bitmap else Bitmap.createScaledBitmap(
            bitmap, max(1, (bitmap.width * shrink).toInt()),
            max(1, (bitmap.height * shrink).toInt()), true,
        )
        try {
            val jpeg = ByteArrayOutputStream()
            check(scaled.compress(Bitmap.CompressFormat.JPEG, 88, jpeg)) { "Cannot encode PDF image" }
            return jpeg.toByteArray()
        } finally {
            if (scaled !== bitmap) scaled.recycle()
        }
    }

    private fun renderPage(renderer: PdfRenderer, index: Int): ByteArray {
        renderer.openPage(index).use { page ->
            val factor = min(2f, min(1600f / page.width, 2200f / page.height))
                .coerceAtLeast(0.3f)
            val width = max(1, (page.width * factor).toInt())
            val height = max(1, (page.height * factor).toInt())
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return encoded(bitmap)
            } finally { bitmap.recycle() }
        }
    }

    fun convert(
        context: Context, pdf: File, output: File,
        mode: Mode = Mode.MIXED,
        titleHint: String? = null,
        onPage: (Int, Int, String) -> Unit = { _, _, _ -> },
    ) {
        require(pdf.isFile && pdf.length() in 1..(100L * 1024 * 1024)) {
            "PDF must be between 1 byte and 100 MiB for experimental conversion"
        }
        PDFBoxResourceLoader.init(context.applicationContext)
        PDDocument.load(pdf).use { book ->
            require(!book.isEncrypted) { "Password-protected PDFs aren't supported yet" }
            val total = book.numberOfPages
            require(total in 1..600) { "PDF page count must be between 1 and 600" }
            val info = book.documentInformation
            val title = info.title?.takeIf { it.isNotBlank() }
                ?: titleHint?.substringBeforeLast('.', titleHint).orEmpty()
                    .ifBlank { pdf.nameWithoutExtension.ifBlank { "Imported PDF" } }
            val author = info.author.orEmpty()
            ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    ReflowEpubWriter.write(output, ReflowMetadata(title, author,
                        description = "Converted locally from PDF, without OCR")) { epub ->
                        for (index in 0 until total) {
                            onPage(index + 1, total, "Inspecting text and figures")
                            val page = book.getPage(index)
                            val text = if (mode == Mode.MIXED) runCatching {
                                val collector = PageCollector(page.cropBox.width, page.cropBox.height)
                                collector.startPage = index + 1
                                collector.endPage = index + 1
                                collector.getText(book)
                                Content(collector.lines.toList(), collector.pictures.toList(),
                                    collector.vectorMarks, collector.hadImageErrors)
                            }.getOrNull() else null
                            // Do not include printed folio numbers, lose decorative initials,
                            // or turn every visually justified line into a separate paragraph.
                            val paragraphs = if (text != null) PdfReflowLines.paragraphs(
                                text.lines, page.cropBox.width, page.cropBox.height,
                            ) else emptyList()
                            val enoughText = paragraphs.sumOf { it.text.length } >= 45
                            // For pages with extensive vector graphics or failed image
                            // decoding, preserve the whole page rather than dropping a
                            // floor plan or diagram. Other pages can still reflow.
                            val fallback = !enoughText || text!!.vectorMarks >= 12 ||
                                text.hadImageErrors ||
                                text.pictures.any { it.coversPage }
                            val sectionTitle = "Page " + (index + 1)
                            if (fallback) {
                                onPage(index + 1, total, "Preserving complex/scanned page as image")
                                val image = epub.addImage(ReflowEpubWriter.ImageAsset(
                                    renderPage(renderer, index), "jpg", "image/jpeg",
                                ))
                                epub.addSection(sectionTitle,
                                    "<figure><img src=\"${ReflowEpubWriter.xml(image)}\" alt=\"PDF page ${index+1} (image content)\"/></figure>")
                            } else {
                                onPage(index + 1, total, "Converting real text and illustrations")
                                val blocks = mutableListOf<Pair<Float, String>>()
                                for (paragraph in paragraphs) {
                                    blocks += paragraph.y to "<p>" +
                                        ReflowEpubWriter.xml(paragraph.text) + "</p>"
                                }
                                text.pictures.forEach { picture ->
                                    val image = epub.addImage(ReflowEpubWriter.ImageAsset(
                                        picture.jpeg, "jpg", "image/jpeg"))
                                    blocks += picture.y to "<figure><img src=\"${ReflowEpubWriter.xml(image)}\" alt=\"Illustration on PDF page ${index+1}\"/></figure>"
                                }
                                val body = blocks.sortedBy { it.first }.joinToString("\n") { it.second }
                                epub.addSection(sectionTitle, body)
                            }
                            require(output.length() < 300L * 1024 * 1024) {
                                "Generated EPUB is larger than 300 MiB; split the PDF into smaller sections"
                            }
                            onPage(index + 1, total, "Added to EPUB")
                        }
                    }
                }
            }
        }
    }
}
