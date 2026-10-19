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
                            try { for (page in render(image, width, height, depth, options)) yield(page) }
                            finally { image.recycle() }
                        }
                    }
                } finally { temp.delete() }
            } else {
                val image = decode({ resolver.openInputStream(uri) ?: error("Cannot open $uri") })
                try { for (page in render(image, width, height, depth, options)) yield(page) }
                finally { image.recycle() }
            }
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
        options: MangaConversionOptions,
    ): List<QuantizedPage> {
        val regions = mutableListOf<Rect>()
        val ratio = src.width.toFloat() / src.height
        val longStrip = when (options.layout) {
            "WEBTOON" -> true
            "PAGED" -> false
            else -> src.height > 3 * src.width
        }
        if (longStrip) {
            val segment = (src.width.toFloat() * height / width).toInt().coerceAtLeast(1)
            var y = 0
            while (y < src.height) {
                val next = minOf(src.height, y + segment)
                regions.add(Rect(0, y, src.width, next))
                y = next
            }
        } else if (ratio > 1.35f && options.splitSpreads) {
            val center = src.width / 2
            if (options.rightToLeft) {
                regions.add(Rect(center, 0, src.width, src.height))
                regions.add(Rect(0, 0, center, src.height))
            } else {
                regions.add(Rect(0, 0, center, src.height))
                regions.add(Rect(center, 0, src.width, src.height))
            }
        } else regions.add(Rect(0, 0, src.width, src.height))
        val result = ArrayList<QuantizedPage>(regions.size)
        for (initial in regions) {
            val r = if (options.cropWhiteMargins) trimWhite(src, initial) else initial
            val scale = minOf(width.toFloat() / r.width(), height.toFloat() / r.height())
            val dw = (r.width() * scale).toInt().coerceIn(1, width)
            val dh = (r.height() * scale).toInt().coerceIn(1, height)
            val rendered = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(rendered)
                canvas.drawColor(Color.WHITE)
                val l = (width - dw) / 2
                val t = (height - dh) / 2
                canvas.drawBitmap(src, r, Rect(l, t, l + dw, t + dh), Paint(Paint.FILTER_BITMAP_FLAG))
                val rgb = IntArray(width * height)
                rendered.getPixels(rgb, 0, width, 0, 0, width, height)
                val gray = ByteArray(rgb.size)
                val boost = (options.contrast.coerceIn(0, 100) / 50f).coerceIn(0.15f, 2f)
                for (i in rgb.indices) {
                    val c = rgb[i]
                    val luminance = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
                    gray[i] = ((128f + (luminance - 128f) * boost).toInt().coerceIn(0, 255)).toByte()
                }
                result.add(if (options.dither) GrayscaleConverter.dither(width, height, gray, depth)
                else {
                    val highest = if (depth == 1) 1 else 3
                    val levels = ByteArray(gray.size) { i ->
                        (((gray[i].toInt() and 255) * highest + 127) / 255).toByte()
                    }
                    QuantizedPage(width, height, levels, depth)
                })
            } finally { rendered.recycle() }
        }
        return result
    }

}

/** Stable human-friendly filename ordering: page 2 comes before page 10. */
internal object NaturalPageOrder {
    private val pieces = Regex("(\\d+|\\D+)")
    fun compare(first: String, second: String): Int {
        val a = pieces.findAll(first).map { it.value }.toList()
        val b = pieces.findAll(second).map { it.value }.toList()
        for (i in 0 until minOf(a.size, b.size)) {
            val x = a[i]; val y = b[i]
            val diff = if (x.all { it.isDigit() } && y.all { it.isDigit() }) {
                val nx=x.trimStart('0').ifBlank { "0" }
                val ny=y.trimStart('0').ifBlank { "0" }
                nx.length.compareTo(ny.length).takeIf { it != 0 }
                    ?: nx.compareTo(ny)
            } else x.compareTo(y, ignoreCase=true)
            if (diff != 0) return diff
        }
        return a.size.compareTo(b.size)
    }
}