package dev.nooir.desktop.ui

import dev.nooir.bridge.XtcBookPreview
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dialog
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.image.BufferedImage
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JCheckBox
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
 * Pure viewport math and palette-preserving display-only scaling.
 * 1:1 mode never touches saved pixels. Fit mode never enlarges small
 * artwork or interpolates new gray levels into an XTC/XTCH page.
 */
internal object DesktopXtcPreviewSizing {
    fun dialogSize(screen: Dimension): Dimension {
        require(screen.width > 0 && screen.height > 0)
        return Dimension(
            minOf(710, (screen.width - 48).coerceAtLeast(1)),
            minOf(900, (screen.height - 96).coerceAtLeast(1)),
        )
    }

    fun displaySize(
        nativeWidth: Int, nativeHeight: Int,
        viewportWidth: Int, viewportHeight: Int, fit: Boolean,
    ): Dimension {
        require(nativeWidth > 0 && nativeHeight > 0)
        if (!fit || viewportWidth <= 0 || viewportHeight <= 0) {
            return Dimension(nativeWidth, nativeHeight)
        }
        val factor = minOf(
            1.0,
            viewportWidth.toDouble() / nativeWidth,
            viewportHeight.toDouble() / nativeHeight,
        )
        return Dimension(
            (nativeWidth * factor).toInt().coerceAtLeast(1),
            (nativeHeight * factor).toInt().coerceAtLeast(1),
        )
    }

    fun nearestNeighbor(source: BufferedImage, size: Dimension): BufferedImage {
        require(size.width > 0 && size.height > 0)
        if (source.width == size.width && source.height == size.height) return source
        val target = BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_RGB)
        val g: Graphics2D = target.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
            g.drawImage(source, 0, 0, size.width, size.height, null)
        } finally {
            g.dispose()
        }
        return target
    }
}

/**
 * Inspect finished XTC/XTCH pages without re-encoding or uploading the book.
 * The existing first-page conversion preview is a separate workflow.
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
        val bounds = parent.graphicsConfiguration?.bounds
            ?: dialog.graphicsConfiguration.bounds
        val initialSize = DesktopXtcPreviewSizing.dialogSize(bounds.size)
        dialog.minimumSize = Dimension(minOf(550, initialSize.width), minOf(480, initialSize.height))
        dialog.size = initialSize
        dialog.setLocationRelativeTo(parent)

        var pageIndex = 0
        var generation = 0
        var worker: SwingWorker<BufferedImage, Unit>? = null
        var nativeFrame: BufferedImage? = null
        var displayFrame: BufferedImage? = null
        var displayedSize: Dimension? = null
        var displayedFit: Boolean? = null

        val preview = JLabel("Reading saved binary page…", SwingConstants.CENTER)
        val scroll = JScrollPane(preview)
        val details = JLabel("", SwingConstants.CENTER)
        val prev = JButton("Previous")
        val next = JButton("Next")
        val fit = JCheckBox("Fit to window (display only)", true)

        fun clearFrames() {
            preview.icon = null
            if (displayFrame != null && displayFrame !== nativeFrame) displayFrame?.flush()
            nativeFrame?.flush()
            displayFrame = null
            nativeFrame = null
            displayedSize = null
            displayedFit = null
        }
        fun showFrame() {
            val frame = nativeFrame ?: return
            val size = DesktopXtcPreviewSizing.displaySize(
                frame.width, frame.height,
                (scroll.viewport.extentSize.width - 8).coerceAtLeast(1),
                (scroll.viewport.extentSize.height - 8).coerceAtLeast(1),
                fit.isSelected,
            )
            if (size == displayedSize && displayedFit == fit.isSelected && preview.icon != null) return
            val shown = DesktopXtcPreviewSizing.nearestNeighbor(frame, size)
            preview.icon = ImageIcon(shown)
            preview.text = ""
            if (displayFrame != null && displayFrame !== frame && displayFrame !== shown) {
                displayFrame?.flush()
            }
            displayFrame = shown
            displayedSize = size
            displayedFit = fit.isSelected
            details.text = "${inspector.format} · page ${pageIndex + 1} / ${inspector.pageCount}" +
                " · saved ${frame.width} × ${frame.height} px" +
                (if (fit.isSelected) " · fitted display" else " · 1:1 pixels")
        }
        fun loadPage() {
            val thisGeneration = ++generation
            worker?.cancel(true)
            worker = null
            clearFrames()
            val requestedIndex = pageIndex
            prev.isEnabled = requestedIndex > 0
            next.isEnabled = requestedIndex + 1 < inspector.pageCount
            details.text = "${inspector.format} · page ${requestedIndex + 1} / ${inspector.pageCount}"
            preview.text = "Decoding saved page ${requestedIndex + 1}…"
            val task = object : SwingWorker<BufferedImage, Unit>() {
                override fun doInBackground(): BufferedImage {
                    val page = inspector.page(requestedIndex)
                    if (isCancelled) throw java.util.concurrent.CancellationException()
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
                    if (!dialog.isDisplayable || generation != thisGeneration || isCancelled) return
                    try {
                        nativeFrame = get()
                        showFrame()
                    } catch (e: Exception) {
                        preview.icon = null
                        preview.text = "Page cannot be decoded; source file unchanged"
                        details.text += " · " + (e.cause?.message ?: e.message ?: "Invalid data").take(100)
                    }
                }
            }
            worker = task
            task.execute()
        }

        prev.addActionListener { if (pageIndex > 0) { pageIndex--; loadPage() } }
        next.addActionListener {
            if (pageIndex + 1 < inspector.pageCount) { pageIndex++; loadPage() }
        }
        fit.addActionListener { showFrame() }
        scroll.viewport.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                if (fit.isSelected) showFrame()
            }
        })
        dialog.addWindowListener(object : WindowAdapter() {
            override fun windowClosed(e: WindowEvent) {
                ++generation
                worker?.cancel(true)
                worker = null
                clearFrames()
            }
        })

        dialog.add(details, BorderLayout.NORTH)
        dialog.add(scroll, BorderLayout.CENTER)
        dialog.add(JPanel(FlowLayout(FlowLayout.CENTER)).apply {
            add(prev); add(fit); add(next)
        }, BorderLayout.SOUTH)
        dialog.isVisible = true
        loadPage()
    }
}
