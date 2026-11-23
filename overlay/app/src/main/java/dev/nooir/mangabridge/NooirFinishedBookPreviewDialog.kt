package dev.nooir.mangabridge

import android.graphics.Bitmap
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Real finished-book decoded output viewer; not a pre-export simulation. */
@Composable
internal fun NooirFinishedBookPreviewDialog(
    onDismiss: () -> Unit,
    initialUri: Uri? = null,
) {
    val context = LocalContext.current
    var source by remember(initialUri) { mutableStateOf(initialUri) }
    var loaded by remember { mutableStateOf<NooirFinishedBookPreview.Loaded?>(null) }
    var pageIndex by remember { mutableStateOf(0) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var opening by remember { mutableStateOf(false) }
    var decoding by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) source = uri
    }

    LaunchedEffect(source) {
        val uri = source ?: return@LaunchedEffect
        opening = true
        loaded = null
        bitmap = null
        pageIndex = 0
        error = ""
        try {
            loaded = NooirFinishedBookPreview.open(context, uri)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: "Cannot open selected finished XTC/XTCH book"
        } finally {
            opening = false
        }
    }
    DisposableEffect(loaded) {
        val held = loaded
        onDispose { held?.file?.delete() }
    }
    LaunchedEffect(loaded, pageIndex) {
        val book = loaded ?: return@LaunchedEffect
        decoding = true
        bitmap = null
        try {
            bitmap = withContext(Dispatchers.IO) {
                NooirFinishedBookPreview.decodePage(book.file, pageIndex)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: "Cannot decode finished book page"
        } finally {
            decoding = false
        }
    }
    DisposableEffect(bitmap) {
        val image = bitmap
        onDispose { image?.recycle() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Finished XTC / XTCH preview") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 550.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Inspect already saved XTC/XTCH pages. No conversion or upload is started.")
                Button(onClick = { picker.launch(arrayOf("*/*")) }) {
                    Text("Choose finished XTC / XTCH book")
                }
                if (opening) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text("Opening finished book (256 MiB maximum)…")
                }
                if (error.isNotBlank()) Text("Preview error: " + error)
                val current = loaded
                if (current != null) {
                    Text(current.format.name + " · " + current.pageCount + " pages")
                    val pageImage = bitmap
                    if (decoding) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    if (pageImage != null && !pageImage.isRecycled) {
                        Image(
                            bitmap = pageImage.asImageBitmap(),
                            contentDescription = "Decoded " + current.format.name +
                                " page " + (pageIndex + 1),
                            modifier = Modifier.fillMaxWidth().height(305.dp),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    Text("Page " + (pageIndex + 1) + " of " + current.pageCount)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(enabled = pageIndex > 0, onClick = { pageIndex = 0 }) {
                            Text("First")
                        }
                        TextButton(enabled = pageIndex > 0, onClick = { pageIndex-- }) {
                            Text("Prev")
                        }
                        TextButton(enabled = pageIndex < current.pageCount - 1,
                            onClick = { pageIndex++ }) { Text("Next") }
                        TextButton(enabled = pageIndex < current.pageCount - 1,
                            onClick = { pageIndex = current.pageCount - 1 }) { Text("Last") }
                    }
                    Text("Pixels decoded from the finished book, including its true " +
                        "grayscale depth and device resolution. Original file unchanged.")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Back to converter") } },
    )
}
