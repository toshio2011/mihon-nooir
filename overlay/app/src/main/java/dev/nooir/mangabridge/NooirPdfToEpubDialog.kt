package dev.nooir.mangabridge

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import java.util.UUID

/** PDF → genuinely reflowable text + positioned illustrations, where possible. */
@Composable
fun NooirPdfToEpubDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("manganooir_export", Context.MODE_PRIVATE) }
    val workManager = remember { WorkManager.getInstance(context) }
    var document by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf("No PDF selected") }
    var host by remember { mutableStateOf(prefs.getString("host", "crosspoint.local") ?: "crosspoint.local") }
    var folder by remember { mutableStateOf(prefs.getString("destination", "/Books") ?: "/Books") }
    var mode by remember { mutableStateOf("MIXED") }
    var workId by remember {
        mutableStateOf(prefs.getString(NooirPdfExportWorker.LAST_WORK, null)?.let {
            runCatching { UUID.fromString(it) }.getOrNull()
        })
    }
    var state by remember { mutableStateOf<WorkInfo?>(null) }
    LaunchedEffect(workId) {
        workId?.let { key -> workManager.getWorkInfoByIdFlow(key).collect { state = it } }
    }

    fun enqueue(saveUri: Uri?) {
        val input = document ?: return
        prefs.edit().putString("host", host.trim()).putString("destination", folder.trim()).apply()
        val job = OneTimeWorkRequestBuilder<NooirPdfExportWorker>()
            .setInputData(workDataOf(
                NooirPdfExportWorker.PDF to input.toString(),
                NooirPdfExportWorker.SAVE_URI to saveUri?.toString(),
                NooirPdfExportWorker.HOST to host.trim(),
                NooirPdfExportWorker.FOLDER to folder.trim(),
                NooirPdfExportWorker.MODE to mode,
            )).build()
        workManager.enqueueUniqueWork(NooirDeviceWorkQueue.NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, job)
        workId = job.id
        prefs.edit().putString(NooirPdfExportWorker.LAST_WORK, job.id.toString()).apply()
    }

    val choosePdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            document = uri
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) { }
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) fileName = it.getString(0) ?: "Selected PDF"
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
    val busy = state?.state == WorkInfo.State.ENQUEUED || state?.state == WorkInfo.State.RUNNING
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("PDF to EPUB · text + images") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 540.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Convert a PDF to an offline EPUB. Extractable PDF text stays selectable and resizable; illustrations are embedded near their original locations when recoverable.")
                Button(onClick = { choosePdf.launch(arrayOf("application/pdf")) }) {
                    Text("Choose PDF from phone")
                }
                Text(fileName)
                Text("How should pages be converted?")
                FilterChip(selected = mode == "MIXED", onClick = { mode = "MIXED" },
                    label = { Text("Reflowable text + illustrations") })
                FilterChip(selected = mode == "ORIGINAL_PAGES",
                    onClick = { mode = "ORIGINAL_PAGES" },
                    label = { Text("Keep original pages as images") })
                Text(if (mode == "MIXED")
                    "Uses the PDF's text layer. Scans and complex diagram pages become images without OCR."
                    else "Preserves every page visually, but text won't resize.")
                OutlinedTextField(value = host, onValueChange = { host = it },
                    label = { Text("XTEink IP or hostname") }, singleLine = true)
                OutlinedTextField(value = folder, onValueChange = { folder = it },
                    label = { Text("XTEink destination folder") }, singleLine = true)
                Button(enabled = document != null && !busy, onClick = {
                    val file = fileName.substringBeforeLast('.', fileName)
                        .ifBlank { "Converted-PDF" }
                    saveEpub.launch(file + ".epub")
                }) { Text("Convert PDF & save EPUB to phone") }
                Button(enabled = document != null && !busy && host.isNotBlank() &&
                    folder.startsWith("/") && !folder.contains(".."),
                    onClick = { enqueue(null) }) { Text("Convert PDF & send EPUB to XTEink") }
                val phase = state?.progress?.getString(NooirPdfExportWorker.PHASE).orEmpty()
                val percent = state?.progress?.getInt(NooirPdfExportWorker.PERCENT, -1) ?: -1
                state?.let { info ->
                    Text(when (info.state) {
                        WorkInfo.State.SUCCEEDED -> if (NooirDeviceWorkQueue.hasFailed(info)) "Failed: " + info.outputData.getString(NooirPdfExportWorker.ERROR).orEmpty() else "EPUB conversion complete"
                        WorkInfo.State.FAILED -> "Failed: " +
                            info.outputData.getString(NooirPdfExportWorker.ERROR).orEmpty()
                        WorkInfo.State.CANCELLED -> "Canceled"
                        else -> phase.ifBlank { "Queued" }
                    })
                }
                if (busy) {
                    if (percent >= 0) LinearProgressIndicator(
                        progress = { percent.coerceIn(0, 100) / 100f }, modifier = Modifier.fillMaxWidth(),
                    ) else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text("Cancel requests are managed in Nooir Tools → Activity Queue; whole-queue cancellation requires confirmation.")
                }
                Text("No AI, OCR service, or paid API. Complex layouts, multi-column documents and vector drawings may need image fallback. Protected PDFs are not yet supported. Experimental 100 MiB / 600-page limit.")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
