import dev.nooir.bridge.ExportFormat
import dev.nooir.desktop.ui.DesktopUiModel
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
    // In headless CI we compile/load the real Swing GUI, but do not instantiate a JFrame.
    check(Class.forName("dev.nooir.desktop.ui.MangaNooirDesktop") != null)
    println("Desktop GUI model tests passed: chapter selection, format extensions and headless-safe class loading")
}
