import dev.nooir.mangabridge.MangaConversionOptions

fun main() {
    val defaults = MangaConversionOptions()
    check(MangaConversionOptions.fromToken(null) == defaults)
    check(MangaConversionOptions.fromToken("bad") == defaults)
    check(MangaConversionOptions.fromToken(defaults.token()) == defaults)
    for (preset in listOf("Manga", "Manhwa", "Manhua")) {
        val original = MangaConversionOptions.preset(preset)
        check(MangaConversionOptions.fromToken(original.token()) == original)
    }
    val custom = MangaConversionOptions(
        preset = "Custom", layout = "WEBTOON", cropWhiteMargins = true,
        splitSpreads = false, rightToLeft = false, contrast = 73, dither = false,
        orientation = "LANDSCAPE", overlapPercent = 17, landscapeClockwise = false,
        includeCover = false, epubColor = true, xtcExtendedMetadata = true,
        allowMissingCover = true, skipUnavailableChapters = true, epubPagesPerXhtml = 0,
    )
    check(MangaConversionOptions.fromToken(custom.token()) == custom)
    val panel = custom.copy(panelMode = "PANEL_BY_PANEL")
    check(MangaConversionOptions.fromToken(panel.token()) == panel)
    check(panel.token().split("|").size == 22)
    val autoFit = panel.copy(orientation = "AUTO_FIT")
    check(MangaConversionOptions.fromToken(autoFit.token()) == autoFit)
    check(autoFit.token().split("|").size == 22) { "New modes must preserve existing diagnostic flags" }
    check(MangaConversionOptions.fromToken(custom.token()).orientation == "LANDSCAPE")
    val debug = panel.copy(panelDiagnostics = true)
    check(MangaConversionOptions.fromToken(debug.token()) == debug)
    check(!MangaConversionOptions.fromToken(debug.token().split("|").take(19).joinToString("|")).panelDiagnostics)
    check(!MangaConversionOptions.fromToken(defaults.token().substringBeforeLast("|")).panelDiagnostics)

    check(MangaConversionOptions.fromToken(custom.token().substringBeforeLast("|")).keepChapterFirstPageWhole)
    check(MangaConversionOptions.fromToken(panel.token().substringBeforeLast("|") + "|UNKNOWN").keepChapterFirstPageWhole)
    check(MangaConversionOptions.fromToken(null).panelMode == "OFF")
    val overview = panel.copy(showFullPageBeforeSplits = true)
    check(MangaConversionOptions.fromToken(overview.token()) == overview)
    check(MangaConversionOptions.fromToken(overview.token().substringBeforeLast("|")).panelMode == "PANEL_BY_PANEL")
    check(!MangaConversionOptions.fromToken(overview.copy(keepChapterFirstPageWhole = false).token()).keepChapterFirstPageWhole)
    check(MangaConversionOptions.fromToken(overview.token().substringBeforeLast("|")).keepChapterFirstPageWhole)
    val legacySeven = "Manga|PAGED|true|false|true|40|false"
    val migrated = MangaConversionOptions.fromToken(legacySeven)
    check(migrated.layout == "PAGED" && migrated.cropWhiteMargins)
    check(!migrated.splitSpreads && migrated.rightToLeft && migrated.contrast == 40)
    check(!migrated.dither && migrated.orientation == "PORTRAIT")
    check(migrated.epubPagesPerXhtml == 1 && !migrated.xtcExtendedMetadata)
    val legacyTen = legacySeven + "|LANDSCAPE|12|false"
    val migratedTen = MangaConversionOptions.fromToken(legacyTen)
    check(migratedTen.orientation == "LANDSCAPE" && migratedTen.overlapPercent == 12)
    check(!migratedTen.landscapeClockwise && migratedTen.includeCover)
    val invalid = MangaConversionOptions.fromToken(
        "Custom|INVALID|true|true|true|999|true|INVALID|999|maybe|maybe|maybe|9999|maybe|maybe|maybe"
    )
    check(invalid.layout == "AUTO" && invalid.orientation == "PORTRAIT")
    check(invalid.contrast == 100 && invalid.overlapPercent == 50)
    check(invalid.epubPagesPerXhtml == 999)
    check(invalid.landscapeClockwise && invalid.includeCover && !invalid.epubColor)
    // Existing 20-field jobs migrate with their EXACT prior Floyd/threshold
    // behavior; no output may change until a user selects a new algorithm.
    val legacyTwenty = panel.token().split("|").take(20).joinToString("|")
    val old = MangaConversionOptions.fromToken(legacyTwenty)
    check(old.panelMode == "PANEL_BY_PANEL")
    check(old.renderAlgorithm == "LEGACY" && old.contextPeekPercent == 0)
    check(old.dither == panel.dither)
    val algorithms = listOf("FLOYD_STEINBERG", "ATKINSON", "BAYER_8X8", "THRESHOLD")
    for (mode in algorithms) {
        val v = panel.copy(renderAlgorithm = mode, contextPeekPercent = 6)
        check(MangaConversionOptions.fromToken(v.token()) == v)
        val legacyTwentyOne = v.token().split("|").take(21).joinToString("|")
        val restored = MangaConversionOptions.fromToken(legacyTwentyOne)
        check(restored.renderAlgorithm == mode && restored.contextPeekPercent == 0)
    }
    check(MangaConversionOptions.fromToken(panel.copy(
        contextPeekPercent = 99).token()).contextPeekPercent == 8)
    check(MangaConversionOptions.fromToken(panel.copy(
        renderAlgorithm = "INVALID").token()).renderAlgorithm == "LEGACY")
    println("Shared Android/desktop conversion settings parity, rendering and legacy migration passed")
}
