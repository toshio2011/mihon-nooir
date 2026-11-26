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

@Composable
private fun Details(client: SuwayomiClient, card: SuwayomiMangaCard, back: () -> Unit) {
    var state by remember(card.id) { mutableStateOf<Result<SuwayomiManga>?>(null) }
    LaunchedEffect(client, card.id) { state = request { client.manga(card.id) } }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        TextButton(onClick = back) { Text("‹ Library") }
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
        Text("Chapters", style = MaterialTheme.typography.titleLarge)
        val result = state
        if (result == null) CircularProgressIndicator()
        else if (result.isFailure) Text("Unable to load chapters. Return to the library and retry.", color = MaterialTheme.colorScheme.error)
        else {
            val chapters = result.getOrThrow().chapters
            Text("${chapters.size} chapters · read-only interface experiment")
            LazyColumn(Modifier.weight(1f)) {
                items(chapters, key = { it.id }) { chapter ->
                    ListItem(headlineContent = { Text(chapter.name) },
                        supportingContent = { Text(if (chapter.downloaded) "Downloaded" else "Online") })
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

@Composable
private fun Sources(client: SuwayomiClient) {
    var result by remember { mutableStateOf<Result<SuwayomiSourcePage>?>(null) }
    var revision by remember { mutableStateOf(0) }
    LaunchedEffect(client, revision) { result = null; result = request { client.sources(0, 100) } }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Browse", style = MaterialTheme.typography.headlineMedium)
        Text("Installed sources · first 100")
        TextButton(onClick = { revision++ }) { Text("Reload") }
        val current = result
        when {
            current == null -> CircularProgressIndicator()
            current.isFailure -> Text("Local server unavailable.", color = MaterialTheme.colorScheme.error)
            else -> LazyColumn {
                items(current.getOrThrow().sources, key = { it.id }) {
                    ListItem(headlineContent = { Text(it.displayName) }, supportingContent = { Text(it.language) })
                    HorizontalDivider()
                }
            }
        }
    }
}

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "MihonNooir · Compose experiment",
        state = rememberWindowState(width = 1040.dp, height = 720.dp)) {
        var section by remember { mutableStateOf("Library") }
        var dark by remember { mutableStateOf(true) }
        val client = remember { SuwayomiClient() }
        MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
            Surface(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxSize()) {
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
                                Text("Read-only UI feasibility build. Use the existing preview for reader, exports, backups, extension management and backend controls.")
                            }
                        }
                    }
                }
            }
        }
    }
}
