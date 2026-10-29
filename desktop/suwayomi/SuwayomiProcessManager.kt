package dev.nooir.desktop.suwayomi

import java.io.Closeable
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path
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
) : Closeable {
    companion object {
        const val PINNED_VERSION = "v2.4.2366"
        const val PINNED_JAR = "Suwayomi-Server-v2.4.2366.jar"
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
        ZipFile(jar).use { zip ->
            require(zip.getEntry("META-INF/MANIFEST.MF") != null) {
                "Selected file does not look like an executable Java server archive"
            }
        }
        synchronized(lock) {
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
            close()
            throw e
        }
    }

    /** Never kills or controls an external process on the same port. */
    override fun close() {
        val p: Process? = synchronized(lock) {
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
