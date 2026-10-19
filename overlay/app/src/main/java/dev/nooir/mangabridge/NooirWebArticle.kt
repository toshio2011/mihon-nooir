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
    private fun publicHttps(value: String): String {
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

    private fun load(url: String): Document {
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

    fun convert(url: String, output: File, onStage: (String) -> Unit = {}) {
        onStage("Downloading webpage")
        val doc = load(url)
        val title = (doc.selectFirst("meta[property=og:title]")?.attr("content")
            ?: doc.selectFirst("h1")?.text()
            ?: doc.title()).trim().ifBlank { "Saved Article" }.take(240)
        val author = (doc.selectFirst("meta[name=author]")?.attr("content")
            ?: doc.selectFirst("[itemprop=author]")?.text().orEmpty()).take(120)
        val description = (doc.selectFirst("meta[name=description]")?.attr("content")
            ?: doc.selectFirst("meta[property=og:description]")?.attr("content").orEmpty()).take(800)
        val root = doc.select("article, main, [itemprop=articleBody], .post-content, .entry-content")
            .maxByOrNull { it.text().length }
            ?: doc.body()
            ?: error("Cannot find readable article content")
        val article = root.clone()
        article.select("script,style,nav,header,footer,aside,form,button,iframe,video,audio,canvas,svg, noscript," +
            ".advertisement,.ads,.social-share,.newsletter,.cookie-banner").remove()
        require(article.text().length >= 60) {
            "No readable article text found. JavaScript-only and login-required websites may not work."
        }
        onStage("Saving text and illustrations")
        ReflowEpubWriter.write(output, ReflowMetadata(
            title = title, author = author, description = description,
            sourceUrl = doc.location(),
            language = doc.selectFirst("html")?.attr("lang")?.take(16)?.ifBlank { "en" } ?: "en",
        )) { epub ->
            var images = 0
            article.select("img").forEach { img ->
                val source = listOf("data-src", "data-original", "src")
                    .firstNotNullOfOrNull { attr -> img.attr(attr).takeIf { it.isNotBlank() } }
                val absolute = source?.let { runCatching { URI(doc.location()).resolve(it).toString() }.getOrNull() }
                if (absolute.isNullOrBlank() || images >= 25) {
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
            epub.addSection(title, "<h1>" + ReflowEpubWriter.xml(title) + "</h1>" +
                fragment.body().html())
        }
        onStage("EPUB complete")
    }
}
