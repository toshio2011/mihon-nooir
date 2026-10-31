package dev.nooir.desktop.suwayomi

import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration
import java.util.UUID
import java.util.zip.ZipFile

/**
 * Optional, consent-gated download of exactly one pinned official Suwayomi JAR.
 *
 * The GUI must explicitly confirm the download with the user first. This
 * class does not auto-run, auto-update, install extensions or alter Android.
 * Every byte is hashed before the executable can be passed to the existing
 * SuwayomiProcessManager (which verifies the same hash independently).
 */
internal class OfficialSuwayomiCache(
    private val directory: Path = Path.of(System.getProperty("user.home"),
        ".mihonnooir", "server-cache"),
    private val artifactUri: URI = URI.create(OFFICIAL_URL),
    private val expectedDigest: String = SuwayomiProcessManager.OFFICIAL_JAR_SHA256,
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build(),
) {
    companion object {
        const val OFFICIAL_URL =
            "https://github.com/Suwayomi/Suwayomi-Server/releases/download/v2.4.2366/" +
                "Suwayomi-Server-v2.4.2366.jar"
        private const val MAX_BYTES = 400_000_000L
    }

    private fun validate(file: File): Boolean {
        if (!file.isFile || Files.isSymbolicLink(file.toPath()) ||
            file.length() !in 1..MAX_BYTES) return false
        if (!SuwayomiProcessManager.sha256(file).equals(expectedDigest, ignoreCase = true)) {
            return false
        }
        return runCatching {
            ZipFile(file).use { it.getEntry("META-INF/MANIFEST.MF") != null }
        }.getOrDefault(false)
    }

    /**
     * Must be run off the Swing event thread. Reports bounded progress for
     * display only; progress callbacks must not make network/GUI operations.
     */
    fun obtain(onProgress: (received: Long, total: Long?) -> Unit = { _, _ -> }): File {
        require(expectedDigest.matches(Regex("[0-9a-fA-F]{64}"))) {
            "Invalid trusted Suwayomi download digest"
        }
        require(artifactUri.scheme == "https" && artifactUri.host == "github.com" ||
            artifactUri.scheme == "http" && artifactUri.host == "127.0.0.1") {
            "Refusing an untrusted Suwayomi artifact URL"
        }
        Files.createDirectories(directory)
        require(Files.isDirectory(directory) && !Files.isSymbolicLink(directory)) {
            "Unsafe server cache directory"
        }
        val destination = directory.resolve(SuwayomiProcessManager.PINNED_JAR)
        if (Files.exists(destination)) {
            require(validate(destination.toFile())) {
                "A damaged or untrusted Suwayomi JAR is already in the cache. " +
                    "Move it aside manually before retrying; it will never be executed."
            }
            return destination.toFile()
        }

        val request = HttpRequest.newBuilder(artifactUri)
            .timeout(Duration.ofMinutes(12)).GET().build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
        val advertised = response.headers().firstValueAsLong("Content-Length")
            .orElse(-1L)
        val total: Long? = if (advertised in 1..MAX_BYTES) advertised else null
        val temp = directory.resolve(".download-${UUID.randomUUID()}.part")
        try {
            response.body().use { input ->
                require(response.statusCode() == 200) {
                    "Official Suwayomi download returned HTTP ${response.statusCode()}"
                }
                require(advertised <= MAX_BYTES) {
                    "Suwayomi download exceeds the allowed size"
                }
                var readBytes = 0L
                var reportedAt = 0L
                val digest = MessageDigest.getInstance("SHA-256")
                Files.newOutputStream(temp).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        readBytes += read
                        require(readBytes <= MAX_BYTES) {
                            "Suwayomi download exceeded 400 MB"
                        }
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                        if (readBytes - reportedAt >= 2 * 1024 * 1024) {
                            onProgress(readBytes, total)
                            reportedAt = readBytes
                        }
                    }
                }
                require(readBytes > 0L && (total == null || readBytes == total)) {
                    "Incomplete Suwayomi server download"
                }
                val actual = digest.digest().joinToString("") {
                    (it.toInt() and 255).toString(16).padStart(2, '0')
                }
                require(actual.equals(expectedDigest, ignoreCase = true)) {
                    "Downloaded Suwayomi server failed the official SHA-256 check"
                }
            }
            require(validate(temp.toFile())) {
                "Downloaded server JAR is missing a valid manifest or checksum"
            }
            try {
                Files.move(temp, destination, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp, destination)
            } catch (_: FileAlreadyExistsException) {
                require(validate(destination.toFile())) {
                    "Another process created an unverified server cache entry"
                }
            }
            require(validate(destination.toFile())) { "Server cache verification failed" }
            onProgress(destination.toFile().length(), total)
            return destination.toFile()
        } finally {
            Files.deleteIfExists(temp)
        }
    }
}
