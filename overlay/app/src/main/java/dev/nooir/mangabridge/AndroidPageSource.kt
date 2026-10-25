package dev.nooir.mangabridge

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.provider.OpenableColumns
import dev.nooir.bridge.GrayscaleConverter
import dev.nooir.bridge.QuantizedPage
import dev.nooir.bridge.PageSlicePlanner
import dev.nooir.bridge.NaturalPageOrder
import dev.nooir.bridge.PanelDetectionCore
import dev.nooir.bridge.PanelViewportPlanner
import dev.nooir.bridge.PanelAutoFit
import dev.nooir.bridge.PanelFitSlice
import dev.nooir.bridge.PageRegion
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

/** Android SAF image/CBZ import adapter. For direct Mihon integration, replace [inputUris]
 * with Mihon's downloaded chapter page streams; no change to the XTC writer is needed.
 */
class AndroidPageSource(private val context: Context) {
    private val resolver: ContentResolver = context.contentResolver
    private fun name(uri: Uri): String {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0) ?: "manga"
        }
        return uri.lastPathSegment ?: "manga"
    }

    fun pages(inputUris: List<Uri>, width: Int, height: Int, depth: Int,
        options: MangaConversionOptions = MangaConversionOptions(),
        fixedFramebuffer: Boolean = true,
    ): Sequence<QuantizedPage> = sequence {
        for (uri in inputUris.sortedWith { a, b -> NaturalPageOrder.compare(name(a), name(b)) }) {
            if (name(uri).endsWith(".cbz", true) || name(uri).endsWith(".zip", true)) {
                val temp = File.createTempFile("nooir-cbz-", ".zip", context.cacheDir)
                try {
                    resolver.openInputStream(uri)?.use { input ->
                        temp.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
                    } ?: error("Could not open $uri")
                    ZipFile(temp).use { zip ->
                        val entries = zip.entries().asSequence()
                            .filter { !it.isDirectory && imageExtension(it.name) }
                            .sortedWith { a, b -> NaturalPageOrder.compare(a.name, b.name) }
                            .toList()
                        for (entry in entries) {
                            // Two independent streams: first checks dimensions, second decodes.
                            val image = decode({ zip.getInputStream(entry) })
                            try { for (page in render(image, width, height, depth, optionsForFile(entry.name, options), fixedFramebuffer)) yield(page) }
                            finally { image.recycle() }
                        }
                    }
                } finally { temp.delete() }
            } else {
                val image = decode({ resolver.openInputStream(uri) ?: error("Cannot open $uri") })
                try { for (page in render(image, width, height, depth, optionsForFile(name(uri), options), fixedFramebuffer)) yield(page) }
                finally { image.recycle() }
            }
        }
    }

    /**
     * Preview the first source image using exactly the export rendering pipeline.
     * Materialize only the requested frames, never the whole chapter.
     * Call from a background dispatcher; decoding and quantization are blocking.
     *
     * The caller owns source selection (downloaded chapter or online cache).
     * A CBZ source uses its first archive image in natural reading order.
     */
    fun previewFirstPage(
        sourceUri: Uri, width: Int, height: Int, depth: Int,
        options: MangaConversionOptions = MangaConversionOptions(),
        fixedFramebuffer: Boolean = true,
        maxFrames: Int = 24,
    ): List<QuantizedPage> {
        require(maxFrames in 1..64) { "Preview frame limit must be 1..64" }
        return pages(listOf(sourceUri), width, height, depth, options, fixedFramebuffer)
            .take(maxFrames).toList()
    }

    private fun optionsForFile(filename: String, options: MangaConversionOptions): MangaConversionOptions {
        // A separate named series cover must never be mistaken for a panel grid.
        val basename = filename.substringAfterLast('/').substringBeforeLast('.').lowercase()
        return if (basename in setOf("cover", "frontcover", "folder")) {
            options.copy(panelMode = "OFF", layout = "PAGED", splitSpreads = false,
                cropWhiteMargins = false, orientation = "PORTRAIT", overlapPercent = 0)
        } else options
    }

    /**
     * OCR-free and phone-only: analyze at most 600x900 pixels per page. The
     * original decoded Bitmap is retained for rendering, never sent anywhere.
     * An uncertain page uses exactly the previous page slicing behavior.
     */
    private fun detectPanels(src: Bitmap, options: MangaConversionOptions, viewportWidth: Int, viewportHeight: Int): List<PageRegion>? {
        if (options.panelMode != "PANEL_BY_PANEL") return null
        if (src.height.toLong() > src.width.toLong() * 3L) return null
        val scale = minOf(1.0, 600.0 / src.width, 900.0 / src.height)
        val w = (src.width * scale).toInt().coerceAtLeast(1)
        val h = (src.height * scale).toInt().coerceAtLeast(1)
        val thumb = if (w == src.width && h == src.height) src
            else Bitmap.createScaledBitmap(src, w, h, true)
        try {
            val argb = IntArray(w * h)
            thumb.getPixels(argb, 0, w, 0, 0, w, h)
            val gray = ByteArray(argb.size) { i ->
                val color = argb[i]
                val alpha = Color.alpha(color)
                val lum = (Color.red(color) * 299 + Color.green(color) * 587 +
                    Color.blue(color) * 114) / 1000
                // Transparent gutters on PNGs are white, not black.
                ((lum * alpha + 255 * (255 - alpha)) / 255).toByte()
            }
            val plan = PanelDetectionCore.detect(w, h, gray, options.rightToLeft)
            if (plan.usedFullPageFallback) return null
            val regions = PanelDetectionCore.sourceRegions(plan, w, h, src.width, src.height, 1)
            // Reject suspiciously thin detected strips. Such rectangles are often
            // fragments of real panels and make dialogue unreadable when fitted.
            // Preserve the complete source page rather than exporting fake panels.
            val portraitWidth = minOf(viewportWidth, viewportHeight)
            val portraitHeight = maxOf(viewportWidth, viewportHeight)
            val suspicious = regions.any { region ->
                val portrait = PanelAutoFit.occupiedFraction(region, portraitWidth, portraitHeight)
                val landscape = PanelAutoFit.occupiedFraction(region, portraitHeight, portraitWidth)
                maxOf(portrait, landscape) < 0.30
            }
            return if (suspicious) listOf(PageRegion(0, 0, src.width, src.height)) else regions
        } finally {
            if (thumb !== src) thumb.recycle()
        }
    }

    private fun imageExtension(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in setOf("png", "jpg", "jpeg", "webp", "bmp", "gif")
    }

    private fun decode(stream: () -> InputStream): Bitmap {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        stream().use { BitmapFactory.decodeStream(it, null, opts) }
        require(opts.outWidth > 0 && opts.outHeight > 0) { "Unsupported manga image" }
        var sample = 1
        while (maxOf(opts.outWidth / sample, opts.outHeight / sample) > 4500) sample *= 2
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        return stream().use { BitmapFactory.decodeStream(it, null, decodeOpts) }
            ?: error("Image decoding failed")
    }

    private fun trimWhite(src: Bitmap, region: Rect): Rect {
        // Conservative trim, never removing more than 30% from a side.
        val step = maxOf(2, minOf(region.width(), region.height()) / 300)
        var l = region.right; var t = region.bottom; var r = region.left; var b = region.top
        var samples = 0
        for (y in region.top until region.bottom step step) {
            for (x in region.left until region.right step step) {
                val c = src.getPixel(x, y)
                val brightness = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
                if (brightness < 240) {
                    l = minOf(l, x); t = minOf(t, y)
                    r = maxOf(r, x); b = maxOf(b, y)
                    samples++
                }
            }
        }
        if (samples < 12) return region
        val maxX = region.width() * 30 / 100
        val maxY = region.height() * 30 / 100
        return Rect(
            maxOf(region.left, minOf(l, region.left + maxX)),
            maxOf(region.top, minOf(t, region.top + maxY)),
            minOf(region.right, maxOf(r + step, region.right - maxX)),
            minOf(region.bottom, maxOf(b + step, region.bottom - maxY)),
        ).takeIf { it.width() > 0 && it.height() > 0 } ?: region
    }

    private fun render(
        src: Bitmap, width: Int, height: Int, depth: Int,
        options: MangaConversionOptions, fixedFramebuffer: Boolean,
    ): Sequence<QuantizedPage> = sequence {
        val auto = options.panelMode == "PANEL_BY_PANEL" && options.orientation == "AUTO_FIT"
        val manualRotate = options.orientation == "LANDSCAPE" && width < height
        val manualViewWidth = if (manualRotate) height else width
        val manualViewHeight = if (manualRotate) width else height
        val panels = detectPanels(src, options, width, height)
        val slices: List<PanelFitSlice> = when {
            panels != null && auto -> PanelAutoFit.plan(
                panels, minOf(width, height), maxOf(width, height),
                options.rightToLeft, options.overlapPercent,
                preserveWholePanels = true,
            )
            panels != null -> panels.flatMap { panel ->
                PanelViewportPlanner.plan(panel, manualViewWidth, manualViewHeight,
                    options.rightToLeft, options.overlapPercent, preserveWholePanel = true)
            }.map { PanelFitSlice(it, manualRotate) }
            else -> PageSlicePlanner.plan(
                src.width, src.height, manualViewWidth, manualViewHeight,
                options.layout, options.splitSpreads, options.rightToLeft,
                options.overlapPercent, if (auto) "PORTRAIT" else options.orientation,
            ).map { PanelFitSlice(it, manualRotate) }
        }
        // A source-page overview precedes any multi-frame result, including panels,
        // landscape overlap, spreads and webtoon slices. Never duplicate single frames.
        // Render the overview in portrait to preserve the full composition.
        val outputSlices = if (options.showFullPageBeforeSplits && slices.size > 1) {
            listOf(PanelFitSlice(PageRegion(0, 0, src.width, src.height), false)) + slices
        } else slices
        // Render one frame at a time, with per-panel orientation only in Auto Fit.
        for ((sliceIndex, slice) in outputSlices.withIndex()) {
            val isOverview = options.showFullPageBeforeSplits && slices.size > 1 && sliceIndex == 0
            val initial = slice.region
            val landscape = if (isOverview) false else if (auto) slice.landscape else manualRotate
            val frameWidth = if (auto && !fixedFramebuffer && landscape) maxOf(width, height) else width
            val frameHeight = if (auto && !fixedFramebuffer && landscape) minOf(width, height) else height
            val needsRotation = fixedFramebuffer && landscape && frameWidth < frameHeight
            val viewWidth = if (needsRotation) frameHeight else frameWidth
            val viewHeight = if (needsRotation) frameWidth else frameHeight
            val sourceRegion = Rect(initial.left, initial.top, initial.right, initial.bottom)
            val r = if (!isOverview && options.cropWhiteMargins && panels == null) trimWhite(src, sourceRegion) else sourceRegion
            val scale = minOf(viewWidth.toFloat() / r.width(), viewHeight.toFloat() / r.height())
            val dw = (r.width() * scale).toInt().coerceIn(1, viewWidth)
            val dh = (r.height() * scale).toInt().coerceIn(1, viewHeight)
            val rendered = Bitmap.createBitmap(frameWidth, frameHeight, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(rendered)
                canvas.drawColor(Color.WHITE)
                if (needsRotation) {
                    // Both rotations map the virtual WxH landscape page exactly
                    // into the native HxW portrait XTC/XTCH framebuffer.
                    canvas.save()
                    if (options.landscapeClockwise) {
                        canvas.translate(frameWidth.toFloat(), 0f)
                        canvas.rotate(90f)
                    } else {
                        canvas.translate(0f, frameHeight.toFloat())
                        canvas.rotate(-90f)
                    }
                }
                val l = (viewWidth - dw) / 2
                val t = (viewHeight - dh) / 2
                canvas.drawBitmap(src, r, Rect(l, t, l + dw, t + dh), Paint(Paint.FILTER_BITMAP_FLAG))
                if (needsRotation) canvas.restore()
                val rgb = IntArray(frameWidth * frameHeight)
                rendered.getPixels(rgb, 0, frameWidth, 0, 0, frameWidth, frameHeight)
                val gray = ByteArray(rgb.size)
                val boost = (options.contrast.coerceIn(0, 100) / 50f).coerceIn(0.15f, 2f)
                for (i in rgb.indices) {
                    val c = rgb[i]
                    val luminance = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
                    gray[i] = ((128f + (luminance - 128f) * boost).toInt().coerceIn(0, 255)).toByte()
                }
                val colorPng = if (options.epubColor) ByteArrayOutputStream().use { buffer ->
                    check(rendered.compress(Bitmap.CompressFormat.PNG, 100, buffer)) { "Color PNG encoding failed" }
                    buffer.toByteArray()
                } else null
                val quantized = if (options.dither) GrayscaleConverter.dither(frameWidth, frameHeight, gray, depth)
                else {
                    val highest = if (depth == 1) 1 else 3
                    val levels = ByteArray(gray.size) { i ->
                        (((gray[i].toInt() and 255) * highest + 127) / 255).toByte()
                    }
                    QuantizedPage(frameWidth, frameHeight, levels, depth)
                }
                yield(if (colorPng == null) quantized else quantized.copy(colorPng = colorPng))
            } finally { rendered.recycle() }
        }
    }

}

