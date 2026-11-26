package dev.nooir.desktop.ui

import dev.nooir.bridge.*
import dev.nooir.desktop.DesktopImageDecoder
import dev.nooir.mangabridge.NooirEpubBook
import dev.nooir.mangabridge.MangaConversionOptions
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.font.LineBreakMeasurer
import java.awt.font.TextAttribute
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.text.AttributedString
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader

/** Native offline prose pagination. Reuses the Android package reader and XTC codec;
 * Java2D layout is deliberately not claimed to replicate the full EPUB CSS cascade. */
internal object DesktopTextEpubImport {
    data class Settings(
        val fontPx: Int = 24,
        val marginPx: Int = 26,
        val lineHeight: Int = 118,
        val family: String = "Serif",
        val direction: String = "Auto",
    ) {
        init {
            require(fontPx in 14..46 && marginPx in 8..80 && lineHeight in 100..200)
            require(family in setOf("Serif", "SansSerif", "Monospaced"))
            require(direction in setOf("Auto", "Left to right", "Right to left"))
        }
    }
    private sealed interface Block {
        data class Text(val value: String, val heading: Boolean) : Block
        data class Image(val src: String) : Block
    }
    private fun blocks(xhtml: String): List<Block> {
        // Never resolve external entities, DTDs, CSS or network resources.
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        val doc = factory.newDocumentBuilder().parse(InputSource(StringReader(xhtml)))
        val body = doc.getElementsByTagNameNS("*", "body").item(0) ?: error("EPUB section has no body")
        val out = mutableListOf<Block>()
        val text = StringBuilder()
        var heading = false
        val boundaries = setOf("p","div","section","article","li","ul","ol","blockquote","pre","figure","figcaption","h1","h2","h3","h4","h5","h6","tr","td","th")
        fun flush() {
            val value = text.toString().replace(Regex("\\s+"), " ").trim()
            if (value.isNotEmpty()) out.add(Block.Text(value, heading))
            text.setLength(0)
            heading = false
            require(out.size <= 50_000) { "EPUB section has too many blocks" }
        }
        fun visit(node: Node, head: Boolean, depth: Int) {
            require(depth <= 128) { "EPUB section nesting too deep" }
            if (node.nodeType == Node.TEXT_NODE || node.nodeType == Node.CDATA_SECTION_NODE) {
                if (text.isEmpty()) heading = head
                text.append(node.nodeValue)
                require(text.length <= 250_000) { "EPUB paragraph exceeds rendering limit" }
                return
            }
            val el = node as? Element ?: return
            val tag = (el.localName ?: el.tagName).lowercase()
            if (tag in setOf("script","style","nav","head","noscript","iframe","object","embed","form","audio","video") ||
                el.hasAttribute("hidden") || el.getAttribute("style").replace(Regex("\\s+"), "").lowercase().contains("display:none")) return
            if (tag in setOf("br","hr")) { flush(); return }
            if (tag == "img" || tag == "image") {
                flush()
                val ref = el.getAttribute("src").ifBlank { el.getAttribute("href") }.ifBlank {
                    el.getAttributeNS("http://www.w3.org/1999/xlink", "href")
                }
                require(ref.isNotBlank()) { "EPUB image has no supported local source" }
                out.add(Block.Image(ref))
                require(out.size <= 50_000)
                return
            }
            val boundary = tag in boundaries
            if (boundary) flush()
            val isHeading = head || tag in setOf("h1","h2","h3","h4","h5","h6")
            for (i in 0 until node.childNodes.length) visit(node.childNodes.item(i), isHeading, depth + 1)
            if (boundary) flush()
        }
        visit(body, false, 0)
        flush()
        return out
    }

    fun export(input: File, output: File, format: ExportFormat, device: String,
               settings: Settings, options: MangaConversionOptions,
               canceled: () -> Boolean = { false }): Int {
        require(format in setOf(ExportFormat.XTC, ExportFormat.XTCH))
        val source = input.canonicalFile
        val target = output.canonicalFile
        require(target != source && !target.exists() && target.parentFile.isDirectory &&
            target.extension.equals(format.extension, true))
        val profile = NooirDeviceProfiles.forId(device)
        require(profile.accepts(format))
        val w = profile.width
        val h = profile.height
        val margin = settings.marginPx
        require(w - 2*margin >= settings.fontPx*2 && h - 2*margin >= settings.fontPx*2) {
            "Reader dimensions too small for selected typography"
        }
        val depth = if (format == ExportFormat.XTC) 1 else 2
        val temp = File.createTempFile(".nooir-text-epub-", ".tmp", target.parentFile)
        fun checkCancel() {
            if (canceled()) throw java.util.concurrent.CancellationException("Conversion canceled")
        }
        try {
            val result = NooirEpubBook.open(source).use { book ->
                val rtl = when(settings.direction) {
                    "Right to left" -> true
                    "Left to right" -> false
                    else -> book.isRightToLeft
                }
                val chapters = mutableListOf<BookChapter>()
                var pageCount = 0
                val canvas = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
                val g = canvas.createGraphics()
                try {
                    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                    var y = margin.toFloat()
                    var dirty = false
                    fun clear() {
                        g.color = Color.WHITE
                        g.fillRect(0,0,w,h)
                        g.color = Color.BLACK
                        y = margin.toFloat()
                        dirty = false
                    }
                    clear()
                    fun snapshot(): QuantizedPage {
                        checkCancel()
                        require(pageCount < 6000) { "EPUB needs more than 6000 pages; split the source" }
                        require(target.parentFile.usableSpace >= 32L * 1024 * 1024) { "Insufficient storage" }
                        val rgb = canvas.getRGB(0,0,w,h,null,0,w)
                        val lum = ByteArray(rgb.size) { i ->
                            val p = rgb[i]
                            ((299*((p shr 16) and 255) + 587*((p shr 8) and 255) + 114*(p and 255))/1000).toByte()
                        }
                        pageCount++
                        return GrayscaleConverter.quantize(w,h,lum,depth,options.renderAlgorithm,options.dither)
                    }
                    val pages = sequence<QuantizedPage> {
                        for (chapter in book.chapters) {
                            checkCancel()
                            val first = pageCount
                            val content = blocks(book.readXhtml(chapter.path))
                            val title = content.filterIsInstance<Block.Text>().firstOrNull { it.heading }?.value?.take(80) ?: chapter.label
                            for (block in content) {
                                checkCancel()
                                when (block) {
                                    is Block.Text -> {
                                        val font = Font(settings.family, if (block.heading) Font.BOLD else Font.PLAIN,
                                            if (block.heading) (settings.fontPx*1.15).toInt() else settings.fontPx)
                                        val attributed = AttributedString(block.value).apply {
                                            addAttribute(TextAttribute.FONT, font)
                                            addAttribute(TextAttribute.RUN_DIRECTION, if (rtl) TextAttribute.RUN_DIRECTION_RTL else TextAttribute.RUN_DIRECTION_LTR)
                                        }
                                        val it = attributed.iterator
                                        val measurer = LineBreakMeasurer(it, g.fontRenderContext)
                                        while (measurer.position < it.endIndex) {
                                            checkCancel()
                                            val layout = measurer.nextLayout((w-2*margin).toFloat()) ?: error("Cannot wrap EPUB text")
                                            val line = (layout.ascent+layout.descent+layout.leading)*settings.lineHeight/100f
                                            require(line <= h-2*margin) { "Typography exceeds page size" }
                                            if (dirty && y+line > h-margin) { yield(snapshot()); clear() }
                                            val x = if (rtl) w-margin-layout.advance else margin.toFloat()
                                            layout.draw(g,x,y+layout.ascent)
                                            y += line
                                            dirty = true
                                        }
                                        y += settings.fontPx * 0.35f
                                    }
                                    is Block.Image -> {
                                        if (dirty) { yield(snapshot()); clear() }
                                        val art = DesktopImageDecoder.decode(book.imageBytes(chapter.path,block.src))
                                        try {
                                            val scale = minOf((w-2*margin).toDouble()/art.width, (h-2*margin).toDouble()/art.height)
                                            val iw = (art.width*scale).toInt().coerceAtLeast(1)
                                            val ih = (art.height*scale).toInt().coerceAtLeast(1)
                                            g.drawImage(art,(w-iw)/2,(h-ih)/2,iw,ih,null)
                                            yield(snapshot())
                                            clear()
                                        } finally { art.flush() }
                                    }
                                }
                            }
                            if (dirty) { yield(snapshot()); clear() }
                            if (pageCount > first) chapters += BookChapter(title,first,pageCount-1)
                        }
                        require(pageCount > 0) { "EPUB contains no renderable text or illustrations" }
                    }
                    temp.outputStream().buffered().use { out ->
                        XtcCodec.write(if (depth == 1) XtcFormat.XTC else XtcFormat.XTCH,
                            pages,out,target.parentFile,
                            metadata=BookMetadata(book.title,book.author),
                            chapterResolver={ chapters.toList() },
                            direction=if (rtl) ReadingDirection.RIGHT_TO_LEFT else ReadingDirection.LEFT_TO_RIGHT,
                            checkOutputCancelled=::checkCancel).pageCount
                    }
                } finally { g.dispose(); canvas.flush() }
            }
            checkCancel()
            Files.move(temp.toPath(),target.toPath())
            return result
        } finally { temp.delete() }
    }
}
