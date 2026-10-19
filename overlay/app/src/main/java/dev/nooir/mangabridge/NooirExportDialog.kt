package dev.nooir.mangabridge

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.nooir.bridge.ExportFormat

/** Lightweight settings dialog; always preserves Mihon's original downloads. */
@Composable
fun NooirExportDialog(
    mangaTitle: String,
    onDismiss: () -> Unit,
    onSave: (ExportFormat, String, Boolean) -> Unit,
    onSend: (ExportFormat, String, String, String, Boolean) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("manganooir_export", Context.MODE_PRIVATE) }
    var selectedFormat by remember {
        mutableStateOf(runCatching { ExportFormat.valueOf(prefs.getString("format", "AUTO")!!) }
            .getOrDefault(ExportFormat.AUTO))
    }
    var device by remember { mutableStateOf(prefs.getString("device", "X4") ?: "X4") }
    var host by remember { mutableStateOf(prefs.getString("host", "crosspoint.local") ?: "crosspoint.local") }
    var destination by remember { mutableStateOf(prefs.getString("destination", "/Books") ?: "/Books") }
    var quickExport by remember { mutableStateOf(false) }

    fun saveSettings() {
        prefs.edit()
            .putString("format", selectedFormat.name)
            .putString("device", device)
            .putString("host", host.trim())
            .putString("destination", destination.trim())
            .apply()
    }
    val canSend = host.trim().isNotEmpty() &&
        destination.trim().startsWith("/") && !destination.contains("..")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export to e-reader") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Manga: $mangaTitle")
                Text("Device preset")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("X4", "X3").forEach { option ->
                        FilterChip(
                            selected = device == option,
                            onClick = { device = option },
                            label = { Text(option) },
                        )
                    }
                }
                Text("Chapter source")
                Row {
                    Checkbox(checked = quickExport, onCheckedChange = { quickExport = it })
                    TextButton(onClick = { quickExport = !quickExport }) {
                        Text("Quick Export · fetch missing pages")
                    }
                }
                Text(
                    if (quickExport) "Select 1–5 chapters in the chapter list first. Missing pages are fetched temporarily, then deleted after export."
                    else "Use Mihon's permanent downloads. If no chapters are selected, all downloaded chapters are exported."
                )
                Text("Output format")
                ExportFormat.entries.forEach { choice ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                        RadioButton(
                            selected = selectedFormat == choice,
                            onClick = { selectedFormat = choice },
                        )
                        TextButton(onClick = { selectedFormat = choice }) {
                            Text(choice.label)
                        }
                    }
                }
                Text("XTEink / Folio Nooir Wi-Fi transfer")
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Device hostname or IP") },
                    singleLine = true,
                    supportingText = { Text("Use crosspoint.local or the IP shown on your XTEink") },
                )
                OutlinedTextField(
                    value = destination,
                    onValueChange = { destination = it },
                    label = { Text("Destination folder") },
                    singleLine = true,
                )
                Text("Start File Transfer on Nooir and connect both devices to the same Wi-Fi. Sending to an existing filename replaces it. Original Mihon downloads are kept.")
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = {
                    saveSettings()
                    onDismiss()
                    onSave(selectedFormat, device, quickExport)
                }) { Text("Save file") }
                Button(enabled = canSend, onClick = {
                    saveSettings()
                    onDismiss()
                    onSend(selectedFormat, device, host.trim(), destination.trim(), quickExport)
                }) { Text("Send to XTEink") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
