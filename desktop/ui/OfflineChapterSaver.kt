package dev.nooir.desktop.ui

import dev.nooir.desktop.suwayomi.SuwayomiChapter
import dev.nooir.desktop.suwayomi.SuwayomiClient
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * Explicit download-to-offline-library bridge.
 *
 * The Suwayomi source must ALREADY have downloaded each selected chapter.
 * This never installs extensions, queues remote chapter downloads, changes
 * reading state or overwrites prior archives. A failed transfer leaves no
 * partially published .cbz file.
 */
internal class OfflineChapterSaver(
    private val library: LocalLibraryStore = LocalLibraryStore(),
    private val downloads: Path = Path.of(System.getProperty("user.home"),
        ".mihonnooir", "downloaded-chapters"),
    private val catalog: OfflineSeriesCatalog = OfflineSeriesCatalog(),
) {
    companion object {
        const val MAX_BATCH = 20
    }

    private fun name(mangaId: Int, chapter: SuwayomiChapter): String {
        val title = chapter.name.replace(Regex("[^\\p{L}\\p{N} ._()-]"), "_")
            .trim().trimEnd(' ', '.').take(65).ifBlank { "Chapter" }
        return "manga-$mangaId-chapter-${chapter.id}-$title.cbz"
    }

    fun save(client: SuwayomiClient, mangaId: Int, chapterIds: List<Int>,
        onProgress: (SuwayomiChapter, Long, Long?) -> Unit = { _, _, _ -> }): List<File> {
        require(mangaId > 0 && chapterIds.size in 1..MAX_BATCH &&
            chapterIds.all { it > 0 } && chapterIds.distinct().size == chapterIds.size) {
            "Select 1 to $MAX_BATCH different chapters for the offline library"
        }
        val manga = client.manga(mangaId)
        val selected = chapterIds.map { id ->
            manga.chapters.firstOrNull { it.id == id }
                ?: error("Chapter $id does not belong to this manga")
        }
        require(selected.all { it.downloaded }) {
            "Selected chapters must already be downloaded in Suwayomi"
        }
        Files.createDirectories(downloads)
        require(Files.isDirectory(downloads)) { "Offline downloads directory unavailable" }
        val results = ArrayList<File>(selected.size)
        for (chapter in selected) {
            val target = downloads.resolve(name(manga.id, chapter))
            if (Files.exists(target)) {
                require(Files.isRegularFile(target)) { "Offline chapter path is not a regular file" }
                // Never overwrite a downloaded file (or treat corruption as a
                // successful transfer). User can repair it explicitly.
                val existingPages = LocalComicPages.names(target.toFile())
                LocalComicPages.read(target.toFile(), existingPages.first())
                library.add(target.toFile())
                catalog.remember(manga.id, manga.title, chapter, target.toFile())
                results.add(target.toFile())
                continue
            }
            val temporary = downloads.resolve(".chapter-${UUID.randomUUID()}.cbz")
            try {
                client.downloadCbz(chapter.id, temporary.toFile(),
                    onProgress = { copied, total -> onProgress(chapter, copied, total) })
                val pages = LocalComicPages.names(temporary.toFile())
                LocalComicPages.read(temporary.toFile(), pages.first())
                try {
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE)
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(temporary, target)
                }
                library.add(target.toFile())
                catalog.remember(manga.id, manga.title, chapter, target.toFile())
                results.add(target.toFile())
            } finally {
                Files.deleteIfExists(temporary)
            }
        }
        return results
    }
}
