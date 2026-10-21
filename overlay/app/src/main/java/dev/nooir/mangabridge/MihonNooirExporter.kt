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
import dev.nooir.bridge.NooirDeviceProfiles
import eu.kanade.tachiyomi.data.cache.CoverCache
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
        coverCache: CoverCache? = null,
    ): ExportResult = renderExport(context, provider, manga, source, chapters, destination, null, null, format, device, quickExport, options, onStage, onUploadProgress, coverCache)

    suspend fun sendToDevice(
        context: Context, provider: DownloadProvider, manga: Manga, source: Source,
        chapters: List<Chapter>, deviceHost: String, destinationFolder: String,
        format: ExportFormat, device: String = "X4", quickExport: Boolean = false,
        options: MangaConversionOptions = MangaConversionOptions(),
        onStage: (String, Int, Int) -> Unit = { _, _, _ -> },
        onUploadProgress: (Long, Long) -> Unit = { _, _ -> },
        coverCache: CoverCache? = null,
        outputName: String = "",
    ): ExportResult {
        require(NooirDeviceProfiles.forId(device).xteinkWifi) {
            "This preset does not support the XTEink Wi-Fi transfer API. Save the export to your phone."
        }
        // Check the Nooir transfer server before spending time processing images.
        withContext(Dispatchers.IO) { NooirUpload.checkConnected(deviceHost) }
        return renderExport(context, provider, manga, source, chapters,
            null, deviceHost, destinationFolder, format, device, quickExport, options, onStage, onUploadProgress, coverCache, outputName)
    }

    /** Stream one source chapter at a time; only the active chapter is temporarily cached. */
    private suspend fun renderExport(
        context: Context, provider: DownloadProvider, manga: Manga, source: Source,
        chapters: List<Chapter>, destination: Uri?, deviceHost: String?,
        destinationFolder: String?, format: ExportFormat, device: String,
        quickExport: Boolean, options: MangaConversionOptions,
        onStage: (String, Int, Int) -> Unit,
        onUploadProgress: (Long, Long) -> Unit,
        coverCache: CoverCache?,
        outputName: String = "",
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
        val profile = NooirDeviceProfiles.forId(device)
        val (width, height) = profile.width to profile.height
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
        // Folio Nooir uses the *first XTC page* to generate its shelf thumbnail.
        // Use the actual Mihon series artwork if we can obtain it; otherwise leave
        // original page ordering intact rather than fabricating a cover.
        val coverUri = if (options.includeCover) NooirMangaCover.resolve(context, manga, source, coverCache, tempFolder) else null
        val coverOptions = options.copy(
            layout = "PAGED", cropWhiteMargins = false, splitSpreads = false,
            orientation = "PORTRAIT", overlapPercent = 0,
        )
        fun coverPage(depth: Int, pageOptions: MangaConversionOptions = coverOptions): dev.nooir.bridge.QuantizedPage? = coverUri?.let { uri ->
            pageSource.pages(listOf(uri), width, height, depth, pageOptions).singleOrNull()
                ?: error("Mihon cover image produced no XTC/XTCH page")
        }
        val safeTitle = manga.title.take(70).map { c ->
            if (c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '-' || c == '_') c else '_'
        }.joinToString("").ifBlank { "Manga" }
        val tmp = File(tempFolder, safeTitle + "." + actual.extension)
        val renamed = if (outputName.isBlank()) tmp.name else
            NooirDestinationGuard.forManga(outputName, format, device)

        fun chapterFrames(
            depth: Int, archiveLandscape: Boolean = false,
        ): Sequence<Pair<Chapter, dev.nooir.bridge.QuantizedPage>> = sequence {
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
                    // EPUB/CBZ can carry genuinely sideways 800x480 images.
                    // XTC/XTCH must retain each target profile's portrait panel dimensions.
                    val frameWidth = if (archiveLandscape) height else width
                    val frameHeight = if (archiveLandscape) width else height
                    for (page in pageSource.pages(uris, frameWidth, frameHeight, depth,
                        if (actual == ExportFormat.XTC || actual == ExportFormat.XTCH) options.copy(epubColor = false) else options)) {
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
                    onStage("Preparing actual book cover", 0, ordered.size)
                    val cover = if (options.includeCover) requireNotNull(coverPage(xtcFormat.depth, coverOptions.copy(epubColor = false))) {
                        "Series cover unavailable. Open this manga in Mihon to refresh its cover, or disable Include separate series cover."
                    } else null
                    val stream = sequence {
                        if (cover != null) { yield(cover); total++ }
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
                        metadata = meta.xtc().copy(coverPage = if (cover != null) 0 else null), direction = meta.direction,
                        chapterResolver = { written ->
                            check(total == written) { "Chapter navigation mismatch" }
                            sections.toList()
                        },
                    ).pageCount
                } else {
                    val cover = if (options.includeCover) coverPage(2, coverOptions.copy(epubColor = options.epubColor)) else null
                    val stream = sequence {
                        if (cover != null) yield(ArchivePage("Cover", archivePng(cover), cover.width, cover.height, isCover = true))
                        for ((chapter, page) in chapterFrames(2, options.orientation == "LANDSCAPE")) {
                            yield(ArchivePage(chapter.name, archivePng(page), page.width, page.height))
                        }
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
                    requireNotNull(destinationFolder), onUploadProgress, filenameOverride = renamed)
                if (actual == ExportFormat.XTC || actual == ExportFormat.XTCH) {
                    onStage("Syncing synopsis", locations.size, locations.size)
                    val synced = runCatching {
                        NooirUpload.updateBookInfo(deviceHost, destinationFolder, renamed,
                            meta.title, meta.author, meta.description)
                    }.getOrDefault(false)
                    if (!synced) onStage("Book sent; synopsis sync unavailable",
                        locations.size, locations.size)
                }
            }
            ExportResult(pageCount, tmp.length(), actual)
        } finally {
            tempFolder.deleteRecursively()
        }
    }

    /** Emit a compact 8-bit grayscale PNG, not Android's ARGB/RGBA PNG.
     * MangaPoint encodes e-reader page images as low-bit-depth PNG; grayscale
     * avoids an unnecessary alpha channel and reduces decoder RAM pressure.
     */
    private fun archivePng(page: dev.nooir.bridge.QuantizedPage): ByteArray = page.colorPng ?: png(page)

    private fun png(page: dev.nooir.bridge.QuantizedPage): ByteArray {
        val width = page.width
        val height = page.height
        val scanlines = ByteArray((width + 1) * height)
        for (y in 0 until height) {
            val row = y * (width + 1)
            scanlines[row] = 0 // PNG filter None
            for (x in 0 until width) {
                val level = page.levels[y * width + x].toInt() and 255
                require(level <= if (page.depth == 1) 1 else 3) { "Invalid grayscale pixel level" }
                scanlines[row + 1 + x] = (if (page.depth == 1) level * 255 else level * 85).toByte()
            }
        }
        val compressed = ByteArrayOutputStream()
        java.util.zip.DeflaterOutputStream(compressed).use { it.write(scanlines) }
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))
        fun chunk(name: String, data: ByteArray) {
            val type = name.toByteArray(Charsets.US_ASCII)
            fun writeInt(v: Int) {
                out.write((v ushr 24) and 255)
                out.write((v ushr 16) and 255)
                out.write((v ushr 8) and 255)
                out.write(v and 255)
            }
            writeInt(data.size)
            out.write(type)
            out.write(data)
            val crc = java.util.zip.CRC32()
            crc.update(type)
            crc.update(data)
            writeInt(crc.value.toInt())
        }
        val ihdr = ByteArray(13)
        for (i in 0..3) {
            ihdr[i] = (width ushr (24 - i * 8)).toByte()
            ihdr[4 + i] = (height ushr (24 - i * 8)).toByte()
        }
        ihdr[8] = 8 // 8 bits per sample
        ihdr[9] = 0 // grayscale, no alpha
        chunk("IHDR", ihdr)
        chunk("IDAT", compressed.toByteArray())
        chunk("IEND", byteArrayOf())
        return out.toByteArray()
    }

}