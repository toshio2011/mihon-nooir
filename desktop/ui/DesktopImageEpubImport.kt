package dev.nooir.desktop.ui

import dev.nooir.bridge.ExportFormat
import dev.nooir.desktop.NooirDesktopCli
import dev.nooir.mangabridge.MangaConversionOptions
import org.w3c.dom.Element
import java.io.File
import java.io.InputStream
import java.net.URI
import java.nio.file.Files
import java.util.zip.ZipFile
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Safe first EPUB adapter for unencrypted raster comic EPUBs.
 * Reject prose, vector-only pages and external resources rather than silently
 * extracting unordered archive images or pretending to render arbitrary HTML.
 */
internal object DesktopImageEpubImport {
    private const val XML_LIMIT = 2_000_000
    private const val IMAGE_LIMIT = 25 * 1024 * 1024
    private const val PAGE_LIMIT = 5_000 // Existing desktop comic source limit.
    private val imageTypes = mapOf("image/png" to "png", "image/jpeg" to "jpg",
        "image/gif" to "gif", "image/webp" to "webp", "image/bmp" to "bmp")

    fun export(input: File, output: File, format: ExportFormat, device: String, options: MangaConversionOptions,
        canceled: () -> Boolean = { false }): Int {
        val source = input.canonicalFile
        val target = output.canonicalFile
        require(source.isFile && source.extension.equals("epub",true))
        require(!target.exists() && target.parentFile.isDirectory && target != source)
        val stage = Files.createTempDirectory(target.parentFile.toPath(),".nooir-epub-input-").toFile()
        try {
            val count = ZipFile(source).use { zip ->
                require(zip.getEntry("META-INF/encryption.xml") == null) { "Protected/encrypted EPUB unsupported" }
                val container = xml(zip,"META-INF/container.xml")
                val roots = elements(container,"rootfile")
                require(roots.size == 1) { "EPUB must have one root package" }
                val opf = resolve("",roots.single().getAttribute("full-path"))
                val pkg = xml(zip,opf)
                val items = elements(pkg,"item").associate { el ->
                    el.getAttribute("id") to Pair(resolve(opf,el.getAttribute("href")),el.getAttribute("media-type"))
                }
                val byPath = items.values.associate { it.first to it.second }
                val spine = elements(pkg,"itemref")
                require(spine.isNotEmpty()) { "EPUB spine is empty" }
                var index = 0
                fun copyPage(path: String, mime: String) {
                    if (canceled()) throw java.util.concurrent.CancellationException("Import canceled")
                    val ext = imageTypes[mime] ?: error("Only supported raster images are accepted")
                    require(index < PAGE_LIMIT) { "Image EPUB exceeds existing desktop comic safety limit ($PAGE_LIMIT pages)" }
                    val entry = zip.getEntry(path) ?: error("Missing EPUB image: $path")
                    require(!entry.isDirectory && entry.size in 1..IMAGE_LIMIT.toLong()) { "Unsafe EPUB image size" }
                    require(stage.usableSpace > entry.size + 32L*1024*1024) { "Not enough free space for EPUB import" }
                    val file = stage.resolve("%08d.%s".format(++index,ext))
                    zip.getInputStream(entry).use { input ->
                        file.outputStream().buffered().use { out ->
                            val bytes = input.readNBytes(IMAGE_LIMIT+1)
                            require(bytes.size.toLong() == entry.size && bytes.size <= IMAGE_LIMIT)
                            out.write(bytes)
                        }
                    }
                }
                for (ref in spine) {
                    val item = items[ref.getAttribute("idref")] ?: error("Missing spine item")
                    if (item.second in imageTypes) { copyPage(item.first,item.second); continue }
                    require(item.second == "application/xhtml+xml") { "Unsupported EPUB spine resource" }
                    val document = xml(zip,item.first)
                    val body = elements(document,"body").singleOrNull() ?: error("Missing XHTML body")
                    require(body.textContent.isBlank()) { "This EPUB contains prose; use a text EPUB reader instead" }
                    val nodes = body.getElementsByTagNameNS("*","*")
                    val refs = mutableListOf<String>()
                    val allowed = setOf("div","section","span","p","img","svg","image","a","br","figure","main")
                    for (i in 0 until nodes.length) {
                        val el = nodes.item(i) as? Element ?: continue
                        val tag = el.localName ?: el.tagName
                        require(tag in allowed) { "Unsupported image EPUB layout element: $tag" }
                        if (tag == "img") {
                            require(!el.hasAttribute("srcset")) { "Responsive image sources unsupported" }
                            refs += el.getAttribute("src")
                        }
                        if (tag == "image") {
                            refs += el.getAttribute("href").ifBlank {
                                el.getAttributeNS("http://www.w3.org/1999/xlink","href")
                            }
                        }
                    }
                    require(refs.isNotEmpty()) { "EPUB spine page has no supported image" }
                    for (ref in refs) {
                        val path = resolve(item.first,ref)
                        copyPage(path,byPath[path] ?: error("Image missing from EPUB manifest"))
                    }
                }
                require(index > 0)
                index
            }
            if (canceled()) throw java.util.concurrent.CancellationException("Import canceled")
            NooirDesktopCli.main(arrayOf("--input",stage.absolutePath,"--output",target.absolutePath,
                "--format",format.name,"--device",device,"--title",source.nameWithoutExtension.take(120),
                "--options-token",options.copy(panelDiagnostics=false).token()))
            return count
        } finally { stage.deleteRecursively() }
    }

    private fun resolve(base: String, raw: String): String {
        require(raw.isNotBlank() && raw.length <= 2048 && !raw.contains('\\'))
        val ref = URI(raw)
        require(!ref.isAbsolute && ref.rawAuthority == null && !ref.path.startsWith('/') &&
            ref.rawQuery == null && ref.rawFragment == null) { "External or invalid EPUB resource" }
        // Normalization allows ordinary ../images references from a spine
        // folder but cannot escape the archive's root.
        val origin = URI("file",null,"/" + base,null)
        val resolved = origin.resolve(ref).normalize().path.removePrefix("/")
        require(resolved.isNotBlank() && resolved.split('/').all { it.isNotBlank() && it != ".." && it != "." } &&
            !resolved.contains('\\') && !resolved.contains('\u0000'))
        return resolved
    }

    private fun xml(zip: ZipFile, path: String): Element {
        val entry = zip.getEntry(path) ?: error("Missing EPUB resource: $path")
        require(!entry.isDirectory && entry.size in 1..XML_LIMIT.toLong())
        val data = zip.getInputStream(entry).use { it.readNBytes(XML_LIMIT+1) }
        require(data.size <= XML_LIMIT)
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true)
            setFeature("http://xml.org/sax/features/external-general-entities",false)
            setFeature("http://xml.org/sax/features/external-parameter-entities",false)
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd",false)
            setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"")
            setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"")
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        return factory.newDocumentBuilder().parse(data.inputStream()).documentElement
    }
    private fun elements(root: Element, name: String): List<Element> {
        val nodes = root.getElementsByTagNameNS("*",name)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }
}
