package dev.nooir.desktop.ui

import dev.nooir.desktop.storage.DesktopDataHome
import dev.nooir.desktop.suwayomi.SuwayomiOnlineChapter
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest

/** Desktop-local position only; no page data, credentials or server mutations. */
internal class OnlineReadingPositionStore(root: Path = DesktopDataHome.root()) {
    private val directory = root.resolve("online-reading-v1")
    companion object { private val lock = Any() }

    private fun file(endpoint: String, chapter: SuwayomiOnlineChapter, manga: String, name: String): Path {
        val fields = listOf(endpoint, chapter.mangaId.toString(), chapter.chapterId.toString(),
            chapter.sourceOrder.toString(), chapter.pageCount.toString(), manga, name)
        val identity = fields.joinToString("") { "${it.length}:$it" }
        val digest = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(UTF_8))
        val key = digest.joinToString("") { "%02x".format(it.toInt() and 255) }
        return directory.resolve("$key.txt")
    }

    private fun read(path: Path, count: Int): Int? {
        if (Files.notExists(path, NOFOLLOW_LINKS)) return null
        require(Files.isDirectory(directory, NOFOLLOW_LINKS) && Files.isRegularFile(path, NOFOLLOW_LINKS)) {
            "Unsafe online reading-position metadata; original preserved"
        }
        val bytes = Files.newInputStream(path).use { it.readNBytes(65) }
        val value = String(bytes, UTF_8)
        val page = value.removeSuffix("\n").toIntOrNull()
        require(bytes.size <= 64 && page != null && page in 0 until count && value == "$page\n") {
            "Invalid online reading position; original preserved"
        }
        return page
    }

    fun load(endpoint: String, chapter: SuwayomiOnlineChapter, manga: String, name: String): Int? =
        synchronized(lock) {
            read(file(endpoint, chapter, manga, name), chapter.pageCount)
        }

    fun save(endpoint: String, chapter: SuwayomiOnlineChapter, manga: String, name: String, page: Int) =
        synchronized(lock) {
            require(page in 0 until chapter.pageCount)
            val path = file(endpoint, chapter, manga, name)
            if (read(path, chapter.pageCount) == page) return@synchronized
            Files.createDirectories(directory)
            require(Files.isDirectory(directory, NOFOLLOW_LINKS)) { "Unsafe online reading-position directory" }
            val temp = Files.createTempFile(directory, ".position-", ".tmp")
            try {
                Files.writeString(temp, "$page\n", UTF_8)
                Files.move(temp, path, ATOMIC_MOVE, REPLACE_EXISTING)
            } finally { Files.deleteIfExists(temp) }
        }
}
