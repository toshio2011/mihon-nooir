import dev.nooir.mangabridge.NooirEpubBook
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private fun fixture(
    items: List<Pair<String, String>>,
    includeEncryption: Boolean = false,
    language: String = "en",
    spineDirection: String? = null,
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
            out.write(value.toByteArray())
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

    println("PASS: EPUB package metadata, OPF spine, offline images and XXE/DTD rejection")
}
