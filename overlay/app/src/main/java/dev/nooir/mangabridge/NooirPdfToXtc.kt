package dev.nooir.mangabridge

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import dev.nooir.bridge.BookChapter
import dev.nooir.bridge.BookMetadata
import dev.nooir.bridge.GrayscaleConverter
import dev.nooir.bridge.NooirDeviceProfiles
import dev.nooir.bridge.QuantizedPage
import dev.nooir.bridge.ReadingDirection
import dev.nooir.bridge.XtcCodec
import dev.nooir.bridge.XtcFormat
import java.io.File

/**
 * Raster PDF → native XTC/XTCH. This deliberately does NOT replace the existing
 * PDF → reflowable EPUB path: text becomes pixels only when XTC/XTCH is selected.
 * Render one PDF page at a time to the chosen reader's framebuffer dimensions.
 */
object NooirPdfToXtc {
    fun convert(
        source: File, output: File, format: XtcFormat, device: String,
        title: String, author: String,
        onPage: (Int, Int) -> Unit = { _, _ -> },
    ): Int {
        require(source.isFile && source.length() > 0L) { "Missing PDF input" }
        val profile = NooirDeviceProfiles.forId(device)
        require(profile.width > 0 && profile.height > 0)
        require(format != XtcFormat.XTCH || profile.height % 8 == 0) {
            "XTCH requires a device height divisible by 8"
        }
        ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { pdf ->
                val count = pdf.pageCount
                require(count in 1..600) { "PDF must have 1–600 pages" }
                val pages = sequence {
                    for (index in 0 until count) {
                        pdf.openPage(index).use { page ->
                            val w = profile.width
                            val h = profile.height
                            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            try {
                                bitmap.eraseColor(Color.WHITE)
                                val scale = minOf(w.toFloat() / page.width, h.toFloat() / page.height)
                                val matrix = Matrix().apply {
                                    postScale(scale, scale)
                                    postTranslate((w - page.width * scale) / 2f,
                                        (h - page.height * scale) / 2f)
                                }
                                page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                val pixels = IntArray(w * h)
                                bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
                                val gray = ByteArray(pixels.size) { i ->
                                    val c = pixels[i]
                                    ((Color.red(c) * 299 + Color.green(c) * 587 +
                                        Color.blue(c) * 114) / 1000).toByte()
                                }
                                yield(GrayscaleConverter.dither(w, h, gray, format.depth))
                            } finally {
                                bitmap.recycle()
                            }
                        }
                    }
                }
                output.outputStream().use { out ->
                    XtcCodec.write(format, pages, out, output.parentFile ?: source.parentFile,
                        metadata = BookMetadata(title = title, author = author, coverPage = 0),
                        chapters = listOf(BookChapter("PDF", 0, count - 1)),
                        direction = ReadingDirection.LEFT_TO_RIGHT,
                        onPageEncoded = { onPage(it, count) })
                }
                return count
            }
        }
    }
}
