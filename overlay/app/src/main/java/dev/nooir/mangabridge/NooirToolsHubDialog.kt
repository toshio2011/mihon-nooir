package dev.nooir.mangabridge

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Exactly one extra entry in Mihon's More screen, instead of adding a new
 * Mihon Settings row for each experimental Nooir converter.
 *
 * Conversion preferences stay inside the tool that uses them.
 */
@Composable
fun NooirToolsHubDialog(onDismiss: () -> Unit) {
    var tool by remember { mutableStateOf("") }
    var previewUri by remember { mutableStateOf<Uri?>(null) }
    when (tool) {
        "queue" -> NooirActivityQueueDialog(onDismiss = { tool = "" })
        "files" -> AnyFileTransferDialog(
            onDismiss = { tool = "converter" },
            onOpenPdfConverter = { tool = "pdf" },
            onOpenFinishedPreview = { uri ->
                previewUri = uri
                tool = "preview"
            },
        )
        "preview" -> NooirFinishedBookPreviewDialog(
            initialUri = previewUri,
            onDismiss = {
                previewUri = null
                tool = "converter"
            },
        )
        "web" -> NooirWebToReaderDialog(onDismiss = { tool = "converter" })
        "novels" -> NooirNovelBatchDialog(onDismiss = { tool = "converter" })
        "pdf" -> NooirPdfToEpubDialog(onDismiss = { tool = "converter" })
        "device" -> NooirDeviceFilesDialog(onDismiss = { tool = "" })
        "converter" -> AlertDialog(
            onDismissRequest = { tool = "" },
            title = { Text("Universal Book Converter") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 550.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Choose where your book comes from. Each source opens the right " +
                        "converter and offers only formats it can actually produce.")
                    OutlinedButton(modifier = Modifier.fillMaxWidth(),
                        onClick = { tool = "files" }) {
                        Text("Local files · EPUB, CBZ, ZIP, images")
                    }
                    Text("Select input type before browsing; then choose output and its settings. " +
                        "You can also send supported original files unchanged.")
                    OutlinedButton(modifier = Modifier.fillMaxWidth(),
                        onClick = { previewUri = null; tool = "preview" }) {
                        Text("Preview finished XTC / XTCH book")
                    }
                    Text("Inspect already exported page pixels from a saved XTC/XTCH file. " +
                        "This uses the actual output, not a simulated EPUB render.")
                    OutlinedButton(modifier = Modifier.fillMaxWidth(),
                        onClick = { tool = "pdf" }) {
                        Text("PDF files · Dedicated PDF converter")
                    }
                    Text("Uses our existing PDF conversion tools; " +
                        "does not pretend the local manga converter handles PDF.")
                    OutlinedButton(modifier = Modifier.fillMaxWidth(),
                        onClick = { tool = "web" }) {
                        Text("Web link · Convert a public webpage")
                    }
                    Text("Paste a link and choose available e-reader output formats.")
                    OutlinedButton(modifier = Modifier.fillMaxWidth(),
                        onClick = { tool = "novels" }) {
                        Text("Web novels · Combine selected chapters")
                    }
                    Text("Use existing chapter discovery and batch conversion workflow. " +
                        "Advanced WebToEpub editor changes remain in their separate draft.")
                }
            },
            confirmButton = {
                TextButton(onClick = { tool = "" }) { Text("Back to Nooir Tools") }
            },
        )
        else -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Nooir Tools") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Text("Create, convert, send and manage books without crowding Mihon's settings.")
                    Button(modifier = Modifier.fillMaxWidth(), onClick = { tool = "converter" }) {
                        Text("Universal Book Converter")
                    }
                    Text("Local EPUB, CBZ, images and PDF; public web links and multi-chapter " +
                        "novels. Choose source → output → relevant settings.")
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { tool = "queue" }) {
                        Text("Activity Queue · All current jobs")
                    }
                    Text("XTEink device management")
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { tool = "device" }) {
                        Text("Browse XTEink files")
                    }
                    Text("Manga details → select chapters → Export stays where it already is. Mihon's native downloads and settings also stay unchanged.")
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        )
    }
}
