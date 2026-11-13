package dev.nooir.desktop.ui

import dev.nooir.bridge.XtcBookPreview
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dialog
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.image.BufferedImage
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.SwingWorker
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Read an existing finished XTC/XTCH book without converting or uploading it.
 * The stored planes are decoded one page at a time off the Swing event thread.
 * This is not the old CBZ reader or Android's pre-encode first-page preview.
 */
internal object DesktopXtcBinaryPreview {
    fun open(parent: Component) {
        val chooser = JFileChooser().apply {
            dialogTitle = "Choose an already exported XTC / XTCH book"
            fileFilter = FileNameExtensionFilter("XTC and XTCH e-ink books", "xtc", "xtch")
        }
        if (chooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) return
        val selected = chooser.selectedFile
        val inspector = runCatching { XtcBookPreview(selected) }.getOrElse {
            JOptionPane.showMessageDialog(parent,
                "Cannot read saved XTC/XTCH book: " + (it.message ?: "Invalid header"),
                "Binary book preview", JOptionPane.ERROR_MESSAGE)
            return
        }
        val dialog = JDialog(SwingUtilities.getWindowAncestor(parent),
            "Saved ${inspector.format} — actual output pixels", Dialog.ModalityType.MODELESS)
        dialog.defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
        dialog.minimumSize = Dimension(550, 480)
        dialog.setSize(710, 900)
        dialog.setLocationRelativeTo(parent)
        var pageIndex = 0
        var generation = 0
        val preview = JLabel("Reading saved binary page…", SwingConstants.CENTER)
        val details = JLabel("")
        val prev = JButton("Previous")
        val next = JButton("Next")
        fun loadPage() {
            val thisGeneration = ++generation
            val requestedIndex = pageIndex
            prev.isEnabled = requestedIndex > 0
            next.isEnabled = requestedIndex + 1 < inspector.pageCount
            details.text = "${inspector.format} · page ${requestedIndex + 1} / ${inspector.pageCount} · saved binary pixels"
            preview.text = "Decoding page ${requestedIndex + 1}…"
            preview.icon = null
            object : SwingWorker<BufferedImage, Unit>() {
                override fun doInBackground(): BufferedImage {
                    val page = inspector.page(requestedIndex)
                    val image = BufferedImage(page.width, page.height, BufferedImage.TYPE_INT_RGB)
                    val pixels = IntArray(page.levels.size) { i ->
                        val brightness = (page.levels[i].toInt() and 255) *
                            (if (page.depth == 1) 255 else 85)
                        (brightness shl 16) or (brightness shl 8) or brightness
                    }
                    image.setRGB(0, 0, page.width, page.height, pixels, 0, page.width)
                    return image
                }
                override fun done() {
                    if (!dialog.isDisplayable || generation != thisGeneration) return
                    try {
                        val frame = get()
                        preview.icon = ImageIcon(frame)
                        preview.text = ""
                        details.text += " · ${frame.width} × ${frame.height} native"
                    } catch (e: Exception) {
                        preview.icon = null
                        preview.text = "Page cannot be decoded; source file unchanged"
                        details.text += " · " + (e.cause?.message ?: e.message ?: "Invalid data").take(100)
                    }
                }
            }.execute()
        }
        prev.addActionListener { if (pageIndex > 0) { pageIndex--; loadPage() } }
        next.addActionListener {
            if (pageIndex + 1 < inspector.pageCount) { pageIndex++; loadPage() }
        }
        val controls = JPanel(FlowLayout(FlowLayout.CENTER)).apply {
            add(prev); add(details); add(next)
        }
        dialog.add(JScrollPane(preview), BorderLayout.CENTER)
        dialog.add(controls, BorderLayout.SOUTH)
        dialog.isVisible = true
        loadPage()
    }
}
