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
import eu.kanade.domain.chapter.model.toSChapter
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import java.io.File
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

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
        device: String = "X4", quickExport: Boolean = false,
        options: MangaConversionOptions = MangaConversionOptions(),
        onStage: (String, Int, Int) -> Unit = { _, _, _ -> },
        onUploadProgress: (Long, Long) -> Unit = { _, _ -> },
    ): ExportResult = renderExport(context, provider, manga, source, chapters, destination, null, null, format, device, quickExport, options, onStage, onUploadProgress)

    suspend fun sendToDevice(
        context: Context, provider: DownloadProvider, manga: Manga, source: Source,
        chapters: List<Chapter>, deviceHost: String, destinationFolder: String,
        format: ExportFormat, device: String = "X4", quickExport: Boolean = false,
        options: MangaConversionOptions = MangaConversionOptions(),
        onStage: (String, Int, Int) -> Unit = { _, _, _ -> },
        onUploadProgress: (Long, Long) -> Unit = { _, _ -> },
    ): ExportResult {
        // Check the Nooir transfer server before spending time processing images.
        withContext(Dispatchers.IO) { NooirUpload.checkConnected(deviceHost) }
        return renderExport(context, provider, manga, source, chapters,
            null, deviceHost, destinationFolder, format, device, quickExport, options, onStage, onUploadProgress)
    }

    /** Stream one source chapter at a time; only the active chapter is temporarily cached. */
    private suspend fun renderExport(
        context: Context, provider: DownloadProvider, manga: Manga, source: Source,
        chapters: List<Chapter>, destination: Uri?, deviceHost: String?,
        destinationFolder: String?, format: ExportFormat, device: String,
        quickExport: Boolean, options: MangaConversionOptions,
        onStage: (String, Int, Int) -> Unit,
        onUploadProgress: (Long, Long) -> Unit,
    ): ExportResult = withContext(Dispatchers.IO) {
        require(chapters.isNotEmpty()) { "Select at least one chapter" }
        val ownerJob = coroutineContext[kotlinx.coroutines.Job]
            ?: error("Quick Export requires a coroutine Job")
        val ordered = chapters.sortedWith(compareBy<Chapter> { it.chapterNumber }.thenBy { it.id })
        // Chapter storage lookups are suspending: do these before entering SequenceScope.
        val locations = ordered.map { chapter ->
            chapter to provider.findChapterDir(
                chapter.name, chapter.scanlator, chapter.url, manga.title, source,
            )
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
            direction = if (options.rightToLeft) dev.nooir.bridge.ReadingDirection.RIGHT_TO_LEFT else dev.nooir.bridge.ReadingDirection.LEFT_TO_RIGHT,
            series = manga.title, identifier = "mihon:" + manga.source + ":" + manga.id,
            sourceUrl = manga.url.takeIf { it.startsWith("https://") || it.startsWith("http://") }.orEmpty(),
        )
        val pageSource = AndroidPageSource(context)
        val tempFolder = File(context.cacheDir, "manganooir-transfer-" + UUID.randomUUID()).apply {
            check(mkdirs()) { "Cannot create conversion cache" }
        }
        val safeTitle = manga.title.take(70).map { c ->
            if (c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '-' || c == '_') c else '_'
        }.joinToString("").ifBlank { "Manga" }
        val tmp = File(tempFolder, safeTitle + "." + actual.extension)

        fun chapterFrames(depth: Int): Sequence<Pair<Chapter, dev.nooir.bridge.QuantizedPage>> = sequence {
            for ((chapterIndex, entry) in locations.withIndex()) {
                val (chapter, downloaded) = entry
                ownerJob.ensureActive()
                onStage("Converting chapter " + (chapterIndex + 1) + "/" + locations.size,
                    chapterIndex + 1, locations.size)
                val remoteCache = if (downloaded == null && quickExport) {
                    File(tempFolder, "chapter-" + chapter.id).apply { check(mkdirs()) }
                } else null
                try {
                    val uris: List<Uri> = if (downloaded != null) {
                        if (downloaded.isFile) {
                            require(downloaded.name?.endsWith(".cbz", ignoreCase = true) == true) {
                                "Unsupported chapter archive: " + downloaded.name
                            }
                            listOf(downloaded.uri)
                        } else {
                            downloaded.listFiles().orEmpty().filter { it.isFile && isImage(it.name) }
                                .sortedWith { a, b -> naturalComparator.compare(a.name, b.name) }
                                .map { it.uri }
                        }
                    } else {
                        val online = source as? HttpSource
                            ?: error("Online source is required for non-downloaded chapters")
                        check(quickExport) { "Chapter not downloaded: " + chapter.name }
                        // Suspend APIs are called within the synchronous image writer's iteration.
                        // Inherit the parent job, so cancellation of the export aborts network requests.
                        kotlinx.coroutines.runBlocking(ownerJob + Dispatchers.IO) {
                            val pages = online.getPageList(chapter.toSChapter())
                            require(pages.isNotEmpty()) { "No images found for " + chapter.name }
                            require(pages.size <= 1000) { "Chapter has too many images" }
                            var chapterBytes = 0L
                            pages.mapIndexed { index, page ->
                                ownerJob.ensureActive()
                                if (page.imageUrl.isNullOrBlank()) page.imageUrl = online.getImageUrl(page)
                                val output = File(requireNotNull(remoteCache), "%05d.img".format(index))
                                online.getImage(page).use { response ->
                                    check(response.isSuccessful) {
                                        "Chapter image HTTP " + response.code + " failed: " + chapter.name
                                    }
                                    response.body.byteStream().use { input ->
                                        output.outputStream().use { target ->
                                            val buffer = ByteArray(32 * 1024)
                                            var imageBytes = 0L
                                            while (true) {
                                                ownerJob.ensureActive()
                                                val n = input.read(buffer)
                                                if (n < 0) break
                                                imageBytes += n
                                                chapterBytes += n
                                                require(imageBytes <= 25L * 1024 * 1024) {
                                                    "Image exceeds 25 MB"
                                                }
                                                require(chapterBytes <= 400L * 1024 * 1024) {
                                                    "Chapter exceeds 400 MB of temporary images"
                                                }
                                                target.write(buffer, 0, n)
                                            }
                                        }
                                    }
                                }
                                Uri.fromFile(output)
                            }
                        }
                    }
                    require(uris.isNotEmpty()) { "No readable images in " + chapter.name }
                    for (page in pageSource.pages(uris, width, height, depth, options)) {
                        ownerJob.ensureActive()
                        yield(chapter to page)
                    }
                } finally {
                    remoteCache?.deleteRecursively()
                }
            }
        }

        try {
            val pageCount: Int
            tmp.outputStream().use { output ->
                if (actual == ExportFormat.XTC || actual == ExportFormat.XTCH) {
                    val xtcFormat = if (actual == ExportFormat.XTC) XtcFormat.XTC else XtcFormat.XTCH
                    val sections = mutableListOf<BookChapter>()
                    var total = 0
                    var activeId: Long? = null
                    var activeName = ""
                    var sectionStart = 0
                    val stream = sequence {
                        for ((chapter, page) in chapterFrames(xtcFormat.depth)) {
                            if (activeId != chapter.id) {
                                if (activeId != null && total > sectionStart) {
                                    sections += BookChapter(activeName, sectionStart, total - 1)
                                }
                                sectionStart = total
                                activeId = chapter.id
                                activeName = chapter.name
                            }
                            yield(page)
                            total++
                        }
                        if (activeId != null && total > sectionStart) {
                            sections += BookChapter(activeName, sectionStart, total - 1)
                        }
                    }
                    pageCount = XtcCodec.write(
                        xtcFormat, stream, output, context.cacheDir,
                        metadata = meta.xtc(), direction = meta.direction,
                        chapterResolver = { written ->
                            check(total == written) { "Chapter navigation mismatch" }
                            sections.toList()
                        },
                    ).pageCount
                } else {
                    val stream = chapterFrames(2).map { (chapter, page) ->
                        ArchivePage(chapter.name, png(page), page.width, page.height)
                    }
                    pageCount = BookArchiveWriter.write(actual, stream, output, meta)
                }
            }
            if (destination != null) {
                context.contentResolver.openOutputStream(destination, "w")?.use { output ->
                    tmp.inputStream().use { it.copyTo(output, 64 * 1024) }
                } ?: error("Cannot open selected output")
            } else {
                require(tmp.length() <= 180L * 1024 * 1024) {
                    "Output is over 180 MB: for XTEink stability, select fewer chapters or export smaller files."
                }
                onStage("Uploading to XTEink", locations.size, locations.size)
                NooirUpload.upload(tmp, requireNotNull(deviceHost),
                    requireNotNull(destinationFolder), onUploadProgress)
            }
            ExportResult(pageCount, tmp.length(), actual)
        } finally {
            tempFolder.deleteRecursively()
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