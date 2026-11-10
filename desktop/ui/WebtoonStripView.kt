package dev.nooir.desktop.ui

import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.RenderingHints
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.ExecutionException
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.SwingUtilities
import javax.swing.SwingWorker

/**
 * Continuous vertical reader with a rolling 1–3 page buffer.
 *
 * No network access; no image decoding on the Swing event thread.
 * Old images are released when the window slides. Long pages are drawn
 * directly at view width instead of allocating full-chapter scaled bitmaps.
 */
internal class WebtoonStripView(
    private val source: File,
    private val pageNames: List<String>,
    private val onVisiblePage: (Int) -> Unit,
    private val onError: (String) -> Unit,
) : JPanel(BorderLayout()) {
    private val canvas = StripCanvas()
    private val pane = JScrollPane(canvas)
    private var active = true
    private var loading = false
    private var startIndex = 0
    private var decodedCount = 0
    private var generation = 0
    private var pendingJump: Int? = null
    private var previousScrollY = 0
    private var suppressScrolling = false

    init {
        require(pageNames.isNotEmpty() && pageNames.size <= 5_000)
        pane.verticalScrollBar.unitIncrement = 70
        pane.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        add(pane, BorderLayout.CENTER)
        pane.viewport.addChangeListener { viewportChanged() }
        pane.viewport.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent?) {
                if (!loading && decodedCount > 0) {
                    canvas.updateWidth(pane.viewport.extentSize.width)
                }
            }
        })
    }

    fun close() {
        active = false
        generation++
        pendingJump = null
        canvas.clear()
    }

    fun open(pageIndex: Int) {
        require(pageIndex in pageNames.indices)
        // A fast page jump while the next strip is decoding must not get lost.
        // Apply only the most recent requested page after the in-flight load.
        if (loading) {
            pendingJump = pageIndex
            return
        }
        load(pageIndex, Shift.INITIAL)
    }

    private enum class Shift { INITIAL, FORWARD, BACKWARD }

    private fun load(start: Int, shift: Shift) {
        if (!active || loading || start !in pageNames.indices) return
        loading = true
        val request = ++generation
        val beforeY = pane.viewport.viewPosition.y
        val removed = if (shift == Shift.FORWARD) canvas.slotHeight(0) else 0
        object : SwingWorker<List<BufferedImage>, Unit>() {
            override fun doInBackground(): List<BufferedImage> {
                val images = mutableListOf<BufferedImage>()
                var pixels = 0L
                for (i in WebtoonWindow.indices(pageNames.size, start)) {
                    if (isCancelled) break
                    val decoded = LocalComicPages.read(source, pageNames[i])
                    val area = decoded.width.toLong() * decoded.height
                    if (images.isNotEmpty() &&
                        pixels + area > WebtoonWindow.MAX_DECODED_PIXELS) break
                    pixels += area
                    images.add(decoded)
                }
                return images
            }

            override fun done() {
                if (!active || generation != request) return
                try {
                    val images = get()
                    check(images.isNotEmpty()) { "No readable webtoon image could be loaded" }
                    suppressScrolling = true
                    startIndex = start
                    decodedCount = images.size
                    canvas.setImages(images, pane.viewport.extentSize.width)
                    val add = if (shift == Shift.BACKWARD) canvas.slotHeight(0) else 0
                    val requested = when (shift) {
                        Shift.INITIAL -> 0
                        Shift.FORWARD -> beforeY - removed
                        Shift.BACKWARD -> beforeY + add
                    }
                    SwingUtilities.invokeLater {
                        if (!active || generation != request) return@invokeLater
                        val maxY = (canvas.preferredSize.height -
                            pane.viewport.extentSize.height).coerceAtLeast(0)
                        val y = requested.coerceIn(0, maxY)
                        pane.viewport.viewPosition = Point(0, y)
                        previousScrollY = y
                        suppressScrolling = false
                        loading = false
                        reportVisiblePage()
                        val jump = pendingJump
                        pendingJump = null
                        if (jump != null && jump != startIndex) {
                            load(jump, Shift.INITIAL)
                        }
                    }
                } catch (e: Exception) {
                    suppressScrolling = false
                    loading = false
                    val cause = if (e is ExecutionException) e.cause ?: e else e
                    onError(cause.message ?: "Cannot display webtoon images")
                    val jump = pendingJump
                    pendingJump = null
                    if (jump != null && jump != startIndex) {
                        load(jump, Shift.INITIAL)
                    }
                }
            }
        }.execute()
    }

    private fun reportVisiblePage() {
        if (!active || decodedCount == 0) return
        val y = pane.viewport.viewPosition.y +
            pane.viewport.extentSize.height.coerceAtLeast(0) / 3
        val page = WebtoonWindow.atOffset(startIndex, canvas.slotHeights(), y)
        onVisiblePage(page.coerceIn(0, pageNames.lastIndex))
    }

    private fun viewportChanged() {
        if (!active || loading || suppressScrolling || decodedCount == 0) return
        val y = pane.viewport.viewPosition.y
        val delta = y - previousScrollY
        previousScrollY = y
        reportVisiblePage()
        if (delta > 0 && y + pane.viewport.extentSize.height >=
            canvas.preferredSize.height - 160) {
            val next = WebtoonWindow.nextStart(pageNames.size, startIndex, decodedCount)
            if (next != null) load(next, Shift.FORWARD)
        } else if (delta < 0 && y <= 160) {
            val prev = WebtoonWindow.previousStart(startIndex)
            if (prev != null) load(prev, Shift.BACKWARD)
        }
    }

    private class StripCanvas : JComponent() {
        private var images: List<BufferedImage> = emptyList()
        private var heightSlots: List<Int> = emptyList()
        private var displayWidths: List<Int> = emptyList()
        private val margin = 10
        private val gap = 8

        fun clear() {
            images = emptyList()
            heightSlots = emptyList()
            displayWidths = emptyList()
            preferredSize = Dimension(1, 1)
            revalidate()
            repaint()
        }

        fun setImages(next: List<BufferedImage>, viewportWidth: Int) {
            images = next
            updateWidth(viewportWidth)
        }

        fun slotHeight(index: Int): Int = heightSlots.getOrNull(index) ?: 0
        fun slotHeights(): List<Int> = heightSlots

        fun updateWidth(viewportWidth: Int) {
            if (images.isEmpty()) return
            val width = (viewportWidth - margin * 2).coerceIn(1, 12_000)
            displayWidths = images.map { img ->
                minOf(width, maxOf(1, (img.width.toLong() * 4)
                    .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()))
            }
            heightSlots = images.mapIndexed { index, img ->
                val w = displayWidths[index]
                val scaled = (img.height.toDouble() * w / img.width)
                    .toInt().coerceIn(1, 16_000)
                scaled + gap
            }
            preferredSize = Dimension(width + margin * 2,
                (heightSlots.sum().toLong() + margin * 2).coerceAtMost(60_000L).toInt())
            revalidate()
            repaint()
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2 = g.create() as Graphics2D
            try {
                g2.color = Color(32, 32, 35)
                g2.fillRect(0, 0, width, height)
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                var y = margin
                for ((index, img) in images.withIndex()) {
                    val w = displayWidths[index]
                    val h = heightSlots[index] - gap
                    val x = (width - w) / 2
                    if (g2.clipBounds?.intersects(x.toDouble(), y.toDouble(),
                            w.toDouble(), h.toDouble()) != false) {
                        g2.drawImage(img, x, y, w, h, null)
                    }
                    y += heightSlots[index]
                }
            } finally {
                g2.dispose()
            }
        }
    }
}
