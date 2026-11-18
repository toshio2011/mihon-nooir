package dev.nooir.desktop.ui

import dev.nooir.desktop.storage.DesktopDataHome
import dev.nooir.mangabridge.MangaConversionOptions
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Local conversion defaults only. No credentials, paths, remote permissions
 * or job approvals are saved. Never modify durable queue records.
 */
internal class DesktopConversionPreferences(
    private val root: () -> Path = DesktopDataHome::root,
) {
    data class Loaded(val options: MangaConversionOptions, val warning: String? = null)

    companion object {
        const val FILE_NAME = "conversion-preferences-v1.txt"
        private const val MAX_BYTES = 4096
        private val validFields = setOf(7,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24)
        private fun defaults() = MangaConversionOptions.preset("Manga")
        private fun persistent(options: MangaConversionOptions) = options.copy(
            allowMissingCover = false, skipUnavailableChapters = false, panelDiagnostics = false,
        )
        private fun decode(bytes: ByteArray): MangaConversionOptions {
            require(bytes.isNotEmpty() && bytes.size <= MAX_BYTES) { "Invalid preference size" }
            val value = String(bytes, StandardCharsets.UTF_8).removeSuffix("\n")
            require(!value.contains('\n') && !value.contains('\r'))
            val fields = value.split("|")
            require(fields.size in validFields && fields[0] in setOf("Manga","Manhwa","Manhua","Custom"))
            val decoded = MangaConversionOptions.fromToken(value)
            // Validate the entire known prefix: reject corrupt, out-of-range,
            // or unknown values rather than silently accepting partial defaults.
            require(decoded.token().split("|").take(fields.size) == fields)
            return persistent(decoded)
        }
    }

    fun load(): Loaded {
        return try {
            val path = root().resolve(FILE_NAME)
            if (Files.notExists(path, LinkOption.NOFOLLOW_LINKS)) return Loaded(defaults())
            require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) { "Invalid preferences file" }
            val bytes = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS).use {
                it.readNBytes(MAX_BYTES + 1)
            }
            Loaded(decode(bytes))
        } catch (_: NoSuchFileException) {
            Loaded(defaults())
        } catch (_: Exception) {
            Loaded(defaults(), "Saved conversion preferences could not be read. Defaults used; existing file retained.")
        }
    }

    /** Atomic replace only; an unsuccessful save must not erase the previous defaults. */
    fun save(options: MangaConversionOptions) {
        val value = persistent(options)
        val bytes = (value.token() + "\n").toByteArray(StandardCharsets.UTF_8)
        require(decode(bytes) == value) { "Invalid conversion preferences" }
        val folder = root()
        Files.createDirectories(folder)
        val path = folder.resolve(FILE_NAME)
        require(Files.notExists(path, LinkOption.NOFOLLOW_LINKS) ||
            Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) { "Invalid preferences file" }
        val tmp = Files.createTempFile(folder,".conversion-preferences-",".tmp")
        try {
            Files.write(tmp,bytes)
            Files.move(tmp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(tmp) }
    }
}
