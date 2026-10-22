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
 * grayscale PNG writer, presets and options model used by MangaNooir Android.
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
                        for (entry in sources) {
                            val img = decode(entry.open)
                            val coverOpts = options.copy(
                                layout = "PAGED", cropWhiteMargins = false,
                                splitSpreads = false, orientation = "PORTRAIT", overlapPercent = 0,
                            )
                            val opts = if (entry.cover) coverOpts else options
                            for (frame in render(img, w, h, depth, opts)) {
                                yield(Rendered(entry.chapter, entry.cover, frame))
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
            System.err.println("MangaNooir desktop conversion failed: ${error.message}")
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
                SourcePage(rel, if (cover) "Cover" else file.parentFile.takeIf { it != input }?.name ?: input.name,
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

    private fun render(src: BufferedImage, width: Int, height: Int, depth: Int,
        options: MangaConversionOptions): Sequence<QuantizedPage> = sequence {
        val rotate = options.orientation == "LANDSCAPE" && width < height
        val vw = if (rotate) height else width
        val vh = if (rotate) width else height
        val regions = PageSlicePlanner.plan(src.width, src.height, vw, vh,
            options.layout, options.splitSpreads, options.rightToLeft,
            options.overlapPercent, options.orientation)
        for (region in regions) {
            val r = if (options.cropWhiteMargins) trimWhite(src, region) else region
            val scale = minOf(vw.toDouble() / r.width, vh.toDouble() / r.height)
            val dw = (r.width * scale).toInt().coerceIn(1, vw)
            val dh = (r.height * scale).toInt().coerceIn(1, vh)
            val frame = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
            val g = frame.createGraphics()
            try {
                g.color = Color.WHITE
                g.fillRect(0, 0, width, height)
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                if (rotate) {
                    if (options.landscapeClockwise) {
                        g.translate(width, 0)
                        g.rotate(Math.PI / 2)
                    } else {
                        g.translate(0, height)
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
            val gray = ByteArray(width * height)
            val boost = (options.contrast.coerceIn(0, 100) / 50f).coerceIn(0.15f, 2f)
            for (y in 0 until height) for (x in 0 until width) {
                val rgb = frame.getRGB(x, y)
                val lum = (((rgb ushr 16) and 255) * 299 + ((rgb ushr 8) and 255) * 587 + (rgb and 255) * 114) / 1000
                gray[y * width + x] = (128f + (lum - 128f) * boost).toInt().coerceIn(0, 255).toByte()
            }
            val quantized = if (options.dither) GrayscaleConverter.dither(width, height, gray, depth)
            else {
                val max = if (depth == 1) 1 else 3
                QuantizedPage(width, height, ByteArray(gray.size) { i ->
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
