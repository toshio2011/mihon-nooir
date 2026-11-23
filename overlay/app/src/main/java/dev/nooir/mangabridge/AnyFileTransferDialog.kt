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
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dev.nooir.bridge.ExportFormat
import dev.nooir.bridge.NooirDeviceProfiles
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
    // Input selection deliberately comes BEFORE Android's document picker.
    // Do not infer source format solely from a provider's often-generic MIME.
    var inputKind by remember { mutableStateOf<NooirLocalConversionPolicy.Picker?>(null) }
    var displayNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var inputMimeTypes by remember { mutableStateOf<List<String>>(emptyList()) }
    var outputNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var showRename by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var workId by remember {
        mutableStateOf(
            prefs.getString("last_transfer_work", null)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        )
    }
    var workInfo by remember { mutableStateOf<WorkInfo?>(null) }
    // A manga export is a chain, not a single task. Watching only the last
    // queued UUID hides the manga that is *currently* uploading.
    var mangaJobs by remember { mutableStateOf<List<WorkInfo>>(emptyList()) }
    var format by remember {
        mutableStateOf(runCatching { ExportFormat.valueOf(prefs.getString("format", "AUTO")!!) }
            .getOrDefault(ExportFormat.AUTO))
    }
    var device by remember { mutableStateOf(prefs.getString("device", "X4") ?: "X4") }
    var host by remember { mutableStateOf(prefs.getString("host", "crosspoint.local") ?: "crosspoint.local") }
    var destination by remember { mutableStateOf(prefs.getString("destination", "/Books") ?: "/Books") }
    var sendSettings by remember { mutableStateOf(false) }
    var options by remember { mutableStateOf(MangaConversionOptions.load(prefs)) }
    var epubFont by remember { mutableStateOf(prefs.getInt("epub_font_px", 24)) }
    var epubMargin by remember { mutableStateOf(prefs.getInt("epub_margin_px", 26)) }
    var epubRtl by remember { mutableStateOf(prefs.getBoolean("epub_right_to_left", false)) }

    LaunchedEffect(workId) {
        val current = workId ?: return@LaunchedEffect
        manager.getWorkInfoByIdFlow(current).collect { workInfo = it }
    }

    LaunchedEffect(manager) {
        manager.getWorkInfosForUniqueWorkFlow(NooirDeviceWorkQueue.NAME)
            .collect { jobs ->
                mangaJobs = jobs.filter { job ->
                    job.tags.any { it.startsWith(NooirMangaExportWorker.TITLE_TAG_PREFIX) } ||
                        job.tags.contains(NooirMangaExportWorker::class.java.name)
                }
            }
    }

    fun persist() {
        prefs.edit().putString("format", format.name)
            .putString("device", device).putString("host", host.trim())
            .putString("destination", destination.trim()).apply()
        options.save(prefs)
        prefs.edit().putInt("epub_font_px", epubFont)
            .putInt("epub_margin_px", epubMargin)
            .putBoolean("epub_right_to_left", epubRtl).apply()
    }
    fun changeInputKind(next: NooirLocalConversionPolicy.Picker) {
        if (inputKind == next) return
        inputKind = next
        chosen = emptyList()
        displayNames = emptyList()
        inputMimeTypes = emptyList()
        outputNames = emptyList()
        showRename = false
        sendSettings = false
        message = "Selected " + next.label + ". Choose source files to continue."
    }
    fun enqueue(convert: Boolean, saveTree: Uri? = null) {
        if (chosen.isEmpty()) return
        if (convert || saveTree != null) {
            val unsupported = displayNames.indices.filterNot { index ->
                NooirLocalConversionPolicy.canConvertTo(
                    displayNames[index], inputMimeTypes.getOrNull(index).orEmpty(),
                    format.resolved(device).name)
            }
            if (unsupported.isNotEmpty() || displayNames.size != chosen.size) {
                message = "Conversion not supported for: " +
                    unsupported.take(3).joinToString(", ") { displayNames[it] } +
                    ". Use Send originals to transfer unchanged; PDF has a separate converter."
                return
            }
        }
        persist()
        val req = OneTimeWorkRequestBuilder<NooirTransferWorker>()
            .setInputData(workDataOf(
                NooirTransferWorker.KEY_URIS to chosen.map(Uri::toString).toTypedArray(),
                NooirTransferWorker.KEY_OUTPUT_NAMES to outputNames.toTypedArray(),
                NooirTransferWorker.KEY_HOST to host.trim(),
                NooirTransferWorker.KEY_FOLDER to destination.trim(),
                NooirTransferWorker.KEY_FORMAT to format.name,
                NooirTransferWorker.KEY_DEVICE to device,
                NooirTransferWorker.KEY_CONVERT to convert,
                NooirTransferWorker.KEY_TREE_URI to saveTree?.toString(),
                NooirTransferWorker.KEY_OPTIONS to options.token(),
                NooirTransferWorker.KEY_EPUB_FONT to epubFont,
                NooirTransferWorker.KEY_EPUB_MARGIN to epubMargin,
                NooirTransferWorker.KEY_EPUB_RTL to epubRtl,
            )).build()
        // Reuse the same sequential chain as manga, URL and PDF exports.
        manager.enqueueUniqueWork(NooirDeviceWorkQueue.NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, req)
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
                val names = files.map { file ->
                    context.contentResolver.query(file, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                        null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    } ?: (file.lastPathSegment ?: "Document")
                }
                val mimes = files.map { file ->
                    context.contentResolver.getType(file).orEmpty()
                }
                val kind = inputKind
                val wrong = names.indices.filterNot { index ->
                    kind != null && NooirLocalConversionPolicy.matchesPicker(
                        kind, names[index], mimes[index])
                }
                if (wrong.isNotEmpty()) {
                    chosen = emptyList()
                    displayNames = emptyList()
                    inputMimeTypes = emptyList()
                    outputNames = emptyList()
                    message = "These files do not match the selected input type (" +
                        (kind?.label ?: "none") + "): " +
                        wrong.take(3).joinToString(", ") { names[it] } +
                        ". Change the input type, or use All files to send originals."
                } else {
                    chosen = files
                    files.forEach { file ->
                        try {
                            context.contentResolver.takePersistableUriPermission(
                                file, Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        } catch (_: SecurityException) {
                            // Some providers lack persistent grants; worker reports read failures.
                        }
                    }
                    displayNames = names
                    inputMimeTypes = mimes
                    outputNames = names
                    message = "Selected " + files.size +
                        " " + (kind?.label ?: "source") + " file(s). Choose output and settings below."
                }
            }
        }
    }
    val valid = host.trim().isNotEmpty() && !host.contains('/') &&
        destination.startsWith("/") && !destination.contains("..")
    val profile = NooirDeviceProfiles.forId(device)
    val eligibleFormats = ExportFormat.entries.filter(profile::accepts).filter { candidate ->
        val actual = candidate.resolved(device).name
        if (displayNames.isEmpty()) {
            inputKind?.let { NooirLocalConversionPolicy.canPickerConvertTo(it, actual) } == true
        } else {
            displayNames.indices.all { index ->
                NooirLocalConversionPolicy.canConvertTo(
                    displayNames[index], inputMimeTypes.getOrNull(index).orEmpty(), actual)
            }
        }
    }
    LaunchedEffect(displayNames, inputMimeTypes, device, inputKind) {
        if (eligibleFormats.isNotEmpty() && format !in eligibleFormats)
            format = eligibleFormats.first()
    }
    val hasEpubInput = inputKind == NooirLocalConversionPolicy.Picker.EPUB ||
        displayNames.indices.any { index ->
            NooirLocalConversionPolicy.classify(
                displayNames[index], inputMimeTypes.getOrNull(index).orEmpty()
            ) == NooirLocalConversionPolicy.Input.EPUB
        }
    val hasMangaInput = inputKind in setOf(
        NooirLocalConversionPolicy.Picker.COMIC, NooirLocalConversionPolicy.Picker.IMAGES) ||
        displayNames.indices.any { index ->
            NooirLocalConversionPolicy.canConvertHere(
                displayNames[index], inputMimeTypes.getOrNull(index).orEmpty())
        }
    val running = workInfo?.state == WorkInfo.State.RUNNING || workInfo?.state == WorkInfo.State.ENQUEUED
    val inputMatches = inputKind?.let { kind ->
        displayNames.size == chosen.size && inputMimeTypes.size == chosen.size &&
            displayNames.indices.all { index ->
                NooirLocalConversionPolicy.matchesPicker(
                    kind, displayNames[index], inputMimeTypes[index])
            }
    } == true
    val canQueue = chosen.isNotEmpty() && inputMatches && profile.xteinkWifi &&
        valid && !running && outputNames.size == chosen.size &&
        outputNames.all { it.isNotBlank() }
    val unsupportedForConversion = displayNames.indices.filterNot { index ->
        NooirLocalConversionPolicy.canConvertTo(
            displayNames[index], inputMimeTypes.getOrNull(index).orEmpty(),
            format.resolved(device).name)
    }
    val canConvertSelection = chosen.isNotEmpty() &&
        displayNames.size == chosen.size && inputMimeTypes.size == chosen.size &&
        unsupportedForConversion.isEmpty() && format in eligibleFormats && inputMatches &&
        outputNames.size == chosen.size &&
        outputNames.all { it.isNotBlank() }
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
                Text("1 · Choose input file type")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        NooirLocalConversionPolicy.Picker.EPUB,
                        NooirLocalConversionPolicy.Picker.COMIC,
                        NooirLocalConversionPolicy.Picker.IMAGES,
                    ).forEach { kind ->
                        FilterChip(selected = inputKind == kind,
                            onClick = { changeInputKind(kind) },
                            label = { Text(kind.label) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        NooirLocalConversionPolicy.Picker.PDF,
                        NooirLocalConversionPolicy.Picker.ANY,
                    ).forEach { kind ->
                        FilterChip(selected = inputKind == kind,
                            onClick = { changeInputKind(kind) },
                            label = { Text(kind.label) })
                    }
                }
                if (inputKind == null) {
                    Text("Select an input type first to filter Android's document picker.")
                } else {
                    Text("2 · Select source files")
                    Button(onClick = {
                        choose.launch(NooirLocalConversionPolicy.pickerMimeTypes(
                            requireNotNull(inputKind)))
                    }) { Text("Choose " + inputKind!!.label + " files") }
                    if (inputKind != NooirLocalConversionPolicy.Picker.ANY) {
                        TextButton(onClick = { choose.launch(arrayOf("*/*")) }) {
                            Text("File missing from picker? Browse all")
                        }
                        Text("Some file providers use generic ZIP or unknown MIME types. " +
                            "Browse all can find them; selected file types are still checked.")
                    }
                    if (inputKind == NooirLocalConversionPolicy.Picker.PDF) {
                        Text("PDF conversion is currently in Nooir Tools → PDF to e-reader. " +
                            "This screen can send PDF originals without changing them.")
                    }
                }
                if (displayNames.isNotEmpty()) {
                    Text("Selected " + displayNames.size + " file(s):")
                    displayNames.take(5).forEach { Text("• " + it, maxLines = 1) }
                    if (displayNames.size > 5) Text("…and " + (displayNames.size - 5) + " more")
                    TextButton(onClick = { showRename = !showRename }) { Text(if (showRename) "Hide output names" else "Rename output files before processing") }
                    if (showRename) {
                        outputNames.forEachIndexed { index, value ->
                            OutlinedTextField(value = value,
                                onValueChange = { updated -> outputNames = outputNames.toMutableList().also { it[index] = updated.take(150) } },
                                label = { Text("File " + (index + 1) + " · output name") },
                                singleLine = true)
                        }
                    }
                }
                if (message.isNotBlank()) Text(message)
                if (workInfo != null) {
                    Text("Transfer: " + when (workInfo?.state) {
                        WorkInfo.State.SUCCEEDED -> if (NooirDeviceWorkQueue.hasFailed(workInfo!!))
                            "Finished with errors: " + workInfo!!.outputData.getString(NooirTransferWorker.KEY_ERROR).orEmpty()
                            else "All files finished successfully"
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
                        Text("To cancel processing, use Nooir Tools → Activity Queue. Cancelling the entire chain requires confirmation.")
                        Text("You can press Back or switch apps. The transfer continues in the background.")
                    }
                }
                if (mangaJobs.isNotEmpty()) {
                    val active = mangaJobs.filter {
                        it.state == WorkInfo.State.RUNNING ||
                            it.state == WorkInfo.State.ENQUEUED ||
                            it.state == WorkInfo.State.BLOCKED
                    }
                    Text("Manga export queue · " + active.size + " active/waiting")
                    Text("Manga books run one at a time. Adding a second book does not stop the first one.")
                    val ordered = mangaJobs.sortedBy {
                        when (it.state) {
                            WorkInfo.State.RUNNING -> 0
                            WorkInfo.State.ENQUEUED -> 1
                            WorkInfo.State.BLOCKED -> 2
                            WorkInfo.State.FAILED -> 3
                            WorkInfo.State.CANCELLED -> 4
                            WorkInfo.State.SUCCEEDED -> 5
                        }
                    }
                    ordered.take(12).forEach { manga ->
                        val title = manga.tags.firstOrNull {
                            it.startsWith(NooirMangaExportWorker.TITLE_TAG_PREFIX)
                        }?.removePrefix(NooirMangaExportWorker.TITLE_TAG_PREFIX)
                            ?.takeIf { it.isNotBlank() }
                            ?: "Manga export " + manga.id.toString().take(8)
                        val stage = manga.progress.getString(NooirMangaExportWorker.KEY_STAGE).orEmpty()
                        val currentChapter = manga.progress.getInt(NooirMangaExportWorker.KEY_CHAPTER, 0)
                        val chapters = manga.progress.getInt(NooirMangaExportWorker.KEY_CHAPTERS, 0)
                        val pct = manga.progress.getInt(NooirMangaExportWorker.KEY_PERCENT, -1)
                        Text("• " + title)
                        Text("  " + when (manga.state) {
                            WorkInfo.State.RUNNING ->
                                stage.ifBlank { "Converting / uploading" } +
                                    if (chapters > 0) " · chapter " + currentChapter + "/" + chapters else ""
                            WorkInfo.State.ENQUEUED -> "Queued · waiting for worker"
                            WorkInfo.State.BLOCKED -> "Waiting for previous manga to finish"
                            WorkInfo.State.SUCCEEDED -> if (NooirDeviceWorkQueue.hasFailed(manga))
                                "Failed: " + manga.outputData.getString(NooirMangaExportWorker.KEY_ERROR).orEmpty()
                                else "Completed successfully"
                            WorkInfo.State.FAILED -> "Failed: " +
                                manga.outputData.getString(NooirMangaExportWorker.KEY_ERROR)
                                    .orEmpty().ifBlank { "Please retry" }
                            WorkInfo.State.CANCELLED -> "Cancelled"
                        })
                        if (manga.state == WorkInfo.State.RUNNING) {
                            if (pct >= 0) {
                                LinearProgressIndicator(
                                    progress = { pct.coerceIn(0, 100) / 100f },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text("  " + pct + "% sent · wait for XTEink confirmation at 100%")
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                Text("  Processing; total page count may not yet be known")
                            }
                        }
                    }
                    if (ordered.size > 12) {
                        Text("…and " + (ordered.size - 12) + " older queue records")
                    }
                    Text("Use Nooir Tools → Activity Queue to cancel the whole chain with confirmation. A failed export no longer blocks later books.")
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
                NooirDevicePicker(device) { id ->
                    device = id
                    if (!NooirDeviceProfiles.forId(id).accepts(format)) format = ExportFormat.AUTO
                }
                Text("3 · Convert to")
                if (eligibleFormats.isEmpty()) {
                    Text(when {
                        inputKind == null -> "Choose an input type first."
                        displayNames.isEmpty() && inputKind == NooirLocalConversionPolicy.Picker.ANY ->
                            "Select files first; supported output formats depend on their contents."
                        inputKind == NooirLocalConversionPolicy.Picker.PDF ->
                            "Use the separate PDF to e-reader tool for PDF conversion."
                        else -> "No common conversion output is supported for these files."
                    })
                }
                eligibleFormats.forEach { option ->
                    FilterChip(
                        selected = format == option,
                        onClick = { format = option },
                        label = { Text(option.label) },
                    )
                }
                if (eligibleFormats.isNotEmpty()) TextButton(
                    onClick = { sendSettings = !sendSettings }) {
                    Text(if (sendSettings) "Hide conversion settings"
                         else "4 · More conversion settings")
                }
                if (sendSettings && eligibleFormats.isNotEmpty()) {
                    if (hasEpubInput) {
                        Text("EPUB → XTC/XTCH · experimental text and picture pagination")
                        Text("Font size: " + epubFont + " px")
                        Slider(value = epubFont.toFloat(), onValueChange = {
                            epubFont = it.toInt().coerceIn(14, 46)
                        }, valueRange = 14f..46f)
                        Text("Page margin: " + epubMargin + " px")
                        Slider(value = epubMargin.toFloat(), onValueChange = {
                            epubMargin = it.toInt().coerceIn(8, 80)
                        }, valueRange = 8f..80f)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Switch(checked = epubRtl, onCheckedChange = { epubRtl = it })
                            Text("Right-to-left page order (for Arabic or RTL books)")
                        }
                        Text("Uses Android text layout and images in EPUB spine order. " +
                            "Advanced CSS, complex tables, SVG and embedded fonts are not fully preserved.")
                    }
                    if (hasMangaInput) {
                        MangaConversionSettings(options, format.resolved(device),
                            onChange = { options = it })
                    }
                }
                if (!profile.xteinkWifi) Text("Direct Wi-Fi upload is only offered for compatible XTEink firmware. Save/export to the phone, then use the reader’s supported transfer method.")
                Button(enabled = canQueue, onClick = { enqueue(false) }) {
                    Text("Send " + chosen.size + " original file(s)")
                }
                Button(enabled = canQueue && canConvertSelection, onClick = { enqueue(true) }) {
                    Text("Convert & send · " + chosen.size + " file(s)")
                }
                TextButton(enabled = canConvertSelection && !running,
                    onClick = { persist(); chooseFolder.launch(null) }) {
                    Text("Convert & save supported files to phone folder")
                }
                if (unsupportedForConversion.isNotEmpty()) {
                    val first = unsupportedForConversion.first()
                    val shownNames = unsupportedForConversion.take(4)
                        .joinToString(", ") { displayNames[it] }
                    val extraNames = if (unsupportedForConversion.size > 4) " (and more)" else ""
                    Text("Not convertible here: " + shownNames + extraNames)
                    Text(NooirLocalConversionPolicy.unsupportedHint(
                        displayNames[first], inputMimeTypes.getOrNull(first).orEmpty()))
                }
                Text("Existing names are checked in the destination before each conversion. Rename above if needed; a conflict skips only that file and later books continue. For converted manga the selected format extension is used.")
                Text("Convert supports images and verified comic CBZ/ZIP inputs, plus experimental " +
                    "EPUB → XTC/XTCH rendering for text and illustrated books. " +
                    "PDF conversion remains in the separate PDF to e-reader tool. " +
                    "No input is silently copied when Convert is selected.")
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
