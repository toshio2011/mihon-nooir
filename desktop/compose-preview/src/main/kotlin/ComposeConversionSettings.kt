package dev.nooir.desktop.compose

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.nooir.bridge.ExportFormat
import dev.nooir.mangabridge.MangaConversionOptions
import dev.nooir.desktop.ui.DesktopTextEpubImport

@Composable
internal fun ConversionToggle(label:String, checked:Boolean, enabled:Boolean, change:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text(label,Modifier.weight(1f)); Switch(checked,change,enabled=enabled)
    }
}
@Composable
internal fun ConversionSlider(label:String,value:Int,range:IntRange,enabled:Boolean,change:(Int)->Unit) {
    Text("$label · $value")
    Slider(value.toFloat(),{change(it.toInt().coerceIn(range))},valueRange=range.first.toFloat()..range.last.toFloat(),
        steps=(range.last-range.first-1).coerceAtLeast(0),enabled=enabled)
}
@Composable
internal fun ComposeRenderingSettings(o:MangaConversionOptions,enabled:Boolean,change:(MangaConversionOptions)->Unit) {
    Text("Image and rendering",style=MaterialTheme.typography.titleMedium)
    ConversionSlider("Contrast (50 = original)",o.contrast,0..100,enabled){change(o.copy(contrast=it))}
    Text("E-ink rendering style")
    ExportChoice(o.renderAlgorithm,listOf("LEGACY","FLOYD_STEINBERG","ATKINSON","BAYER_8X8","THRESHOLD"),enabled){change(o.copy(renderAlgorithm=it))}
    if(o.renderAlgorithm=="LEGACY") ConversionToggle("Dithering",o.dither,enabled){change(o.copy(dither=it))}
}
@Composable
internal fun ComposeMangaSettings(o:MangaConversionOptions,format:ExportFormat,enabled:Boolean,change:(MangaConversionOptions)->Unit) {
    Text("Conversion preset",style=MaterialTheme.typography.titleMedium)
    ExportChoice(o.preset,listOf("Manga","Manhwa","Manhua","Custom"),enabled){change(if(it=="Custom")o.copy(preset="Custom") else MangaConversionOptions.preset(it))}
    OutlinedButton(onClick={change(o.copy(preset="Custom",layout="PAGED",panelMode="PANEL_BY_PANEL",orientation="AUTO_FIT",overlapPercent=0,cropWhiteMargins=true,splitSpreads=true,rightToLeft=true,showFullPageBeforeSplits=false))},enabled=enabled){Text("Apply manga settings")}
    Text("Layout")
    ExportChoice(o.layout,listOf("AUTO","PAGED","WEBTOON"),enabled){change(o.copy(layout=it))}
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text("Panel mode")
            ExportChoice(o.panelMode,listOf("OFF","ROW_BY_ROW","PANEL_BY_PANEL"),enabled){
                change(o.copy(panelMode=it,orientation=if(it!="PANEL_BY_PANEL" && o.orientation=="AUTO_FIT")"PORTRAIT" else o.orientation))
            }
        }
        Column(Modifier.weight(1f)) {
            Text("Orientation")
            ExportChoice(o.orientation,if(o.panelMode=="PANEL_BY_PANEL")listOf("PORTRAIT","LANDSCAPE","AUTO_FIT") else listOf("PORTRAIT","LANDSCAPE"),enabled){change(o.copy(orientation=it))}
        }
    }
    if(o.orientation!="PORTRAIT") ConversionToggle("Rotate content clockwise",o.landscapeClockwise,enabled){change(o.copy(landscapeClockwise=it))}
    ConversionToggle("Full page before splits",o.showFullPageBeforeSplits,enabled){change(o.copy(showFullPageBeforeSplits=it))}
    ConversionToggle("Keep chapter first page whole",o.keepChapterFirstPageWhole,enabled){change(o.copy(keepChapterFirstPageWhole=it))}
    if(o.panelMode!="OFF") {
        ConversionToggle("Enlarge oversized splits",o.enlargeOversizedSplits,enabled){change(o.copy(enlargeOversizedSplits=it))}
        if(o.enlargeOversizedSplits) ConversionSlider("Minimum screen fill (%)",o.minimumScreenFillPercent,25..80,enabled){change(o.copy(minimumScreenFillPercent=it))}
        if(o.panelMode=="PANEL_BY_PANEL") ConversionSlider("Context Peek (%) · 0 = off",o.contextPeekPercent,0..8,enabled){change(o.copy(contextPeekPercent=it))}
    }
    ConversionSlider("Overlap (%)",o.overlapPercent,0..50,enabled){change(o.copy(overlapPercent=it))}
    ConversionToggle("Trim white margins",o.cropWhiteMargins,enabled){change(o.copy(cropWhiteMargins=it))}
    ConversionToggle("Split double-page spreads",o.splitSpreads,enabled){change(o.copy(splitSpreads=it))}
    ConversionToggle("Read right to left",o.rightToLeft,enabled){change(o.copy(rightToLeft=it))}
    ComposeRenderingSettings(o,enabled,change)
    HorizontalDivider()
    Text("Book packaging",style=MaterialTheme.typography.titleMedium)
    ConversionToggle("Include cover",o.includeCover,enabled){change(o.copy(includeCover=it))}
    if(format in setOf(ExportFormat.XTC,ExportFormat.XTCH)) {
        ConversionToggle("Extended metadata (experimental)",o.xtcExtendedMetadata,enabled){change(o.copy(xtcExtendedMetadata=it))}
        Text("Older readers may ignore the private metadata trailer.",style=MaterialTheme.typography.bodySmall)
    }
    if(format in setOf(ExportFormat.EPUB,ExportFormat.EPUB2,ExportFormat.CBZ)) ConversionToggle("Retain original color",o.epubColor,enabled){change(o.copy(epubColor=it))}
    if(format in setOf(ExportFormat.EPUB,ExportFormat.EPUB2)) {
        val grouping=when(o.epubPagesPerXhtml){0->"Entire chapter";1->"One page per XHTML";else->"Custom"}
        ExportChoice(grouping,listOf("One page per XHTML","Entire chapter","Custom"),enabled){
            change(o.copy(epubPagesPerXhtml=when(it){"Entire chapter"->0;"One page per XHTML"->1;else->2}))
        }
        if(grouping=="Custom") {
            var text by remember { mutableStateOf(o.epubPagesPerXhtml.toString()) }
            OutlinedTextField(text,{v->text=v.take(3);v.toIntOrNull()?.takeIf{it in 2..999}?.let{change(o.copy(epubPagesPerXhtml=it))}},
                label={Text("Pages per XHTML (2–999)")},singleLine=true,enabled=enabled,isError=text.toIntOrNull()?.let{it !in 2..999}!=false)
            Text("Applied: "+o.epubPagesPerXhtml,style=MaterialTheme.typography.labelSmall)
        }
    }
}
@Composable
internal fun ComposeTypographySettings(s:DesktopTextEpubImport.Settings,enabled:Boolean,change:(DesktopTextEpubImport.Settings)->Unit) {
    Text("Typography",style=MaterialTheme.typography.titleMedium)
    ExportChoice(s.family,listOf("Serif","SansSerif","Monospaced"),enabled){change(s.copy(family=it))}
    ConversionSlider("Font size (px)",s.fontPx,14..46,enabled){change(s.copy(fontPx=it))}
    ConversionSlider("Margins (px)",s.marginPx,8..80,enabled){change(s.copy(marginPx=it))}
    ConversionSlider("Line height (%)",s.lineHeight,100..200,enabled){change(s.copy(lineHeight=it))}
    Text("Reading direction")
    ExportChoice(s.direction,listOf("Auto","Left to right","Right to left"),enabled){change(s.copy(direction=it))}
}
