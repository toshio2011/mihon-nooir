package dev.nooir.desktop

import dev.nooir.bridge.*
import dev.nooir.mangabridge.MangaConversionOptions
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.util.zip.ZipFile
import javax.imageio.ImageIO

/**
 * JVM/Windows feasibility CLI. Compiles against the EXACT Kotlin codec, page planner,
 * grayscale PNG writer, presets and options model used by MihonNooir Android.
 * Not yet a desktop GUI or a Suwayomi downloader.
 */
object NooirDesktopCli {
    private val imageExt = setOf("png", "jpg", "jpeg", "bmp", "gif", "webp")
    private const val MAX_IMAGE_BYTES = 25 * 1024 * 1024
    private const val MAX_IMAGE_PIXELS = 25_000_000L
    private const val MAX_PAGES = 5_000

    private data class SourcePage(
        val name: String, val chapter: String, val cover: Boolean,
        val open: () -> InputStream,
    )
    private data class Rendered(val chapter: String, val cover: Boolean, val page: QuantizedPage)

    @JvmStatic
    fun main(args: Array<String>) {
        try {
            require(args.size % 2 == 0) { "Use --input PATH --output PATH --format EPUB2 --device X4 [--options-token TOKEN] [--title TITLE]" }
            val flags = args.toList().chunked(2).associate { it[0] to it[1] }
            require(flags.keys.all { it in setOf("--input", "--output", "--format", "--device", "--options-token", "--title") }) { "Unknown option" }
            val input = File(flags["--input"] ?: error("Missing --input")).canonicalFile
            val output = File(flags["--output"] ?: error("Missing --output")).canonicalFile
            require(input.isDirectory || (input.isFile && input.extension.lowercase() in setOf("cbz", "zip"))) {
                "Input must be an image folder or CBZ/ZIP archive"
            }
            require(!output.exists()) { "Output already exists: $output" }
            require(output.parentFile.isDirectory) { "Output directory does not exist" }
            val device = flags["--device"] ?: "X4"
            val format = ExportFormat.valueOf((flags["--format"] ?: "AUTO").uppercase()).resolved(device)
            require(output.extension.lowercase() == format.extension) {
                "Output extension must be .${format.extension} for $format"
            }
            val options = MangaConversionOptions.fromToken(flags["--options-token"])
            val title = (flags["--title"] ?: input.nameWithoutExtension).take(120).ifBlank { "Manga" }
            val profile = NooirDeviceProfiles.forId(device)
            val meta = ExtendedMetadata(title = title,
                direction = if (options.rightToLeft) ReadingDirection.RIGHT_TO_LEFT else ReadingDirection.LEFT_TO_RIGHT)
            val tmp = File.createTempFile(".nooir-export-", ".tmp", output.parentFile)
            try {
                val pageCount = withInput(input) { source ->
                    val sources = source.filter { !it.cover || options.includeCover }
                    require(sources.isNotEmpty()) { "No readable image pages" }
                    require(sources.size <= MAX_PAGES) { "Too many input pages" }
                    val archive = format in setOf(ExportFormat.EPUB, ExportFormat.EPUB2, ExportFormat.CBZ)
                    val w = if (archive && options.orientation == "LANDSCAPE") profile.height else profile.width
                    val h = if (archive && options.orientation == "LANDSCAPE") profile.width else profile.height
                    val depth = if (format == ExportFormat.XTC) 1 else 2
                    val pages = sequence {
                        var previousChapter: String? = null
                        for (entry in sources) {
                            val img = decode(entry.open)
                            val firstInChapter = !entry.cover && entry.chapter != previousChapter
                            if (!entry.cover) previousChapter = entry.chapter
                            // Mirror Android: an optional intact first page keeps
                            // chapter title/cover art readable even in panel mode.
                            val preserveWhole = firstInChapter &&
                                options.keepChapterFirstPageWhole && options.panelMode != "OFF"
                            val wholeOptions = options.copy(
                                layout = "PAGED", cropWhiteMargins = false,
                                splitSpreads = false, orientation = "PORTRAIT", overlapPercent = 0,
                                panelMode = "OFF", showFullPageBeforeSplits = false,
                            )
                            val opts = if (entry.cover || preserveWhole) wholeOptions else options
                            val frames = render(img, w, h, depth, opts, fixedFramebuffer = !archive).iterator()
                            if (frames.hasNext()) {
                                val first = frames.next()
                                // The overview is deliberate: only pages with
                                // at least two real detailed screens get one.
                                if (!entry.cover && options.showFullPageBeforeSplits &&
                                    opts.panelMode != "OFF" && frames.hasNext()) {
                                    val overview = render(img, w, h, depth, wholeOptions,
                                        fixedFramebuffer = !archive).first()
                                    yield(Rendered(entry.chapter, false, overview))
                                }
                                yield(Rendered(entry.chapter, entry.cover, first))
                                while (frames.hasNext()) {
                                    yield(Rendered(entry.chapter, entry.cover, frames.next()))
                                }
                            }
                        }
                    }
                    tmp.outputStream().buffered().use { out ->
                        if (archive) {
                            BookArchiveWriter.write(format,
                                pages.map { item ->
                                    ArchivePage(item.chapter, item.page.colorPng ?: GrayscalePng.encode(item.page),
                                        item.page.width, item.page.height, isCover = item.cover)
                                }, out, meta, options.epubPagesPerXhtml)
                        } else {
                            val chapters = mutableListOf<BookChapter>()
                            var active: String? = null
                            var chapterStart = 0
                            var count = 0
                            var hasCover = false
                            val quantized = pages.map { item ->
                                if (count == 0 && item.cover) hasCover = true
                                if (!item.cover && item.chapter != active) {
                                    if (active != null) chapters += BookChapter(active!!, chapterStart, count - 1)
                                    active = item.chapter
                                    chapterStart = count
                                }
                                count++
                                item.page
                            }
                            XtcCodec.write(
                                if (format == ExportFormat.XTC) XtcFormat.XTC else XtcFormat.XTCH,
                                quantized, out, output.parentFile,
                                metadata = meta.xtc().copy(coverPage = if (sources.first().cover) 0 else null),
                                direction = meta.direction,
                                extendedMetadataJson = if (options.xtcExtendedMetadata) BookArchiveWriter.jsonMetadata(meta) else null,
                                chapterResolver = { written ->
                                    if (active != null) chapters += BookChapter(active!!, chapterStart, count - 1)
                                    check(written == count && (count == 0 || chapters.isNotEmpty() || hasCover))
                                    chapters
                                },
                            ).pageCount
                        }
                    }
                }
                require(pageCount > 0) { "Empty output" }
                Files.move(tmp.toPath(), output.toPath())
                println("Converted $pageCount pages to $output ($format, $device)")
            } finally {
                tmp.delete()
            }
        } catch (error: Exception) {
            System.err.println("MihonNooir desktop conversion failed: ${error.message}")
            throw error
        }
    }

    private fun <T> withInput(input: File, use: (List<SourcePage>) -> T): T {
        if (input.isDirectory) {
            val files = input.walkTopDown().filter { it.isFile && it.extension.lowercase() in imageExt }.toList()
            require(files.size <= MAX_PAGES) { "Too many source files" }
            val sorted = files.sortedWith { a, b ->
                val aRel = a.relativeTo(input).invariantSeparatorsPath
                val bRel = b.relativeTo(input).invariantSeparatorsPath
                NaturalPageOrder.compare(aRel, bRel)
            }
            val sources = sorted.map { file ->
                val rel = file.relativeTo(input).invariantSeparatorsPath
                val cover = !rel.contains('/') && file.nameWithoutExtension.equals("cover", ignoreCase = true)
                SourcePage(rel, if (cover) "Cover" else file.parentFile.takeIf { it != input }?.name?.replace(Regex("^[0-9]{4}__"), "") ?: input.name,
                    cover) { file.inputStream() }
            }.sortedWith(compareBy<SourcePage> { !it.cover })
            return use(sources)
        }
        ZipFile(input).use { zip ->
            val entries = zip.entries().asSequence()
                .filter { !it.isDirectory && it.name.substringAfterLast('.', "").lowercase() in imageExt }
                .sortedWith { a, b -> NaturalPageOrder.compare(a.name, b.name) }
                .take(MAX_PAGES + 1).toList()
            require(entries.size <= MAX_PAGES) { "Too many archive images" }
            val sources = entries.map { entry ->
                require(entry.size <= MAX_IMAGE_BYTES || entry.size == -1L) { "Image entry too large" }
                val normalized = entry.name.replace('\\', '/')
                val cover = !normalized.contains('/') && normalized.substringBeforeLast('.').equals("cover", true)
                SourcePage(normalized, if (cover) "Cover" else normalized.substringBeforeLast('/', input.nameWithoutExtension),
                    cover) { zip.getInputStream(entry) }
            }.sortedWith(compareBy<SourcePage> { !it.cover })
            return use(sources)
        }
    }

    private fun decode(open: () -> InputStream): BufferedImage {
        val bytes = open().use { input -> input.readNBytes(MAX_IMAGE_BYTES + 1) }
        require(bytes.isNotEmpty() && bytes.size <= MAX_IMAGE_BYTES) { "Invalid or oversized image" }
        val img = ImageIO.read(ByteArrayInputStream(bytes)) ?: error("Unsupported image codec")
        require(img.width > 0 && img.height > 0 && img.width.toLong() * img.height <= MAX_IMAGE_PIXELS) {
            "Image dimensions exceed safe desktop preview limit"
        }
        return img
    }

    private fun trimWhite(src: BufferedImage, region: PageRegion): PageRegion {
        val step = maxOf(2, minOf(region.width, region.height) / 300)
        var l = region.right; var t = region.bottom; var r = region.left; var b = region.top
        var samples = 0
        for (y in region.top until region.bottom step step) for (x in region.left until region.right step step) {
            val c = src.getRGB(x, y)
            val brightness = (((c ushr 16) and 255) * 299 + ((c ushr 8) and 255) * 587 + (c and 255) * 114) / 1000
            if (brightness < 240) {
                l = minOf(l, x); t = minOf(t, y); r = maxOf(r, x); b = maxOf(b, y); samples++
            }
        }
        if (samples < 12) return region
        val maxX = region.width * 30 / 100
        val maxY = region.height * 30 / 100
        return PageRegion(
            maxOf(region.left, minOf(l, region.left + maxX)),
            maxOf(region.top, minOf(t, region.top + maxY)),
            minOf(region.right, maxOf(r + step, region.right - maxX)),
            minOf(region.bottom, maxOf(b + step, region.bottom - maxY)),
        ).takeIf { it.width > 0 && it.height > 0 } ?: region
    }

    /** Same guarded trim and orientation logic as Android for detected panels. */
    private fun trimDetectedPanel(src: BufferedImage, region: PageRegion): PageRegion {
        val row = IntArray(region.width)
        var loadedY = -1
        return PanelWhitespaceCrop.trim(region) { x, y ->
            if (loadedY != y) {
                src.getRGB(region.left, y, region.width, 1, row, 0, region.width)
                loadedY = y
            }
            val pixel = row[x - region.left]
            val alpha = (pixel ushr 24) and 255
            val lum = (((pixel ushr 16) and 255) * 299 +
                ((pixel ushr 8) and 255) * 587 + (pixel and 255) * 114) / 1000
            (lum * alpha + 255 * (255 - alpha)) / 255 < 249
        }
    }

    /** Identical source-pixel seam policy to the Android bitmap adapter. */
    private fun safePanelSlices(
        src: BufferedImage, panel: PageRegion, viewWidth: Int, viewHeight: Int,
        rightToLeft: Boolean, overlapPercent: Int,
    ): List<PageRegion> = PanelSafeSlicePlanner.plan(
        panel, viewWidth, viewHeight, rightToLeft, overlapPercent,
    ) { x, y ->
        val pixel = src.getRGB(x, y)
        val alpha = (pixel ushr 24) and 255
        val lum = (((pixel ushr 16) and 255) * 299 +
            ((pixel ushr 8) and 255) * 587 + (pixel and 255) * 114) / 1000
        (lum * alpha + 255 * (255 - alpha)) / 255 < 247
    }

    /** Same bounded OCR-free panel detector as Android, with a Java2D thumbnail adapter. */
    private fun detectPanels(src: BufferedImage, options: MangaConversionOptions): List<PageRegion>? {
        if (options.panelMode != "PANEL_BY_PANEL" && options.panelMode != "ROW_BY_ROW") return null
        if (src.height.toLong() > src.width.toLong() * 3L) return null
        val scale = minOf(1.0, 600.0 / src.width, 900.0 / src.height)
        val w = (src.width * scale).toInt().coerceAtLeast(1)
        val h = (src.height * scale).toInt().coerceAtLeast(1)
        val thumbnail = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val graphics = thumbnail.createGraphics()
        try {
            graphics.color = Color.WHITE
            graphics.fillRect(0, 0, w, h)
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            graphics.drawImage(src, 0, 0, w, h, null)
        } finally {
            graphics.dispose()
        }
        val rgb = thumbnail.getRGB(0, 0, w, h, null, 0, w)
        val gray = ByteArray(rgb.size) { i ->
            val c = rgb[i]
            ((((c ushr 16) and 255) * 299 + ((c ushr 8) and 255) * 587 +
                (c and 255) * 114) / 1000).toByte()
        }
        val plan = if (options.panelMode == "ROW_BY_ROW")
            PanelDetectionCore.detectRows(w, h, gray)
        else PanelDetectionCore.detect(w, h, gray, options.rightToLeft)
        return if (plan.usedFullPageFallback) null
            else PanelDetectionCore.sourceRegions(plan, w, h, src.width, src.height, 1)
    }

    private fun render(src: BufferedImage, width: Int, height: Int, depth: Int,
        options: MangaConversionOptions, fixedFramebuffer: Boolean): Sequence<QuantizedPage> = sequence {
        val auto = options.panelMode != "OFF" && options.orientation == "AUTO_FIT"
        val manualRotate = options.orientation == "LANDSCAPE" && width < height
        val manualViewWidth = if (manualRotate) height else width
        val manualViewHeight = if (manualRotate) width else height
        val detectedPanels = detectPanels(src, options)
        val panels = if (detectedPanels != null && options.cropWhiteMargins)
            detectedPanels.map { trimDetectedPanel(src, it) }
        else detectedPanels
        val slices: List<PanelFitSlice> = when {
            // Row by Row means exactly one screen per detected horizontal tier.
            // Never split a row again on its internal vertical panel gutters.
            panels != null && options.panelMode == "ROW_BY_ROW" && auto ->
                PanelAutoFit.plan(
                    panels, minOf(width, height), maxOf(width, height),
                    options.rightToLeft, options.overlapPercent,
                    preserveWholePanels = true,
                )
            panels != null && options.panelMode == "ROW_BY_ROW" ->
                panels.map { PanelFitSlice(it, manualRotate) }
            panels != null && auto -> PanelAutoFit.plan(
                panels, minOf(width, height), maxOf(width, height),
                options.rightToLeft, options.overlapPercent,
                preserveWholePanels = true,
            ).flatMap { fit ->
                val vw = if (fit.landscape) maxOf(width, height) else minOf(width, height)
                val vh = if (fit.landscape) minOf(width, height) else maxOf(width, height)
                safePanelSlices(src, fit.region, vw, vh, options.rightToLeft,
                    options.overlapPercent).map { PanelFitSlice(it, fit.landscape) }
            }
            panels != null -> panels.flatMap { panel ->
                safePanelSlices(src, panel, manualViewWidth, manualViewHeight,
                    options.rightToLeft, options.overlapPercent)
            }.map { PanelFitSlice(it, manualRotate) }
            else -> PageSlicePlanner.plan(
                src.width, src.height, manualViewWidth, manualViewHeight,
                options.layout, options.splitSpreads, options.rightToLeft,
                options.overlapPercent, if (auto) "PORTRAIT" else options.orientation,
            ).map { PanelFitSlice(it, manualRotate) }
        }
        for (slice in slices) {
            val region = slice.region
            val landscape = if (auto) slice.landscape else manualRotate
            val frameWidth = if (auto && !fixedFramebuffer && landscape) maxOf(width, height) else width
            val frameHeight = if (auto && !fixedFramebuffer && landscape) minOf(width, height) else height
            val rotate = fixedFramebuffer && landscape && frameWidth < frameHeight
            val vw = if (rotate) frameHeight else frameWidth
            val vh = if (rotate) frameWidth else frameHeight
            // Already trimmed detected panels retain their safety padding.
            val r = if (options.cropWhiteMargins && panels == null) trimWhite(src, region) else region
            val scale = minOf(vw.toDouble() / r.width, vh.toDouble() / r.height)
            val dw = (r.width * scale).toInt().coerceIn(1, vw)
            val dh = (r.height * scale).toInt().coerceIn(1, vh)
            val frame = BufferedImage(frameWidth, frameHeight, BufferedImage.TYPE_INT_RGB)
            val g = frame.createGraphics()
            try {
                g.color = Color.WHITE
                g.fillRect(0, 0, frameWidth, frameHeight)
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                if (rotate) {
                    if (options.landscapeClockwise) {
                        g.translate(frameWidth, 0)
                        g.rotate(Math.PI / 2)
                    } else {
                        g.translate(0, frameHeight)
                        g.rotate(-Math.PI / 2)
                    }
                }
                val left = (vw - dw) / 2
                val top = (vh - dh) / 2
                g.drawImage(src, left, top, left + dw, top + dh,
                    r.left, r.top, r.right, r.bottom, null)
            } finally {
                g.dispose()
            }
            val gray = ByteArray(frameWidth * frameHeight)
            val boost = (options.contrast.coerceIn(0, 100) / 50f).coerceIn(0.15f, 2f)
            for (y in 0 until frameHeight) for (x in 0 until frameWidth) {
                val rgb = frame.getRGB(x, y)
                val lum = (((rgb ushr 16) and 255) * 299 + ((rgb ushr 8) and 255) * 587 + (rgb and 255) * 114) / 1000
                gray[y * frameWidth + x] = (128f + (lum - 128f) * boost).toInt().coerceIn(0, 255).toByte()
            }
            val quantized = if (options.dither) GrayscaleConverter.dither(frameWidth, frameHeight, gray, depth)
            else {
                val max = if (depth == 1) 1 else 3
                QuantizedPage(frameWidth, frameHeight, ByteArray(gray.size) { i ->
                    (((gray[i].toInt() and 255) * max + 127) / 255).toByte()
                }, depth)
            }
            val color = if (options.epubColor) ByteArrayOutputStream().use {
                check(ImageIO.write(frame, "png", it))
                it.toByteArray()
            } else null
            yield(if (color == null) quantized else quantized.copy(colorPng = color))
        }
    }
}
