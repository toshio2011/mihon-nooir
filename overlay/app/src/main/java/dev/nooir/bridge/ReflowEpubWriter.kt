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

/** EPUB 2 supports selectable XHTML prose and diagrams, not only image-based comics. */
enum class ReflowEpubFormat { EPUB2, EPUB3 }

class ReflowEpubWriter private constructor(
    private val zip: ZipOutputStream,
    private val meta: ReflowMetadata,
    private val format: ReflowEpubFormat,
) {
    data class ImageAsset(val bytes: ByteArray, val extension: String, val mime: String)
    private val sections = mutableListOf<Pair<String, String>>()
    private val manifest = mutableListOf<String>()
    private var imageIndex = 0
    private var coverId: String? = null

    /** Add an embedded image and get its relative EPUB XHTML URL. */
    fun addImage(asset: ImageAsset, isCover: Boolean = false): String {
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
        if (isCover) coverId = "img$imageIndex"
        val properties = if (isCover && format == ReflowEpubFormat.EPUB3) " properties=\"cover-image\"" else ""
        manifest += "<item id=\"img$imageIndex\" href=\"$name\" media-type=\"$mime\"$properties/>"
        return "../" + name
    }

    /** Body must already be sanitized, well-formed XHTML (no html/body root). */
    fun addSection(title: String, xhtmlBody: String) {
        require(xhtmlBody.length <= 2_000_000) { "Section too long" }
        val index = sections.size + 1
        val name = "text/s%05d.xhtml".format(index)
        // EPUB 2 XHTML 1.1 predates <figure>/<figcaption>. Preserve their
        // visual meaning using valid <div>/<p> elements, leaving EPUB 3 as-is.
        val body = if (format == ReflowEpubFormat.EPUB2) xhtmlBody
            .replace("<figure>", "<div class=\"illustration\">")
            .replace("</figure>", "</div>")
            .replace("<figcaption>", "<p class=\"caption\">")
            .replace("</figcaption>", "</p>")
        else xhtmlBody
        val doc = """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="${xml(meta.language.ifBlank { "en" })}">
<head><title>${xml(title)}</title><meta name="viewport" content="width=device-width"/>
<style type="text/css">body{margin:4%;line-height:1.45;font-family:serif}p{margin:.7em 0}h1,h2,h3{break-after:avoid}img{display:block;max-width:100%;height:auto;margin:1em auto}figure{margin:1em 0;text-align:center}figcaption{font-size:.85em}pre{white-space:pre-wrap;overflow-wrap:anywhere}blockquote{margin:1em 4%}table{border-collapse:collapse;max-width:100%;width:100%;margin:1em 0}th,td{vertical-align:top;padding:.2em .3em}div.illustration{text-align:center;page-break-inside:avoid}.caption{font-size:.85em;text-align:center}</style>
</head><body>$body</body></html>"""
        writeEntry("OEBPS/" + name, doc.toByteArray(Charsets.UTF_8))
        manifest += "<item id=\"s$index\" href=\"$name\" media-type=\"application/xhtml+xml\"/>"
        sections += name to title
    }

    private fun finish() {
        require(sections.isNotEmpty()) { "No readable article or document content" }
        // Metadata for articles/PDFs may be genuinely unavailable.
        // EPUB 3 requires that any emitted Dublin Core element be non-empty.
        val title = meta.title.ifBlank { "Untitled" }
        val creatorTag = meta.author.takeIf { it.isNotBlank() }
            ?.let { "<dc:creator>${xml(it)}</dc:creator>" }.orEmpty()
        val descriptionTag = meta.description.takeIf { it.isNotBlank() }
            ?.let { "<dc:description>${xml(it)}</dc:description>" }.orEmpty()
        val sourceTag = meta.sourceUrl.takeIf { it.isNotBlank() }
            ?.let { "<dc:source>${xml(it)}</dc:source>" }.orEmpty()
        val identifier = "urn:uuid:${UUID.randomUUID()}"
        val navItems = sections.joinToString("") { (name, title) ->
            "<li><a href=\"${xml(name)}\">${xml(title)}</a></li>"
        }
        val spine = sections.indices.joinToString("") { "<itemref idref=\"s${it+1}\"/>" }
        val commonMetadata = "<dc:identifier id=\"bookid\">${xml(identifier)}</dc:identifier>" +
            "<dc:title>${xml(title)}</dc:title>" + creatorTag +
            "<dc:language>${xml(meta.language.ifBlank { "en" })}</dc:language>" +
            descriptionTag + sourceTag
        if (format == ReflowEpubFormat.EPUB2) {
            // Folio Nooir's EPUB2 parser has a 512 KiB OPF guard; make it
            // explicit rather than silently exporting a book it cannot open.
            val ncxItems = sections.mapIndexed { index, (name, heading) ->
                "<navPoint id=\"nav${index+1}\" playOrder=\"${index+1}\">" +
                    "<navLabel><text>${xml(heading)}</text></navLabel>" +
                    "<content src=\"${xml(name)}\"/></navPoint>"
            }.joinToString("")
            writeEntry("OEBPS/toc.ncx", """<?xml version="1.0" encoding="UTF-8"?>
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
<head><meta name="dtb:uid" content="${xml(identifier)}"/><meta name="dtb:depth" content="1"/>
<meta name="dtb:totalPageCount" content="0"/><meta name="dtb:maxPageNumber" content="0"/></head>
<docTitle><text>${xml(title)}</text></docTitle><navMap>$ncxItems</navMap></ncx>""".toByteArray(Charsets.UTF_8))
            val coverMeta = coverId?.let { "<meta name=\"cover\" content=\"$it\"/>" }.orEmpty()
            val guide = if (coverId != null) {
                "<guide><reference type=\"cover\" title=\"Cover\" href=\"${sections.first().first}\"/></guide>"
            } else ""
            val opf = """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="bookid">
<metadata xmlns:dc="http://purl.org/dc/elements/1.1/">$commonMetadata$coverMeta</metadata>
<manifest><item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
${manifest.joinToString("")}</manifest>
<spine toc="ncx">$spine</spine>$guide</package>"""
            val bytes = opf.toByteArray(Charsets.UTF_8)
            require(bytes.size <= 512 * 1024) {
                "EPUB 2 OPF exceeds 512 KiB Folio Nooir metadata limit; split into smaller volumes"
            }
            writeEntry("OEBPS/package.opf", bytes)
        } else {
            writeEntry("OEBPS/nav.xhtml", """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>Contents</title></head>
<body><nav epub:type="toc" id="toc"><h1>Contents</h1><ol>$navItems</ol></nav></body></html>""".toByteArray(Charsets.UTF_8))
            writeEntry("OEBPS/package.opf", """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid">
<metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
$commonMetadata<meta property="dcterms:modified">2026-10-19T00:00:00Z</meta>
</metadata><manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
${manifest.joinToString("")}</manifest><spine>$spine</spine></package>""".toByteArray(Charsets.UTF_8))
        }
    }

    private fun writeEntry(name: String, data: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(data)
        zip.closeEntry()
    }

    companion object {
        fun write(target: File, meta: ReflowMetadata, format: ReflowEpubFormat = ReflowEpubFormat.EPUB3, build: (ReflowEpubWriter) -> Unit) {
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
                    val writer = ReflowEpubWriter(zip, meta, format)
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
