package dev.nooir.desktop.storage

import java.nio.file.Path

/**
 * One data root for Windows preview metadata, downloaded CBZs and the optional
 * verified Suwayomi server/JAR.
 *
 * The override is explicit; existing data is NEVER moved or deleted.
 * By default legacy ~/.mihonnooir locations are retained, so previous
 * previews reopen the same bookshelf without migration.
 */
object DesktopDataHome {
    const val ENV_NAME = "MIHONNOOIR_DATA_DIR"

    fun root(): Path = rootFor(
        System.getenv(ENV_NAME),
        System.getProperty("user.home") ?: error("Missing home directory"),
    )

    /** Pure path resolution for cross-platform tests. Does not write to disk. */
    fun rootFor(overrideRoot: String?, userHome: String): Path {
        val preferred = overrideRoot?.trim()?.takeIf { it.isNotEmpty() }
        val target = if (preferred == null) {
            Path.of(userHome, ".mihonnooir")
        } else {
            Path.of(preferred)
        }
        require(target.isAbsolute) {
            "$ENV_NAME must be an absolute folder path, such as D:\\MihonNooirData"
        }
        val normalized = target.normalize()
        require(normalized.parent != null) {
            "$ENV_NAME must name a dedicated folder, not a drive root"
        }
        return normalized
    }
}
