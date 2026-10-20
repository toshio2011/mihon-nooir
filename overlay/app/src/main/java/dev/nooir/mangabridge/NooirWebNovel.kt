package dev.nooir.mangabridge

import dev.nooir.bridge.ReflowEpubWriter
import org.jsoup.nodes.Document
import org.jsoup.Jsoup
import dev.nooir.bridge.ReflowEpubWriter.ImageAsset
import java.io.File
import java.net.URI

/**
 * Website-neutral, low-rate, public-HTML multi-chapter novel extractor.
 *
 * Inspired by WebToEpub's chapter-selection UX, implemented independently
 * rather than importing its GPLv3 extension code into MangaNooir's MIT tree.
 * This is deliberately NOT a premium/paywall/unlock client. No login tokens,
 * private site APIs, hidden chapter endpoints or browser-session extraction.
 */
object NooirWebNovel {
    data class Chapter(val url: String, val title: String)
    data class Result(val title: String, val exportedChapters: Int, val discoveredChapters: Int)


    /** Returns TOC entries from existing, publicly returned HTML only.
     * Site-specific/dynamic JS chapters may not be discoverable.
     */
    fun discover(doc: Document): List<Chapter> {
        val origin = URI(doc.location())
        val selectors = "ul.table-of-contents a[href], .chapter-list a[href], " +
            ".chapter-listing a[href], .chapters a[href], #toc a[href], " +
            "[class*=toc] a[href], a[href*='/chapter/'], a[href*='/chapter-'], " +
            "a[href*='/episode/'], a[href*='/part-']"
        val found = linkedMapOf<String, Chapter>()
        for (node in doc.select(selectors)) {
            val href = node.attr("href").trim()
            val text = node.text().trim().replace(Regex("\\s+"), " ").take(160)
            if (href.isBlank() || text.length < 2) continue
            val uri = runCatching { origin.resolve(href).normalize() }.getOrNull() ?: continue
            if (!uri.scheme.equals("https", ignoreCase = true) ||
                !uri.host.equals(origin.host, ignoreCase = true) ||
                uri.port != -1 || uri.rawUserInfo != null) continue
            if (uri.path == origin.path) continue
            if (node.hasAttr("aria-disabled") ||
                node.classNames().any { it.contains("locked", true) || it.contains("disabled", true) }) continue
            val path = uri.path.lowercase()
            val likely = Regex("(?i)\\b(chapter|chapitre|episode|part|prologue|epilogue|ch\\.?\\s*[0-9]+|bab\\s*[0-9]+|capítulo)\\b")
                .containsMatchIn(text) ||
                listOf("/chapter/", "/chapter-", "/episode/", "/part-").any { path.contains(it) } ||
                node.parents().any { it.className().contains("toc", true) ||
                    it.className().contains("chapters", true) }
            if (!likely) continue
            val target = URI("https", null, uri.host, -1, uri.path, uri.query, null).toASCIIString()
            found.putIfAbsent(target, Chapter(target, text))
        }
        return found.values.toList()
    }

    /** Optional metadata cover. Failure is nonfatal and doesn't fetch extra pages. */
    private fun embedCover(doc: Document, epub: ReflowEpubWriter) {
        val original = doc.selectFirst("meta[property=og:image]")?.attr("content")
            ?.takeIf { it.isNotBlank() } ?: return
        runCatching {
            val candidate = URI(doc.location()).resolve(original).toString()
            val url = NooirWebArticle.publicHttps(candidate)
            val response = Jsoup.connect(url).ignoreContentType(true)
                .timeout(12000).maxBodySize(1_500_000).followRedirects(false).execute()
            require(response.statusCode() == 200)
            val mime = response.contentType().orEmpty().substringBefore(';').lowercase()
            val ext = when (mime) {
                "image/jpeg" -> "jpg"
                "image/png" -> "png"
                else -> error("Not a supported book cover")
            }
            val bytes = response.bodyAsBytes()
            require(bytes.size in 1..1_500_000)
            val image = epub.addImage(ImageAsset(bytes, ext, mime), isCover = true)
            epub.addSection("Cover", """<figure><img src="$image" alt="Book cover"/></figure>""")
        }
    }

    /** One EPUB per URL. Individual chapters become EPUB sections with a TOC. */
    fun convert(
        storyUrl: String, output: File,
        startAt: Int = 1, endAt: Int = Int.MAX_VALUE,
        preloadedPage: Document? = null,
        onStage: (Int, Int, String) -> Unit = { _, _, _ -> },
    ): Result {
        require(startAt >= 1 && endAt >= startAt) { "Invalid chapter range" }
        onStage(0, 0, "Checking publicly available story")
        val page = preloadedPage ?: NooirWebArticle.load(storyUrl)
        val meta = NooirWebArticle.metadata(page)
        val chapterLinks = discover(page)
        if (chapterLinks.isEmpty()) {
            require(startAt == 1) { "This page has no visible chapter list; use chapter 1 or a directly readable chapter link." }
            if (URI(page.location()).host.lowercase().endsWith("wattpad.com") &&
                URI(page.location()).path.startsWith("/story/")) {
                error("Wattpad's story page does not expose its chapter list as public HTML to this importer. No book was generated; try publicly readable chapter URLs or an authorized EPUB export.")
            }
        }
        val chosen = if (chapterLinks.isEmpty()) {
            listOf(Chapter(page.location(), NooirWebArticle.title(page)))
        } else {
            chapterLinks.drop(startAt - 1).take(endAt - startAt + 1)
        }
        require(chosen.isNotEmpty()) {
            "No chapters in the selected range. This site may load its chapter list with JavaScript."
        }
        // "All chapters" means every chapter discoverable from public HTML, without a count limit.
        // Failed chapters stop this book rather than silently creating a truncated EPUB.
        // Never silently omit undiscovered chapters or pretend locked text is readable.
        ReflowEpubWriter.write(output, meta) { epub ->
            embedCover(page, epub)
            val total = chosen.size
            for ((i, item) in chosen.withIndex()) {
                if (i > 0) Thread.sleep(1300L) // basic rate etiquette; no burst fetching
                onStage(i + 1, total, "Fetching " + item.title)
                val chapterDoc = if (i == 0 && chapterLinks.isEmpty()) page
                    else NooirWebArticle.load(item.url)
                // Even if a source returns HTTP 200 to a sign-in/paywall message,
                // the extractor will reject pages without article text.
                NooirWebArticle.appendChapter(chapterDoc, epub, item.title, maxImages = 8)
                onStage(i + 1, total, "Saved chapter " + (i + 1) + "/" + total)
            }
        }
        return Result(meta.title, chosen.size, chapterLinks.size)
    }
}
