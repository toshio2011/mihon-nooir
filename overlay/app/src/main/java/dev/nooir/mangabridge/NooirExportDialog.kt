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
import dev.nooir.bridge.NooirDeviceProfiles

/** Lightweight settings dialog; always preserves Mihon's original downloads. */
@Composable
fun NooirExportDialog(
    mangaTitle: String,
    selectedChapterCount: Int,
    onlineSource: Boolean,
    onDismiss: () -> Unit,
    onSave: (ExportFormat, String, Boolean, MangaConversionOptions) -> Unit,
    onSend: (ExportFormat, String, String, String, Boolean, MangaConversionOptions, String) -> Unit,
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
    var outputName by remember(mangaTitle) { mutableStateOf(mangaTitle) }
    var showAdvanced by remember { mutableStateOf(false) }
    var options by remember { mutableStateOf(MangaConversionOptions.load(prefs)) }

    fun saveSettings() {
        prefs.edit()
            .putString("format", selectedFormat.name)
            .putString("device", device)
            .putString("host", host.trim())
            .putString("destination", destination.trim())
            .apply()
        options.save(prefs)
    }
    val canExport = !quickExport || (onlineSource && selectedChapterCount >= 1)
    val profile = NooirDeviceProfiles.forId(device)
    val canSend = canExport && profile.xteinkWifi && outputName.isNotBlank() && host.trim().isNotEmpty() &&
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
                NooirDevicePicker(device) { id ->
                    device = id
                    if (!NooirDeviceProfiles.forId(id).accepts(selectedFormat)) selectedFormat = ExportFormat.AUTO
                }
                Text("Chapter source")
                Row {
                    Checkbox(checked = quickExport, enabled = onlineSource, onCheckedChange = { quickExport = it })
                    TextButton(enabled = onlineSource, onClick = { quickExport = !quickExport }) {
                        Text("Quick Export · fetch missing pages")
                    }
                }
                Text(
                    if (!onlineSource) "Quick Export requires an online manga source."
                    else if (quickExport) "Select any number of chapters first, even a whole volume. MangaNooir fetches and converts one chapter at a time, cleaning temporary images after each."
                    else "Use Mihon's permanent downloads. If no chapters are selected, all downloaded chapters are exported."
                )
                if (quickExport) Text("Selected chapters: $selectedChapterCount")
                Text("Output format")
                ExportFormat.entries.filter(profile::accepts).forEach { choice ->
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
                TextButton(onClick = { showAdvanced = !showAdvanced }) {
                    Text(if (showAdvanced) "Hide advanced conversion" else "More conversion settings")
                }
                if (showAdvanced) MangaConversionSettings(options, selectedFormat.resolved(device), onChange = { options = it })
                Text(if (profile.xteinkWifi) "XTEink Wi-Fi transfer (compatible firmware only)"
                    else "Direct XTEink Wi-Fi sending is unavailable for this reader. Save the file and transfer it with the reader’s supported method.")
                if (profile.xteinkWifi) OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Device hostname or IP") },
                    singleLine = true,
                    supportingText = { Text("Use crosspoint.local or the IP shown on your XTEink") },
                )
                if (profile.xteinkWifi) OutlinedTextField(
                    value = destination,
                    onValueChange = { destination = it },
                    label = { Text("Destination folder") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = outputName,
                    onValueChange = { outputName = it.take(70) },
                    label = { Text("Output filename (without extension)") },
                    singleLine = true,
                    supportingText = { Text("Change this name if the book already exists on the reader. The format extension is added automatically.") },
                )
                Text("MangaNooir checks the destination filename before converting. If it exists, this book fails safely and the next queued book continues.")
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(enabled = canExport, onClick = {
                    saveSettings()
                    onDismiss()
                    onSave(selectedFormat, device, quickExport, options)
                }) { Text("Save file") }
                Button(enabled = canSend, onClick = {
                    saveSettings()
                    onDismiss()
                    onSend(selectedFormat, device, host.trim(), destination.trim(), quickExport, options, outputName.trim())
                }) { Text("Send to XTEink") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
