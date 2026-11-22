import dev.nooir.mangabridge.NooirLocalConversionPolicy as Policy

fun main() {
    check(Policy.classify("My Novel.epub") == Policy.Input.EPUB)
    check(Policy.classify("unnamed", "application/epub+zip") == Policy.Input.EPUB)
    check(Policy.classify("paper.PDF") == Policy.Input.PDF)
    check(Policy.classify("book", "application/pdf") == Policy.Input.PDF)
    check(Policy.classify("volume.CBZ") == Policy.Input.COMIC_ARCHIVE_CANDIDATE)
    check(Policy.classify("volume.zip") == Policy.Input.COMIC_ARCHIVE_CANDIDATE)
    check(Policy.classify("picture.PNG") == Policy.Input.IMAGE)
    check(Policy.classify("notnamed", "image/webp") == Policy.Input.IMAGE)
    check(Policy.classify("notes.docx") == Policy.Input.OTHER)
    check(Policy.classify("book.xtc") == Policy.Input.OTHER)
    check(Policy.classify("book.xtch") == Policy.Input.OTHER)

    for ((name, mime) in listOf(
        "book.epub" to "application/epub+zip",
        "paper.pdf" to "application/pdf",
        "book.xtc" to "application/octet-stream",
        "notes.docx" to "application/octet-stream",
        "book.epub" to "image/png",
        "mystery.jpg" to "application/pdf",
    )) check(!Policy.canConvertHere(name, mime)) {
        "Unsupported source was advertised as convertible: $name / $mime"
    }
    check(Policy.canConvertHere("comic.cbz", "application/zip"))
    check(Policy.canConvertHere("spread.zip"))
    check(Policy.canConvertHere("picture.jpeg", "image/jpeg"))
    check(Policy.canConvertHere("opaque", "image/png"))
    check(Policy.unsupportedHint("novel.epub").contains("not converted"))
    check(Policy.unsupportedHint("paper.pdf").contains("PDF to e-reader"))
    println("PASS: original EPUB, PDF, XTC/XTCH are never misrepresented as image conversions")
}
