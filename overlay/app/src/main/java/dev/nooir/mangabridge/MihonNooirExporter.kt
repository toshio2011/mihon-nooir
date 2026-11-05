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

    data class ExportResult(
        val pageCount: Int, val fileBytes: Long, val format: ExportFormat,
        val skippedChapters: List<String> = emptyList(), val missingCover: Boolean = false,
        val diagnosticsMessage: String = "",
    )

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
        var locations = ordered.map { chapter ->
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
        val coverUri = if (options.includeCover) {
            onStage("Finding or refreshing series cover", 0, ordered.size)
            NooirMangaCover.resolve(context, manga, source, coverCache, tempFolder)
        } else null
        val coverOptions = options.copy(
            panelMode = "OFF", layout = "PAGED", cropWhiteMargins = false, splitSpreads = false,
            orientation = "PORTRAIT", overlapPercent = 0,
        )
        fun coverPage(depth: Int, pageOptions: MangaConversionOptions = coverOptions): dev.nooir.bridge.QuantizedPage? = coverUri?.let { uri ->
            pageSource.pages(listOf(uri), width, height, depth, pageOptions).singleOrNull()
                ?: error("Mihon cover image produced no XTC/XTCH page")
        }
        val safeTitle = manga.title.take(70).map { c ->
            if (c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '-' || c == '_') c else '_'
        }.joinToString("").ifBlank { "Manga" }
        // Diagnostic capture is opt-in and never changes exported book content.
        // Open only AFTER preflight so failed or paused jobs do not publish
        // incomplete diagnostics over the last successful ZIP.
        var diagnostics: PanelDiagnosticArchive? = null
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
                        if (actual == ExportFormat.XTC || actual == ExportFormat.XTCH) options.copy(epubColor = false) else options,
                        fixedFramebuffer = (actual == ExportFormat.XTC || actual == ExportFormat.XTCH),
                        diagnostics = diagnostics, chapterOrdinal = chapterIndex + 1)) {
                        ownerJob.ensureActive()
                        yield(chapter to page)
                    }
                } finally {
                    remoteCache?.deleteRecursively()
                }
            }
        }

        try {
            // Preflight is deliberately before tmp.outputStream(): no partial book is created.
            // A missing/invalid cover requires an explicit decision, regardless of output format.
            onStage("Checking cover and chapter access", 0, locations.size)
            val preparedCover = if (options.includeCover) {
                try {
                    coverPage(if (actual == ExportFormat.XTC) 1 else 2,
                        coverOptions.copy(epubColor = actual != ExportFormat.XTC &&
                            actual != ExportFormat.XTCH && options.epubColor))
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
            } else null
            val missingCover = options.includeCover && preparedCover == null
            if (missingCover && !options.allowMissingCover) {
                throw NooirExportPreflightException(NooirExportPreflightException.COVER,
                    "Series cover unavailable after checking cache, fetching the thumbnail and refreshing manga details from the source. Continue without a separate cover?")
            }

            val unavailable = mutableListOf<Pair<Chapter, String>>()
            val retryable = mutableListOf<Pair<Chapter, String>>()
            for ((chapter, downloaded) in locations) {
                ownerJob.ensureActive()
                if (downloaded != null) {
                    val readable = if (downloaded.isFile) {
                        downloaded.name?.endsWith(".cbz", ignoreCase = true) == true &&
                            downloaded.length() > 0L
                    } else {
                        downloaded.listFiles().orEmpty().any { it.isFile && isImage(it.name) }
                    }
                    if (!readable) unavailable += chapter to "Downloaded chapter has no readable pages"
                } else if (!quickExport || source !is HttpSource) {
                    unavailable += chapter to "Chapter not downloaded"
                } else {
                    // Quick Export: probe the source and its first image to catch
                    // paywalls/HTTP 403 before building any book. Remaining pages
                    // are still validated during streaming, never silently skipped.
                    try {
                        val pages = source.getPageList(chapter.toSChapter())
                        if (pages.isEmpty()) {
                            unavailable += chapter to "Source returned no pages"
                        } else if (pages.size > 1000) {
                            retryable += chapter to "Chapter exceeds 1000 pages"
                        } else {
                            val first = pages.first()
                            if (first.imageUrl.isNullOrBlank()) first.imageUrl = source.getImageUrl(first)
                            source.getImage(first).use { response ->
                                if (!response.isSuccessful) {
                                    val reason = "HTTP " + response.code
                                    if (NooirChapterAccess.classify(response.code) ==
                                        NooirChapterAccess.Issue.UNAVAILABLE) {
                                        unavailable += chapter to reason
                                    } else retryable += chapter to reason
                                } else if (response.body.byteStream().read() < 0) {
                                    retryable += chapter to "First page was empty"
                                }
                            }
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        val reason = e.message.orEmpty().take(100).ifBlank { "Source request failed" }
                        if (NooirChapterAccess.classify(message = reason) ==
                            NooirChapterAccess.Issue.UNAVAILABLE) {
                            unavailable += chapter to reason
                        } else retryable += chapter to reason
                    }
                }
            }
            fun chapterDetails(items: List<Pair<Chapter, String>>): String =
                items.take(8).joinToString("; ") { it.first.name + " (" + it.second + ")" } +
                    if (items.size > 8) "; and " + (items.size - 8) + " more" else ""
            if (retryable.isNotEmpty()) {
                throw NooirExportPreflightException(NooirExportPreflightException.RETRY,
                    "Could not verify " + retryable.size + " chapter(s): " +
                        chapterDetails(retryable) + ". Retry when the source is available.")
            }
            if (unavailable.isNotEmpty() && !options.skipUnavailableChapters) {
                throw NooirExportPreflightException(NooirExportPreflightException.CHAPTERS,
                    unavailable.size.toString() + " locked, missing or unavailable chapter(s): " +
                        chapterDetails(unavailable) + ". Skip these chapters or cancel?")
            }
            val skippedNames = unavailable.map { it.first.name }
            if (unavailable.isNotEmpty()) {
                val skippedIds = unavailable.map { it.first.id }.toSet()
                locations = locations.filterNot { it.first.id in skippedIds }
                require(locations.isNotEmpty()) { "All selected chapters are unavailable; nothing to export" }
            }

            if (options.panelDiagnostics && options.panelMode == "PANEL_BY_PANEL") {
                diagnostics = runCatching { PanelDiagnosticArchive(tempFolder) }.getOrNull()
                diagnostics?.setHasSeparateCover(preparedCover != null)
            }
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
                    val cover = preparedCover
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
                        extendedMetadataJson = if (options.xtcExtendedMetadata) BookArchiveWriter.jsonMetadata(meta) else null,
                        chapterResolver = { written ->
                            check(total == written) { "Chapter navigation mismatch" }
                            sections.toList()
                        },
                    ).pageCount
                } else {
                    val cover = preparedCover
                    val stream = sequence {
                        if (cover != null) yield(ArchivePage("Cover", archivePng(cover), cover.width, cover.height, isCover = true))
                        for ((chapter, page) in chapterFrames(2, options.orientation == "LANDSCAPE")) {
                            yield(ArchivePage(chapter.name, archivePng(page), page.width, page.height))
                        }
                    }
                    pageCount = BookArchiveWriter.write(actual, stream, output, meta, options.epubPagesPerXhtml)
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
            val debugStatus = if (options.panelDiagnostics && options.panelMode == "PANEL_BY_PANEL") {
                onStage("Saving private panel diagnostic ZIP", locations.size, locations.size)
                diagnostics?.publish(context) ?: "Diagnostic ZIP could not be initialized"
            } else ""
            ExportResult(pageCount, tmp.length(), actual, skippedNames, missingCover, debugStatus)
        } finally {
            // Closing diagnostics on unsuccessful exports never publishes them.
            runCatching { diagnostics?.close() }
            tempFolder.deleteRecursively()
        }
    }

    /** Emit a compact 8-bit grayscale PNG, not Android's ARGB/RGBA PNG.
     * MangaPoint encodes e-reader page images as low-bit-depth PNG; grayscale
     * avoids an unnecessary alpha channel and reduces decoder RAM pressure.
     */
    private fun archivePng(page: dev.nooir.bridge.QuantizedPage): ByteArray = page.colorPng ?: png(page)

    private fun png(page: dev.nooir.bridge.QuantizedPage): ByteArray =
        dev.nooir.bridge.GrayscalePng.encode(page)

}