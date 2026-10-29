import dev.nooir.bridge.ExportFormat
import dev.nooir.mangabridge.MangaConversionOptions
import dev.nooir.desktop.ui.DesktopUiModel
import dev.nooir.desktop.ui.LocalComicPages
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
    } finally {
        folder.deleteRecursively()
    }
    // In headless CI we compile/load the real Swing GUI, but do not instantiate a JFrame.
    check(Class.forName("dev.nooir.desktop.ui.MangaNooirDesktop") != null)
    println("Desktop GUI model tests passed: chapter selection, format extensions and headless-safe class loading")
}
