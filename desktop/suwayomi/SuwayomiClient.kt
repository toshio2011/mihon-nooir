package dev.nooir.desktop.suwayomi

import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** v2.4.2366 Suwayomi GraphQL/CBZ REST bridge; no server internals or extension code copied. */
data class SuwayomiChapter(
    val id: Int,
    val name: String,
    val chapterNumber: Double,
    val sourceOrder: Int,
    val downloaded: Boolean,
)

data class SuwayomiManga(
    val id: Int,
    val title: String,
    val author: String,
    val chapters: List<SuwayomiChapter>,
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
)

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
        require(response.statusCode() == 200) { "Suwayomi GraphQL returned HTTP ${response.statusCode()}" }
        val body = response.body().use { it.readNBytes(4_000_001) }
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
    fun library(offset: Int = 0, limit: Int = 100): SuwayomiLibraryPage {
        require(offset in 0..100_000 && limit in 1..200) { "Invalid library pagination" }
        val query = """
            query NooirLibrary {
              mangas(condition: { inLibrary: true }, first: $limit, offset: $offset) {
                totalCount
                nodes { id title author description thumbnailUrl inLibrary }
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
            )
        }
        require(mangas.size <= limit && mangas.all { it.inLibrary }) {
            "Suwayomi returned invalid library entries"
        }
        return SuwayomiLibraryPage(mangas, total, offset, limit)
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
        require(sourceId != 0L) { "Invalid source ID" }
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

    fun manga(id: Int): SuwayomiManga {
        require(id > 0) { "Invalid manga ID" }
        val query = """
            query NooirManga {
              manga(id: $id) {
                id title author
                chapters { nodes { id name chapterNumber sourceOrder isDownloaded } }
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
            )
        }
        require(chapters.size <= 50_000) { "Too many chapters" }
        return SuwayomiManga(result.int("id"), result.str("title"),
            result["author"] as? String ?: "", chapters)
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

    /** Returns downloaded CBZ bytes without changing the chapter's read status. */
    fun downloadCbz(chapterId: Int, target: File, maxBytes: Long = 400L * 1024 * 1024) {
        require(chapterId > 0 && !target.exists()) { "Invalid chapter or existing destination" }
        val response = http.send(
            request("/api/v1/chapter/$chapterId/download?markAsRead=false").build(),
            HttpResponse.BodyHandlers.ofInputStream(),
        )
        response.body().use { input ->
            require(response.statusCode() == 200) {
                "Suwayomi chapter $chapterId CBZ returned HTTP ${response.statusCode()}"
            }
            var total = 0L
            target.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= maxBytes) { "Suwayomi chapter archive exceeds size limit" }
                    output.write(buffer, 0, count)
                }
            }
            require(total >= 22) { "Empty or invalid chapter archive" }
        }
    }

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
