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
import dev.nooir.desktop.ui.PdfBookDetails
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
                              typography: DesktopTextEpubImport.Settings, pdfDetails:PdfBookDetails=PdfBookDetails()) {
    val source = input.canonicalFile
    val out = target.canonicalFile
    require(input.isAbsolute && target.isAbsolute && source != out &&
        out.parentFile.isDirectory && !out.exists() && out.extension.equals(format.extension, true))
    require(mode.accepts(format) && NooirDeviceProfiles.forId(device).accepts(format))
    require(!source.isDirectory || !out.toPath().startsWith(source.toPath())) { "Choose output outside the source folder" }
    when (mode) {
        LocalConversionMode.MANGA -> NooirDesktopCli.main(arrayOf("--input", source.path, "--output", out.path,
            "--format", format.name, "--device", device, "--options-token", options.token()))
        LocalConversionMode.PDF -> DesktopPdfImport.exportWithDetails(source, out, format, device, options,pdfDetails)
        LocalConversionMode.PDF_REFLOW -> DesktopPdfImport.exportReflowWithDetails(source, out, format, device, options,pdfDetails)
        LocalConversionMode.PDF_COMPANION -> DesktopPdfImport.exportWithDetails(source, out, format, device, options,pdfDetails,textCompanion=true)
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
internal fun ComposeConverter(initialInput:UniversalInput=UniversalInput.COMIC,close: () -> Unit) {
    LaunchedEffect(Unit) {
        if (System.getenv("NOOIR_COMPOSE_QA_CAPTURE") == "1") println("QA_LOCAL_CONVERTER_OPEN")
    }
    val operations = LocalOperations.current
    val sessionConversions = LocalSessionConversions.current
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf<File?>(null) }
    var inputKind by remember { mutableStateOf(initialInput) }
    var mode by remember { mutableStateOf(initialInput.mode()) }
    var device by remember { mutableStateOf(NooirDeviceProfiles.builtins.first()) }
    var format by remember { mutableStateOf(ExportFormat.XTC) }
    val defaults=rememberMangaDefaults() ?: return
    var mangaDefaults by remember { mutableStateOf(defaults.options) }
    var options by remember { mutableStateOf(if(initialInput.mode()==LocalConversionMode.MANGA) mangaDefaults else androidMangaDefaults().copy(rightToLeft=false)) }
    var typography by remember { mutableStateOf(androidTypographyDefaults()) }
    var pdfTitle by remember(source){mutableStateOf("")}
    var pdfAuthor by remember(source){mutableStateOf("")}
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
    LaunchedEffect(Unit){ adjustFormat() }
    BasicAlertDialog(onDismissRequest = { if (!busy) close() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(0.9f)) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Universal Converter", style = MaterialTheme.typography.headlineSmall)
                if (preview && finished != null) {
                    ConvertedBookPreview(finished!!) { preview = false }
                } else {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        Text("Input type")
                        ExportChoice(inputKind.label,UniversalInput.entries.map{it.label},!busy){label->
                            inputKind=UniversalInput.entries.first{it.label==label}
                            source=null;mode=inputKind.mode()
                            options=if(mode==LocalConversionMode.MANGA)mangaDefaults else androidMangaDefaults().copy(rightToLeft=false)
                            finished=null;result="";adjustFormat()
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(source?.name ?: "Choose a local file or image folder", Modifier.weight(1f))
                        OutlinedButton(onClick = {
                            val picker = JFileChooser().apply {
                                fileSelectionMode = if(inputKind.folders)JFileChooser.FILES_AND_DIRECTORIES else JFileChooser.FILES_ONLY
                                fileFilter = FileNameExtensionFilter(inputKind.label,*inputKind.extensions.toTypedArray())
                                isAcceptAllFileFilterUsed=false
                            }
                            if (picker.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                                val file = picker.selectedFile.absoluteFile
                                if (inputKind.accepts(file)) {
                                    source = file
                                    mode = modesFor(file).first()
                                    options = if (mode in setOf(LocalConversionMode.MANGA,LocalConversionMode.EPUB_IMAGES)) mangaDefaults
                                        else androidMangaDefaults().copy(rightToLeft=false)
                                    adjustFormat()
                                    result = ""; finished = null
                                } else { result="Choose a supported "+inputKind.label+" input." }
                            }
                        }, enabled = !busy) { Text("Choose input") }
                    }
                    Column(Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Input mode")
                        val modes=when(inputKind){UniversalInput.PDF->listOf(LocalConversionMode.PDF,LocalConversionMode.PDF_REFLOW,LocalConversionMode.PDF_COMPANION);UniversalInput.EPUB->listOf(LocalConversionMode.EPUB_TEXT,LocalConversionMode.EPUB_IMAGES);else->listOf(LocalConversionMode.MANGA)}
                        ExportChoice(mode.label, modes.map { it.label }, !busy) { label ->
                            mode = modes.first { it.label == label }
                            options = if (mode in setOf(LocalConversionMode.MANGA,LocalConversionMode.EPUB_IMAGES))
                                mangaDefaults else androidMangaDefaults().copy(rightToLeft=false)
                            adjustFormat()
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("Reader device")
                                ComposeDeviceChoice(device,!busy) { chosen ->
                                    device = chosen; adjustFormat()
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                Text("Output format")
                                ExportChoice(format.name, formats.map { it.name }, !busy && formats.isNotEmpty()) {
                                    format = ExportFormat.valueOf(it); outputText = ""
                                }
                            }
                        }
                        if(inputKind==UniversalInput.PDF){
                            Text("Book metadata (optional)",style=MaterialTheme.typography.titleMedium)
                            OutlinedTextField(pdfTitle,{pdfTitle=it.filterNot{c->Character.isISOControl(c)}.take(120)},label={Text("Book title")},singleLine=true,enabled=!busy)
                            OutlinedTextField(pdfAuthor,{pdfAuthor=it.filterNot{c->Character.isISOControl(c)}.take(120)},label={Text("Author")},singleLine=true,enabled=!busy)
                            Text("Blank uses PDF title/author for EPUB when available; otherwise the filename supplies the title.",style=MaterialTheme.typography.bodySmall)
                        }
                        when(mode) {
                            LocalConversionMode.MANGA, LocalConversionMode.EPUB_IMAGES ->
                                { MangaPreferenceActions(options,!busy,defaults.warning,{mangaDefaults=it}) { options = it }; ComposeMangaSettings(options,format,!busy) { options = it }; ComposeLiveSample(device.id,format,options,!busy) }
                            LocalConversionMode.EPUB_TEXT -> {
                                ComposeTypographySettings(typography,!busy) { typography = it }
                                ComposeRenderingSettings(options,!busy) { options = it }
                            }
                            LocalConversionMode.PDF -> ComposePdfSettings(options,format,!busy){options=it}
                            LocalConversionMode.PDF_COMPANION -> Text("Original page images plus selectable text. Rendering uses the source colors and device dimensions.",style=MaterialTheme.typography.bodySmall)
                            LocalConversionMode.PDF_REFLOW -> Text("Reflow text when safe; preserve complex pages as images with extracted text.",style=MaterialTheme.typography.bodySmall)
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
                        TextButton(enabled=!busy && source!=null && File(outputText).isAbsolute &&
                            File(outputText).extension.equals(format.extension,true) && format in formats,onClick={
                            val input=source ?: return@TextButton
                            val target=File(outputText)
                            val selectedMode=mode;val selectedFormat=format;val selectedDevice=device.id
                            val selectedOptions=options;val selectedTypography=typography;val selectedDetails=PdfBookDetails(pdfTitle,pdfAuthor)
                            try{
                                sessionConversions.submit(target,input.name){
                                    convertLocalFile(input,target,selectedMode,selectedFormat,selectedDevice,selectedOptions,selectedTypography,selectedDetails)
                                }
                                close()
                            }catch(e:Exception){result=e.message ?: "Unable to queue conversion"}
                        }){Text("Queue & close")}
                        TextButton(onClick = close, enabled = !busy) { Text("Close") }
                        Button(enabled = !busy && !sessionConversions.active && source != null && File(outputText).isAbsolute &&
                            File(outputText).extension.equals(format.extension,true) && format in formats, onClick = {
                            val input = source ?: return@Button
                            val target = File(outputText)
                            val selectedMode = mode; val selectedFormat = format; val selectedDevice = device.id
                            val selectedOptions = options; val selectedTypography = typography;val selectedDetails=PdfBookDetails(pdfTitle,pdfAuthor)
                            busy = true; finished = null; result = "Converting…"
                            scope.launchProtected(operations) {
                                try {
                                    withContext(Dispatchers.IO) {
                                        convertLocalFile(input,target,selectedMode,selectedFormat,selectedDevice,selectedOptions,selectedTypography,selectedDetails)
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
