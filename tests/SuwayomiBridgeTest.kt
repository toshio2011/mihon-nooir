import com.sun.net.httpserver.HttpServer
import dev.nooir.desktop.suwayomi.MiniJson
import dev.nooir.desktop.suwayomi.SuwayomiClient
import dev.nooir.desktop.suwayomi.SuwayomiToNooir
import dev.nooir.desktop.ui.OfflineChapterSaver
import dev.nooir.desktop.ui.LocalLibraryStore
import dev.nooir.desktop.ui.OfflineSeriesCatalog
import dev.nooir.desktop.ui.LocalComicPages
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO

private fun picture(seed: Int): ByteArray {
    val image = BufferedImage(90, 130, BufferedImage.TYPE_INT_RGB)
    for (y in 0 until image.height) for (x in 0 until image.width) {
        val gray = if ((x + y + seed) % 11 == 0) 0 else 255
        image.setRGB(x, y, (gray shl 16) or (gray shl 8) or gray)
    }
    return ByteArrayOutputStream().also { check(ImageIO.write(image, "png", it)) }.toByteArray()
}

private fun archive(): ByteArray = ByteArrayOutputStream().also { bytes ->
    ZipOutputStream(bytes).use { zip ->
        for ((name, seed) in listOf("page10.png" to 10, "page2.png" to 2)) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(picture(seed))
            zip.closeEntry()
        }
    }
}.toByteArray()

private fun zipText(file: File, name: String): String = ZipFile(file).use { zip ->
    zip.getInputStream(zip.getEntry(name)).bufferedReader().use { it.readText() }
}
private fun u16(b: ByteArray, i: Int) = (b[i].toInt() and 255) or ((b[i + 1].toInt() and 255) shl 8)

fun main() {
    check(MiniJson.parse("{\"a\":1,\"b\":[true,null,\"\\u263a\"]}") is Map<*, *>)
    check(MiniJson.parse(MiniJson.quote("hello \"\\\n")) == "hello \"\\\n")
    check(runCatching { MiniJson.parse("{\"x\":1,\"x\":2}") }.isFailure)
    check(runCatching { MiniJson.parse("{\"bad\":[}") }.isFailure)
    check(runCatching { SuwayomiClient("http://example.com:4567") }.isFailure)
    check(runCatching { SuwayomiClient("http://127.0.0.1:4567@evil.test:1234") }.isFailure)
    check(runCatching { SuwayomiClient("http://127.0.0.1:4567/sneaky") }.isFailure)

    val root = Files.createTempDirectory("nooir-suwayomi-test-").toFile()
    val downloaded = AtomicBoolean(false)
    val queued = AtomicInteger(0)
    val fetched = AtomicInteger(0)
    val cbz = archive()
    val cover = picture(17)
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/api/graphql") { ctx ->
        val request = ctx.requestBody.bufferedReader().use { it.readText() }
        val query = (MiniJson.parse(request) as Map<*, *>)["query"] as String
        val response = when {
            "enqueueChapterDownloads" in query -> {
                check("ids: [12]" in query)
                queued.incrementAndGet()
                downloaded.set(true)
                """{"data":{"enqueueChapterDownloads":{"clientMutationId":null}}}"""
            }
            "manga(id: 1)" in query -> {
                """{"data":{"manga":{"id":1,"title":"Mock Manga","author":"Mock Author","chapters":{"nodes":[{"id":11,"name":"Chapter 2","chapterNumber":2.0,"sourceOrder":2,"isDownloaded":true},{"id":12,"name":"Chapter 10","chapterNumber":10.0,"sourceOrder":1,"isDownloaded":${downloaded.get()}}]}}}}"""
            }
            else -> """{"errors":[{"message":"Unexpected GraphQL query"}]}"""
        }
        val bytes = response.toByteArray()
        ctx.responseHeaders.add("Content-Type", "application/json")
        ctx.sendResponseHeaders(200, bytes.size.toLong())
        ctx.responseBody.use { it.write(bytes) }
    }
    server.createContext("/api/v1/chapter/") { ctx ->
        fetched.incrementAndGet()
        check(ctx.requestURI.rawQuery == "markAsRead=false") { "Export must not mark chapter as read" }
        check(ctx.requestURI.path.endsWith("/11/download") || ctx.requestURI.path.endsWith("/12/download"))
        ctx.responseHeaders.add("Content-Type", "application/vnd.comicbook+zip")
        ctx.sendResponseHeaders(200, cbz.size.toLong())
        ctx.responseBody.use { it.write(cbz) }
    }
    server.createContext("/api/v1/manga/1/thumbnail") { ctx ->
        ctx.responseHeaders.add("Content-Type", "image/png")
        ctx.sendResponseHeaders(200, cover.size.toLong())
        ctx.responseBody.use { it.write(cover) }
    }
    server.start()
    try {
        val base = "http://127.0.0.1:${server.address.port}"
        val client = SuwayomiClient(base, "Bearer test")
        val manga = client.manga(1)
        check(manga.title == "Mock Manga" && manga.chapters.size == 2)
        SuwayomiToNooir.main(arrayOf("--base-url", base, "--manga-id", "1", "--mode", "list"))
        val epub = File(root, "sample.epub")
        val flags = arrayOf("--base-url", base, "--manga-id", "1", "--mode", "export",
            "--chapters", "12,11", "--format", "EPUB2", "--device", "X4", "--output", epub.absolutePath)
        check(runCatching { SuwayomiToNooir.main(flags) }.isFailure) {
            "Undownloaded chapter must not be fetched without explicit consent"
        }
        check(queued.get() == 0 && fetched.get() == 0)
        SuwayomiToNooir.main(flags + arrayOf("--download-missing", "true", "--wait-seconds", "3"))
        check(queued.get() == 1 && fetched.get() == 2)
        check(epub.length() > 100)
        val toc = zipText(epub, "OEBPS/toc.ncx")
        check(toc.contains("Chapter 2") && toc.contains("Chapter 10")) { "Original chapter titles lost" }
        check(toc.indexOf("Chapter 2") < toc.indexOf("Chapter 10")) { "Chapter ordering lost" }
        val xtch = File(root, "sample.xtch")
        SuwayomiToNooir.main(arrayOf("--base-url", base, "--manga-id", "1", "--mode", "export",
            "--chapters", "11,12", "--format", "XTCH", "--device", "X4", "--output", xtch.absolutePath))
        val bytes = xtch.readBytes()
        check(String(bytes.copyOfRange(0, 4)) == "XTCH")
        check(u16(bytes, 6) == 5) { "Expected cover + four chapter pages" }
        check(u16(bytes, 56 + 246) == 2) { "Expected two chapters" }
        for ((format, extension) in listOf("EPUB" to "epub", "XTC" to "xtc", "CBZ" to "cbz")) {
            val file = File(root, "from-suwayomi-$format.$extension")
            SuwayomiToNooir.main(arrayOf("--base-url", base, "--manga-id", "1", "--mode", "export",
                "--chapters", "11,12", "--format", format, "--device", "X4", "--output", file.absolutePath))
            check(file.length() > 100)
            if (format == "XTC") check(u16(file.readBytes(), 6) == 5)
            else ZipFile(file).use { zip ->
                check(zip.getEntry(if (format == "CBZ") "ComicInfo.xml" else "OEBPS/package.opf") != null)
            }
        }
        check(queued.get() == 1) { "Already-downloaded chapters should not be queued again" }

        // Real locally served CBZ bytes -> Windows offline bookshelf. Downloads
        // are explicit and never trigger source enqueue without user action.
        val offlineIndex = File(root, "offline-library-index").toPath()
        val offlineLibrary = LocalLibraryStore(offlineIndex)
        val groupedCatalog = OfflineSeriesCatalog(File(root, "offline-series-index").toPath())
        val offlineSaver = OfflineChapterSaver(offlineLibrary, File(root, "offline-books").toPath(), groupedCatalog)
        check(runCatching { offlineSaver.save(client, 1, listOf(11, 11)) }.isFailure)
        check(runCatching { offlineSaver.save(client, 1, listOf(999)) }.isFailure)
        val savedChapters = offlineSaver.save(client, 1, listOf(11, 12))
        check(savedChapters.size == 2 && savedChapters.all { it.isFile })
        check(offlineLibrary.books().size == 2)
        val series = groupedCatalog.series()
        check(series.size == 1 && series.first().mangaId == 1 && series.first().title == "Mock Manga")
        check(series.first().chapters.map { it.chapterId } == listOf(11, 12)) {
            "Saved chapter numbers must appear in reading order"
        }
        check(series.first().chapters.map { it.chapterTitle } == listOf("Chapter 2", "Chapter 10"))
        check(series.first().chapters.all { it.available })
        val restartedCatalog = OfflineSeriesCatalog(File(root, "offline-series-index").toPath())
        check(restartedCatalog.series().first().chapters.size == 2) {
            "Series grouping and chapter metadata must persist across Windows restarts"
        }
        check(LocalComicPages.names(savedChapters.first()) == listOf("page2.png", "page10.png"))
        val fetchCount = fetched.get()
        check(offlineSaver.save(client, 1, listOf(11, 12)).size == 2)
        check(fetched.get() == fetchCount) { "Existing offline CBZ must be reused without network download" }
        check(groupedCatalog.series().first().chapters.size == 2) {
            "Repeated chapter imports must not create duplicate series entries"
        }
        check(queued.get() == 1) { "Offline book save must never enqueue source downloads" }
        restartedCatalog.forget(1, 11)
        check(restartedCatalog.series().first().chapters.map { it.chapterId } == listOf(12)) {
            "Removing a grouped chapter must not remove unrelated chapters"
        }
        check(savedChapters.first().exists()) { "Removing grouped metadata must not delete CBZ" }
        check(LocalLibraryStore(offlineIndex).books().size == 2) {
            "Imported chapters must remain in local library after Windows restart"
        }
        check(File(root, "offline-books").listFiles().orEmpty().none {
            it.name.startsWith(".chapter-")
        }) { "Offline save must not leave partial chapter download files" }

        check(runCatching { SuwayomiToNooir.main(flags) }.isFailure) { "Output collision must fail" }
        val bad = File(root, "unsafe.cbz")
        ZipOutputStream(bad.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("../evil.png"))
            zip.write(picture(1))
            zip.closeEntry()
        }
        val destination = File(root, "extracted").apply { mkdir() }
        check(runCatching { SuwayomiToNooir.extractChapter(bad, destination) }.isFailure)
        check(!File(root, "evil.png").exists())
        check(root.listFiles().orEmpty().none { it.name.startsWith(".nooir-suwayomi-") }) {
            "Temporary downloaded archives should be removed"
        }
        println("Suwayomi D0 mock contract passed: GraphQL, explicit download, CBZ, cover, EPUB2/3, XTC/XTCH, CBZ output, ordering, safe ZIP, no-overwrite")
    } finally {
        server.stop(0)
        root.deleteRecursively()
    }
}
