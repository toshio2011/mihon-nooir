package dev.nooir.mangabridge

/**
 * Platform-neutral MangaNooir conversion settings and backwards-compatible job token.
 * Keep this shared between Android and Windows; Android preferences and Compose UI
 * remain in MangaConversionOptions.kt.
 */
data class MangaConversionOptions(
    val preset: String = "Manga",
    val layout: String = "AUTO",          // AUTO, PAGED, WEBTOON
    val cropWhiteMargins: Boolean = false,
    val splitSpreads: Boolean = true,
    val rightToLeft: Boolean = true,
    val contrast: Int = 50,               // 50 is neutral; 0..100
    val dither: Boolean = true,           // false = nearest threshold
    val orientation: String = "PORTRAIT", // PORTRAIT, LANDSCAPE, AUTO_FIT (panel-by-panel only)
    val overlapPercent: Int = 8,         // repeating rows on long WEBTOON strips, 0..50
    val landscapeClockwise: Boolean = true, // turn content clockwise for XTC/XTCH
    val includeCover: Boolean = true,
    val epubColor: Boolean = false, // EPUB/CBZ only; XTC and XTCH remain grayscale
    val xtcExtendedMetadata: Boolean = false,
    // Internal one-job preflight decisions; only changed by explicit queue confirmation.
    val allowMissingCover: Boolean = false,
    val skipUnavailableChapters: Boolean = false,
    val epubPagesPerXhtml: Int = 1, // 0 = entire chapter, 1 = original, 2..999 = custom
    val panelMode: String = "OFF", // OFF, ROW_BY_ROW or PANEL_BY_PANEL; opt-in and backward compatible
    val showFullPageBeforeSplits: Boolean = false, // add overview only when a source yields multiple frames
) {
    fun token() = listOf(preset, layout, cropWhiteMargins, splitSpreads, rightToLeft,
        contrast.coerceIn(0, 100), dither, orientation, overlapPercent.coerceIn(0, 50),
        landscapeClockwise, includeCover, epubColor, epubPagesPerXhtml.coerceIn(0, 999), xtcExtendedMetadata,
        allowMissingCover, skipUnavailableChapters, panelMode, showFullPageBeforeSplits).joinToString("|")

    companion object {
        fun preset(name: String): MangaConversionOptions = when (name) {
            "Manhwa" -> MangaConversionOptions(
                preset = name, layout = "WEBTOON", cropWhiteMargins = true,
                splitSpreads = false, rightToLeft = false, contrast = 55,
                overlapPercent = 10,
            )
            "Manhua" -> MangaConversionOptions(
                preset = name, layout = "AUTO", cropWhiteMargins = true,
                splitSpreads = true, rightToLeft = false, contrast = 60,
            )
            else -> MangaConversionOptions(preset = "Manga", layout = "PAGED", cropWhiteMargins = true)
        }

        fun fromToken(raw: String?): MangaConversionOptions {
            if (raw.isNullOrBlank()) return MangaConversionOptions()
            val v = raw.split("|")
            // Existing jobs/settings created by 1.0.4/1.0.5 stored seven tokens.
            // Preserve those values and migrate only the new options to defaults.
            if (v.size != 7 && v.size != 10 && v.size != 11 && v.size != 12 && v.size != 13 && v.size != 14 && v.size != 15 && v.size != 16 && v.size != 17 && v.size != 18) return MangaConversionOptions()
            return MangaConversionOptions(
                preset = v[0].take(32),
                layout = v[1].takeIf { it in setOf("AUTO", "PAGED", "WEBTOON") } ?: "AUTO",
                cropWhiteMargins = v[2].toBoolean(),
                splitSpreads = v[3].toBoolean(),
                rightToLeft = v[4].toBoolean(),
                contrast = (v[5].toIntOrNull() ?: 50).coerceIn(0, 100),
                dither = v[6].toBoolean(),
                orientation = v.getOrNull(7)?.takeIf { it in setOf("PORTRAIT", "LANDSCAPE", "AUTO_FIT") }
                    ?: "PORTRAIT",
                overlapPercent = (v.getOrNull(8)?.toIntOrNull() ?: 8).coerceIn(0, 50),
                landscapeClockwise = v.getOrNull(9)?.toBooleanStrictOrNull() ?: true,
                includeCover = v.getOrNull(10)?.toBooleanStrictOrNull() ?: true,
                epubColor = v.getOrNull(11)?.toBooleanStrictOrNull() ?: false,
                epubPagesPerXhtml = (v.getOrNull(12)?.toIntOrNull() ?: 1).coerceIn(0, 999),
                xtcExtendedMetadata = v.getOrNull(13)?.toBooleanStrictOrNull() ?: false,
                allowMissingCover = v.getOrNull(14)?.toBooleanStrictOrNull() ?: false,
                skipUnavailableChapters = v.getOrNull(15)?.toBooleanStrictOrNull() ?: false,
                panelMode = v.getOrNull(16)?.takeIf { it in setOf("PANEL_BY_PANEL", "ROW_BY_ROW") } ?: "OFF",
                showFullPageBeforeSplits = v.getOrNull(17)?.toBooleanStrictOrNull() ?: false,
            )
        }

    }
}
