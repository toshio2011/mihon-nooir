package dev.nooir.desktop.suwayomi

import java.io.Closeable
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

/**
 * Windows-specific lifecycle for an explicitly selected, pinned Suwayomi jar.
 *
 * Never downloads or installs a server silently; never attaches a process
 * belonging to another app. Only process handles created here can be stopped.
 * All network binding flags are forced to IPv4 loopback.
 */
class SuwayomiProcessManager(
    private val root: Path = Path.of(System.getProperty("user.home"),
        ".mihonnooir", "managed-suwayomi"),
    private val javaExecutable: Path = Path.of(System.getProperty("java.home"),
        "bin", if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java"),
    private val port: Int = 4567,
    // Test fixtures use a different digest explicitly; app callers use the
    // official release fingerprint below and cannot set it from the GUI.
    private val expectedJarSha256: String = OFFICIAL_JAR_SHA256,
) : Closeable {
    companion object {
        const val PINNED_VERSION = "v2.4.2366"
        const val PINNED_JAR = "Suwayomi-Server-v2.4.2366.jar"
        // The SHA-256 digest published with the official GitHub v2.4.2366
        // release asset. Never accept a same-name unverified binary.
        const val OFFICIAL_JAR_SHA256 = "af9feb20af9d7ebe9e30769e6c6ebc7fc7ab1c447388d42e0280b1da5fe07bfd"

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                }
            }
            return digest.digest().joinToString("") {
                (it.toInt() and 255).toString(16).padStart(2, '0')
            }
        }
        const val DEFAULT_PORT = 4567

        /** Pure command construction, independently testable without spawning a JVM. */
        fun command(javaExe: Path, jar: Path, dataRoot: Path, port: Int): List<String> {
            require(port in 1024..65535) { "Suwayomi managed port must be 1024..65535" }
            require(jar.fileName.toString() == PINNED_JAR) {
                "Only the explicitly pinned Suwayomi $PINNED_VERSION server jar is supported"
            }
            return listOf(
                javaExe.toAbsolutePath().normalize().toString(),
                "-Dsuwayomi.tachidesk.config.server.rootDir=${dataRoot.toAbsolutePath().normalize()}",
                "-Dsuwayomi.tachidesk.config.server.ip=127.0.0.1",
                "-Dsuwayomi.tachidesk.config.server.port=$port",
                "-Dsuwayomi.tachidesk.config.server.systemTrayEnabled=false",
                "-Dsuwayomi.tachidesk.config.server.initialOpenInBrowserEnabled=false",
                "-jar",
                jar.toAbsolutePath().normalize().toString(),
            )
        }

        /** An already-bound port is somebody else's server; never take ownership. */
        fun portOccupied(port: Int): Boolean =
            Socket().use { s ->
                runCatching { s.connect(InetSocketAddress("127.0.0.1", port), 300) }.isSuccess
            }
    }

    private val url = "http://127.0.0.1:$port"
    @Volatile private var owned: Process? = null
    private val lock = Any()
    @Volatile private var closed = false

    val endpoint: String get() = url
    val running: Boolean get() = owned?.isAlive == true

    /**
     * Called from a background worker. Explicit jar selection is mandatory.
     * A successful return means GraphQL was reachable, not merely that the
     * TCP socket happened to open.
     */
    fun start(jar: File, startupTimeoutSeconds: Long = 90L): String {
        require(startupTimeoutSeconds in 1..180) { "Invalid startup timeout" }
        require(port in 1024..65535) { "Invalid managed backend port" }
        require(jar.name == PINNED_JAR && jar.isFile && jar.length() in 1..400_000_000L) {
            "Choose the official $PINNED_JAR from the Suwayomi release"
        }
        require(Files.isRegularFile(javaExecutable)) {
            "Java launcher not found in the bundled runtime; managed backend needs a Java-capable runtime"
        }
        require(expectedJarSha256.matches(Regex("[0-9a-f]{64}"))) { "Invalid pinned server digest" }
        require(sha256(jar).equals(expectedJarSha256, ignoreCase = true)) {
            "Server JAR SHA-256 does not match the trusted Suwayomi v2.4.2366 release. " +
                "Download the official jar again and verify its checksum."
        }
        ZipFile(jar).use { zip ->
            require(zip.getEntry("META-INF/MANIFEST.MF") != null) {
                "Selected file does not look like an executable Java server archive"
            }
        }
        synchronized(lock) {
            check(!closed) { "Managed server session has already been closed" }
            require(owned?.isAlive != true) {
                "A managed Suwayomi process is already running"
            }
            require(!portOccupied(port)) {
                "Port $port is already in use. Use Connect to the existing server instead; MihonNooir will not terminate it."
            }
            Files.createDirectories(root)
            val log = root.resolve("server-output.log")
            val args = command(javaExecutable, jar.toPath(), root, port)
            owned = ProcessBuilder(args)
                .directory(root.toFile())
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()))
                .start()
        }
        try {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(startupTimeoutSeconds)
            while (System.nanoTime() < deadline) {
                val p = owned ?: error("Managed server was stopped")
                check(p.isAlive) {
                    "Suwayomi server exited during startup. See ${root.resolve("server-output.log")}"
                }
                if (portOccupied(port)) {
                    val ready = runCatching { SuwayomiClient(url).sources(0, 1) }.isSuccess
                    if (ready) return url
                }
                Thread.sleep(750)
            }
            error("Suwayomi did not pass GraphQL readiness within $startupTimeoutSeconds seconds. See ${root.resolve("server-output.log")}")
        } catch (e: Exception) {
            stop()
            throw e
        }
    }

    /** Stop only the server started by this manager; allow another explicit start. */
    fun stop() = terminate(closeManager = false)

    /** Permanently close the session when the application window is disposed. */
    override fun close() = terminate(closeManager = true)

    private fun terminate(closeManager: Boolean) {
        val p: Process? = synchronized(lock) {
            if (closeManager) closed = true
            val current = owned
            owned = null
            current
        }
        if (p != null && p.isAlive) {
            p.destroy()
            if (!p.waitFor(5, TimeUnit.SECONDS)) {
                p.destroyForcibly()
                p.waitFor(5, TimeUnit.SECONDS)
            }
        }
    }
}
