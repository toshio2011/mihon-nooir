package dev.nooir.desktop.ui

import java.io.File
import java.nio.file.Files
import java.util.concurrent.CancellationException

/** Cooperative cancellation and one final publication point for a web export.
 * Never interrupt the shared writer while it owns a ZIP stream.
 * Keep the UI busy until the producer really returns and temporary files are removed.
 */
internal class DesktopWebExportSession {
    private var canceled = false
    private var published = false
    @Synchronized fun requestCancel(): Boolean {
        if (published) return false
        canceled = true
        return true
    }
    @Synchronized fun check() {
        if (canceled) throw CancellationException("Web export canceled")
    }
    @Synchronized private fun publish(temp: File, target: File) {
        check()
        require(!published) { "This export session has already finished" }
        require(temp.isFile && temp.length() > 0) { "No EPUB output generated" }
        // No REPLACE_EXISTING or ATOMIC_MOVE: Java's atomic move semantics
        // for an existing target vary by provider. Refuse a late conflict.
        Files.move(temp.toPath(), target.toPath())
        published = true
    }
    fun <T> export(output: File, produce: (File, () -> Unit) -> T): T {
        val target = output.canonicalFile
        require(target.extension.equals("epub", true) &&
            target.parentFile.isDirectory && !target.exists()) {
            "Choose a new .epub filename; existing files are never overwritten"
        }
        check()
        val temp = File.createTempFile(".nooir-web-", ".epub", target.parentFile)
        try {
            val result = produce(temp, ::check)
            publish(temp, target)
            return result
        } finally {
            Files.deleteIfExists(temp.toPath())
        }
    }
}
