import dev.nooir.mangabridge.NooirLocalConversionPolicy as Policy

fun main() {
    // Step 1: input selection restricts Android's document picker.
    check(Policy.pickerMimeTypes(Policy.Picker.EPUB).contains("application/epub+zip"))
    check(Policy.pickerMimeTypes(Policy.Picker.COMIC).contains("application/zip"))
    check(Policy.pickerMimeTypes(Policy.Picker.IMAGES).toList() == listOf("image/*"))
    check(Policy.pickerMimeTypes(Policy.Picker.PDF).toList() == listOf("application/pdf"))
    check(Policy.pickerMimeTypes(Policy.Picker.ANY).toList() == listOf("*/*"))
    check(Policy.matchesPicker(Policy.Picker.EPUB, "Novel.EPUB", "application/zip"))
    check(Policy.matchesPicker(Policy.Picker.COMIC, "Manga.CBZ", "application/zip"))
    check(Policy.matchesPicker(Policy.Picker.IMAGES, "Artwork.png", "application/octet-stream"))
    check(Policy.matchesPicker(Policy.Picker.PDF, "Report.pdf", "application/octet-stream"))
    check(!Policy.matchesPicker(Policy.Picker.EPUB, "report.pdf", "application/pdf"))
    check(!Policy.matchesPicker(Policy.Picker.COMIC, "story.epub", "application/zip"))
    check(!Policy.matchesPicker(Policy.Picker.IMAGES, "notes.docx"))
    check(Policy.matchesPicker(Policy.Picker.ANY, "notes.docx"))
    // Step 2: output choices appear before file selection without claiming
    // PDF/other imports are convertible or that EPUB produces CBZ/EPUB.
    check(Policy.canPickerConvertTo(Policy.Picker.EPUB, "XTC"))
    check(Policy.canPickerConvertTo(Policy.Picker.EPUB, "XTCH"))
    check(!Policy.canPickerConvertTo(Policy.Picker.EPUB, "EPUB2"))
    check(!Policy.canPickerConvertTo(Policy.Picker.EPUB, "CBZ"))
    check(Policy.canPickerConvertTo(Policy.Picker.COMIC, "CBZ"))
    check(Policy.canPickerConvertTo(Policy.Picker.IMAGES, "EPUB2"))
    check(!Policy.canPickerConvertTo(Policy.Picker.PDF, "XTC"))
    check(!Policy.canPickerConvertTo(Policy.Picker.ANY, "XTCH"))
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
    check(Policy.canConvertTo("My Novel.epub", "application/epub+zip", "XTC"))
    check(Policy.canConvertTo("My Novel.epub", "application/epub+zip", "XTCH"))
    check(!Policy.canConvertTo("My Novel.epub", "application/epub+zip", "EPUB"))
    check(!Policy.canConvertTo("My Novel.epub", "application/epub+zip", "CBZ"))
    check(!Policy.canConvertTo("paper.pdf", "application/pdf", "XTC"))
    check(Policy.canConvertTo("comic.cbz", "application/zip", "XTC"))
    check(Policy.canConvertTo("picture.png", "image/png", "EPUB2"))
    check(Policy.canConvertHere("comic.cbz", "application/zip"))
    check(Policy.canConvertHere("spread.zip"))
    check(Policy.canConvertHere("picture.jpeg", "image/jpeg"))
    check(Policy.canConvertHere("opaque", "image/png"))
    check(Policy.unsupportedHint("novel.epub").contains("experimental XTC/XTCH"))
    check(Policy.unsupportedHint("paper.pdf").contains("PDF to e-reader"))
    println("PASS: EPUB only permits native XTC/XTCH raster output; unsupported formats are blocked")
}
