package dev.nooir.desktop.ui

import dev.nooir.bridge.BookMetadata
import dev.nooir.bridge.QuantizedPage
import dev.nooir.bridge.XtcCodec
import dev.nooir.bridge.XtcFormat
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicReference
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JScrollPane
import javax.swing.SwingUtilities

/** Real Swing dialog acceptance under Xvfb, never run in headless mode. */
fun main() {
    check(!GraphicsEnvironment.isHeadless()) { "Use a virtual display (xvfb-run)" }
    val folder = Files.createTempDirectory("nooir-saved-book-swing-").toFile()
    try {
        for ((device, dims) in listOf("X4" to (480 to 800), "X3" to (528 to 792))) {
            val (width, height) = dims
            for (format in XtcFormat.entries) {
                val levels = if (format == XtcFormat.XTC) 2 else 4
                val pages = (0..2).map { index ->
                    QuantizedPage(width, height, ByteArray(width * height) { pixel ->
                        ((pixel % width / 41 + pixel / width / 67 + index) % levels).toByte()
                    }, format.depth)
                }
                val saved = folder.resolve("gui-" + device + "." + format.extension)
                saved.outputStream().use { target ->
                    XtcCodec.write(format, pages.asSequence(), target, folder,
                        BookMetadata("Original synthetic GUI fixture"))
                }
                val originalBytes = saved.readBytes()
                val parent = onSwingThread {
                    JFrame("Nooir viewer test parent").apply {
                        size = Dimension(700, 650)
                        isVisible = true
                    }
                }
                try {
                    val dialog = onSwingThread {
                        checkNotNull(DesktopXtcBinaryPreview.showSavedBook(parent, saved))
                    }
                    try {
                        val labels = onSwingThread {
                            descendants(dialog).filterIsInstance<JLabel>()
                        }
                        val details = labels.first { it.text.startsWith(format.name + " · page") }
                        val scroll = onSwingThread {
                            descendants(dialog).filterIsInstance<JScrollPane>().first()
                        }
                        val preview = onSwingThread {
                            scroll.viewport.view as JLabel
                        }
                        val buttons = onSwingThread {
                            descendants(dialog).filterIsInstance<JButton>()
                        }
                        val previous = buttons.first { it.text == "Previous" }
                        val next = buttons.first { it.text == "Next" }
                        val fit = onSwingThread {
                            descendants(dialog).filterIsInstance<JCheckBox>().single()
                        }
                        check(onSwingThread { fit.isSelected && !previous.isEnabled && next.isEnabled })
                        awaitPage(dialog, details, preview, 1, "fitted display")
                        onSwingThread {
                            dialog.size = Dimension(560, 520)
                            dialog.validate()
                        }
                        awaitPage(dialog, details, preview, 1, "fitted display")
                        awaitFitInsideViewport(scroll, preview)
                        check(onSwingThread { (preview.icon as ImageIcon).iconHeight < height }) {
                            "Saved page did not fit reduced window: $device $format"
                        }
                        onSwingThread { fit.doClick() }
                        awaitPage(dialog, details, preview, 1, "1:1 pixels")
                        check(onSwingThread {
                            (preview.icon as ImageIcon).iconWidth == width &&
                                (preview.icon as ImageIcon).iconHeight == height
                        }) { "Native inspection changed dimensions: $device $format" }
                        onSwingThread {
                            next.doClick()
                            next.doClick()
                        }
                        awaitPage(dialog, details, preview, 3, "1:1 pixels")
                        check(onSwingThread { previous.isEnabled && !next.isEnabled })
                        onSwingThread {
                            previous.doClick()
                            previous.doClick()
                        }
                        awaitPage(dialog, details, preview, 1, "1:1 pixels")
                        check(onSwingThread { !previous.isEnabled && next.isEnabled })
                        onSwingThread { fit.doClick() }
                        awaitPage(dialog, details, preview, 1, "fitted display")
                    } finally {
                        onSwingThread { dialog.dispose() }
                        check(!onSwingThread { dialog.isDisplayable })
                    }
                    verifyCorruptBookError(parent, folder, device, format)
                } finally {
                    onSwingThread { parent.dispose() }
                }
                check(saved.readBytes().contentEquals(originalBytes)) {
                    "Swing viewer must never modify its selected saved book: $device $format"
                }
            }
        }
        println("PASS: real Swing X3/X4 XTC/XTCH viewer, fit, native, navigation, resize, close, corrupt-file warning")
    } finally {
        folder.deleteRecursively()
    }
}

/**
 * The production viewer must reject a corrupt saved XTC/XTCH file using a
 * visible modal error, without opening a reader or changing source bytes.
 * Dismiss the modal from a non-EDT test thread.
 */
private fun verifyCorruptBookError(
    parent: JFrame, folder: java.io.File, device: String, format: XtcFormat,
) {
    val broken = folder.resolve("damaged-$device.${format.extension}")
    val bytes = byteArrayOf(0x58, 0x54, 0x43, 0)
    broken.writeBytes(bytes)
    val completed = AtomicReference<Boolean?>(null)
    val failure = AtomicReference<Throwable?>()
    SwingUtilities.invokeLater {
        try {
            check(DesktopXtcBinaryPreview.showSavedBook(parent, broken) == null) {
                "Corrupt binary must not open a page reader"
            }
            completed.set(true)
        } catch (e: Throwable) {
            failure.set(e)
            completed.set(false)
        }
    }
    val deadline = System.nanoTime() + 10_000_000_000L
    var dismissed = false
    try {
        while (System.nanoTime() < deadline) {
            val warning = onSwingThread {
                java.awt.Window.getWindows().filterIsInstance<JDialog>()
                    .firstOrNull { it.isShowing && it.title == "Binary book preview" }
            }
            if (warning != null) {
                val errorShown = onSwingThread {
                    descendants(warning).filterIsInstance<JLabel>().any {
                        it.text.contains("Cannot read saved XTC/XTCH book:")
                    }
                }
                check(errorShown) { "Corrupt source did not show the expected error" }
                onSwingThread { warning.dispose() }
                dismissed = true
                break
            }
            failure.get()?.let { throw it }
            Thread.sleep(30)
        }
        check(dismissed) { "Timed out waiting for corrupt-file warning: $device $format" }
        while (System.nanoTime() < deadline && completed.get() == null) Thread.sleep(30)
        failure.get()?.let { throw it }
        check(completed.get() == true) { "Corrupt-file handler failed to return cleanly" }
        check(broken.readBytes().contentEquals(bytes)) { "Damaged source was modified" }
    } finally {
        onSwingThread {
            java.awt.Window.getWindows().filterIsInstance<JDialog>()
                .filter { it.isShowing && it.title == "Binary book preview" }
                .forEach { it.dispose() }
        }
    }
}

private fun descendants(component: Component): List<Component> =
    listOf(component) + if (component is Container) {
        component.components.flatMap { descendants(it) }
    } else emptyList()

private fun awaitPage(
    dialog: JDialog, details: JLabel, preview: JLabel, page: Int, mode: String,
) {
    val deadline = System.nanoTime() + 10_000_000_000L
    while (System.nanoTime() < deadline) {
        val passed = onSwingThread {
            dialog.isDisplayable && details.text.contains("page $page / 3") &&
                details.text.contains(mode) && preview.icon is ImageIcon
        }
        if (passed) return
        Thread.sleep(30)
    }
    val state = onSwingThread { details.text + " / icon=" + (preview.icon != null) }
    error("Timed out waiting for saved page $page ($mode): $state")
}

/**
 * Check actual post-resize rendered pixels, not only a stale label from the
 * previous window size. The viewport event must have reached the viewer.
 */
private fun awaitFitInsideViewport(scroll: JScrollPane, preview: JLabel) {
    val deadline = System.nanoTime() + 10_000_000_000L
    while (System.nanoTime() < deadline) {
        val passed = onSwingThread {
            val extent = scroll.viewport.extentSize
            val icon = preview.icon as? ImageIcon
            icon != null && extent.width > 50 && extent.height > 50 &&
                icon.iconWidth <= (extent.width - 8).coerceAtLeast(1) &&
                icon.iconHeight <= (extent.height - 8).coerceAtLeast(1)
        }
        if (passed) return
        Thread.sleep(30)
    }
    val sizes = onSwingThread {
        "viewport=" + scroll.viewport.extentSize + " icon=" +
            (preview.icon as? ImageIcon)?.let { Dimension(it.iconWidth, it.iconHeight) }
    }
    error("Fit-to-window failed after dialog resize: $sizes")
}

private fun <T> onSwingThread(block: () -> T): T {
    if (SwingUtilities.isEventDispatchThread()) return block()
    val result = AtomicReference<T>()
    val failure = AtomicReference<Throwable?>()
    SwingUtilities.invokeAndWait {
        try { result.set(block()) } catch (t: Throwable) { failure.set(t) }
    }
    failure.get()?.let { throw it }
    return result.get()
}
