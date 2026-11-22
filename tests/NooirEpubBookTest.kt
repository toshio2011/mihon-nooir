import dev.nooir.mangabridge.NooirEpubBook
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private fun fixture(
    items: List<Pair<String, String>>,
    includeEncryption: Boolean = false,
): File {
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
                <metadata><dc:title>A Tiny Story</dc:title><dc:creator>Sample Author</dc:creator></metadata>
                <manifest>
                  <item id="b" href="Text/b.xhtml" media-type="application/xhtml+xml"/>
                  <item id="a" href="Text/a.xhtml" media-type="application/xhtml+xml"/>
                  <item id="cover" href="Images/cover.png" media-type="image/png" properties="cover-image"/>
                </manifest>
                <spine><itemref idref="a"/><itemref idref="b"/></spine>
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
            check(book.coverPath == "OEBPS/Images/cover.png")
            check(book.chapters.map { it.path } ==
                listOf("OEBPS/Text/a.xhtml", "OEBPS/Text/b.xhtml"))
            check(book.readXhtml(book.chapters[0].path).contains("First chapter"))
        }
    } finally { first.delete() }
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
    println("PASS: EPUB2/3 package metadata, OPF spine order, local images and trust guards")
}
