package dev.nooir.desktop.suwayomi

/**
 * Developer-only manga source browser for an already-running localhost Suwayomi server.
 * It does not install sources, download chapters or modify the MangaNooir Android library.
 */
object SuwayomiBrowseCli {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size % 2 == 0) {
            "Use --mode sources|library|popular|latest|search|chapters [--source-id ID] [--query TEXT]"
        }
        val flags = args.toList().chunked(2).associate { it[0] to it[1] }
        require(flags.keys.all { it in setOf(
            "--mode", "--base-url", "--source-id", "--manga-id", "--query",
            "--page", "--offset", "--limit", "--refresh",
        ) }) { "Unknown source browse option" }
        require(flags.size == args.size / 2) { "Duplicate browse option" }
        val client = SuwayomiClient(
            flags["--base-url"] ?: "http://127.0.0.1:4567",
            System.getenv("NOOIR_SUWAYOMI_AUTHORIZATION"),
        )
        when (flags["--mode"] ?: "sources") {
            "sources" -> {
                require(flags["--source-id"] == null && flags["--manga-id"] == null &&
                    flags["--query"] == null && flags["--page"] == null && flags["--refresh"] == null) {
                    "Source listing accepts --offset and --limit, not manga search arguments"
                }
                val offset = flags["--offset"]?.toIntOrNull() ?: 0
                val limit = flags["--limit"]?.toIntOrNull() ?: 100
                val result = client.sources(offset, limit)
                println("Installed sources: ${result.totalCount}; showing ${result.sources.size} at offset $offset")
                for (source in result.sources) {
                    println(listOf(
                        source.id.toString(), source.language, clean(source.displayName),
                        if (source.supportsLatest) "latest" else "no-latest", source.contentWarning,
                    ).joinToString("\t"))
                }
                if (offset.toLong() + result.sources.size < result.totalCount) {
                    println("More sources: repeat with --offset ${offset + result.sources.size}")
                }
            }
            "library" -> {
                require(flags["--source-id"] == null && flags["--manga-id"] == null &&
                    flags["--query"] == null && flags["--page"] == null && flags["--refresh"] == null) {
                    "Library listing accepts --offset and --limit only"
                }
                val offset = flags["--offset"]?.toIntOrNull() ?: 0
                val limit = flags["--limit"]?.toIntOrNull() ?: 100
                val result = client.library(offset, limit)
                println("Suwayomi library: ${result.totalCount}; showing ${result.mangas.size} at offset $offset")
                for (manga in result.mangas) {
                    println("${manga.id}\\t${clean(manga.title)}\\t${clean(manga.author ?: "")}")
                }
                if (offset.toLong() + result.mangas.size < result.totalCount) {
                    println("More library titles: repeat with --offset ${offset + result.mangas.size}")
                }
            }
            "popular", "latest", "search" -> {
                require(flags["--offset"] == null && flags["--limit"] == null && flags["--manga-id"] == null && flags["--refresh"] == null) {
                    "Manga browsing uses --source-id and --page, not source pagination"
                }
                val sourceId = flags["--source-id"]?.toLongOrNull()
                    ?.takeIf { it != 0L } ?: error("Missing nonzero --source-id (use --mode sources first)")
                val page = flags["--page"]?.toIntOrNull() ?: 1
                val mode = when (flags["--mode"]) {
                    "popular" -> SuwayomiBrowseMode.POPULAR
                    "latest" -> SuwayomiBrowseMode.LATEST
                    else -> SuwayomiBrowseMode.SEARCH
                }
                val result = client.browse(sourceId, mode, page, flags["--query"])
                println("${mode.name} from source $sourceId, page ${result.page}, ${result.mangas.size} titles")
                for (manga in result.mangas) {
                    println(listOf(
                        manga.id.toString(), clean(manga.title), clean(manga.author ?: ""),
                        if (manga.inLibrary) "in-library" else "not-in-library",
                    ).joinToString("\t"))
                }
                if (result.hasNextPage) println("More titles: repeat with --page ${page + 1}")
            }
            "chapters" -> {
                require(flags["--source-id"] == null && flags["--query"] == null &&
                    flags["--offset"] == null && flags["--limit"] == null && flags["--page"] == null) {
                    "Chapter details only accept --manga-id"
                }
                val id = flags["--manga-id"]?.toIntOrNull()
                    ?.takeIf { it > 0 } ?: error("Missing positive --manga-id")
                val refresh = when (flags["--refresh"] ?: "false") {
                    "true" -> true
                    "false" -> false
                    else -> error("--refresh must be true or false")
                }
                if (refresh) client.refreshManga(id)
                val manga = client.manga(id)
                println("${clean(manga.title)} (manga ID ${manga.id}) — ${manga.chapters.size} chapters")
                val chapters = manga.chapters.sortedWith(compareBy<SuwayomiChapter> {
                    if (it.chapterNumber >= 0) it.chapterNumber else Double.MAX_VALUE
                }.thenByDescending { it.sourceOrder }.thenBy { it.id })
                for (chapter in chapters) {
                    println("${chapter.id}\t${if (chapter.downloaded) "downloaded" else "not-downloaded"}\t${clean(chapter.name)}")
                }
                println("Export selected IDs with SuwayomiToNooir --mode export --manga-id $id --chapters ID,ID ...")
            }
            else -> error("--mode must be sources, library, popular, latest, search or chapters")
        }
    }

    private fun clean(value: String): String =
        value.replace(Regex("[\\t\\r\\n\\p{Cc}]"), " ").trim().take(240)
}
