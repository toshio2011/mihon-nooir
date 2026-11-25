package dev.nooir.desktop.ui

import dev.nooir.bridge.*
import dev.nooir.mangabridge.*
import org.jsoup.Jsoup
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import javax.imageio.ImageIO

fun main() {
    val root=Files.createTempDirectory("nooir-desktop-web-").toFile()
    try {
        val details=NooirWattpadPublic.fromPublicStoryDetailsJson("""{"title":"Original Observatory","user":{"name":"QA Author"},"description":"A small town kept its lights on. The keeper waited for dawn.","cover":"https://img.wattpad.com/test.jpg","storyLanguage":"en","parts":[{"id":123456,"title":"Part One"},{"id":123457,"title":"Part Two"}]}""")
        check(details.title=="Original Observatory" && details.author=="QA Author" && details.chapters.size==2)
        check(details.coverUrl=="https://img.wattpad.com/test.jpg")
        check(runCatching{NooirWattpadPublic.fromPublicStoryJson("""{"parts":[{"id":123456,"paid":true}]}""")}.isFailure)
        val cover=root.resolve("cover.png")
        val image=BufferedImage(80,120,BufferedImage.TYPE_INT_RGB)
        try{check(ImageIO.write(image,"png",cover))}finally{image.flush()}
        val asset=NooirWebCover.loadLocal(cover)
        fun page(name:String, prose:String)=Jsoup.parse("""<html lang="en"><head><title>$name</title></head><body><article><h1>$name</h1><p>$prose</p><p>The quiet evening sky held a thousand lights, and the reader opened a folded note that had waited there for many years.</p><aside>Unrelated recommendation</aside><div class="advertisement">Unrelated advertisement</div></article></body></html>""","https://example.org/chapter")
        val a=page("Part One","The little observatory overlooked the town. Everyone looked through its windows to watch the lights move across the quiet evening sky.")
        val b=page("Part Two","A second page carried the story onward. The next morning brought the promise of a long journey and several new chapters to discover.")
        check(NooirWebArticle.previewChapterText(a,"Part One").contains("little observatory"))
        for(format in ReflowEpubFormat.values()){
            val output=root.resolve(format.name+".epub")
            ReflowEpubWriter.write(output,ReflowMetadata(details.title,details.author,details.synopsis,details.language),format){writer->
                NooirWebCover.embedImage(writer,asset)
                NooirWebNovel.addSynopsis(writer,details.synopsis)
                NooirWebArticle.appendChapter(b,writer,"Part Two",maxImages=0)
                NooirWebArticle.appendChapter(a,writer,"Part One",maxImages=0)
            }
            ZipFile(output).use{zip->
                fun read(path:String)=zip.getInputStream(zip.getEntry(path)).bufferedReader().use{it.readText()}
                val opf=read("OEBPS/package.opf")
                check(opf.contains(details.title)&&opf.contains(details.author)&&opf.contains("dc:description"))
                check(read("OEBPS/text/s00001.xhtml").contains("Book cover"))
                check(read("OEBPS/text/s00002.xhtml").contains("The keeper waited for dawn."))
                check(read("OEBPS/text/s00003.xhtml").contains("A second page carried the story onward"))
                check(read("OEBPS/text/s00004.xhtml").contains("The little observatory overlooked the town"))
                check(!read("OEBPS/text/s00004.xhtml").contains("Unrelated advertisement"))
                check(zip.getEntry("OEBPS/images/img00001.png")!=null)
                check(read(if(format==ReflowEpubFormat.EPUB2)"OEBPS/toc.ncx" else "OEBPS/nav.xhtml").contains("Synopsis"))
            }
        }
        check(runCatching{NooirWebArticle.publicHttps("https://127.0.0.1/")}.isFailure)
        check(runCatching{NooirWebArticle.publicHttps("http://example.org/")}.isFailure)
        println("PASS: shared HTML extraction, Wattpad public metadata/restriction parsing, actual EPUB2/3 cover, synopsis, reordered chapters and private URL rejection")
    }finally{root.deleteRecursively()}
}
