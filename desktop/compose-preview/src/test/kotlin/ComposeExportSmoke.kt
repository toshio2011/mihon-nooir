package dev.nooir.desktop.compose

import dev.nooir.bridge.GrayscalePng
import dev.nooir.bridge.XtcBookPreview
import dev.nooir.bridge.XtcFormat
import dev.nooir.mangabridge.MangaConversionOptions
import java.io.File

fun main(args: Array<String>) {
    require(args.size == 1 && args[0].isNotBlank())
    val folder = File(args[0]).canonicalFile
    require(folder.isDirectory)
    val options = MangaConversionOptions.preset("Manga").copy(
        includeCover = false, panelMode = "PANEL_BY_PANEL", orientation = "AUTO_FIT",
        keepChapterFirstPageWhole = false,
    )
    for (format in listOf(XtcFormat.XTC, XtcFormat.XTCH)) {
        val target = File(folder, "compose-export-test.${format.extension}")
        val request = arrayOf("--mode", "export", "--base-url", "http://127.0.0.1:4567",
            "--manga-id", "1", "--chapters", "101", "--format", format.name,
            "--device", "X4", "--output", target.absolutePath,
            "--options-token", options.token(), "--download-missing", "false")
        runComposeExport(request, target)
        val book = XtcBookPreview(target)
        check(book.format == format && book.pageCount >= 3)
        for (i in 0 until book.pageCount) {
            val page = book.page(i)
            check(page.width == 480 && page.height == 800 && page.depth == format.depth)
            check(page.levels.toSet().size > 1) { "Unexpected blank page" }
            if (i == 0) File(folder, "decoded-${format.extension}-page1.png").writeBytes(GrayscalePng.encode(page))
        }
        check(runCatching { runComposeExport(request, target) }.isFailure) { "Existing output was accepted" }
        println("${format.name}: ${book.pageCount} decoded frames, ${target.length()} bytes")
    }
    println("PASS: Compose export XTC and XTCH decoded successfully")
}
