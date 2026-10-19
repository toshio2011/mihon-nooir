package dev.nooir.mangabridge

import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
) {
    fun token() = listOf(preset, layout, cropWhiteMargins, splitSpreads, rightToLeft,
        contrast.coerceIn(0, 100), dither, orientation, overlapPercent.coerceIn(0, 50),
        landscapeClockwise).joinToString("|")

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
            if (v.size != 7 && v.size != 10) return MangaConversionOptions()
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
            )
        }

        fun load(prefs: SharedPreferences): MangaConversionOptions = fromToken(prefs.getString("conversion_options", null))
    }
    fun save(prefs: SharedPreferences) {
        prefs.edit().putString("conversion_options", token()).apply()
    }
}

@Composable
fun MangaConversionSettings(options: MangaConversionOptions, onChange: (MangaConversionOptions) -> Unit) {
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
            Text("Turn the X3/X4 sideways to read landscape XTC/XTCH pages.")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
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
        Text("Changes apply to the next conversion. Landscape preserves XTC panel dimensions and rotates its content; EPUB/CBZ use a wider page. Manual crop, zoom, preview and guide arrows are still planned.")
    }
}
