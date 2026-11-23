package dev.nooir.mangabridge

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.File
import java.net.URI
import java.util.zip.ZipFile
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Offline EPUB2/3 package reader for the experimental page renderer.
 * Uses OPF spine order; never fetches remote assets or executes scripts.
 */
internal class NooirEpubBook private constructor(
    private val zip: ZipFile,
    val title: String,
    val author: String,
    val chapters: List<Chapter>,
    val coverPath: String?,
    val isRightToLeft: Boolean,
) : Closeable {
    data class Chapter(val path: String, val label: String)
    companion object {
        const val MAX_INPUT = 200L * 1024 * 1024
        const val MAX_ENTRY = 16L * 1024 * 1024
        private const val MAX_TOTAL = 512L * 1024 * 1024
        private const val MAX_ENTRIES = 10000

        fun open(file: File): NooirEpubBook {
            require(file.isFile && file.length() in 1..MAX_INPUT) {
                "EPUB must be a local file no larger than 200 MiB"
            }
            val zip = ZipFile(file)
            try {
                val entries = zip.entries().asSequence().toList()
                require(entries.size in 3..MAX_ENTRIES) { "Invalid EPUB archive entry count" }
                var total = 0L
                for (entry in entries) {
                    require(!entry.name.startsWith('/') && '\\' !in entry.name &&
                        entry.name.split('/').none { it == ".." || it == "." } && '\u0000' !in entry.name) {
                        "EPUB contains an unsafe archive path"
                    }
                    require(entry.size in 0..MAX_TOTAL) { "EPUB entry is too large or unknown in size" }
                    total += entry.size
                    require(total <= MAX_TOTAL) { "EPUB expands beyond 512 MiB" }
                }
                require(zip.getEntry("META-INF/encryption.xml") == null) {
                    "Encrypted EPUB resources are not supported; no DRM bypass is attempted"
                }
                val mimetype = readZip(zip, "mimetype", 100).toString(Charsets.UTF_8).trim()
                require(mimetype == "application/epub+zip") { "Not an EPUB package" }
                val container = xml(readZip(zip, "META-INF/container.xml"))
                val root = container.getElementsByTagNameNS("*", "rootfile")
                require(root.length in 1..6) { "EPUB is missing its container rootfile" }
                val opfPath = (root.item(0) as Element).getAttribute("full-path")
                val opfFile = resolve("META-INF/container.xml", "/" + opfPath)
                val opf = xml(readZip(zip, opfFile))
                val manifest = linkedMapOf<String, Pair<String, String>>()
                var officialCover: String? = null
                val items = opf.getElementsByTagNameNS("*", "item")
                require(items.length in 1..MAX_ENTRIES) { "EPUB has no OPF manifest" }
                for (index in 0 until items.length) {
                    val item = items.item(index) as? Element ?: continue
                    val id = item.getAttribute("id")
                    val href = item.getAttribute("href")
                    if (id.isBlank() || href.isBlank()) continue
                    val path = resolve(opfFile, href)
                    val media = item.getAttribute("media-type").lowercase()
                    require(zip.getEntry(path) != null) { "Missing EPUB manifest resource: " + path }
                    manifest[id] = path to media
                    if (item.getAttribute("properties").split(' ').any { it == "cover-image" } &&
                        media.startsWith("image/")) officialCover = path
                }
                if (officialCover == null) {
                    val metas = opf.getElementsByTagNameNS("*", "meta")
                    for (index in 0 until metas.length) {
                        val meta = metas.item(index) as? Element ?: continue
                        if (meta.getAttribute("name") == "cover") {
                            val candidate = manifest[meta.getAttribute("content")]
                            if (candidate?.second?.startsWith("image/") == true) officialCover = candidate.first
                        }
                    }
                }
                val spine = opf.getElementsByTagNameNS("*", "itemref")
                require(spine.length in 1..65535) { "EPUB has no readable spine" }
                val chapters = mutableListOf<Chapter>()
                for (index in 0 until spine.length) {
                    val item = spine.item(index) as? Element ?: continue
                    if (item.getAttribute("linear").equals("no", true)) continue
                    val resource = manifest[item.getAttribute("idref")]
                        ?: error("EPUB spine references a missing manifest ID")
                    require(resource.second in setOf("application/xhtml+xml", "text/html")) {
                        "Unsupported EPUB spine resource: " + resource.second
                    }
                    chapters += Chapter(resource.first, "Section " + (chapters.size + 1))
                }
                require(chapters.isNotEmpty()) { "EPUB has no linear reading sections" }
                val titles = opf.getElementsByTagNameNS("*", "title")
                val creators = opf.getElementsByTagNameNS("*", "creator")
                val title = titles.item(0)?.textContent?.trim()?.take(150).orEmpty()
                    .ifBlank { file.nameWithoutExtension.take(120) }
                val author = creators.item(0)?.textContent?.trim()?.take(90).orEmpty()
                // OPF spine direction outranks dc:language. 'default'/missing
                // direction falls back to known RTL languages, then LTR.
                val spineNode = opf.getElementsByTagNameNS("*", "spine").item(0) as? Element
                val declaredDirection = spineNode?.getAttribute("page-progression-direction")
                    ?.trim()?.lowercase().orEmpty()
                val language = opf.getElementsByTagNameNS("*", "language").item(0)
                    ?.textContent?.trim()?.lowercase().orEmpty()
                    .substringBefore('-').substringBefore('_')
                val languageIsRtl = language in setOf("ar", "fa", "he", "ur", "ps", "sd", "ug", "yi")
                val isRightToLeft = when (declaredDirection) {
                    "rtl" -> true
                    "ltr" -> false
                    else -> languageIsRtl
                }
                return NooirEpubBook(zip, title, author, chapters, officialCover, isRightToLeft)
            } catch (failure: Throwable) {
                zip.close()
                throw failure
            }
        }

        fun resolve(basePath: String, href: String): String {
            require(basePath.isNotBlank() && href.isNotBlank() && '\\' !in href) {
                "Unsafe EPUB resource reference"
            }
            val base = URI("https://nooir.invalid/" + basePath.replace(" ", "%20"))
            val target = base.resolve(href.replace(" ", "%20")).normalize()
            require(target.scheme == "https" && target.host == "nooir.invalid" &&
                target.port == -1 && target.userInfo == null && target.rawQuery == null &&
                !target.path.startsWith("//")) {
                "EPUB references an external or invalid resource"
            }
            val path = target.path.removePrefix("/")
            require(path.isNotBlank() && path.split('/').none { it == ".." || it == "." }) {
                "EPUB resource escaped its package"
            }
            return path
        }

        private fun readZip(zip: ZipFile, name: String, limit: Long = MAX_ENTRY): ByteArray {
            val entry = zip.getEntry(name) ?: error("Missing EPUB resource: " + name)
            require(!entry.isDirectory && entry.size in 0..limit) {
                "Oversized EPUB resource: " + name
            }
            return zip.getInputStream(entry).use { input ->
                val buffer = java.io.ByteArrayOutputStream()
                val temp = ByteArray(16 * 1024)
                var copied = 0L
                while (true) {
                    val n = input.read(temp)
                    if (n < 0) break
                    copied += n
                    require(copied <= limit) { "Oversized EPUB resource: " + name }
                    buffer.write(temp, 0, n)
                }
                buffer.toByteArray()
            }
        }

        /**
         * Android's bundled XML parser rejects the JAXP secure-processing
         * feature URI, unlike the desktop JDK. For untrusted EPUB metadata,
         * prohibit DTD/ENTITY declarations in the decoded XML itself and
         * additionally reject any entity resolver request. Apply parser
         * security features when supported by the current runtime.
         */
        private fun xml(bytes: ByteArray): Document {
            val charset = when {
                bytes.size >= 2 && bytes[0] == 0xff.toByte() && bytes[1] == 0xfe.toByte() ->
                    Charsets.UTF_16LE
                bytes.size >= 2 && bytes[0] == 0xfe.toByte() && bytes[1] == 0xff.toByte() ->
                    Charsets.UTF_16BE
                bytes.size >= 4 && bytes[0] == 0.toByte() && bytes[1] == '<'.code.toByte() ->
                    Charsets.UTF_16BE
                bytes.size >= 4 && bytes[0] == '<'.code.toByte() && bytes[1] == 0.toByte() ->
                    Charsets.UTF_16LE
                else -> Charsets.UTF_8
            }
            val xmlText = bytes.toString(charset)
            require(!Regex("<!\\s*(?:DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE).containsMatchIn(xmlText)) {
                "EPUB XML DTD and entity declarations are not supported"
            }
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isExpandEntityReferences = false
                runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
                runCatching { setXIncludeAware(false) }
            }
            val builder = factory.newDocumentBuilder()
            builder.setEntityResolver { _, _ ->
                throw org.xml.sax.SAXException("External EPUB XML entities are forbidden")
            }
            return builder.parse(ByteArrayInputStream(bytes))
        }
    }

    fun readXhtml(path: String): String =
        readZip(zip, path, MAX_ENTRY).toString(Charsets.UTF_8)

    fun imageBytes(chapterPath: String, src: String): ByteArray =
        imageBytesAt(resolve(chapterPath, src))

    /**
     * Use the first supported image actually present inside the EPUB package.
     * Invalid, remote, or absent alternatives are never fetched. The chosen
     * local resource still passes all existing decode and size constraints.
     */
    fun imageBytesFromCandidates(chapterPath: String, candidates: List<String>): ByteArray {
        require(candidates.isNotEmpty() && candidates.size <= 16) {
            "Invalid EPUB illustration candidates"
        }
        val extensions = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")
        for (reference in candidates) {
            if (reference.length > 2048) continue
            val path = runCatching { resolve(chapterPath, reference) }.getOrNull() ?: continue
            val extension = path.substringAfterLast('.', "").lowercase()
            if (extension !in extensions || zip.getEntry(path) == null) continue
            return imageBytesAt(path)
        }
        error("EPUB illustration has no supported local raster resource")
    }

    fun imageBytesAt(path: String): ByteArray {
        val suffix = path.substringAfterLast('.', "").lowercase()
        require(suffix in setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")) {
            "Unsupported EPUB illustration: " + suffix
        }
        return readZip(zip, path)
    }

    override fun close() = zip.close()
}
