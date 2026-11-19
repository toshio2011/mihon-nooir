package dev.nooir.desktop.ui

import dev.nooir.bridge.ExportFormat
import dev.nooir.bridge.NooirDeviceProfiles
import dev.nooir.bridge.XtcBookPreview
import dev.nooir.mangabridge.MangaConversionOptions
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO

fun main() {
    val root = Files.createTempDirectory("nooir-documents-").toFile()
    try {
        val pdf = root.resolve("original.pdf")
        PDDocument().use { doc ->
            val page=PDPage(PDRectangle(180f,240f)); doc.addPage(page)
            PDPageContentStream(doc,page).use { stream ->
                stream.beginText();stream.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA),12f)
                stream.newLineAtOffset(12f,100f);stream.showText("Original selectable text");stream.endText()
            }
            doc.addPage(PDPage(PDRectangle(180f,240f)))
            doc.save(pdf)
        }
        val opts=MangaConversionOptions().copy(rightToLeft=false,renderAlgorithm="THRESHOLD",epubColor=false)
        val device=NooirDeviceProfiles.custom(64,64)
        for (format in listOf(ExportFormat.XTC,ExportFormat.XTCH,ExportFormat.EPUB2,ExportFormat.EPUB,ExportFormat.CBZ)) {
            val output=root.resolve(format.name+"."+format.extension)
            check(DesktopPdfImport.export(pdf,output,format,device,opts)==2)
            if (format in setOf(ExportFormat.XTC,ExportFormat.XTCH)) check(XtcBookPreview(output).pageCount==2)
            else ZipFile(output).use { zip -> check(zip.size()>2) }
        }
        val text=root.resolve("text-companion.epub")
        check(DesktopPdfImport.export(pdf,text,ExportFormat.EPUB2,device,opts,true)==2)
        ZipFile(text).use { zip ->
            val first=zip.getInputStream(zip.getEntry("OEBPS/text/s00001.xhtml")).bufferedReader().use { it.readText() }
            check(first.contains("Original selectable text") && first.contains("<img "))
        }
        val big=root.resolve("long.pdf")
        PDDocument().use { doc ->
            repeat(1001) { doc.addPage(PDPage(PDRectangle(32f,32f))) }
            doc.save(big)
        }
        val long=root.resolve("long.xtc")
        check(DesktopPdfImport.export(big,long,ExportFormat.XTC,device,opts)==1001)
        check(XtcBookPreview(long).pageCount==1001)
        val canceled=root.resolve("canceled.xtc")
        var processed=0
        check(runCatching { DesktopPdfImport.export(big,canceled,ExportFormat.XTC,device,opts,
            canceled={processed>=2},progress={n,_->processed=n}) }.isFailure)
        check(!canceled.exists())

        fun comic(name:String, prose:Boolean): java.io.File {
            val epub=root.resolve(name)
            ZipOutputStream(epub.outputStream()).use { zip ->
                fun entry(name:String, bytes:ByteArray) {
                    zip.putNextEntry(ZipEntry(name));zip.write(bytes);zip.closeEntry()
                }
                fun xml(name:String, data:String)=entry(name,data.toByteArray())
                xml("META-INF/container.xml","""<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OPS/package.opf"/></rootfiles></container>""")
                xml("OPS/package.opf","""<package xmlns="http://www.idpf.org/2007/opf"><manifest>
                    <item id="a" href="text/a.xhtml" media-type="application/xhtml+xml"/>
                    <item id="b" href="text/b.xhtml" media-type="application/xhtml+xml"/>
                    <item id="black" href="img/z.png" media-type="image/png"/>
                    <item id="white" href="img/a.png" media-type="image/png"/>
                    </manifest><spine><itemref idref="a"/><itemref idref="b"/></spine></package>""")
                xml("OPS/text/a.xhtml","""<html xmlns="http://www.w3.org/1999/xhtml"><body><img src="../img/z.png"/>${if(prose) "Must not drop this text" else ""}</body></html>""")
                xml("OPS/text/b.xhtml","""<html xmlns="http://www.w3.org/1999/xhtml"><body><img src="../img/a.png"/></body></html>""")
                for ((path,color) in listOf("OPS/img/z.png" to Color.BLACK,"OPS/img/a.png" to Color.WHITE)) {
                    val img=BufferedImage(64,64,BufferedImage.TYPE_INT_RGB)
                    val g=img.createGraphics();g.color=color;g.fillRect(0,0,64,64);g.dispose()
                    val bytes=ByteArrayOutputStream().use { out -> check(ImageIO.write(img,"png",out));out.toByteArray() }
                    img.flush();entry(path,bytes)
                }
            }
            return epub
        }
        val original=comic("comic.epub",false)
        val sourceBytes=original.readBytes()
        val out=root.resolve("comic.xtc")
        val comicOpts=opts.copy(layout="PAGED",cropWhiteMargins=false,splitSpreads=false,panelMode="OFF",includeCover=true)
        check(DesktopImageEpubImport.export(original,out,ExportFormat.XTC,device,comicOpts)==2)
        val book=XtcBookPreview(out)
        check(book.pageCount==2)
        check(book.page(0).levels.count { it.toInt()==0 } > book.page(1).levels.count { it.toInt()==0 })
        val rejected=root.resolve("prose.xtc")
        check(runCatching { DesktopImageEpubImport.export(comic("prose.epub",true),rejected,ExportFormat.XTC,device,comicOpts) }.isFailure)
        check(!rejected.exists() && original.readBytes().contentEquals(sourceBytes))
        val dest=root.resolve("batch").apply { mkdir() }
        val queueRoot=root.resolve("queue-state").toPath()
        val queue=DesktopConversionQueue(queueRoot)
        check(queue.enqueue(listOf(pdf, original, root.resolve("prose.epub")),
            dest,ExportFormat.XTC,"X4",comicOpts)==3)
        check(dest.list()?.isEmpty() == true)
        val reloaded=DesktopConversionQueue(queueRoot)
        reloaded.runPending({ false })
        check(reloaded.list().map { it.state } == listOf(
            ConversionJobState.COMPLETED, ConversionJobState.COMPLETED, ConversionJobState.FAILED))
        check(XtcBookPreview(dest.resolve("original.xtc")).pageCount==2)
        check(XtcBookPreview(dest.resolve("comic.xtc")).pageCount==2)
        check(!dest.resolve("prose.xtc").exists())
        check(original.readBytes().contentEquals(sourceBytes))
        println("PASS: Windows PDF all formats, selectable companion text, 1001 pages, cancel cleanup, EPUB spine order and prose refusal")
        println("PASS: mixed PDF/image EPUB durable queue, continued after unsupported EPUB, actual output and no accidental overwrite")
    } finally { root.deleteRecursively() }
}
