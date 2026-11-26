package dev.nooir.mangabridge

import java.io.File
import java.io.ByteArrayOutputStream
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

    private fun checkedAsset(bytes: ByteArray, declaredType: String = ""): ReflowEpubWriter.ImageAsset {
        require(bytes.size in 8..MAX_BYTES) { "Cover exceeds 1.5 MB or is empty" }
        val png = bytes.take(8) ==
            listOf(137, 80, 78, 71, 13, 10, 26, 10).map(Int::toByte)
        val jpeg = bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte()
        val mime = when {
            png -> "image/png"
            jpeg -> "image/jpeg"
            else -> error("Cover must be JPEG or PNG")
        }
        require(declaredType.isBlank() || declaredType == mime) {
            "Cover content type does not match its image bytes"
        }
        val asset = ReflowEpubWriter.ImageAsset(bytes, if (png) "png" else "jpg", mime)
        DesktopWebImageCodec.decode(asset).flush()
        return asset
    }

    /** Read only an explicitly chosen local JPEG/PNG, with a bounded stream. */
    fun loadLocal(file: File): ReflowEpubWriter.ImageAsset {
        require(file.isFile && file.length() in 8L..MAX_BYTES.toLong())
        val bytes = file.inputStream().use { input ->
            val output = ByteArrayOutputStream()
            val block = ByteArray(8192)
            while (true) {
                val read = input.read(block)
                if (read < 0) break
                require(output.size() + read <= MAX_BYTES) { "Selected cover exceeds 1.5 MB" }
                output.write(block, 0, read)
            }
            output.toByteArray()
        }
        return checkedAsset(bytes)
    }

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
            checkedAsset(response.bodyAsBytes(), type)
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
