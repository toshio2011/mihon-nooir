package dev.nooir.mangabridge

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.nooir.bridge.ExportFormat
import dev.nooir.bridge.NooirUpload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** More -> Send & Convert files. User-selected SAF documents only; no storage scanning. */
@Composable
fun AnyFileTransferDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("manganooir_export", Context.MODE_PRIVATE) }
    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var selected by remember { mutableStateOf<ImportedMangaConverter.SelectedFile?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var format by remember {
        mutableStateOf(runCatching { ExportFormat.valueOf(prefs.getString("format", "AUTO")!!) }
            .getOrDefault(ExportFormat.AUTO))
    }
    var device by remember { mutableStateOf(prefs.getString("device", "X4") ?: "X4") }
    var host by remember { mutableStateOf(prefs.getString("host", "crosspoint.local") ?: "crosspoint.local") }
    var destination by remember { mutableStateOf(prefs.getString("destination", "/Books") ?: "/Books") }

    fun persistSettings() {
        prefs.edit().putString("format", format.name).putString("device", device)
            .putString("host", host.trim()).putString("destination", destination.trim()).apply()
    }

    fun conversion(destinationUri: Uri?, sendToNooir: Boolean) {
        val uri = pickedUri ?: return
        val file = selected ?: return
        if (!file.isManga || busy) return
        persistSettings()
        busy = true
        status = "Converting manga…"
        scope.launch {
            status = try {
                val result = ImportedMangaConverter.convert(
                    context, uri, file, format, device,
                    saveDestination = destinationUri,
                    sendHost = if (sendToNooir) host.trim() else null,
                    sendFolder = destination.trim(),
                )
                if (sendToNooir) "Sent " + result.pages + " pages to Nooir as " + result.format.extension.uppercase() + "."
                else "Saved " + result.pages + " pages as " + result.format.extension.uppercase() + "."
            } catch (e: Exception) {
                "Conversion failed: " + (e.message ?: "Unknown error")
            } finally {
                busy = false
            }
        }
    }

    val saveConverted = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { output ->
        if (output != null) conversion(output, false)
    }
    val chooseFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { picked ->
        if (picked != null) {
            pickedUri = picked
            selected = null
            status = "Inspecting selected file…"
            busy = true
            scope.launch {
                try {
                    selected = withContext(Dispatchers.IO) { ImportedMangaConverter.inspect(context, picked) }
                    status = when (selected?.kind) {
                        ImportedMangaConverter.Kind.COMIC_ARCHIVE -> "Manga archive detected — conversion available."
                        ImportedMangaConverter.Kind.IMAGE -> "Manga image detected — conversion available."
                        else -> "Send original or share this file. Conversion is not available for this format."
                    }
                } catch (e: Exception) {
                    status = "Cannot inspect file: " + e.message
                    selected = null
                } finally {
                    busy = false
                }
            }
        }
    }
    val validReceiver = host.isNotBlank() && destination.startsWith("/") && !destination.contains("..")
    val name = selected?.name?.substringBeforeLast('.', selected?.name.orEmpty())
        ?.take(64)?.map { c -> if ((c.isLetterOrDigit() && c.code < 128) || c == '-' || c == '_') c else '_' }
        ?.joinToString("")?.ifBlank { "Manga" } ?: "Manga"

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Send & Convert files") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Button(onClick = { chooseFile.launch(arrayOf("*/*")) }, enabled = !busy) {
                    Text(if (selected == null) "Choose a file from phone" else "Choose another file")
                }
                selected?.let { file ->
                    Text("Selected: " + file.name)
                    file.bytes?.let { Text("Size: " + it / 1024 + " KB") }
                }
                if (status.isNotBlank()) Text(status)
                Text("Receiver (Folio Nooir)")
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("XTEink hostname or IP") },
                    singleLine = true,
                    supportingText = { Text("Start File Transfer on your XTEink. Both devices need the same Wi-Fi.") },
                )
                OutlinedTextField(
                    value = destination,
                    onValueChange = { destination = it },
                    label = { Text("Device folder") },
                    singleLine = true,
                )
                Button(
                    enabled = !busy && selected != null && validReceiver,
                    onClick = {
                        val uri = pickedUri ?: return@Button
                        persistSettings()
                        busy = true
                        status = "Sending original file…"
                        scope.launch {
                            status = try {
                                withContext(Dispatchers.IO) {
                                    NooirUpload.checkConnected(host.trim())
                                    NooirUpload.uploadDocument(context, uri, host.trim(), destination.trim())
                                }
                                "Original file transferred. The receiver may not support opening this format."
                            } catch (e: Exception) {
                                "Transfer failed: " + (e.message ?: "Unknown error")
                            } finally { busy = false }
                        }
                    },
                ) { Text("Send original to XTEink") }
                TextButton(
                    enabled = !busy && pickedUri != null,
                    onClick = { pickedUri?.let { ImportedMangaConverter.shareOriginal(context, it) } },
                ) { Text("Share original to other device/app") }

                if (selected?.isManga == true) {
                    Text("Manga detected — convert before sending")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("X4", "X3").forEach { preset ->
                            FilterChip(selected = device == preset, onClick = { device = preset }, label = { Text(preset) })
                        }
                    }
                    ExportFormat.entries.forEach { option ->
                        FilterChip(
                            selected = format == option,
                            onClick = { format = option },
                            label = { Text(option.label) },
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(enabled = !busy, onClick = {
                            saveConverted.launch(name + "." + format.resolved(device).extension)
                        }) { Text("Convert & save") }
                        Button(enabled = !busy && validReceiver, onClick = { conversion(null, true) }) {
                            Text("Convert & send")
                        }
                    }
                    Text("CBZ/ZIP images and JPG, PNG, WebP, BMP, GIF are supported inputs. Metadata from ComicInfo.xml is retained where available.")
                }
                Text("Other files (PDF, EPUB, documents, audio and more) can be sent unchanged. Whether they open on the receiver depends on its firmware.")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Done") }
        },
    )
}
