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
        // Simple prose, vector artwork plus prose, and a scan-like graphics page.
        val mixed=root.resolve("mixed-layout.pdf")
        PDDocument().use { doc ->
            repeat(3) { i ->
                val page=PDPage(PDRectangle(180f,240f)); doc.addPage(page)
                PDPageContentStream(doc,page).use { stream ->
                    if (i != 2) {
                        stream.beginText();stream.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA),12f)
                        stream.newLineAtOffset(12f,200f);stream.showText("Original selectable text");stream.endText()
                    }
                    if (i != 0) { stream.addRect(12f,30f,100f,60f);stream.fill() }
                }
            }
            doc.save(mixed)
        }
        val mixedBytes=mixed.readBytes()
        for (format in listOf(ExportFormat.EPUB2,ExportFormat.EPUB)) {
            val hybrid=root.resolve("hybrid-"+format.name+".epub")
            check(DesktopPdfImport.exportReflow(mixed,hybrid,format,device,opts)==3)
            ZipFile(hybrid).use { zip ->
                fun section(i:Int)=zip.getInputStream(zip.getEntry("OEBPS/text/s%05d.xhtml".format(i))).bufferedReader().use { it.readText() }
                check(section(1).contains("Original selectable text") && !section(1).contains("<img "))
                check(section(2).contains("Original selectable text") && section(2).contains("<img "))
                check(section(3).contains("<img ") && !section(3).contains("Extracted text"))
            }
        }
        val hybridCanceled=root.resolve("hybrid-canceled.epub")
        var hybridProcessed=0
        check(runCatching { DesktopPdfImport.exportReflow(mixed,hybridCanceled,ExportFormat.EPUB,device,opts,
            canceled={hybridProcessed>=1},progress={n,_->hybridProcessed=n}) }.isFailure)
        check(!hybridCanceled.exists() && mixedBytes.contentEquals(mixed.readBytes()))
        check(runCatching { DesktopPdfImport.exportReflow(mixed,root.resolve("invalid.xtc"),ExportFormat.XTC,device,opts) }.isFailure)
        val hybridDest=root.resolve("hybrid-batch").apply { mkdir() }
        val hybridRoot=root.resolve("hybrid-queue").toPath()
        val hybridQueue=DesktopConversionQueue(hybridRoot)
        val corruptPdf=root.resolve("corrupt.pdf").apply { writeText("Not a PDF") }
        check(runCatching { hybridQueue.enqueue(listOf(mixed),hybridDest,ExportFormat.XTC,"X4",opts,pdfReflow=true) }.isFailure)
        check(hybridQueue.enqueue(listOf(corruptPdf,mixed),hybridDest,ExportFormat.EPUB2,"X4",opts,pdfReflow=true)==2)
        val hybridReload=DesktopConversionQueue(hybridRoot)
        check(hybridReload.list().all { it.pdfReflow })
        hybridReload.runPending({false})
        check(hybridReload.list().map { it.state }==listOf(ConversionJobState.FAILED,ConversionJobState.COMPLETED))
        check(!hybridDest.resolve("corrupt.epub").exists())
        ZipFile(hybridDest.resolve("mixed-layout.epub")).use { zip ->
            val first=zip.getInputStream(zip.getEntry("OEBPS/text/s00001.xhtml")).bufferedReader().use { it.readText() }
            check(first.contains("Original selectable text") && !first.contains("<img "))
        }
        println("PASS: hybrid PDF EPUB2/3, text-only reflow, graphic/scan fallback, cancellation and durable batch failure isolation")
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
        // Real text EPUB2/3 from the shared writer, then inspect saved XTC/XTCH.
        val proseDevice=NooirDeviceProfiles.custom(240,320)
        for (epubFormat in dev.nooir.bridge.ReflowEpubFormat.values()) {
            val bookFile=root.resolve("text-" + epubFormat.name + ".epub")
            dev.nooir.bridge.ReflowEpubWriter.write(bookFile,dev.nooir.bridge.ReflowMetadata("Original novel"),epubFormat) { writer ->
                writer.addSection("Chapter one","<h1>Chapter one</h1><p>" + "Original words for offline reading. ".repeat(100) + "</p>")
                writer.addSection("Chapter two","<h1>Chapter two</h1><p>Another chapter.</p>")
            }
            val bytes=bookFile.readBytes()
            val small=root.resolve(epubFormat.name+"-small.xtc")
            val large=root.resolve(epubFormat.name+"-large.xtch")
            val smallCount=DesktopTextEpubImport.export(bookFile,small,ExportFormat.XTC,proseDevice,
                DesktopTextEpubImport.Settings(14,8),comicOpts)
            val largeCount=DesktopTextEpubImport.export(bookFile,large,ExportFormat.XTCH,proseDevice,
                DesktopTextEpubImport.Settings(28,12),comicOpts)
            check(smallCount > 1 && largeCount > smallCount)
            check(XtcBookPreview(small).pageCount==smallCount && XtcBookPreview(large).pageCount==largeCount)
            check(XtcBookPreview(small).page(0).levels.any { it.toInt()==0 })
            val stopped=root.resolve(epubFormat.name+"-stopped.xtc")
            check(runCatching { DesktopTextEpubImport.export(bookFile,stopped,ExportFormat.XTC,proseDevice,
                DesktopTextEpubImport.Settings(14,8),comicOpts,{ true }) }.isFailure)
            check(!stopped.exists() && bytes.contentEquals(bookFile.readBytes()))
        }
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
        // New settings survive process restart; old eight-column jobs remain unchanged.
        val novelRoot=root.resolve("novel-queue").toPath()
        val novelDest=root.resolve("novel-output").apply { mkdir() }
        val novelQueue=DesktopConversionQueue(novelRoot)
        val smallSettings=DesktopTextEpubImport.Settings(14,8,118,"Serif","Auto")
        val largeSettings=DesktopTextEpubImport.Settings(28,24,150,"SansSerif","Left to right")
        val novels=dev.nooir.bridge.ReflowEpubFormat.values().map { root.resolve("text-" + it.name + ".epub") }
        val broken=root.resolve("broken.epub").apply { writeText("Not an EPUB") }
        check(runCatching { novelQueue.enqueue(listOf(novels.first()),novelDest,ExportFormat.EPUB,"X4",comicOpts,smallSettings) }.isFailure)
        check(runCatching { novelQueue.enqueue(listOf(pdf),novelDest,ExportFormat.XTC,"X4",comicOpts,smallSettings) }.isFailure)
        check(novelQueue.enqueue(listOf(novels.first(), broken),novelDest,ExportFormat.XTC,"X4",comicOpts,smallSettings)==2)
        check(novelQueue.enqueue(listOf(novels.last()),novelDest,ExportFormat.XTCH,"X4",comicOpts,largeSettings)==1)
        val restartedNovelQueue=DesktopConversionQueue(novelRoot)
        check(restartedNovelQueue.list().map { it.textEpub } == listOf(smallSettings,smallSettings,largeSettings))
        var started=0
        restartedNovelQueue.runPending({started>=1}) {started++}
        check(restartedNovelQueue.list().map { it.state } == listOf(ConversionJobState.COMPLETED,ConversionJobState.PENDING,ConversionJobState.PENDING))
        DesktopConversionQueue(novelRoot).runPending({false})
        check(novelQueue.list().map { it.state } == listOf(ConversionJobState.COMPLETED,ConversionJobState.FAILED,ConversionJobState.COMPLETED))
        check(XtcBookPreview(novelDest.resolve(novels.first().nameWithoutExtension+".xtc")).pageCount>1)
        check(XtcBookPreview(novelDest.resolve(novels.last().nameWithoutExtension+".xtch")).pageCount>1)
        check(!novelDest.resolve("broken.xtc").exists())
        check(reloaded.list().all { it.textEpub == null })
        println("PASS: text EPUB durable typography, restart/pause/resume, unsupported output rejection, failed book isolation and actual XTC/XTCH")
        println("PASS: Windows PDF all formats, selectable companion text, 1001 pages, cancel cleanup, EPUB spine order and prose refusal")
        println("PASS: mixed PDF/image EPUB durable queue, continued after unsupported EPUB, actual output and no accidental overwrite")
    } finally { root.deleteRecursively() }
}
