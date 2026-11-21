package dev.nooir.mangabridge

import android.graphics.BitmapFactory
import dev.nooir.bridge.ReflowEpubWriter
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URI

/**
 * User-selected or page-supplied public cover, independently implemented.
 * Never reuse a browser session, cookies, credentials or a private address.
 */
internal object NooirWebCover {
    private const val MAX_BYTES = 1_500_000

    fun embed(doc: Document, epub: ReflowEpubWriter, selectedUrl: String = ""): Boolean {
        val override = selectedUrl.trim()
        val candidate = if (override.isNotBlank()) override else
            sequenceOf(
                doc.selectFirst("meta[property=og:image]")?.attr("content"),
                doc.selectFirst("meta[name=twitter:image]")?.attr("content"),
                doc.selectFirst("link[rel=image_src]")?.attr("href"),
            ).firstOrNull { !it.isNullOrBlank() } ?: return false
        val fetch = {
            val url = NooirWebArticle.publicHttps(URI(doc.location()).resolve(candidate).toString())
            val response = Jsoup.connect(url)
                .ignoreContentType(true).timeout(12000).maxBodySize(MAX_BYTES + 1)
                .followRedirects(false).execute()
            require(response.statusCode() == 200) { "Cover image HTTP ${response.statusCode()}" }
            val type = response.contentType().orEmpty().substringBefore(';').trim().lowercase()
            val ext = when (type) {
                "image/jpeg" -> "jpg"
                "image/png" -> "png"
                else -> error("Cover must be JPEG or PNG")
            }
            val bytes = response.bodyAsBytes()
            require(bytes.size in 8..MAX_BYTES) { "Cover exceeds 1.5 MB or is empty" }
            require(when (ext) {
                "png" -> bytes.take(8) == listOf(137, 80, 78, 71, 13, 10, 26, 10).map(Int::toByte)
                else -> bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte()
            }) { "Cover has an invalid image signature" }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            require(bounds.outWidth in 1..12000 && bounds.outHeight in 1..12000 &&
                bounds.outWidth.toLong() * bounds.outHeight.toLong() <= 16_000_000L) {
                "Cover dimensions are invalid or exceed the safe pixel limit"
            }
            ReflowEpubWriter.ImageAsset(bytes, ext, type)
        }
        // A chosen cover is intentional: invalid input must be visible to the
        // user instead of silently producing the wrong book. Auto is best effort.
        val asset = if (override.isNotBlank()) fetch() else runCatching(fetch).getOrNull() ?: return false
        val source = epub.addImage(asset, isCover = true)
        epub.addSection("Cover", """<figure><img src="$source" alt="Book cover"/></figure>""")
        return true
    }
}
