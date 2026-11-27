package dev.nooir.desktop.compose

import androidx.compose.foundation.Image
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
    LaunchedEffect(client, card.id, chapter.id, openRevision) {
        manifestResult = null
        val result = request { client.openOnlineChapter(card.id, chapter.id) }
        result.getOrNull()?.let { page = it.lastPageRead.coerceIn(0, it.pageCount - 1) }
        manifestResult = result
    }
    val result = manifestResult
    val manifest = result?.getOrNull()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = back) { Text("‹ Chapters") }
            Text(card.title + " · " + chapter.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { page-- }, enabled = page > 0 && rendered != null) { Text("Previous") }
                Text("${page + 1} / ${manifest.pageCount}")
                TextButton(onClick = { page++ }, enabled = page + 1 < manifest.pageCount && rendered != null) { Text("Next") }
            }
            Text("Read-only experiment · server progress is not automatically saved", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun Details(client: SuwayomiClient, card: SuwayomiMangaCard, backLabel: String = "‹ Library", refreshAllowed: Boolean = false, back: () -> Unit) {
    var state by remember(card.id) { mutableStateOf<Result<SuwayomiManga>?>(null) }
    var opened by remember(card.id) { mutableStateOf<SuwayomiChapter?>(null) }
    var refreshRevision by remember(card.id) { mutableStateOf(0) }
    var selectionMode by remember(card.id) { mutableStateOf(false) }
    var selectedIds by remember(card.id) { mutableStateOf(setOf<Int>()) }
    var exportOpen by remember(card.id) { mutableStateOf(false) }
    LaunchedEffect(client, card.id, refreshRevision) {
        state = null
        state = request {
            if (refreshRevision > 0) client.refreshManga(card.id)
            client.manga(card.id)
        }
    }
    val chapter = opened
    if (chapter != null) {
        Reader(client, card, chapter) { opened = null }
        return
    }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        TextButton(onClick = back) { Text(backLabel) }
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
            if (refreshAllowed) TextButton(onClick = { refreshRevision++ }, enabled = state != null) { Text("Refresh chapters") }
        }
        val result = state
        if (result == null) CircularProgressIndicator()
        else if (result.isFailure) Text("Unable to load chapters. Return to the library and retry.", color = MaterialTheme.colorScheme.error)
        else {
            val chapters = result.getOrThrow().chapters
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${chapters.size} chapters", Modifier.weight(1f))
                TextButton(onClick = { selectionMode = !selectionMode; selectedIds = emptySet() }) {
                    Text(if (selectionMode) "Cancel selection" else "Select chapters")
                }
                if (selectionMode) {
                    Text("${selectedIds.size} selected")
                    TextButton(onClick = { exportOpen = true }, enabled = selectedIds.isNotEmpty()) { Text("Export") }
                }
            }
            if (exportOpen) ComposeExport(card, chapters.filter { it.id in selectedIds }) { exportOpen = false }
            if (chapters.isEmpty() && refreshAllowed) Text("Refresh chapters to fetch available chapters from this source.")
            LazyColumn(Modifier.weight(1f)) {
                items(chapters, key = { it.id }) { chapter ->
                    ListItem(headlineContent = { Text(chapter.name) },
                        supportingContent = { Text(if (chapter.downloaded) "Downloaded" else "Online") },
                        leadingContent = if (selectionMode) {
                            { Checkbox(checked = chapter.id in selectedIds, onCheckedChange = { checked ->
                                if (checked && selectedIds.size < 500) selectedIds = selectedIds + chapter.id
                                if (!checked) selectedIds = selectedIds - chapter.id
                            }, enabled = chapter.id in selectedIds || selectedIds.size < 500) }
                        } else null,
                        trailingContent = { TextButton(onClick = { opened = chapter }, enabled = !selectionMode) { Text("Read") } })
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
    LaunchedEffect(client, offset, revision) {
        page = null
        error = false
        request { client.library(offset, 100) }.fold({ page = it }, { error = true })
    }
    val card = selected
    if (card != null) Details(client, card) { selected = null }
    else Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Library", style = MaterialTheme.typography.headlineMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(search, { search = it }, label = { Text("Filter this page") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { revision++ }) { Text("Refresh") }
        }
        Spacer(Modifier.height(16.dp))
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
                            Cover(client, item, Modifier.fillMaxWidth().aspectRatio(0.70f))
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

fun main() = application {
    val backend = remember { ComposeBackendSession() }
    val lifecycle = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    Window(onCloseRequest = {
        if (!closing) {
            closing = true
            lifecycle.launch {
                try { withContext(Dispatchers.IO) { backend.close() } }
                finally { exitApplication() }
            }
        }
    }, title = "MihonNooir · Compose experiment",
        state = rememberWindowState(width = 1040.dp, height = 720.dp)) {
        var section by remember { mutableStateOf("Library") }
        var dark by remember { mutableStateOf(true) }
        val client = remember { SuwayomiClient() }
        var backendState by remember { mutableStateOf<BackendState>(BackendState.Checking) }
        var setupRevision by remember { mutableStateOf(0) }
        LaunchedEffect(setupRevision) {
            backendState = BackendState.Checking
            backendState = try { withContext(Dispatchers.IO) { backend.connectOrStartCached() } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { BackendState.Unavailable("Local reading service could not start. Check the existing server and retry.") }
        }
        MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
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
                        listOf("Library", "Browse", "More").forEach { name ->
                            NavigationRailItem(selected = section == name, onClick = { section = name },
                                icon = { Text(name.take(1)) }, label = { Text(name) })
                        }
                    }
                    VerticalDivider()
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        when (section) {
                            "Library" -> Library(client)
                            "Browse" -> Sources(client)
                            else -> Column(Modifier.padding(24.dp)) {
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
                                var queueOpen by remember { mutableStateOf(false) }
                                ListItem(headlineContent = { Text("Activity Queue") },
                                    supportingContent = { Text("Local conversion jobs and completed books") },
                                    trailingContent = { TextButton(onClick = { queueOpen = true }) { Text("Open") } })
                                if (queueOpen) ComposeQueue { queueOpen = false }
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
                                val connection = backendState as? BackendState.Ready
                                ListItem(headlineContent = { Text("Reading service") },
                                    supportingContent = { Text(if (connection?.owned == true) "Managed by MihonNooir" else "Connected to existing local service") },
                                    trailingContent = { TextButton(onClick = { setupRevision++ }) { Text("Reconnect") } })
                                Text("Experimental desktop interface. Remote chapter jobs and saved reading progress still require the existing preview.")
                            }
                        }
                    }
                }
            }
        }
    }
}
