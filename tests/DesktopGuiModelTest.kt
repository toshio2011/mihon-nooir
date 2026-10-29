import dev.nooir.bridge.ExportFormat
import dev.nooir.mangabridge.MangaConversionOptions
import dev.nooir.desktop.ui.DesktopUiModel
import dev.nooir.desktop.ui.LocalComicPages
import dev.nooir.desktop.ui.DesktopReadingLayout
import dev.nooir.desktop.ui.ReaderChapter
import dev.nooir.desktop.ui.ReaderChapterSequence
import dev.nooir.desktop.ui.LocalLibraryStore
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO
import dev.nooir.desktop.suwayomi.SuwayomiChapter

fun main() {
    val chapters = listOf(
        SuwayomiChapter(40, "Chapter 10", 10.0, 1, true),
        SuwayomiChapter(12, "Chapter 2", 2.0, 3, false),
        SuwayomiChapter(20, "Chapter 1", 1.0, 5, true),
    )
    check(DesktopUiModel.selectedChapterIds(chapters) == listOf(20, 12, 40))
    check(runCatching { DesktopUiModel.selectedChapterIds(emptyList()) }.isFailure)
    check(runCatching { DesktopUiModel.selectedChapterIds(chapters + chapters.first()) }.isFailure)
    check(DesktopUiModel.suggestedFile("Manga: Chapter/1", ExportFormat.EPUB2) == "Manga_ Chapter_1.epub")
    check(DesktopUiModel.suggestedFile("...", ExportFormat.XTCH) == "MangaNooir.xtch")
    check(DesktopUiModel.suggestedFile("X", ExportFormat.XTC) == "X.xtc")
    check(DesktopUiModel.suggestedFile("CON", ExportFormat.EPUB2) == "_CON.epub")
    check(DesktopUiModel.suggestedFile("AUX.txt", ExportFormat.XTCH) == "_AUX.txt.xtch")
    check(DesktopUiModel.suggestedFile("lpt9", ExportFormat.CBZ) == "_lpt9.cbz")
    check(DesktopUiModel.suggestedFile("Chapter. ", ExportFormat.EPUB2) == "Chapter.epub")
    check(DesktopUiModel.suggestedFile("  ", ExportFormat.CBZ) == "MangaNooir.cbz")
    check(DesktopUiModel.suggestedFile("東京の漫画", ExportFormat.EPUB2) == "東京の漫画.epub")
    check(runCatching { DesktopUiModel.selectedChapterIds((1..501).map {
        SuwayomiChapter(it, "Chapter $it", it.toDouble(), it, true)
    }) }.isFailure)
    // Windows and Android use one backwards-compatible settings token.
    // Panel/row modes, Auto Fit and overview must survive GUI-to-CLI handoff.
    for (mode in listOf("OFF", "ROW_BY_ROW", "PANEL_BY_PANEL")) {
        val settings = MangaConversionOptions(
            panelMode = mode,
            orientation = if (mode == "OFF") "PORTRAIT" else "AUTO_FIT",
            showFullPageBeforeSplits = mode != "OFF",
            keepChapterFirstPageWhole = false,
            panelDiagnostics = false,
        )
        val reloaded = MangaConversionOptions.fromToken(settings.token())
        check(reloaded.panelMode == mode)
        check(reloaded.orientation == settings.orientation)
        check(reloaded.showFullPageBeforeSplits == settings.showFullPageBeforeSplits)
        check(!reloaded.keepChapterFirstPageWhole)
        check(!reloaded.panelDiagnostics)
    }
    check(MangaConversionOptions.fromToken(null).panelMode == "OFF")
    // Single page, manga-like cover, two-page spread, odd final page and RTL
    // visual placement must all be deterministic and never skip a page.
    val layout = DesktopReadingLayout
    check(layout.visibleIndices(6, 0, true) == listOf(0))
    check(layout.visibleIndices(6, 1, true) == listOf(1, 2))
    check(layout.visibleIndices(6, 2, true) == listOf(1, 2))
    check(layout.visibleIndices(6, 3, true) == listOf(3, 4))
    check(layout.visibleIndices(6, 5, true) == listOf(5))
    check(layout.visibleIndices(6, 2, false) == listOf(2))
    check(layout.visibleIndices(6, 0, true, false) == listOf(0, 1))
    check(layout.visibleIndices(6, 3, true, false) == listOf(2, 3))
    check(layout.move(6, 0, 1, true) == 1)
    check(layout.move(6, 1, 1, true) == 3)
    check(layout.move(6, 3, -1, true) == 1)
    check(layout.move(6, 1, -1, true) == 0)
    check(layout.move(6, 5, 1, true) == 5)
    check(layout.move(6, 0, -1, true) == 0)
    check(layout.visualIndices(listOf(1, 2), true) == listOf(2, 1))
    check(layout.visualIndices(listOf(1, 2), false) == listOf(1, 2))
    check(runCatching { layout.visibleIndices(0, 0, true) }.isFailure)
    check(runCatching { layout.visibleIndices(2, 2, true) }.isFailure)
    // Across book lengths, forward navigation must cover each page exactly
    // once in reading order even when the last spread has only one page.
    for (count in 1..25) for (firstSolo in listOf(true, false)) {
        var position = 0
        val encountered = mutableListOf<Int>()
        while (true) {
            encountered += layout.visibleIndices(count, position, true, firstSolo)
            val nextPosition = layout.move(count, position, 1, true, firstSolo)
            if (nextPosition == position) break
            position = nextPosition
        }
        check(encountered == (0 until count).toList()) {
            "Double-page reader skipped or duplicated pages for count=$count, firstSolo=$firstSolo: $encountered"
        }
    }
    // Offline reader: pages sort naturally; archive entries are decoded
    // in-memory and unsafe ZIP paths are excluded.
    val folder = Files.createTempDirectory("mihonnooir-reader-smoke-").toFile()
    try {
        val page = BufferedImage(32, 48, BufferedImage.TYPE_INT_RGB).apply {
            val g = createGraphics()
            try { g.color = Color.RED; g.fillRect(0, 0, 32, 48) } finally { g.dispose() }
        }
        val raw = ByteArrayOutputStream().use {
            check(ImageIO.write(page, "png", it))
            it.toByteArray()
        }
        File(folder, "page 10.png").writeBytes(raw)
        File(folder, "page 2.png").writeBytes(raw)
        check(LocalComicPages.names(folder) == listOf("page 2.png", "page 10.png"))
        check(LocalComicPages.read(folder, "page 2.png").width == 32)
        check(runCatching { LocalComicPages.read(folder, "../wrong.png") }.isFailure)
        val zip = File(folder, "sample.cbz")
        ZipOutputStream(zip.outputStream()).use { z ->
            for (name in listOf("../secret.png", "page 10.png", "page 2.png")) {
                z.putNextEntry(ZipEntry(name))
                z.write(raw)
                z.closeEntry()
            }
        }
        check(LocalComicPages.names(zip) == listOf("page 2.png", "page 10.png"))
        check(LocalComicPages.read(zip, "page 10.png").height == 48)
        val sequence = ReaderChapterSequence("Fixture Manga", listOf(
            ReaderChapter(1, "Chapter 1", zip),
            ReaderChapter(2, "Missing chapter", File(folder, "missing.cbz")),
            ReaderChapter(3, "Chapter 3", zip),
        ), 0)
        check(sequence.adjacent(-1) == null)
        val chapter3 = sequence.adjacent(1)
        check(chapter3?.current?.id == 3) { "Chapter navigation should skip missing archives" }
        check(chapter3.adjacent(-1)?.current?.id == 1)
        check(chapter3.adjacent(1) == null)
        check(runCatching { sequence.adjacent(0) }.isFailure)
        check(runCatching {
            ReaderChapterSequence("Duplicate", listOf(
                ReaderChapter(1, "One", zip), ReaderChapter(1, "Two", zip)
            ), 0)
        }.isFailure)
        check(runCatching { LocalComicPages.read(zip, "../secret.png") }.isFailure) {
            "Direct ZIP image reads must refuse path traversal"
        }
        val library = LocalLibraryStore(File(folder, "user-library-index").toPath())
        library.add(folder)
        library.add(zip)
        library.add(zip) // idempotent, no duplicate items
        check(library.books().size == 2)
        check(library.books().all { it.available })
        library.markOpened(zip, 12345L)
        check(library.books().first().file.canonicalFile == zip.canonicalFile)
        check(library.books().first().lastOpenedAt == 12345L)
        val reloaded = LocalLibraryStore(File(folder, "user-library-index").toPath())
        check(reloaded.books().size == 2) { "Library must survive application restart" }
        val renamedZip = File(folder, "renamed.cbz")
        check(zip.renameTo(renamedZip))
        check(reloaded.books().any { !it.available && it.file.name == "sample.cbz" }) {
            "Missing books must remain visible for manual recovery/removal"
        }
        reloaded.remove(zip)
        check(renamedZip.exists()) { "Removing library entry must never delete original comic" }
        check(reloaded.books().size == 1)

    } finally {
        folder.deleteRecursively()
    }
    // In headless CI we compile/load the real Swing GUI, but do not instantiate a JFrame.
    check(Class.forName("dev.nooir.desktop.ui.MangaNooirDesktop") != null)
    println("Desktop GUI model tests passed: chapter selection, two-page RTL paging, across-chapter offline navigation, formats and headless GUI")
}
