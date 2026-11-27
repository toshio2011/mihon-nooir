package dev.nooir.desktop.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.DialogProperties
import dev.nooir.bridge.GrayscalePng
import dev.nooir.bridge.XtcBookPreview
import javax.imageio.ImageIO
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ComposeExport(card: SuwayomiMangaCard, chapters: List<SuwayomiChapter>, close: () -> Unit) {
    var device by remember { mutableStateOf(NooirDeviceProfiles.builtins.first()) }
    var format by remember { mutableStateOf(ExportFormat.XTC) }
    var options by remember { mutableStateOf(MangaConversionOptions.preset("Manga")) }
    var allowDownload by remember { mutableStateOf(false) }
    var outputText by remember { mutableStateOf("") }
    val output = remember(outputText, format) {
        File(outputText).takeIf { outputText.isNotBlank() && it.isAbsolute && it.extension.equals(format.extension, true) }
    }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var finishedBook by remember { mutableStateOf<File?>(null) }
    var showPreview by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // Opt-in CI diagnostic only: no title, source IDs or user paths.
        if (System.getenv("NOOIR_COMPOSE_QA_CAPTURE") == "1") {
            println("QA_EXPORT_DIALOG chapters=${chapters.size}")
        }
    }
    LaunchedEffect(output != null) {
        if (output != null && System.getenv("NOOIR_COMPOSE_QA_CAPTURE") == "1") println("QA_OUTPUT_READY")
    }
    val operations = LocalOperations.current
    val scope = rememberCoroutineScope()
    val missing = chapters.count { !it.downloaded }
    val valid = chapters.isNotEmpty() && chapters.size <= 500 && (missing == 0 || allowDownload)
    BasicAlertDialog(onDismissRequest = { if (!busy) close() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth(0.85f)) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Export manga", style = MaterialTheme.typography.headlineSmall)
                if (showPreview && finishedBook != null) {
                    ConvertedBookPreview(finishedBook!!) { showPreview = false }
                } else {
            Column(Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(card.title, style = MaterialTheme.typography.titleMedium)
                Text("${chapters.size} chapters selected · $missing not downloaded")
                Text("Device", style = MaterialTheme.typography.titleSmall)
                ExportChoice(device.name, NooirDeviceProfiles.builtins.map { it.name }, !busy) { name ->
                    device = NooirDeviceProfiles.builtins.first { it.name == name }
                    if (!device.accepts(format)) format = device.autoFormat
                    outputText = ""
                }
                Text("Output format", style = MaterialTheme.typography.titleSmall)
                ExportChoice(format.name, ExportFormat.entries.filter { it != ExportFormat.AUTO && device.accepts(it) }.map { it.name }, !busy) {
                    format = ExportFormat.valueOf(it); outputText = ""
                }
                ComposeMangaSettings(options,format,!busy) { options = it }
                if (missing > 0) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(allowDownload, { allowDownload = it }, enabled = !busy)
                    Text("Download selected missing chapters")
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = outputText, onValueChange = { outputText = it },
                    modifier = Modifier.weight(1f), enabled = !busy, singleLine = true,
                    label = { Text("Output file (.${format.extension})") })
                OutlinedButton(onClick = {
                    val picker = JFileChooser().apply {
                        dialogTitle = "Choose export destination"
                        selectedFile = File("manga.${format.extension}")
                        fileFilter = javax.swing.filechooser.FileNameExtensionFilter(format.name, format.extension)
                    }
                    if (picker.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                        val f = picker.selectedFile
                        if (f.extension.equals(format.extension, true) && !f.exists() && f.absoluteFile.parentFile.isDirectory) {
                            outputText = f.absolutePath
                            result = null
                        } else {
                            outputText = ""
                            result = "Choose a new .${format.extension} file in an existing folder."
                        }
                    }
                }, enabled = !busy) { Text("Browse") }
            }
            Box(Modifier.fillMaxWidth().height(40.dp)) {
                if (busy) Column {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Exporting… keep this window open.", style = MaterialTheme.typography.bodySmall)
                } else Text(result ?: "Choose a new file in an existing folder.", style = MaterialTheme.typography.bodySmall, maxLines = 2)
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { showPreview = true }, enabled = !busy && finishedBook != null) { Text("Preview converted book") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = close, enabled = !busy) { Text("Close") }
            Button(enabled = !busy && valid && output != null, onClick = {
                val target = output ?: return@Button
                val args = arrayOf("--mode", "export", "--manga-id", card.id.toString(),
                    "--chapters", chapters.joinToString(",") { it.id.toString() }, "--format", format.name,
                    "--device", device.id, "--output", target.absolutePath, "--options-token", options.token(),
                    "--download-missing", allowDownload.toString())
                busy = true
                result = null
                finishedBook = null
                scope.launchProtected(operations) {
                    try {
                        withContext(Dispatchers.IO) {
                            runComposeExport(args, target)
                        }
                        result = "Exported: ${target.name}"
                        finishedBook = target.takeIf { it.extension.lowercase() in setOf("xtc", "xtch") }
                        outputText = ""
                        if (System.getenv("NOOIR_COMPOSE_QA_CAPTURE") == "1") println("QA_EXPORT_COMPLETE")
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) {
                        result = "Export failed. Check the selected chapters, output and local server before retrying."
                    } finally { busy = false }
                }
            }) { Text("Export") }
            }
                }
            }
        }
    }
}

private data class DecodedFrame(val count: Int, val image: ImageBitmap)

@Composable
internal fun ConvertedBookPreview(file: File, back: () -> Unit) {
    var page by remember(file) { mutableStateOf(0) }
    var frame by remember(file, page) { mutableStateOf<DecodedFrame?>(null) }
    var failed by remember(file, page) { mutableStateOf(false) }
    LaunchedEffect(file, page) {
        try {
            frame = withContext(Dispatchers.IO) {
                val book = XtcBookPreview(file)
                val png = GrayscalePng.encode(book.page(page))
                val decoded = png.inputStream().use { ImageIO.read(it) } ?: error("Cannot decode exported page")
                DecodedFrame(book.pageCount, decoded.toComposeImageBitmap())
            }
            if (System.getenv("NOOIR_COMPOSE_QA_CAPTURE") == "1") println("QA_CONVERTED_FRAME page=${page + 1} count=${frame?.count}")
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { failed = true }
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(file.name, style = MaterialTheme.typography.titleMedium)
        Text("Actual converted output", style = MaterialTheme.typography.labelMedium)
        Box(Modifier.fillMaxWidth().height(390.dp), contentAlignment = Alignment.Center) {
            val current = frame
            when {
                failed -> Text("Unable to decode this page. The exported file has not been modified.")
                current == null -> CircularProgressIndicator()
                else -> Image(current.image, contentDescription = "Converted page ${page + 1}",
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { page-- }, enabled = frame != null && page > 0) { Text("Previous") }
            Text("${page + 1} / ${frame?.count ?: "…"}")
            TextButton(onClick = { page++ }, enabled = frame?.let { page + 1 < it.count } == true) { Text("Next") }
        }
        var sendOpen by remember(file) { mutableStateOf(false) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = back) { Text("Back to export") }
            TextButton(onClick = { sendOpen = true }) { Text("Send to XTEink") }
        }
        if (sendOpen) ComposeTransfer(initialFile = file) { sendOpen = false }
    }
}


@Composable
internal fun ExportChoice(value: String, choices: List<String>, enabled: Boolean, select: (String) -> Unit) {
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
    require(target.isAbsolute && target.parentFile.isDirectory && !target.exists()) { "Choose a new output file in an existing folder" }
    SuwayomiToNooir.main(args)
    check(target.isFile && target.length() > 0) { "No output created" }
}
