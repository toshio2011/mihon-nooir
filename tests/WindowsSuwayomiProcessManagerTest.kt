import dev.nooir.desktop.suwayomi.SuwayomiProcessManager
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path

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
    println("Managed localhost backend contract passed: pinned jar, forced loopback, isolated root, port ownership and safe refusal")
}
