package dev.nooir.mangabridge

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import dev.nooir.bridge.ReflowEpubWriter
import java.io.ByteArrayOutputStream

/** Memory-bounded image collage with original covers fitted inside their tiles. */
internal object NooirCoverCollage {
    private const val WIDTH = 800
    private const val HEIGHT = 1120
    private const val MAX_OUTPUT_BYTES = 1_500_000

    fun compose(covers: List<ReflowEpubWriter.ImageAsset>): ReflowEpubWriter.ImageAsset {
        val tiles = NooirCoverCollageLayout.tiles(covers.size, WIDTH, HEIGHT)
        val canvasBitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.RGB_565)
        try {
            val canvas = Canvas(canvasBitmap)
            canvas.drawColor(Color.rgb(20, 23, 33))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            for ((i, source) in covers.withIndex()) {
                val tile = tiles[i]
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(source.bytes, 0, source.bytes.size, bounds)
                require(bounds.outWidth in 1..12000 && bounds.outHeight in 1..12000 &&
                    bounds.outWidth.toLong() * bounds.outHeight.toLong() <= 16_000_000L) {
                    "Invalid source book-cover dimensions"
                }
                var sample = 1
                while (bounds.outWidth / sample > WIDTH || bounds.outHeight / sample > HEIGHT) {
                    sample *= 2
                }
                val options = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                val image = BitmapFactory.decodeByteArray(source.bytes, 0, source.bytes.size, options)
                    ?: error("Cannot decode source book cover")
                try {
                    val rect = RectF(tile.x.toFloat(), tile.y.toFloat(),
                        (tile.x + tile.width).toFloat(), (tile.y + tile.height).toFloat())
                    val corner = 17f
                    paint.color = Color.rgb(39, 44, 58)
                    paint.style = Paint.Style.FILL
                    canvas.drawRoundRect(rect, corner, corner, paint)
                    val inner = RectF(rect.left + 7, rect.top + 7, rect.right - 7, rect.bottom - 7)
                    val factor = minOf(inner.width() / image.width, inner.height() / image.height)
                    val shownW = image.width * factor
                    val shownH = image.height * factor
                    val placement = RectF(inner.centerX() - shownW / 2, inner.centerY() - shownH / 2,
                        inner.centerX() + shownW / 2, inner.centerY() + shownH / 2)
                    val clip = Path().apply { addRoundRect(rect, corner, corner, Path.Direction.CW) }
                    canvas.save()
                    try {
                        canvas.clipPath(clip)
                        paint.color = Color.WHITE
                        canvas.drawBitmap(image, null, placement, paint)
                    } finally { canvas.restore() }
                    paint.color = Color.rgb(85, 92, 112)
                    paint.strokeWidth = 2f
                    paint.style = Paint.Style.STROKE
                    canvas.drawRoundRect(rect, corner, corner, paint)
                    paint.style = Paint.Style.FILL
                } finally { image.recycle() }
            }
            for (quality in listOf(88, 78, 68)) {
                val out = ByteArrayOutputStream()
                require(canvasBitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) {
                    "Could not encode the combined book cover"
                }
                val result = out.toByteArray()
                if (result.size in 1..MAX_OUTPUT_BYTES) {
                    return ReflowEpubWriter.ImageAsset(result, "jpg", "image/jpeg")
                }
            }
            error("Combined cover exceeds the safe EPUB image budget")
        } finally { canvasBitmap.recycle() }
    }
}
