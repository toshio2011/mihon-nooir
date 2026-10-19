package dev.nooir.mangabridge

import android.content.Context
import android.net.Uri
import dev.nooir.bridge.BookChapter
import dev.nooir.bridge.ExportFormat
import dev.nooir.bridge.ExtendedMetadata
import dev.nooir.bridge.ArchivePage
import dev.nooir.bridge.BookArchiveWriter
import dev.nooir.bridge.NooirUpload
import java.util.UUID
import dev.nooir.bridge.XtcCodec
import dev.nooir.bridge.XtcFormat
import android.graphics.Bitmap
import android.graphics.Color
import java.io.ByteArrayOutputStream
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.source.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import java.io.File

/** Export chapters already downloaded by Mihon, using its own storage abstraction.
 *  No Suwayomi server, storage permission, or access to another app's private data.
 */
object MihonNooirExporter {
    private val imageTypes = setOf("png", "jpg", "jpeg", "webp", "bmp", "gif")
    private fun isImage(name: String?) = name?.substringAfterLast('.', "")?.lowercase() in imageTypes
    private fun naturalKey(name: String?): List<String> = Regex("(\\d+|\\D+)")
        .findAll(name.orEmpty()).map { it.value }.toList()
    private val naturalComparator = Comparator<String?> { a, b ->
        val ka = naturalKey(a); val kb = naturalKey(b)
        for (i in 0 until minOf(ka.size,kb.size)) {
            val x = ka[i]; val y = kb[i]
            val numeric = x.all { it.isDigit() } && y.all { it.isDigit() }
            val comparison = if (numeric) {
                x.trimStart('0').length.compareTo(y.trimStart('0').length)
                    .takeIf { it != 0 } ?: x.trimStart('0').compareTo(y.trimStart('0'))
            } else x.compareTo(y,ignoreCase=true)
            if(comparison != 0) return@Comparator comparison
        }
        ka.size.compareTo(kb.size)
    }

    data class ExportResult(val pageCount: Int, val fileBytes: Long, val format: ExportFormat)

    suspend fun export(
        context: Context, provider: DownloadProvider, manga: Manga, source: Source,
        chapters: List<Chapter>, destination: Uri, format: ExportFormat,
        device: String = "X4",
    ): ExportResult = renderExport(context, provider, manga, source, chapters, destination, null, null, format, device)

    suspend fun sendToDevice(
        context: Context, provider: DownloadProvider, manga: Manga, source: Source,
        chapters: List<Chapter>, deviceHost: String, destinationFolder: String,
        format: ExportFormat, device: String = "X4",
    ): ExportResult {
        // Check the Nooir transfer server before spending time processing images.
        NooirUpload.checkConnected(deviceHost)
        return renderExport(context, provider, manga, source, chapters,
            null, deviceHost, destinationFolder, format, device)
    }

    private suspend fun renderExport(
        context: Context, provider: DownloadProvider, manga: Manga, source: Source,
        chapters: List<Chapter>, destination: Uri?, deviceHost: String?,
        destinationFolder: String?, format: ExportFormat, device: String,
    ): ExportResult = withContext(Dispatchers.IO) {
        require(chapters.isNotEmpty()) { "Select at least one downloaded chapter" }
        val resolved = chapters.sortedWith(compareBy<Chapter> { it.chapterNumber }.thenBy { it.id })
            .map { chapter ->
                val location = provider.findChapterDir(chapter.name, chapter.scanlator, chapter.url, manga.title, source)
                    ?: error("Chapter is not downloaded: ${chapter.name}")
                val uris: List<Uri> = if (location.isFile) {
                    require(location.name?.endsWith(".cbz", ignoreCase = true) == true) {
                        "Unsupported chapter archive: ${location.name}"
                    }
                    listOf(location.uri)
                } else {
                    location.listFiles().orEmpty().filter { it.isFile && isImage(it.name) }
                        .sortedWith { a, b -> naturalComparator.compare(a.name, b.name) }
                        .map { it.uri }
                }
                require(uris.isNotEmpty()) { "No readable image pages in ${chapter.name}" }
                chapter.name to uris
            }

        val actual = format.resolved(device)
        val (width, height) = when (device) {
            "X3" -> 528 to 792
            else -> 480 to 800
        }
        val meta = ExtendedMetadata(
            title = manga.title, author = manga.author.orEmpty(), artist = manga.artist.orEmpty(),
            description = manga.description.orEmpty(), genres = manga.genre.orEmpty(),
            language = source.lang.ifBlank { "und" },
            series = manga.title, identifier = "mihon:${manga.source}:${manga.id}",
            sourceUrl = manga.url.takeIf { it.startsWith("https://") || it.startsWith("http://") }.orEmpty(),
        )
        val sourcePages = AndroidPageSource(context)
        val tempDir = File(context.cacheDir, "manganooir-transfer-" + UUID.randomUUID()).apply {
            check(mkdirs()) { "Could not create temp export folder" }
        }
        val safeTitle = manga.title.take(70).map { c ->
            if (c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '-' || c == '_') c else '_'
        }.joinToString("").ifBlank { "Manga" }
        val tmp = File(tempDir, safeTitle + "." + actual.extension)
        try {
            val resultCount: Int
            tmp.outputStream().use { stream ->
                if (actual == ExportFormat.XTC || actual == ExportFormat.XTCH) {
                    val xformat = if (actual == ExportFormat.XTC) XtcFormat.XTC else XtcFormat.XTCH
                    val sections = mutableListOf<BookChapter>()
                    var count = 0
                    val pages = sequence {
                        for ((chapterName, files) in resolved) {
                            val start = count
                            for (page in sourcePages.pages(files, width, height, xformat.depth)) {
                                yield(page)
                                count++
                            }
                            if (count > start) sections += BookChapter(chapterName, start, count - 1)
                        }
                    }
                    resultCount = XtcCodec.write(xformat, pages, stream, context.cacheDir,
                        metadata = meta.xtc(), direction = meta.direction,
                        chapterResolver = { pageCount ->
                            check(pageCount == count) { "Chapter page counts became inconsistent" }
                            sections.toList()
                        }
                    ).pageCount
                } else {
                    val archivePages = sequence {
                        for ((chapterName, files) in resolved) {
                            for (page in sourcePages.pages(files, width, height, 2)) {
                                yield(ArchivePage(chapterName, png(page), page.width, page.height))
                            }
                        }
                    }
                    resultCount = BookArchiveWriter.write(actual, archivePages, stream, meta)
                }
            }
            if (destination != null) {
                context.contentResolver.openOutputStream(destination, "w")?.use { output ->
                    tmp.inputStream().use { it.copyTo(output, 64 * 1024) }
                } ?: error("Could not open selected output location")
            } else {
                NooirUpload.upload(tmp, requireNotNull(deviceHost), requireNotNull(destinationFolder))
            }
            ExportResult(resultCount, tmp.length(), actual)
        } finally {
            tmp.delete()
            tempDir.delete()
        }
    }

    private fun png(page: dev.nooir.bridge.QuantizedPage): ByteArray {
        val pixels = IntArray(page.levels.size) { i ->
            val level = page.levels[i].toInt() and 255
            val gray = if (page.depth == 1) level * 255 else level * 85
            Color.rgb(gray, gray, gray)
        }
        val bitmap = Bitmap.createBitmap(pixels, page.width, page.height, Bitmap.Config.ARGB_8888)
        return try {
            ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Could not encode PNG" }
                output.toByteArray()
            }
        } finally { bitmap.recycle() }
    }
}