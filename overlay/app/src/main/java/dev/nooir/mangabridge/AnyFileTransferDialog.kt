package dev.nooir.mangabridge

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dev.nooir.bridge.ExportFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/** More -> Send & Convert files, with multi-selection and durable background work. */
@Composable
fun AnyFileTransferDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("manganooir_export", Context.MODE_PRIVATE) }
    val manager = remember { WorkManager.getInstance(context) }
    var chosen by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var displayNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var message by remember { mutableStateOf("") }
    var workId by remember {
        mutableStateOf(
            prefs.getString("last_transfer_work", null)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        )
    }
    var workInfo by remember { mutableStateOf<WorkInfo?>(null) }
    var mangaWorkId by remember {
        mutableStateOf(
            prefs.getString(NooirMangaExportWorker.PREF_WORK_ID, null)
                ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        )
    }
    var mangaWorkInfo by remember { mutableStateOf<WorkInfo?>(null) }
    var format by remember {
        mutableStateOf(runCatching { ExportFormat.valueOf(prefs.getString("format", "AUTO")!!) }
            .getOrDefault(ExportFormat.AUTO))
    }
    var device by remember { mutableStateOf(prefs.getString("device", "X4") ?: "X4") }
    var host by remember { mutableStateOf(prefs.getString("host", "crosspoint.local") ?: "crosspoint.local") }
    var destination by remember { mutableStateOf(prefs.getString("destination", "/Books") ?: "/Books") }
    var sendSettings by remember { mutableStateOf(false) }
    var options by remember { mutableStateOf(MangaConversionOptions.load(prefs)) }

    LaunchedEffect(workId) {
        val current = workId ?: return@LaunchedEffect
        manager.getWorkInfoByIdFlow(current).collect { workInfo = it }
    }

    LaunchedEffect(mangaWorkId) {
        val current = mangaWorkId ?: return@LaunchedEffect
        manager.getWorkInfoByIdFlow(current).collect { mangaWorkInfo = it }
    }

    fun persist() {
        prefs.edit().putString("format", format.name)
            .putString("device", device).putString("host", host.trim())
            .putString("destination", destination.trim()).apply()
        options.save(prefs)
    }
    fun enqueue(convert: Boolean, saveTree: Uri? = null) {
        if (chosen.isEmpty()) return
        persist()
        val req = OneTimeWorkRequestBuilder<NooirTransferWorker>()
            .setInputData(workDataOf(
                NooirTransferWorker.KEY_URIS to chosen.map(Uri::toString).toTypedArray(),
                NooirTransferWorker.KEY_HOST to host.trim(),
                NooirTransferWorker.KEY_FOLDER to destination.trim(),
                NooirTransferWorker.KEY_FORMAT to format.name,
                NooirTransferWorker.KEY_DEVICE to device,
                NooirTransferWorker.KEY_CONVERT to convert,
                NooirTransferWorker.KEY_TREE_URI to saveTree?.toString(),
                NooirTransferWorker.KEY_OPTIONS to options.token(),
            )).build()
        manager.enqueue(req)
        workId = req.id
        workInfo = null
        prefs.edit().putString("last_transfer_work", req.id.toString()).apply()
        message = "Queued " + chosen.size + " file(s). You may press Back; work continues."
    }

    val chooseFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { folder ->
        if (folder != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    folder, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                enqueue(true, saveTree = folder)
            } catch (e: SecurityException) {
                message = "Cannot keep access to that folder: " + e.message
            }
        }
    }
    val choose = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { files ->
        if (files.isNotEmpty()) {
            if (files.size > 25) {
                message = "Select at most 25 files per batch for now."
            } else {
                chosen = files
                files.forEach { file ->
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            file, Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: SecurityException) {
                        // Some providers don't offer persistent access; report if worker can't read it.
                    }
                }
                displayNames = files.map { file ->
                    context.contentResolver.query(file, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                        null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    } ?: (file.lastPathSegment ?: "Document")
                }
                message = "Selected " + files.size + " file(s)."
            }
        }
    }
    val valid = host.trim().isNotEmpty() && !host.contains('/') &&
        destination.startsWith("/") && !destination.contains("..")
    val running = workInfo?.state == WorkInfo.State.RUNNING || workInfo?.state == WorkInfo.State.ENQUEUED
    val canQueue = chosen.isNotEmpty() && valid && !running
    val progress = workInfo?.progress
    val phase = progress?.getString(NooirTransferWorker.KEY_PHASE).orEmpty()
    val item = progress?.getInt(NooirTransferWorker.KEY_INDEX, 0) ?: 0
    val count = progress?.getInt(NooirTransferWorker.KEY_COUNT, 0) ?: 0
    val percent = progress?.getInt(NooirTransferWorker.KEY_PERCENT, -1) ?: -1
    val sent = progress?.getLong(NooirTransferWorker.KEY_SENT, 0) ?: 0
    val bytes = progress?.getLong(NooirTransferWorker.KEY_TOTAL_BYTES, 0) ?: 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send & Convert files") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 540.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = { choose.launch(arrayOf("*/*")) }) {
                    Text("Choose multiple files")
                }
                if (displayNames.isNotEmpty()) {
                    Text("Selected " + displayNames.size + " file(s):")
                    displayNames.take(5).forEach { Text("• " + it, maxLines = 1) }
                    if (displayNames.size > 5) Text("…and " + (displayNames.size - 5) + " more")
                }
                if (message.isNotBlank()) Text(message)
                if (workInfo != null) {
                    Text("Transfer: " + when (workInfo?.state) {
                        WorkInfo.State.SUCCEEDED -> "All files finished successfully"
                        WorkInfo.State.FAILED -> "Failed: " + workInfo?.outputData
                            ?.getString(NooirTransferWorker.KEY_ERROR).orEmpty()
                        WorkInfo.State.CANCELLED -> "Cancelled"
                        else -> if (count > 0) "File " + item + "/" + count + " · " + phase
                            else "Queued or connecting…"
                    })
                    if (running) {
                        if (percent >= 0) {
                            LinearProgressIndicator(
                                progress = { percent.coerceIn(0, 100) / 100f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(percent.toString() + "% · " + (sent / 1024) + "/" + (bytes / 1024) + " KB sent")
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text("Preparing / converting. No accurate percentage available yet.")
                        }
                        TextButton(onClick = { workId?.let(manager::cancelWorkById) }) {
                            Text("Cancel transfer")
                        }
                        Text("You can press Back or switch apps. The transfer continues in the background.")
                    }
                }
                if (mangaWorkInfo != null) {
                    val mangaProgress = mangaWorkInfo?.progress
                    val stage = mangaProgress?.getString(NooirMangaExportWorker.KEY_STAGE).orEmpty()
                    val chapter = mangaProgress?.getInt(NooirMangaExportWorker.KEY_CHAPTER, 0) ?: 0
                    val chapters = mangaProgress?.getInt(NooirMangaExportWorker.KEY_CHAPTERS, 0) ?: 0
                    val pct = mangaProgress?.getInt(NooirMangaExportWorker.KEY_PERCENT, -1) ?: -1
                    val mangaActive = mangaWorkInfo?.state == WorkInfo.State.RUNNING ||
                        mangaWorkInfo?.state == WorkInfo.State.ENQUEUED
                    Text("Manga-detail export: " + when (mangaWorkInfo?.state) {
                        WorkInfo.State.SUCCEEDED -> "Complete"
                        WorkInfo.State.FAILED -> "Failed: " + mangaWorkInfo?.outputData
                            ?.getString(NooirMangaExportWorker.KEY_ERROR).orEmpty()
                        WorkInfo.State.CANCELLED -> "Cancelled"
                        else -> if (chapters > 0) stage + " · " + chapter + "/" + chapters
                            else "Queued / preparing…"
                    })
                    if (mangaActive) {
                        if (pct >= 0) {
                            LinearProgressIndicator(
                                progress = { pct.coerceIn(0, 100) / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(pct.toString() + "% sent; awaiting device confirmation at 100%")
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text("Preparing/converting: chapter progress, not a byte percentage")
                        }
                        TextButton(onClick = { mangaWorkId?.let(manager::cancelWorkById) }) {
                            Text("Cancel manga export")
                        }
                    }
                }
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("XTEink hostname or IP") },
                    singleLine = true,
                    supportingText = { Text("Same trusted Wi-Fi. Example: 192.168.1.102 (without http://)") },
                )
                OutlinedTextField(
                    value = destination,
                    onValueChange = { destination = it },
                    label = { Text("Destination folder on XTEink") },
                    singleLine = true,
                    supportingText = { Text("Use / if no Books folder exists.") },
                )
                Text("Device")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("X4", "X3").forEach { name ->
                        FilterChip(selected = device == name,
                            onClick = { device = name }, label = { Text(name) })
                    }
                }
                Text("Manga output format")
                ExportFormat.entries.forEach { option ->
                    FilterChip(
                        selected = format == option,
                        onClick = { format = option },
                        label = { Text(option.label) },
                    )
                }
                TextButton(onClick = { sendSettings = !sendSettings }) {
                    Text(if (sendSettings) "Hide conversion settings" else "More conversion settings")
                }
                if (sendSettings) {
                    MangaConversionSettings(options, onChange = { options = it })
                }
                Button(enabled = canQueue, onClick = { enqueue(false) }) {
                    Text("Send " + chosen.size + " original file(s)")
                }
                Button(enabled = canQueue, onClick = { enqueue(true) }) {
                    Text("Convert manga & send · " + chosen.size + " file(s)")
                }
                TextButton(enabled = chosen.isNotEmpty() && !running,
                    onClick = { persist(); chooseFolder.launch(null) }) {
                    Text("Convert & save selected to phone folder")
                }
                Text("Convert mode converts CBZ/images and copies unsupported documents unchanged. Each file is saved or sent separately.")
                TextButton(
                    enabled = chosen.isNotEmpty(),
                    onClick = {
                        val intent = Intent(if (chosen.size > 1) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND).apply {
                            type = "*/*"
                            if (chosen.size > 1) putParcelableArrayListExtra(
                                Intent.EXTRA_STREAM, ArrayList(chosen)
                            ) else putExtra(Intent.EXTRA_STREAM, chosen.first())
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share selected files"))
                    },
                ) { Text("Share selected with another app or device") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Back to MangaNooir") } },
    )
}
