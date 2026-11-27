package dev.nooir.desktop.ui

import dev.nooir.desktop.storage.DesktopDataHome
import dev.nooir.mangabridge.MangaConversionOptions
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/** Local, bounded settings metadata. Never writes to sources, books or queued jobs. */
internal class DesktopConversionSettingsStore(private val directory: Path = DesktopDataHome.root()) {
    companion object {
        const val FILE_NAME = "conversion-settings-v1.txt"
        private const val HEADER = "MIHONNOOIR_CONVERSION_SETTINGS_V1"
        private const val MAX_BYTES = 4096

        // Explicit per-job permissions and diagnostics are not permanent consent.
        private fun preferences(o: MangaConversionOptions) = o.copy(
            allowMissingCover = false, skipUnavailableChapters = false, panelDiagnostics = false,
        )
        private fun decode(raw: String): MangaConversionOptions {
            val count = raw.split('|').size
            require(count == 22 || count == 24) { "Unknown conversion settings format" }
            val decoded = MangaConversionOptions.fromToken(raw)
            // The shared decoder intentionally supplies defaults for bad/missing
            // fields. For files, distinguish real legacy migration from corruption.
            require(decoded.preset in setOf("Manga", "Manhwa", "Manhua", "Custom") &&
                decoded.token().split('|').take(count).joinToString("|") == raw) {
                "Invalid conversion settings; original file preserved"
            }
            return preferences(decoded)
        }
    }

    private val file = directory.resolve(FILE_NAME)

    fun load(defaultIfMissing: MangaConversionOptions = MangaConversionOptions.preset("Manga")): MangaConversionOptions {
        if (Files.notExists(file, NOFOLLOW_LINKS)) return preferences(defaultIfMissing)
        require(Files.isRegularFile(file, NOFOLLOW_LINKS)) { "Conversion settings file is unreadable or not a regular file" }
        val bytes = Files.newInputStream(file).use { it.readNBytes(MAX_BYTES + 1) }
        require(bytes.size in 1..MAX_BYTES) { "Conversion settings file is empty or too large" }
        val lines = String(bytes, UTF_8).trimEnd('\n', '\r').split('\n').map { it.removeSuffix("\r") }
        require(lines.size == 2 && lines[0] == HEADER) { "Unknown conversion settings; original file preserved" }
        return decode(lines[1])
    }

    fun save(options: MangaConversionOptions) {
        val raw = preferences(options).token()
        require(decode(raw) == preferences(options)) { "Conversion settings are out of range" }
        // Never silently overwrite an invalid or newer format. The user can
        // back up/move that metadata file and save again after reviewing it.
        load()
        Files.createDirectories(directory)
        val temp = Files.createTempFile(directory, ".conversion-settings-", ".tmp")
        try {
            Files.writeString(temp, HEADER + "\n" + raw + "\n", UTF_8)
            try {
                Files.move(temp, file, ATOMIC_MOVE, REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp, file, REPLACE_EXISTING)
            }
        } finally { Files.deleteIfExists(temp) }
    }
}
