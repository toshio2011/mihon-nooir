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
            // Explicit server progress and real pinned-server backup API.
            val opened=api.openOnlineChapter(manga.id,manga.chapters.first().id)
            check(api.saveReadingProgress(opened,1)==1)
            check(api.saveReadingProgress(opened,0)==1) { "Earlier page must not intentionally rewind server progress" }
            val backup=DesktopBackupTransport(endpoint)
            val portable=root.resolve("portable-test.tachibk").toFile()
            backup.export(portable,includeExtensionStores=true)
            val summary=DesktopBackupInspector.inspect(portable)
            check(summary.favorites==1 && summary.chapters==2 && summary.progressed>=1) { "Portable backup missing synthetic progress" }
            backup.validate(portable)
            check(!api.setMangaInLibrary(card.id, false))
            check(api.library(0,100).mangas.none{it.id==card.id})
            val recovery=root.resolve("recovery-test.tachibk").toFile()
            val ticket=backup.beginRestore(portable,recovery)
            check(backup.await(ticket)=="SUCCESS") { "Real server restore did not report success" }
            check(recovery.isFile && api.library(0,100).mangas.any{it.id==card.id})
            val restored=api.openOnlineChapter(manga.id,manga.chapters.first().id)
            check(restored.lastPageRead>=1) { "Server progress absent after restore" }
            println("PASS: real pinned-server portable backup, validation, recovery and library restore; explicit progress checkpoint")
            check(!api.setMangaInLibrary(card.id, false))
            check(api.library(0, 100).mangas.none { it.id == card.id })
            println("PASS: real pinned Suwayomi Local source search, chapter discovery, library membership and four ordered page decodes")
            println("No third-party extensions, external manga, user credentials or normal data directory used.")
        } finally {
            manager.close()
        }
        check(!manager.running) { "Managed Suwayomi process survived close()" }
        println("PASS: MihonNooir shut down only its own managed Suwayomi server")
    }
}
