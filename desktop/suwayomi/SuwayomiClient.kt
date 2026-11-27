package dev.nooir.desktop.suwayomi

import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.time.Duration

/** v2.4.2366 Suwayomi GraphQL/CBZ REST bridge; no server internals or extension code copied. */
data class SuwayomiChapter(
    val id: Int,
    val name: String,
    val chapterNumber: Double,
    val sourceOrder: Int,
    val downloaded: Boolean,
    val read: Boolean? = null,
    val lastPageRead: Int? = null,
    val bookmarked: Boolean? = null,
    val uploadDate: Long? = null,
)

data class SuwayomiManga(
    val id: Int,
    val title: String,
    val author: String,
    val chapters: List<SuwayomiChapter>,
    val firstUnreadId: Int? = null,
)

/** Source IDs are signed 64-bit hashes serialized as GraphQL LongString; valid IDs may be negative. */
data class SuwayomiSource(
    val id: Long,
    val name: String,
    val language: String,
    val displayName: String,
    val supportsLatest: Boolean,
    val contentWarning: String,
)

data class SuwayomiSourcePage(
    val sources: List<SuwayomiSource>,
    val totalCount: Int,
    val offset: Int,
    val limit: Int,
)

data class SuwayomiMangaCard(
    val id: Int,
    val title: String,
    val author: String?,
    val description: String?,
    val thumbnailUrl: String?,
    val inLibrary: Boolean,
    val unreadCount: Int? = null,
)

data class SuwayomiCategory(val id: Int, val name: String, val order: Int)

data class SuwayomiLibraryPage(
    val mangas: List<SuwayomiMangaCard>,
    val totalCount: Int,
    val offset: Int,
    val limit: Int,
)

data class SuwayomiBrowsePage(
    val sourceId: Long,
    val page: Int,
    val mangas: List<SuwayomiMangaCard>,
    val hasNextPage: Boolean,
)

enum class SuwayomiBrowseMode { POPULAR, LATEST, SEARCH }

data class SuwayomiFeedEntry(
    val manga: SuwayomiMangaCard,
    val chapter: SuwayomiChapter,
    val read: Boolean,
    val lastPageRead: Int,
    val pageCount: Int,
    val lastReadAt: Long,
    val fetchedAt: Long,
)

data class SuwayomiFeedPage(val entries: List<SuwayomiFeedEntry>, val totalCount: Int)

data class SuwayomiDownload(val chapterId:Int,val mangaId:Int,val mangaTitle:String,val chapterName:String,
    val state:String,val tries:Int,val position:Int)
data class SuwayomiDownloads(val state:String,val queue:List<SuwayomiDownload>)

/** Numeric identities from a validated pinned-server chapter-page manifest. */
data class SuwayomiOnlineChapter internal constructor(
    val chapterId: Int,
    val mangaId: Int,
    val sourceOrder: Int,
    val pageCount: Int,
    val lastPageRead: Int,
    val syncConflict: SuwayomiReadingConflict?,
)

data class SuwayomiReadingConflict(val deviceName: String, val remotePage: Int)

class SuwayomiClient(
    endpoint: String = "http://127.0.0.1:4567",
    private val authorization: String? = null,
) {
    private val base: String
    private val http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    init {
        val uri = URI(endpoint)
        require(uri.scheme == "http" || uri.scheme == "https") { "Suwayomi must use HTTP(S)" }
        require(uri.host?.lowercase() in setOf("localhost", "127.0.0.1", "::1")) {
            "This D0 client connects to localhost only; remote servers are not yet supported"
        }
        require(uri.port in 1..65535) { "Supply a localhost server port" }
        require(uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null) {
            "Do not embed credentials or query parameters in the Suwayomi URL"
        }
        require(uri.rawPath.isNullOrEmpty() || uri.rawPath == "/") {
            "Server subpaths are not supported by this first integration spike"
        }
        base = "${uri.scheme}://${uri.rawAuthority}"
    }

    private fun request(path: String, query: String? = null): HttpRequest.Builder {
        require(path.startsWith("/api/") && !path.contains("..")) { "Invalid API path" }
        val builder = HttpRequest.newBuilder(URI.create(base + path))
            .timeout(Duration.ofSeconds(if (query == null) 120 else 30))
            .header("User-Agent", "MangaNooir-Desktop-D0")
        if (!authorization.isNullOrBlank()) builder.header("Authorization", authorization)
        return if (query == null) builder.GET()
        else builder.header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{\"query\":" + MiniJson.quote(query) + "}"))
    }

    private fun graphql(query: String): Map<String, Any?> {
        val response = http.send(request("/api/graphql", query).build(), HttpResponse.BodyHandlers.ofInputStream())
        val body = response.body().use {
            require(response.statusCode() == 200) { "Suwayomi GraphQL returned HTTP ${response.statusCode()}" }
            it.readNBytes(4_000_001)
        }
        require(body.size <= 4_000_000) { "Suwayomi GraphQL reply too large" }
        val root = MiniJson.parse(body.toString(Charsets.UTF_8)).asObject()
        val errors = root["errors"] as? List<*>
        require(errors.isNullOrEmpty()) {
            val messages = errors.orEmpty().take(3).joinToString("; ") {
                (it as? Map<*, *>)?.get("message")?.toString()?.take(250) ?: "GraphQL error"
            }
            "Suwayomi GraphQL error: $messages"
        }
        return root.obj("data")
    }

    /**
     * List sources already installed/configured in the user's own Suwayomi server.
     * GraphQL SourceQuery.sources(first, offset), verified against v2.4.2366.
     */
    fun sources(offset: Int = 0, limit: Int = 100): SuwayomiSourcePage {
        require(offset in 0..100_000 && limit in 1..200) { "Invalid source pagination" }
        val query = """
            query NooirSources {
              sources(first: $limit, offset: $offset) {
                totalCount
                nodes { id name lang displayName supportsLatest contentWarning }
              }
            }
        """.trimIndent()
        val result = graphql(query).obj("sources")
        val total = result.int("totalCount")
        require(total >= 0) { "Invalid source count" }
        val sources = result.list("nodes").map { item ->
            val node = item.asObject()
            SuwayomiSource(
                id = node.long("id"),
                name = node.str("name"),
                language = node.str("lang"),
                displayName = node.str("displayName"),
                supportsLatest = node["supportsLatest"] as? Boolean ?: error("Missing supportsLatest"),
                contentWarning = node.str("contentWarning"),
            )
        }
        require(sources.size <= limit) { "Source page exceeds requested limit" }
        return SuwayomiSourcePage(sources, total, offset, limit)
    }

    /** User's Suwayomi library (not an Android Mihon library sync). */
    fun categories(): List<SuwayomiCategory> {
        val found = mutableListOf<SuwayomiCategory>()
        var offset = 0
        do {
            val page = graphql("query NooirCategories { categories(first: 100, offset: $offset) { totalCount nodes { id name order } } }").obj("categories")
            val total = page.int("totalCount")
            require(total in 0..1000)
            val nodes = page.list("nodes")
            require(nodes.size <= 100 && offset + nodes.size <= total)
            found += nodes.map {
                val node = it.asObject()
                SuwayomiCategory(node.int("id"),node.str("name"),node.int("order")).also { category ->
                    require(category.id >= 0 && category.name.length <= 500)
                }
            }
            offset += nodes.size
            if (offset == total) break
            require(nodes.isNotEmpty()) { "Incomplete category listing" }
        } while (true)
        require(found.map { it.id }.distinct().size == found.size)
        return found.sortedWith(compareBy<SuwayomiCategory> { it.order }.thenBy { it.id })
    }

    fun library(offset: Int = 0, limit: Int = 100, categoryId: Int? = null): SuwayomiLibraryPage {
        require(offset in 0..100_000 && limit in 1..200) { "Invalid library pagination" }
        require(categoryId == null || categoryId >= 0)
        val category = categoryId?.let { ", categoryIds: [$it]" }.orEmpty()
        val query = """
            query NooirLibrary {
              mangas(condition: { inLibrary: true$category }, first: $limit, offset: $offset) {
                totalCount
                nodes { id title author description thumbnailUrl inLibrary unreadCount }
              }
            }
        """.trimIndent()
        val result = graphql(query).obj("mangas")
        val total = result.int("totalCount")
        require(total >= 0) { "Invalid library total" }
        val mangas = result.list("nodes").map { item ->
            val node = item.asObject()
            SuwayomiMangaCard(
                id = node.int("id"),
                title = node.str("title"),
                author = node["author"] as? String,
                description = node["description"] as? String,
                thumbnailUrl = node["thumbnailUrl"] as? String,
                inLibrary = node["inLibrary"] as? Boolean ?: error("Missing inLibrary"),
                unreadCount = (node["unreadCount"] as? Long)?.takeIf { it in 0..Int.MAX_VALUE.toLong() }?.toInt(),
            )
        }
        require(mangas.size <= limit && mangas.all { it.inLibrary }) {
            "Suwayomi returned invalid library entries"
        }
        return SuwayomiLibraryPage(mangas, total, offset, limit)
    }

    /** Official pinned v2.4.2366 updateManga patch; explicit UI action only. */
    fun setMangaInLibrary(id: Int, inLibrary: Boolean): Boolean {
        require(id > 0) { "Invalid manga ID" }
        val query = """
            mutation NooirLibraryMembership {
              updateManga(input: { id: $id, patch: { inLibrary: $inLibrary } }) {
                manga { id inLibrary }
              }
            }
        """.trimIndent()
        val changed = graphql(query).obj("updateManga").obj("manga")
        require(changed.int("id") == id) { "Suwayomi returned a different manga ID" }
        val state = changed["inLibrary"] as? Boolean ?: error("Missing updated library status")
        require(state == inLibrary) { "Suwayomi did not apply the library change" }
        return state
    }

    /**
     * Fetch a source page using Suwayomi's v2.4.2366 FetchSourceManga mutation.
     * Search terms are GraphQL-string-escaped and signed source IDs use LongString literals.
     * Suwayomi may cache fetched manga metadata in its own database.
     */
    fun browse(
        sourceId: Long,
        mode: SuwayomiBrowseMode,
        page: Int = 1,
        searchTerm: String? = null,
    ): SuwayomiBrowsePage {
        // Source IDs are signed LongString values; 0 is the built-in Local source.
        require(page in 1..10_000) { "Invalid source page" }
        val queryPart = if (mode == SuwayomiBrowseMode.SEARCH) {
            val term = searchTerm?.trim() ?: ""
            require(term.isNotEmpty() && term.length <= 200 && term.none { it.code < 32 }) {
                "Search requires a 1–200 character term without control characters"
            }
            ", query: ${MiniJson.quote(term)}"
        } else {
            require(searchTerm.isNullOrBlank()) { "Search term is only valid in SEARCH mode" }
            ""
        }
        val query = """
            mutation NooirBrowse {
              fetchSourceManga(input: { source: "${sourceId}", type: ${mode.name}, page: $page$queryPart }) {
                hasNextPage
                mangas { id title author description thumbnailUrl inLibrary }
              }
            }
        """.trimIndent()
        val result = graphql(query).obj("fetchSourceManga")
        val mangas = result.list("mangas").map { item ->
            val node = item.asObject()
            SuwayomiMangaCard(
                id = node.int("id"),
                title = node.str("title"),
                author = node["author"] as? String,
                description = node["description"] as? String,
                thumbnailUrl = node["thumbnailUrl"] as? String,
                inLibrary = node["inLibrary"] as? Boolean ?: error("Missing inLibrary"),
            )
        }
        require(mangas.size <= 500) { "Unexpectedly large source browse page" }
        val next = result["hasNextPage"] as? Boolean ?: error("Missing hasNextPage")
        return SuwayomiBrowsePage(sourceId, page, mangas, next)
    }

    /**
     * Explicit source-network refresh, needed for chapter lists of newly discovered manga.
     * Never invoked implicitly by browse/search. The server updates its metadata cache.
     */
    fun refreshManga(mangaId: Int, fetchManga: Boolean = true, fetchChapters: Boolean = true) {
        require(mangaId > 0) { "Invalid manga ID" }
        require(fetchManga || fetchChapters) { "Select at least one refresh operation" }
        val query = """
            mutation NooirRefresh {
              fetchMangaAndChapters(input: {
                id: $mangaId,
                fetchManga: $fetchManga,
                fetchChapters: $fetchChapters
              }) {
                manga { id }
              }
            }
        """.trimIndent()
        val response = graphql(query).obj("fetchMangaAndChapters")
        require(response.obj("manga").int("id") == mangaId) {
            "Suwayomi returned an unexpected manga ID"
        }
    }

    /** Read-only library chapter feed, using pinned v2.4.2366 ChapterQuery.
     * History lists chapters with a saved read timestamp, not an invented event log.
     * Updates lists cached chapter metadata, never implies a remote source refresh.
     */
    fun chapterFeed(history: Boolean, offset: Int = 0, limit: Int = 50): SuwayomiFeedPage {
        require(offset in 0..100_000 && limit in 1..100)
        val order = if (history) "LAST_READ_AT" else "FETCHED_AT"
        val timestamp = if (history) "lastReadAt" else "fetchedAt"
        val query = """
            query NooirChapterFeed {
              chapters(filter: { inLibrary: { equalTo: true }, $timestamp: { greaterThan: "0" } },
                order: [{ by: $order, byType: DESC }], first: $limit, offset: $offset) {
                totalCount
                nodes { id mangaId name chapterNumber sourceOrder isDownloaded isRead lastPageRead pageCount lastReadAt fetchedAt
                  manga { id title author description thumbnailUrl inLibrary }
                }
              }
            }
        """.trimIndent()
        val root = graphql(query).obj("chapters")
        val total = root.int("totalCount")
        val rows = root.list("nodes")
        require(total >= 0 && rows.size <= limit && (rows.isEmpty() || offset + rows.size <= total))
        val entries = rows.map { raw ->
            val c = raw.asObject()
            val m = c.obj("manga")
            val card = SuwayomiMangaCard(m.int("id"), m.str("title"), m["author"] as? String,
                m["description"] as? String, m["thumbnailUrl"] as? String, m["inLibrary"] == true)
            val chapter = SuwayomiChapter(c.int("id"), c.str("name"),
                (c["chapterNumber"] as? Number)?.toDouble() ?: error("Missing chapter number"),
                c.int("sourceOrder"), c["isDownloaded"] as? Boolean ?: error("Missing download status"))
            val readAt = c.long("lastReadAt")
            val fetched = c.long("fetchedAt")
            val lastPage = c.int("lastPageRead")
            val pageCount = c.int("pageCount")
            require(card.id > 0 && card.id == c.int("mangaId") && card.inLibrary &&
                chapter.id > 0 && chapter.chapterNumber.isFinite() && chapter.sourceOrder >= 0 &&
                lastPage >= 0 && pageCount >= -1 && readAt >= 0 && fetched >= 0 &&
                (if (history) readAt > 0 else fetched > 0)) { "Invalid chapter feed record" }
            SuwayomiFeedEntry(card,chapter,c["isRead"] as? Boolean ?: error("Missing reading state"),
                lastPage,pageCount,readAt,fetched)
        }
        require(entries.map { it.chapter.id }.distinct().size == entries.size)
        return SuwayomiFeedPage(entries,total)
    }

    fun manga(id: Int): SuwayomiManga {
        require(id > 0) { "Invalid manga ID" }
        val query = """
            query NooirManga {
              manga(id: $id) {
                id title author
                firstUnreadChapter { id }
                chapters { nodes { id name chapterNumber sourceOrder isDownloaded isRead lastPageRead isBookmarked uploadDate } }
              }
            }
        """.trimIndent()
        val data = graphql(query)
        val result = data.obj("manga")
        val chapters = result.obj("chapters").list("nodes").map { raw ->
            val c = raw.asObject()
            SuwayomiChapter(
                id = c.int("id"),
                name = c.str("name"),
                chapterNumber = (c["chapterNumber"] as? Number)?.toDouble()
                    ?: error("Missing chapter number"),
                sourceOrder = c.int("sourceOrder"),
                downloaded = c["isDownloaded"] as? Boolean ?: error("Missing download status"),
                read = c["isRead"] as? Boolean,
                lastPageRead = c["lastPageRead"]?.let { c.int("lastPageRead").also { page -> require(page >= 0) } },
                bookmarked = c["isBookmarked"] as? Boolean,
                uploadDate = c["uploadDate"]?.let { c.long("uploadDate") },
            )
        }
        require(chapters.size <= 50_000) { "Too many chapters" }
        require(result.int("id") == id && chapters.map { it.id }.distinct().size == chapters.size)
        val firstUnread = result["firstUnreadChapter"]?.asObject()?.int("id")
        require(firstUnread == null || chapters.any { it.id == firstUnread && it.read != true })
        return SuwayomiManga(result.int("id"), result.str("title"),
            result["author"] as? String ?: "", chapters, firstUnread)
    }

    /** Explicit flag-only update. Validate membership against a fresh manga snapshot.
     * Mark unread preserves lastPageRead; this never deletes or resets a chapter. */
    fun updateChapterFlags(mangaId:Int,ids:List<Int>,read:Boolean?=null,bookmarked:Boolean?=null) {
        require(mangaId>0 && ids.size in 1..500 && ids.all{it>0} && ids.distinct().size==ids.size)
        require((read!=null) != (bookmarked!=null)) { "Choose one chapter action" }
        val current=manga(mangaId).chapters.associateBy{it.id}
        require(ids.all{it in current}) { "Chapter selection changed; reload before updating" }
        val patch=if(read!=null) "isRead: $read" else "isBookmarked: $bookmarked"
        val result=graphql("mutation NooirUpdateFlags { updateChapters(input: { ids: ["+
            ids.joinToString(",")+"], patch: { "+patch+" } }) { chapters { id mangaId isRead isBookmarked } } }")
            .obj("updateChapters").list("chapters").map{it.asObject()}
        require(result.size==ids.size && result.map{it.int("id")}.toSet()==ids.toSet() &&
            result.all{it.int("mangaId")==mangaId && (if(read!=null)it["isRead"]==read else it["isBookmarked"]==bookmarked)}) {
            "Server did not confirm every chapter flag; reload before retrying"
        }
    }

    /** Actual pinned downloader state; progress is not fabricated from local records. */
    fun downloadStatus():SuwayomiDownloads {
        val root=graphql("query NooirDownloadStatus { downloadStatus { state queue { state tries position chapter { id mangaId name } manga { id title } } } }").obj("downloadStatus")
        val state=root.str("state")
        require(state in setOf("STARTED","STOPPED"))
        val rows=root.list("queue")
        require(rows.size<=10000)
        val entries=rows.map{
            val item=it.asObject();val c=item.obj("chapter");val m=item.obj("manga")
            val row=SuwayomiDownload(c.int("id"),m.int("id"),m.str("title"),c.str("name"),item.str("state"),item.int("tries"),item.int("position"))
            require(row.chapterId>0 && row.mangaId>0 && c.int("mangaId")==row.mangaId && row.tries>=0 && row.position>=0 &&
                row.state in setOf("QUEUED","DOWNLOADING","FINISHED","ERROR"))
            row
        }
        require(entries.map{it.chapterId}.distinct().size==entries.size)
        return SuwayomiDownloads(state,entries.sortedBy{it.position})
    }

    fun setDownloaderRunning(running:Boolean) {
        val target=if(running)"STARTED" else "STOPPED"
        if(downloadStatus().state==target) return
        val operation=if(running)"startDownloader" else "stopDownloader"
        val result=graphql("mutation NooirDownloader { "+operation+"(input: {}) { downloadStatus { state } } }")
            .obj(operation).obj("downloadStatus")
        require(result.str("state")==target) { "Downloader state not confirmed; reload" }
    }

    fun removeQueuedChapter(mangaId:Int,chapterId:Int) {
        require(mangaId>0 && chapterId>0)
        val current=downloadStatus().queue.singleOrNull{it.chapterId==chapterId}
            ?: error("Chapter is no longer in the queue")
        require(current.mangaId==mangaId && current.state in setOf("QUEUED","ERROR")) { "Queue changed; reload" }
        val result=graphql("mutation NooirRemoveQueued { dequeueChapterDownloads(input: { ids: ["+
            chapterId+"] }) { downloadStatus { queue { chapter { id } } } } }")
            .obj("dequeueChapterDownloads").obj("downloadStatus").list("queue")
        require(result.none{it.asObject().obj("chapter").int("id")==chapterId}) { "Queue removal not confirmed" }
    }

    fun enqueue(ids: List<Int>) {
        require(ids.isNotEmpty() && ids.size <= 500 && ids.all { it > 0 } && ids.distinct().size == ids.size) {
            "Select 1 to 500 distinct chapter IDs"
        }
        val literal = ids.joinToString(",")
        val query = """
            mutation NooirEnqueue {
              enqueueChapterDownloads(input: { ids: [$literal] }) {
                clientMutationId
              }
            }
        """.trimIndent()
        require(graphql(query)["enqueueChapterDownloads"] != null) {
            "Suwayomi did not accept the download queue request"
        }
    }

    /**
     * Stream an ALREADY downloaded CBZ from localhost Suwayomi into an exclusive
     * new file. The callback measures only this local CBZ copy, never Suwayomi
     * source-download progress. Fail closed on oversized/truncated responses
     * and low disk space. A failed copy never leaves a partial destination.
     */
    fun downloadCbz(
        chapterId: Int,
        target: File,
        maxBytes: Long = 400L * 1024 * 1024,
        onProgress: (Long, Long?) -> Unit = { _, _ -> },
        minFreeDiskBytes: Long = 32L * 1024 * 1024,
        availableDiskBytes: () -> Long = {
            Files.getFileStore(target.canonicalFile.parentFile.toPath()).usableSpace
        },
    ) {
        require(chapterId > 0 && !target.exists()) { "Invalid chapter or existing destination" }
        require(maxBytes >= 22L && minFreeDiskBytes >= 0L) { "Invalid CBZ copy limits" }
        val path = target.toPath()
        val response = http.send(
            request("/api/v1/chapter/$chapterId/download?markAsRead=false").build(),
            HttpResponse.BodyHandlers.ofInputStream(),
        )
        var created = false
        try {
            response.body().use { input ->
                require(response.statusCode() == 200) {
                    "Suwayomi chapter $chapterId CBZ returned HTTP ${response.statusCode()}"
                }
                // Null means chunked/no trustworthy size: never invent a percent.
                val expected = response.headers().firstValue("Content-Length")
                    .orElse(null)?.toLongOrNull()?.takeIf { it >= 0L }
                require(expected == null || expected in 22L..maxBytes) {
                    "CBZ Content-Length exceeds the copy limit or is invalid"
                }
                val free = availableDiskBytes()
                require(free >= minFreeDiskBytes &&
                    (expected == null || expected <= free - minFreeDiskBytes)) {
                    "Not enough disk space for the local CBZ copy (keep ${minFreeDiskBytes / 1048576} MiB free)"
                }
                Files.newOutputStream(path, StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE).use { output ->
                    created = true
                    var total = 0L
                    var lastProgress = 0L
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        require(total <= maxBytes - count) {
                            "Local CBZ copy exceeds the 400 MiB default limit"
                        }
                        require(availableDiskBytes() >= minFreeDiskBytes + count) {
                            "Disk space became too low during CBZ copy"
                        }
                        require(expected == null || total + count <= expected) {
                            "CBZ received more bytes than the server Content-Length"
                        }
                        output.write(buffer, 0, count)
                        total += count
                        // Bound Swing UI updates on large manga archives.
                        if (total - lastProgress >= 1024L * 1024L) {
                            onProgress(total, expected)
                            lastProgress = total
                        }
                    }
                    require(total >= 22L) { "Empty or invalid chapter archive" }
                    require(expected == null || total == expected) {
                        "Incomplete local CBZ copy: received $total of $expected bytes"
                    }
                    if (total != lastProgress) onProgress(total, expected)
                }
            }
        } catch (error: Exception) {
            if (created) runCatching { Files.deleteIfExists(path) }
            throw error
        }
    }

    /**
     * Explicit chapter-open action only. Pinned v2.4.2366 fetchChapterPages:
     * gets page locations, may consult enabled server-side reading sync.
     * No enqueue/download-CBZ mutation or client-side read/progress mutation.
     */
    fun openOnlineChapter(mangaId: Int, chapterId: Int): SuwayomiOnlineChapter {
        require(mangaId > 0 && chapterId > 0) { "Invalid chapter identity" }
        val query = """
            mutation NooirChapterPages {
              fetchChapterPages(input: { chapterId: $chapterId }) {
                pages
                chapter { id mangaId sourceOrder pageCount lastPageRead }
                syncConflict { deviceName remotePage }
              }
            }
        """.trimIndent()
        val result = graphql(query).obj("fetchChapterPages")
        val c = result.obj("chapter")
        require(c.int("id") == chapterId && c.int("mangaId") == mangaId) { "Unexpected chapter identity" }
        val order = c.int("sourceOrder")
        val count = c.int("pageCount")
        val lastPage = c.int("lastPageRead")
        require(order >= 0 && count in 1..5000 && lastPage in 0..count) { "Invalid or unsupported chapter page count/progress" }
        val locations = result.list("pages")
        require(locations.size == count) { "Chapter page count does not match manifest" }
        locations.forEachIndexed { index, location ->
            // Validate all locations against the selected numeric chapter and
            // exact pinned REST path. Do not resolve arbitrary URLs or queries.
            require(location == onlinePagePath(mangaId, order, index)) {
                "Unexpected chapter page location at index $index"
            }
        }
        val conflict = result["syncConflict"]?.asObject()?.let {
            SuwayomiReadingConflict(it.str("deviceName").take(200), it.int("remotePage").also { page ->
                require(page in 0..count) { "Unexpected conflicting reading position" }
            })
        }
        return SuwayomiOnlineChapter(chapterId, mangaId, order, count, lastPage, conflict)
    }

    /** Explicit action; refuse known rewind. Concurrent remote writers are not atomic. */
    fun saveOnlinePosition(chapter: SuwayomiOnlineChapter, page: Int): Int {
        require(chapter.syncConflict == null) { "Resolve server reading conflict first" }
        require(chapter.chapterId > 0 && chapter.mangaId > 0 && page in 0 until chapter.pageCount)
        val current = graphql("query NooirCurrentPosition { chapter(id: "+chapter.chapterId+") { id mangaId pageCount lastPageRead } }").obj("chapter")
        require(current.int("id")==chapter.chapterId && current.int("mangaId")==chapter.mangaId &&
            current.int("pageCount")==chapter.pageCount) { "Chapter changed; reopen before saving" }
        val existing=current.int("lastPageRead")
        require(existing in 0..chapter.pageCount && existing <= page) { "Server has a later or invalid position; no changes made" }
        if(existing==page)return existing
        val changed=graphql("mutation NooirSavePosition { updateChapter(input: { id: "+chapter.chapterId+
            ", patch: { lastPageRead: "+page+" } }) { chapter { id mangaId lastPageRead } } }").obj("updateChapter").obj("chapter")
        require(changed.int("id")==chapter.chapterId && changed.int("mangaId")==chapter.mangaId &&
            changed.int("lastPageRead")==page) { "Server did not confirm reading position" }
        return page
    }

    /** Fetch just one image's bounded encoded bytes; no disk cache, no decode. */
    fun onlinePage(chapter: SuwayomiOnlineChapter, index: Int): ByteArray {
        require(chapter.chapterId > 0 && chapter.mangaId > 0 && chapter.sourceOrder >= 0 &&
            chapter.pageCount in 1..5000 && index in 0 until chapter.pageCount) { "Invalid page index or chapter" }
        val limit = 25 * 1024 * 1024
        val response = http.send(
            request(onlinePagePath(chapter.mangaId, chapter.sourceOrder, index)).build(),
            HttpResponse.BodyHandlers.ofInputStream(),
        )
        return response.body().use { input ->
            require(response.statusCode() == 200) { "Suwayomi page returned HTTP ${response.statusCode()}" }
            val type = response.headers().firstValue("Content-Type").orElse("").substringBefore(';').trim().lowercase()
            require(type in setOf("image/png", "image/jpeg", "image/jpg", "image/webp", "image/gif", "image/bmp", "image/avif")) {
                "Unsupported chapter page media type"
            }
            val declared = response.headers().firstValueAsLong("Content-Length")
            require(!declared.isPresent || declared.asLong in 1L..limit.toLong()) { "Chapter page exceeds encoded size limit" }
            val bytes = input.readNBytes(limit + 1)
            require(bytes.isNotEmpty() && bytes.size <= limit &&
                (!declared.isPresent || declared.asLong == bytes.size.toLong())) { "Empty, oversized or truncated chapter page" }
            bytes
        }
    }

    private fun onlinePagePath(mangaId: Int, order: Int, index: Int) =
        "/api/v1/manga/$mangaId/chapter/$order/page/$index"

    /** Cover fetch is best effort; absent/invalid cover never invents an image. */
    fun thumbnail(mangaId: Int): ByteArray? {
        require(mangaId > 0)
        val response = http.send(
            request("/api/v1/manga/$mangaId/thumbnail").build(),
            HttpResponse.BodyHandlers.ofInputStream(),
        )
        return response.body().use { input ->
            if (response.statusCode() != 200) return@use null
            val bytes = input.readNBytes(8_000_001)
            if (bytes.size > 8_000_000) null else bytes
        }
    }
}
