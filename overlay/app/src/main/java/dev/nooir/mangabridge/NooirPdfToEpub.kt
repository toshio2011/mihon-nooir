package dev.nooir.mangabridge

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageTree
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import dev.nooir.bridge.ReflowEpubFormat
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
    private data class PlacedImage(
        val y: Float, val bottom: Float, val jpeg: ByteArray,
        val coversPage: Boolean, val spansTextColumn: Boolean,
    )
    private data class Content(val lines: List<PdfReflowLines.Line>, val pictures: List<PlacedImage>,
        val vectorMarks: Int, val hadImageErrors: Boolean)

    /** One PDF page's positioned text/image blocks, using the native PDF text layer. */
    private class PageCollector(
        private val pageWidth: Float, private val pageHeight: Float,
        private val selectedPage: PDPage,
    ) : PDFTextStripper() {
        val lines = mutableListOf<PdfReflowLines.Line>()
        val pictures = mutableListOf<PlacedImage>()
        var vectorMarks: Int = 0
        var hadImageErrors = false

        init {
            sortByPosition = true
            // PDFTextStripper resets its private currentPageNo to 0 for each
            // writeText() call. This override processes just selectedPage and
            // therefore intentionally uses zero-based page admission.
            startPage = 0
            endPage = 0
        }

        override fun processPages(pages: PDPageTree) {
            // Default PDFBox loops over the entire document page tree even
            // when startPage/endPage select one page. Calling that separately
            // N times was O(N^2). We already have the selected PDPage from a
            // single sequential page iterator, so extract directly here.
            // A blank page is preserved by the normal image fallback.
            if (selectedPage.hasContents()) processPage(selectedPage)
        }

        override fun writeString(text: String, positions: MutableList<TextPosition>) {
            val clean = text.trim()
            if (clean.isNotEmpty()) {
                val first = positions.firstOrNull()
                lines += PdfReflowLines.Line(clean, first?.xDirAdj ?: 0f,
                    first?.yDirAdj ?: 0f, first?.fontSizeInPt ?: 12f)
            }
        }

        override fun processOperator(operator: Operator, operands: MutableList<COSBase>) {
            if (operator.name in setOf("m", "l", "re", "c", "v", "y", "S", "f", "B")) vectorMarks++
            if (operator.name == "Do" && operands.firstOrNull() is COSName) {
                try {
                    val name = operands[0] as COSName
                    val image = resources?.getXObject(name) as? PDImageXObject
                    if (image != null && pictures.size >= 30) {
                        // Never silently omit image 31+: fall back to a full
                        // rendering of this original PDF page.
                        hadImageErrors = true
                    } else if (image != null &&
                        image.width.toLong() * image.height.toLong() > 25_000_000L) {
                        // Inspect PDF image headers before requesting a huge
                        // Bitmap from PDFBox; preserve the source page instead.
                        hadImageErrors = true
                    } else if (image != null) {
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
                                pictures += PlacedImage(
                                    top, (top + abs(ctm.scaleY)).coerceAtMost(pageHeight),
                                    jpeg, coversPage,
                                    spansTextColumn = abs(ctm.scaleX) >= pageWidth * 0.28f,
                                )
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
            require(page.width > 0 && page.height > 0) { "Invalid PDF page dimensions" }
            // Never force a minimum scale that can allocate a gigantic
            // bitmap for unusually large physical PDF page dimensions.
            val factor = min(2f, min(1600f / page.width, 2200f / page.height))
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
        titleOverride: String? = null,
        authorOverride: String? = null,
        epubFormat: ReflowEpubFormat = ReflowEpubFormat.EPUB3,
        onPage: (Int, Int, String) -> Unit = { _, _, _ -> },
    ) {
        require(pdf.isFile && pdf.length() > 0L) { "Missing or empty PDF input" }
        PDFBoxResourceLoader.init(context.applicationContext)
        // Keep the PDF scratch streams off the Java heap for long documents.
        // There is no arbitrary PDF file/page-count cutoff; physical storage
        // and metadata/reader limits are enforced separately.
        val scratch = MemoryUsageSetting.setupMixed(16L * 1024 * 1024)
            .setTempDir(context.cacheDir)
        PDDocument.load(pdf, scratch).use { book ->
            require(!book.isEncrypted) { "Password-protected PDFs aren't supported yet" }
            val total = book.numberOfPages
            require(total > 0) { "PDF has no pages" }
            val info = book.documentInformation
            val title = titleOverride?.trim()?.takeIf { it.isNotBlank() }
                ?: info.title?.takeIf { it.isNotBlank() }
                ?: titleHint?.substringBeforeLast('.', titleHint).orEmpty()
                    .ifBlank { pdf.nameWithoutExtension.ifBlank { "Imported PDF" } }
            val author = authorOverride?.trim()?.takeIf { it.isNotBlank() }
                ?: info.author.orEmpty()
            ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    ReflowEpubWriter.write(output, ReflowMetadata(title, author,
                        description = "Converted locally from PDF, without OCR"), epubFormat) { epub ->
                        // Reflowable novel paragraphs should span PDF source pages.
                        // Only split at visible drop-cap section openings or image-only
                        // front/back pages, rather than adding hundreds of PDF pages
                        // as separate EPUB TOC entries.
                        val pendingBody = StringBuilder()
                        var pendingStart = 1
                        var pendingLast = 1
                        fun finishSection() {
                            if (pendingBody.isNotEmpty()) {
                                val label = if (pendingStart == pendingLast) "PDF page $pendingStart"
                                    else "PDF pages $pendingStart–$pendingLast"
                                epub.addSection(label, pendingBody.toString())
                                pendingBody.setLength(0)
                            }
                        }
                        // Avoid slow indexed PDFBox page lookups on 1000+ page books.
                        // Original-page mode only uses PdfRenderer, not PDFBox page metadata.
                        // MIXED walks the page tree lazily; its per-page text scan remains to be profiled.
                        val pageIterator = if (mode == Mode.MIXED) book.pages.iterator() else null
                        for (index in 0 until total) {
                            onPage(index + 1, total, "Inspecting text and figures")
                            val page = pageIterator?.next()
                            val text = if (page != null) runCatching {
                                val collector = PageCollector(
                                    page.cropBox.width, page.cropBox.height, page)
                                collector.getText(book)
                                Content(collector.lines.toList(), collector.pictures.toList(),
                                    collector.vectorMarks, collector.hadImageErrors)
                            }.getOrNull() else null
                            val paragraphs = if (text != null && page != null) PdfReflowLines.paragraphs(
                                text.lines, page.cropBox.width, page.cropBox.height,
                                text.pictures.filter { it.spansTextColumn }.map {
                                    PdfReflowLines.FigureBand(it.y, it.bottom)
                                },
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
                                finishSection()
                                onPage(index + 1, total, "Preserving complex/scanned page as image")
                                val image = epub.addImage(ReflowEpubWriter.ImageAsset(
                                    renderPage(renderer, index), "jpg", "image/jpeg",
                                ), isCover = index == 0)
                                epub.addSection(sectionTitle,
                                    "<figure><img src=\"${ReflowEpubWriter.xml(image)}\" alt=\"PDF page ${index+1} (image content)\"/></figure>")
                            } else {
                                onPage(index + 1, total, "Converting real text and illustrations")
                                val blocks = mutableListOf<Pair<Float, String>>()
                                // PDF layout semantics are tested independently of Android.
                                // Preserve the section-sized EPUB3 grouping below; only
                                // replace the line/paragraph detection and drop-cap handling.
                                for (paragraph in paragraphs) {
                                    blocks += paragraph.y to "<p>" +
                                        ReflowEpubWriter.xml(paragraph.text) + "</p>"
                                }
                                val bodySizes = text.lines.filter { it.text.length > 3 }
                                    .map { it.fontSize }.sorted()
                                val typicalFont = bodySizes.getOrNull(bodySizes.size / 2) ?: 12f
                                text.pictures.forEach { picture ->
                                    val image = epub.addImage(ReflowEpubWriter.ImageAsset(
                                        picture.jpeg, "jpg", "image/jpeg"))
                                    blocks += picture.y to "<figure><img src=\"${ReflowEpubWriter.xml(image)}\" alt=\"Illustration on PDF page ${index+1}\"/></figure>"
                                }
                                val body = blocks.sortedBy { it.first }.joinToString("\n") { it.second }
                                val hasOpening = text.lines.any {
                                    it.fontSize > typicalFont * 2.3f &&
                                        it.text.trimStart('“', '‘', '"').length == 1 &&
                                        it.text.lastOrNull()?.isUpperCase() == true
                                }
                                if (hasOpening && pendingBody.isNotEmpty()) finishSection()
                                if (pendingBody.isEmpty()) pendingStart = index + 1
                                pendingLast = index + 1
                                pendingBody.append("<!-- source PDF page ").append(index + 1)
                                    .append(" -->\n").append(body).append('\n')
                                // Prevent pathological documents from growing an
                                // unbounded XHTML section on a small Android device.
                                if (pendingBody.length > 300_000) finishSection()
                            }
                            // The EPUB ZIP is streamed to disk. Keep a real free-space
                            // reserve rather than rejecting an otherwise valid
                            // 1000+ page book at a fixed output byte limit.
                            require(output.absoluteFile.parentFile.usableSpace >= 64L * 1024 * 1024) {
                                "Device storage is running low; clear space and retry the PDF export"
                            }
                            onPage(index + 1, total, "Added to EPUB")
                        }
                        finishSection()
                    }
                }
            }
        }
    }
}
