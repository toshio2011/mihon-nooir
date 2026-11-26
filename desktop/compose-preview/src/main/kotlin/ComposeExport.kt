package dev.nooir.desktop.compose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.nooir.bridge.ExportFormat
import dev.nooir.bridge.NooirDeviceProfiles
import dev.nooir.desktop.suwayomi.SuwayomiMangaCard
import dev.nooir.desktop.suwayomi.SuwayomiChapter
import dev.nooir.desktop.suwayomi.SuwayomiToNooir
import dev.nooir.mangabridge.MangaConversionOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser

/** Initial native export slice. Shares the exact existing converter and options token. */
@Composable
internal fun ComposeExport(card: SuwayomiMangaCard, chapters: List<SuwayomiChapter>, close: () -> Unit) {
    var device by remember { mutableStateOf(NooirDeviceProfiles.builtins.first()) }
    var format by remember { mutableStateOf(ExportFormat.XTC) }
    var options by remember { mutableStateOf(MangaConversionOptions.preset("Manga")) }
    var allowDownload by remember { mutableStateOf(false) }
    var output by remember { mutableStateOf<File?>(null) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        // Opt-in CI diagnostic only: no title, source IDs or user paths.
        if (System.getenv("NOOIR_COMPOSE_QA_CAPTURE") == "1") {
            println("QA_EXPORT_DIALOG chapters=${chapters.size}")
        }
    }
    val scope = rememberCoroutineScope()
    val missing = chapters.count { !it.downloaded }
    val valid = chapters.isNotEmpty() && chapters.size <= 500 && (missing == 0 || allowDownload)
    AlertDialog(onDismissRequest = { if (!busy) close() },
        title = { Text("Export manga") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(card.title, style = MaterialTheme.typography.titleMedium)
                Text("${chapters.size} chapters selected · $missing not downloaded")
                Text("Device", style = MaterialTheme.typography.titleSmall)
                ExportChoice(device.name, NooirDeviceProfiles.builtins.map { it.name }, !busy) { name ->
                    device = NooirDeviceProfiles.builtins.first { it.name == name }
                    if (!device.accepts(format)) format = device.autoFormat
                    output = null
                }
                Text("Output format", style = MaterialTheme.typography.titleSmall)
                ExportChoice(format.name, ExportFormat.entries.filter { it != ExportFormat.AUTO && device.accepts(it) }.map { it.name }, !busy) {
                    format = ExportFormat.valueOf(it); output = null
                }
                OutlinedButton(onClick = {
                    options = MangaConversionOptions.preset("Manga").copy(panelMode = "PANEL_BY_PANEL", orientation = "AUTO_FIT")
                }, enabled = !busy) { Text("Apply manga settings") }
                Text("Panel mode")
                ExportChoice(options.panelMode, listOf("OFF", "ROW_BY_ROW", "PANEL_BY_PANEL"), !busy) {
                    options = options.copy(panelMode = it)
                }
                Text("Orientation")
                ExportChoice(options.orientation, listOf("PORTRAIT", "LANDSCAPE", "AUTO_FIT"), !busy) {
                    options = options.copy(orientation = it)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(options.showFullPageBeforeSplits, { options = options.copy(showFullPageBeforeSplits = it) }, enabled = !busy)
                    Text("Full page before splits")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(options.includeCover, { options = options.copy(includeCover = it) }, enabled = !busy)
                    Text("Include cover")
                }
                if (missing > 0) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(allowDownload, { allowDownload = it }, enabled = !busy)
                    Text("Download selected missing chapters")
                }
                OutlinedButton(onClick = {
                    val picker = JFileChooser().apply {
                        selectedFile = File("manga.${format.extension}")
                        fileFilter = javax.swing.filechooser.FileNameExtensionFilter(format.name, format.extension)
                    }
                    if (picker.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                        val f = picker.selectedFile
                        if (f.extension.equals(format.extension, true) && !f.exists() && f.absoluteFile.parentFile.isDirectory) {
                            output = f.absoluteFile
                            result = null
                        } else {
                            output = null
                            result = "Choose a new .${format.extension} file in an existing folder."
                        }
                    }
                }, enabled = !busy) { Text("Choose output file") }
                output?.let { Text(it.absolutePath, style = MaterialTheme.typography.bodySmall) }
                if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Exporting… keep this window open.") }
                result?.let { Text(it) }
            }
        },
        confirmButton = {
            Button(enabled = !busy && valid && output != null, onClick = {
                val target = output ?: return@Button
                val args = arrayOf("--mode", "export", "--manga-id", card.id.toString(),
                    "--chapters", chapters.joinToString(",") { it.id.toString() }, "--format", format.name,
                    "--device", device.id, "--output", target.absolutePath, "--options-token", options.token(),
                    "--download-missing", allowDownload.toString())
                busy = true
                result = null
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            runComposeExport(args, target)
                        }
                        result = "Exported: ${target.name}"
                        output = null
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) {
                        result = "Export failed. Check the selected chapters, output and local server before retrying."
                    } finally { busy = false }
                }
            }) { Text("Export") }
        },
        dismissButton = { TextButton(onClick = close, enabled = !busy) { Text("Close") } })
}

@Composable
private fun ExportChoice(value: String, choices: List<String>, enabled: Boolean, select: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled) { Text(value) }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { item ->
                DropdownMenuItem(text = { Text(item) }, onClick = { expanded = false; select(item) })
            }
        }
    }
}

/** Shared production entry point; the QA fixture exercises this exact function. */
internal fun runComposeExport(args: Array<String>, target: File) {
    require(!target.exists()) { "Choose a new output file" }
    SuwayomiToNooir.main(args)
    check(target.isFile && target.length() > 0) { "No output created" }
}
