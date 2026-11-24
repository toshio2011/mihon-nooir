package dev.nooir.mangabridge

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import dev.nooir.bridge.XtcBookPreview
import dev.nooir.bridge.XtcFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

/** Read-only, post-encoding XTC/XTCH preview. No network or source mutation. */
internal object NooirFinishedBookPreview {
    private const val MAX_BYTES = 256L * 1024 * 1024
    data class Loaded(val file: File, val format: XtcFormat, val pageCount: Int)

    suspend fun open(context: Context, uri: Uri): Loaded = withContext(Dispatchers.IO) {
        val file = File.createTempFile("nooir-finished-preview-", ".xtc", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                file.outputStream().use { output ->
                    var total = 0L
                    val bytes = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = stream.read(bytes)
                        if (n < 0) break
                        total += n
                        require(total <= MAX_BYTES) { "Finished book preview limit is 256 MiB" }
                        output.write(bytes, 0, n)
                    }
                }
            } ?: error("Cannot read the selected XTC/XTCH document")
            currentCoroutineContext().ensureActive()
            val book = XtcBookPreview(file)
            Loaded(file, book.format, book.pageCount)
        } catch (e: Throwable) {
            file.delete()
            throw e
        }
    }

    fun decodePage(file: File, index: Int): Bitmap {
        val page = XtcBookPreview(file).page(index)
        val maxLevel = (1 shl page.depth) - 1
        val pixels = IntArray(page.levels.size) { i ->
            val level = (page.levels[i].toInt() and 255).coerceIn(0, maxLevel)
            val gray = level * 255 / maxLevel
            Color.rgb(gray, gray, gray)
        }
        return Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, page.width, 0, 0, page.width, page.height)
        }
    }
}
