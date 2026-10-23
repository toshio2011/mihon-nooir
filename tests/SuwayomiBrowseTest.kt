import com.sun.net.httpserver.HttpServer
import dev.nooir.desktop.suwayomi.MiniJson
import dev.nooir.desktop.suwayomi.SuwayomiBrowseCli
import dev.nooir.desktop.suwayomi.SuwayomiBrowseMode
import dev.nooir.desktop.suwayomi.SuwayomiClient
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger

private const val SOURCE_ID = -900000000000000001L

private fun outputOf(block: () -> Unit): String {
    val previous = System.out
    val output = ByteArrayOutputStream()
    try {
        System.setOut(PrintStream(output, true, "UTF-8"))
        block()
    } finally {
        System.setOut(previous)
    }
    return output.toString("UTF-8")
}

fun main() {
    val hits = AtomicInteger()
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/api/graphql") { ctx ->
        check(ctx.requestMethod == "POST")
        val body = ctx.requestBody.bufferedReader().use { it.readText() }
        val query = (MiniJson.parse(body) as Map<*, *>)["query"] as String
        hits.incrementAndGet()
        val response = when {
            "sources(first: 2, offset: 0)" in query -> {
                """{"data":{"sources":{"totalCount":3,"nodes":[{"id":"$SOURCE_ID","name":"Alpha","lang":"en","displayName":"Alpha (EN)","supportsLatest":true,"contentWarning":"SAFE"},{"id":"42","name":"Beta","lang":"ja","displayName":"Beta (JP)","supportsLatest":false,"contentWarning":"MIXED"}]}}}"""
            }
            "sources(first: 2, offset: 2)" in query -> {
                """{"data":{"sources":{"totalCount":3,"nodes":[{"id":"314","name":"Gamma","lang":"es","displayName":"Gamma","supportsLatest":true,"contentWarning":"SAFE"}]}}}"""
            }
            "mangas(condition: { inLibrary: true }, first: 2, offset: 0)" in query -> {
                """{"data":{"mangas":{"totalCount":3,"nodes":[{"id":101,"title":"One Piece","author":"Oda","description":"Pirates","thumbnailUrl":"/cover","inLibrary":true},{"id":102,"title":"Second","author":null,"description":null,"thumbnailUrl":null,"inLibrary":true}]}}}"""
            }
            "mangas(condition: { inLibrary: true }, first: 2, offset: 2)" in query -> {
                """{"data":{"mangas":{"totalCount":3,"nodes":[{"id":103,"title":"Third","author":null,"description":null,"thumbnailUrl":null,"inLibrary":true}]}}}"""
            }
            "fetchSourceManga" in query && "type: POPULAR" in query -> {
                check("source: \"$SOURCE_ID\"" in query && "page: 1" in query)
                """{"data":{"fetchSourceManga":{"hasNextPage":true,"mangas":[{"id":101,"title":"One Piece","author":"Oda","description":"Pirates","thumbnailUrl":"/api/v1/manga/101/thumbnail","inLibrary":false},{"id":102,"title":"Manga Two","author":null,"description":null,"thumbnailUrl":null,"inLibrary":true}]}}}"""
            }
            "fetchSourceManga" in query && "type: LATEST" in query -> {
                check("page: 2" in query)
                """{"data":{"fetchSourceManga":{"hasNextPage":false,"mangas":[]}}}"""
            }
            "fetchSourceManga" in query && "type: SEARCH" in query -> {
                check("page: 3" in query)
                check("query: ${MiniJson.quote("Tokyo \"Cafe\"")}" in query) {
                    "Search term was not GraphQL escaped"
                }
                """{"data":{"fetchSourceManga":{"hasNextPage":false,"mangas":[{"id":103,"title":"Tokyo Cafe","author":null,"description":null,"thumbnailUrl":null,"inLibrary":false}]}}}"""
            }
            "fetchMangaAndChapters" in query -> {
                check("id: 101" in query && "fetchManga: true" in query &&
                    "fetchChapters: true" in query)
                """{"data":{"fetchMangaAndChapters":{"manga":{"id":101}}}}"""
            }
            "manga(id: 101)" in query -> {
                """{"data":{"manga":{"id":101,"title":"One Piece","author":"Oda","chapters":{"nodes":[{"id":7001,"name":"Chapter 1","chapterNumber":1.0,"sourceOrder":5,"isDownloaded":true},{"id":7002,"name":"Chapter 2","chapterNumber":2.0,"sourceOrder":4,"isDownloaded":false}]}}}}"""
            }
            else -> """{"errors":[{"message":"Unrecognized source browser query"}]}"""
        }
        val bytes = response.toByteArray(Charsets.UTF_8)
        ctx.responseHeaders.add("Content-Type", "application/json")
        ctx.sendResponseHeaders(200, bytes.size.toLong())
        ctx.responseBody.use { it.write(bytes) }
    }
    server.start()
    try {
        val base = "http://127.0.0.1:${server.address.port}"
        val client = SuwayomiClient(base)
        val first = client.sources(0, 2)
        check(first.totalCount == 3 && first.sources.size == 2)
        check(first.sources[0].id == SOURCE_ID && first.sources[0].supportsLatest)
        check(first.sources[1].id == 42L && !first.sources[1].supportsLatest)
        val second = client.sources(2, 2)
        check(second.sources.single().name == "Gamma")
        val library = client.library(0, 2)
        check(library.totalCount == 3 && library.mangas.size == 2 && library.mangas.all { it.inLibrary })
        check(client.library(2, 2).mangas.single().id == 103)
        val popular = client.browse(SOURCE_ID, SuwayomiBrowseMode.POPULAR)
        check(popular.hasNextPage && popular.mangas.size == 2)
        check(popular.mangas[0].title == "One Piece" && popular.mangas[1].inLibrary)
        val latest = client.browse(SOURCE_ID, SuwayomiBrowseMode.LATEST, 2)
        check(latest.mangas.isEmpty() && !latest.hasNextPage)
        val searched = client.browse(SOURCE_ID, SuwayomiBrowseMode.SEARCH, 3, "Tokyo \"Cafe\"")
        check(searched.mangas.single().id == 103)
        client.refreshManga(101)
        check(runCatching { client.refreshManga(0) }.isFailure)
        check(runCatching { client.refreshManga(101, false, false) }.isFailure)
        val beforeInvalid = hits.get()
        check(runCatching { client.sources(-1, 200) }.isFailure)
        check(runCatching { client.sources(0, 201) }.isFailure)
        check(runCatching { client.library(-1, 100) }.isFailure)
        check(runCatching { client.library(0, 201) }.isFailure)
        check(runCatching { client.browse(0, SuwayomiBrowseMode.POPULAR) }.isFailure)
        check(runCatching { client.browse(SOURCE_ID, SuwayomiBrowseMode.SEARCH, 1, " ") }.isFailure)
        check(runCatching { client.browse(SOURCE_ID, SuwayomiBrowseMode.SEARCH, 1, "x\ny") }.isFailure)
        check(runCatching { client.browse(SOURCE_ID, SuwayomiBrowseMode.LATEST, 1, "query") }.isFailure)
        check(runCatching { client.browse(SOURCE_ID, SuwayomiBrowseMode.POPULAR, 0) }.isFailure)
        check(hits.get() == beforeInvalid) { "Invalid input must fail before hitting server" }
        val sourceCli = outputOf {
            SuwayomiBrowseCli.main(arrayOf("--base-url", base, "--mode", "sources",
                "--limit", "2", "--offset", "0"))
        }
        check(sourceCli.contains("Alpha (EN)") && sourceCli.contains("More sources"))
        val libraryCli = outputOf {
            SuwayomiBrowseCli.main(arrayOf("--base-url", base, "--mode", "library",
                "--limit", "2", "--offset", "0"))
        }
        check(libraryCli.contains("One Piece") && libraryCli.contains("More library titles"))
        val browseCli = outputOf {
            SuwayomiBrowseCli.main(arrayOf("--base-url", base, "--mode", "popular",
                "--source-id", SOURCE_ID.toString()))
        }
        check(browseCli.contains("One Piece") && browseCli.contains("More titles"))
        val chaptersCli = outputOf {
            SuwayomiBrowseCli.main(arrayOf("--base-url", base, "--mode", "chapters",
                "--manga-id", "101", "--refresh", "true"))
        }
        check(chaptersCli.contains("7001\tdownloaded") && chaptersCli.contains("7002\tnot-downloaded"))
        check(runCatching {
            SuwayomiBrowseCli.main(arrayOf("--base-url", base, "--mode", "search",
                "--source-id", SOURCE_ID.toString(), "--query", ""))
        }.isFailure)
        check(runCatching {
            SuwayomiBrowseCli.main(arrayOf("--base-url", base, "--mode", "sources",
                "--limit", "2", "--limit", "1"))
        }.isFailure)
        println("Suwayomi source browsing contract passed: GraphQL LongString signed source IDs, paginated installed sources and library, popular/latest/search, escaping, explicit refresh, chapters and CLI")
    } finally {
        server.stop(0)
    }
}
