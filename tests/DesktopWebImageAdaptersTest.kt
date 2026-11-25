package dev.nooir.desktop.ui

import dev.nooir.bridge.ReflowEpubWriter
import dev.nooir.mangabridge.*
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

fun main() {
    fun cover(color: Color): ReflowEpubWriter.ImageAsset {
        val img=BufferedImage(120,200,BufferedImage.TYPE_INT_RGB)
        val g=img.createGraphics()
        try { g.color=color;g.fillRect(0,0,120,200) } finally { g.dispose() }
        val bytes=ByteArrayOutputStream().use { check(ImageIO.write(img,"png",it));it.toByteArray() }
        img.flush()
        return ReflowEpubWriter.ImageAsset(bytes,"png","image/png")
    }
    val red=cover(Color.RED)
    val blue=cover(Color.BLUE)
    for (count in 2..4) {
        val result=NooirCoverCollage.compose(List(count) { if(it%2==0) red else blue })
        check(result.mime=="image/jpeg" && result.bytes.size<=1_500_000)
        val img=DesktopWebImageCodec.decode(result)
        try {
            check(img.width==800 && img.height==1120)
            val tiles=NooirCoverCollageLayout.tiles(count)
            for ((i,t) in tiles.withIndex()) {
                val p=Color(img.getRGB(t.x+t.width/2,t.y+t.height/2))
                check(if(i%2==0) p.red>200 && p.blue<50 else p.blue>200 && p.red<50)
            }
        } finally { img.flush() }
    }
    check(runCatching { NooirCoverCollage.compose(listOf(red)) }.isFailure)
    check(runCatching { DesktopWebImageCodec.decode(red.copy(mime="image/jpeg")) }.isFailure)
    check(NooirWebImageOptimizer.optimize(red,NooirWebImageProfile.ORIGINAL) === red)
    check(NooirWebImageOptimizer.optimize(red,NooirWebImageProfile.SMALL) === red)
    val original=BufferedImage(1800,1200,BufferedImage.TYPE_INT_RGB)
    try {
        val g=original.createGraphics()
        try { g.color=Color.WHITE;g.fillRect(0,0,1800,1200)
            g.color=Color.BLACK
            for(y in 0 until 1200 step 20)g.drawLine(0,y,1799,y)
        } finally { g.dispose() }
        val asset=DesktopWebImageCodec.encode(original,false,95)
        val optimized=NooirWebImageOptimizer.optimize(asset,NooirWebImageProfile.SMALL)
        check(optimized.bytes.size < asset.bytes.size)
        val image=DesktopWebImageCodec.decode(optimized)
        try { check(maxOf(image.width,image.height)==1000) } finally { image.flush() }
    } finally { original.flush() }
    val entries=NooirWebChapterPlan.start(listOf(
        NooirWebChapterPlan.Entry("https://example.org/one","One"),
        NooirWebChapterPlan.Entry("https://example.org/two","Two")))
    check(NooirWebChapterPlan.export(NooirWebChapterPlan.reverse(entries)).titles==listOf("Two","One"))
    check(NooirWebChapterUpdates.findNew(listOf("https://example.org/one?utm_source=old"),entries).single().title=="Two")
    println("PASS: desktop bounded cover collage, illustration resizing, shared chapter ordering and update identity")
}
