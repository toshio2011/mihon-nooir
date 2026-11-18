package dev.nooir.desktop.ui

import dev.nooir.bridge.ExportFormat
import dev.nooir.bridge.XtcBookPreview
import dev.nooir.desktop.storage.DesktopDataHome
import dev.nooir.mangabridge.MangaConversionOptions
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO

/** Standalone installed-runtime QA, deliberately excluded from the app JAR. */
fun main(args: Array<String>) {
    check(args.size == 1 && args[0] in setOf("seed","verify"))
    check(!System.getenv(DesktopDataHome.ENV_NAME).isNullOrBlank())
    val root=DesktopDataHome.root()
    val marker=root.resolve("nooir-installed-qa.marker")
    check(Files.isRegularFile(marker) && Files.readString(marker).trim()=="disposable installer acceptance")
    val comic=root.resolve("original-comic").toFile()
    val out=root.resolve("exports").toFile()
    val library=LocalLibraryStore()
    val bookmarks=ReaderBookmarkStore()
    val jobs=DesktopConversionQueue()
    val preferences=DesktopConversionPreferences()
    val savedOptions=MangaConversionOptions.preset("Manhua").copy(
        preset="Custom",panelMode="ROW_BY_ROW",orientation="AUTO_FIT",
        enlargeOversizedSplits=false,minimumScreenFillPercent=65,renderAlgorithm="ATKINSON")
    if(args[0]=="seed") {
        check(preferences.load().options==MangaConversionOptions.preset("Manga"))
        preferences.save(savedOptions)
        check(!comic.exists() && !out.exists() && library.books().isEmpty() && jobs.list().isEmpty())
        comic.mkdir();out.mkdir()
        val image=BufferedImage(48,72,BufferedImage.TYPE_INT_RGB)
        try {
            val g=image.createGraphics()
            try { g.color=java.awt.Color.WHITE;g.fillRect(0,0,48,72);g.color=java.awt.Color.BLACK;g.fillRect(8,12,32,48) } finally { g.dispose() }
            check(ImageIO.write(image,"png",comic.resolve("001.png")))
            check(ImageIO.write(image,"png",comic.resolve("002.png")))
        } finally { image.flush() }
        library.add(comic);library.markOpened(comic,123456L)
        bookmarks.add(comic,1,123457L)
        val options=MangaConversionOptions.preset("Manga").copy(cropWhiteMargins=false,splitSpreads=false,panelMode="OFF")
        check(jobs.enqueue(listOf(comic),out,ExportFormat.XTC,"X4",options)==1)
        jobs.runPending({false})
        check(jobs.enqueue(listOf(comic),out,ExportFormat.XTCH,"X4",options)==1)
    }
    check(preferences.load()==DesktopConversionPreferences.Loaded(savedOptions))
    val entry=library.books().single()
    check(entry.available && entry.file.canonicalFile==comic.canonicalFile && entry.lastOpenedAt==123456L)
    val mark=bookmarks.list(comic).single()
    check(mark.pageIndex==1 && mark.createdAt==123457L)
    val list=jobs.list()
    check(list.size==2 && list[0].state==ConversionJobState.COMPLETED && list[1].state==ConversionJobState.PENDING)
    val completed=File(list[0].output)
    check(completed.isFile && XtcBookPreview(completed).pageCount==2)
    check(!File(list[1].output).exists())
    check(comic.resolve("001.png").isFile && comic.resolve("002.png").isFile)
    println("PASS: installed-runtime " + args[0] + " library, bookmark, completed export and pending conversion persisted")
}
