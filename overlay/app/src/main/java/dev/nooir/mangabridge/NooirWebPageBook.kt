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

    /** Shared collage construction for actual EPUB covers and the editor preview. */
    internal fun collageAsset(
        first: Document,
        preloaded: Map<Int, Document>,
        extraCovers: List<String>,
    ): ReflowEpubWriter.ImageAsset {
        val sources = if (extraCovers.isNotEmpty()) {
                    extraCovers.map { chosen ->
                        NooirWebCover.load(first, chosen)
                            ?: error("Selected collage cover could not be loaded")
                    }
                } else {
                    preloaded.toSortedMap().values
                        .mapNotNull { doc ->
                            NooirWebCover.detectedUrl(doc)?.let { candidate -> candidate to doc }
                        }
                        .distinctBy { it.first }.take(4)
                        .mapNotNull { (cover, doc) -> NooirWebCover.load(doc, cover) }
                }
        // Two different image URLs may return the exact same cover.
        // Deduplicate the decoded source bytes before building the grid.
                val sourcesByHash = sources.distinctBy { asset ->
                    java.security.MessageDigest.getInstance("SHA-256")
                        .digest(asset.bytes).joinToString("") { byte ->
                            "%02x".format(byte.toInt() and 0xff)
                        }
                }
                if (extraCovers.isNotEmpty()) require(sourcesByHash.size == sources.size) {
                    "Two selected cover URLs returned identical artwork. Choose distinct book covers."
                }
                require(sourcesByHash.size in 2..4) {
                    "Only ${sourcesByHash.size} distinct public source covers could be loaded. " +
                    "Provide 2–4 direct HTTPS JPEG/PNG cover URLs, or switch to a single cover."
                }
        return NooirCoverCollage.compose(sourcesByHash)
    }

    /** The preview uses the same image validation and collage layout as export.
     * The caller performs this explicit network work on Dispatchers.IO.
     */
    internal fun previewCoverAsset(
        urls: List<String>,
        coverUrl: String,
        collageCover: Boolean,
        collageCoverUrls: List<String>,
    ): ReflowEpubWriter.ImageAsset? {
        require(urls.isNotEmpty()) { "Add public chapter URLs before previewing a cover" }
        require(!collageCover || coverUrl.isBlank()) { "Select either a single or combined cover" }
        val safeUrls = urls.take(4).map { NooirWebArticle.publicHttps(it) }
        val first = NooirWebArticle.load(safeUrls.first())
        if (!collageCover) return NooirWebCover.load(first, coverUrl)
        require(collageCoverUrls.isEmpty() ||
            (collageCoverUrls.size in 2..4 &&
                collageCoverUrls.distinct().size == collageCoverUrls.size)) {
            "Select 2–4 different public cover URLs"
        }
        val extras = collageCoverUrls.map { NooirWebArticle.publicHttps(it) }
        val preloaded = mutableMapOf(0 to first)
        if (extras.isEmpty()) {
            for (index in 1 until safeUrls.size) {
                Thread.sleep(1300L)
                preloaded[index] = NooirWebArticle.load(safeUrls[index])
            }
        }
        return collageAsset(first, preloaded, extras)
    }

    fun convert(
        urls: List<String>,
        output: File,
        bookTitle: String,
        bookAuthor: String = "",
        chapterTitles: List<String> = emptyList(),
        firstPage: Document? = null,
        coverUrl: String = "",
        collageCover: Boolean = false,
        collageCoverUrls: List<String> = emptyList(),
        bookDescription: String = "",
        bookLanguage: String = "",
        epubFormat: ReflowEpubFormat = ReflowEpubFormat.EPUB3,
        includeImages: Boolean = true,
        contentSelector: String = "",
        removeSelector: String = "",
        onPage: (Int, Int, String) -> Unit = { _, _, _ -> },
    ): Result {
        require(urls.isNotEmpty()) { "Add at least one public chapter URL" }
        require(bookTitle.isNotBlank()) { "Enter a book title" }
        NooirWebExtractRules.validate(contentSelector, removeSelector)
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
        require(!collageCover || coverUrl.isBlank()) {
            "Choose either one custom cover or a combined cover, not both"
        }
        require(collageCover || collageCoverUrls.isEmpty()) {
            "Extra cover URLs require combined cover mode"
        }
        require(collageCoverUrls.isEmpty() || (
            collageCoverUrls.size in 2..4 && collageCoverUrls.distinct().size == collageCoverUrls.size
        )) { "Use 2–4 different cover URLs for the collage" }
        val extraCovers = collageCoverUrls.map {
            require(it.length <= 600) { "A collage cover URL exceeds 600 characters" }
            NooirWebArticle.publicHttps(it)
        }
        // The first four source webpages are inspected only when the user
        // chooses automatic collage covers. Each fetched page is later used
        // for its actual EPUB chapter, avoiding duplicate requests.
        val preloaded = mutableMapOf(0 to first)
        if (collageCover && extraCovers.isEmpty()) {
            val inspectCount = minOf(safeUrls.size, 4)
            for (i in 1 until inspectCount) {
                Thread.sleep(1300L)
                onPage(0, inspectCount, "Inspecting cover metadata for source " + (i + 1))
                preloaded[i] = NooirWebArticle.load(safeUrls[i])
            }
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
            if (collageCover) {
                NooirWebCover.embedImage(epub, collageAsset(first, preloaded, extraCovers))
            } else {
                NooirWebCover.embed(first, epub, coverUrl)
            }
            for ((index, url) in safeUrls.withIndex()) {
                if (index > 0 && index !in preloaded) Thread.sleep(1300L)
                onPage(index + 1, safeUrls.size, "Reading public webpage")
                val page = preloaded.remove(index) ?: NooirWebArticle.load(url)
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
                NooirWebArticle.appendChapter(
                    page, epub, title, maxImages = if (includeImages) 25 else 0,
                    contentSelector = contentSelector, removeSelector = removeSelector,
                )
                onPage(index + 1, safeUrls.size, "Added " + title)
            }
        }
        return Result(meta.title, safeUrls.size)
    }
}
