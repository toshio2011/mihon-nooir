package dev.nooir.desktop.suwayomi

import dev.nooir.bridge.NaturalPageOrder
import dev.nooir.desktop.NooirDesktopCli
import dev.nooir.mangabridge.MangaConversionOptions
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import javax.imageio.ImageIO

/**
 * D0 integration: existing Suwayomi localhost server -> downloaded chapter CBZ
 * -> safe temporary chapter folders -> the exact shared MangaNooir export CLI.
 *
 * No extensions, manga files or third-party backend binaries are bundled.
 */
object SuwayomiToNooir {
    private const val MAX_CHAPTER_PAGES = 1_000
    private const val MAX_IMAGE_BYTES = 25L * 1024 * 1024
    private const val MAX_CHAPTER_BYTES = 400L * 1024 * 1024
    private val imageExt = setOf("png", "jpg", "jpeg", "bmp", "gif", "webp")

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size % 2 == 0) { "Use --mode sources|library|popular|latest|search|list|export with the appropriate IDs" }
        val flags = args.toList().chunked(2).associate { it[0] to it[1] }
        require(flags.keys.all { it in setOf(
            "--mode", "--base-url", "--manga-id", "--chapters", "--output", "--format",
            "--device", "--title", "--options-token", "--download-missing", "--wait-seconds",
            "--source-id", "--page", "--query", "--limit", "--offset",
        ) }) { "Unknown Suwayomi CLI option" }
        val client = SuwayomiClient(
            flags["--base-url"] ?: "http://127.0.0.1:4567",
            System.getenv("NOOIR_SUWAYOMI_AUTHORIZATION"),
        )
        val mode = flags["--mode"] ?: "sources"
        val catalog = SuwayomiCatalog(client)
        val mangaId = if (mode in setOf("list", "export")) {
            flags["--manga-id"]?.toIntOrNull()?.takeIf { it > 0 } ?: error("Missing positive --manga-id")
        } else null
        val manga = mangaId?.let { client.manga(it) }
        when (mode) {
            "sources" -> {
                val limit = (flags["--limit"] ?: "100").toIntOrNull() ?: error("Invalid --limit")
                val offset = (flags["--offset"] ?: "0").toIntOrNull() ?: error("Invalid --offset")
                val result = catalog.sources(limit, offset)
                for (source in result.items) {
                    println("${source.id}\\t${source.language}\\t${source.name}\\tlatest=${source.supportsLatest}")
                }
                println("Next page: ${result.hasNextPage}")
            }
            "library" -> {
                val limit = (flags["--limit"] ?: "100").toIntOrNull() ?: error("Invalid --limit")
                val offset = (flags["--offset"] ?: "0").toIntOrNull() ?: error("Invalid --offset")
                val result = catalog.library(limit, offset)
                printManga(result)
            }
            "popular", "latest", "search" -> {
                val sourceId = flags["--source-id"]?.toLongOrNull()?.takeIf { it > 0 }
                    ?: error("Missing positive --source-id (use --mode sources)")
                val page = (flags["--page"] ?: "1").toIntOrNull() ?: error("Invalid --page")
                val browseMode = when (mode) {
                    "popular" -> SuwayomiCatalog.BrowseMode.POPULAR
                    "latest" -> SuwayomiCatalog.BrowseMode.LATEST
                    else -> SuwayomiCatalog.BrowseMode.SEARCH
                }
                printManga(catalog.browse(sourceId, browseMode, page, flags["--query"] ?: ""))
            }
            "list" -> {
                val book = requireNotNull(manga)
                println("${book.title} (Suwayomi manga ID ${book.id})")
                for (c in book.chapters.sortedWith(chapterOrder)) {
                    println("${c.id}\\t${if (c.downloaded) "downloaded" else "not downloaded"}\\t${c.name}")
                }
            }
            "export" -> {
                val ids = flags["--chapters"]?.split(",")?.map {
                    it.trim().toIntOrNull()?.takeIf { id -> id > 0 } ?: error("Invalid chapter ID")
                } ?: error("Explicit --chapters ID,ID is required (use --mode list first)")
                require(ids.isNotEmpty() && ids.size <= 500 && ids.distinct().size == ids.size) {
                    "Choose 1 to 500 distinct chapter IDs"
                }
                val selected = ids.map { id ->
                    requireNotNull(manga).chapters.find { it.id == id } ?: error("Chapter $id does not belong to manga $mangaId")
                }.sortedWith(chapterOrder)
                val missing = selected.filterNot { it.downloaded }
                val downloadMissing = when (flags["--download-missing"] ?: "false") {
                    "true" -> true
                    "false" -> false
                    else -> error("--download-missing must be true or false")
                }
                if (missing.isNotEmpty()) {
                    require(downloadMissing) {
                        "Chapters not downloaded: ${missing.joinToString { it.id.toString() }}. " +
                            "Download in Suwayomi first, or explicitly set --download-missing true."
                    }
                    client.enqueue(missing.map { it.id })
                    val waitSeconds = (flags["--wait-seconds"] ?: "180").toIntOrNull()
                        ?.takeIf { it in 0..600 } ?: error("--wait-seconds must be 0..600")
                    val deadline = System.nanoTime() + waitSeconds * 1_000_000_000L
                    while (true) {
                        val fresh = client.manga(requireNotNull(mangaId)).chapters
                        val pending = selected.filter { wanted ->
                            fresh.none { it.id == wanted.id && it.downloaded }
                        }
                        if (pending.isEmpty()) break
                        check(System.nanoTime() < deadline) {
                            "Timed out waiting for Suwayomi downloads: ${pending.joinToString { it.id.toString() }}"
                        }
                        Thread.sleep(1000)
                    }
                }
                val output = File(flags["--output"] ?: error("Missing --output")).canonicalFile
                require(output.parentFile.isDirectory && !output.exists()) { "Output directory missing or file already exists" }
                val format = flags["--format"] ?: "EPUB2"
                val device = flags["--device"] ?: "X4"
                val opts = MangaConversionOptions.fromToken(flags["--options-token"])
                val work = Files.createTempDirectory(output.parentFile.toPath(), ".nooir-suwayomi-").toFile()
                try {
                    if (opts.includeCover) {
                        val cover = runCatching { client.thumbnail(requireNotNull(mangaId)) }.getOrNull()
                        val img = cover?.inputStream()?.use { ImageIO.read(it) }
                        if (img == null) {
                            require(opts.allowMissingCover) {
                                "Series cover unavailable. Enable 'allow missing cover' in the Android options token or disable cover."
                            }
                            System.err.println("Warning: no valid cover; exporting without a separate cover")
                        } else {
                            require(img.width > 0 && img.height > 0 &&
                                img.width.toLong() * img.height <= 25_000_000L) { "Cover dimensions too large" }
                            check(ImageIO.write(img, "png", File(work, "cover.png"))) { "Cannot encode cover" }
                        }
                    }
                    for ((index, chapter) in selected.withIndex()) {
                        val safe = chapter.name.replace(Regex("[^\\p{L}\\p{N} ._()-]"), "_")
                            .trim().take(64).ifBlank { "Chapter-${chapter.id}" }
                        val folder = File(work, "%04d__%s".format(index + 1, safe))
                        check(folder.mkdir()) { "Cannot create chapter folder" }
                        val cbz = File(work, "download-${chapter.id}.cbz")
                        try {
                            client.downloadCbz(chapter.id, cbz)
                            val count = extractChapter(cbz, folder)
                            println("Imported ${chapter.name}: $count pages")
                        } finally {
                            cbz.delete()
                        }
                    }
                    val forwarded = arrayOf(
                        "--input", work.absolutePath,
                        "--output", output.absolutePath,
                        "--format", format,
                        "--device", device,
                        "--title", flags["--title"] ?: requireNotNull(manga).title,
                        "--options-token", opts.token(),
                    )
                    NooirDesktopCli.main(forwarded)
                } finally {
                    work.deleteRecursively()
                }
            }
            else -> error("--mode must be list or export")
        }
    }

    private fun printManga(page: SuwayomiPage<SuwayomiMangaCard>) {
        for (book in page.items) println("${book.id}\\t${book.title}\\t${book.author}\\tsource=${book.sourceId}")
        println("Next page: ${page.hasNextPage}")
    }

    private val chapterOrder = compareBy<SuwayomiChapter> {
        if (it.chapterNumber >= 0) it.chapterNumber else Double.MAX_VALUE
    }.thenByDescending { it.sourceOrder }.thenBy { it.id }

    /** Never trust ZIP paths: normalize page ordering, extract only images to generated filenames. */
    internal fun extractChapter(archive: File, destination: File): Int {
        require(destination.isDirectory && destination.listFiles().isNullOrEmpty())
        ZipFile(archive).use { zip ->
            val pages = zip.entries().asSequence().filter { entry ->
                if (entry.isDirectory) return@filter false
                val normalized = entry.name.replace('\\', '/')
                require(normalized.split('/').none { it == ".." || it == "." } &&
                    !normalized.startsWith('/')) { "Unsafe CBZ entry path" }
                normalized.substringAfterLast('.', "").lowercase() in imageExt
            }.take(MAX_CHAPTER_PAGES + 1).toList()
                .sortedWith { a, b -> NaturalPageOrder.compare(a.name, b.name) }
            require(pages.isNotEmpty() && pages.size <= MAX_CHAPTER_PAGES) { "Invalid chapter page count" }
            var total = 0L
            for ((index, entry) in pages.withIndex()) {
                require(entry.size < 0 || entry.size <= MAX_IMAGE_BYTES) { "Oversized CBZ image entry" }
                val ext = entry.name.substringAfterLast('.').lowercase()
                val target = File(destination, "%05d.%s".format(index + 1, ext))
                zip.getInputStream(entry).use { input ->
                    target.outputStream().use { out ->
                        val buffer = ByteArray(64 * 1024)
                        var current = 0L
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            current += n
                            total += n
                            require(current <= MAX_IMAGE_BYTES && total <= MAX_CHAPTER_BYTES) {
                                "Chapter decompression size limit exceeded"
                            }
                            out.write(buffer, 0, n)
                        }
                    }
                }
                require(target.length() > 0) { "Empty chapter image" }
            }
            return pages.size
        }
    }
}
