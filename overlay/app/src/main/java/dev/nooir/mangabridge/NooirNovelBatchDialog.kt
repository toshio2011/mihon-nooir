package dev.nooir.mangabridge

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import dev.nooir.bridge.ReflowEpubStyle
import dev.nooir.bridge.NooirEpubCssRules
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Simple manual multi-link importer. All work runs in the phone and shared IO queue. */
@Composable
fun NooirNovelBatchDialog(
    onDismiss: () -> Unit,
    initialCombinePages: Boolean = false,
    initialUrls: String = "",
) {
    val context = LocalContext.current
    val manager = remember { WorkManager.getInstance(context) }
    val previewScope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("manganooir_export", Context.MODE_PRIVATE) }
    val projectStore = remember { NooirWebProjectStore(context.applicationContext) }
    var savedProjects by remember {
        mutableStateOf(runCatching { projectStore.list() }.getOrDefault(emptyList()))
    }
    var projectName by remember { mutableStateOf("") }
    var activeProjectId by remember { mutableStateOf("") }
    var projectMessage by remember { mutableStateOf("") }
    var pendingDeletion by remember { mutableStateOf("") }
    var sourceStoryUrl by remember { mutableStateOf("") }
    var knownChapterUrls by remember { mutableStateOf("") }
    var checkingUpdates by remember { mutableStateOf(false) }
    var foundNewChapters by remember {
        mutableStateOf<List<NooirWebChapterPlan.Entry>>(emptyList())
    }
    var latestInventory by remember { mutableStateOf("") }
    var updatesPage by remember { mutableStateOf(0) }
    var updateMessage by remember { mutableStateOf("") }
    var links by remember(initialUrls) { mutableStateOf(initialUrls) }
    var customNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var first by remember { mutableStateOf("1") }
    var last by remember { mutableStateOf("80") }
    var allChapters by remember { mutableStateOf(true) }
    var combinePages by remember(initialCombinePages) { mutableStateOf(initialCombinePages) }
    var combinedTitle by remember { mutableStateOf("") }
    var combinedAuthor by remember { mutableStateOf("") }
    var combinedDescription by remember { mutableStateOf("") }
    var combinedLanguage by remember { mutableStateOf("") }
    var coverUrl by remember { mutableStateOf("") }
    var localCoverUri by remember { mutableStateOf("") }
    var combineCovers by remember { mutableStateOf(false) }
    var showCoverSettings by remember { mutableStateOf(false) }
    var showAdvancedExtraction by remember { mutableStateOf(false) }
    var collageCoverLinks by remember { mutableStateOf("") }
    var coverPreview by remember { mutableStateOf<ImageBitmap?>(null) }
    var previewingCover by remember { mutableStateOf(false) }
    var coverPreviewMessage by remember { mutableStateOf("") }
    var coverPreviewFor by remember { mutableStateOf("") }
    var epub2 by remember { mutableStateOf(false) }
    var includeImages by remember { mutableStateOf(true) }
    var imageProfile by remember { mutableStateOf(NooirWebImageProfile.BALANCED) }
    var readingStyle by remember { mutableStateOf(ReflowEpubStyle.STANDARD) }
    var customCss by remember { mutableStateOf("") }
    var contentSelector by remember { mutableStateOf("") }
    var removeSelector by remember { mutableStateOf("") }
    var checkingExtraction by remember { mutableStateOf(false) }
    var extractionPreview by remember { mutableStateOf("") }
    var analysis by remember { mutableStateOf<NooirWebChapterDiscovery.Analysis?>(null) }
    var choices by remember { mutableStateOf<List<NooirWebChapterPlan.Entry>>(emptyList()) }
    var analyzing by remember { mutableStateOf(false) }
    var chaptersPage by remember { mutableStateOf(0) }
    var previewing by remember { mutableStateOf(false) }
    var sampleUrl by remember { mutableStateOf("") }
    var sampleText by remember { mutableStateOf("") }
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
    val freshAnalysis = analysis?.takeIf { !combinePages && urls.size == 1 &&
        it.inputUrl == urls.single() }
    LaunchedEffect(links, combinePages) {
        if (combinePages || analysis?.inputUrl != urls.singleOrNull()) {
            analysis = null
            choices = emptyList()
            chaptersPage = 0
            sampleUrl = ""
            sampleText = ""
        }
    }
    val collageUrls = collageCoverLinks.lines().map(String::trim).filter(String::isNotBlank)
    val coverPreviewKey = listOf(links, coverUrl, combineCovers.toString(),
        collageCoverLinks, localCoverUri).joinToString("\u0001")
    val start = first.toIntOrNull()
    val end = last.toIntOrNull()
    // A combined book can use hundreds of public chapter links. The manifest
    // is stored privately on the phone, avoiding WorkManager's 10 KiB input cap.
    val good = urls.isNotEmpty() &&
        (if (combinePages) links.toByteArray(Charsets.UTF_8).size <= 120_000
            else urls.size <= 10) &&
        urls.all { it.length <= 600 && it.startsWith("https://", true) } &&
        NooirEpubCssRules.valid(customCss) &&
        (!combinePages || NooirWebExtractRules.valid(contentSelector, removeSelector)) &&
        (!combinePages || orderedChapterTitles.isBlank() ||
            orderedChapterTitles.lines().size <= urls.size) &&
        (!combinePages || (
            coverUrl.length <= 600 &&
            (localCoverUri.isBlank() || (localCoverUri.startsWith("content://") &&
                localCoverUri.length <= 1000 && coverUrl.isBlank() && !combineCovers)) &&
            (coverUrl.isBlank() || coverUrl.trim().startsWith("https://", true)) &&
            (!combineCovers || (coverUrl.isBlank() &&
                (collageUrls.isEmpty() || (collageUrls.size in 2..4 &&
                    collageUrls.distinct().size == collageUrls.size &&
                    collageUrls.all { it.length <= 600 && it.startsWith("https://", true) })) )) &&
            (combinedLanguage.isBlank() ||
                combinedLanguage.matches(Regex("[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8}){0,3}")))
        )) &&
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
        try {
            val job = OneTimeWorkRequestBuilder<NooirNovelBatchWorker>()
            .setInputData(workDataOf(
                NooirNovelBatchWorker.URLS to (if (combinePages) emptyArray<String>() else urls.toTypedArray()),
                NooirNovelBatchWorker.NAMES to (if (combinePages) emptyArray<String>()
                    else urls.map { customNames[it].orEmpty() }.toTypedArray()),
                NooirNovelBatchWorker.URL_MANIFEST to manifest?.name,
                NooirNovelBatchWorker.COMBINE_PAGES to combinePages,
                NooirNovelBatchWorker.BOOK_TITLE to combinedTitle.trim(),
                NooirNovelBatchWorker.BOOK_AUTHOR to combinedAuthor.trim(),
                NooirNovelBatchWorker.BOOK_DESCRIPTION to (if (combinePages) combinedDescription.trim() else ""),
                NooirNovelBatchWorker.BOOK_LANGUAGE to (if (combinePages) combinedLanguage.trim() else ""),
                NooirNovelBatchWorker.BOOK_COVER_URL to (if (combinePages) coverUrl.trim() else ""),
                NooirNovelBatchWorker.BOOK_COVER_LOCAL_URI to (
                    if (combinePages && !combineCovers) localCoverUri else ""),
                NooirNovelBatchWorker.BOOK_COVER_COLLAGE to (combinePages && combineCovers),
                NooirNovelBatchWorker.BOOK_COVER_SOURCES to (
                    if (combinePages && combineCovers) collageUrls.toTypedArray() else emptyArray<String>()),
                NooirNovelBatchWorker.EPUB_VERSION to (if (epub2) "EPUB2" else "EPUB3"),
                NooirNovelBatchWorker.INCLUDE_IMAGES to includeImages,
                NooirNovelBatchWorker.IMAGE_PROFILE to imageProfile.name,
                NooirNovelBatchWorker.READING_STYLE to readingStyle.name,
                NooirNovelBatchWorker.CUSTOM_CSS to customCss,
                NooirNovelBatchWorker.CONTENT_SELECTOR to (if (combinePages) contentSelector.trim() else ""),
                NooirNovelBatchWorker.REMOVE_SELECTOR to (if (combinePages) removeSelector.trim() else ""),
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
        } catch (e: Exception) {
            manifest?.delete()
            inputError = "Could not queue web import: " + (e.message ?: "Invalid settings").take(200)
        }
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

    val coverPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { target ->
        if (target != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    target, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                localCoverUri = target.toString()
                coverUrl = ""
                combineCovers = false
                coverPreview = null
                coverPreviewMessage = ""
                inputError = ""
            } catch (_: SecurityException) {
                inputError = "Cannot keep access to this cover; choose a different image document."
            }
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
                if (!combinePages && urls.size == 1) {
                    OutlinedButton(
                        enabled = !busy && !analyzing,
                        onClick = {
                            val requested = urls.single()
                            analyzing = true
                            analysis = null
                            choices = emptyList()
                            sampleUrl = ""
                            sampleText = ""
                            inputError = ""
                            previewScope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    runCatching { NooirWebChapterDiscovery.inspect(requested) }
                                }
                                analyzing = false
                                if (combinePages || links.lines().map(String::trim)
                                        .filter(String::isNotBlank).distinct() != listOf(requested)) {
                                    return@launch
                                }
                                result.onSuccess {
                                    analysis = it
                                    choices = it.chapters
                                    chaptersPage = 0
                                }.onFailure {
                                    inputError = "Could not analyze chapter list: " +
                                        (it.message ?: "public chapters unavailable").take(260)
                                }
                            }
                        },
                    ) { Text("Analyze chapters before export") }
                    if (analyzing) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("Discovering public chapter links…")
                    }
                    freshAnalysis?.let { discovered ->
                        val current = choices
                        val selectedCount = current.count { it.included }
                        Text("Found ${current.size} listed chapters · ${selectedCount} selected")
                        Text("Book: " + discovered.title.take(120))
                        if (discovered.author.isNotBlank()) Text("Author: " + discovered.author.take(120))
                        if (discovered.detectedCoverUrl.isNotBlank())
                            Text("Detected cover: " + discovered.detectedCoverUrl.take(115))
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            OutlinedButton(onClick = {
                                choices = NooirWebChapterPlan.chooseAll(choices, true)
                            }) { Text("Select all") }
                            OutlinedButton(onClick = {
                                choices = NooirWebChapterPlan.chooseAll(choices, false)
                            }) { Text("Select none") }
                            OutlinedButton(onClick = {
                                choices = NooirWebChapterPlan.reverse(choices)
                            }) { Text("Reverse") }
                        }
                        val chunkSize = 10
                        val maxPage = ((current.size - 1) / chunkSize).coerceAtLeast(0)
                        val page = chaptersPage.coerceIn(0, maxPage)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(enabled = page > 0, onClick = {
                                chaptersPage = page - 1
                            }) { Text("Previous 10") }
                            Text("Page ${page + 1}/${maxPage + 1}")
                            OutlinedButton(enabled = page < maxPage, onClick = {
                                chaptersPage = page + 1
                            }) { Text("Next 10") }
                        }
                        val from = page * chunkSize
                        for (index in from until minOf(current.size, from + chunkSize)) {
                            val entry = current[index]
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Checkbox(checked = entry.included, onCheckedChange = {
                                    choices = NooirWebChapterPlan.toggle(choices, index)
                                })
                                Text("${index + 1}. " + entry.title, modifier = Modifier.weight(1f),
                                    maxLines = 2)
                                OutlinedButton(enabled = index > 0, onClick = {
                                    choices = NooirWebChapterPlan.move(choices, index, index - 1)
                                }) { Text("↑") }
                                OutlinedButton(enabled = index < current.lastIndex, onClick = {
                                    choices = NooirWebChapterPlan.move(choices, index, index + 1)
                                }) { Text("↓") }
                            }
                            OutlinedButton(
                                enabled = !previewing,
                                onClick = {
                                    previewing = true
                                    sampleText = ""
                                    sampleUrl = entry.url
                                    val title = entry.title
                                    val chapterUrl = entry.url
                                    previewScope.launch {
                                        val result = withContext(Dispatchers.IO) {
                                            runCatching {
                                                val pageDoc = NooirWebArticle.load(chapterUrl)
                                                NooirWebArticle.previewChapterText(pageDoc, title)
                                            }
                                        }
                                        previewing = false
                                        if (analysis?.inputUrl == discovered.inputUrl &&
                                            choices.any { it.url == chapterUrl } && !combinePages) {
                                            sampleUrl = chapterUrl
                                            sampleText = result.getOrElse {
                                                "Preview unavailable: " + (it.message ?: "Cannot read public text").take(180)
                                            }
                                        }
                                    }
                                },
                            ) { Text("Preview text") }
                            if (sampleUrl == entry.url) {
                                if (previewing) Text("Loading public text…")
                                else if (sampleText.isNotBlank())
                                    Text(sampleText.take(1500))
                            }
                        }
                        OutlinedButton(enabled = selectedCount > 0 && !busy,
                            onClick = {
                                runCatching { NooirWebChapterPlan.export(choices) }
                                    .onSuccess { selected ->
                                        links = selected.urls.joinToString("\n")
                                        orderedChapterTitles = selected.titles.joinToString("\n")
                                        if (combinedTitle.isBlank()) combinedTitle = discovered.title
                                        if (combinedAuthor.isBlank()) combinedAuthor = discovered.author
                                        if (combinedLanguage.isBlank())
                                            combinedLanguage = discovered.language
                                        if (combinedDescription.isBlank())
                                            combinedDescription = discovered.description
                                        if (coverUrl.isBlank() && !combineCovers)
                                            coverUrl = discovered.detectedCoverUrl.take(600)
                                        combinePages = true
                                        sourceStoryUrl = discovered.inputUrl
                                        knownChapterUrls = discovered.chapters.joinToString("\n") { it.url }
                                        foundNewChapters = emptyList()
                                        latestInventory = ""
                                        updateMessage = ""
                                        if (projectName.isBlank()) projectName = discovered.title.take(100)
                                        inputError = ""
                                    }
                                    .onFailure { inputError = it.message ?: "Invalid chapter selection" }
                            }) {
                            Text("Use selected chapters in one EPUB")
                        }
                        Text("This opens the editable combined-book settings next. " +
                            "Chapter content is fetched only during preview or export; " +
                            "a public chapter list does not guarantee readable text.")
                    }
                }

                if (combinePages) {
                    OutlinedTextField(value = combinedTitle,
                        onValueChange = { combinedTitle = it.take(180) },
                        label = { Text("Combined book title (optional)") },
                        placeholder = { Text("Uses first page title when blank") },
                        singleLine = true)
                    OutlinedTextField(value = combinedAuthor,
                        onValueChange = { combinedAuthor = it.take(100) },
                        label = { Text("Book author (optional)") }, singleLine = true)
                    OutlinedTextField(value = combinedLanguage,
                        onValueChange = { combinedLanguage = it.take(32) },
                        label = { Text("Book language (optional, e.g. en, ms, ar)") },
                        supportingText = { Text("Blank keeps the source page language.") },
                        singleLine = true)
                    OutlinedTextField(value = combinedDescription,
                        onValueChange = { combinedDescription = it.take(1200) },
                        label = { Text("Book description (optional)") },
                        minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth())
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { showCoverSettings = !showCoverSettings },
                    ) {
                        Text(if (showCoverSettings) "Hide cover settings" else
                            "Cover options · auto-detect source cover")
                    }
                    if (showCoverSettings) {
                    androidx.compose.foundation.layout.Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        androidx.compose.material3.Checkbox(
                            checked = combineCovers,
                            onCheckedChange = { enabled ->
                                combineCovers = enabled
                                if (enabled) {
                                    coverUrl = ""
                                    localCoverUri = ""
                                }
                            },
                        )
                        Text("Make one combined cover from multiple book covers")
                    }
                    if (combineCovers) {
                        Text("Automatically use 2–4 distinct book covers found in the first four source pages. For chapter-only pages without book cover metadata, paste 2–4 public JPEG/PNG cover image URLs below.")
                        OutlinedTextField(
                            value = collageCoverLinks,
                            onValueChange = { collageCoverLinks = it.take(2500) },
                            label = { Text("Optional individual cover image URLs (one per line)") },
                            placeholder = { Text("https://example.org/cover-one.jpg\\nhttps://example.org/cover-two.jpg") },
                            supportingText = { Text("Leave empty for automatic covers. All selected covers must be public HTTPS; minimum two distinct covers. The finished EPUB has one combined portrait cover.") },
                            minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        OutlinedTextField(value = coverUrl,
                            onValueChange = {
                                coverUrl = it.take(600)
                                if (it.isNotBlank()) localCoverUri = ""
                            },
                            label = { Text("Single cover image HTTPS URL (optional)") },
                            supportingText = { Text("Blank: try source og:image or Twitter cover. JPEG or PNG, up to 1.5 MB.") },
                            singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    if (!combineCovers) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(enabled = !busy,
                                onClick = { coverPicker.launch(arrayOf("image/jpeg", "image/png")) }) {
                                Text("Choose cover from phone")
                            }
                            if (localCoverUri.isNotBlank()) {
                                TextButton(onClick = { localCoverUri = "" }) {
                                    Text("Clear phone cover")
                                }
                            }
                        }
                        if (localCoverUri.isNotBlank())
                            Text("Phone cover selected. It must remain accessible until export.")
                    }
                    OutlinedButton(
                        enabled = !previewingCover && !busy && urls.isNotEmpty() &&
                            (!combineCovers || collageUrls.isEmpty() ||
                                (collageUrls.size in 2..4 && collageUrls.distinct().size == collageUrls.size)),
                        onClick = {
                            val requestedUrls = urls.toList()
                            val requestedSingle = coverUrl.trim()
                            val requestedLocal = localCoverUri
                            val requestedCollage = combineCovers
                            val requestedCollageUrls = collageUrls.toList()
                            val requestedKey = coverPreviewKey
                            previewingCover = true
                            coverPreviewMessage = ""
                            coverPreview = null
                            previewScope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    runCatching {
                                        val asset = if (requestedLocal.isNotBlank())
                                            NooirWebCover.loadLocal(context, Uri.parse(requestedLocal))
                                        else NooirWebPageBook.previewCoverAsset(
                                            requestedUrls, requestedSingle,
                                            requestedCollage, requestedCollageUrls,
                                        ) ?: error("No cover found in public metadata. " +
                                            "Choose a phone image or a public JPEG/PNG cover URL.")
                                        val bounds = BitmapFactory.Options().apply {
                                            inJustDecodeBounds = true
                                        }
                                        BitmapFactory.decodeByteArray(
                                            asset.bytes, 0, asset.bytes.size, bounds)
                                        var scale = 1
                                        while (bounds.outWidth / scale > 480 ||
                                            bounds.outHeight / scale > 640) scale *= 2
                                        val options = BitmapFactory.Options().apply {
                                            inSampleSize = scale
                                            inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                                        }
                                        (BitmapFactory.decodeByteArray(
                                            asset.bytes, 0, asset.bytes.size, options)
                                            ?: error("Cannot decode selected cover")).asImageBitmap()
                                    }
                                }
                                previewingCover = false
                                val currentKey = listOf(links, coverUrl, combineCovers.toString(),
                                    collageCoverLinks, localCoverUri).joinToString("\u0001")
                                if (combinePages && currentKey == requestedKey) {
                                    coverPreviewFor = requestedKey
                                    result.onSuccess { coverPreview = it }
                                        .onFailure { coverPreviewMessage =
                                            "Cover preview failed: " +
                                            (it.message ?: "Unknown cover error").take(220) }
                                }
                            }
                        },
                    ) {
                        Text(if (combineCovers) "Preview actual combined cover" else "Preview selected book cover")
                    }
                    if (previewingCover) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("Loading public cover images…")
                    }
                    if (coverPreviewFor == coverPreviewKey) {
                        coverPreview?.let { image ->
                            Image(
                                bitmap = image,
                                contentDescription = "Preview of the selected EPUB book cover",
                                modifier = Modifier.fillMaxWidth().height(230.dp),
                                contentScale = ContentScale.Fit,
                            )
                        }
                        if (coverPreviewMessage.isNotBlank()) Text(coverPreviewMessage)
                    }
                    } // optional cover settings
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
                    Text("Saved web-novel projects (on this phone only)")
                    OutlinedTextField(
                        value = projectName,
                        onValueChange = { projectName = it.take(100) },
                        label = { Text("Project name (for saving this setup)") },
                        placeholder = { Text("My novel or Volume 1") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            enabled = !busy && urls.isNotEmpty(),
                            onClick = {
                                val selectedName = projectName.trim().ifBlank {
                                    combinedTitle.trim().ifBlank { "My web novel" }
                                }
                                runCatching {
                                    savedProjects = projectStore.save(
                                        NooirWebProjectStore.Project(
                                            id = activeProjectId,
                                            name = selectedName.take(100),
                                            sourceUrl = sourceStoryUrl,
                                            knownChapterUrls = knownChapterUrls,
                                            chapterUrls = links,
                                            chapterTitles = orderedChapterTitles,
                                            bookTitle = combinedTitle,
                                            author = combinedAuthor,
                                            description = combinedDescription,
                                            language = combinedLanguage,
                                            coverUrl = coverUrl,
                                            localCoverUri = localCoverUri,
                                            collageUrls = collageCoverLinks,
                                            collage = combineCovers,
                                            filename = combinedFilename,
                                            contentSelector = contentSelector,
                                            removeSelector = removeSelector,
                                            epub2 = epub2,
                                            includeImages = includeImages,
                                            imageProfile = imageProfile.name,
                                            readingStyle = readingStyle.name,
                                            customCss = customCss,
                                        ))
                                    activeProjectId = savedProjects.first().id
                                    projectName = selectedName
                                    projectMessage = "Saved project locally. EPUB files are not modified."
                                }.onFailure {
                                    projectMessage = "Could not save project: " +
                                        (it.message ?: "Unknown error").take(160)
                                }
                            },
                        ) { Text("Save project") }
                        OutlinedButton(
                            enabled = sourceStoryUrl.isNotBlank() && knownChapterUrls.isNotBlank() &&
                                !busy && !checkingUpdates,
                            onClick = {
                                val requested = sourceStoryUrl
                                val previouslyKnown = knownChapterUrls.lines()
                                    .map(String::trim).filter(String::isNotBlank)
                                checkingUpdates = true
                                foundNewChapters = emptyList()
                                latestInventory = ""
                                updateMessage = ""
                                previewScope.launch {
                                    val latest = withContext(Dispatchers.IO) {
                                        runCatching { NooirWebChapterDiscovery.inspect(requested) }
                                    }
                                    checkingUpdates = false
                                    if (sourceStoryUrl != requested || !combinePages) return@launch
                                    latest.onSuccess { discovery ->
                                        val newItems = runCatching {
                                            NooirWebChapterUpdates.findNew(
                                                previouslyKnown, discovery.chapters)
                                        }.getOrElse {
                                            updateMessage = "Update comparison failed: " +
                                                (it.message ?: "Invalid chapter inventory")
                                            return@onSuccess
                                        }
                                        foundNewChapters = newItems
                                        updatesPage = 0
                                        latestInventory = discovery.chapters.joinToString("\n") { it.url }
                                        updateMessage = if (newItems.isEmpty())
                                            "No newly listed public chapters found. Existing EPUB is unchanged."
                                        else
                                            "${newItems.size} new publicly listed chapter(s) found. Review before adding."
                                    }.onFailure {
                                        updateMessage = "Could not check public chapter list: " +
                                            (it.message ?: "Source not readable").take(200)
                                    }
                                }
                            },
                        ) { Text("Check new chapters") }
                    }
                    if (checkingUpdates) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("Checking public chapter metadata…")
                    }
                    if (updateMessage.isNotBlank()) Text(updateMessage)
                    if (foundNewChapters.isNotEmpty()) {
                        val selectedUpdates = foundNewChapters.count { it.included }
                        Text("New chapters: ${foundNewChapters.size} found · $selectedUpdates selected")
                        Text("Review each new chapter before adding it. Excluded chapters are " +
                            "marked as seen when you accept the update.")
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = {
                                foundNewChapters = NooirWebChapterPlan.chooseAll(
                                    foundNewChapters, true)
                            }) { Text("Select all new") }
                            OutlinedButton(onClick = {
                                foundNewChapters = NooirWebChapterPlan.chooseAll(
                                    foundNewChapters, false)
                            }) { Text("Select none") }
                        }
                        val updatesChunk = 10
                        val maxUpdatesPage = ((foundNewChapters.size - 1) / updatesChunk)
                            .coerceAtLeast(0)
                        val currentUpdatesPage = updatesPage.coerceIn(0, maxUpdatesPage)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(enabled = currentUpdatesPage > 0, onClick = {
                                updatesPage = currentUpdatesPage - 1
                            }) { Text("Previous new") }
                            Text("${currentUpdatesPage + 1}/${maxUpdatesPage + 1}")
                            OutlinedButton(enabled = currentUpdatesPage < maxUpdatesPage,
                                onClick = { updatesPage = currentUpdatesPage + 1 }) {
                                Text("Next new")
                            }
                        }
                        val updateFrom = currentUpdatesPage * updatesChunk
                        for (index in updateFrom until minOf(
                                foundNewChapters.size, updateFrom + updatesChunk)) {
                            val entry = foundNewChapters[index]
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Checkbox(
                                    checked = entry.included,
                                    onCheckedChange = {
                                        foundNewChapters = NooirWebChapterPlan.toggle(
                                            foundNewChapters, index)
                                    },
                                )
                                Text("${index + 1}. " + entry.title,
                                    modifier = Modifier.weight(1f), maxLines = 2)
                            }
                        }
                        OutlinedButton(enabled = selectedUpdates > 0 && !busy, onClick = {
                            runCatching {
                                val merged = NooirWebChapterUpdates.append(
                                    urls,
                                    orderedChapterTitles.lines(),
                                    foundNewChapters,
                                )
                                links = merged.urls.joinToString("\n")
                                orderedChapterTitles = merged.titles.joinToString("\n")
                                knownChapterUrls = latestInventory
                                foundNewChapters = emptyList()
                                updatesPage = 0
                                updateMessage = "Selected new chapters added; excluded ones marked as seen. " +
                                    "Save the project, then export a new EPUB file when ready."
                            }.onFailure {
                                updateMessage = "Could not add chapters: " +
                                    (it.message ?: "Chapter list too large").take(200)
                            }
                        }) { Text("Add new chapters to export list") }
                    }
                    for (saved in savedProjects) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Text(saved.name.take(40), modifier = Modifier.weight(1f), maxLines = 2)
                            OutlinedButton(enabled = !busy, onClick = {
                                activeProjectId = saved.id
                                projectName = saved.name
                                sourceStoryUrl = saved.sourceUrl
                                knownChapterUrls = saved.knownChapterUrls
                                links = saved.chapterUrls
                                orderedChapterTitles = saved.chapterTitles
                                combinedTitle = saved.bookTitle
                                combinedAuthor = saved.author
                                combinedDescription = saved.description
                                combinedLanguage = saved.language
                                coverUrl = saved.coverUrl
                                localCoverUri = saved.localCoverUri
                                collageCoverLinks = saved.collageUrls
                                combineCovers = saved.collage
                                combinedFilename = saved.filename
                                contentSelector = saved.contentSelector
                                removeSelector = saved.removeSelector
                                epub2 = saved.epub2
                                includeImages = saved.includeImages
                                imageProfile = NooirWebImageProfile.parse(saved.imageProfile)
                                readingStyle = ReflowEpubStyle.parse(saved.readingStyle)
                                customCss = saved.customCss
                                combinePages = true
                                foundNewChapters = emptyList()
                                latestInventory = ""
                                updateMessage = ""
                                projectMessage = "Loaded ${saved.name}. Review settings before exporting."
                            }) { Text("Load") }
                            TextButton(onClick = {
                                if (pendingDeletion == saved.id) {
                                    runCatching { savedProjects = projectStore.delete(saved.id) }
                                        .onSuccess {
                                            if (activeProjectId == saved.id) activeProjectId = ""
                                            projectMessage = "Deleted the saved project only; EPUBs are untouched."
                                        }.onFailure {
                                            projectMessage = "Could not delete project: " +
                                                (it.message ?: "Storage error").take(160)
                                        }
                                    pendingDeletion = ""
                                } else pendingDeletion = saved.id
                            }) { Text(if (pendingDeletion == saved.id) "Confirm" else "Delete") }
                        }
                    }
                    if (projectMessage.isNotBlank()) Text(projectMessage)
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { showAdvancedExtraction = !showAdvancedExtraction },
                    ) {
                        Text(if (showAdvancedExtraction) "Hide advanced extraction" else
                            "Advanced extraction · automatic by default")
                    }
                    if (showAdvancedExtraction) {
                    Text("Advanced chapter text extraction (optional). Leave both blank for automatic detection. " +
                        "Use simple CSS selectors such as article, #chapter-content or .reading-content. " +
                        "No scripts, complex selectors or login-only content.")
                    OutlinedTextField(
                        value = contentSelector,
                        onValueChange = {
                            contentSelector = it.take(160)
                            extractionPreview = ""
                        },
                        label = { Text("Read chapter content from CSS selector (optional)") },
                        placeholder = { Text("article .chapter-body") },
                        supportingText = { Text("Blank: automatic prose detection. Up to four simple selectors.") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = removeSelector,
                        onValueChange = {
                            removeSelector = it.take(160)
                            extractionPreview = ""
                        },
                        label = { Text("Remove unwanted elements (optional)") },
                        placeholder = { Text(".advertisement, .social-share") },
                        supportingText = { Text("Removes these elements after the chapter body is selected.") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    if (!NooirWebExtractRules.valid(contentSelector, removeSelector)) {
                        Text("Use tags, #ids, .classes and descendant spaces only. " +
                            "Pseudo-selectors, attributes and more than four alternatives are not supported.")
                    }
                    OutlinedButton(
                        enabled = !checkingExtraction && urls.isNotEmpty() &&
                            NooirWebExtractRules.valid(contentSelector, removeSelector),
                        onClick = {
                            val requested = urls.first()
                            val content = contentSelector.trim()
                            val removed = removeSelector.trim()
                            checkingExtraction = true
                            extractionPreview = ""
                            previewScope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    runCatching {
                                        val page = NooirWebArticle.load(requested)
                                        NooirWebArticle.previewChapterText(
                                            page, NooirWebArticle.title(page), content, removed)
                                    }
                                }
                                checkingExtraction = false
                                if (combinePages && links.lines().map(String::trim)
                                        .filter(String::isNotBlank).distinct().firstOrNull() == requested &&
                                    contentSelector.trim() == content && removeSelector.trim() == removed) {
                                    extractionPreview = result.getOrElse {
                                        "Preview failed: " + (it.message ?: "No readable public text").take(240)
                                    }.take(800)
                                }
                            }
                        },
                    ) { Text("Test extraction on first chapter") }
                    if (checkingExtraction) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("Checking first public chapter…")
                    }
                    if (extractionPreview.isNotBlank()) {
                        Text("Extraction preview (first 800 characters):")
                        Text(extractionPreview)
                    }
                    } // optional advanced extraction settings
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
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    androidx.compose.material3.Checkbox(
                        checked = epub2, onCheckedChange = { epub2 = it },
                    )
                    Text("EPUB 2 compatibility (unchecked: EPUB 3)")
                }
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    androidx.compose.material3.Checkbox(
                        checked = includeImages, onCheckedChange = { includeImages = it },
                    )
                    Text("Include inline chapter illustrations")
                }
                Text("EPUB text style")
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    for (preset in ReflowEpubStyle.entries) {
                        androidx.compose.material3.FilterChip(
                            selected = readingStyle == preset,
                            onClick = { readingStyle = preset },
                            label = {
                                Text(when (preset) {
                                    ReflowEpubStyle.STANDARD -> "Standard"
                                    ReflowEpubStyle.COMPACT -> "Compact"
                                    ReflowEpubStyle.LARGE_PRINT -> "Large"
                                })
                            },
                        )
                    }
                }
                Text("Styles are saved inside the EPUB. E-readers may override font size.")
                OutlinedTextField(
                    value = customCss,
                    onValueChange = { customCss = it.take(2000) },
                    label = { Text("Optional custom EPUB text CSS") },
                    placeholder = { Text("p { text-indent: 1em; line-height: 1.5; }") },
                    supportingText = { Text("Safe offline text styling only: body, p, headings, " +
                        "font size, spacing and alignment. No imports, URLs or complex selectors.") },
                    minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth(),
                )
                if (!NooirEpubCssRules.valid(customCss)) {
                    Text("Invalid custom CSS. Use simple tag { property: value; } rules " +
                        "without external resources or HTML.")
                }
                if (includeImages) {
                    Text("Chapter illustration size (covers remain unchanged)")
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        for (preset in NooirWebImageProfile.entries) {
                            androidx.compose.material3.FilterChip(
                                selected = imageProfile == preset,
                                onClick = { imageProfile = preset },
                                label = {
                                    Text(when (preset) {
                                        NooirWebImageProfile.ORIGINAL -> "Original"
                                        NooirWebImageProfile.BALANCED -> "Balanced"
                                        NooirWebImageProfile.SMALL -> "Small"
                                    })
                                },
                            )
                        }
                    }
                    Text("Balanced resizes oversized artwork to 1600 px; Small to 1000 px. " +
                        "Original preserves existing illustration bytes. PNG transparency is retained.")
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
                    val failureDetails = info.outputData.getString(NooirNovelBatchWorker.ERRORS)
                        .orEmpty()
                    if ((info.state == WorkInfo.State.FAILED ||
                            NooirDeviceWorkQueue.hasFailed(info)) &&
                        failureDetails.isNotBlank()) {
                        TextButton(onClick = {
                            val clip = android.content.ClipData.newPlainText(
                                "MangaNooir web importer error", failureDetails)
                            (context.getSystemService(Context.CLIPBOARD_SERVICE)
                                as android.content.ClipboardManager).setPrimaryClip(clip)
                        }) { Text("Copy error details") }
                    }
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
