package dev.nooir.mangabridge

import android.graphics.BitmapFactory
import dev.nooir.bridge.ReflowEpubWriter
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URI

/**
 * Embedded EPUB book cover. Source metadata and user-supplied covers are
 * handled independently of WebToEpub; no browser cookies or auth.
 */
internal object NooirWebCover {
    private const val MAX_BYTES = 1_500_000

    /** Only a real, public, fully resolved cover URL is eligible for a collage. */
    fun detectedUrl(doc: Document): String? {
        val candidate = sequenceOf(
            doc.selectFirst("meta[property=og:image]")?.attr("content"),
            doc.selectFirst("meta[name=twitter:image]")?.attr("content"),
            doc.selectFirst("link[rel=image_src]")?.attr("href"),
        ).firstOrNull { !it.isNullOrBlank() } ?: return null
        return runCatching {
            NooirWebArticle.publicHttps(URI(doc.location()).resolve(candidate).toString())
        }.getOrNull()
    }

    /** Explicit cover failures are always visible; automatic discovery is best-effort. */
    fun load(doc: Document, selectedUrl: String = ""): ReflowEpubWriter.ImageAsset? {
        val selected = selectedUrl.trim()
        val candidate = if (selected.isNotEmpty()) selected else detectedUrl(doc) ?: return null
        val fetch = {
            val fullUrl = NooirWebArticle.publicHttps(URI(doc.location()).resolve(candidate).toString())
            val response = Jsoup.connect(fullUrl).ignoreContentType(true)
                .timeout(12000).maxBodySize(MAX_BYTES + 1)
                .followRedirects(false).execute()
            require(response.statusCode() == 200) { "Cover image HTTP ${response.statusCode()}" }
            val type = response.contentType().orEmpty().substringBefore(';').trim().lowercase()
            val extension = when (type) {
                "image/jpeg" -> "jpg"
                "image/png" -> "png"
                else -> error("Cover must be JPEG or PNG")
            }
            val bytes = response.bodyAsBytes()
            require(bytes.size in 8..MAX_BYTES) { "Cover exceeds 1.5 MB or is empty" }
            require(when (extension) {
                "png" -> bytes.take(8) == listOf(137, 80, 78, 71, 13, 10, 26, 10).map(Int::toByte)
                else -> bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte()
            }) { "Cover has an invalid image signature" }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            require(bounds.outWidth in 1..12000 && bounds.outHeight in 1..12000 &&
                bounds.outWidth.toLong() * bounds.outHeight.toLong() <= 16_000_000L) {
                "Cover dimensions are invalid or exceed the safe pixel limit"
            }
            ReflowEpubWriter.ImageAsset(bytes, extension, type)
        }
        return if (selected.isNotBlank()) fetch() else runCatching(fetch).getOrNull()
    }

    fun embedImage(epub: ReflowEpubWriter, asset: ReflowEpubWriter.ImageAsset) {
        val src = epub.addImage(asset, isCover = true)
        epub.addSection("Cover", """<figure><img src="$src" alt="Book cover"/></figure>""")
    }

    fun embed(doc: Document, epub: ReflowEpubWriter, selectedUrl: String = ""): Boolean {
        val image = load(doc, selectedUrl) ?: return false
        embedImage(epub, image)
        return true
    }
}
