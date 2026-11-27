package dev.nooir.desktop.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.nooir.desktop.suwayomi.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

private val coverSlots = Semaphore(3)

private suspend fun <T> request(block: () -> T): Result<T> = withContext(Dispatchers.IO) {
    try { Result.success(block()) }
    catch (e: CancellationException) { throw e }
    catch (e: Exception) { Result.failure(e) }
}

private fun decodeCover(bytes: ByteArray): ImageBitmap? {
    val input = ImageIO.createImageInputStream(ByteArrayInputStream(bytes)) ?: return null
    return input.use { stream ->
        val readers = ImageIO.getImageReaders(stream)
        if (!readers.hasNext()) return@use null
        val reader = readers.next()
        try {
            reader.input = stream
            val w = reader.getWidth(0)
            val h = reader.getHeight(0)
            require(w > 0 && h > 0 && w.toLong() * h <= 25_000_000L) { "Cover dimensions exceed limit" }
            val factor = maxOf(1, (maxOf(w, h) + 511) / 512)
            val param = reader.defaultReadParam
            param.setSourceSubsampling(factor, factor, 0, 0)
            reader.read(0, param).toComposeImageBitmap()
        } finally { reader.dispose() }
    }
}

@Composable
private fun Cover(client: SuwayomiClient, card: SuwayomiMangaCard, modifier: Modifier) {
    var bitmap by remember(client, card.id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(client, card.id) {
        bitmap = coverSlots.withPermit {
            request { client.thumbnail(card.id)?.let(::decodeCover) }.getOrNull()
        }
    }
    Surface(modifier, color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
        val img = bitmap
        if (img != null) Image(img, contentDescription = card.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
            Text(card.title, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

// Only one page decode/fetch runs at a time. Cancelled effects cannot
// publish into the next chapter or page. Do not silently mutate progress.
private val pageSlots = Semaphore(1)

private fun decodeReaderPage(bytes: ByteArray): ImageBitmap {
    val input = ImageIO.createImageInputStream(ByteArrayInputStream(bytes))
        ?: error("Unable to read page image")
    return input.use { stream ->
        val readers = ImageIO.getImageReaders(stream)
        require(readers.hasNext()) { "Unsupported page image format" }
        val reader = readers.next()
        try {
            reader.input = stream
            val w = reader.getWidth(0)
            val h = reader.getHeight(0)
            require(w > 0 && h > 0 && w.toLong() * h <= 25_000_000L) { "Page dimensions exceed limit" }
            val factor = maxOf(1, (maxOf(w, h) + 4095) / 4096)
            val param = reader.defaultReadParam
            param.setSourceSubsampling(factor, factor, 0, 0)
            reader.read(0, param).toComposeImageBitmap()
        } finally { reader.dispose() }
    }
}

@Composable
private fun Reader(client: SuwayomiClient, card: SuwayomiMangaCard, chapter: SuwayomiChapter, back: () -> Unit) {
    var manifestResult by remember(card.id, chapter.id) { mutableStateOf<Result<SuwayomiOnlineChapter>?>(null) }
    var page by remember(card.id, chapter.id) { mutableStateOf(0) }
    var openRevision by remember(card.id, chapter.id) { mutableStateOf(0) }
    var pageRevision by remember(card.id, chapter.id) { mutableStateOf(0) }
    var saveOpen by remember(card.id, chapter.id) { mutableStateOf(false) }
    var saving by remember(card.id, chapter.id) { mutableStateOf(false) }
    var savedPage by remember(card.id, chapter.id) { mutableStateOf<Int?>(null) }
    var saveStatus by remember(card.id, chapter.id) { mutableStateOf("") }
    val operations = LocalOperations.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(client, card.id, chapter.id, openRevision) {
        manifestResult = null
        val result = request { client.openOnlineChapter(card.id, chapter.id) }
        result.getOrNull()?.let { page = it.lastPageRead.coerceIn(0, it.pageCount - 1); savedPage = it.lastPageRead }
        manifestResult = result
    }
    val result = manifestResult
    val manifest = result?.getOrNull()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = back, enabled = !saving) { Text("‹ Chapters") }
            Text(card.title + " · " + chapter.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (saving) Text("Saving…", style = MaterialTheme.typography.labelSmall)
        }
        if (result == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (manifest == null) {
            Text("Unable to open chapter. Check the installed source and server connection.", color = MaterialTheme.colorScheme.error)
            Button(onClick = { openRevision++ }) { Text("Retry chapter") }
        } else {
            var imageResult by remember(manifest, page, pageRevision) { mutableStateOf<Result<ImageBitmap>?>(null) }
            LaunchedEffect(client, manifest, page, pageRevision) {
                val index = page
                imageResult = pageSlots.withPermit { request { decodeReaderPage(client.onlinePage(manifest, index)) } }
            }
            if (manifest.syncConflict != null) Text("Reading-position conflict reported by server. No position is being written.")
            val rendered = imageResult
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when {
                    rendered == null -> CircularProgressIndicator()
                    rendered.isFailure -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Unable to display this page.")
                        Button(onClick = { pageRevision++ }) { Text("Retry page") }
                    }
                    else -> Image(rendered.getOrThrow(), contentDescription = "Page " + (page + 1),
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
            }
            Box(Modifier.fillMaxWidth()) {
                Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { page-- }, enabled = !saving && page > 0 && rendered != null) { Text("Previous") }
                    Text("${page + 1} / ${manifest.pageCount}")
                    TextButton(onClick = { page++ }, enabled = !saving && page + 1 < manifest.pageCount && rendered != null) { Text("Next") }
                }
                TextButton(onClick = { saveOpen = true }, modifier = Modifier.align(Alignment.CenterEnd),
                    enabled = !saving && rendered?.isSuccess == true &&
                    manifest.syncConflict == null && page > (savedPage ?: Int.MAX_VALUE)) { Text("Save position") }
            }
            Text(saveStatus.ifBlank { "Progress is saved only on request." }, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (saveOpen) AlertDialog(onDismissRequest = { saveOpen = false },
                title = { Text("Save reading position?") },
                text = { Text("Save page " + (page + 1) + " to your local reading service? A known later position will not be overwritten. Concurrent writers may still race.") },
                confirmButton = { Button(onClick = {
                    val target = page
                    saveOpen = false; saving = true; saveStatus = "Checking position…"
                    scope.launchProtected(operations) {
                        try {
                            val saved = withContext(Dispatchers.IO) { client.saveOnlinePosition(manifest, target) }
                            savedPage = saved
                            saveStatus = "Saved page " + (saved + 1)
                            if (System.getenv("NOOIR_COMPOSE_QA_CAPTURE") == "1") println("QA_POSITION_SAVED")
                        } catch(e: CancellationException) { throw e }
                        catch(e: Exception) { saveStatus = "Save not confirmed. Reopen chapter to check the current position." }
                        finally { saving = false }
                    }
                }) { Text("Save") } },
                dismissButton = { TextButton(onClick = { saveOpen = false }) { Text("Cancel") } })
        }
    }
}

@Composable
private fun Details(client: SuwayomiClient, card: SuwayomiMangaCard, backLabel: String = "‹ Library", refreshAllowed: Boolean = false, back: () -> Unit) {
    var state by remember(card.id) { mutableStateOf<Result<SuwayomiManga>?>(null) }
    var opened by remember(card.id) { mutableStateOf<SuwayomiChapter?>(null) }
    var refreshRevision by remember(card.id) { mutableStateOf(0) }
    var reloadRevision by remember(card.id) { mutableStateOf(0) }
    var consumedRefresh by remember(card.id) { mutableStateOf(0) }
    var selectionMode by remember(card.id) { mutableStateOf(false) }
    var selectedIds by remember(card.id) { mutableStateOf(setOf<Int>()) }
    var exportOpen by remember(card.id) { mutableStateOf(false) }
    var readFilter by remember(card.id) { mutableStateOf("All") }
    var downloadFilter by remember(card.id) { mutableStateOf("All") }
    var bookmarkFilter by remember(card.id) { mutableStateOf("All") }
    var chapterSort by remember(card.id) { mutableStateOf("Source order") }
    var descending by remember(card.id) { mutableStateOf(false) }
    var filterOpen by remember(card.id) { mutableStateOf(false) }
    var flagAction by remember(card.id) { mutableStateOf<String?>(null) }
    var actionsOpen by remember(card.id) { mutableStateOf(false) }
    var downloadConfirm by remember(card.id) { mutableStateOf<List<Int>>(emptyList()) }
    var downloadStatus by remember(card.id) { mutableStateOf("") }
    var downloading by remember(card.id) { mutableStateOf(false) }
    val operations = LocalOperations.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(client, card.id, refreshRevision, reloadRevision) {
        state = null
        state = request {
            if (refreshRevision > consumedRefresh) client.refreshManga(card.id)
            client.manga(card.id)
        }
        consumedRefresh = refreshRevision
    }
    val chapter = opened
    if (chapter != null) {
        Reader(client, card, chapter) { opened = null; reloadRevision++ }
        return
    }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        TextButton(onClick = back, enabled = !downloading) { Text(backLabel) }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Cover(client, card, Modifier.width(128.dp).height(184.dp))
            Column(Modifier.weight(1f)) {
                Text(card.title, style = MaterialTheme.typography.headlineMedium)
                Text(card.author.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Text(card.description.orEmpty(), maxLines = 6, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Chapters", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if(selectionMode) Box {
                TextButton(onClick={actionsOpen=true},enabled=selectedIds.isNotEmpty() && !downloading){Text("Chapter actions")}
                DropdownMenu(actionsOpen,onDismissRequest={actionsOpen=false}){
                    listOf("Mark read","Mark unread","Bookmark","Remove bookmark").forEach{action->
                        DropdownMenuItem(text={Text(action)},onClick={actionsOpen=false;flagAction=action})
                    }
                }
            }
            TextButton(onClick = { filterOpen = true }, enabled = state?.isSuccess == true && !downloading) { Text("Filter / sort") }
            TextButton(onClick = { refreshRevision++ }, enabled = state != null && !downloading) { Text("Refresh chapters") }
        }
        val result = state
        if (result == null) CircularProgressIndicator()
        else if (result.isFailure) Text("Unable to load chapters. Return to the library and retry.", color = MaterialTheme.colorScheme.error)
        else {
            val chapters = result.getOrThrow().chapters
            val visible = remember(chapters,readFilter,downloadFilter,bookmarkFilter,chapterSort,descending) {
                val filtered = chapters.filter {
                    (readFilter=="All" || (readFilter=="Read" && it.read==true) || (readFilter=="Unread" && it.read==false)) &&
                    (downloadFilter=="All" || (downloadFilter=="Downloaded" && it.downloaded) || (downloadFilter=="Not downloaded" && !it.downloaded)) &&
                    (bookmarkFilter=="All" || (bookmarkFilter=="Bookmarked" && it.bookmarked==true) || (bookmarkFilter=="Not bookmarked" && it.bookmarked==false))
                }
                val sorted = when(chapterSort) {
                    "Chapter number" -> filtered.sortedWith(compareBy<SuwayomiChapter>{it.chapterNumber}.thenBy{it.sourceOrder})
                    "Upload date" -> filtered.sortedWith(compareBy<SuwayomiChapter>{it.uploadDate ?: 0L}.thenBy{it.sourceOrder})
                    else -> filtered
                }
                if(descending) sorted.reversed() else sorted
            }
            if(filterOpen) AlertDialog(onDismissRequest={filterOpen=false},title={Text("Chapter filters and sorting")},
                text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Text("Reading status")
                    ExportChoice(readFilter,listOf("All","Unread","Read"),true){readFilter=it;selectedIds=emptySet()}
                    Text("Download status")
                    ExportChoice(downloadFilter,listOf("All","Downloaded","Not downloaded"),true){downloadFilter=it;selectedIds=emptySet()}
                    Text("Bookmarks")
                    ExportChoice(bookmarkFilter,listOf("All","Bookmarked","Not bookmarked"),true){bookmarkFilter=it;selectedIds=emptySet()}
                    TextButton(onClick={readFilter="All";downloadFilter="All";bookmarkFilter="All";selectedIds=emptySet()}){Text("Clear filters")}
                    Text("Sort by")
                    ExportChoice(chapterSort,listOf("Source order","Chapter number","Upload date"),true){chapterSort=it;selectedIds=emptySet()}
                    ConversionToggle("Reverse order",descending,true){descending=it;selectedIds=emptySet()}
                    Text("Changing filters or sorting clears chapter selection.",style=MaterialTheme.typography.bodySmall)
                }},confirmButton={TextButton(onClick={filterOpen=false}){Text("Done")}})
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if(readFilter=="All" && downloadFilter=="All" && bookmarkFilter=="All") "${chapters.size} chapters" else "${visible.size} / ${chapters.size} chapters", Modifier.weight(1f))
                val next = chapters.firstOrNull { it.id == result.getOrThrow().firstUnreadId }
                if (!selectionMode && next != null) TextButton(onClick = { opened = next }) { Text("Continue reading") }
                TextButton(onClick = { selectionMode = !selectionMode; selectedIds = emptySet() }, enabled = !downloading) {
                    Text(if (selectionMode) "Cancel selection" else "Select chapters")
                }
                if (selectionMode) {
                    Text("${selectedIds.size} selected")
                    TextButton(onClick={selectedIds=visible.take(500).map{it.id}.toSet()},enabled=!downloading){Text(if(visible.size>500) "Select first 500" else "Select all")}
                    TextButton(onClick={downloadConfirm=visible.filter{it.id in selectedIds && !it.downloaded}.map{it.id}},enabled=!downloading && visible.any{it.id in selectedIds && !it.downloaded}){Text("Download")}
                    TextButton(onClick = { exportOpen = true }, enabled = selectedIds.isNotEmpty() && !downloading) { Text("Export") }
                }
            }
            if (exportOpen) ComposeExport(card, visible.filter { it.id in selectedIds }) { exportOpen = false }
            val action=flagAction
            if(action!=null) AlertDialog(onDismissRequest={flagAction=null},title={Text(action+"?")},
                text={Text("Apply to "+selectedIds.size+" selected chapters? Existing page positions and downloaded files are retained. Marking read may sync to connected readers.")},
                confirmButton={Button(onClick={
                    val ids=visible.filter{it.id in selectedIds}.map{it.id}
                    flagAction=null;downloading=true
                    scope.launchProtected(operations){
                        try {
                            withContext(Dispatchers.IO){
                                when(action){
                                    "Mark read"->client.updateChapterFlags(card.id,ids,read=true)
                                    "Mark unread"->client.updateChapterFlags(card.id,ids,read=false)
                                    "Bookmark"->client.updateChapterFlags(card.id,ids,bookmarked=true)
                                    else->client.updateChapterFlags(card.id,ids,bookmarked=false)
                                }
                            }
                            downloadStatus="Updated "+ids.size+" chapters."
                        }catch(e:CancellationException){throw e}
                        catch(e:Exception){downloadStatus="Chapter changes not fully confirmed. Reload before retrying."}
                        finally{selectedIds=emptySet();selectionMode=false;reloadRevision++;downloading=false}
                    }
                }){Text("Apply")}},dismissButton={TextButton(onClick={flagAction=null}){Text("Cancel")}})
            if(downloadStatus.isNotBlank()) Text(downloadStatus,style=MaterialTheme.typography.bodySmall)
            if(downloadConfirm.isNotEmpty()) AlertDialog(onDismissRequest={downloadConfirm=emptyList()},title={Text("Download chapters?")},
                text={Text("Queue "+downloadConfirm.size+" chapters in the local reading service? Download completion is separate from queue acceptance.")},
                confirmButton={Button(onClick={
                    val ids=downloadConfirm.toList();downloadConfirm=emptyList();downloading=true
                    scope.launchProtected(operations){
                        try {
                            withContext(Dispatchers.IO){client.enqueue(ids)}
                            downloadStatus="Queued "+ids.size+" chapters. Reload chapter status after downloads finish."
                            reloadRevision++
                        }catch(e:CancellationException){throw e}
                        catch(e:Exception){downloadStatus="Download request not confirmed. Check the reading service before retrying."}
                        finally{downloading=false}
                    }
                }){Text("Queue downloads")}},dismissButton={TextButton(onClick={downloadConfirm=emptyList()}){Text("Cancel")}})
            if (visible.isEmpty()) Text("No chapters match this filter.")
            LazyColumn(Modifier.weight(1f)) {
                items(visible, key = { it.id }) { chapter ->
                    ListItem(headlineContent = { Text(chapter.name) },
                        supportingContent = { Text(listOfNotNull(if (chapter.downloaded) "Downloaded" else "Online",
                            chapter.read?.let { if (it) "Read" else "Unread" }, if(chapter.bookmarked==true) "Bookmarked" else null, chapter.lastPageRead?.takeIf { it > 0 }?.let { "Saved page " + (it + 1) }).joinToString(" · ")) },
                        leadingContent = if (selectionMode) {
                            { Checkbox(checked = chapter.id in selectedIds, onCheckedChange = { checked ->
                                if (checked && selectedIds.size < 500) selectedIds = selectedIds + chapter.id
                                if (!checked) selectedIds = selectedIds - chapter.id
                            }, enabled = !downloading && (chapter.id in selectedIds || selectedIds.size < 500)) }
                        } else null,
                        trailingContent = { Row {
                            if(!selectionMode && !chapter.downloaded) TextButton(onClick={downloadConfirm=listOf(chapter.id)},enabled=!downloading){Text("Download")}
                            TextButton(onClick = { opened = chapter }, enabled = !selectionMode && !downloading) { Text("Read") }
                        } })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun Library(client: SuwayomiClient) {
    var page by remember { mutableStateOf<SuwayomiLibraryPage?>(null) }
    var selected by remember { mutableStateOf<SuwayomiMangaCard?>(null) }
    var error by remember { mutableStateOf(false) }
    var offset by remember { mutableStateOf(0) }
    var revision by remember { mutableStateOf(0) }
    var search by remember { mutableStateOf("") }
    var categories by remember { mutableStateOf<List<SuwayomiCategory>>(emptyList()) }
    var categoryId by remember { mutableStateOf<Int?>(null) }
    var categoryError by remember { mutableStateOf(false) }
    LaunchedEffect(client, revision) {
        request { client.categories() }.fold({ values ->
            categories = values; categoryError = false
            if (categoryId != null && values.none { it.id == categoryId }) { categoryId = null; offset = 0 }
        }, { categoryError = true })
    }
    LaunchedEffect(client, offset, revision, categoryId) {
        page = null
        error = false
        request { client.library(offset, 100, categoryId) }.fold({ page = it }, { error = true })
    }
    val card = selected
    if (card != null) Details(client, card) { selected = null }
    else Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Library", style = MaterialTheme.typography.headlineMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(search, { search = it }, label = { Text("Filter this page") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { revision++ }) { Text("Refresh") }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = categoryId == null, onClick = { categoryId = null; offset = 0 }, label = { Text("All") })
            categories.forEach { category ->
                FilterChip(selected = categoryId == category.id, onClick = { categoryId = category.id; offset = 0 }, label = { Text(category.name) })
            }
        }
        if (categoryError) Text("Categories unavailable; refresh to retry.", style = MaterialTheme.typography.labelSmall)
        val current = page
        when {
            error -> {
                Text("Can't connect to your local Suwayomi. Start it in the existing desktop preview, then refresh.", color = MaterialTheme.colorScheme.error)
                Button(onClick = { revision++ }) { Text("Retry") }
            }
            current == null -> CircularProgressIndicator()
            else -> {
                Text("${current.totalCount} library titles")
                val cards = current.mangas.filter { it.title.contains(search, ignoreCase = true) }
                if (cards.isEmpty()) Text("No titles on this page")
                LazyVerticalGrid(GridCells.Adaptive(150.dp), modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(cards, key = { it.id }) { item ->
                        Card(onClick = { selected = item }) {
                            Box(Modifier.fillMaxWidth().aspectRatio(0.70f)) {
                                Cover(client, item, Modifier.fillMaxSize())
                                item.unreadCount?.takeIf { it > 0 }?.let { count ->
                                    Surface(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp), shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer) {
                                        Text(count.toString(), Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                            Text(item.title, Modifier.padding(10.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { offset = maxOf(0, offset - 100) }, enabled = offset > 0) { Text("Previous") }
                    Text("Page ${offset / 100 + 1}")
                    TextButton(onClick = { offset += 100 }, enabled = offset + 100 < current.totalCount) { Text("Next") }
                }
            }
        }
    }
}

private data class BrowseRequest(val mode: SuwayomiBrowseMode, val page: Int = 1, val term: String? = null)

@Composable
private fun SourceBrowser(client: SuwayomiClient, source: SuwayomiSource, back: () -> Unit) {
    var search by remember(source.id) { mutableStateOf("") }
    var query by remember(source.id) { mutableStateOf(BrowseRequest(SuwayomiBrowseMode.POPULAR)) }
    var revision by remember(source.id) { mutableStateOf(0) }
    var result by remember(source.id, query, revision) { mutableStateOf<Result<SuwayomiBrowsePage>?>(null) }
    var selected by remember(source.id) { mutableStateOf<SuwayomiMangaCard?>(null) }
    LaunchedEffect(client, source.id, query, revision) {
        val q = query
        result = request { client.browse(source.id, q.mode, q.page, q.term) }
    }
    val manga = selected
    if (manga != null) {
        Details(client, manga, backLabel = "‹ Browse", refreshAllowed = true) { selected = null }
        return
    }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        TextButton(onClick = back) { Text("‹ Sources") }
        Text(source.displayName, style = MaterialTheme.typography.headlineMedium)
        if (source.contentWarning.isNotBlank()) Text(source.contentWarning, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = query.mode == SuwayomiBrowseMode.POPULAR,
                onClick = { query = BrowseRequest(SuwayomiBrowseMode.POPULAR) }, label = { Text("Popular") })
            if (source.supportsLatest) FilterChip(selected = query.mode == SuwayomiBrowseMode.LATEST,
                onClick = { query = BrowseRequest(SuwayomiBrowseMode.LATEST) }, label = { Text("Latest") })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(search, { search = it.take(200) }, label = { Text("Search this source") }, singleLine = true, modifier = Modifier.weight(1f))
            Button(onClick = { query = BrowseRequest(SuwayomiBrowseMode.SEARCH, term = search.trim()); revision++ },
                enabled = search.isNotBlank() && search.none { it.code < 32 }) { Text("Search") }
        }
        Spacer(Modifier.height(12.dp))
        val current = result
        when {
            current == null -> CircularProgressIndicator()
            current.isFailure -> {
                Text("Could not load this source. Check extension compatibility and network access.", color = MaterialTheme.colorScheme.error)
                Button(onClick = { revision++ }) { Text("Retry") }
            }
            else -> {
                val page = current.getOrThrow()
                if (page.mangas.isEmpty()) Text("No titles found")
                LazyVerticalGrid(GridCells.Adaptive(150.dp), modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(page.mangas.distinctBy { it.id }, key = { it.id }) { item ->
                        Card(onClick = { selected = item }) {
                            Cover(client, item, Modifier.fillMaxWidth().aspectRatio(0.70f))
                            Text(item.title, Modifier.padding(10.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (item.inLibrary) Text("In library", Modifier.padding(horizontal = 10.dp), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { query = query.copy(page = query.page - 1) }, enabled = query.page > 1) { Text("Previous") }
                    Text("Page ${page.page}")
                    TextButton(onClick = { query = query.copy(page = query.page + 1) }, enabled = page.hasNextPage && query.page < 10_000) { Text("Next") }
                }
            }
        }
    }
}

@Composable
private fun Sources(client: SuwayomiClient) {
    var result by remember { mutableStateOf<Result<SuwayomiSourcePage>?>(null) }
    var revision by remember { mutableStateOf(0) }
    var offset by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf<SuwayomiSource?>(null) }
    var extensionsOpen by remember { mutableStateOf(false) }
    if (extensionsOpen) {
        ComposeExtensions { extensionsOpen = false; revision++ }
        return
    }
    LaunchedEffect(client, revision, offset) { result = null; result = request { client.sources(offset, 100) } }
    val source = selected
    if (source != null) {
        SourceBrowser(client, source) { selected = null }
        return
    }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Browse", style = MaterialTheme.typography.headlineMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Installed sources", Modifier.weight(1f))
            TextButton(onClick = { extensionsOpen = true }) { Text("Extensions") }
            TextButton(onClick = { revision++ }) { Text("Reload") }
        }
        val current = result
        when {
            current == null -> CircularProgressIndicator()
            current.isFailure -> Text("Local server unavailable.", color = MaterialTheme.colorScheme.error)
            else -> {
                val page = current.getOrThrow()
                Text("${page.totalCount} sources")
                LazyColumn(Modifier.weight(1f)) {
                    items(page.sources, key = { it.id }) {
                        ListItem(headlineContent = { Text(it.displayName) }, supportingContent = { Text(it.language) },
                            trailingContent = { TextButton(onClick = { selected = it }) { Text("Browse") } })
                        HorizontalDivider()
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { offset = maxOf(0, offset - 100) }, enabled = offset > 0) { Text("Previous") }
                    Text("Page ${offset / 100 + 1}")
                    TextButton(onClick = { offset += 100 }, enabled = offset + 100 < page.totalCount) { Text("Next") }
                }
            }
        }
    }
}

@Composable
private fun ChapterFeed(client: SuwayomiClient, history: Boolean) {
    var result by remember(history) { mutableStateOf<Result<SuwayomiFeedPage>?>(null) }
    var offset by remember(history) { mutableStateOf(0) }
    var revision by remember(history) { mutableStateOf(0) }
    var selected by remember(history) { mutableStateOf<SuwayomiFeedEntry?>(null) }
    LaunchedEffect(client,history,offset,revision) {
        result = null
        result = request { client.chapterFeed(history,offset,50) }
        if (result?.isSuccess == true && System.getenv("NOOIR_COMPOSE_QA_CAPTURE") == "1") {
            println(if (history) "QA_HISTORY_READY" else "QA_UPDATES_READY")
        }
    }
    val entry = selected
    if (entry != null) {
        Reader(client,entry.manga,entry.chapter) { selected = null; revision++ }
        return
    }
    Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text(if(history) "History" else "Updates",style=MaterialTheme.typography.headlineMedium,modifier=Modifier.weight(1f))
            OutlinedButton(onClick={revision++}) {Text("Reload")}
        }
        Text(if(history) "Previously read library chapters, most recent first."
            else "Recently fetched library chapters. Reload reads the local catalogue; it does not refresh remote sources.",
            style=MaterialTheme.typography.bodySmall)
        val state=result
        val data=state?.getOrNull()
        when {
            state==null -> CircularProgressIndicator()
            data==null -> Column { Text("Unable to load chapter records.",color=MaterialTheme.colorScheme.error); Text(state.exceptionOrNull()?.message?.take(240) ?: "Unknown response error",style=MaterialTheme.typography.bodySmall) }
            else -> {
                if(data.entries.isEmpty()) Text(if(history) "No saved reading history yet." else "No cached chapter updates yet.")
                LazyColumn(Modifier.weight(1f)) {
                    items(data.entries,key={it.chapter.id}) { row ->
                        ListItem(leadingContent={Cover(client,row.manga,Modifier.width(54.dp).height(76.dp))},
                            headlineContent={Text(row.manga.title,maxLines=1,overflow=TextOverflow.Ellipsis)},
                            supportingContent={Column {
                                Text(row.chapter.name,maxLines=2,overflow=TextOverflow.Ellipsis)
                                Text(if(row.read) "Read" else if(history && row.pageCount>0)
                                    "Saved page " + (row.lastPageRead.coerceIn(0,row.pageCount-1)+1) + " / " + row.pageCount
                                    else if(row.chapter.downloaded) "Downloaded" else "Unread")
                            }},
                            trailingContent={TextButton(onClick={selected=row}) {Text(if(history) "Continue" else "Read")}})
                        HorizontalDivider()
                    }
                }
                Row(verticalAlignment=Alignment.CenterVertically) {
                    TextButton(onClick={offset=maxOf(0,offset-50)},enabled=offset>0){Text("Previous")}
                    Text("Page " + (offset/50+1) + " · " + data.totalCount + " records")
                    TextButton(onClick={offset+=50},enabled=offset+50<data.totalCount){Text("Next")}
                }
            }
        }
    }
}

/** Lightweight vector navigation icons without adding a large icon dependency. */
@Composable
private fun NooirNavigationIcon(name: String) {
    val ink=MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.size(24.dp)) {
        val u=size.width/24f
        val width=1.9f*u
        fun line(x:Float,y:Float,x2:Float,y2:Float)=drawLine(ink,Offset(x*u,y*u),Offset(x2*u,y2*u),width,StrokeCap.Round)
        fun rect(x:Float,y:Float,w:Float,h:Float)=drawRect(ink,Offset(x*u,y*u),Size(w*u,h*u),style=Stroke(width))
        when(name) {
            "Library" -> {rect(3f,4f,4f,16f);rect(9f,4f,4f,16f);line(16f,5f,20f,19f)}
            "Updates" -> {drawCircle(ink,8f*u,Offset(12f*u,12f*u),style=Stroke(width));line(12f,7f,12f,12f);line(12f,12f,16f,14f)}
            "History" -> {drawArc(ink,45f,285f,false,Offset(4f*u,4f*u),Size(16f*u,16f*u),style=Stroke(width));line(4f,4f,4f,10f);line(4f,10f,10f,10f);line(12f,8f,12f,12f);line(12f,12f,15f,15f)}
            "Browse" -> {drawCircle(ink,8f*u,Offset(12f*u,12f*u),style=Stroke(width));line(8f,16f,11f,9f);line(11f,9f,16f,8f);line(16f,8f,13f,15f);line(13f,15f,8f,16f)}
            else -> {for(x in listOf(5f,12f,19f)) drawCircle(ink,1.6f*u,Offset(x*u,12f*u))}
        }
    }
}

fun main() = application {
    val backend = remember { ComposeBackendSession() }
    val operations = remember { OperationGuard() }
    var closeBlocked by remember { mutableStateOf(false) }
    val lifecycle = rememberCoroutineScope()
    val sessionConversions = remember { SessionConversions(lifecycle) }
    var closing by remember { mutableStateOf(false) }
    Window(onCloseRequest = {
        if (operations.active || sessionConversions.active) {
            closeBlocked = true
        } else if (!closing) {
            closing = true
            lifecycle.launch {
                try { withContext(Dispatchers.IO) { backend.close() } }
                finally { exitApplication() }
            }
        }
    }, title = "MihonNooir · Compose experiment",
        state = rememberWindowState(width = 1040.dp, height = 720.dp)) {
        var section by remember { mutableStateOf("Library") }
        var activityOpen by remember { mutableStateOf(false) }
        var batchOpen by remember { mutableStateOf(false) }
        var dark by remember { mutableStateOf(true) }
        val client = remember { SuwayomiClient() }
        var backendState by remember { mutableStateOf<BackendState>(BackendState.Checking) }
        var setupRevision by remember { mutableStateOf(0) }
        LaunchedEffect(setupRevision) {
            backendState = BackendState.Checking
            backendState = try { operations.protect { withContext(Dispatchers.IO) { backend.connectOrStartCached() } } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { BackendState.Unavailable("Local reading service could not start. Check the existing server and retry.") }
        }
        CompositionLocalProvider(LocalOperations provides operations, LocalSessionConversions provides sessionConversions) {
        MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
            if (closeBlocked) AlertDialog(onDismissRequest = { closeBlocked = false },
                title = { Text("Operation in progress") },
                text = { Text("Finish the active operation before closing MihonNooir. For a batch, use Pause after current.") },
                confirmButton = { TextButton(onClick = { closeBlocked = false }) { Text("OK") } })
            Surface(Modifier.fillMaxSize()) {
                if (closing) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Closing MihonNooir…") }
                } else if (backendState !is BackendState.Ready) {
                    ComposeBackendSetup(backend, backendState, { backendState = it }) { setupRevision++ }
                } else Row(Modifier.fillMaxSize()) {
                    NavigationRail {
                        Spacer(Modifier.height(16.dp))
                        Text("Nooir", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(20.dp))
                        listOf("Library", "Updates", "History", "Browse", "More").forEach { name ->
                            NavigationRailItem(selected = section == name, onClick = { if (!operations.active) section = name }, enabled = !operations.active,
                                icon = { NooirNavigationIcon(name) }, label = { Text(name) })
                        }
                    }
                    VerticalDivider()
                    if(activityOpen) SessionActivity({activityOpen=false},{activityOpen=false;batchOpen=true})
                    if(batchOpen) ComposeQueue { batchOpen=false }
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        when (section) {
                            "Library" -> Library(client)
                            "Updates" -> ChapterFeed(client,false)
                            "History" -> ChapterFeed(client,true)
                            "Browse" -> Sources(client)
                            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
                                Text("More", style = MaterialTheme.typography.headlineMedium)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Dark theme"); Switch(dark, { dark = it })
                                }
                                var converterOpen by remember { mutableStateOf(false) }
                                ListItem(headlineContent = { Text("Universal Converter") },
                                    supportingContent = { Text("Convert local manga, PDF and EPUB files") },
                                    trailingContent = { TextButton(onClick = { converterOpen = true }) { Text("Open") } })
                                if (converterOpen) ComposeConverter { converterOpen = false }
                                var webOpen by remember { mutableStateOf(false) }
                                ListItem(headlineContent = { Text("Web to EPUB") },
                                    supportingContent = { Text("Analyze public chapters and create an EPUB") },
                                    trailingContent = { TextButton(onClick = { webOpen = true }) { Text("Open") } })
                                if (webOpen) ComposeWeb { webOpen = false }
                                ListItem(headlineContent = { Text("Activity Queue") },
                                    supportingContent = { Text(if(sessionConversions.active) "Conversion running · continue browsing" else "Conversions and completed books") },
                                    trailingContent = { TextButton(onClick = { activityOpen = true }) { Text("Open") } })
                                var backupOpen by remember { mutableStateOf(false) }
                                ListItem(headlineContent = { Text("Backup & restore") },
                                    supportingContent = { Text("Library backups and compatible extension recovery") },
                                    trailingContent = { TextButton(onClick = { backupOpen = true }) { Text("Open") } })
                                if (backupOpen) ComposeBackup { backupOpen = false }
                                var transferOpen by remember { mutableStateOf(false) }
                                ListItem(headlineContent = { Text("Send to XTEink") },
                                    supportingContent = { Text("Transfer a local book to a compatible Folio Nooir reader") },
                                    trailingContent = { TextButton(onClick = { transferOpen = true }) { Text("Open") } })
                                if (transferOpen) ComposeTransfer { transferOpen = false }
                                var downloadsOpen by remember { mutableStateOf(false) }
                                ListItem(headlineContent={Text("Download queue")},
                                    supportingContent={Text("Actual chapter downloads from the reading service")},
                                    trailingContent={TextButton(onClick={downloadsOpen=true}){Text("Open")}})
                                if(downloadsOpen) ComposeDownloads(client){downloadsOpen=false}
                                val connection = backendState as? BackendState.Ready
                                ListItem(headlineContent = { Text("Reading service") },
                                    supportingContent = { Text(if (connection?.owned == true) "Managed by MihonNooir" else "Connected to existing local service") },
                                    trailingContent = { TextButton(onClick = { if (!operations.active) setupRevision++ }, enabled = !operations.active && !sessionConversions.active) { Text("Reconnect") } })
                                Text("Experimental desktop interface. Conversion jobs and chapter downloads are separate.")
                            }
                        }
                    }
                }
            }
        }
    }
}
}
