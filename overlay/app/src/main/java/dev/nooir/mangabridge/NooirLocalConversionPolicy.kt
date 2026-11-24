package dev.nooir.mangabridge

/**
 * Honest preflight for the file picker. The worker still inspects the actual
 * source bytes before accepting a CBZ/ZIP or image. This policy NEVER claims
 * to convert EPUB/PDF/XTC/XTCH simply because they can be selected or sent.
 */
internal object NooirLocalConversionPolicy {
    enum class Input { IMAGE, COMIC_ARCHIVE_CANDIDATE, EPUB, PDF, OTHER }
    enum class Picker(val label: String) {
        EPUB("EPUB"),
        COMIC("CBZ / ZIP"),
        IMAGES("Images"),
        PDF("PDF"),
        ANY("All files"),
    }

    /**
     * Android's document providers filter by MIME, not extensions. Some
     * providers mislabel EPUBs/CBZs as ZIP or octet-stream, so the UI offers
     * an explicit "Browse all" escape hatch and validates selected names
     * after selection instead of relying on MIME alone.
     */
    fun pickerMimeTypes(picker: Picker): Array<String> = when (picker) {
        Picker.EPUB -> arrayOf("application/epub+zip", "application/x-epub+zip")
        Picker.COMIC -> arrayOf("application/vnd.comicbook+zip", "application/x-cbz",
            "application/zip", "application/x-zip-compressed")
        Picker.IMAGES -> arrayOf("image/*")
        Picker.PDF -> arrayOf("application/pdf")
        Picker.ANY -> arrayOf("*/*")
    }

    fun matchesPicker(picker: Picker, filename: String, mimeType: String = ""): Boolean =
        when (picker) {
            Picker.EPUB -> classify(filename, mimeType) == Input.EPUB
            Picker.COMIC -> classify(filename, mimeType) == Input.COMIC_ARCHIVE_CANDIDATE
            Picker.IMAGES -> classify(filename, mimeType) == Input.IMAGE
            Picker.PDF -> classify(filename, mimeType) == Input.PDF
            Picker.ANY -> true
        }

    /** Preview valid targets even before a user opens Android's file picker. */
    fun canPickerConvertTo(picker: Picker, output: String): Boolean = when (picker) {
        Picker.EPUB -> output in setOf("XTC", "XTCH")
        Picker.COMIC, Picker.IMAGES -> output in setOf("XTC", "XTCH", "EPUB", "EPUB2", "CBZ")
        Picker.PDF, Picker.ANY -> false // ANY needs actual selected files first
    }


    private val imageExt = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif")
    private val imageMime = setOf(
        "image/jpeg", "image/png", "image/webp", "image/bmp", "image/gif",
    )

    fun classify(filename: String, mimeType: String = ""): Input {
        val ext = filename.substringAfterLast('.', "").lowercase()
        val mime = mimeType.substringBefore(';').trim().lowercase()
        return when {
            ext == "epub" || mime in setOf("application/epub+zip", "application/x-epub+zip") -> Input.EPUB
            ext == "pdf" || mime == "application/pdf" -> Input.PDF
            ext in setOf("cbz", "zip") ||
                mime in setOf("application/vnd.comicbook+zip", "application/x-cbz") ->
                Input.COMIC_ARCHIVE_CANDIDATE
            ext in imageExt || mime in imageMime -> Input.IMAGE
            else -> Input.OTHER
        }
    }

    fun canConvertHere(filename: String, mimeType: String = "") =
        classify(filename, mimeType) in setOf(Input.IMAGE, Input.COMIC_ARCHIVE_CANDIDATE)

    /**
     * The EPUB pipeline only encodes real XTC/XTCH page bitmaps. No arbitrary
     * EPUB-to-CBZ, EPUB-to-PDF or EPUB-to-EPUB re-export is implied.
     */
    fun canConvertTo(filename: String, mimeType: String = "", output: String): Boolean =
        when (classify(filename, mimeType)) {
            Input.IMAGE, Input.COMIC_ARCHIVE_CANDIDATE ->
                output in setOf("XTC", "XTCH", "EPUB", "EPUB2", "CBZ")
            Input.EPUB -> output in setOf("XTC", "XTCH")
            else -> false
        }

    fun unsupportedHint(filename: String, mimeType: String = ""): String =
        when (classify(filename, mimeType)) {
            Input.EPUB -> "EPUB conversion currently supports experimental XTC/XTCH " +
                "page images only. Choose XTC or XTCH, or send the original EPUB unchanged."
            Input.PDF -> "PDF conversion uses Nooir Tools → PDF to e-reader. " +
                "This Send & Convert screen sends PDFs unchanged."
            Input.OTHER -> "This file can be sent unchanged, but is not a supported " +
                "image or comic archive for conversion."
            else -> ""
        }
}
