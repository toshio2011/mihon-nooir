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
/** Android-only persistence adapter; conversion settings live in the shared JVM model. */
fun MangaConversionOptions.Companion.load(prefs: SharedPreferences): MangaConversionOptions =
    fromToken(prefs.getString("conversion_options", null))

fun MangaConversionOptions.save(prefs: SharedPreferences) {
    prefs.edit().putString("conversion_options", token()).apply()
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
        Text("Recommended for Japanese manga: Panel by Panel + Auto Fit. This attempts one panel per view.")
        TextButton(onClick = {
            onChange(options.copy(preset = "Custom", layout = "PAGED",
                panelMode = "PANEL_BY_PANEL", orientation = "AUTO_FIT",
                overlapPercent = 0, cropWhiteMargins = true,
                splitSpreads = true, rightToLeft = true,
                showFullPageBeforeSplits = false))
        }) { Text("Apply recommended manga panel settings") }
        Text("Help: Pages keeps normal manga pages; Webtoon handles long scrolling strips. Panel by Panel attempts individual panels; Row by Row keeps each horizontal tier together.")
        Text("Auto Fit rotates each detected panel to best use the screen. Overlap repeats part of a slice; 0% is a good starting point for manga panels.")
        Text("Trim white margins removes empty borders. Split double-page spreads separates facing pages, not individual panels.")
        Text("Some pages remain whole when the detector cannot find a safe boundary; changing overlap will not solve that.")
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
        Text("Page splitting and reading order")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Switch(checked = options.showFullPageBeforeSplits, onCheckedChange = {
                onChange(options.copy(preset = "Custom", showFullPageBeforeSplits = it))
            })
            Text("Show full page before splits")
        }
        if (options.showFullPageBeforeSplits) Text("The complete original page appears before any multiple crops or slices. Single-screen pages are not duplicated.")
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
        Text("Reading segmentation")
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("OFF" to "Page", "ROW_BY_ROW" to "Row by Row",
                "PANEL_BY_PANEL" to "Panel by Panel").forEach { (value, title) ->
                FilterChip(
                    selected = options.panelMode == value,
                    onClick = {
                        onChange(options.copy(preset = "Custom", panelMode = value,
                            orientation = if (value == "OFF" && options.orientation == "AUTO_FIT")
                                "PORTRAIT" else options.orientation))
                    },
                    label = { Text(title) },
                )
            }
        }
        if (options.panelMode == "ROW_BY_ROW") {
            Text("Detect horizontal manga rows independently. Keep panels together across each row; uncertain pages remain intact.")
        } else if (options.panelMode == "PANEL_BY_PANEL") {
            Text("Detect individual manga panels. Uncertain boundaries preserve the page rather than guess.")
        }
        Text("Book packaging")
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
            (listOf("PORTRAIT" to "Portrait", "LANDSCAPE" to "Landscape") +
                (if (options.panelMode != "OFF") listOf("AUTO_FIT" to "Auto Fit") else emptyList())).forEach { (value, title) ->
                FilterChip(
                    selected = options.orientation == value,
                    onClick = { onChange(options.copy(preset = "Custom", orientation = value)) },
                    label = { Text(title) },
                )
            }
        }
        if (options.orientation == "AUTO_FIT" && options.panelMode != "OFF") {
            Text("Auto Fit chooses portrait or landscape for each detected panel to use more screen space, " +
                "without cutting panel content. Uncertain pages use the safe portrait fallback.")
            if (isXtc) Text("XTC/XTCH stays at the device's native pixel dimensions; " +
                "some wide panels will be rotated and may require turning the reader sideways.")
        }
        if (options.orientation == "LANDSCAPE") {
            Text(if (isXtc) "Turn the X3/X4 sideways to read landscape XTC/XTCH pages." else "Landscape exports use a wider page.")
            if (isXtc) Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Rotate content clockwise")
                Switch(checked = options.landscapeClockwise,
                    onCheckedChange = { onChange(options.copy(preset = "Custom", landscapeClockwise = it)) })
            }
        }
        if (options.orientation == "AUTO_FIT" && isXtc) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Rotate wide panels clockwise")
                Switch(checked = options.landscapeClockwise,
                    onCheckedChange = { onChange(options.copy(preset = "Custom", landscapeClockwise = it)) })
            }
        }
        if (options.panelMode != "OFF" || options.layout != "PAGED" || options.orientation == "LANDSCAPE" || options.orientation == "AUTO_FIT") {
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
        Text("Changes apply to the next conversion. Open the first-page preview in the export dialog to inspect rendered crops. Landscape preserves XTC panel dimensions and rotates its content; EPUB/CBZ use a wider page.")
    }
}
