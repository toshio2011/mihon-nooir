package dev.nooir.bridge

import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * EPUB 2.0.1 image-book exporter (NOT a renamed EPUB 3).
 * EPUB 2 uses XHTML content documents, OPF 2.0, NCX, metadata cover ID,
 * and a guide cover reference. One image is retained at a time.
 *
 * Folio Nooir ContentOpfParser: OPF <= 512 KiB; meta[name=cover] must refer
 * directly to a manifest image, not the cover XHTML.
 */
object Epub2Writer {
    private const val MAX_OPF_BYTES = 512 * 1024
    private fun escape(value: String) = buildString {
        value.forEach { c -> when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            else -> if (c.code >= 32 || c == '\n' || c == '\t') append(c)
        } }
    }
    private fun writeEntry(zip: ZipOutputStream, path: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(bytes)
        zip.closeEntry()
    }
    private fun xmlEntry(zip: ZipOutputStream, path: String, xml: String) =
        writeEntry(zip, path, xml.toByteArray(StandardCharsets.UTF_8))

    fun write(pages: Sequence<ArchivePage>, output: OutputStream, metadata: ExtendedMetadata, pagesPerXhtml: Int = 1): Int {
        require(pagesPerXhtml in 1..30)
        val identifier = metadata.identifier.ifBlank {
            "urn:uuid:" + UUID.nameUUIDFromBytes(
                ("manganooir:" + metadata.series + ":" + metadata.title + ":" + metadata.author)
                    .toByteArray(StandardCharsets.UTF_8)
            )
        }
        val lang = metadata.language.ifBlank { "und" }
        val chapters = mutableListOf<Pair<String, Int>>()
        var count = 0
        var previousChapter: String? = null
        var hasRealCover = false
        val groups = mutableListOf<Int>()
        var grouped = mutableListOf<Int>()
        var groupChapter = ""
        ZipOutputStream(output).use { zip ->
            val type = "application/epub+zip".toByteArray(StandardCharsets.US_ASCII)
            val crc = CRC32().apply { update(type) }
            val first = ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = type.size.toLong()
                compressedSize = size
                this.crc = crc.value
                extra = byteArrayOf()
            }
            zip.putNextEntry(first)
            zip.write(type)
            zip.closeEntry()
            xmlEntry(zip, "META-INF/container.xml", """<?xml version="1.0" encoding="UTF-8"?>
<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0"><rootfiles><rootfile full-path="OEBPS/package.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""")

            fun flush() {
                if (grouped.isEmpty()) return
                val start = grouped.first()
                val imgs = grouped.joinToString("") { n -> "<div class=\"page\"><img src=\"../images/p%05d.png\" alt=\"Page $n\"/></div>".format(n) }
                xmlEntry(zip, "OEBPS/text/p%05d.xhtml".format(start), """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="${escape(lang)}"><head><title>${escape(groupChapter)}</title><style type="text/css">html,body{margin:0;padding:0;background:#fff;text-align:center}.page{page-break-after:always}.page:last-child{page-break-after:auto}.page img{display:block;max-width:100%;max-height:100%;width:auto;height:auto;margin:auto}</style></head><body>$imgs</body></html>""")
                groups += start
                grouped = mutableListOf()
            }
            for (page in pages) {
                require(page.width > 0 && page.height > 0 && page.imagePng.isNotEmpty()) { "Invalid image page" }
                require(count < 65535) { "EPUB page count exceeds 65535; export smaller volumes" }
                count++
                if (page.isCover && count == 1) hasRealCover = true
                if (pagesPerXhtml > 1 && grouped.isNotEmpty() && (groupChapter != page.chapter || grouped.size >= pagesPerXhtml)) flush()
                if (page.chapter != previousChapter) {
                    chapters += page.chapter to count
                    previousChapter = page.chapter
                }
                if (pagesPerXhtml > 1) { if (grouped.isEmpty()) groupChapter = page.chapter; grouped.add(count) }
                val idx = "%05d".format(count)
                writeEntry(zip, "OEBPS/images/p$idx.png", page.imagePng)
                if (pagesPerXhtml == 1) {
                    groups += count
                    xmlEntry(zip, "OEBPS/text/p$idx.xhtml", """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="${escape(lang)}"><head>
<title>${escape(page.chapter)} — $count</title>
<style type="text/css">html,body{margin:0;padding:0;background:#fff;text-align:center}
img{display:block;max-width:100%;max-height:100%;width:auto;height:auto;margin:auto}</style>
</head><body><div><img src="../images/p$idx.png" alt="Page $count"/></div></body></html>""")
                }
            }
            if (pagesPerXhtml > 1) flush()
            require(count > 0) { "At least one manga page is required" }
            val dc = buildString {
                append("<dc:identifier id=\"bookid\">${escape(identifier)}</dc:identifier>")
                append("<dc:title>${escape(metadata.title)}</dc:title>")
                if (metadata.author.isNotBlank()) append("<dc:creator>${escape(metadata.author)}</dc:creator>")
                else if (metadata.artist.isNotBlank()) append("<dc:creator>${escape(metadata.artist)}</dc:creator>")
                append("<dc:language>${escape(lang)}</dc:language>")
                if (metadata.description.isNotBlank()) append("<dc:description>${escape(metadata.description)}</dc:description>")
                if (metadata.publisher.isNotBlank()) append("<dc:publisher>${escape(metadata.publisher)}</dc:publisher>")
                metadata.genres.filter(String::isNotBlank).forEach { append("<dc:subject>${escape(it)}</dc:subject>") }
                append("<meta name=\"cover\" content=\"cover-image\"/>")
            }
            val manifest = buildString {
                append("<item id=\"ncx\" href=\"toc.ncx\" media-type=\"application/x-dtbncx+xml\"/>")
                for (i in 1..count) {
                    val idx = "%05d".format(i)
                    append("<item id=\"${if (i == 1) "cover-image" else "i$i"}\" href=\"images/p$idx.png\" media-type=\"image/png\"/>")

                }
                for (start in groups) append("<item id=\"p$start\" href=\"text/p%05d.xhtml\" media-type=\"application/xhtml+xml\"/>".format(start))
            }
            val spine = groups.joinToString("") { "<itemref idref=\"p$it\"/>" }
            val guide = if (hasRealCover)
                "<guide><reference type=\"cover\" title=\"Cover\" href=\"text/p00001.xhtml\"/>" +
                    "<reference type=\"start\" title=\"Start\" href=\"text/p00001.xhtml\"/></guide>"
                else ""
            val opf = """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="bookid">
<metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">$dc</metadata>
<manifest>$manifest</manifest><spine toc="ncx">$spine</spine>$guide</package>"""
            require(opf.toByteArray(StandardCharsets.UTF_8).size <= MAX_OPF_BYTES) {
                "EPUB 2 OPF exceeds Folio Nooir 512 KiB metadata limit; export smaller chapter ranges"
            }
            xmlEntry(zip, "OEBPS/package.opf", opf)
            val navPoints = chapters.mapIndexed { index, (title, page) ->
                val idx = "%05d".format(groups.lastOrNull { it <= page } ?: page)
                "<navPoint id=\"n${index + 1}\" playOrder=\"${index + 1}\"><navLabel><text>${escape(title.ifBlank { "Chapter ${index + 1}" })}</text></navLabel><content src=\"text/p$idx.xhtml\"/></navPoint>"
            }.joinToString("")
            xmlEntry(zip, "OEBPS/toc.ncx", """<?xml version="1.0" encoding="UTF-8"?>
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
<head><meta name="dtb:uid" content="${escape(identifier)}"/><meta name="dtb:depth" content="1"/><meta name="dtb:totalPageCount" content="0"/><meta name="dtb:maxPageNumber" content="0"/></head>
<docTitle><text>${escape(metadata.title)}</text></docTitle><navMap>$navPoints</navMap></ncx>""")
            return count
        }
    }
}
