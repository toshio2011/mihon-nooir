package dev.nooir.mangabridge

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dev.nooir.bridge.ExportFormat
import dev.nooir.bridge.NooirDeviceProfiles
import java.util.UUID

/** PDF → genuinely reflowable text + positioned illustrations, where possible. */
@Composable
fun NooirPdfToEpubDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("manganooir_export", Context.MODE_PRIVATE) }
    val workManager = remember { WorkManager.getInstance(context) }
    var document by remember { mutableStateOf<Uri?>(null) }
    var documents by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var selectedNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectionError by remember { mutableStateOf("") }
    var queuedSummary by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf("No PDF selected") }
    var outputName by remember { mutableStateOf("Converted-PDF") }
    var bookTitle by remember { mutableStateOf("") }
    var bookAuthor by remember { mutableStateOf("") }
    var host by remember { mutableStateOf(prefs.getString("host", "crosspoint.local") ?: "crosspoint.local") }
    var folder by remember { mutableStateOf(prefs.getString("destination", "/Books") ?: "/Books") }
    var mode by remember { mutableStateOf("MIXED") }
    var outputFormat by remember { mutableStateOf("EPUB3") }
    var device by remember { mutableStateOf(prefs.getString("device", "X4") ?: "X4") }
    var workId by remember {
        mutableStateOf(prefs.getString(NooirPdfExportWorker.LAST_WORK, null)?.let {
            runCatching { UUID.fromString(it) }.getOrNull()
        })
    }
    var state by remember { mutableStateOf<WorkInfo?>(null) }
    var batchIds by remember {
        mutableStateOf(prefs.getString("last_pdf_batch_work_ids", "").orEmpty()
            .split(',').mapNotNull { raw -> runCatching { UUID.fromString(raw) }.getOrNull() }.toSet())
    }
    var batchInfos by remember { mutableStateOf<List<WorkInfo>>(emptyList()) }
    LaunchedEffect(workId) {
        workId?.let { key -> workManager.getWorkInfoByIdFlow(key).collect { state = it } }
    }
    LaunchedEffect(batchIds) {
        if (batchIds.isEmpty()) {
            batchInfos = emptyList()
        } else {
            workManager.getWorkInfosForUniqueWorkFlow(NooirDeviceWorkQueue.NAME).collect { jobs ->
                batchInfos = jobs.filter { it.id in batchIds }
            }
        }
    }

    fun enqueue(saveUri: Uri?, saveTree: Uri? = null) {
        val inputs = documents.ifEmpty { listOfNotNull(document) }
        if (inputs.isEmpty()) return
        try {
            require(saveUri == null || (saveTree == null && inputs.size == 1)) {
                "Multiple PDFs require one destination folder, not one shared output file"
            }
            val stems = if (inputs.size == 1) listOf(outputName.trim())
                else PdfBatchPlan.outputStems(selectedNames)
            require(stems.size == inputs.size && stems.all { it.isNotBlank() })
            prefs.edit().putString("host", host.trim()).putString("destination", folder.trim()).apply()
            val requests = inputs.mapIndexed { index, uri ->
                OneTimeWorkRequestBuilder<NooirPdfExportWorker>()
                    .setInputData(workDataOf(
                        NooirPdfExportWorker.PDF to uri.toString(),
                        NooirPdfExportWorker.SAVE_URI to saveUri?.toString(),
                        NooirPdfExportWorker.SAVE_TREE to saveTree?.toString(),
                        NooirPdfExportWorker.HOST to host.trim(),
                        NooirPdfExportWorker.FOLDER to folder.trim(),
                        NooirPdfExportWorker.MODE to mode,
                        NooirPdfExportWorker.EPUB_FORMAT to outputFormat,
                        NooirPdfExportWorker.OUTPUT_FORMAT to outputFormat,
                        NooirPdfExportWorker.DEVICE to device,
                        NooirPdfExportWorker.OUTPUT_NAME to stems[index],
                        NooirPdfExportWorker.BOOK_TITLE to (if (inputs.size == 1) bookTitle.trim() else ""),
                        NooirPdfExportWorker.BOOK_AUTHOR to (if (inputs.size == 1) bookAuthor.trim() else ""),
                    )).build()
            }
            // Create one persisted serial WorkManager continuation, never
            // simultaneous PDFBox loads or competing XTEink uploads.
            var continuation = workManager.beginUniqueWork(
                NooirDeviceWorkQueue.NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, requests.first())
            for (next in requests.drop(1)) continuation = continuation.then(next)
            continuation.enqueue()
            workId = requests.last().id
            batchInfos = emptyList()
            batchIds = requests.map { it.id }.toSet()
            prefs.edit()
                .putString(NooirPdfExportWorker.LAST_WORK, workId.toString())
                .putString("last_pdf_batch_work_ids", requests.joinToString(",") { it.id.toString() })
                .apply()
            queuedSummary = "Queued " + requests.size + " PDF(s) in order. Each produces its own " +
                (if (outputFormat == "EPUB2" || outputFormat == "EPUB3") "EPUB" else outputFormat) +
                " book; see Nooir Tools → Activity Queue for individual progress."
            selectionError = ""
        } catch (e: Exception) {
            selectionError = e.message ?: "Unable to queue selected PDFs"
        }
    }

    val choosePdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { files ->
        if (files.isNotEmpty()) {
            try {
                require(files.size <= PdfBatchPlan.MAX_SELECTION) {
                    "Select up to " + PdfBatchPlan.MAX_SELECTION + " PDFs per batch; queue more batches afterward"
                }
                val names = files.map { uri ->
                    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME),
                        null, null, null)?.use {
                        if (it.moveToFirst()) it.getString(0) else null
                    } ?: (uri.lastPathSegment ?: "PDF")
                }
                PdfBatchPlan.outputStems(names)
                files.forEach { uri ->
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (_: SecurityException) {
                        // A provider may offer temporary access only; workers
                        // report if that access expires before processing.
                    }
                }
                documents = files
                selectedNames = names
                document = files.first()
                fileName = if (files.size == 1) names.first()
                    else files.size.toString() + " PDFs selected: " + names.take(3).joinToString(", ") +
                        (if (files.size > 3) "…" else "")
                outputName = names.first().substringBeforeLast('.', names.first())
                bookTitle = if (files.size == 1) outputName else ""
                bookAuthor = ""
                selectionError = ""
                queuedSummary = ""
            } catch (e: Exception) {
                selectionError = e.message ?: "Cannot inspect selected PDF files"
            }
        }
    }
    val saveEpub = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/epub+zip")
    ) { uri ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            ) } catch (_: SecurityException) { }
            enqueue(uri)
        }
    }
    val saveFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                enqueue(null, saveTree = tree)
            } catch (e: SecurityException) {
                selectionError = "Cannot keep access to selected output folder: " + e.message
            }
        }
    }
    val binaryFormat = outputFormat == "XTC" || outputFormat == "XTCH"
    val extension = if (binaryFormat) outputFormat.lowercase() else "epub"
    val selectedExport = when (outputFormat) {
        "XTC" -> ExportFormat.XTC
        "XTCH" -> ExportFormat.XTCH
        "EPUB2" -> ExportFormat.EPUB2
        else -> ExportFormat.EPUB
    }
    val profile = NooirDeviceProfiles.forId(device)
    val validFormat = profile.accepts(selectedExport) &&
        (outputFormat != "XTCH" || profile.height % 8 == 0)
    val saveBinary = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            ) } catch (_: SecurityException) { }
            enqueue(uri)
        }
    }
    val tracked = batchInfos
    val activeStates = setOf(WorkInfo.State.BLOCKED, WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING)
    val busy = tracked.any { it.state in activeStates } ||
        (tracked.isEmpty() && state?.state in activeStates)
    val displayState = tracked.firstOrNull { it.state == WorkInfo.State.RUNNING }
        ?: tracked.firstOrNull { it.state == WorkInfo.State.ENQUEUED }
        ?: tracked.firstOrNull { it.state == WorkInfo.State.BLOCKED }
        ?: tracked.firstOrNull { NooirDeviceWorkQueue.hasFailed(it) }
        ?: tracked.lastOrNull()
        ?: state
    val batchComplete = tracked.count { it.state == WorkInfo.State.SUCCEEDED &&
        !NooirDeviceWorkQueue.hasFailed(it) }
    val batchFailed = tracked.count { NooirDeviceWorkQueue.hasFailed(it) ||
        it.state == WorkInfo.State.CANCELLED }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("PDF to e-reader") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 540.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Choose one or more PDFs. Each becomes a separate EPUB 2/3 or XTC/XTCH book, processed in order. Large PDFs are supported while storage permits.")
                Button(onClick = { choosePdf.launch(arrayOf("application/pdf")) }) {
                    Text("Choose PDF file(s) from phone")
                }
                Text(fileName)
                if (documents.size > 1) {
                    Text("Batch: each PDF keeps its own metadata and gets a distinct filename. Select an output folder to save locally; you can queue more batches.")
                } else {
                    OutlinedTextField(value = bookTitle, onValueChange = { bookTitle = it.take(192) },
                        label = { Text("Book title (optional)") }, singleLine = true,
                        supportingText = { Text("Visible book title; leave blank to use PDF metadata.") })
                    OutlinedTextField(value = bookAuthor, onValueChange = { bookAuthor = it.take(128) },
                        label = { Text("Author or pen name (optional)") }, singleLine = true,
                        supportingText = { Text("Overrides the PDF author metadata if supplied.") })
                    OutlinedTextField(value = outputName, onValueChange = { outputName = it.take(70) },
                        label = { Text("Output filename (without extension)") }, singleLine = true,
                        supportingText = { Text("An existing output filename is never overwritten.") })
                }
                if (selectionError.isNotBlank()) Text("Selection error: " + selectionError)
                if (queuedSummary.isNotBlank()) Text(queuedSummary)
                Text("Output format")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = outputFormat == "EPUB2",
                        onClick = { outputFormat = "EPUB2" },
                        label = { Text("EPUB 2") })
                    FilterChip(selected = outputFormat == "EPUB3",
                        onClick = { outputFormat = "EPUB3" },
                        label = { Text("EPUB 3") })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = outputFormat == "XTC",
                        onClick = { outputFormat = "XTC" },
                        label = { Text("XTC · 1-bit") })
                    FilterChip(selected = outputFormat == "XTCH",
                        onClick = { outputFormat = "XTCH" },
                        label = { Text("XTCH · 2-bit") })
                }
                if (binaryFormat) {
                    NooirDevicePicker(device) { device = it }
                    Text("XTC/XTCH render each PDF page to the selected device size. Text becomes pixels; no OCR is required. EPUB remains available for selectable text.")
                    if (!validFormat) Text("This device profile does not support the selected format.")
                } else {
                    Text("EPUB keeps extractable text selectable and embeds illustrations.")
                    Text("How should pages be converted?")
                    FilterChip(selected = mode == "MIXED", onClick = { mode = "MIXED" },
                        label = { Text("Reflowable text + illustrations") })
                    FilterChip(selected = mode == "ORIGINAL_PAGES",
                        onClick = { mode = "ORIGINAL_PAGES" },
                        label = { Text("Keep original pages as images") })
                    Text(if (mode == "MIXED")
                        "Uses the PDF's text layer. Scans and complex diagram pages become images without OCR."
                        else "Preserves every page visually, but text won't resize.")
                }
                OutlinedTextField(value = host, onValueChange = { host = it },
                    label = { Text("XTEink IP or hostname") }, singleLine = true)
                OutlinedTextField(value = folder, onValueChange = { folder = it },
                    label = { Text("XTEink destination folder") }, singleLine = true)
                Button(enabled = document != null && !busy &&
                    (documents.size > 1 || outputName.isNotBlank()) && validFormat, onClick = {
                    if (documents.size > 1) saveFolder.launch(null)
                    else if (binaryFormat) saveBinary.launch(NooirDestinationGuard.filename(outputName, extension))
                    else saveEpub.launch(NooirDestinationGuard.filename(outputName, "epub"))
                }) { Text(if (documents.size > 1) "Convert all PDFs & save separate " +
                    extension.uppercase() + " files to folder" else
                    "Convert PDF & save " + extension.uppercase() + " to phone") }
                Button(enabled = document != null && !busy &&
                    (documents.size > 1 || outputName.isNotBlank()) && validFormat && host.isNotBlank() &&
                    folder.startsWith("/") && !folder.contains(".."),
                    onClick = { enqueue(null) }) { Text(if (documents.size > 1)
                    "Convert all PDFs & send separately to XTEink" else
                    "Convert PDF & send " + extension.uppercase() + " to XTEink") }
                if (batchIds.size > 1 && tracked.isNotEmpty()) {
                    Text("PDF batch: $batchComplete/${batchIds.size} saved · " +
                        "$batchFailed failed/cancelled · " +
                        (batchIds.size - batchComplete - batchFailed).coerceAtLeast(0) +
                        " waiting or running")
                }
                val phase = displayState?.progress?.getString(NooirPdfExportWorker.PHASE).orEmpty()
                val percent = displayState?.progress?.getInt(NooirPdfExportWorker.PERCENT, -1) ?: -1
                displayState?.let { info ->
                    Text(when (info.state) {
                        WorkInfo.State.SUCCEEDED -> if (NooirDeviceWorkQueue.hasFailed(info)) "Failed: " + info.outputData.getString(NooirPdfExportWorker.ERROR).orEmpty() else extension.uppercase() + " conversion complete"
                        WorkInfo.State.FAILED -> "Failed: " +
                            info.outputData.getString(NooirPdfExportWorker.ERROR).orEmpty()
                        WorkInfo.State.CANCELLED -> "Canceled"
                        WorkInfo.State.BLOCKED -> "Waiting for previous PDF to finish"
                        else -> phase.ifBlank { "Queued" }
                    })
                }
                if (busy) {
                    if (percent >= 0) LinearProgressIndicator(
                        progress = { percent.coerceIn(0, 100) / 100f }, modifier = Modifier.fillMaxWidth(),
                    ) else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text("Cancel requests are managed in Nooir Tools → Activity Queue; whole-queue cancellation requires confirmation.")
                }
                Text("No AI or OCR service. PDF conversion processes pages progressively and stops on low storage or real EPUB/XTC format limits—not a fixed 600-page cutoff. Huge PDFs may take time or fail on complex images; protected PDFs remain unsupported.")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
