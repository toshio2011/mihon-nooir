package dev.nooir.mangabridge

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

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
        value = withContext(Dispatchers.IO) {
            runCatching {
                val file = File.createTempFile("nooir-xtc-inspect-", ".bin", context.cacheDir)
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
                    } ?: error("Cannot read selected output file")
                    file
                } catch (e: Throwable) {
                    file.delete()
                    throw e
                }
            }
        }
    }
    val file = local?.getOrNull()
    DisposableEffect(file) {
        onDispose { file?.delete() }
    }
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
