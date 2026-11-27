package dev.nooir.desktop.compose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.nooir.bridge.ExportFormat
import dev.nooir.bridge.NooirDeviceProfiles
import dev.nooir.desktop.ui.ConversionJobState
import dev.nooir.desktop.ui.DesktopConversionJob
import dev.nooir.desktop.ui.DesktopConversionQueue
import dev.nooir.desktop.ui.DesktopTextEpubImport
import dev.nooir.mangabridge.MangaConversionOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

private enum class BatchKind(val label: String, val extensions: List<String>, val folders: Boolean) {
    MANGA("Manga / image files and folders", listOf("cbz","zip","png","jpg","jpeg","webp","bmp","gif"),true),
    PDF("PDF original pages", listOf("pdf"),false),
    REFLOW("PDF reflow", listOf("pdf"),false),
    EPUB_TEXT("Text EPUB", listOf("epub"),false),
    EPUB_IMAGES("Image EPUB", listOf("epub"),false);
    fun accepts(format: ExportFormat) = when (this) {
        REFLOW -> format in setOf(ExportFormat.EPUB,ExportFormat.EPUB2)
        EPUB_TEXT -> format in setOf(ExportFormat.XTC,ExportFormat.XTCH)
        else -> format != ExportFormat.AUTO
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ComposeQueue(close: () -> Unit) {
    val queue = remember { DesktopConversionQueue() }
    val operations = LocalOperations.current
    val scope = rememberCoroutineScope()
    val controller = LocalSessionConversions.current
    var jobs by remember { mutableStateOf<List<DesktopConversionJob>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    val running = controller.batchRunning
    val pausing = controller.batchPausing
    var status by remember { mutableStateOf("Loading local conversion jobs…") }
    var addOpen by remember { mutableStateOf(false) }
    var clearConfirm by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<File?>(null) }
    suspend fun reload() {
        jobs = withContext(Dispatchers.IO) { queue.list() }
    }
    fun action(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launchProtected(operations) {
            try { block(); reload() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { status = "Queue operation failed. Existing records and output files were not deliberately removed. Reload before retrying." }
            finally { busy = false }
        }
    }
    LaunchedEffect(Unit) {
        try {
            reload()
            status = "Jobs are stored locally. Start conversions explicitly."
            if (System.getenv("NOOIR_COMPOSE_QA_CAPTURE") == "1") println("QA_ACTIVITY_QUEUE_OPEN")
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { status = "Could not read the queue. Original records are retained." }
    }
    LaunchedEffect(running) {
        do {
            try { reload() } catch (e: CancellationException) { throw e }
            catch (_: Exception) { status="Unable to refresh queue. Existing records retained." }
            if(running) kotlinx.coroutines.delay(1000)
        } while(running)
    }

    BasicAlertDialog(onDismissRequest = { if (!busy) close() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.widthIn(max = 850.dp).fillMaxWidth(0.93f)) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Activity Queue", style = MaterialTheme.typography.headlineSmall)
                val book = preview
                if (book != null) {
                    ConvertedBookPreview(book) { preview = null }
                } else {
                    Text("Local conversions", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { addOpen = true }, enabled = !busy) { Text("Add books") }
                        OutlinedButton(onClick = {
                            try{controller.startBatch(queue)}
                            catch(e:Exception){status=e.message ?: "Could not start batch"}
                        }, enabled = !busy && !controller.active && jobs.any { it.state in setOf(ConversionJobState.PENDING,ConversionJobState.RUNNING) }) { Text("Run / recover") }
                        OutlinedButton(onClick = { controller.pauseBatch() },
                            enabled = running && !pausing) { Text("Pause after current") }
                    }
                    Text("${jobs.count { it.state == ConversionJobState.PENDING }} pending · ${jobs.count { it.state == ConversionJobState.COMPLETED }} completed · ${jobs.count { it.state in setOf(ConversionJobState.FAILED,ConversionJobState.INTERRUPTED) }} need review")
                    if (running) LinearProgressIndicator(Modifier.fillMaxWidth())
                    LazyColumn(Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 350.dp)) {
                        if (jobs.isEmpty()) item { Text("No local jobs yet. Add books to begin.", modifier = Modifier.padding(16.dp)) }
                        items(jobs, key = { it.id }) { job ->
                            ListItem(headlineContent = { Text(File(job.input).name) }, supportingContent = {
                                Column {
                                    Text("${job.format.name} · ${job.device} · ${job.state.name.lowercase().replaceFirstChar { it.uppercase() }}")
                                    Text(File(job.output).name)
                                    if (job.error.isNotBlank()) Text(job.error.take(200), color = MaterialTheme.colorScheme.error)
                                }
                            }, trailingContent = {
                                Row {
                                    if (job.state in setOf(ConversionJobState.FAILED,ConversionJobState.INTERRUPTED)) {
                                        TextButton(onClick = { action { withContext(Dispatchers.IO) { queue.retry(job.id) }; status = "Job queued again." } }, enabled = !busy) { Text("Retry") }
                                    }
                                    if (job.state in setOf(ConversionJobState.PENDING,ConversionJobState.FAILED,ConversionJobState.INTERRUPTED)) {
                                        TextButton(onClick = { action { withContext(Dispatchers.IO) { queue.cancel(job.id) }; status = "Job canceled." } }, enabled = !busy) { Text("Cancel") }
                                    }
                                    if (job.state == ConversionJobState.COMPLETED && job.format in setOf(ExportFormat.XTC,ExportFormat.XTCH)) {
                                        TextButton(onClick = { preview = File(job.output) }, enabled = !busy) { Text("Preview") }
                                    }
                                }
                            })
                            HorizontalDivider()
                        }
                    }
                    if(controller.batchStatus.isNotBlank()) Text(controller.batchStatus,style=MaterialTheme.typography.bodySmall)
                    Text(status, style = MaterialTheme.typography.bodySmall)
                    Text("Close this queue to keep reading or browsing while conversions run.",style=MaterialTheme.typography.bodySmall)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { action { status = "Queue refreshed." } }, enabled = !busy) { Text("Reload") }
                        TextButton(onClick = { clearConfirm = true }, enabled = !busy) { Text("Clear finished") }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = close, enabled = !busy) { Text("Close") }
                    }
                }
            }
        }
    }
    if (addOpen) ComposeBatchOptions(close = { addOpen = false }) { files, folder, kind, format, device, options, typography, names ->
        addOpen = false
        action {
            val count = withContext(Dispatchers.IO) {
                queue.enqueue(files,folder,format,device,options,
                    textEpub = if (kind == BatchKind.EPUB_TEXT) typography else null,
                    pdfReflow = kind == BatchKind.REFLOW,outputNames=names)
            }
            status = "Added $count local jobs. Press Run to convert."
        }
    }
    if (clearConfirm) AlertDialog(onDismissRequest = { clearConfirm = false },
        title = { Text("Clear finished records?") },
        text = { Text("Completed and canceled queue records will be forgotten. Output files are kept.") },
        confirmButton = { Button(onClick = {
            clearConfirm = false
            action { val count = withContext(Dispatchers.IO) { queue.forgetFinished() }; status = "Cleared $count records." }
        }) { Text("Clear records") } },
        dismissButton = { TextButton(onClick = { clearConfirm = false }) { Text("Cancel") } })
}

@Composable
private fun ComposeBatchOptions(close: () -> Unit,
    enqueue: (List<File>,File,BatchKind,ExportFormat,String,MangaConversionOptions,DesktopTextEpubImport.Settings,List<String>) -> Unit) {
    var kind by remember { mutableStateOf(BatchKind.MANGA) }
    var files by remember { mutableStateOf<List<File>>(emptyList()) }
    var folder by remember { mutableStateOf<File?>(null) }
    var device by remember { mutableStateOf(NooirDeviceProfiles.builtins.first()) }
    var format by remember { mutableStateOf(ExportFormat.XTC) }
    val defaults=rememberMangaDefaults() ?: return
    var mangaDefaults by remember { mutableStateOf(defaults.options) }
    var options by remember { mutableStateOf(mangaDefaults) }
    var typography by remember { mutableStateOf(androidTypographyDefaults()) }
    var names by remember{mutableStateOf<List<String>>(emptyList())}
    var rename by remember{mutableStateOf(false)}
    var selectionError by remember { mutableStateOf("") }
    val formats = ExportFormat.entries.filter { kind.accepts(it) && device.accepts(it) }
    fun adjust() { if (format !in formats && formats.isNotEmpty()) format = formats.first() }
    LaunchedEffect(kind,device) { adjust() }
    AlertDialog(onDismissRequest = close, title = { Text("Add local conversion jobs") },
        text = { Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Input type")
            ExportChoice(kind.label,BatchKind.entries.map { it.label },true) { label ->
                kind = BatchKind.entries.first { it.label == label }; files = emptyList(); names=emptyList(); selectionError = ""
                options = if(kind in setOf(BatchKind.MANGA,BatchKind.EPUB_IMAGES)) mangaDefaults else androidMangaDefaults().copy(rightToLeft=false)
            }
            OutlinedButton(onClick = {
                val picker = JFileChooser().apply {
                    fileSelectionMode = if (kind.folders) JFileChooser.FILES_AND_DIRECTORIES else JFileChooser.FILES_ONLY
                    fileFilter = FileNameExtensionFilter(kind.label,*kind.extensions.toTypedArray())
                    isAcceptAllFileFilterUsed = false
                    isMultiSelectionEnabled = true
                }
                if (picker.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    val picked = picker.selectedFiles.toList().ifEmpty { listOfNotNull(picker.selectedFile) }
                    val accepted = picked.filter { kind.folders && it.isDirectory || it.isFile && it.extension.lowercase() in kind.extensions }
                    if(accepted.size!=picked.size){files=emptyList();names=emptyList();selectionError="Some selected files are unsupported. Choose again."} else {files=accepted;names=accepted.map{""};selectionError=""}
                }
            }) { Text("Choose books (${files.size})") }
            if(files.isNotEmpty()){
                TextButton(onClick={rename=!rename}){Text(if(rename)"Hide output names" else "Rename output books")}
                if(rename) files.forEachIndexed{index,file->
                    OutlinedTextField(names.getOrElse(index){""},{v->names=names.mapIndexed{i,n->if(i==index)v.take(80) else n}},
                        label={Text(file.name+" · output name")},placeholder={Text(file.nameWithoutExtension)},singleLine=true,modifier=Modifier.fillMaxWidth())
                }
                Text("Leave blank for the original filename. Enter names without the extension.",style=MaterialTheme.typography.bodySmall)
            }
            Text("Device")
            ComposeDeviceChoice(device,true) { chosen -> device = chosen }
            Text("Format")
            ExportChoice(format.name,formats.map { it.name },formats.isNotEmpty()) { format = ExportFormat.valueOf(it) }
            OutlinedButton(onClick = {
                val picker = JFileChooser().apply { fileSelectionMode = JFileChooser.DIRECTORIES_ONLY }
                if (picker.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) folder = picker.selectedFile
            }) { Text(folder?.name ?: "Choose output folder") }
            if(selectionError.isNotBlank()) Text(selectionError,color=MaterialTheme.colorScheme.error)
            when(kind) {
                BatchKind.MANGA,BatchKind.EPUB_IMAGES -> { MangaPreferenceActions(options,true,defaults.warning,{mangaDefaults=it}){options=it}; ComposeMangaSettings(options,format,true){options=it}; ComposeLiveSample(device.id,format,options,formats.contains(format),localInput=files.firstOrNull().takeIf{kind==BatchKind.MANGA}) }
                BatchKind.PDF -> ComposePdfSettings(options,format,true){options=it}
                BatchKind.EPUB_TEXT -> {ComposeTypographySettings(typography,true){typography=it};ComposeRenderingSettings(options,true){options=it}}
                BatchKind.REFLOW -> Text("PDF reflow uses existing extraction defaults.")
            }
            Text("These settings apply to every selected book. Existing files are never overwritten.",style=MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { Button(enabled = files.isNotEmpty() && folder != null && format in formats, onClick = {
            val target = folder ?: return@Button
            enqueue(files,target,kind,format,device.id,options,typography,names)
        }) { Text("Add to queue") } },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } })
}
