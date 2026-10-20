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
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

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
    var combinePages by remember { mutableStateOf(false) }
    var combinedTitle by remember { mutableStateOf("") }
    var combinedAuthor by remember { mutableStateOf("") }
    var combinedFilename by remember { mutableStateOf("") }
    var orderedChapterTitles by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf("") }
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
    // A combined book can use hundreds of public chapter links. The manifest
    // is stored privately on the phone, avoiding WorkManager's 10 KiB input cap.
    val good = urls.isNotEmpty() &&
        (if (combinePages) links.toByteArray(Charsets.UTF_8).size <= 120_000
            else urls.size <= 10) &&
        urls.all { it.length <= 600 && it.startsWith("https://", true) } &&
        (!combinePages || orderedChapterTitles.isBlank() ||
            orderedChapterTitles.lines().size <= urls.size) &&
        (combinePages || allChapters || (start != null && end != null && start >= 1 && end >= start))
    val busy = jobState?.state == WorkInfo.State.ENQUEUED ||
        jobState?.state == WorkInfo.State.BLOCKED ||
        jobState?.state == WorkInfo.State.RUNNING

    fun enqueue(treeUri: Uri?) {
        if (!good) return
        inputError = ""
        // Only the small manifest filename goes into WorkManager Data.
        // The complete ordered chapter list stays in our app-private files.
        val manifest = if (combinePages) try {
            val json = JSONArray()
            val labels = orderedChapterTitles.lines()
            urls.forEachIndexed { index, url ->
                json.put(JSONObject().put("url", url)
                    .put("title", labels.getOrNull(index).orEmpty().trim()))
            }
            val bytes = json.toString().toByteArray(Charsets.UTF_8)
            require(bytes.size <= 128 * 1024) {
                "The pasted chapter list exceeds the 128 KiB import-manifest budget"
            }
            File(context.filesDir, "nooir-webpages-" + UUID.randomUUID() + ".json").also {
                it.writeBytes(bytes)
            }
        } catch (e: Exception) {
            inputError = e.message ?: "Cannot save chapter list"
            return
        } else null
        prefs.edit().putString("host", host.trim()).putString("destination", folder.trim()).apply()
        val job = OneTimeWorkRequestBuilder<NooirNovelBatchWorker>()
            .setInputData(workDataOf(
                NooirNovelBatchWorker.URLS to (if (combinePages) emptyArray<String>() else urls.toTypedArray()),
                NooirNovelBatchWorker.NAMES to (if (combinePages) emptyArray<String>()
                    else urls.map { customNames[it].orEmpty() }.toTypedArray()),
                NooirNovelBatchWorker.URL_MANIFEST to manifest?.name,
                NooirNovelBatchWorker.COMBINE_PAGES to combinePages,
                NooirNovelBatchWorker.BOOK_TITLE to combinedTitle.trim(),
                NooirNovelBatchWorker.BOOK_AUTHOR to combinedAuthor.trim(),
                NooirNovelBatchWorker.BOOK_FILENAME to combinedFilename.trim(),
                NooirNovelBatchWorker.OUTPUT_TREE to treeUri?.toString(),
                NooirNovelBatchWorker.HOST to host.trim(),
                NooirNovelBatchWorker.FOLDER to folder.trim(),
                NooirNovelBatchWorker.FIRST to (if (combinePages || allChapters) 1 else start),
                NooirNovelBatchWorker.LAST to (if (combinePages || allChapters) Int.MAX_VALUE else end),
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
        title = { Text("Web pages & novels → EPUB") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 540.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Paste public HTTPS story or chapter links, one per line. Auto-discover all chapters of a story, or combine manually selected webpages into one EPUB. Separate-story batches allow up to 10 stories; combined books use a private on-phone chapter list.")
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    androidx.compose.material3.Checkbox(
                        checked = combinePages, onCheckedChange = { combinePages = it },
                    )
                    Text("Combine pasted webpages into ONE EPUB")
                }
                Text(if (combinePages)
                    "Each pasted URL becomes one chapter, in the order shown above. Move lines to rearrange chapters. The pages must contain readable public text."
                    else "Each story link creates its own EPUB. Visible public chapters are discovered automatically.")
                OutlinedTextField(
                    value = links, onValueChange = { links = it },
                    label = { Text(if (combinePages) "Public chapter/page URLs in reading order" else "Public story URLs (one per line)") },
                    placeholder = { Text("https://example.org/story-one\nhttps://example.org/story-two") },
                    minLines = 3, maxLines = 7,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text((if (combinePages) "Selected chapters: " else "Selected stories: ") + urls.size +
                    if (combinePages) " · no fixed chapter count (128 KiB list budget)" else " / 10")
                if (combinePages) {
                    OutlinedTextField(value = combinedTitle,
                        onValueChange = { combinedTitle = it.take(180) },
                        label = { Text("Combined book title (optional)") },
                        placeholder = { Text("Uses first page title when blank") },
                        singleLine = true)
                    OutlinedTextField(value = combinedAuthor,
                        onValueChange = { combinedAuthor = it.take(100) },
                        label = { Text("Book author (optional)") }, singleLine = true)
                    OutlinedTextField(value = combinedFilename,
                        onValueChange = { combinedFilename = it.take(70) },
                        label = { Text("Combined EPUB filename (without .epub)") },
                        supportingText = { Text("Duplicate names are checked before downloading the remaining pages.") },
                        singleLine = true)
                }
                if (combinePages) {
                    Text("Optional chapter titles, one per line in the same order as the URLs. Leave blank to use the actual webpage titles.")
                    OutlinedTextField(value = orderedChapterTitles,
                        onValueChange = { orderedChapterTitles = it },
                        label = { Text("Optional chapter titles (one per line)") },
                        placeholder = { Text("Prologue\\nChapter One\\nChapter Two") },
                        minLines = 2, maxLines = 6,
                        modifier = Modifier.fillMaxWidth())
                } else {
                    Text("Optional: rename each EPUB before conversion. Leave blank to use the story title.")
                    urls.forEach { story ->
                        OutlinedTextField(value = customNames[story].orEmpty(),
                            onValueChange = { value -> customNames = customNames + (story to value.take(70)) },
                            label = { Text("Output name · " + story.take(38)) },
                            placeholder = { Text("Book title (without .epub)") },
                            singleLine = true)
                    }
                }
                if (!combinePages) {
                    androidx.compose.foundation.layout.Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        androidx.compose.material3.Checkbox(
                            checked = allChapters, onCheckedChange = { allChapters = it },
                        )
                        Text("Download all chapters found in the public chapter list")
                    }
                }
                if (!combinePages && !allChapters) {
                    Text("Chapter range (from the site's publicly visible chapter list)")
                    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = first, onValueChange = { first = it.take(9) },
                            label = { Text("First") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = last, onValueChange = { last = it.take(9) },
                            label = { Text("Last") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                }
                Text(if (combinePages)
                    "All selected pages go into one reflowable EPUB, with a navigable chapter list. If one page is unreadable, the whole book stops instead of silently omitting that chapter. Extremely long imports remain subject to battery, network and file-size limits."
                    else "All chapters uses every publicly discoverable chapter. Large stories may exceed the XTEink 180 MiB limit. Existing EPUB filenames are rejected before downloading chapters.")
                if (inputError.isNotBlank()) Text("Could not queue: " + inputError)
                OutlinedTextField(value = host, onValueChange = { host = it },
                    label = { Text("XTEink IP/hostname") }, singleLine = true)
                OutlinedTextField(value = folder, onValueChange = { folder = it },
                    label = { Text("Reader destination folder") }, singleLine = true)
                Button(enabled = good && !busy, onClick = { folderPicker.launch(null) }) {
                    Text(if (combinePages) "Save combined EPUB to phone" else "Download EPUBs to phone folder")
                }
                Button(enabled = good && !busy && host.isNotBlank() &&
                    folder.startsWith("/") && !folder.contains(".."),
                    onClick = { enqueue(null) }) {
                    Text(if (combinePages) "Combine & send one EPUB to XTEink" else "Convert novels & send to XTEink")
                }
                jobState?.let { info ->
                    val stage = info.progress.getString(NooirNovelBatchWorker.STAGE).orEmpty()
                    Text(when (info.state) {
                        WorkInfo.State.SUCCEEDED -> if (NooirDeviceWorkQueue.hasFailed(info)) "Export failed: " + info.outputData.getString(NooirNovelBatchWorker.ERRORS).orEmpty() else "Export completed"
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
