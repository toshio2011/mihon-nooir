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

// Android stores legacy tokens for compatibility but displays their actual algorithm.
internal fun androidRendering(o:MangaConversionOptions):String =
    if(o.renderAlgorithm=="LEGACY") {if(o.dither)"FLOYD_STEINBERG" else "THRESHOLD"} else o.renderAlgorithm

internal fun androidMangaDefaults():MangaConversionOptions =
    MangaConversionOptions().copy(renderAlgorithm="FLOYD_STEINBERG",dither=true)

internal fun androidMangaPreset(name:String):MangaConversionOptions =
    MangaConversionOptions.preset(name).copy(renderAlgorithm="FLOYD_STEINBERG",dither=true)

internal fun recommendedManga(o:MangaConversionOptions):MangaConversionOptions =
    o.copy(preset="Custom",layout="PAGED",panelMode="PANEL_BY_PANEL",orientation="AUTO_FIT",
        overlapPercent=0,cropWhiteMargins=true,splitSpreads=true,rightToLeft=true,showFullPageBeforeSplits=false)

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
    Text("Contrast · "+o.contrast+" (50 = original)")
    Slider(o.contrast.toFloat(),{change(o.copy(preset="Custom",contrast=it.toInt()))},valueRange=0f..100f,enabled=enabled)
    Text("E-ink rendering style")
    val names=linkedMapOf("FLOYD_STEINBERG" to "Floyd–Steinberg","ATKINSON" to "Atkinson","BAYER_8X8" to "Bayer 8×8","THRESHOLD" to "Threshold")
    val active=androidRendering(o)
    ExportChoice(names[active] ?: active,names.values.toList(),enabled){label->
        val algorithm=names.entries.first{it.value==label}.key
        change(o.copy(preset="Custom",renderAlgorithm=algorithm,dither=algorithm!="THRESHOLD"))
    }
    Text(when(active){
        "ATKINSON"->"Cleaner white areas and sharper lettering; some soft tones may disappear."
        "BAYER_8X8"->"Regular dot patterns for manga screentones; may show a visible grid."
        "THRESHOLD"->"No dither texture; smooth gradients may band."
        else->"Original default: smooth shading, with possible texture in dark areas."
    },style=MaterialTheme.typography.bodySmall)
}
@Composable
internal fun ComposeMangaSettings(o:MangaConversionOptions,format:ExportFormat,enabled:Boolean,change:(MangaConversionOptions)->Unit) {
    Text("Conversion preset",style=MaterialTheme.typography.titleMedium)
    ExportChoice(o.preset,listOf("Manga","Manhwa","Manhua","Custom"),enabled){change(if(it=="Custom")o.copy(preset="Custom") else androidMangaPreset(it))}
    OutlinedButton(onClick={change(recommendedManga(o))},enabled=enabled){Text("Apply manga settings")}
    Text("Layout")
    ExportChoice(o.layout,listOf("AUTO","PAGED","WEBTOON"),enabled){change(o.copy(preset="Custom",layout=it))}
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text("Panel mode")
            ExportChoice(o.panelMode,listOf("OFF","ROW_BY_ROW","PANEL_BY_PANEL"),enabled){
                change(o.copy(preset="Custom",panelMode=it,orientation=if(it=="OFF" && o.orientation=="AUTO_FIT")"PORTRAIT" else o.orientation))
            }
        }
        Column(Modifier.weight(1f)) {
            Text("Orientation")
            ExportChoice(o.orientation,if(o.panelMode!="OFF")listOf("PORTRAIT","LANDSCAPE","AUTO_FIT") else listOf("PORTRAIT","LANDSCAPE"),enabled){change(o.copy(preset="Custom",orientation=it))}
        }
    }
    if(format in setOf(ExportFormat.XTC,ExportFormat.XTCH) && o.orientation!="PORTRAIT") ConversionToggle("Rotate content clockwise",o.landscapeClockwise,enabled){change(o.copy(preset="Custom",landscapeClockwise=it))}
    ConversionToggle("Full page before splits",o.showFullPageBeforeSplits,enabled){change(o.copy(preset="Custom",showFullPageBeforeSplits=it))}
    ConversionToggle("Keep chapter first page whole",o.keepChapterFirstPageWhole,enabled){change(o.copy(preset="Custom",keepChapterFirstPageWhole=it))}
    if(o.panelMode!="OFF") {
        ConversionToggle("Enlarge oversized splits",o.enlargeOversizedSplits,enabled){change(o.copy(preset="Custom",enlargeOversizedSplits=it))}
        if(o.enlargeOversizedSplits) {
            Text("Minimum screen fill · "+o.minimumScreenFillPercent+"%")
            Slider(o.minimumScreenFillPercent.toFloat(),{change(o.copy(preset="Custom",minimumScreenFillPercent=it.toInt()))},
                valueRange=25f..80f,steps=10,enabled=enabled)
        }
        if(o.panelMode=="PANEL_BY_PANEL") ConversionSlider("Context Peek (%) · 0 = off",o.contextPeekPercent,0..8,enabled){change(o.copy(preset="Custom",contextPeekPercent=it))}
    }
    if(o.panelMode!="OFF" || o.layout!="PAGED" || o.orientation!="PORTRAIT") ConversionSlider("Overlap (%)",o.overlapPercent,0..50,enabled){change(o.copy(preset="Custom",overlapPercent=it))}
    ConversionToggle("Trim white margins",o.cropWhiteMargins,enabled){change(o.copy(preset="Custom",cropWhiteMargins=it))}
    ConversionToggle("Split double-page spreads",o.splitSpreads,enabled){change(o.copy(preset="Custom",splitSpreads=it))}
    ConversionToggle("Read right to left",o.rightToLeft,enabled){change(o.copy(preset="Custom",rightToLeft=it))}
    ComposeRenderingSettings(o,enabled,change)
    HorizontalDivider()
    Text("Book packaging",style=MaterialTheme.typography.titleMedium)
    ConversionToggle("Include cover",o.includeCover,enabled){change(o.copy(preset="Custom",includeCover=it))}
    if(format in setOf(ExportFormat.XTC,ExportFormat.XTCH)) {
        ConversionToggle("Extended metadata (experimental)",o.xtcExtendedMetadata,enabled){change(o.copy(preset="Custom",xtcExtendedMetadata=it))}
        Text("Older readers may ignore the private metadata trailer.",style=MaterialTheme.typography.bodySmall)
    }
    if(format in setOf(ExportFormat.EPUB,ExportFormat.EPUB2,ExportFormat.CBZ)) ConversionToggle("Retain original color",o.epubColor,enabled){change(o.copy(preset="Custom",epubColor=it))}
    if(format in setOf(ExportFormat.EPUB,ExportFormat.EPUB2)) {
        val grouping=when(o.epubPagesPerXhtml){0->"Entire chapter";1->"One page per XHTML";else->"Custom"}
        ExportChoice(grouping,listOf("One page per XHTML","Entire chapter","Custom"),enabled){
            change(o.copy(preset="Custom",epubPagesPerXhtml=when(it){"Entire chapter"->0;"One page per XHTML"->1;else->2}))
        }
        if(grouping=="Custom") {
            var text by remember { mutableStateOf(o.epubPagesPerXhtml.toString()) }
            OutlinedTextField(text,{v->text=v.take(3);v.toIntOrNull()?.takeIf{it in 2..999}?.let{change(o.copy(preset="Custom",epubPagesPerXhtml=it))}},
                label={Text("Pages per XHTML (2–999)")},singleLine=true,enabled=enabled,isError=text.toIntOrNull()?.let{it !in 2..999}!=false)
            Text("Applied: "+o.epubPagesPerXhtml,style=MaterialTheme.typography.labelSmall)
        }
    }
}
internal fun androidTypographyDefaults()=DesktopTextEpubImport.Settings(family="SansSerif")
internal val androidReadingPresets=linkedMapOf("Compact" to Triple(20,14,110),
    "Balanced" to Triple(24,26,118),"Large" to Triple(32,20,135))
internal fun typographyPreset(s:DesktopTextEpubImport.Settings,name:String):DesktopTextEpubImport.Settings {
    val (size,margin,lineHeight)=androidReadingPresets.getValue(name)
    return s.copy(fontPx=size,marginPx=margin,lineHeight=lineHeight)
}
@Composable
internal fun ComposeTypographySettings(s:DesktopTextEpubImport.Settings,enabled:Boolean,change:(DesktopTextEpubImport.Settings)->Unit) {
    Text("Typography",style=MaterialTheme.typography.titleMedium)
    Text("Reading presets")
    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
        androidReadingPresets.forEach{(name,values)->
            FilterChip(selected=Triple(s.fontPx,s.marginPx,s.lineHeight)==values,
                onClick={change(typographyPreset(s,name))},label={Text(name)},enabled=enabled)
        }
    }
    Text("Font family")
    val families=linkedMapOf("SansSerif" to "Sans","Serif" to "Serif","Monospaced" to "Mono")
    ExportChoice(families[s.family] ?: s.family,families.values.toList(),enabled){label->
        change(s.copy(family=families.entries.first{it.value==label}.key))
    }
    ConversionSlider("Font size (px)",s.fontPx,14..46,enabled){change(s.copy(fontPx=it))}
    ConversionSlider("Margins (px)",s.marginPx,8..80,enabled){change(s.copy(marginPx=it))}
    ConversionSlider("Line height (%)",s.lineHeight,100..200,enabled){change(s.copy(lineHeight=it))}
    Text("Reading direction")
    ExportChoice(s.direction,listOf("Auto","Left to right","Right to left"),enabled){change(s.copy(direction=it))}
}

/** Only settings consumed by DesktopPdfImport's original-page adapter. */
@Composable
internal fun ComposePdfSettings(o:MangaConversionOptions,format:ExportFormat,enabled:Boolean,change:(MangaConversionOptions)->Unit){
    Text("PDF page rendering",style=MaterialTheme.typography.titleMedium)
    Text("Original PDF pages remain intact, fitted to the selected device.",style=MaterialTheme.typography.bodySmall)
    ConversionToggle("Right-to-left book metadata",o.rightToLeft,enabled){change(o.copy(preset="Custom",rightToLeft=it))}
    val archive=format in setOf(ExportFormat.EPUB,ExportFormat.EPUB2,ExportFormat.CBZ)
    if(archive)ConversionToggle("Retain source colors",o.epubColor,enabled){change(o.copy(preset="Custom",epubColor=it))}
    if(!archive || !o.epubColor)ComposeRenderingSettings(o,enabled,change)
    if(format in setOf(ExportFormat.EPUB,ExportFormat.EPUB2)){
        Text("Pages per XHTML")
        val grouping=when(o.epubPagesPerXhtml){0->"Entire book";1->"One page";else->"Custom"}
        ExportChoice(grouping,listOf("One page","Entire book","Custom"),enabled){change(o.copy(preset="Custom",epubPagesPerXhtml=when(it){"Entire book"->0;"One page"->1;else->2}))}
        if(grouping=="Custom"){
            var value by remember{mutableStateOf(o.epubPagesPerXhtml.toString())}
            OutlinedTextField(value,{v->value=v.take(3);v.toIntOrNull()?.takeIf{it in 2..999}?.let{change(o.copy(preset="Custom",epubPagesPerXhtml=it))}},
                label={Text("Pages per XHTML (2–999)")},singleLine=true,enabled=enabled)
            Text("Applied: "+o.epubPagesPerXhtml)
        }
    }
}
