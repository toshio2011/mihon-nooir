package dev.nooir.desktop.suwayomi

import dev.nooir.desktop.DesktopImageDecoder
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

/**
 * CI-only real pinned-server test with an isolated data root and synthetic local manga.
 * Never reads the user's normal Suwayomi data or installs external extensions.
 */
object RealSuwayomiServerSmoke {
    private const val TITLE = "Nooir Synthetic Server Contract"
    private val colors = listOf(Color.RED, Color.BLUE)

    private fun createFixture(root: Path) {
        for (chapter in 1..2) {
            val folder = root.resolve("local").resolve(TITLE).resolve("Chapter %02d".format(chapter))
            Files.createDirectories(folder)
            colors.forEachIndexed { page, color ->
                val image = BufferedImage(64, 96, BufferedImage.TYPE_INT_RGB)
                val g = image.createGraphics()
                try {
                    g.color = color
                    g.fillRect(0, 0, image.width, image.height)
                    g.color = Color.WHITE
                    g.fillRect(chapter * 5, page * 5, 3, 3)
                    check(ImageIO.write(image, "png", folder.resolve("page%02d.png".format(page + 1)).toFile()))
                } finally {
                    g.dispose()
                    image.flush()
                }
            }
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 1) { "Pass the verified official Suwayomi server JAR path" }
        val jar = File(args.single()).canonicalFile
        val root = Files.createTempDirectory("nooir-suwayomi-ci-")
        println("Isolated real-server fixture and log directory: $root")
        createFixture(root)
        val manager = SuwayomiProcessManager(root = root)
        try {
            val endpoint = manager.start(jar, startupTimeoutSeconds = 150)
            check(endpoint == "http://127.0.0.1:4567" && manager.running)
            val api = SuwayomiClient(endpoint)
            val sources = api.sources(offset = 0, limit = 100)
            check(sources.sources.any { it.id == 0L }) { "Built-in Local source missing" }
            check(api.library(offset = 0, limit = 100).mangas.isEmpty()) { "Expected isolated empty library" }
            val card = api.browse(0L, SuwayomiBrowseMode.SEARCH, searchTerm = TITLE)
                .mangas.single { it.title == TITLE }
            api.refreshManga(card.id)
            val manga = api.manga(card.id)
            check(manga.id == card.id && manga.chapters.size == 2) { "Expected two synthetic chapters" }
            check(api.setMangaInLibrary(card.id, true))
            check(api.library(0, 100).mangas.any { it.id == card.id })
            for (chapter in manga.chapters) {
                val opened = api.openOnlineChapter(manga.id, chapter.id)
                check(opened.pageCount == 2 && opened.syncConflict == null)
                colors.forEachIndexed { page, color ->
                    val decoded = DesktopImageDecoder.decode(api.onlinePage(opened, page))
                    try {
                        check(decoded.width == 64 && decoded.height == 96) { "Wrong page dimensions" }
                        check(decoded.getRGB(40, 80) == color.rgb) { "Wrong page content/order" }
                    } finally {
                        decoded.flush()
                    }
                }
            }
            check(!api.setMangaInLibrary(card.id, false))
            check(api.library(0, 100).mangas.none { it.id == card.id })
            println("PASS: real pinned Suwayomi Local source search, chapter discovery, library membership and four ordered page decodes")
            verifyBackupRoundTrip(root, endpoint, api)
            check(api.setMangaInLibrary(card.id, true))
            val progressChapter = api.openOnlineChapter(manga.id, manga.chapters.first().id)
            check(api.saveOnlinePosition(progressChapter, 1)==1)
            check(api.openOnlineChapter(manga.id,progressChapter.chapterId).lastPageRead==1)
            check(runCatching{api.saveOnlinePosition(progressChapter,0)}.isFailure)
            val progressExport=root.resolve("with-desktop-progress.tachibk").toFile()
            DesktopBackupTransport(endpoint).export(progressExport)
            check(DesktopBackupInspector.inspect(progressExport).progressed>=2)
            println("PASS: real pinned server explicit desktop page write-back and portable backup progress; rewind refused")
            println("No third-party extensions, external manga, user credentials or normal data directory used.")
        } catch (failure: Throwable) {
            // This root contains only the isolated synthetic fixture. Keep the
            // original failure and expose bounded backend diagnostics for CI.
            runCatching {
                val log = root.resolve("server-output.log").toFile()
                if (log.isFile) {
                    val tail = java.io.RandomAccessFile(log,"r").use {
                        val size = minOf(it.length(), 64L * 1024).toInt()
                        it.seek(it.length()-size)
                        ByteArray(size).also { bytes -> it.readFully(bytes) }.toString(Charsets.UTF_8)
                    }
                    System.err.println("BEGIN isolated synthetic Suwayomi log tail")
                    System.err.println(tail)
                    System.err.println("END isolated synthetic Suwayomi log tail")
                }
            }
            throw failure
        } finally {
            manager.close()
        }
        check(!manager.running) { "Managed Suwayomi process survived close()" }
        println("PASS: MihonNooir shut down only its own managed Suwayomi server")
    }
    private fun verifyBackupRoundTrip(root: Path, endpoint: String, api: SuwayomiClient) {
        val backup = DesktopBackupTransport(endpoint)
        val input = root.resolve("synthetic-android.tachibk").toFile()
        val recovery = root.resolve("before-synthetic-restore.tachibk").toFile()
        val exported = root.resolve("synthetic-desktop.tachibk").toFile()
        val duplicateRecovery = root.resolve("before-repeat-restore.tachibk").toFile()
        val repeated = root.resolve("after-repeat-restore.tachibk").toFile()
        // Independently encoded Android wire fixture: deliberately distinct category ID/order,
        // negative source hash, and real chapter flags. No external URLs or extensions.
        val source = -456789123L
        val title = "Nooir Synthetic Backup Round Trip"
        val category = "Nooir Synthetic Category"
        fun v(n:Long):ByteArray {
            var x=n; val b=java.io.ByteArrayOutputStream()
            while(x ushr 7!=0L) { b.write((x.toInt() and 127) or 128); x=x ushr 7 }
            b.write(x.toInt());return b.toByteArray()
        }
        fun num(n:Int,x:Long)=v(n*8L)+v(x)
        fun msg(n:Int,x:ByteArray)=v(n*8L+2)+v(x.size.toLong())+x
        fun str(n:Int,x:String)=msg(n,x.toByteArray(Charsets.UTF_8))
        val chapterUrl="/synthetic/chapter/1"
        val chapter=msg(16,str(1,chapterUrl)+str(2,"Chapter 1")+num(4,1)+num(5,1)+num(6,7)+num(10,1))
        val history=msg(104,str(1,chapterUrl)+num(2,1700000000000L))
        val manga=msg(1,num(1,source)+str(2,"/synthetic/manga")+str(3,title)+chapter+num(17,4)+history)
        val cat=msg(2,str(1,category)+num(2,4)+num(3,99))
        val src=msg(101,str(1,"Synthetic missing source")+num(2,source))
        java.util.zip.GZIPOutputStream(input.outputStream()).use{it.write(manga+cat+src)}
        check(backup.validate(input).missingSources.any{it.id==source}) { "Missing signed source was not reported" }
        val firstTicket=backup.beginRestore(input,recovery)
        val firstState=backup.await(firstTicket)
        check(firstState=="SUCCESS") { "First restore "+firstTicket.id+" ended in "+firstState+"; inspect synthetic server log" }
        check(recovery.isFile && recovery.length()>0)
        check(api.library(0,100).mangas.count{it.title==title}==1) { "Restore reported success but synthetic title missing or duplicated" }
        backup.export(exported,includeExtensionStores=true)
        val summary=DesktopBackupInspector.inspect(exported)
        check(summary.favorites==1 && summary.categories>=1 && summary.chapters==1 &&
            summary.read==1 && summary.bookmarks==1 && summary.progressed==1 && summary.history==1 &&
            source in summary.referencedSourceIds) { "Exported library did not preserve the expected synthetic chapter metadata" }
        val repeatTicket=backup.beginRestore(input,duplicateRecovery)
        check(repeatTicket.id!=firstTicket.id) { "Server reused restore ID" }
        val repeatState=backup.await(repeatTicket)
        check(repeatState=="SUCCESS") { "Repeat restore "+repeatTicket.id+" ended in "+repeatState+"; inspect synthetic server log" }
        check(api.library(0,100).mangas.count{it.title==title}==1) { "Repeated import duplicated library title" }
        backup.export(repeated,includeExtensionStores=true)
        val after=DesktopBackupInspector.inspect(repeated)
        check(after.favorites==summary.favorites && after.chapters==summary.chapters &&
            after.read==summary.read && after.bookmarks==summary.bookmarks && after.progressed==summary.progressed &&
            after.history==summary.history) { "Repeat restore changed expected record counts" }
        println("PASS: real pinned server Android fixture validation, recovery backup, library restore/export, missing signed source, chapter flags and repeated import")
    }

}
