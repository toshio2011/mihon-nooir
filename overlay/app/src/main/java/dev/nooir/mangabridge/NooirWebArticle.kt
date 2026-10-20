package dev.nooir.mangabridge

import dev.nooir.bridge.ReflowEpubWriter
import dev.nooir.bridge.ReflowMetadata
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.safety.Safelist
import java.io.File
import java.net.InetAddress
import java.net.URI

/**
 * Reader-friendly offline webpage -> reflowable EPUB.
 *
 * Deliberately conservative: HTTPS public sites, regular article HTML only.
 * Never executes JS, bypasses paywalls, or sends content to a third-party API.
 */
object NooirWebArticle {
    internal fun publicHttps(value: String): String {
        val url = URI(value.trim()).normalize()
        require(url.scheme.equals("https", ignoreCase = true) &&
            !url.host.isNullOrBlank() && url.port == -1 &&
            url.rawUserInfo == null && url.host.length <= 253) {
            "Use a public HTTPS webpage URL (https://...), not a local address"
        }
        val host = url.host.lowercase()
        require(host != "localhost" && !host.endsWith(".local") &&
            !host.endsWith(".internal")) { "Private URLs cannot be imported" }
        // Check both literal addresses and public hosts. Avoid intentionally
        // connecting to private/local IPs through a pasted URL.
        InetAddress.getAllByName(host).forEach { ip ->
            require(!ip.isAnyLocalAddress && !ip.isLoopbackAddress &&
                !ip.isLinkLocalAddress && !ip.isSiteLocalAddress &&
                !ip.isMulticastAddress) { "Private network URLs are not supported" }
        }
        return url.toASCIIString()
    }

    internal fun load(url: String): Document {
        var current = publicHttps(url)
        repeat(4) {
            val response = Jsoup.connect(current)
                .userAgent("MangaNooir/1.0 (offline article export)")
                .timeout(15000)
                .maxBodySize(3_000_000)
                .followRedirects(false)
                .execute()
            if (response.statusCode() in 300..399) {
                val next = response.header("Location").orEmpty()
                require(next.isNotBlank()) { "Article redirect missing destination" }
                current = publicHttps(URI(current).resolve(next).toString())
            } else {
                require(response.statusCode() in 200..299) {
                    "Webpage HTTP " + response.statusCode()
                }
                require(response.contentType()?.contains("html", ignoreCase = true) == true) {
                    "This URL is not an HTML article"
                }
                return response.parse().also { it.setBaseUri(current) }
            }
        }
        error("Too many webpage redirects")
    }

    internal fun title(doc: Document): String = (
        doc.selectFirst("meta[property=og:title]")?.attr("content")
            ?: doc.selectFirst("h1")?.text()
            ?: doc.title()
        ).trim().ifBlank { "Saved Article" }.take(240)

    internal fun metadata(doc: Document): ReflowMetadata = ReflowMetadata(
        title = title(doc),
        author = (doc.selectFirst("meta[name=author]")?.attr("content")
            ?: doc.selectFirst("[itemprop=author]")?.text().orEmpty()).take(120),
        description = (doc.selectFirst("meta[name=description]")?.attr("content")
            ?: doc.selectFirst("meta[property=og:description]")?.attr("content").orEmpty()).take(800),
        sourceUrl = doc.location(),
        language = doc.selectFirst("html")?.attr("lang")?.take(16)?.ifBlank { "en" } ?: "en",
    )

    /** Append one already-accessible chapter, keeping genuine XHTML text and
     * embedded JPEG/PNG/GIF artwork, without taking full-page screenshots.
     * Images are capped for memory/size safety; no source scripts execute.
     */
    internal fun appendChapter(
        doc: Document, epub: ReflowEpubWriter, chapterTitle: String,
        maxImages: Int = 25,
    ) {
        // Keep the actual text of a publicly served WordPress/Elementor page,
        // rather than relying only on traditional <article>/<main> wrappers.
        // Strip menus and scripts before choosing the best readable candidate.
        val contentSelectors = "article, main, [role=main], [itemprop=articleBody], " +
            ".post-content, .entry-content, .reading-content, .chapter-content, " +
            ".elementor-widget-theme-post-content, .elementor-widget-text-editor, " +
            ".et_pb_post, .single-post-content, .novel-content, #chapter-content, " +
            ".prose, [data-page-number]"
        fun stripped(element: org.jsoup.nodes.Element): org.jsoup.nodes.Element =
            element.clone().also { item ->
                item.select("script,style,nav,header,footer,aside,form,button,iframe," +
                    "video,audio,canvas,svg,noscript,.advertisement,.ads,.social-share," +
                    ".newsletter,.cookie-banner,.related-posts,.sidebar").remove()
            }
        val candidates = doc.select(contentSelectors).map(::stripped)
        val article = candidates.maxByOrNull { it.text().length }
            ?.takeIf { it.text().length >= 60 }
            ?: stripped(doc.body() ?: error("Cannot find a readable HTML page"))
        val textLength = article.text().trim().length
        require(textLength >= 60) {
            "This URL appears to be a story landing/catalog page rather than a readable chapter " +
                "('$chapterTitle': $textLength text characters). Open a public chapter with actual " +
                "prose and paste that chapter URL. JavaScript-only or sign-in-only pages " +
                "cannot be converted without an authorized source."
        }
        var images = 0
        article.select("img").forEach { img ->
            val source = listOf("data-src", "data-original", "src")
                .firstNotNullOfOrNull { attr -> img.attr(attr).takeIf { it.isNotBlank() } }
            val absolute = source?.let { runCatching { URI(doc.location()).resolve(it).toString() }.getOrNull() }
            if (absolute.isNullOrBlank() || images >= maxImages) {
                img.remove()
                return@forEach
            }
            val asset = runCatching {
                val verified = publicHttps(absolute)
                val response = Jsoup.connect(verified).ignoreContentType(true)
                    .userAgent("MangaNooir/1.0 (offline article export)")
                    .timeout(12000).maxBodySize(2_000_000).followRedirects(false).execute()
                require(response.statusCode() == 200) { "Image not accessible" }
                val mime = response.contentType().orEmpty().substringBefore(';').lowercase()
                val extension = when (mime) {
                    "image/jpeg" -> "jpg"
                    "image/png" -> "png"
                    "image/gif" -> "gif"
                    else -> error("Unsupported web image format")
                }
                val bytes = response.bodyAsBytes()
                require(bytes.size in 1..2_000_000)
                ReflowEpubWriter.ImageAsset(bytes, extension, mime)
            }.getOrNull()
            if (asset == null) img.remove()
            else {
                img.attr("src", epub.addImage(asset))
                img.removeAttr("srcset")
                img.removeAttr("data-src")
                images++
            }
        }
        val whitelist = Safelist.relaxed()
            .addTags("figure", "figcaption", "section", "h1", "h2", "h3", "h4")
            .addAttributes("img", "alt")
            .preserveRelativeLinks(true)
        val cleaned = Jsoup.clean(article.html(), doc.location(), whitelist)
        val fragment = Jsoup.parseBodyFragment(cleaned)
        fragment.outputSettings(Document.OutputSettings()
            .syntax(Document.OutputSettings.Syntax.xml)
            .escapeMode(org.jsoup.nodes.Entities.EscapeMode.xhtml)
            .prettyPrint(false))
        epub.addSection(chapterTitle,
            "<h1>" + ReflowEpubWriter.xml(chapterTitle) + "</h1>" + fragment.body().html())
    }

    fun convert(url: String, output: File, onStage: (String) -> Unit = {}) {
        onStage("Downloading webpage")
        val doc = load(url)
        onStage("Saving text and illustrations")
        ReflowEpubWriter.write(output, metadata(doc)) { epub ->
            appendChapter(doc, epub, title(doc))
        }
        onStage("EPUB complete")
    }
}
