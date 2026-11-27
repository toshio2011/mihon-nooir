package dev.nooir.desktop.compose

import dev.nooir.desktop.NooirDesktopCli
import dev.nooir.bridge.ExportFormat
import dev.nooir.bridge.NooirDeviceProfiles
import dev.nooir.mangabridge.MangaConversionOptions
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

fun main() {
    val image=BufferedImage(480,800,BufferedImage.TYPE_INT_RGB)
    val g=image.createGraphics()
    try {
        g.color=Color.WHITE;g.fillRect(0,0,480,800)
        g.color=Color.BLACK
        for(y in listOf(20,410)) for(x in listOf(20,250)) {
            g.drawRect(x,y,210,350);g.fillRect(x+20,y+20,150,210)
        }
    }finally{g.dispose()}
    val bytes=ByteArrayOutputStream().also{ImageIO.write(image,"png",it)}.toByteArray()
    image.flush()
    val directory=java.nio.file.Files.createTempDirectory("nooir-compose-defaults-")
    try {
        val store=dev.nooir.desktop.ui.DesktopConversionSettingsStore(directory)
        check(loadMangaDefaults(store).options==androidMangaDefaults())
        val custom=MangaConversionOptions(renderAlgorithm="ATKINSON",contrast=73,contextPeekPercent=4,
            allowMissingCover=true,skipUnavailableChapters=true,panelDiagnostics=true)
        store.save(custom)
        check(loadMangaDefaults(store).options==custom.copy(allowMissingCover=false,skipUnavailableChapters=false,panelDiagnostics=false))
        val path=directory.resolve(dev.nooir.desktop.ui.DesktopConversionSettingsStore.FILE_NAME)
        java.nio.file.Files.writeString(path,"unknown settings")
        check(loadMangaDefaults(store).warning.isNotBlank())
        check(runCatching{store.save(custom)}.isFailure && java.nio.file.Files.readString(path)=="unknown settings")
    }finally{directory.toFile().deleteRecursively()}
    val typography=androidTypographyDefaults()
    check(typography.family=="SansSerif")
    check(typographyPreset(typography,"Compact").let{Triple(it.fontPx,it.marginPx,it.lineHeight)}==Triple(20,14,110))
    check(typographyPreset(typography,"Balanced")==typography)
    check(typographyPreset(typography,"Large").let{Triple(it.fontPx,it.marginPx,it.lineHeight)}==Triple(32,20,135))
    val customReader=checkNotNull(validatedCustomReader("600","1000"))
    check(customReader.width==600 && customReader.height==1000 && !customReader.xteinkWifi)
    check(validatedCustomReader("4096","4096")==null && validatedCustomReader("63","800")==null)
    val customPreview=NooirDesktopCli.previewImage(bytes,customReader.id,ExportFormat.XTC,androidMangaDefaults())
    check(customPreview.frames.all{png->ImageIO.read(png.inputStream()).let{img->
        try{img.width==600 && img.height==1000}finally{img.flush()}
    }})
    val fresh=androidMangaDefaults()
    check(fresh.copy(renderAlgorithm="LEGACY")==MangaConversionOptions())
    check(androidRendering(fresh)=="FLOYD_STEINBERG")
    check(androidRendering(MangaConversionOptions(dither=false))=="THRESHOLD")
    for(name in listOf("Manga","Manhwa","Manhua")){
        check(androidMangaPreset(name).copy(renderAlgorithm="LEGACY")==MangaConversionOptions.preset(name))
    }
    val prior=MangaConversionOptions(renderAlgorithm="ATKINSON",contrast=73,contextPeekPercent=4,showFullPageBeforeSplits=true)
    val recommended=recommendedManga(prior)
    check(recommended.panelMode=="PANEL_BY_PANEL" && recommended.orientation=="AUTO_FIT" &&
        recommended.renderAlgorithm=="ATKINSON" && recommended.contrast==73 && recommended.contextPeekPercent==4 &&
        !recommended.showFullPageBeforeSplits && recommended.overlapPercent==0)
    val device=NooirDeviceProfiles.forId("X4")
    val legacy=NooirDesktopCli.previewImage(bytes,device.id,ExportFormat.XTC,MangaConversionOptions())
    val explicit=NooirDesktopCli.previewImage(bytes,device.id,ExportFormat.XTC,fresh)
    check(legacy.frames.size==explicit.frames.size && legacy.frames.zip(explicit.frames).all{(a,b)->a.contentEquals(b)})
    val options=MangaConversionOptions.preset("Manga").copy(panelMode="PANEL_BY_PANEL",orientation="AUTO_FIT",keepChapterFirstPageWhole=true)
    val whole=NooirDesktopCli.previewImage(bytes,device.id,ExportFormat.XTC,options,true)
    check(whole.frames.size==1)
    val normal=NooirDesktopCli.previewImage(bytes,device.id,ExportFormat.XTC,options,false)
    check(normal.frames.isNotEmpty() && normal.frames.size<=12)
    for(png in normal.frames){
        val decoded=ImageIO.read(png.inputStream())
        check(decoded.width==device.width && decoded.height==device.height)
        decoded.flush()
    }
    val sampleDir=java.nio.file.Files.createTempDirectory("nooir-sample-archive").toFile()
    try {
        val input=sampleDir.resolve("sample.cbz")
        java.util.zip.ZipOutputStream(input.outputStream()).use{zip->
            for(name in listOf("10.png","cover.png","2.png")){
                zip.putNextEntry(java.util.zip.ZipEntry(name));zip.write(bytes);zip.closeEntry()
            }
        }
        val original=input.readBytes()
        val cover=NooirDesktopCli.localSample(input,0,true)
        check(cover.cover && cover.name=="cover.png" && cover.count==3)
        val opening=NooirDesktopCli.localSample(input,0,false)
        val content=NooirDesktopCli.localSample(input,1,false)
        check(opening.name=="2.png" && opening.firstChapterPage && opening.count==2)
        check(content.name=="10.png" && !content.firstChapterPage)
        val selected=NooirDesktopCli.previewImage(content.bytes,"X4",ExportFormat.XTC,options,content.firstChapterPage,content.cover)
        check(selected.frames.size==normal.frames.size && selected.frames.zip(normal.frames).all{(a,b)->a.contentEquals(b)})
        val out=sampleDir.resolve("sample.xtc")
        NooirDesktopCli.main(arrayOf("--input",input.path,"--output",out.path,"--device","X4","--format","XTC",
            "--options-token",options.copy(includeCover=false).token()))
        val exported=dev.nooir.bridge.XtcBookPreview(out)
        check(exported.pageCount==whole.frames.size+selected.frames.size)
        for((i,png) in selected.frames.withIndex()){
            val frame=dev.nooir.bridge.GrayscalePng.encode(exported.page(whole.frames.size+i))
            val a=ImageIO.read(frame.inputStream());val b=ImageIO.read(png.inputStream())
            try{check(a.width==b.width && a.height==b.height && a.getRGB(0,0,a.width,a.height,null,0,a.width).contentEquals(b.getRGB(0,0,b.width,b.height,null,0,b.width)))}
            finally{a.flush();b.flush()}
        }
        check(input.readBytes().contentEquals(original))
    }finally{sampleDir.deleteRecursively()}
    val gray=NooirDesktopCli.previewImage(bytes,device.id,ExportFormat.XTCH,options,false)
    check(gray.frames.isNotEmpty())
    println("PASS: actual renderer sample preview, first chapter page and fixed device dimensions")
}
