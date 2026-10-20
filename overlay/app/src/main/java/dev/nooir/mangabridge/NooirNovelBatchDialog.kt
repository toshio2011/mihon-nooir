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

/** Simple manual multi-link importer. All work runs in the phone and shared IO queue. */
@Composable
fun NooirNovelBatchDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val manager = remember { WorkManager.getInstance(context) }
    val prefs = remember { context.getSharedPreferences("manganooir_export", Context.MODE_PRIVATE) }
    var links by remember { mutableStateOf("") }
    var customNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var first by remember { mutableStateOf("1") }
    var last by remember { mutableStateOf("80") }
    var allChapters by remember { mutableStateOf(true) }
    var host by remember { mutableStateOf(prefs.getString("host", "crosspoint.local") ?: "crosspoint.local") }
    var folder by remember { mutableStateOf(prefs.getString("destination", "/Books") ?: "/Books") }
    var jobId by remember {
        mutableStateOf(prefs.getString(NooirNovelBatchWorker.LAST_WORK, null)?.let {
            runCatching { UUID.fromString(it) }.getOrNull()
        })
    }
    var jobState by remember { mutableStateOf<WorkInfo?>(null) }
    LaunchedEffect(jobId) {
        jobId?.let { manager.getWorkInfoByIdFlow(it).collect { state -> jobState = state } }
    }

    val urls = links.lines().map(String::trim).filter(String::isNotBlank).distinct()
    val start = first.toIntOrNull()
    val end = last.toIntOrNull()
    val good = urls.isNotEmpty() && urls.size <= 10 &&
        urls.all { it.length <= 600 && it.startsWith("https://", true) } &&
        (allChapters || (start != null && end != null && start >= 1 && end >= start))
    val busy = jobState?.state == WorkInfo.State.ENQUEUED ||
        jobState?.state == WorkInfo.State.BLOCKED ||
        jobState?.state == WorkInfo.State.RUNNING

    fun enqueue(treeUri: Uri?) {
        if (!good) return
        prefs.edit().putString("host", host.trim()).putString("destination", folder.trim()).apply()
        val job = OneTimeWorkRequestBuilder<NooirNovelBatchWorker>()
            .setInputData(workDataOf(
                NooirNovelBatchWorker.URLS to urls.toTypedArray(),
                NooirNovelBatchWorker.NAMES to urls.map { customNames[it].orEmpty() }.toTypedArray(),
                NooirNovelBatchWorker.OUTPUT_TREE to treeUri?.toString(),
                NooirNovelBatchWorker.HOST to host.trim(),
                NooirNovelBatchWorker.FOLDER to folder.trim(),
                NooirNovelBatchWorker.FIRST to (if (allChapters) 1 else start),
                NooirNovelBatchWorker.LAST to (if (allChapters) Int.MAX_VALUE else end),
            )).build()
        manager.enqueueUniqueWork(NooirDeviceWorkQueue.NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, job)
        jobId = job.id
        prefs.edit().putString(NooirNovelBatchWorker.LAST_WORK, job.id.toString()).apply()
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { target ->
        if (target != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    target, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            } catch (_: SecurityException) { }
            enqueue(target)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Web novels → EPUB batch") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 540.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Paste public story links, one per line (up to 10 books). MangaNooir checks each story's visible HTML chapter links, then builds one reflowable EPUB per novel.")
                OutlinedTextField(
                    value = links, onValueChange = { links = it },
                    label = { Text("Public story URLs (one per line)") },
                    placeholder = { Text("https://example.org/story-one\nhttps://example.org/story-two") },
                    minLines = 3, maxLines = 7,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Selected stories: " + urls.size + " / 10")
                Text("Optional: rename each EPUB before conversion. Leave blank to use the story title.")
                urls.forEach { story ->
                    OutlinedTextField(value = customNames[story].orEmpty(),
                        onValueChange = { value -> customNames = customNames + (story to value.take(70)) },
                        label = { Text("Output name · " + story.take(38)) },
                        placeholder = { Text("Book title (without .epub)") },
                        singleLine = true)
                }
                androidx.compose.foundation.layout.Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    androidx.compose.material3.Checkbox(
                        checked = allChapters, onCheckedChange = { allChapters = it },
                    )
                    Text("Download all chapters found in the public chapter list")
                }
                if (!allChapters) {
                    Text("Chapter range (from the site's publicly visible chapter list)")
                    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = first, onValueChange = { first = it.take(9) },
                            label = { Text("First") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = last, onValueChange = { last = it.take(9) },
                            label = { Text("Last") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                }
                Text("All chapters uses every publicly discoverable chapter. Large stories can take a long time or exceed XTEink's 180 MiB upload limit; save to phone if necessary. Existing EPUB filenames are rejected before any chapters download.")
                OutlinedTextField(value = host, onValueChange = { host = it },
                    label = { Text("XTEink IP/hostname") }, singleLine = true)
                OutlinedTextField(value = folder, onValueChange = { folder = it },
                    label = { Text("Reader destination folder") }, singleLine = true)
                Button(enabled = good && !busy, onClick = { folderPicker.launch(null) }) {
                    Text("Download EPUBs to phone folder")
                }
                Button(enabled = good && !busy && host.isNotBlank() &&
                    folder.startsWith("/") && !folder.contains(".."),
                    onClick = { enqueue(null) }) {
                    Text("Convert novels & send to XTEink")
                }
                jobState?.let { info ->
                    val stage = info.progress.getString(NooirNovelBatchWorker.STAGE).orEmpty()
                    Text(when (info.state) {
                        WorkInfo.State.SUCCEEDED -> if (NooirDeviceWorkQueue.hasFailed(info)) "Some exports failed: " + info.outputData.getString(NooirNovelBatchWorker.ERRORS).orEmpty() else "All selected novels exported"
                        WorkInfo.State.FAILED -> "Some exports failed: " +
                            info.outputData.getString(NooirNovelBatchWorker.ERRORS).orEmpty()
                        WorkInfo.State.CANCELLED -> "Cancelled"
                        WorkInfo.State.BLOCKED -> "Waiting in the device queue"
                        else -> stage.ifBlank { "Queued / preparing" }
                    })
                    if (busy) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("Use Nooir Tools → Activity Queue to cancel the entire chain with confirmation.")
                    }
                }
                Text("Publicly readable stories only. No paywall bypass, DRM removal, login-cookie collection or premium unlocking. Wattpad and other script-heavy sites may not expose chapters to this experimental HTML importer.")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
