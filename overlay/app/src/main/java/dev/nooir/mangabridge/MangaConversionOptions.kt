package dev.nooir.mangabridge

import android.content.SharedPreferences
import dev.nooir.bridge.ExportFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth

/**
 * Small, real subset of MangaPoint conversion controls.
 * These parameters all affect AndroidPageSource; unfinished options are not exposed.
 */
data class MangaConversionOptions(
    val preset: String = "Manga",
    val layout: String = "AUTO",          // AUTO, PAGED, WEBTOON
    val cropWhiteMargins: Boolean = false,
    val splitSpreads: Boolean = true,
    val rightToLeft: Boolean = true,
    val contrast: Int = 50,               // 50 is neutral; 0..100
    val dither: Boolean = true,           // false = nearest threshold
    val orientation: String = "PORTRAIT", // portrait panel / sideways reading
    val overlapPercent: Int = 8,         // repeating rows on long WEBTOON strips, 0..50
    val landscapeClockwise: Boolean = true, // turn content clockwise for XTC/XTCH
    val includeCover: Boolean = true,
    val epubColor: Boolean = false, // EPUB/CBZ only; XTC and XTCH remain grayscale
    val xtcExtendedMetadata: Boolean = false,
    // Internal one-job preflight decisions; only changed by explicit queue confirmation.
    val allowMissingCover: Boolean = false,
    val skipUnavailableChapters: Boolean = false,
    val epubPagesPerXhtml: Int = 1, // 0 = entire chapter, 1 = original, 2..999 = custom
) {
    fun token() = listOf(preset, layout, cropWhiteMargins, splitSpreads, rightToLeft,
        contrast.coerceIn(0, 100), dither, orientation, overlapPercent.coerceIn(0, 50),
        landscapeClockwise, includeCover, epubColor, epubPagesPerXhtml.coerceIn(0, 999), xtcExtendedMetadata,
        allowMissingCover, skipUnavailableChapters).joinToString("|")

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
            if (v.size != 7 && v.size != 10 && v.size != 11 && v.size != 12 && v.size != 13 && v.size != 14 && v.size != 15 && v.size != 16) return MangaConversionOptions()
            return MangaConversionOptions(
                preset = v[0].take(32),
                layout = v[1].takeIf { it in setOf("AUTO", "PAGED", "WEBTOON") } ?: "AUTO",
                cropWhiteMargins = v[2].toBoolean(),
                splitSpreads = v[3].toBoolean(),
                rightToLeft = v[4].toBoolean(),
                contrast = (v[5].toIntOrNull() ?: 50).coerceIn(0, 100),
                dither = v[6].toBoolean(),
                orientation = v.getOrNull(7)?.takeIf { it in setOf("PORTRAIT", "LANDSCAPE") }
                    ?: "PORTRAIT",
                overlapPercent = (v.getOrNull(8)?.toIntOrNull() ?: 8).coerceIn(0, 50),
                landscapeClockwise = v.getOrNull(9)?.toBooleanStrictOrNull() ?: true,
                includeCover = v.getOrNull(10)?.toBooleanStrictOrNull() ?: true,
                epubColor = v.getOrNull(11)?.toBooleanStrictOrNull() ?: false,
                epubPagesPerXhtml = (v.getOrNull(12)?.toIntOrNull() ?: 1).coerceIn(0, 999),
                xtcExtendedMetadata = v.getOrNull(13)?.toBooleanStrictOrNull() ?: false,
                allowMissingCover = v.getOrNull(14)?.toBooleanStrictOrNull() ?: false,
                skipUnavailableChapters = v.getOrNull(15)?.toBooleanStrictOrNull() ?: false,
            )
        }

        fun load(prefs: SharedPreferences): MangaConversionOptions = fromToken(prefs.getString("conversion_options", null))
    }
    fun save(prefs: SharedPreferences) {
        prefs.edit().putString("conversion_options", token()).apply()
    }
}

@Composable
fun MangaConversionSettings(options: MangaConversionOptions, format: ExportFormat, onChange: (MangaConversionOptions) -> Unit) {
    var epubExpanded by remember { mutableStateOf(false) }
    var xtcExpanded by remember { mutableStateOf(false) }
    var imageExpanded by remember { mutableStateOf(false) }
    val isXtc = format == ExportFormat.XTC || format == ExportFormat.XTCH
    val isEpub = format == ExportFormat.EPUB2 || format == ExportFormat.EPUB
    val supportsColor = isEpub || format == ExportFormat.CBZ
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Conversion preset")
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Manga", "Manhwa", "Manhua").forEach { name ->
                FilterChip(
                    selected = options.preset == name,
                    onClick = { onChange(MangaConversionOptions.preset(name)) },
                    label = { Text(name) },
                )
            }
        }
        Text("Layout")
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("AUTO" to "Auto", "PAGED" to "Pages", "WEBTOON" to "Webtoon").forEach { (value, title) ->
                FilterChip(
                    selected = options.layout == value,
                    onClick = { onChange(options.copy(preset = "Custom", layout = value)) },
                    label = { Text(title) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Switch(checked = options.includeCover, onCheckedChange = {
                onChange(options.copy(preset = "Custom", includeCover = it))
            })
            Text("Include separate series cover (if available)")
        }
        if (isXtc) {
        TextButton(onClick = { xtcExpanded = !xtcExpanded }) {
            Text(if (xtcExpanded) "▾ Hide XTC / XTCH metadata" else "▸ Show XTC / XTCH metadata")
        }
        if (xtcExpanded) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Switch(checked = options.xtcExtendedMetadata, onCheckedChange = {
                    onChange(options.copy(preset = "Custom", xtcExtendedMetadata = it))
                })
                Text("Include synopsis and extended metadata (experimental)")
            }
            Text("Off by default. Adds a JSON trailer; older readers may ignore it.")
        }
        }
        if (isEpub || supportsColor) {
        TextButton(onClick = { epubExpanded = !epubExpanded }) {
            Text(if (epubExpanded) "▾ Hide ${if (isEpub) "EPUB" else "CBZ"} settings" else "▸ Show ${if (isEpub) "EPUB" else "CBZ"} settings")
        }
        if (epubExpanded) {
        Text("EPUB / CBZ image colors")
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(false to "Grayscale (e-reader)", true to "Original color").forEach { (value, title) ->
                FilterChip(selected = options.epubColor == value,
                    onClick = { onChange(options.copy(preset = "Custom", epubColor = value)) },
                    label = { Text(title) })
            }
        }
        Text("EPUB and CBZ can retain original color. XTC/XTCH remain grayscale.")
            if (isEpub) {
            Text("EPUB 2 / EPUB 3 section grouping")
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(1 to "One page", 5 to "Custom", 0 to "Entire chapter").forEach { (value, label) ->
                FilterChip(
                    selected = if (value == 5) options.epubPagesPerXhtml > 1 else options.epubPagesPerXhtml == value,
                    onClick = { onChange(options.copy(preset = "Custom", epubPagesPerXhtml = value)) },
                    label = { Text(label) },
                )
            }
        }
        if (options.epubPagesPerXhtml > 1) {
            OutlinedTextField(
                value = options.epubPagesPerXhtml.toString(),
                onValueChange = { raw ->
                    raw.toIntOrNull()?.takeIf { it in 2..999 }?.let {
                        onChange(options.copy(preset = "Custom", epubPagesPerXhtml = it))
                    }
                },
                label = { Text("Pages per XHTML (2–999)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text("Entire chapter groups all its images in one XHTML. Experimental: larger sections may take longer to open on low-memory e-readers.")
            }
        }
        }
        TextButton(onClick = { imageExpanded = !imageExpanded }) {
            Text(if (imageExpanded) "▾ Hide image and layout controls" else "▸ Show image and layout controls")
        }
        if (imageExpanded) {
        Text("Page orientation")
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("PORTRAIT" to "Portrait", "LANDSCAPE" to "Landscape").forEach { (value, title) ->
                FilterChip(
                    selected = options.orientation == value,
                    onClick = { onChange(options.copy(preset = "Custom", orientation = value)) },
                    label = { Text(title) },
                )
            }
        }
        if (options.orientation == "LANDSCAPE") {
            Text(if (isXtc) "Turn the X3/X4 sideways to read landscape XTC/XTCH pages." else "Landscape exports use a wider page.")
            if (isXtc) Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Rotate content clockwise")
                Switch(checked = options.landscapeClockwise,
                    onCheckedChange = { onChange(options.copy(preset = "Custom", landscapeClockwise = it)) })
            }
        }
        if (options.layout != "PAGED" || options.orientation == "LANDSCAPE") {
            Text("Overlapping page cuts · " + options.overlapPercent + "%")
            Slider(
                value = options.overlapPercent.toFloat(),
                onValueChange = { onChange(options.copy(preset = "Custom", overlapPercent = it.toInt())) },
                valueRange = 0f..50f,
                steps = 49,
            )
            Text("Repeats the previous page's last lines on the next page, for webtoons and portrait pages read sideways. 0% disables overlap.")
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Trim white margins")
            Switch(checked = options.cropWhiteMargins,
                onCheckedChange = { onChange(options.copy(preset = "Custom", cropWhiteMargins = it)) })
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Split double-page spreads")
            Switch(checked = options.splitSpreads,
                onCheckedChange = { onChange(options.copy(preset = "Custom", splitSpreads = it)) })
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Read right to left")
            Switch(checked = options.rightToLeft,
                onCheckedChange = { onChange(options.copy(preset = "Custom", rightToLeft = it)) })
        }
        Text("Contrast · " + options.contrast + " (50 = original)")
        Slider(
            value = options.contrast.toFloat(),
            onValueChange = { onChange(options.copy(preset = "Custom", contrast = it.toInt())) },
            valueRange = 0f..100f,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Floyd–Steinberg dithering")
            Switch(checked = options.dither,
                onCheckedChange = { onChange(options.copy(preset = "Custom", dither = it)) })
        }
        }
        Text("Changes apply to the next conversion. Landscape preserves XTC panel dimensions and rotates its content; EPUB/CBZ use a wider page. Manual crop, zoom, preview and guide arrows are still planned.")
    }
}
