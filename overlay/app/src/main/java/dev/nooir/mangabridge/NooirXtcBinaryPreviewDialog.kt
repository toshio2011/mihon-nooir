package dev.nooir.mangabridge

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.nooir.bridge.XtcBookPreview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Copy a user-selected SAF document to the caller-owned temporary file.
 * Do not impose an arbitrary page count or file size: streaming copy keeps
 * heap bounded, and cancellation is checked on every 64 KiB block.
 *
 * The caller MUST delete the temp file when its Compose producer is cancelled.
 */
internal suspend fun copyNooirXtcPreviewDocument(context: Context, uri: Uri, file: File) {
    val coroutine = currentCoroutineContext()
    val input = context.contentResolver.openInputStream(uri)
        ?: error("Cannot read selected output file")
    input.use { source ->
        file.outputStream().use { target ->
            val bytes = ByteArray(64 * 1024)
            while (true) {
                coroutine.ensureActive()
                val count = source.read(bytes)
                if (count < 0) break
                if (count > 0) target.write(bytes, 0, count)
            }
            coroutine.ensureActive()
        }
    }
}

/**
 * Inspect pages from an already-saved, encoded XTC/XTCH output.
 *
 * This is intentionally separate from AndroidPageSource.previewFirstPage(),
 * which previews conversion results before the binary writer runs.
 * The SAF document is copied to private temporary storage for seekable reading
 * (bounded RAM, not an arbitrary page or size cutoff), then deleted on close.
 */
@Composable
fun NooirXtcBinaryPreviewDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var picked by remember { mutableStateOf<Uri?>(null) }
    var pageIndex by remember { mutableStateOf(0) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            picked = uri
            pageIndex = 0
        }
    }
    val local by produceState<Result<File>?>(initialValue = null, picked) {
        value = null
        val uri = picked ?: return@produceState
        var temporary: File? = null
        try {
            withContext(Dispatchers.IO) {
                val staging = File.createTempFile("nooir-xtc-inspect-", ".bin", context.cacheDir)
                // Assign before any cancellable read: cancellation on return
                // from Dispatchers.IO must still clean up this same file.
                temporary = staging
                copyNooirXtcPreviewDocument(context, uri, staging)
            }
            value = Result.success(checkNotNull(temporary))
            // Keep ownership while the file is displayed. A finished producer
            // previously leaked the file if the picker/dialog closed between
            // copy completion and DisposableEffect(file) installation.
            awaitCancellation()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            value = Result.failure(error)
        } finally {
            // Guaranteed on success, I/O failure, picker change and dismissal.
            withContext(NonCancellable + Dispatchers.IO) {
                temporary?.delete()
            }
        }
    }
    val file = local?.getOrNull()
    val book = remember(file) { file?.let { runCatching { XtcBookPreview(it) } } }
    val inspector = book?.getOrNull()
    val shown by produceState<Result<Bitmap>?>(initialValue = null, file, pageIndex) {
        value = null
        val opened = inspector ?: return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                val page = opened.page(pageIndex)
                val pixels = IntArray(page.levels.size) { i ->
                    val level = page.levels[i].toInt() and 255
                    val grayscale = if (page.depth == 1) level * 255 else level * 85
                    Color.rgb(grayscale, grayscale, grayscale)
                }
                Bitmap.createBitmap(pixels, page.width, page.height, Bitmap.Config.ARGB_8888)
            }
        }
    }
    val bitmap = shown?.getOrNull()
    DisposableEffect(bitmap) { onDispose { bitmap?.recycle() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Inspect actual XTC / XTCH file") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 510.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Open a completed .xtc or .xtch file. This reads the saved binary page data — unlike the live conversion preview.")
                TextButton(onClick = {
                    picker.launch(arrayOf("application/octet-stream", "application/x-xtc", "application/x-xtch", "*/*"))
                }) { Text(if (picked == null) "Choose exported book" else "Choose another book") }
                when {
                    picked == null -> Text("No file selected. The reader and source files will not be modified.")
                    local == null -> Text("Preparing seekable preview from selected file…")
                    local?.isFailure == true -> Text("Unable to open selected file: " +
                        (local?.exceptionOrNull()?.message ?: "File unavailable").take(160))
                    book?.isFailure == true -> Text("Invalid XTC / XTCH file: " +
                        (book.exceptionOrNull()?.message ?: "Damaged index").take(160))
                    inspector != null -> {
                        Text("Format: ${inspector.format.name} · ${inspector.pageCount} pages")
                        when {
                            shown == null -> Text("Decoding stored page…")
                            shown?.isFailure == true -> Text("Page damaged: " +
                                (shown?.exceptionOrNull()?.message ?: "Cannot decode").take(160))
                            bitmap != null -> {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Actual saved binary page ${pageIndex + 1}",
                                    modifier = Modifier.fillMaxWidth().height(300.dp),
                                    contentScale = ContentScale.Fit
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(enabled = pageIndex > 0,
                                        onClick = { pageIndex-- }) { Text("Previous") }
                                    Text("Page ${pageIndex + 1} / ${inspector.pageCount}")
                                    TextButton(enabled = pageIndex + 1 < inspector.pageCount,
                                        onClick = { pageIndex++ }) { Text("Next") }
                                }
                            }
                        }
                    }
                }
                Text("Local inspection only. No Wi-Fi transfer, reader changes, or automatic file overwrite.")
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Done") } }
    )
}
