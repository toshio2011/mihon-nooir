package dev.nooir.desktop.ui

import dev.nooir.desktop.suwayomi.SuwayomiChapter
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Base64
import java.util.UUID

/** Metadata only. CBZ bytes remain in the user's existing local library. */
internal data class OfflineSeriesChapter(
    val mangaId: Int,
    val mangaTitle: String,
    val chapterId: Int,
    val chapterTitle: String,
    val chapterNumber: Double,
    val sourceOrder: Int,
    val file: File,
) {
    val available: Boolean get() = file.isFile && file.extension.equals("cbz", true)
}

internal data class OfflineSeries(
    val mangaId: Int,
    val title: String,
    val chapters: List<OfflineSeriesChapter>,
)

/**
 * A bounded, versioned index of explicitly saved Suwayomi chapters.
 *
 * Never scans the filesystem, downloads pages, modifies source libraries or
 * silently removes entries. Missing CBZs stay visible until manually removed.
 * Metadata writes are atomic and preserve the existing index on errors.
 */
internal class OfflineSeriesCatalog(
    private val directory: Path = Path.of(System.getProperty("user.home"), ".mihonnooir"),
) {
    companion object {
        private const val HEADER = "MIHONNOOIR_OFFLINE_SERIES_V1"
        private const val MAX_RECORDS = 2_000
        private const val MAX_BYTES = 2_000_000L
        private const val MAX_PATH_LENGTH = 2048
        private val encoder = Base64.getUrlEncoder().withoutPadding()
        private val decoder = Base64.getUrlDecoder()
        private fun enc(value: String): String =
            encoder.encodeToString(value.toByteArray(StandardCharsets.UTF_8))
        private fun dec(value: String): String =
            String(decoder.decode(value), StandardCharsets.UTF_8)
    }

    private val index = directory.resolve("offline-series-v1.tsv")

    private fun readAll(): MutableMap<Pair<Int, Int>, OfflineSeriesChapter> {
        if (!Files.exists(index)) return linkedMapOf()
        require(Files.isRegularFile(index) && Files.size(index) <= MAX_BYTES) {
            "Offline series metadata index is invalid or too large"
        }
        val lines = Files.readAllLines(index, StandardCharsets.UTF_8)
        require(lines.firstOrNull() == HEADER && lines.size <= MAX_RECORDS + 1) {
            "Offline series index has an unknown format. Original file was preserved."
        }
        val result = linkedMapOf<Pair<Int, Int>, OfflineSeriesChapter>()
        for (line in lines.drop(1)) {
            val p = line.split('\t')
            require(p.size == 7) { "Damaged series index: original file preserved" }
            val mangaId = p[0].toInt()
            val chapterId = p[2].toInt()
            val title = dec(p[1])
            val chapterTitle = dec(p[3])
            val chapterNumber = p[4].toDouble()
            val order = p[5].toInt()
            val filename = dec(p[6])
            require(mangaId > 0 && chapterId > 0 &&
                title.length in 1..200 && chapterTitle.length in 1..250 &&
                chapterNumber.isFinite() && filename.length in 1..MAX_PATH_LENGTH &&
                File(filename).isAbsolute && filename.endsWith(".cbz", true)) {
                "Invalid series record. Original index preserved."
            }
            val record = OfflineSeriesChapter(
                mangaId, title, chapterId, chapterTitle, chapterNumber, order, File(filename))
            require(result.putIfAbsent(mangaId to chapterId, record) == null) {
                "Duplicate chapter metadata. Original index preserved."
            }
        }
        return result
    }

    private fun writeAll(records: Collection<OfflineSeriesChapter>) {
        require(records.size <= MAX_RECORDS) { "Offline series index has reached its chapter limit" }
        Files.createDirectories(directory)
        val data = buildString {
            append(HEADER).append('\n')
            for (r in records) {
                append(r.mangaId).append('\t')
                    .append(enc(r.mangaTitle)).append('\t')
                    .append(r.chapterId).append('\t')
                    .append(enc(r.chapterTitle)).append('\t')
                    .append(r.chapterNumber).append('\t')
                    .append(r.sourceOrder).append('\t')
                    .append(enc(r.file.absolutePath)).append('\n')
            }
        }
        require(data.toByteArray(StandardCharsets.UTF_8).size <= MAX_BYTES) {
            "Offline series metadata index is too large"
        }
        val temporary = directory.resolve(".offline-series-${UUID.randomUUID()}.tmp")
        try {
            Files.writeString(temporary, data, StandardCharsets.UTF_8)
            try {
                Files.move(temporary, index, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, index, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    @Synchronized
    fun remember(mangaId: Int, title: String, chapter: SuwayomiChapter, archive: File) {
        require(mangaId > 0 && chapter.id > 0)
        require(title.isNotBlank() && title.length <= 200)
        require(chapter.name.isNotBlank() && chapter.name.length <= 250)
        require(chapter.chapterNumber.isFinite())
        require(archive.isFile && archive.extension.equals("cbz", true))
        val fullPath = archive.canonicalFile
        require(fullPath.path.length <= MAX_PATH_LENGTH)
        val records = readAll()
        val key = mangaId to chapter.id
        val value = OfflineSeriesChapter(mangaId, title, chapter.id, chapter.name,
            chapter.chapterNumber, chapter.sourceOrder, fullPath)
        if (records[key] != value) {
            records[key] = value
            writeAll(records.values)
        }
    }

    @Synchronized
    fun series(): List<OfflineSeries> {
        val all = readAll().values
        return all.groupBy { it.mangaId }.map { (id, chapters) ->
            OfflineSeries(id, chapters.last().mangaTitle, chapters.sortedWith(
                compareBy<OfflineSeriesChapter> {
                    if (it.chapterNumber >= 0) it.chapterNumber else Double.MAX_VALUE
                }.thenByDescending { it.sourceOrder }.thenBy { it.chapterId }
            ))
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    }

    @Synchronized
    fun forget(mangaId: Int, chapterId: Int) {
        val records = readAll()
        if (records.remove(mangaId to chapterId) != null) writeAll(records.values)
        // Intentionally do not touch the original CBZ or other library entries.
    }
}
