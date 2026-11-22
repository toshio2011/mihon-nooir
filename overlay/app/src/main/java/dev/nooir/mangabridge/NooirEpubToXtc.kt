package dev.nooir.mangabridge

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import dev.nooir.bridge.BookChapter
import dev.nooir.bridge.BookMetadata
import dev.nooir.bridge.GrayscaleConverter
import dev.nooir.bridge.NooirDeviceProfiles
import dev.nooir.bridge.QuantizedPage
import dev.nooir.bridge.ReadingDirection
import dev.nooir.bridge.XtcCodec
import dev.nooir.bridge.XtcFormat
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.io.File
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * First experimental native EPUB → XTC/XTCH renderer.
 *
 * Text is paginated with Android StaticLayout, pictures are scaled in document
 * order and both become actual framebuffer pages for the existing XTC codec.
 * This does NOT claim CSS/WebView-equivalent layout fidelity: custom fonts,
 * complex tables, math, SVG, scripts and advanced floats are not replicated.
 */
internal object NooirEpubToXtc {
    data class Settings(
        val fontPx: Int = 24,
        val marginPx: Int = 26,
        val lineSpacing: Float = 1.18f,
    ) {
        init {
            require(fontPx in 14..46 && marginPx in 8..80 &&
                lineSpacing in 1.0f..2.0f) { "Invalid EPUB rendering settings" }
        }
    }

    private sealed interface Block {
        data class Text(val text: String, val heading: Boolean = false) : Block
        data class Image(val src: String) : Block
    }

    private fun blocks(xhtml: String): Pair<String, List<Block>> {
        val doc = Jsoup.parse(xhtml)
        doc.select("script,style,iframe,object,embed,form,video,audio").remove()
        val body = doc.body()
        val out = mutableListOf<Block>()
        val ignored = setOf("nav", "script", "style", "head", "noscript", "footer")
        val textContainer = setOf("h1", "h2", "h3", "h4", "h5", "h6",
            "p", "li", "pre", "blockquote", "td", "th", "figcaption")
        fun visit(node: Node) {
            if (out.size >= 50000) error("EPUB section has too many text/image blocks")
            if (node is TextNode) {
                val content = node.text().trim()
                if (content.isNotEmpty()) out += Block.Text(content)
                return
            }
            val element = node as? Element ?: return
            val tag = element.normalName()
            if (tag in ignored) return
            if (tag == "img" || tag == "image") {
                val src = element.attr("src").ifBlank { element.attr("xlink:href") }
                if (src.isNotBlank()) out += Block.Image(src)
                return
            }
            if (tag in textContainer) {
                val text = element.text().replace(Regex("[ \t]+"), " ").trim()
                if (text.isNotBlank()) out += Block.Text(text, tag.startsWith("h"))
                // EPUBs may nest an actual illustration in a paragraph.
                element.select("img[src],image[xlink:href]").forEach { image ->
                    val src = image.attr("src").ifBlank { image.attr("xlink:href") }
                    if (src.isNotBlank()) out += Block.Image(src)
                }
                return
            }
            element.childNodes().forEach(::visit)
        }
        body.childNodes().forEach(::visit)
        val heading = doc.selectFirst("h1,h2,h3")?.text()?.trim()?.take(80)
            .orEmpty().ifBlank { doc.title().trim().take(80) }
        return heading to out
    }

    private class PageCanvas(
        val width: Int, val height: Int, val format: XtcFormat,
        private val rtl: Boolean, private val settings: Settings,
    ) {
        private var bitmap = blank()
        private var canvas = Canvas(bitmap)
        private var y = settings.marginPx
        private var written = false
        private val contentWidth = width - settings.marginPx * 2
        private val contentHeight = height - settings.marginPx * 2

        init { require(contentWidth > 40 && contentHeight > 50) }

        private fun blank(): Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            it.eraseColor(Color.WHITE)
        }

        private fun flush(): QuantizedPage {
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            val gray = ByteArray(pixels.size) { i ->
                val c = pixels[i]
                ((Color.red(c) * 299 + Color.green(c) * 587 +
                    Color.blue(c) * 114) / 1000).toByte()
            }
            val quantized = GrayscaleConverter.dither(width, height, gray, format.depth)
            bitmap.recycle()
            bitmap = blank()
            canvas = Canvas(bitmap)
            y = settings.marginPx
            written = false
            return quantized
        }

        fun text(content: String, heading: Boolean): List<QuantizedPage> {
            val result = mutableListOf<QuantizedPage>()
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = settings.fontPx * if (heading) 1.26f else 1f
                typeface = Typeface.create("sans-serif", if (heading) Typeface.BOLD else Typeface.NORMAL)
            }
            val text = content.replace(Regex("\\s+"), " ").trim()
            if (text.isEmpty()) return result
            require(text.length <= 200000) { "A single EPUB paragraph is too long to render" }
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, contentWidth)
                .setAlignment(if (rtl) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL)
                .setTextDirection(if (rtl) TextDirectionHeuristics.FIRSTSTRONG_RTL
                    else TextDirectionHeuristics.FIRSTSTRONG_LTR)
                .setIncludePad(false)
                .setLineSpacing(2f, settings.lineSpacing)
                .build()
            var start = 0
            while (start < layout.lineCount) {
                if (height - settings.marginPx - y < settings.fontPx * 1.6f) {
                    if (written) result += flush()
                }
                var end = start
                val startTop = layout.getLineTop(start)
                while (end < layout.lineCount &&
                    layout.getLineBottom(end) - startTop <= height - settings.marginPx - y) {
                    end++
                }
                if (end == start) {
                    if (written) { result += flush(); continue }
                    error("Text line exceeds selected e-reader viewport")
                }
                val segmentHeight = layout.getLineBottom(end - 1) - startTop
                canvas.save()
                canvas.clipRect(settings.marginPx, y,
                    width - settings.marginPx, y + segmentHeight)
                canvas.translate(settings.marginPx.toFloat(), y.toFloat() - startTop)
                layout.draw(canvas)
                canvas.restore()
                written = true
                y += segmentHeight
                start = end
            }
            y += if (heading) settings.fontPx else max(5, settings.fontPx / 2)
            return result
        }

        fun image(bytes: ByteArray): List<QuantizedPage> {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            require(bounds.outWidth in 1..16000 && bounds.outHeight in 1..16000 &&
                bounds.outWidth.toLong() * bounds.outHeight <= 70_000_000L) {
                "EPUB illustration dimensions exceed supported bounds"
            }
            var sample = 1
            while (bounds.outWidth / sample > width * 3 ||
                bounds.outHeight / sample > height * 3) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                ?: error("Cannot decode EPUB illustration")
            try {
                val scale = min(contentWidth.toFloat() / decoded.width,
                    contentHeight.toFloat() / decoded.height)
                val dw = max(1, (decoded.width * scale).toInt())
                val dh = max(1, (decoded.height * scale).toInt())
                val pages = mutableListOf<QuantizedPage>()
                if (written && y + dh > height - settings.marginPx) pages += flush()
                val x = settings.marginPx + (contentWidth - dw) / 2
                canvas.drawBitmap(decoded, null, Rect(x, y, x + dw, y + dh),
                    Paint(Paint.FILTER_BITMAP_FLAG))
                y += dh + 8
                written = true
                return pages
            } finally {
                decoded.recycle()
            }
        }

        fun finish(): QuantizedPage? =
            if (written) flush().also { bitmap.recycle() } else {
                bitmap.recycle()
                null
            }
    }

    fun convert(
        source: File, output: File, format: XtcFormat, device: String,
        settings: Settings = Settings(), rtl: Boolean = false,
        checkCancelled: () -> Unit = {},
        onPage: (Int, Int) -> Unit = { _, _ -> },
    ): Int {
        val profile = NooirDeviceProfiles.forId(device)
        require(profile.accepts(if (format == XtcFormat.XTC)
            dev.nooir.bridge.ExportFormat.XTC else dev.nooir.bridge.ExportFormat.XTCH)) {
            "Selected reader does not accept this format"
        }
        require(format != XtcFormat.XTCH || profile.height % 8 == 0) {
            "XTCH requires a framebuffer height divisible by 8"
        }
        NooirEpubBook.open(source).use { book ->
            var pageCount = 0
            val chapters = mutableListOf<BookChapter>()
            val pages = sequence {
                if (book.coverPath != null) {
                    checkCancelled()
                    val cover = PageCanvas(profile.width, profile.height, format, rtl, settings)
                    for (page in cover.image(book.imageBytesAt(book.coverPath))) {
                        pageCount++
                        yield(page)
                    }
                    cover.finish()?.let { pageCount++; yield(it) }
                }
                for ((index, chapter) in book.chapters.withIndex()) {
                    checkCancelled()
                    val (foundTitle, blocks) = blocks(book.readXhtml(chapter.path))
                    require(blocks.isNotEmpty()) {
                        "EPUB spine section " + (index + 1) + " has no readable text or illustrations"
                    }
                    val start = pageCount
                    val renderer = PageCanvas(profile.width, profile.height, format, rtl, settings)
                    for (block in blocks) {
                        checkCancelled()
                        val generated = when (block) {
                            is Block.Text -> renderer.text(block.text, block.heading)
                            is Block.Image -> renderer.image(book.imageBytes(chapter.path, block.src))
                        }
                        for (page in generated) {
                            require(pageCount < 6000) { "EPUB requires over 6000 XTC pages; split the book" }
                            pageCount++
                            yield(page)
                        }
                    }
                    renderer.finish()?.let {
                        require(pageCount < 6000) { "EPUB requires over 6000 XTC pages; split the book" }
                        pageCount++
                        yield(it)
                    }
                    chapters += BookChapter(foundTitle.ifBlank { chapter.label }, start, pageCount - 1)
                }
            }
            output.outputStream().use { out ->
                XtcCodec.write(format, pages, out, output.parentFile ?: source.parentFile,
                    metadata = BookMetadata(title = book.title, author = book.author,
                        coverPage = if (book.coverPath != null) 0 else null),
                    chapterResolver = { chapters.toList() },
                    direction = if (rtl) ReadingDirection.RIGHT_TO_LEFT else ReadingDirection.LEFT_TO_RIGHT,
                    checkOutputCancelled = checkCancelled,
                    onPageEncoded = {
                        checkCancelled()
                        require((output.parentFile ?: source.parentFile).usableSpace >= 32L * 1024 * 1024) {
                            "Insufficient storage to finish EPUB conversion"
                        }
                        onPage(it, book.chapters.size)
                    },
                )
            }
            checkCancelled()
            return pageCount
        }
    }
}
