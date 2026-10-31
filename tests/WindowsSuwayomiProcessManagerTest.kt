import dev.nooir.desktop.suwayomi.SuwayomiProcessManager
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import javax.tools.ToolProvider
import java.nio.file.Files
import java.nio.file.Path

/** Launch and stop a disposable fake server using the actual Java process manager. */
private fun exerciseProcessLifecycle() {
    val root = Files.createTempDirectory("nooir-managed-server-lifecycle-")
    try {
        val javaSource = root.resolve("FakeSuwayomi.java")
        Files.writeString(javaSource, """
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
public class FakeSuwayomi {
  public static void main(String[] args) throws Exception {
    if (!"127.0.0.1".equals(System.getProperty("suwayomi.tachidesk.config.server.ip"))) {
      throw new IllegalStateException("Not bound to loopback");
    }
    int port = Integer.parseInt(System.getProperty("suwayomi.tachidesk.config.server.port"));
    ServerSocket listener = new ServerSocket();
    listener.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port));
    while (true) {
      try (Socket socket = listener.accept()) {
        socket.setSoTimeout(4000);
        BufferedReader reader = new BufferedReader(new InputStreamReader(
          socket.getInputStream(), StandardCharsets.US_ASCII));
        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {}
        String body = "{\"data\":{\"sources\":{\"totalCount\":0,\"nodes\":[]}}}";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        String headers = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: "
          + bytes.length + "\r\nConnection: close\r\n\r\n";
        socket.getOutputStream().write(headers.getBytes(StandardCharsets.US_ASCII));
        socket.getOutputStream().write(bytes);
        socket.getOutputStream().flush();
      } catch (Exception ignored) {
        // A readiness TCP probe intentionally opens/closes without HTTP.
      }
    }
  }
}
""".trimIndent(), StandardCharsets.UTF_8)
        val javaCompiler = ToolProvider.getSystemJavaCompiler()
            ?: error("A JDK compiler is required for this lifecycle test")
        check(javaCompiler.run(null, null, null,
            "-d", root.toString(), javaSource.toString()) == 0)
        val jar = root.resolve(SuwayomiProcessManager.PINNED_JAR)
        val manifest = Manifest().apply {
            mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
            mainAttributes[Attributes.Name.MAIN_CLASS] = "FakeSuwayomi"
        }
        JarOutputStream(Files.newOutputStream(jar), manifest).use { out ->
            out.putNextEntry(JarEntry("FakeSuwayomi.class"))
            out.write(Files.readAllBytes(root.resolve("FakeSuwayomi.class")))
            out.closeEntry()
        }
        val port = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1")).use {
            it.localPort
        }
        val fakeJarDigest = SuwayomiProcessManager.sha256(jar.toFile())
        check(fakeJarDigest != SuwayomiProcessManager.OFFICIAL_JAR_SHA256)
        val refused = SuwayomiProcessManager(root.resolve("untrusted-server"),
            port = port)
        check(runCatching { refused.start(jar.toFile(), 2) }.isFailure) {
            "Production manager must refuse a renamed or tampered server JAR"
        }
        check(!refused.running && !SuwayomiProcessManager.portOccupied(port))
        refused.close()
        // Explicit test-only digest enables the harmless disposable JVM fixture.
        val manager = SuwayomiProcessManager(root.resolve("server-data"),
            port = port, expectedJarSha256 = fakeJarDigest)
        try {
            check(manager.start(jar.toFile(), startupTimeoutSeconds = 15) ==
                "http://127.0.0.1:$port")
            check(manager.running)
            check(SuwayomiProcessManager.portOccupied(port))
            manager.stop()
            check(!manager.running)
            check(!SuwayomiProcessManager.portOccupied(port))
            check(manager.start(jar.toFile(), startupTimeoutSeconds = 15)
                .endsWith(":$port"))
            manager.close()
            check(!manager.running)
            check(runCatching { manager.start(jar.toFile(), 1) }.isFailure) {
                "Closed process manager must never relaunch a server"
            }
        } finally {
            manager.close()
        }
        ServerSocket(0, 4, InetAddress.getByName("127.0.0.1")).use { other ->
            val keeper = SuwayomiProcessManager(root.resolve("another-server"),
                port = other.localPort, expectedJarSha256 = fakeJarDigest)
            try {
                check(runCatching { keeper.start(jar.toFile(), 1) }.isFailure) {
                    "Must refuse a port belonging to someone else"
                }
                keeper.close()
                check(!other.isClosed &&
                    SuwayomiProcessManager.portOccupied(other.localPort)) {
                    "Closing manager must not stop or claim somebody else's server"
                }
            } finally {
                keeper.close()
            }
        }
    } finally {
        root.toFile().deleteRecursively()
    }
}

/** No real Suwayomi binary is downloaded or executed in this test. */
fun main() {
    val name = SuwayomiProcessManager.PINNED_JAR
    val java = Path.of("mock", "bin", "java.exe")
    val root = Path.of("user data", "MihonNooir", "Suwayomi data")
    val jar = Path.of("trusted downloads", name)
    val cmd = SuwayomiProcessManager.command(java, jar, root, 4567)
    check(cmd.size == 8)
    check(cmd.last() == jar.toAbsolutePath().normalize().toString())
    check(cmd[cmd.size - 2] == "-jar")
    check(cmd.any { it == "-Dsuwayomi.tachidesk.config.server.ip=127.0.0.1" })
    check(cmd.any { it == "-Dsuwayomi.tachidesk.config.server.port=4567" })
    check(cmd.any { it == "-Dsuwayomi.tachidesk.config.server.systemTrayEnabled=false" })
    check(cmd.any { it == "-Dsuwayomi.tachidesk.config.server.initialOpenInBrowserEnabled=false" })
    check(cmd.any { it == "-Dsuwayomi.tachidesk.config.server.rootDir=${root.toAbsolutePath().normalize()}" })
    check(cmd.first() == java.toAbsolutePath().normalize().toString())
    check(cmd.none { it.contains("0.0.0.0") })
    check(runCatching {
        SuwayomiProcessManager.command(java, Path.of("other.jar"), root, 4567)
    }.isFailure)
    check(runCatching { SuwayomiProcessManager.command(java, jar, root, 80) }.isFailure)

    ServerSocket(0, 4, InetAddress.getByName("127.0.0.1")).use { bound ->
        check(SuwayomiProcessManager.portOccupied(bound.localPort)) {
            "Existing server ports must never be claimed/killed by our backend"
        }
    }

    val dir = Files.createTempDirectory("nooir-backend-test-")
    try {
        val manager = SuwayomiProcessManager(dir)
        check(!manager.running)
        check(runCatching {
            manager.start(File(dir.toFile(), name), 1L)
        }.isFailure) { "Must not run an unknown/missing server binary" }
        check(!manager.running)
        manager.stop()
        manager.close()
        manager.close() // cleanup idempotent
    } finally {
        dir.toFile().deleteRecursively()
    }
    exerciseProcessLifecycle()
    println("Managed localhost backend tests passed: pinned jar, loopback, live readiness, start/stop/restart, occupied-port safety and owned-process shutdown")
}
