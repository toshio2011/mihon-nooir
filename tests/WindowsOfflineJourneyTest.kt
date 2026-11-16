import com.sun.net.httpserver.HttpServer
import dev.nooir.desktop.NooirDesktopCli
import dev.nooir.desktop.suwayomi.SuwayomiClient
import dev.nooir.desktop.ui.LocalComicPages
import dev.nooir.desktop.ui.LocalLibraryStore
import dev.nooir.desktop.ui.OfflineChapterSaver
import dev.nooir.desktop.ui.OfflineJobState
import dev.nooir.desktop.ui.OfflineSaveQueue
import dev.nooir.desktop.ui.OfflineSeriesCatalog
import dev.nooir.desktop.ui.RemoteChapterOfflineHandoff
import dev.nooir.desktop.ui.RemoteChapterQueue
import dev.nooir.desktop.ui.RemoteChapterRunner
import dev.nooir.desktop.ui.RemoteChapterState
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO

/**
 * Synthetic legal end-to-end Windows workflow, using the actual desktop queue,
 * Suwayomi HTTP client, offline saver/library, reader page decoder and exporter.
 *
 * Not a test of any real manga source/extension. No online copyrighted content.
 * All mutations are explicitly claimed; offline reading/conversion must not
 * contact Suwayomi, even after its local test server is stopped.
 */
fun main() {
    val home = Files.createTempDirectory("nooir-windows-journey-")
    val metadata = home.resolve("index")
    val chaptersDir = home.resolve("chapters")
    val downloaded = AtomicBoolean(false)
    val mutations = AtomicInteger()
    val cbzTransfers = AtomicInteger()
    val mangaQueries = AtomicInteger()

    fun png(seed: Int): ByteArray {
        val image = BufferedImage(96, 144, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until image.height) for (x in 0 until image.width) {
            val gray = if ((x * 3 + y + seed) % 17 == 0) 0 else 255
            image.setRGB(x, y, (gray shl 16) or (gray shl 8) or gray)
        }
        return ByteArrayOutputStream().also { check(ImageIO.write(image, "png", it)) }.toByteArray()
    }

    val originalCbz = ByteArrayOutputStream().also { bytes ->
        ZipOutputStream(bytes).use { zip ->
            for ((name, seed) in listOf("page10.png" to 10, "page2.png" to 2)) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(png(seed))
                zip.closeEntry()
            }
        }
    }.toByteArray()

    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/api/graphql") { ctx ->
        val body = ctx.requestBody.bufferedReader().use { it.readText() }
        val response = when {
            "enqueueChapterDownloads" in body -> {
                check("ids: [101]" in body) { "Only explicitly approved chapter 101 may be queued" }
                mutations.incrementAndGet()
                downloaded.set(true)
                """{"data":{"enqueueChapterDownloads":{"clientMutationId":null}}}"""
            }
            "manga(id: 7)" in body -> {
                mangaQueries.incrementAndGet()
                """{"data":{"manga":{"id":7,"title":"Original Test Manga","author":"",
                    "chapters":{"nodes":[{"id":101,"name":"Chapter 1",
                    "chapterNumber":1.0,"sourceOrder":1,
                    "isDownloaded":${downloaded.get()}}]}}}}"""
            }
            else -> error("Unexpected GraphQL request, no sources/extensions can be installed")
        }
        val data = response.toByteArray(Charsets.UTF_8)
        ctx.responseHeaders.add("Content-Type", "application/json")
        ctx.sendResponseHeaders(200, data.size.toLong())
        ctx.responseBody.use { it.write(data) }
    }
    server.createContext("/api/v1/chapter/101/download") { ctx ->
        check(downloaded.get()) { "Cannot copy chapter that was never downloaded" }
        check(ctx.requestURI.rawQuery == "markAsRead=false") { "Must never mark chapter read" }
        cbzTransfers.incrementAndGet()
        ctx.responseHeaders.add("Content-Type", "application/vnd.comicbook+zip")
        ctx.sendResponseHeaders(200, originalCbz.size.toLong())
        ctx.responseBody.use { it.write(originalCbz) }
    }

    server.start()
    try {
        val endpoint = "http://127.0.0.1:${server.address.port}"
        val client = SuwayomiClient(endpoint)
        val remote = RemoteChapterQueue(metadata)
        val offline = OfflineSaveQueue(metadata)
        val library = LocalLibraryStore(metadata)
        val catalog = OfflineSeriesCatalog(metadata)
        val saver = OfflineChapterSaver(library, chaptersDir, catalog)
        val handoff = RemoteChapterOfflineHandoff(remote, offline)

        val job = remote.add(endpoint, 7, listOf(101)).single()
        check(job.state == RemoteChapterState.PENDING)
        check(mutations.get() == 0 && mangaQueries.get() == 0 && cbzTransfers.get() == 0) {
            "Queueing must not start source downloads, metadata requests or CBZ copies"
        }
        check(runCatching { handoff.queueDownloaded(endpoint, listOf(job.id)) }.isFailure) {
            "Not-downloaded remote chapter must not be handed to offline copy"
        }

        // A real user action must explicitly claim and submit this one chapter.
        val claimed = checkNotNull(remote.claim(endpoint))
        check(RemoteChapterRunner(remote, client, pollIntervalMs = 0, maxPolls = 3) {}
            .runClaimed(claimed, stop = { false }))
        check(remote.list().single().state == RemoteChapterState.DOWNLOADED)
        check(mutations.get() == 1 && cbzTransfers.get() == 0)

        val offlineJob = handoff.queueDownloaded(endpoint, listOf(job.id)).single()
        check(offlineJob.state == OfflineJobState.PENDING)
        check(handoff.queueDownloaded(endpoint, listOf(job.id)).single().id == offlineJob.id)
        check(cbzTransfers.get() == 0) {
            "Offline handoff must require a second user approval to copy CBZ"
        }

        // Simulate the separate 'Run pending' action.
        val copy = checkNotNull(offline.claimNext(endpoint))
        check(copy.id == offlineJob.id)
        val saved = saver.save(client, copy.mangaId, listOf(copy.chapterId)).single()
        offline.complete(copy.id)
        check(offline.list().single().state == OfflineJobState.COMPLETED)
        check(cbzTransfers.get() == 1 && saved.isFile)
        check(LocalComicPages.names(saved) == listOf("page2.png", "page10.png"))
        val decoded = LocalComicPages.read(saved, "page2.png")
        check(decoded.width == 96 && decoded.height == 144)
        check(library.books().single().available)
        check(catalog.series().single().chapters.single().file.canonicalFile == saved.canonicalFile)

        // Reopening metadata must preserve the actual source chapter without a
        // remote query. Recopying an existing validated archive must be idempotent.
        check(LocalLibraryStore(metadata).books().single().available)
        check(OfflineSeriesCatalog(metadata).series().single().chapters.single().available)
        saver.save(client, 7, listOf(101))
        check(cbzTransfers.get() == 1) { "Repeat import unexpectedly copied an existing CBZ" }
        val remoteBeforeOffline = mangaQueries.get()
        server.stop(0)

        val epub = home.resolve("opened-offline.epub").toFile()
        NooirDesktopCli.main(arrayOf("--input", saved.absolutePath,
            "--output", epub.absolutePath, "--format", "EPUB2", "--device", "X4"))
        ZipFile(epub).use { archive ->
            check(archive.getEntry("mimetype") != null)
            check(archive.getEntry("OEBPS/package.opf") != null)
            check(archive.getEntry("OEBPS/toc.ncx") != null)
        }
        val xtc = home.resolve("opened-offline.xtc").toFile()
        NooirDesktopCli.main(arrayOf("--input", saved.absolutePath,
            "--output", xtc.absolutePath, "--format", "XTC", "--device", "X4"))
        check(xtc.length() > 100 && String(xtc.readBytes().copyOfRange(0, 3)) == "XTC")
        check(mutations.get() == 1 && mangaQueries.get() == remoteBeforeOffline &&
            cbzTransfers.get() == 1) {
            "Offline import/reader/export must not re-submit a source download"
        }
        check(runCatching {
            NooirDesktopCli.main(arrayOf("--input", saved.absolutePath,
                "--output", epub.absolutePath, "--format", "EPUB2", "--device", "X4"))
        }.isFailure) { "Offline export must not overwrite an existing output" }

        println("PASS: approved localhost download -> durable remote queue -> separately approved CBZ copy")
        println("PASS: offline library, source chapter metadata, natural page decoding, restart and EPUB2/XTC")
        println("PASS: no network/source mutation for local reading/export after Suwayomi is stopped")
    } finally {
        server.stop(0)
        home.toFile().deleteRecursively()
    }
}
