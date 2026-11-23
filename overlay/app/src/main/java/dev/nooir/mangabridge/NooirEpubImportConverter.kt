package dev.nooir.mangabridge

import android.content.Context
import android.net.Uri
import dev.nooir.bridge.NooirUpload
import dev.nooir.bridge.XtcFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** One EPUB book per conversion, buffered in bounded local temp files. */
internal object NooirEpubImportConverter {
    suspend fun convert(
        context: Context,
        sourceUri: Uri,
        format: XtcFormat,
        device: String,
        fontPx: Int = 24,
        marginPx: Int = 26,
        rightToLeft: Boolean = false,
        lineHeightPercent: Int = 118,
        fontFamily: String = "sans-serif",
        saveDestination: Uri? = null,
        sendHost: String? = null,
        sendFolder: String = "/Books",
        outputName: String = "Book.xtc",
        checkCancelled: () -> Unit = {},
        onStage: (String) -> Unit = {},
        onUploadProgress: (Long, Long) -> Unit = { _, _ -> },
    ): Int = withContext(Dispatchers.IO) {
        require(saveDestination != null || sendHost != null) { "Select a destination" }
        val folder = File(context.cacheDir, "nooir-epub-" + UUID.randomUUID())
        check(folder.mkdirs()) { "Cannot create temporary EPUB conversion directory" }
        val input = File(folder, "input.epub")
        val output = File(folder, outputName.substringAfterLast('/').take(80))
        try {
            onStage("Reading EPUB package")
            context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                input.outputStream().use { target ->
                    val bytes = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        checkCancelled()
                        val n = stream.read(bytes)
                        if (n < 0) break
                        total += n
                        require(total <= NooirEpubBook.MAX_INPUT) {
                            "EPUB is larger than 200 MiB; choose a smaller book"
                        }
                        target.write(bytes, 0, n)
                    }
                }
            } ?: error("Cannot open EPUB file")
            onStage("Paginating EPUB text and illustrations")
            val count = NooirEpubToXtc.convert(
                input, output, format, device,
                settings = NooirEpubToXtc.Settings(
                    fontPx = fontPx, marginPx = marginPx,
                    lineSpacing = lineHeightPercent.coerceIn(100, 200) / 100f,
                    fontFamily = fontFamily,
                ),
                rtl = rightToLeft,
                checkCancelled = checkCancelled,
                onPage = { page, _ -> onStage("Rendering EPUB page " + page) },
            )
            checkCancelled()
            if (saveDestination != null) {
                onStage("Saving converted XTC/XTCH")
                context.contentResolver.openOutputStream(saveDestination, "w")?.use { sink ->
                    output.inputStream().use { src ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            checkCancelled()
                            val n = src.read(buffer)
                            if (n < 0) break
                            sink.write(buffer, 0, n)
                        }
                    }
                } ?: error("Cannot write converted book")
            } else {
                onStage("Sending converted XTC/XTCH")
                NooirUpload.checkConnected(requireNotNull(sendHost))
                NooirUpload.upload(output, sendHost, sendFolder, onUploadProgress)
            }
            checkCancelled()
            count
        } finally {
            output.delete()
            input.delete()
            folder.delete()
        }
    }
}
