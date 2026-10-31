package dev.nooir.desktop.ui

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

/**
 * Windows-only explicit page bookmarks, independent of the reader's resume
 * position and of Suwayomi/Android databases. Persist only a hash of the
 * local book path, never the user's original paths or pages.
 */
internal data class ReaderPageBookmark(val pageIndex: Int, val createdAt: Long)

internal class ReaderBookmarkStore(
    private val directory: Path = Path.of(System.getProperty("user.home"), ".mihonnooir"),
) {
    companion object {
        private const val HEADER = "MIHONNOOIR_READER_BOOKMARKS_V1"
        private const val MAX_BYTES = 1_048_576L
        const val MAX_BOOKMARKS_PER_BOOK = 100
        private const val MAX_TOTAL = 10_000
        private val lock = Any()
        private val SHA256 = Regex("[a-f0-9]{64}")
    }

    private data class Entry(val key: String, val page: Int, val createdAt: Long)
    private val index = directory.resolve("reader-bookmarks-v1.tsv")

    private fun key(book: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(book.canonicalPath.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    }

    private fun read(): MutableList<Entry> {
        if (!Files.exists(index)) return mutableListOf()
        require(Files.isRegularFile(index) && !Files.isSymbolicLink(index) &&
            Files.size(index) <= MAX_BYTES) { "Reader bookmark index is unsafe; original preserved" }
        val lines = Files.readAllLines(index, StandardCharsets.UTF_8)
        require(lines.firstOrNull() == HEADER && lines.size <= MAX_TOTAL + 1) {
            "Unknown reader bookmark format; original preserved"
        }
        val entries = lines.drop(1).map { line ->
            val parts = line.split('\t')
            require(parts.size == 3) { "Damaged reader bookmark; original preserved" }
            val entry = Entry(parts[0], parts[1].toInt(), parts[2].toLong())
            require(SHA256.matches(entry.key) && entry.page in 0..4_999 &&
                entry.createdAt >= 0L) { "Invalid reader bookmark; original preserved" }
            entry
        }
        require(entries.map { it.key to it.page }.distinct().size == entries.size) {
            "Duplicate reader bookmark; original preserved"
        }
        require(entries.groupingBy { it.key }.eachCount().values.all {
            it <= MAX_BOOKMARKS_PER_BOOK
        }) { "Reader bookmark limit exceeded; original preserved" }
        return entries.toMutableList()
    }

    private fun write(entries: List<Entry>) {
        require(entries.size <= MAX_TOTAL)
        Files.createDirectories(directory)
        require(Files.isDirectory(directory) && !Files.isSymbolicLink(directory)) {
            "Unsafe reader bookmark directory"
        }
        val content = buildString {
            append(HEADER).append('\n')
            entries.forEach { append(it.key).append('\t').append(it.page)
                .append('\t').append(it.createdAt).append('\n') }
        }
        require(content.toByteArray(StandardCharsets.UTF_8).size <= MAX_BYTES)
        val temp = directory.resolve(".reader-bookmarks-${UUID.randomUUID()}.tmp")
        try {
            Files.writeString(temp, content, StandardCharsets.UTF_8)
            try {
                Files.move(temp, index, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp, index, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    /** Zero-based source-page bookmarks; distinct from the last-read position. */
    fun list(book: File): List<ReaderPageBookmark> = synchronized(lock) {
        val id = key(book)
        read().asSequence().filter { it.key == id }.sortedBy { it.page }
            .map { ReaderPageBookmark(it.page, it.createdAt) }.toList()
    }

    fun add(book: File, page: Int, createdAt: Long = System.currentTimeMillis()) =
        synchronized(lock) {
            require(page in 0..4_999 && createdAt >= 0L) { "Invalid reader bookmark page" }
            val id = key(book)
            val entries = read()
            if (entries.any { it.key == id && it.page == page }) return@synchronized
            require(entries.count { it.key == id } < MAX_BOOKMARKS_PER_BOOK) {
                "This book already has 100 bookmarks"
            }
            require(entries.size < MAX_TOTAL) { "Reader bookmark index is full" }
            entries += Entry(id, page, createdAt)
            write(entries)
        }

    fun remove(book: File, page: Int) = synchronized(lock) {
        require(page in 0..4_999) { "Invalid reader bookmark page" }
        val id = key(book)
        val entries = read()
        if (entries.removeAll { it.key == id && it.page == page }) write(entries)
    }
}
