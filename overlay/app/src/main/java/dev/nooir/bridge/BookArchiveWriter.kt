package dev.nooir.bridge

import java.io.OutputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Already processed page image, not a manga-source URL. No server required. */
data class ArchivePage(val chapter: String, val imagePng: ByteArray, val width: Int, val height: Int, val isCover: Boolean = false)

/** EPUB 3 fixed-layout / CBZ writing with metadata embedded in the formats readers recognize.
 * Page arrays are processed one at a time so a long comic does not live in RAM.
 */
object BookArchiveWriter {
    fun write(format: ExportFormat, pages: Sequence<ArchivePage>, output: OutputStream, meta: ExtendedMetadata): Int {
        require(format == ExportFormat.EPUB || format == ExportFormat.EPUB2 || format == ExportFormat.CBZ)
        if (format == ExportFormat.EPUB2) return Epub2Writer.write(pages, output, meta)
        ZipOutputStream(output).use { zip ->
            if (format == ExportFormat.EPUB) {
                val mimetype = "application/epub+zip".toByteArray(UTF_8)
                val crc = CRC32().apply { update(mimetype) }
                zip.putNextEntry(ZipEntry("mimetype").apply {
                    method = ZipEntry.STORED; size = mimetype.size.toLong(); compressedSize = size; this.crc = crc.value
                })
                zip.write(mimetype); zip.closeEntry()
                entry(zip, "META-INF/container.xml", """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/package.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""")
            }
            val sections = linkedMapOf<String, MutableList<Int>>()
            var count = 0
            var dedicatedCover = false
            for (page in pages) {
                require(page.width > 0 && page.height > 0 && page.imagePng.isNotEmpty()) { "Invalid image page" }
                require(count < 65535) { "Too many pages" }
                count++
                if (count == 1 && page.isCover) dedicatedCover = true
                sections.getOrPut(page.chapter) { mutableListOf() }.add(count)
                val imagePath = if (format == ExportFormat.EPUB) "OEBPS/images/p%05d.png".format(count)
                                else "pages/p%05d.png".format(count)
                entry(zip, imagePath, page.imagePng)
                if (format == ExportFormat.EPUB) {
                    entry(zip, "OEBPS/text/p%05d.xhtml".format(count), """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" lang="${xml(meta.language)}"><head><title>${xml(meta.title)}</title><meta name="viewport" content="width=${page.width},height=${page.height}"/><style>html,body{margin:0;padding:0;width:100%%;height:100%%;background:white}img{display:block;width:100%%;height:100%%;object-fit:contain}</style></head><body><img alt="Page $count" src="../images/p%05d.png"/></body></html>""".format(count))
                }
            }
            require(count > 0) { "At least one manga page is required" }
            if (format == ExportFormat.EPUB) {
                writeEpubIndex(zip, sections, count, meta, dedicatedCover)
            } else {
                writeComicInfo(zip, sections, count, meta)
            }
            return count
        }
    }

    private fun writeComicInfo(zip: ZipOutputStream, sections: Map<String, List<Int>>, count: Int, m: ExtendedMetadata) {
        val bookmarks = sections.mapValues { it.value.first() - 1 }.entries.associate { it.value to it.key }
        val pages = (0 until count).joinToString("") { p ->
            "<Page Image=\"$p\"" + (if (p == 0) " Type=\"FrontCover\"" else "") +
                (bookmarks[p]?.let { " Bookmark=\"${xml(it)}\"" } ?: "") + "/>"
        }
        entry(zip, "ComicInfo.xml", """<?xml version="1.0" encoding="UTF-8"?>
<ComicInfo xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"><Title>${xml(m.title)}</Title><Series>${xml(m.series)}</Series><Writer>${xml(m.author)}</Writer><Penciller>${xml(m.artist)}</Penciller><Publisher>${xml(m.publisher)}</Publisher><Summary>${xml(m.description)}</Summary><Genre>${xml(m.genres.joinToString(", "))}</Genre><LanguageISO>${xml(m.language)}</LanguageISO><Web>${xml(m.sourceUrl)}</Web><Manga>YesAndRightToLeft</Manga><PageCount>$count</PageCount><Pages>$pages</Pages></ComicInfo>""")
        entry(zip, "nooir-metadata.json", jsonMetadata(m)) // richer metadata for later round-trip; readers may ignore it
    }

    private fun writeEpubIndex(zip: ZipOutputStream, sections: Map<String, List<Int>>, count: Int, m: ExtendedMetadata, dedicatedCover: Boolean) {
        val uuid = m.identifier.ifBlank { "urn:uuid:${UUID.randomUUID()}" }
        val manifest = buildString {
            append("<item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>")
            for (i in 1..count) {
                append("<item id=\"i$i\" href=\"images/p%05d.png\" media-type=\"image/png\"".format(i))
                if (i == 1 && dedicatedCover) append(" properties=\"cover-image\"")
                append("/>")
                append("<item id=\"p$i\" href=\"text/p%05d.xhtml\" media-type=\"application/xhtml+xml\"/>".format(i))
            }
        }
        val spine = (1..count).joinToString("") { "<itemref idref=\"p$it\"/>" }
        val subjects = m.genres.joinToString("") { "<dc:subject>${xml(it)}</dc:subject>" }
        val direction = if (m.direction == ReadingDirection.RIGHT_TO_LEFT) "rtl" else "ltr"
        entry(zip, "OEBPS/package.opf", """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid" prefix="rendition: http://www.idpf.org/vocab/rendition/#"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="bookid">${xml(uuid)}</dc:identifier><dc:title>${xml(m.title)}</dc:title><dc:creator>${xml(m.author)}</dc:creator><dc:publisher>${xml(m.publisher)}</dc:publisher><dc:language>${xml(m.language)}</dc:language><dc:description>${xml(m.description)}</dc:description><dc:rights>${xml(m.rights)}</dc:rights>$subjects<meta property="dcterms:modified">1970-01-01T00:00:00Z</meta><meta property="rendition:layout">pre-paginated</meta><meta property="rendition:orientation">auto</meta><meta property="rendition:spread">none</meta></metadata><manifest>$manifest</manifest><spine page-progression-direction="$direction">$spine</spine></package>""")
        val chapters = sections.entries.joinToString("") { (title, pages) ->
            "<li><a href=\"text/p%05d.xhtml\">${xml(title)}</a></li>".format(pages.first())
        }
        entry(zip, "OEBPS/nav.xhtml", """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" lang="${xml(m.language)}"><head><title>Contents</title></head><body><nav epub:type="toc" id="toc"><h1>Contents</h1><ol>$chapters</ol></nav></body></html>""")
        entry(zip, "META-INF/nooir-metadata.json", jsonMetadata(m))
    }

    private fun entry(zip: ZipOutputStream, name: String, content: String) = entry(zip, name, content.toByteArray(UTF_8))
    private fun entry(zip: ZipOutputStream, name: String, content: ByteArray) {
        zip.putNextEntry(ZipEntry(name)); zip.write(content); zip.closeEntry()
    }
    private fun xml(s: String) = buildString {
        s.forEach { c -> when (c) {
            '&' -> append("&amp;"); '<' -> append("&lt;"); '>' -> append("&gt;"); '"' -> append("&quot;"); '\'' -> append("&apos;")
            else -> if (c.code >= 32 || c == '\n' || c == '\t') append(c)
        } }
    }
    /** Shared JSON metadata for EPUB/CBZ and the optional XTC/XTCH tail. */
    fun jsonMetadata(m: ExtendedMetadata): String {
        fun esc(s: String) = buildString { append('"'); s.forEach { c -> when(c) { '\\' -> append("\\\\"); '"' -> append("\\\""); '\n' -> append("\\n"); '\r' -> append("\\r"); '\t' -> append("\\t"); else -> if (c.code < 32) append("\\u%04x".format(c.code)) else append(c) } }; append('"') }
        return """{"title":${esc(m.title)},"author":${esc(m.author)},"artist":${esc(m.artist)},"description":${esc(m.description)},"genres":[${m.genres.joinToString(","){esc(it)}}],"publisher":${esc(m.publisher)},"language":${esc(m.language)},"series":${esc(m.series)},"identifier":${esc(m.identifier)},"sourceUrl":${esc(m.sourceUrl)},"status":${esc(m.status)},"rights":${esc(m.rights)}}"""
    }
}