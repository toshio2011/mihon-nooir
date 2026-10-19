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

    fun pages(inputUris: List<Uri>, width: Int, height: Int, depth: Int): Sequence<QuantizedPage> = sequence {
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
                            try { for (page in render(image, width, height, depth)) yield(page) }
                            finally { image.recycle() }
                        }
                    }
                } finally { temp.delete() }
            } else {
                val image = decode({ resolver.openInputStream(uri) ?: error("Cannot open $uri") })
                try { for (page in render(image, width, height, depth)) yield(page) }
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

    private fun render(src: Bitmap, width: Int, height: Int, depth: Int): List<QuantizedPage> {
        val regions = mutableListOf<Rect>()
        val ratio = src.width.toFloat() / src.height
        if (ratio > 1.35f) {
            // Japanese-style double spreads, ordered right-to-left.
            val center = src.width / 2
            regions.add(Rect(center, 0, src.width, src.height))
            regions.add(Rect(0, 0, center, src.height))
        } else if (src.height > 3 * src.width) {
            // Vertical webtoon strip: cut into roughly one-screen chunks.
            val segment = (src.width.toFloat() * height / width).toInt().coerceAtLeast(1)
            var y = 0
            while (y < src.height) {
                val next = minOf(src.height, y + segment)
                regions.add(Rect(0, y, src.width, next))
                y = next
            }
        } else regions.add(Rect(0, 0, src.width, src.height))
        val result = ArrayList<QuantizedPage>(regions.size)
        for (r in regions) {
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
                for (i in rgb.indices) {
                    val c = rgb[i]
                    val r8 = Color.red(c)
                    val g8 = Color.green(c)
                    val b8 = Color.blue(c)
                    gray[i] = ((r8 * 299 + g8 * 587 + b8 * 114) / 1000).toByte()
                }
                result.add(GrayscaleConverter.dither(width, height, gray, depth))
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