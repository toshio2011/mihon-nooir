package dev.nooir.mangabridge

/**
 * Honest preflight for the file picker. The worker still inspects the actual
 * source bytes before accepting a CBZ/ZIP or image. This policy NEVER claims
 * to convert EPUB/PDF/XTC/XTCH simply because they can be selected or sent.
 */
internal object NooirLocalConversionPolicy {
    enum class Input { IMAGE, COMIC_ARCHIVE_CANDIDATE, EPUB, PDF, OTHER }

    private val imageExt = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif")
    private val imageMime = setOf(
        "image/jpeg", "image/png", "image/webp", "image/bmp", "image/gif",
    )

    fun classify(filename: String, mimeType: String = ""): Input {
        val ext = filename.substringAfterLast('.', "").lowercase()
        val mime = mimeType.substringBefore(';').trim().lowercase()
        return when {
            ext == "epub" || mime == "application/epub+zip" -> Input.EPUB
            ext == "pdf" || mime == "application/pdf" -> Input.PDF
            ext in setOf("cbz", "zip") -> Input.COMIC_ARCHIVE_CANDIDATE
            ext in imageExt || mime in imageMime -> Input.IMAGE
            else -> Input.OTHER
        }
    }

    fun canConvertHere(filename: String, mimeType: String = "") =
        classify(filename, mimeType) in setOf(Input.IMAGE, Input.COMIC_ARCHIVE_CANDIDATE)

    fun unsupportedHint(filename: String, mimeType: String = ""): String =
        when (classify(filename, mimeType)) {
            Input.EPUB -> "EPUB is not converted to XTC/XTCH here yet. " +
                "Send the original EPUB unchanged, or keep it for the future EPUB renderer."
            Input.PDF -> "PDF conversion uses Nooir Tools → PDF to e-reader. " +
                "This Send & Convert screen sends PDFs unchanged."
            Input.OTHER -> "This file can be sent unchanged, but is not a supported " +
                "image or comic archive for conversion."
            else -> ""
        }
}
