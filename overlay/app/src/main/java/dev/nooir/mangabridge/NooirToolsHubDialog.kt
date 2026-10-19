package dev.nooir.mangabridge

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
    when (tool) {
        "queue" -> NooirActivityQueueDialog(onDismiss = { tool = "" })
        "files" -> AnyFileTransferDialog(onDismiss = { tool = "" })
        "web" -> NooirWebToReaderDialog(onDismiss = { tool = "" })
        "novels" -> NooirNovelBatchDialog(onDismiss = { tool = "" })
        "pdf" -> NooirPdfToEpubDialog(onDismiss = { tool = "" })
        "device" -> NooirDeviceFilesDialog(onDismiss = { tool = "" })
        else -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Nooir Tools") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Text("Convert, send and manage books—all in one place without crowding Mihon's original settings.")
                    Button(modifier = Modifier.fillMaxWidth(), onClick = { tool = "queue" }) {
                        Text("Activity Queue · All current jobs")
                    }
                    Text("Manga & local files")
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { tool = "files" }) {
                        Text("Send & Convert files")
                    }
                    Text("Books from the web")
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { tool = "web" }) {
                        Text("Web link → EPUB")
                    }
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { tool = "novels" }) {
                        Text("Web novels · Batch EPUB")
                    }
                    Text("Books from PDF")
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { tool = "pdf" }) {
                        Text("PDF → EPUB · Text + illustrations")
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
