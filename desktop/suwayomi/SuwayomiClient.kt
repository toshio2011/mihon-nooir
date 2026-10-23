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

/** Source IDs are 64-bit in Suwayomi and must not be coerced to JavaScript-style numbers. */
data class SuwayomiSource(
    val id: String,
    val name: String,
    val language: String,
    val supportsLatest: Boolean,
    val displayName: String,
)

data class SuwayomiBrowseManga(
    val id: Int,
    val title: String,
    val author: String,
    val sourceId: String,
    val inLibrary: Boolean,
)

data class SuwayomiBrowsePage(
    val manga: List<SuwayomiBrowseManga>,
    val hasNextPage: Boolean,
)

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

    private fun rest(path: String): Any? {
        val response = http.send(request(path).build(), HttpResponse.BodyHandlers.ofInputStream())
        val body = response.body().use { it.readNBytes(4_000_001) }
        require(response.statusCode() == 200) {
            "Suwayomi REST returned HTTP ${response.statusCode()} for ${path.substringBefore('?')}"
        }
        require(body.size <= 4_000_000) { "Suwayomi REST reply too large" }
        return MiniJson.parse(body.toString(Charsets.UTF_8))
    }

    /** Only locally installed Suwayomi sources. No extension installation or global scraping. */
    fun sources(): List<SuwayomiSource> =
        rest("/api/v1/source/list").asList().map { item ->
            val source = item.asObject()
            val id = source.str("id")
            require(id.toLongOrNull()?.let { it >= 0 } == true) { "Invalid source ID" }
            SuwayomiSource(
                id = id,
                name = source.str("name"),
                language = source.str("lang"),
                supportsLatest = source["supportsLatest"] as? Boolean ?: false,
                displayName = source["displayName"] as? String ?: source.str("name"),
            )
        }.also { require(it.size <= 2000) { "Too many installed sources" } }

    /** v1 API page numbering is one-based; searchTerm is UTF-8 percent-encoded. */
    fun browse(sourceId: String, mode: String, page: Int = 1, searchTerm: String = ""): SuwayomiBrowsePage {
        require(sourceId.toLongOrNull()?.let { it >= 0 } == true) { "Invalid source ID" }
        require(page in 1..1000) { "Page must be between 1 and 1000" }
        val suffix = when (mode) {
            "popular" -> "popular/$page"
            "latest" -> "latest/$page"
            "search" -> {
                require(searchTerm.isNotBlank() && searchTerm.length <= 250) { "Search term must be 1..250 characters" }
                val encoded = java.net.URLEncoder.encode(searchTerm, Charsets.UTF_8)
                "search?searchTerm=$encoded&pageNum=$page"
            }
            else -> error("Browse mode must be popular, latest or search")
        }
        val root = rest("/api/v1/source/$sourceId/$suffix").asObject()
        val manga = root.list("mangaList").map { raw ->
            val m = raw.asObject()
            SuwayomiBrowseManga(
                id = m.int("id"),
                title = m.str("title"),
                author = m["author"] as? String ?: "",
                sourceId = m.str("sourceId"),
                inLibrary = m["inLibrary"] as? Boolean ?: false,
            )
        }
        require(manga.size <= 1000) { "Source returned too many manga" }
        return SuwayomiBrowsePage(
            manga = manga,
            hasNextPage = root["hasNextPage"] as? Boolean ?: error("Missing hasNextPage"),
        )
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
