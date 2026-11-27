package dev.nooir.desktop.compose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.nooir.bridge.ExportFormat
import dev.nooir.bridge.NooirDeviceProfiles
import dev.nooir.desktop.NooirDesktopCli
import dev.nooir.desktop.ui.DesktopPdfImport
import dev.nooir.desktop.ui.DesktopTextEpubImport
import dev.nooir.desktop.ui.DesktopImageEpubImport
import dev.nooir.mangabridge.MangaConversionOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

internal enum class LocalConversionMode(val label: String) {
    MANGA("Manga / image archive"), PDF("PDF original pages"),
    PDF_REFLOW("PDF reflowable text"), PDF_COMPANION("PDF pages + selectable text"),
    EPUB_TEXT("EPUB text + illustrations"), EPUB_IMAGES("EPUB image comic");
    fun accepts(format: ExportFormat): Boolean = when (this) {
        EPUB_TEXT -> format in setOf(ExportFormat.XTC, ExportFormat.XTCH)
        PDF_REFLOW, PDF_COMPANION -> format in setOf(ExportFormat.EPUB, ExportFormat.EPUB2)
        else -> format != ExportFormat.AUTO
    }
}

internal fun convertLocalFile(input: File, target: File, mode: LocalConversionMode,
                              format: ExportFormat, device: String, options: MangaConversionOptions,
                              typography: DesktopTextEpubImport.Settings) {
    val source = input.canonicalFile
    val out = target.canonicalFile
    require(input.isAbsolute && target.isAbsolute && source != out &&
        out.parentFile.isDirectory && !out.exists() && out.extension.equals(format.extension, true))
    require(mode.accepts(format) && NooirDeviceProfiles.forId(device).accepts(format))
    require(!source.isDirectory || !out.toPath().startsWith(source.toPath())) { "Choose output outside the source folder" }
    when (mode) {
        LocalConversionMode.MANGA -> NooirDesktopCli.main(arrayOf("--input", source.path, "--output", out.path,
            "--format", format.name, "--device", device, "--options-token", options.token()))
        LocalConversionMode.PDF -> DesktopPdfImport.export(source, out, format, device, options)
        LocalConversionMode.PDF_REFLOW -> DesktopPdfImport.exportReflow(source, out, format, device, options)
        LocalConversionMode.PDF_COMPANION -> DesktopPdfImport.export(source, out, format, device, options, textCompanion = true)
        LocalConversionMode.EPUB_TEXT -> DesktopTextEpubImport.export(source, out, format, device, typography, options)
        LocalConversionMode.EPUB_IMAGES -> DesktopImageEpubImport.export(source, out, format, device, options)
    }
    check(out.isFile && out.length() > 0)
}

private fun modesFor(file: File?): List<LocalConversionMode> = when {
    file == null || file.isDirectory -> listOf(LocalConversionMode.MANGA)
    file.extension.equals("pdf", true) -> listOf(LocalConversionMode.PDF, LocalConversionMode.PDF_REFLOW, LocalConversionMode.PDF_COMPANION)
    file.extension.equals("epub", true) -> listOf(LocalConversionMode.EPUB_TEXT, LocalConversionMode.EPUB_IMAGES)
    else -> listOf(LocalConversionMode.MANGA)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ComposeConverter(close: () -> Unit) {
    LaunchedEffect(Unit) {
        if (System.getenv("NOOIR_COMPOSE_QA_CAPTURE") == "1") println("QA_LOCAL_CONVERTER_OPEN")
    }
    val operations = LocalOperations.current
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf<File?>(null) }
    var mode by remember { mutableStateOf(LocalConversionMode.MANGA) }
    var device by remember { mutableStateOf(NooirDeviceProfiles.builtins.first()) }
    var format by remember { mutableStateOf(ExportFormat.XTC) }
    var options by remember { mutableStateOf(MangaConversionOptions.preset("Manga")) }
    var typography by remember { mutableStateOf(DesktopTextEpubImport.Settings()) }
    var outputText by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }
    var finished by remember { mutableStateOf<File?>(null) }
    var preview by remember { mutableStateOf(false) }
    val formats = ExportFormat.entries.filter { mode.accepts(it) && device.accepts(it) }
    fun adjustFormat() {
        val available = ExportFormat.entries.filter { mode.accepts(it) && device.accepts(it) }
        if (format !in available && available.isNotEmpty()) format = available.first()
        outputText = ""
    }
    BasicAlertDialog(onDismissRequest = { if (!busy) close() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(0.9f)) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Universal Converter", style = MaterialTheme.typography.headlineSmall)
                if (preview && finished != null) {
                    ConvertedBookPreview(finished!!) { preview = false }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(source?.name ?: "Choose a local file or image folder", Modifier.weight(1f))
                        OutlinedButton(onClick = {
                            val picker = JFileChooser().apply {
                                fileSelectionMode = JFileChooser.FILES_AND_DIRECTORIES
                                fileFilter = FileNameExtensionFilter("CBZ, ZIP, PDF or EPUB", "cbz","zip","pdf","epub")
                            }
                            if (picker.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                                val file = picker.selectedFile.absoluteFile
                                if (file.isDirectory || file.isFile && file.extension.lowercase() in setOf("cbz","zip","pdf","epub")) {
                                    source = file
                                    mode = modesFor(file).first()
                                    options = if (mode == LocalConversionMode.MANGA) MangaConversionOptions.preset("Manga")
                                        else MangaConversionOptions().copy(rightToLeft = false)
                                    adjustFormat()
                                    result = ""; finished = null
                                }
                            }
                        }, enabled = !busy) { Text("Choose input") }
                    }
                    Column(Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Input mode")
                        ExportChoice(mode.label, modesFor(source).map { it.label }, !busy) { label ->
                            mode = modesFor(source).first { it.label == label }
                            options = if (mode in setOf(LocalConversionMode.MANGA,LocalConversionMode.EPUB_IMAGES))
                                MangaConversionOptions.preset("Manga") else MangaConversionOptions().copy(rightToLeft = false)
                            adjustFormat()
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("Reader device")
                                ExportChoice(device.name, NooirDeviceProfiles.builtins.map { it.name }, !busy) { name ->
                                    device = NooirDeviceProfiles.builtins.first { it.name == name }; adjustFormat()
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                Text("Output format")
                                ExportChoice(format.name, formats.map { it.name }, !busy && formats.isNotEmpty()) {
                                    format = ExportFormat.valueOf(it); outputText = ""
                                }
                            }
                        }
                        when(mode) {
                            LocalConversionMode.MANGA, LocalConversionMode.EPUB_IMAGES, LocalConversionMode.PDF ->
                                { MangaPreferenceActions(options,!busy) { options = it }; ComposeMangaSettings(options,format,!busy) { options = it }; ComposeLiveSample(device.id,format,options,!busy) }
                            LocalConversionMode.EPUB_TEXT -> {
                                ComposeTypographySettings(typography,!busy) { typography = it }
                                ComposeRenderingSettings(options,!busy) { options = it }
                            }
                            LocalConversionMode.PDF_COMPANION -> {
                                ComposeRenderingSettings(options,!busy) { options = it }
                                ConversionToggle("Include cover",options.includeCover,!busy){options=options.copy(includeCover=it)}
                            }
                            LocalConversionMode.PDF_REFLOW -> Text("Reflowable PDF text retains existing extraction defaults.",style=MaterialTheme.typography.bodySmall)
                        }
                        Text("Protected documents are not supported. Text reflow is not a full CSS layout reproduction.", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(outputText, { outputText = it }, singleLine = true, enabled = !busy,
                            label = { Text("New output file (.${format.extension})") }, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = {
                            val picker = JFileChooser().apply {
                                selectedFile = File((source?.nameWithoutExtension ?: "book") + "." + format.extension)
                            }
                            if (picker.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) outputText = picker.selectedFile.absolutePath
                        }, enabled = !busy) { Text("Browse") }
                    }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (result.isNotBlank()) Text(result, style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { preview = true }, enabled = !busy && finished != null) { Text("Preview converted book") }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = close, enabled = !busy) { Text("Close") }
                        Button(enabled = !busy && source != null && File(outputText).isAbsolute &&
                            File(outputText).extension.equals(format.extension,true) && format in formats, onClick = {
                            val input = source ?: return@Button
                            val target = File(outputText)
                            val selectedMode = mode; val selectedFormat = format; val selectedDevice = device.id
                            val selectedOptions = options; val selectedTypography = typography
                            busy = true; finished = null; result = "Converting…"
                            scope.launchProtected(operations) {
                                try {
                                    withContext(Dispatchers.IO) {
                                        convertLocalFile(input,target,selectedMode,selectedFormat,selectedDevice,selectedOptions,selectedTypography)
                                    }
                                    finished = target.takeIf { selectedFormat in setOf(ExportFormat.XTC,ExportFormat.XTCH) }
                                    result = "Converted: ${target.name}"; outputText = ""
                                } catch (e: CancellationException) { throw e }
                                catch (e: Exception) { result = "Conversion failed. Check input compatibility and choose a new output path." }
                                finally { busy = false }
                            }
                        }) { Text("Convert") }
                    }
                }
            }
        }
    }
}
