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
    // In headless CI we compile/load the real Swing GUI, but do not instantiate a JFrame.
    check(Class.forName("dev.nooir.desktop.ui.MangaNooirDesktop") != null)
    println("Desktop GUI model tests passed: chapter selection, format extensions and headless-safe class loading")
}
