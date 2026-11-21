package dev.nooir.mangabridge

import dev.nooir.bridge.ReflowEpubWriter
import dev.nooir.bridge.ReflowEpubFormat
import org.jsoup.nodes.Document
import java.io.File
import java.net.URI

/**
 * Turn an ordered collection of publicly readable HTTPS pages into ONE EPUB.
 *
 * No browser scripting, login cookies, paywall bypass, AI or server needed.
 * Any inaccessible/empty chapter aborts this book rather than silently saving
 * a partial book. ReflowEpubWriter removes incomplete output on error.
 */
object NooirWebPageBook {
    data class Result(val title: String, val pages: Int)

    fun convert(
        urls: List<String>,
        output: File,
        bookTitle: String,
        bookAuthor: String = "",
        chapterTitles: List<String> = emptyList(),
        firstPage: Document? = null,
        coverUrl: String = "",
        bookDescription: String = "",
        bookLanguage: String = "",
        epubFormat: ReflowEpubFormat = ReflowEpubFormat.EPUB3,
        includeImages: Boolean = true,
        onPage: (Int, Int, String) -> Unit = { _, _, _ -> },
    ): Result {
        require(urls.isNotEmpty()) { "Add at least one public chapter URL" }
        require(bookTitle.isNotBlank()) { "Enter a book title" }
        require(urls.distinct().size == urls.size) { "Remove repeated chapter URLs" }
        val safeUrls = urls.map { NooirWebArticle.publicHttps(it) }
        val first = firstPage ?: NooirWebArticle.load(safeUrls.first())
        val existing = NooirWebArticle.metadata(first)
        val language = bookLanguage.trim().ifBlank { existing.language }
        require(language.matches(Regex("[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8}){0,3}"))) {
            "Use a valid language tag such as en, ms, ar or en-GB"
        }
        require(bookDescription.length <= 1200 && coverUrl.length <= 600) {
            "Book description or cover URL is too long"
        }
        // Validate explicit inputs before writing. No remote request or
        // source content change is made during the metadata check.
        if (coverUrl.isNotBlank()) require(coverUrl.trim().startsWith("https://", true)) {
            "Cover image must be a public HTTPS URL"
        }
        val meta = existing.copy(
            title = bookTitle.trim().take(240),
            author = bookAuthor.trim().ifBlank { existing.author }.take(120),
            description = bookDescription.trim().ifBlank {
                "Combined from ${safeUrls.size} public webpages. " + existing.description
            },
            language = language,
        )
        ReflowEpubWriter.write(output, meta, epubFormat) { epub ->
            NooirWebCover.embed(first, epub, coverUrl)
            for ((index, url) in safeUrls.withIndex()) {
                if (index > 0) Thread.sleep(1300L) // polite sequential requests
                onPage(index + 1, safeUrls.size, "Reading public webpage")
                val page = if (index == 0) first else NooirWebArticle.load(url)
                val pageUri = URI(page.location())
                require(!(pageUri.host.orEmpty().lowercase().endsWith("wattpad.com") &&
                    pageUri.path.startsWith("/story/"))) {
                    "Webpage ${index + 1} is a Wattpad story overview, not a readable chapter. " +
                        "Paste public chapter URLs instead; no partial EPUB was generated."
                }
                val title = chapterTitles.getOrNull(index)?.trim()
                    ?.takeIf { it.isNotBlank() } ?: NooirWebArticle.title(page)
                require(title.isNotBlank()) { "Page ${index + 1} has no chapter title" }
                // Avoid creating a partial EPUB from navigation-only landing
                // pages or blocked chapters; appendChapter validates content.
                NooirWebArticle.appendChapter(page, epub, title, maxImages = if (includeImages) 25 else 0)
                onPage(index + 1, safeUrls.size, "Added " + title)
            }
        }
        return Result(meta.title, safeUrls.size)
    }
}
