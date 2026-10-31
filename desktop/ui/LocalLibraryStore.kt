package dev.nooir.desktop.ui

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Base64
import java.util.UUID

/** User-added paths only. Never copies comic pages, uploads scans or deletes originals. */
internal data class LocalLibraryBook(
    val file: File,
    val title: String,
    val lastOpenedAt: Long,
    val available: Boolean,
)

/** Small, versioned, atomic library index. All paths are user-approved local paths. */
internal class LocalLibraryStore(
    private val directory: Path = Path.of(System.getProperty("user.home"), ".mihonnooir"),
) {
    companion object {
        private const val HEADER = "MIHONNOOIR_LOCAL_BOOKS_V1"
        private const val MAX_BOOKS = 500
        private const val MAX_INDEX_BYTES = 512_000L
        private const val MAX_PATH_LENGTH = 2048
        private val b64 = Base64.getUrlEncoder().withoutPadding()
        private val decoder = Base64.getUrlDecoder()
    }
    private val index = directory.resolve("local-library-v1.txt")

    private fun readEntries(): MutableMap<String, Long> {
        if (!Files.exists(index)) return linkedMapOf()
        require(Files.isRegularFile(index) && Files.size(index) <= MAX_INDEX_BYTES) {
            "Local library index is too large or unreadable"
        }
        val lines = Files.readAllLines(index, StandardCharsets.UTF_8)
        require(lines.firstOrNull() == HEADER) {
            "Unknown or damaged library index; original index has been preserved"
        }
        require(lines.size <= MAX_BOOKS + 1) { "Local library contains too many entries" }
        val entries = linkedMapOf<String, Long>()
        for (line in lines.drop(1)) {
            val parts = line.split('\t')
            require(parts.size == 2) { "Damaged local library record; original preserved" }
            val path = runCatching { String(decoder.decode(parts[0]), StandardCharsets.UTF_8) }
                .getOrElse { error("Invalid library path record; original preserved") }
            val opened = parts[1].toLongOrNull()
                ?: error("Invalid reading history; original library preserved")
            require(path.isNotBlank() && path.length <= MAX_PATH_LENGTH &&
                File(path).isAbsolute && opened >= 0L) { "Invalid local book record" }
            require(entries.putIfAbsent(path, opened) == null) {
                "Duplicate local library entry; original library preserved"
            }
        }
        return entries
    }

    private fun writeEntries(entries: Map<String, Long>) {
        require(entries.size <= MAX_BOOKS) { "Local library limit of $MAX_BOOKS books reached" }
        Files.createDirectories(directory)
        val lines = buildString {
            append(HEADER).append('\n')
            for ((path, opened) in entries) {
                require(path.length in 1..MAX_PATH_LENGTH && opened >= 0)
                append(b64.encodeToString(path.toByteArray(StandardCharsets.UTF_8)))
                    .append('\t').append(opened).append('\n')
            }
        }
        require(lines.toByteArray(StandardCharsets.UTF_8).size <= MAX_INDEX_BYTES)
        val temp = directory.resolve(".local-library-" + UUID.randomUUID() + ".tmp")
        try {
            Files.writeString(temp, lines, StandardCharsets.UTF_8)
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

    @Synchronized
    fun books(): List<LocalLibraryBook> = readEntries().map { (path, opened) ->
        val file = File(path)
        LocalLibraryBook(
            file = file,
            title = file.nameWithoutExtension.ifBlank { file.name },
            lastOpenedAt = opened,
            available = file.isDirectory ||
                (file.isFile && file.extension.lowercase() in setOf("zip", "cbz")),
        )
    }.sortedWith(compareByDescending<LocalLibraryBook> { it.lastOpenedAt }
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title })

    @Synchronized
    fun add(file: File) {
        // Verify before recording the source, with bounded natural image listing.
        LocalComicPages.names(file)
        val canonical = file.canonicalFile.absolutePath
        require(canonical.length <= MAX_PATH_LENGTH) { "Selected file path is too long" }
        val entries = readEntries()
        if (!entries.containsKey(canonical)) {
            require(entries.size < MAX_BOOKS) { "Library has reached its $MAX_BOOKS book limit" }
            entries[canonical] = 0L
            writeEntries(entries)
        }
    }

    @Synchronized
    fun markOpened(file: File, time: Long = System.currentTimeMillis()) {
        require(time >= 0L)
        val canonical = file.canonicalPath
        val entries = readEntries()
        if (entries.containsKey(canonical)) {
            entries[canonical] = time
            writeEntries(entries)
        }
    }

    @Synchronized
    fun remove(file: File) {
        val canonical = file.canonicalPath
        val entries = readEntries()
        if (entries.remove(canonical) != null) writeEntries(entries)
        // Never delete, alter or move the user's original comic!
    }
}
