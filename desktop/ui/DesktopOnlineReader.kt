package dev.nooir.desktop.ui

import dev.nooir.desktop.DesktopImageDecoder
import dev.nooir.desktop.suwayomi.SuwayomiClient
import dev.nooir.desktop.suwayomi.SuwayomiOnlineChapter
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Point
import java.awt.event.ActionEvent
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.image.BufferedImage
import javax.swing.AbstractAction
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSpinner
import javax.swing.KeyStroke
import javax.swing.SpinnerNumberModel
import javax.swing.SwingWorker

/** Native single-page online reading, separate from local CBZ/storage and export. */
internal class DesktopOnlineReader(
    private val client: SuwayomiClient,
    private val chapter: SuwayomiOnlineChapter,
    mangaTitle: String,
    chapterName: String,
) : JFrame("MihonNooir Online Reader — ${mangaTitle.take(100)} / ${chapterName.take(80)}") {
    private data class Request(val generation: Long, val page: Int)
    private val gate = LatestPageLoadGate<Request>()
    private val canvas = ReaderCanvas()
    private val scroller = JScrollPane(canvas)
    private val previous = JButton("Previous")
    private val next = JButton("Next")
    private val retry = JButton("Retry")
    private val close = JButton("Close reader")
    private val jump = JSpinner(SpinnerNumberModel(1, 1, chapter.pageCount, 1))
    private val go = JButton("Go")
    private val fit = JComboBox(arrayOf("Fit page", "Fit width", "Original pixels"))
    private val zoom = JSpinner(SpinnerNumberModel(100, 50, 200, 10))
    private val rtl = JCheckBox("Right to left", true)
    private val resume = JButton("Resume server position")
    private val status = JLabel("Opening page…")
    private var activeImage: BufferedImage? = null
    private var index = 0
    private var generation = 0L
    private var disposed = false

    init {
        defaultCloseOperation = DISPOSE_ON_CLOSE
        DesktopWindowSizing.fit(this, Dimension(1050, 760), Dimension(480, 360))
        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 5, 3)).apply {
            add(previous); add(next); add(JLabel("Page")); add(jump); add(go); add(retry)
            add(fit); add(JLabel("Zoom %")); add(zoom); add(rtl); add(close)
        }
        add(DesktopWindowSizing.toolbar(toolbar), BorderLayout.NORTH)
        add(scroller, BorderLayout.CENTER)
        val conflict = chapter.syncConflict
        val note = when {
            conflict != null -> "Server sync conflict: positions differ. Read from page 1 or jump manually; this reader does not resolve sync."
            chapter.lastPageRead > 0 -> "Server has a saved position. Online reading here does not write progress yet."
            else -> "Online session — no local CBZ and no progress write-back yet."
        }
        add(JPanel(BorderLayout()).apply {
            add(status, BorderLayout.NORTH)
            add(DesktopWindowSizing.toolbar(JPanel(FlowLayout(FlowLayout.LEFT, 5, 3)).apply {
                add(JLabel(note)); if (conflict == null && chapter.lastPageRead > 0) add(resume)
            }), BorderLayout.SOUTH)
        }, BorderLayout.SOUTH)
        scroller.verticalScrollBar.unitIncrement = 24
        scroller.horizontalScrollBar.unitIncrement = 24
        scroller.viewport.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent?) { paintPage() }
        })
        previous.addActionListener { requestPage(index-1) }
        next.addActionListener { requestPage(index+1) }
        retry.addActionListener { requestPage(index) }
        go.addActionListener {
            try { jump.commitEdit(); requestPage((jump.value as Int)-1) }
            catch (_: Exception) { status.text = "Enter a valid page number." }
        }
        fit.addActionListener { paintPage() }
        zoom.addChangeListener { paintPage() }
        rtl.addActionListener { paintPage() }
        resume.addActionListener { requestPage(chapter.lastPageRead.coerceIn(0,chapter.pageCount-1)) }
        close.addActionListener { dispose() }
        bind("LEFT", "reader-left") { requestPage(index + if (rtl.isSelected) 1 else -1) }
        bind("RIGHT", "reader-right") { requestPage(index + if (rtl.isSelected) -1 else 1) }
        bind("PAGE_DOWN", "reader-next") { requestPage(index+1) }
        bind("PAGE_UP", "reader-previous") { requestPage(index-1) }
        bind("ESCAPE", "reader-close") { dispose() }
        addWindowListener(object : WindowAdapter() {
            override fun windowClosed(e: WindowEvent?) {
                disposed = true
                gate.close()
                generation++
                releaseImage()
            }
        })
        requestPage(0)
    }

    private fun bind(key: String, name: String, action: () -> Unit) {
        rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key), name)
        rootPane.actionMap.put(name, object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) { action() }
        })
    }

    private fun releaseImage() {
        val prior = activeImage
        activeImage = null
        paintPage()
        prior?.flush()
    }

    private fun paintPage() {
        canvas.configure(listOfNotNull(activeImage), rtl.isSelected,
            fit.selectedItem as String, zoom.value as Int, scroller.viewport.extentSize)
    }

    private fun requestPage(target: Int) {
        if (disposed || target !in 0 until chapter.pageCount) return
        index = target
        releaseImage()
        val request = Request(++generation, target)
        jump.value = target+1
        previous.isEnabled = target > 0
        next.isEnabled = target < chapter.pageCount-1
        retry.isEnabled = false
        status.text = "Loading page ${target+1} / ${chapter.pageCount}…"
        gate.submit(request)?.let { startLoad(it) }
    }

    private fun startLoad(request: Request) {
        object : SwingWorker<BufferedImage, Unit>() {
            override fun doInBackground(): BufferedImage =
                DesktopImageDecoder.decode(client.onlinePage(chapter, request.page))

            override fun done() {
                val current = !disposed && isDisplayable && request.generation == generation
                try {
                    val image = get()
                    if (current) {
                        activeImage = image
                        paintPage()
                        scroller.viewport.viewPosition = Point(0,0)
                        status.text = "Page ${request.page+1} / ${chapter.pageCount}"
                    } else image.flush()
                } catch (e: Exception) {
                    if (current) {
                        status.text = "Page ${request.page+1} failed — Retry or navigate. " +
                            (e.cause?.message ?: e.message ?: "").take(140)
                        retry.isEnabled = true
                    }
                } finally {
                    // Do not release the active slot early on cancel. Only the
                    // completed worker starts a replacement; no decoder pile-up.
                    gate.completed()?.let { if (!disposed && isDisplayable) startLoad(it) }
                }
            }
        }.execute()
    }
}
