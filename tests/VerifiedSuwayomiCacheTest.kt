import com.sun.net.httpserver.HttpServer
import dev.nooir.desktop.suwayomi.OfficialSuwayomiCache
import dev.nooir.desktop.suwayomi.SuwayomiProcessManager
import java.io.File
import java.net.InetSocketAddress
import java.net.URI
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

/** Self-contained localhost fixture: no official binary or copyrighted media downloaded. */
fun main() {
    val root = Files.createTempDirectory("mihonnooir-server-cache-test-").toFile()
    val fixture = File(root, "server-fixture.jar")
    val manifest = Manifest().apply {
        mainAttributes.putValue("Manifest-Version", "1.0")
        mainAttributes.putValue("Main-Class", "example.Fake")
    }
    JarOutputStream(fixture.outputStream(), manifest).use { jar ->
        jar.flush()
    }
    val data = fixture.readBytes()
    val sha = SuwayomiProcessManager.sha256(fixture)
    val calls = AtomicInteger(0)
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/official.jar") { ctx ->
        calls.incrementAndGet()
        ctx.responseHeaders.add("Content-Type", "application/java-archive")
        ctx.sendResponseHeaders(200, data.size.toLong())
        ctx.responseBody.use { it.write(data) }
    }
    server.createContext("/not-found.jar") { ctx ->
        ctx.sendResponseHeaders(404, -1)
        ctx.close()
    }
    server.start()
    try {
        val base = "http://127.0.0.1:${server.address.port}"
        val cacheDir = File(root, "downloaded").toPath()
        val valid = OfficialSuwayomiCache(cacheDir, URI("$base/official.jar"), sha)
        val progress = mutableListOf<Long>()
        val jar = valid.obtain { bytes, _ -> progress.add(bytes) }
        check(jar.name == SuwayomiProcessManager.PINNED_JAR)
        check(jar.readBytes().contentEquals(data))
        check(progress.last() == data.size.toLong())
        check(calls.get() == 1)
        val same = valid.obtain()
        check(jar == same && calls.get() == 1) {
            "An unchanged verified cache must not redownload its server"
        }

        jar.appendBytes(byteArrayOf(1))
        check(runCatching { valid.obtain() }.isFailure) {
            "Corrupted cached server JAR must be rejected before starting"
        }
        check(calls.get() == 1) { "Corrupted cache must not be overwritten without clear user action" }

        val wrongHash = OfficialSuwayomiCache(
            File(root, "bad-hash-cache").toPath(),
            URI("$base/official.jar"),
            "0".repeat(64),
        )
        check(runCatching { wrongHash.obtain() }.isFailure)
        val badDir = File(root, "bad-hash-cache")
        check(!File(badDir, SuwayomiProcessManager.PINNED_JAR).exists())
        check(badDir.listFiles().orEmpty().none { it.name.endsWith(".part") }) {
            "Rejected downloads must not leave executable files or partial archives"
        }

        val missing = OfficialSuwayomiCache(
            File(root, "missing-cache").toPath(),
            URI("$base/not-found.jar"), sha,
        )
        check(runCatching { missing.obtain() }.isFailure)
        check(File(root, "missing-cache").listFiles().orEmpty().none {
            it.name.endsWith(".jar") || it.name.endsWith(".part")
        })
        check(runCatching {
            OfficialSuwayomiCache(
                File(root, "untrusted-origin").toPath(),
                URI("http://example.org/official.jar"), sha,
            ).obtain()
        }.isFailure) { "Arbitrary non-loopback HTTP downloads must never be permitted" }

        println("Windows official server cache tests passed: digest, JAR manifest, offline reuse, corrupt refusal, missing-file and partial cleanup")
    } finally {
        server.stop(0)
        root.deleteRecursively()
    }
}
