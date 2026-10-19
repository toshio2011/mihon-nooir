package dev.nooir.bridge

import java.io.File
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Shared offline EPUB 3 container for extracted web articles and mixed PDF documents.
 * Sections and image assets are streamed to ZIP one-by-one to bound memory.
 */
data class ReflowMetadata(
    val title: String,
    val author: String = "",
    val description: String = "",
    val language: String = "en",
    val sourceUrl: String = "",
)

class ReflowEpubWriter private constructor(private val zip: ZipOutputStream, private val meta: ReflowMetadata) {
    data class ImageAsset(val bytes: ByteArray, val extension: String, val mime: String)
    private val sections = mutableListOf<Pair<String, String>>()
    private val manifest = mutableListOf<String>()
    private var imageIndex = 0

    /** Add an embedded image and get its relative EPUB XHTML URL. */
    fun addImage(asset: ImageAsset): String {
        require(asset.bytes.isNotEmpty() && asset.bytes.size <= 12 * 1024 * 1024) { "Unsupported image size" }
        val ext = asset.extension.lowercase()
        require(ext in setOf("jpg", "jpeg", "png", "gif", "svg")) { "Unsupported EPUB image type" }
        val mime = when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            else -> "image/svg+xml"
        }
        require(asset.mime == mime) { "Incorrect image media type" }
        imageIndex++
        val name = "images/img%05d.%s".format(imageIndex, ext)
        writeEntry("OEBPS/" + name, asset.bytes)
        manifest += "<item id=\"img$imageIndex\" href=\"$name\" media-type=\"$mime\"/>"
        return name
    }

    /** Body must already be sanitized, well-formed XHTML (no html/body root). */
    fun addSection(title: String, xhtmlBody: String) {
        require(sections.size < 3000) { "Too many EPUB sections" }
        require(xhtmlBody.length <= 2_000_000) { "Section too long" }
        val index = sections.size + 1
        val name = "text/s%05d.xhtml".format(index)
        val doc = """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="${xml(meta.language)}">
<head><title>${xml(title)}</title><meta name="viewport" content="width=device-width"/>
<style type="text/css">body{margin:4%;line-height:1.45;font-family:serif}p{margin:.7em 0}h1,h2,h3{break-after:avoid}img{display:block;max-width:100%;height:auto;margin:1em auto}figure{margin:1em 0;text-align:center}figcaption{font-size:.85em}pre{white-space:pre-wrap;overflow-wrap:anywhere}blockquote{margin:1em 4%}</style>
</head><body>$xhtmlBody</body></html>"""
        writeEntry("OEBPS/" + name, doc.toByteArray(Charsets.UTF_8))
        manifest += "<item id=\"s$index\" href=\"$name\" media-type=\"application/xhtml+xml\"/>"
        sections += name to title
    }

    private fun finish() {
        require(sections.isNotEmpty()) { "No readable article or document content" }
        val identifier = "urn:uuid:${UUID.randomUUID()}"
        val navItems = sections.joinToString("") { (name, title) ->
            "<li><a href=\"${xml(name)}\">${xml(title)}</a></li>"
        }
        writeEntry("OEBPS/nav.xhtml", """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>Contents</title></head>
<body><nav epub:type="toc" id="toc"><h1>Contents</h1><ol>$navItems</ol></nav></body></html>""".toByteArray(Charsets.UTF_8))
        val spine = sections.indices.joinToString("") { "<itemref idref=\"s${it+1}\"/>" }
        writeEntry("OEBPS/package.opf", """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid">
<metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
<dc:identifier id="bookid">${xml(identifier)}</dc:identifier>
<dc:title>${xml(meta.title)}</dc:title><dc:creator>${xml(meta.author)}</dc:creator>
<dc:language>${xml(meta.language.ifBlank { "en" })}</dc:language>
<dc:description>${xml(meta.description)}</dc:description>
<dc:source>${xml(meta.sourceUrl)}</dc:source><meta property="dcterms:modified">2026-10-19T00:00:00Z</meta>
</metadata><manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
${manifest.joinToString("")}</manifest><spine>$spine</spine></package>""".toByteArray(Charsets.UTF_8))
    }

    private fun writeEntry(name: String, data: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(data)
        zip.closeEntry()
    }

    companion object {
        fun write(target: File, meta: ReflowMetadata, build: (ReflowEpubWriter) -> Unit) {
            try {
                ZipOutputStream(target.outputStream().buffered()).use { zip ->
                    val type = "application/epub+zip".toByteArray(Charsets.US_ASCII)
                    val crc = CRC32().apply { update(type) }
                    val entry = ZipEntry("mimetype").apply {
                        method = ZipEntry.STORED
                        size = type.size.toLong()
                        compressedSize = size
                        this.crc = crc.value
                    }
                    zip.putNextEntry(entry); zip.write(type); zip.closeEntry()
                    val writer = ReflowEpubWriter(zip, meta)
                    writer.writeEntry("META-INF/container.xml", """<?xml version="1.0" encoding="UTF-8"?>
<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0"><rootfiles>
<rootfile full-path="OEBPS/package.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""".toByteArray(Charsets.UTF_8))
                    build(writer)
                    writer.finish()
                }
            } catch (e: Exception) {
                target.delete()
                throw e
            }
        }

        fun xml(value: String) = buildString {
            value.forEach { c -> when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> if (c.code >= 32 || c == '\n' || c == '\t') append(c)
            } }
        }
    }
}
