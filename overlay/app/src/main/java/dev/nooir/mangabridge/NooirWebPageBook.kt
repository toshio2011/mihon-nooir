package dev.nooir.mangabridge

import dev.nooir.bridge.ReflowEpubWriter
import org.jsoup.nodes.Document
import java.io.File

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
        onPage: (Int, Int, String) -> Unit = { _, _, _ -> },
    ): Result {
        require(urls.isNotEmpty()) { "Add at least one public chapter URL" }
        require(bookTitle.isNotBlank()) { "Enter a book title" }
        require(urls.distinct().size == urls.size) { "Remove repeated chapter URLs" }
        val safeUrls = urls.map(NooirWebArticle::publicHttps)
        val first = firstPage ?: NooirWebArticle.load(safeUrls.first())
        val existing = NooirWebArticle.metadata(first)
        val meta = existing.copy(
            title = bookTitle.trim().take(240),
            author = bookAuthor.trim().ifBlank { existing.author }.take(120),
            description = "Combined from ${safeUrls.size} public webpages. " + existing.description,
        )
        ReflowEpubWriter.write(output, meta) { epub ->
            for ((index, url) in safeUrls.withIndex()) {
                if (index > 0) Thread.sleep(1300L) // polite sequential requests
                onPage(index + 1, safeUrls.size, "Reading public webpage")
                val page = if (index == 0) first else NooirWebArticle.load(url)
                val title = chapterTitles.getOrNull(index)?.trim()
                    ?.takeIf { it.isNotBlank() } ?: NooirWebArticle.title(page)
                require(title.isNotBlank()) { "Page ${index + 1} has no chapter title" }
                // Avoid creating a partial EPUB from navigation-only landing
                // pages or blocked chapters; appendChapter validates content.
                NooirWebArticle.appendChapter(page, epub, title, maxImages = 25)
                onPage(index + 1, safeUrls.size, "Added " + title)
            }
        }
        return Result(meta.title, safeUrls.size)
    }
}
