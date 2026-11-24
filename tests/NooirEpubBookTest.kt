import dev.nooir.mangabridge.NooirEpubBook
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private fun fixture(
    items: List<Pair<String, String>>,
    includeEncryption: Boolean = false,
    language: String = "en",
    spineDirection: String? = null,
    chapterBytes: ByteArray? = null,
): File {
    val directionAttr = spineDirection?.let { " page-progression-direction=\"$it\"" }.orEmpty()
    val result = File.createTempFile("nooir-epub-fixture-", ".epub")
    ZipOutputStream(result.outputStream()).use { out ->
        val files = linkedMapOf(
            "mimetype" to "application/epub+zip",
            "META-INF/container.xml" to """<?xml version="1.0" encoding="UTF-8"?>
                <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
                <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
                </container>""".trimIndent(),
            "OEBPS/content.opf" to """<?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" xmlns:dc="http://purl.org/dc/elements/1.1/" version="3.0" unique-identifier="bookid">
                <metadata><dc:title>A Tiny Story</dc:title><dc:creator>Sample Author</dc:creator><dc:language>$language</dc:language></metadata>
                <manifest>
                  <item id="b" href="Text/b.xhtml" media-type="application/xhtml+xml"/>
                  <item id="a" href="Text/a.xhtml" media-type="application/xhtml+xml"/>
                  <item id="cover" href="Images/cover.png" media-type="image/png" properties="cover-image"/>
                </manifest>
                <spine$directionAttr><itemref idref="a"/><itemref idref="b"/></spine>
                </package>""".trimIndent(),
            "OEBPS/Text/a.xhtml" to "<html><body><p>First chapter</p></body></html>",
            "OEBPS/Text/b.xhtml" to "<html><body><p>Second chapter</p></body></html>",
            "OEBPS/Images/cover.png" to "FAKE IMAGE FOR PACKAGE-ONLY TEST",
        )
        files.putAll(items)
        if (includeEncryption) files["META-INF/encryption.xml"] = "<encryption/>"
        for ((name, value) in files) {
            out.putNextEntry(ZipEntry(name))
            out.write(if (name == "OEBPS/Text/a.xhtml" && chapterBytes != null)
                chapterBytes else value.toByteArray())
            out.closeEntry()
        }
    }
    return result
}
fun main() {
    val first = fixture(emptyList())
    try {
        NooirEpubBook.open(first).use { book ->
            check(book.title == "A Tiny Story")
            check(book.author == "Sample Author")
            check(!book.isRightToLeft)
            check(book.coverPath == "OEBPS/Images/cover.png")
            check(book.chapters.map { it.path } ==
                listOf("OEBPS/Text/a.xhtml", "OEBPS/Text/b.xhtml"))
            check(book.readXhtml(book.chapters[0].path).contains("First chapter"))
            val coverBytes = book.imageBytesFromCandidates(
                book.chapters[0].path, listOf("../Images/not-found.png", "../Images/cover.png"))
            check(coverBytes.toString(Charsets.UTF_8) == "FAKE IMAGE FOR PACKAGE-ONLY TEST")
            val accepted = book.imageBytesFromCandidates(
                book.chapters[0].path,
                listOf("../Images/cover.png", "../Images/cover.png"),
            ) { candidate -> candidate.decodeToString().startsWith("FAKE IMAGE") }
            check(accepted.contentEquals(coverBytes))
            check(runCatching {
                book.imageBytesFromCandidates(book.chapters[0].path,
                    listOf("../Images/cover.png")) { false }
            }.isFailure)
            check(runCatching {
                book.imageBytesFromCandidates(book.chapters[0].path,
                    listOf("https://external.invalid/image.png", "../Images/nope.png"))
            }.isFailure)
        }
    } finally { first.delete() }
    for ((language, direction, expectedRtl) in listOf(
        Triple("ar", null, true),
        Triple("fa-IR", "ltr", false),
        Triple("en", "rtl", true),
        Triple("en", "default", false),
        Triple("he", "default", true),
    )) {
        val sample = fixture(emptyList(), language = language, spineDirection = direction)
        try {
            NooirEpubBook.open(sample).use { book ->
                check(book.isRightToLeft == expectedRtl) {
                    "Unexpected EPUB direction: language=$language direction=$direction"
                }
            }
        } finally { sample.delete() }
    }
    val multilingual = "<html><body><p>Café 世界 مرحبا</p></body></html>"
    val encodedCases = listOf(
        "UTF-8 BOM" to (
            byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
                multilingual.toByteArray(Charsets.UTF_8)),
        "UTF-16 LE BOM" to (
            byteArrayOf(0xFF.toByte(), 0xFE.toByte()) +
                multilingual.toByteArray(Charsets.UTF_16LE)),
        "UTF-16 BE BOM" to (
            byteArrayOf(0xFE.toByte(), 0xFF.toByte()) +
                multilingual.toByteArray(Charsets.UTF_16BE)),
        "UTF-16 LE inferred" to multilingual.toByteArray(Charsets.UTF_16LE),
        "UTF-16 BE inferred" to multilingual.toByteArray(Charsets.UTF_16BE),
    )
    for ((label, payload) in encodedCases) {
        val sample = fixture(emptyList(), chapterBytes = payload)
        try {
            NooirEpubBook.open(sample).use { book ->
                check(book.readXhtml(book.chapters[0].path) == multilingual) {
                    "$label XHTML was not decoded correctly"
                }
            }
        } finally { sample.delete() }
    }
    check(NooirEpubBook.resolve("OEBPS/Text/one.xhtml", "../Images/book.png") ==
        "OEBPS/Images/book.png")
    for (href in listOf(
        "http://outside.example/pic.jpg", "https://outside.example/pic.jpg",
        "//outside.example/pic.jpg", "../nope.jpg?secret=1", "",
    )) {
        check(runCatching { NooirEpubBook.resolve("OEBPS/Text/one.xhtml", href) }.isFailure) {
            "An untrusted EPUB resource escaped validation: " + href
        }
    }
    val encrypted = fixture(emptyList(), includeEncryption = true)
    try {
        val failure = runCatching { NooirEpubBook.open(encrypted).use {} }
        check(failure.isFailure)
        check(failure.exceptionOrNull()?.message?.contains("Encrypted EPUB") == true)
    } finally { encrypted.delete() }
    val hostileContainer = fixture(listOf(
        "META-INF/container.xml" to """<?xml version="1.0"?>
            <!DOCTYPE container [<!ENTITY hostile SYSTEM "file:///etc/passwd">]>
            <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles>
            </container>""".trimIndent(),
    ))
    try {
        val result = runCatching { NooirEpubBook.open(hostileContainer).use {} }
        check(result.isFailure)
        check(result.exceptionOrNull()?.message?.contains("DTD and entity") == true)
    } finally { hostileContainer.delete() }

    val hostileOpf = fixture(listOf(
        "OEBPS/content.opf" to """<?xml version="1.0"?>
            <!DOCTYPE package SYSTEM "https://untrusted.example/epub.dtd">
            <package xmlns="http://www.idpf.org/2007/opf" />""".trimIndent(),
    ))
    try {
        val result = runCatching { NooirEpubBook.open(hostileOpf).use {} }
        check(result.isFailure)
        check(result.exceptionOrNull()?.message?.contains("DTD and entity") == true)
    } finally { hostileOpf.delete() }

    println("PASS: EPUB package, UTF-8/UTF-16 XHTML, RTL, offline images and XXE/DTD rejection")
}
