package dev.nooir.mangabridge

import android.content.Context
import android.content.Intent
import android.net.Uri
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

/** Manual public webpage URL input. Sharesheet link intake is a later UX step. */
@Composable
fun NooirWebToReaderDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val manager = remember { WorkManager.getInstance(context) }
    val prefs = remember { context.getSharedPreferences("manganooir_export", Context.MODE_PRIVATE) }
    var url by remember { mutableStateOf(prefs.getString("last_article_url", "") ?: "") }
    var host by remember { mutableStateOf(prefs.getString("host", "crosspoint.local") ?: "crosspoint.local") }
    var folder by remember { mutableStateOf(prefs.getString("destination", "/Books") ?: "/Books") }
    var workId by remember {
        mutableStateOf(prefs.getString(NooirWebExportWorker.LAST_WORK, null)?.let {
            runCatching { UUID.fromString(it) }.getOrNull()
        })
    }
    var state by remember { mutableStateOf<WorkInfo?>(null) }
    LaunchedEffect(workId) {
        workId?.let { key -> manager.getWorkInfoByIdFlow(key).collect { state = it } }
    }
    fun submit(saveUri: Uri?) {
        prefs.edit().putString("last_article_url", url.trim())
            .putString("host", host.trim()).putString("destination", folder.trim()).apply()
        val request = OneTimeWorkRequestBuilder<NooirWebExportWorker>()
            .setInputData(workDataOf(
                NooirWebExportWorker.URL to url.trim(),
                NooirWebExportWorker.SAVE_URI to saveUri?.toString(),
                NooirWebExportWorker.HOST to host.trim(),
                NooirWebExportWorker.FOLDER to folder.trim(),
            )).build()
        manager.enqueueUniqueWork(NooirDeviceWorkQueue.NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        workId = request.id
        prefs.edit().putString(NooirWebExportWorker.LAST_WORK, request.id.toString()).apply()
    }
    val savePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/epub+zip")
    ) { uri ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            ) } catch (_: SecurityException) { }
            submit(uri)
        }
    }
    val busy = state?.state == WorkInfo.State.ENQUEUED || state?.state == WorkInfo.State.RUNNING
    val validUrl = url.trim().startsWith("https://", ignoreCase = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Web to E-reader · EPUB") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 540.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Paste a public HTTPS article URL. MangaNooir saves readable text and supported inline images as a reflowable EPUB, locally on your phone.")
                OutlinedTextField(value = url, onValueChange = { url = it },
                    label = { Text("Article URL") }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, supportingText = { Text("https://example.com/article") })
                OutlinedTextField(value = host, onValueChange = { host = it },
                    label = { Text("XTEink IP or hostname") }, singleLine = true)
                OutlinedTextField(value = folder, onValueChange = { folder = it },
                    label = { Text("XTEink destination folder") }, singleLine = true)
                Button(enabled = validUrl && !busy, onClick = {
                    savePicker.launch("Article.epub")
                }) { Text("Save article EPUB to phone") }
                Button(enabled = validUrl && !busy && host.isNotBlank() &&
                    folder.startsWith("/") && !folder.contains(".."),
                    onClick = { submit(null) }) { Text("Convert article & send to XTEink") }
                val phase = state?.progress?.getString(NooirWebExportWorker.PHASE).orEmpty()
                val percent = state?.progress?.getInt(NooirWebExportWorker.PERCENT, -1) ?: -1
                state?.let { info ->
                    Text(when (info.state) {
                        WorkInfo.State.SUCCEEDED -> "Article EPUB saved successfully"
                        WorkInfo.State.FAILED -> "Failed: " +
                            info.outputData.getString(NooirWebExportWorker.ERROR).orEmpty()
                        WorkInfo.State.CANCELLED -> "Canceled"
                        else -> phase.ifBlank { "Queued" }
                    })
                }
                if (busy) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        progress = { if (percent >= 0) percent.coerceIn(0, 100) / 100f else 0f },
                    )
                    TextButton(onClick = { workId?.let(manager::cancelWorkById) }) {
                        Text("Cancel conversion")
                    }
                }
                Text("Experimental: works best on ordinary HTML articles. Script-only pages, paywalls and unsupported web image types may not import. EPUB text stays selectable and resizable. No AI or OCR is used.")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
