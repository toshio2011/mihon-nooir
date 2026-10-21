package dev.nooir.mangabridge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.nooir.bridge.NooirDeviceProfiles

/**
 * One chooser shared by manga export and local file conversion.
 * Non-XTEink readers are export-to-file targets; their delivery is handled
 * externally using the reader's supported app/cable/network method.
 */
@Composable
fun NooirDevicePicker(device: String, onSelect: (String) -> Unit) {
    val chosen = NooirDeviceProfiles.forId(device)
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Target e-reader")
        TextButton(onClick = { expanded = true }) {
            Text("${chosen.name} · ${chosen.width} × ${chosen.height} ▾")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            NooirDeviceProfiles.builtins.forEach { entry ->
                DropdownMenuItem(
                    text = { Text("${entry.name} (${entry.width}×${entry.height})") },
                    onClick = {
                        expanded = false
                        onSelect(entry.id)
                    },
                )
            }
            DropdownMenuItem(text = { Text("Custom screen size…") }, onClick = {
                expanded = false
                onSelect(if (NooirDeviceProfiles.isCustom(device)) device
                    else NooirDeviceProfiles.custom(480, 800))
            })
        }
        if (NooirDeviceProfiles.isCustom(device)) {
            var widthText by remember(device) { mutableStateOf(chosen.width.toString()) }
            var heightText by remember(device) { mutableStateOf(chosen.height.toString()) }
            val custom = runCatching {
                NooirDeviceProfiles.custom(
                    widthText.toIntOrNull() ?: -1,
                    heightText.toIntOrNull() ?: -1,
                )
            }.getOrNull()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = widthText,
                    onValueChange = { widthText = it.take(4).filter(Char::isDigit) },
                    label = { Text("Width px") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = heightText,
                    onValueChange = { heightText = it.take(4).filter(Char::isDigit) },
                    label = { Text("Height px") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Button(enabled = custom != null && custom != device, onClick = {
                custom?.let(onSelect)
            }) { Text("Apply custom size") }
            if (custom == null) Text("64–4096 px per edge, maximum 3 million pixels.")
        }
        Text("Auto format: ${chosen.autoFormat.extension.uppercase()} · " +
            if (chosen.xteinkWifi) "XTEink Wi-Fi transfer requires compatible reader firmware."
            else "Save to phone and transfer using this reader's supported method.")
        if (chosen.note.isNotBlank()) Text(chosen.note)
    }
}
