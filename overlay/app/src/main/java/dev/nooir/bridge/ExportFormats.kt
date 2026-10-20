package dev.nooir.bridge

/** Conversion preference is separate from Mihon's lossless original chapter download. */
enum class ExportFormat(val extension: String, val mime: String, val label: String) {
    AUTO("", "", "Automatic (for device)"),
    XTC("xtc", "application/octet-stream", "XTC · crisp black & white"),
    XTCH("xtch", "application/octet-stream", "XTCH · 4 gray levels"),
    EPUB("epub", "application/epub+zip", "EPUB 3 · Modern"),
    EPUB2("epub", "application/epub+zip", "EPUB 2 · Classic / e-reader compatible"),
    CBZ("cbz", "application/vnd.comicbook+zip", "CBZ · image archive");

    fun resolved(device: String = "X4"): ExportFormat {
        val profile = NooirDeviceProfiles.forId(device)
        val result = if (this == AUTO) profile.autoFormat else this
        require(profile.accepts(result)) {
            "${result.name} is not a supported export choice for ${profile.name}. Choose Auto or another supported format."
        }
        return result
    }
}

/** Different formats have fundamentally different native metadata capabilities. */
data class ExtendedMetadata(
    val title: String,
    val author: String = "",
    val artist: String = "",
    val description: String = "",
    val genres: List<String> = emptyList(),
    val publisher: String = "",
    val language: String = "en",
    val series: String = title,
    val identifier: String = "",
    val sourceUrl: String = "",
    val status: String = "",
    val rights: String = "",
    val direction: ReadingDirection = ReadingDirection.RIGHT_TO_LEFT,
) {
    fun xtc() = BookMetadata(
        title = title, author = author, publisher = publisher, language = language,
        creationUnixSeconds = System.currentTimeMillis() / 1000,
        coverPage = 0,
    )
}
/** Optional compact metadata extension for XTC/XTCH.
 * Bounded to fit low-memory readers; legacy readers can ignore the trailer.
 */
fun ExtendedMetadata.xtcRichJson(): String {
    val base = copy(
        title = title.take(240),
        author = author.take(160),
        artist = artist.take(160),
        publisher = publisher.take(120),
        series = series.take(240),
        genres = genres.take(20).map { it.take(80) },
        sourceUrl = sourceUrl.take(400),
        identifier = identifier.take(200),
        status = status.take(100),
        rights = rights.take(120),
        description = description.take(2048),
    )
    var synopsis = base.description
    while (true) {
        val json = BookArchiveWriter.jsonMetadata(base.copy(description = synopsis))
        if (json.toByteArray(Charsets.UTF_8).size <= 8192) return json
        if (synopsis.isEmpty()) return BookArchiveWriter.jsonMetadata(
            ExtendedMetadata(title = base.title.take(80), author = base.author.take(60))
        )
        synopsis = synopsis.take(synopsis.length / 2)
    }
}
