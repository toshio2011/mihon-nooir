package dev.nooir.desktop.ui

import dev.nooir.bridge.ReflowEpubFormat
import dev.nooir.mangabridge.NooirWebArticle
import dev.nooir.mangabridge.NooirWebChapterDiscovery
import dev.nooir.mangabridge.NooirWebChapterPlan
import dev.nooir.mangabridge.NooirWebPageBook
import java.nio.file.Files
import java.util.zip.ZipFile
import org.jsoup.Jsoup

/** Explicit network acceptance; no credentials, no restricted content, no retained book artifacts. */
fun main() {
    val root = Files.createTempDirectory("nooir-live-web-").toFile()
    try {
        // Public-domain US government text, one page only.
        val link = "https://www.archives.gov/founding-docs/declaration-transcript"
        val page = NooirWebArticle.load(link)
        val article = root.resolve("public-domain.epub")
        DesktopWebExportSession().export(article) { temp, checkpoint ->
            NooirWebPageBook.convert(listOf(link), temp, "Public domain acceptance",
                firstPage = page, includeImages = false, epubFormat = ReflowEpubFormat.EPUB3,
                onPage = { _, _, _ -> checkpoint() })
        }
        ZipFile(article).use { zip ->
            val body = zip.entries().asSequence().filter { it.name.startsWith("OEBPS/text/") && it.name.endsWith(".xhtml") }
                .joinToString(" ") { entry -> zip.getInputStream(entry).bufferedReader().use { it.readText() } }
            check(Jsoup.parse(body).text().contains("When in the Course of human events", ignoreCase = true))
        }
        println("PASS: actual public-domain HTTPS page exported to EPUB3 with expected text")
        // Previously used public story link; analyze only public metadata.
        // Never fetch locked parts, log prose, or upload the generated book.
        val info = try {
            NooirWebChapterDiscovery.inspect("https://www.wattpad.com/story/413612349")
        } catch (e: Exception) {
            error("Live Wattpad metadata unavailable; this acceptance gate remains open (" +
                e.javaClass.simpleName + ")")
        }
        check(info.title.isNotBlank() && info.description.isNotBlank() && info.detectedCoverUrl.isNotBlank()) {
            "Public story did not supply complete cover/synopsis metadata"
        }
        val chosen = NooirWebChapterPlan.export(info.chapters.take(1))
        val target = root.resolve("live-story.epub")
        DesktopWebExportSession().export(target) { temp, checkpoint ->
            NooirWebPageBook.convert(chosen.urls, temp, info.title, info.author, chosen.titles,
                coverUrl = info.detectedCoverUrl, bookDescription = info.description,
                bookLanguage = info.language.ifBlank { "en" }, includeImages = false,
                epubFormat = ReflowEpubFormat.EPUB3, onPage = { _, _, _ -> checkpoint() })
        }
        ZipFile(target).use { zip ->
            val opf = zip.getInputStream(zip.getEntry("OEBPS/package.opf")).bufferedReader().use { it.readText() }
            check(opf.contains("dc:description") && opf.contains("cover-image"))
            val chapters = zip.entries().asSequence().filter { it.name.startsWith("OEBPS/text/") && it.name.endsWith(".xhtml") }.toList()
            check(chapters.size >= 3) { "Expected cover, synopsis and chapter" }
            val chapter = zip.getInputStream(chapters.last()).bufferedReader().use { it.readText() }
            check(Jsoup.parse(chapter).text().length >= 100) { "Public chapter text missing" }
        }
        println("PASS: live public Wattpad metadata, embedded cover, synopsis and one readable chapter")
    } finally {
        root.deleteRecursively()
    }
}
