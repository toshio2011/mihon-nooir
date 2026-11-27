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
    val device=NooirDeviceProfiles.forId("X4")
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
    val gray=NooirDesktopCli.previewImage(bytes,device.id,ExportFormat.XTCH,options,false)
    check(gray.frames.isNotEmpty())
    println("PASS: actual renderer sample preview, first chapter page and fixed device dimensions")
}
