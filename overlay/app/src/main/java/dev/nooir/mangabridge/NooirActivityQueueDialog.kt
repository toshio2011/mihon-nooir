package dev.nooir.mangabridge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
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
import androidx.work.WorkInfo
import androidx.work.WorkManager

/** Every long-running MangaNooir exporter uses one persistent work chain.
 * Query it through WorkManager on the phone; the XTEink performs no extra work.
 */
@Composable
fun NooirActivityQueueDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val workManager = remember { WorkManager.getInstance(context) }
    var jobs by remember { mutableStateOf<List<WorkInfo>>(emptyList()) }
    var filter by remember { mutableStateOf("Current") }
    var confirmCancelAll by remember { mutableStateOf(false) }
    var expandedIds by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(workManager) {
        workManager.getWorkInfosForUniqueWorkFlow(NooirDeviceWorkQueue.NAME)
            .collect { items -> jobs = items }
    }

    fun active(job: WorkInfo): Boolean = job.state in setOf(
        WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED,
    )
    val waiting = jobs.count { it.state in setOf(WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED) }
    val running = jobs.count { it.state == WorkInfo.State.RUNNING }
    val finished = jobs.count { !active(it) }
    val visible = jobs.filter { when (filter) {
        "Current" -> active(it)
        "History" -> !active(it)
        else -> true
    } }.sortedWith(compareBy<WorkInfo> {
        when (it.state) {
            WorkInfo.State.RUNNING -> 0
            WorkInfo.State.ENQUEUED -> 1
            WorkInfo.State.BLOCKED -> 2
            WorkInfo.State.FAILED -> 3
            WorkInfo.State.CANCELLED -> 4
            WorkInfo.State.SUCCEEDED -> if (NooirDeviceWorkQueue.hasFailed(it)) 3 else 5
        }
    }).take(40)

    fun label(job: WorkInfo): String {
        val custom = job.tags.firstOrNull { it.startsWith("nooir_label:") }
            ?.removePrefix("nooir_label:")
        if (!custom.isNullOrBlank()) return custom
        val manga = job.tags.firstOrNull {
            it.startsWith(NooirMangaExportWorker.TITLE_TAG_PREFIX)
        }?.removePrefix(NooirMangaExportWorker.TITLE_TAG_PREFIX)
        if (!manga.isNullOrBlank()) return "Manga · " + manga
        return when {
            job.tags.any { it.endsWith("NooirMangaExportWorker") } -> "Manga export"
            job.tags.any { it.endsWith("NooirTransferWorker") } -> "Local file transfer"
            job.tags.any { it.endsWith("NooirWebExportWorker") } -> "Web article → EPUB"
            job.tags.any { it.endsWith("NooirPdfExportWorker") } -> "PDF → EPUB"
            job.tags.any { it.endsWith("NooirNovelBatchWorker") } -> "Web novel batch"
            else -> "Nooir task"
        }
    }

    fun stage(job: WorkInfo): String = when {
        job.tags.any { it.endsWith("NooirMangaExportWorker") } ->
            job.progress.getString(NooirMangaExportWorker.KEY_STAGE).orEmpty()
        job.tags.any { it.endsWith("NooirTransferWorker") } ->
            job.progress.getString(NooirTransferWorker.KEY_PHASE).orEmpty()
        job.tags.any { it.endsWith("NooirWebExportWorker") } ->
            job.progress.getString(NooirWebExportWorker.PHASE).orEmpty()
        job.tags.any { it.endsWith("NooirPdfExportWorker") } ->
            job.progress.getString(NooirPdfExportWorker.PHASE).orEmpty()
        job.tags.any { it.endsWith("NooirNovelBatchWorker") } ->
            job.progress.getString(NooirNovelBatchWorker.STAGE).orEmpty()
        else -> ""
    }

    fun progress(job: WorkInfo): Int {
        val key = when {
            job.tags.any { it.endsWith("NooirMangaExportWorker") } ->
                NooirMangaExportWorker.KEY_PERCENT
            job.tags.any { it.endsWith("NooirTransferWorker") } ->
                NooirTransferWorker.KEY_PERCENT
            job.tags.any { it.endsWith("NooirWebExportWorker") } ->
                NooirWebExportWorker.PERCENT
            job.tags.any { it.endsWith("NooirPdfExportWorker") } ->
                NooirPdfExportWorker.PERCENT
            else -> ""
        }
        return if (key.isBlank()) -1 else job.progress.getInt(key, -1)
    }

    fun error(job: WorkInfo): String {
        val keys = listOf(NooirMangaExportWorker.KEY_ERROR, NooirTransferWorker.KEY_ERROR,
            NooirWebExportWorker.ERROR, NooirPdfExportWorker.ERROR, NooirNovelBatchWorker.ERRORS)
        return keys.firstNotNullOfOrNull { key ->
            job.outputData.getString(key)?.takeIf { it.isNotBlank() }
        }.orEmpty()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nooir Activity Queue") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 570.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Running: " + running + " · Waiting: " + waiting + " · Finished: " + finished)
                Text("A failed export is recorded as failed here, but the shared FIFO queue continues with the next book.")
                Text("All Nooir manga, local files, PDFs, web articles and novel batches use one sequential background queue. No extra processing runs on the e-reader.")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Current", "History", "All").forEach { name ->
                        FilterChip(selected = filter == name, onClick = { filter = name },
                            label = { Text(name) })
                    }
                }
                if (visible.isEmpty()) {
                    Text(if (filter == "Current") "No active Nooir conversions or transfers."
                        else "No jobs to display yet.")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { expandedIds = visible.map { it.id.toString() }.toSet() }) {
                        Text("Expand all")
                    }
                    TextButton(onClick = { expandedIds = emptySet() }) {
                        Text("Collapse all")
                    }
                }
                visible.forEach { job ->
                    val key = job.id.toString()
                    val expanded = key in expandedIds
                    val isManga = job.tags.any { it.endsWith("NooirMangaExportWorker") }
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(label(job))
                            Text(when (job.state) {
                                WorkInfo.State.RUNNING -> stage(job).ifBlank { "Running" }
                                WorkInfo.State.ENQUEUED -> "Queued; waiting for earlier jobs"
                                WorkInfo.State.BLOCKED -> "Waiting for previous task to finish"
                                WorkInfo.State.SUCCEEDED -> if (NooirDeviceWorkQueue.hasFailed(job))
                                    "Failed: " + error(job).ifBlank { "See task notification" } else "Completed"
                                WorkInfo.State.FAILED -> "Failed: " +
                                    error(job).ifBlank { "Check task notification for details" }
                                WorkInfo.State.CANCELLED -> "Cancelled"
                            })
                            if (job.state == WorkInfo.State.RUNNING) {
                                val pct = progress(job)
                                if (pct < 0) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                else {
                                    LinearProgressIndicator(
                                        progress = { pct.coerceIn(0, 100) / 100f },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Text("$" + "{pct.coerceIn(0, 100)}% · upload completion requires reader confirmation")
                                }
                            }
                            TextButton(onClick = {
                                expandedIds = if (expanded) expandedIds - key else expandedIds + key
                            }) { Text(if (expanded) "Hide details" else "Show details") }
                            if (expanded) {
                                if (isManga) {
                                    val selected = job.inputData.getInt(
                                        NooirMangaExportWorker.KEY_SELECTED_CHAPTERS, 0)
                                    val chapterNames = job.inputData.getString(
                                        NooirMangaExportWorker.KEY_CHAPTER_PREVIEW).orEmpty()
                                    val format = job.inputData.getString("format").orEmpty()
                                    val device = job.inputData.getString("device").orEmpty()
                                    val opts = MangaConversionOptions.fromToken(
                                        job.inputData.getString("options"))
                                    Text("Format: $" + "{format.ifBlank { "Auto" }} · Device: $" + "{device.ifBlank { "X4" }}")
                                    Text("Selected chapters: $" + "{selected.takeIf { it > 0 }?.toString() ?: "See original selection"}")
                                    if (chapterNames.isNotBlank()) {
                                        Text(chapterNames)
                                        if (selected > 12) Text("… and $" + "{selected - 12} more")
                                    }
                                    Text("Source: " + if (job.inputData.getBoolean("quick", false))
                                        "Quick Export (online)" else "Downloaded chapters")
                                    Text("Cover: " + if (opts.includeCover) "Include" else "Disabled")
                                    Text("Orientation: " + opts.orientation +
                                        " · Layout: " + opts.layout)
                                    if (opts.xtcExtendedMetadata) Text("Extended XTC/XTCH metadata enabled")
                                }
                                val saved = job.inputData.getString("output_uri").orEmpty()
                                val host = job.inputData.getString("host").orEmpty()
                                val folder = job.inputData.getString("folder").orEmpty()
                                if (saved.isNotBlank()) Text("Destination: phone file")
                                else if (host.isNotBlank()) Text("Destination: $" + "{host + folder}")
                                val detail = error(job)
                                if (detail.isNotBlank()) Text("Error details: " + detail)
                                Text("Job ID: " + key.take(8))
                            }
                        }
                    }
                }
                if (jobs.size > 40 && filter != "Current") {
                    Text("Showing up to 40 records; older records remain managed by Android WorkManager.")
                }
                if (running + waiting > 0) {
                    if (confirmCancelAll) {
                        Text("Cancel the entire Nooir queue, including waiting books? Partially uploaded files may remain on the device.")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                workManager.cancelUniqueWork(NooirDeviceWorkQueue.NAME)
                                confirmCancelAll = false
                            }) { Text("Yes, cancel all") }
                            TextButton(onClick = { confirmCancelAll = false }) {
                                Text("Keep jobs")
                            }
                        }
                    } else {
                        OutlinedButton(onClick = { confirmCancelAll = true }) {
                            Text("Cancel entire Nooir queue…")
                        }
                        Text("Cancelling one job in a WorkManager dependency chain can cancel following jobs, so individual cancellation is not offered on this screen.")
                    }
                }
                Text("Mihon's own chapter Download Queue remains separate and unchanged.")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
