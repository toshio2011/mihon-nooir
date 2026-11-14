import dev.nooir.desktop.storage.DesktopDataHome
import java.nio.file.Files
import java.nio.file.Path

fun main() {
    val temp = Files.createTempDirectory("nooir-storage-root")
    try {
        val home = temp.resolve("existing-user-home")
        val oldRoot = home.resolve(".mihonnooir")
        val resolved = DesktopDataHome.rootFor(null, home.toString())
        check(resolved == oldRoot)
        check(DesktopDataHome.rootFor("  ", home.toString()) == oldRoot)

        val external = temp.resolve("another-volume").resolve("MihonNooirData")
        val chosen = DesktopDataHome.rootFor("  $external  ", home.toString())
        check(chosen == external)
        check(chosen != oldRoot)
        check(!Files.exists(external)) { "Reading storage configuration must not create files" }

        val canonical = temp.resolve("another-volume").resolve("cache").resolve("..").resolve("MihonNooirData")
        check(DesktopDataHome.rootFor(canonical.toString(), home.toString()) == external)

        for (bad in listOf("relative-folder", "../escape", ".")) {
            check(runCatching { DesktopDataHome.rootFor(bad, home.toString()) }.isFailure) {
                "Relative override unexpectedly accepted: $bad"
            }
        }
        val driveRoot = temp.root ?: error("Missing filesystem root")
        check(runCatching { DesktopDataHome.rootFor(driveRoot.toString(), home.toString()) }.isFailure) {
            "Drive root must be rejected; select a dedicated data subfolder"
        }

        check(!Files.exists(oldRoot))
        println("PASS: Windows storage directory default, explicit D-style override, path safety and no implicit migration")
    } finally {
        Files.walk(temp).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }
}
