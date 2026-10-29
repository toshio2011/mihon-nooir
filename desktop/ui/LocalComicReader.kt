package dev.nooir.desktop.ui

import dev.nooir.bridge.NaturalPageOrder
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Color
import java.awt.Point
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Image
import java.awt.RenderingHints
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.prefs.Preferences
import java.util.zip.ZipFile
import javax.imageio.ImageIO
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSlider
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel
import javax.swing.KeyStroke
import javax.swing.SwingWorker

/** Local Windows reader: no Suwayomi, network, manga download or OCR required. */
internal object LocalComicPages {
    private val extensions = setOf("png", "jpg", "jpeg", "bmp", "gif", "webp")
    private const val maxPages = 5_000
    private const val maxBytes = 25 * 1024 * 1024
    private const val maxPixels = 25_000_000L

    fun names(source: File): List<String> {
        require(source.isDirectory || source.isFile && source.extension.lowercase() in setOf("cbz", "zip")) {
            "Choose a comic image folder or CBZ/ZIP archive"
        }
        val items = if (source.isDirectory) {
            val root = source.canonicalFile
            root.walkTopDown().filter { it.isFile && it.extension.lowercase() in extensions &&
                it.canonicalFile.toPath().startsWith(root.toPath()) }
                .map { it.relativeTo(root).invariantSeparatorsPath }.toList()
        } else {
            ZipFile(source).use { zip ->
                zip.entries().asSequence().filter { !it.isDirectory &&
                    !it.name.startsWith("/") && !it.name.contains("\\") &&
                    it.name.split('/').none { part -> part == ".." } &&
                    it.name.substringAfterLast('.').lowercase() in extensions }
                    .map { it.name }.toList()
            }
        }
        require(items.isNotEmpty()) { "No readable comic images found" }
        require(items.size <= maxPages) { "Too many pages in this comic" }
        return items.sortedWith(NaturalPageOrder::compare)
    }

    private fun boundedRead(stream: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var bytes = 0
        while (true) {
            val n = stream.read(buffer)
            if (n < 0) break
            bytes += n
            require(bytes <= maxBytes) { "Page image exceeds 25 MiB limit" }
            output.write(buffer, 0, n)
        }
        return output.toByteArray()
    }

    fun read(source: File, relativePath: String): BufferedImage {
        val bytes = if (source.isDirectory) {
            val canonicalRoot = source.canonicalFile
            val page = File(canonicalRoot, relativePath).canonicalFile
            require(page.toPath().startsWith(canonicalRoot.toPath())) { "Invalid page path" }
            require(page.isFile && page.length() in 1..maxBytes.toLong()) { "Invalid page image size" }
            page.inputStream().use { boundedRead(it) }
        } else ZipFile(source).use { zip ->
            require(relativePath.isNotBlank() && !relativePath.startsWith("/") &&
                !relativePath.contains("\\") &&
                relativePath.split('/').none { it == ".." } &&
                relativePath.substringAfterLast('.').lowercase() in extensions) {
                "Invalid archive page path"
            }
            val entry = zip.getEntry(relativePath) ?: error("Comic page is missing")
            require(!entry.isDirectory && entry.size <= maxBytes) { "Invalid archive image size" }
            zip.getInputStream(entry).use { boundedRead(it) }
        }
        val decoded = ImageIO.read(ByteArrayInputStream(bytes))
            ?: error("This page image format is not supported by the installed Java image codecs")
        require(decoded.width > 0 && decoded.height > 0 &&
            decoded.width.toLong() * decoded.height <= maxPixels) { "Page resolution is too large" }
        return decoded
    }
}

internal object LocalComicReader {
    fun open(source: File, parent: Component?): Boolean =
        try {
            val pages = LocalComicPages.names(source)
            LocalComicWindow(source, pages).isVisible = true
            true
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(parent, e.message ?: "Cannot open comic",
                "MihonNooir Reader", JOptionPane.WARNING_MESSAGE)
            false
        }
}

/**
 * Painting happens on demand without creating gigantic scaled preview images.
 * At most two source pages are kept decoded for a single active spread.
 */
private class ReaderCanvas : JComponent() {
    private var pages: List<BufferedImage> = emptyList()
    private var visualRightToLeft = true
    private var scale = 1.0
    private val gap = 12
    private val padding = 10

    fun configure(
        decoded: List<BufferedImage>, rtl: Boolean,
        fit: String, zoomPercent: Int, viewport: Dimension,
    ) {
        pages = decoded
        visualRightToLeft = rtl
        if (decoded.isEmpty()) {
            preferredSize = Dimension(maxOf(1, viewport.width), maxOf(1, viewport.height))
            revalidate()
            repaint()
            return
        }
        val naturalWidth = decoded.sumOf { it.width.toLong() } + (decoded.size - 1) * gap
        val naturalHeight = decoded.maxOf { it.height }
        val vw = maxOf(1, viewport.width - 2 * padding)
        val vh = maxOf(1, viewport.height - 2 * padding)
        val baseScale = when (fit) {
            "Fit width" -> vw.toDouble() / naturalWidth
            "Original pixels" -> 1.0
            else -> minOf(vw.toDouble() / naturalWidth, vh.toDouble() / naturalHeight)
        }
        // Avoid allocating extreme window surfaces, even for unusual zooms.
        scale = minOf(baseScale * (zoomPercent.coerceIn(50, 200) / 100.0),
            12_000.0 / naturalWidth, 12_000.0 / naturalHeight)
        val w = (naturalWidth * scale).toInt().coerceIn(1, 12_000) + padding * 2
        val h = (naturalHeight * scale).toInt().coerceIn(1, 12_000) + padding * 2
        val size = Dimension(w, h)
        if (preferredSize != size) {
            preferredSize = size
            revalidate()
        }
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
            val ordered = if (visualRightToLeft) pages.reversed() else pages
            var x = padding
            for (img in ordered) {
                val w = (img.width * scale).toInt().coerceAtLeast(1)
                val h = (img.height * scale).toInt().coerceAtLeast(1)
                val y = padding + ((height - 2 * padding - h) / 2).coerceAtLeast(0)
                g2.drawImage(img, x, y, w, h, null)
                x += w + (gap * scale).toInt().coerceAtLeast(1)
            }
        } finally {
            g2.dispose()
        }
    }
}

private class LocalComicWindow(
    private val source: File,
    private val pages: List<String>,
) : JFrame("MihonNooir Reader — ${source.name}") {
    private val bookmarks = Preferences.userRoot().node("dev/nooir/mihonnooir/reader")
    private val bookmarkId = MessageDigest.getInstance("SHA-256")
        .digest(source.canonicalPath.toByteArray(Charsets.UTF_8))
        .take(24).joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }

    private var pageIndex = bookmarks.getInt(bookmarkId, 0).coerceIn(0, pages.lastIndex)
    private var currentImages: List<BufferedImage> = emptyList()
    private val canvas = ReaderCanvas()
    private val scroller = JScrollPane(canvas)
    private val status = JLabel()
    private val rtl = JCheckBox("RTL", bookmarks.getBoolean("layout-rtl", true))
    private val twoPage = JCheckBox("Two pages", bookmarks.getBoolean("layout-two-page", false))
    private val firstAlone = JCheckBox("First page alone", bookmarks.getBoolean("layout-first-alone", true))
    private val sizing = JComboBox(arrayOf("Fit page", "Fit width", "Original pixels"))
    private val zoom = JSlider(50, 200, 100)
    private val jump = JSpinner(SpinnerNumberModel(pageIndex + 1, 1, pages.size, 1))
    private val previous = JButton("Previous")
    private val next = JButton("Next")
    private var generation = 0
    private var updatingJump = false

    init {
        defaultCloseOperation = DISPOSE_ON_CLOSE
        minimumSize = Dimension(600, 440)
        setSize(1080, 760)
        setLocationRelativeTo(null)
        add(scroller, BorderLayout.CENTER)
        val controls = JPanel().apply {
            add(previous); add(next)
            add(rtl); add(twoPage); add(firstAlone)
            add(sizing); add(JLabel("Zoom")); add(zoom)
            add(JLabel("Go to page")); add(jump)
        }
        add(controls, BorderLayout.NORTH)
        add(status, BorderLayout.SOUTH)
        previous.addActionListener { move(-1) }
        next.addActionListener { move(1) }
        rtl.addActionListener {
            bookmarks.putBoolean("layout-rtl", rtl.isSelected)
            repaintPage()
        }
        twoPage.addActionListener {
            bookmarks.putBoolean("layout-two-page", twoPage.isSelected)
            firstAlone.isEnabled = twoPage.isSelected
            // Recompute the currently displayed spread from the saved anchor.
            showPage()
        }
        firstAlone.isEnabled = twoPage.isSelected
        firstAlone.addActionListener {
            bookmarks.putBoolean("layout-first-alone", firstAlone.isSelected)
            showPage()
        }
        sizing.addActionListener { repaintPage() }
        zoom.addChangeListener { if (!zoom.valueIsAdjusting) repaintPage() }
        jump.addChangeListener {
            if (!updatingJump) {
                val requested = ((jump.value as Number).toInt() - 1).coerceIn(0, pages.lastIndex)
                if (requested != pageIndex) {
                    pageIndex = requested
                    bookmarks.putInt(bookmarkId, pageIndex)
                    showPage()
                }
            }
        }
        scroller.viewport.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent?) = repaintPage()
        })
        bindKey("RIGHT", "reader-right") { move(if (rtl.isSelected) -1 else 1) }
        bindKey("LEFT", "reader-left") { move(if (rtl.isSelected) 1 else -1) }
        bindKey("PAGE_DOWN", "reader-next") { move(1) }
        bindKey("PAGE_UP", "reader-previous") { move(-1) }
        bindKey("SPACE", "reader-space") { move(1) }
        bindKey("HOME", "reader-first") { jumpTo(0) }
        bindKey("END", "reader-last") { jumpTo(pages.lastIndex) }
        showPage()
    }

    private fun bindKey(key: String, name: String, action: () -> Unit) {
        rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke(key), name)
        rootPane.actionMap.put(name, object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) = action()
        })
    }

    private fun jumpTo(target: Int) {
        val newPage = target.coerceIn(0, pages.lastIndex)
        if (newPage != pageIndex) {
            pageIndex = newPage
            bookmarks.putInt(bookmarkId, pageIndex)
            showPage()
        }
    }

    private fun move(direction: Int) {
        jumpTo(DesktopReadingLayout.move(
            pages.size, pageIndex, direction, twoPage.isSelected, firstAlone.isSelected,
        ))
    }

    private fun showPage() {
        val indices = DesktopReadingLayout.visibleIndices(
            pages.size, pageIndex, twoPage.isSelected, firstAlone.isSelected,
        )
        // Persist the first page of a spread so a restart always resumes at
        // the same visually displayed pair, not the second half of a spread.
        pageIndex = indices.first()
        bookmarks.putInt(bookmarkId, pageIndex)
        val request = ++generation
        currentImages = emptyList()
        repaintPage()
        val first = indices.first() + 1
        val last = indices.last() + 1
        status.text = "Loading ${if (first == last) "page $first" else "pages $first–$last"} / ${pages.size}..."
        previous.isEnabled = pageIndex > 0
        next.isEnabled = indices.last() < pages.lastIndex
        updatingJump = true
        jump.value = pageIndex + 1
        updatingJump = false
        object : SwingWorker<List<BufferedImage>, Unit>() {
            override fun doInBackground(): List<BufferedImage> =
                indices.map { index -> LocalComicPages.read(source, pages[index]) }
            override fun done() {
                if (!this@LocalComicWindow.isDisplayable || request != generation) return
                try {
                    currentImages = get()
                    repaintPage()
                    status.text = "Page ${if (first == last) "$first" else "$first–$last"} / ${pages.size}" +
                        " — ${pages[indices.first()].take(90)}"
                    scroller.viewport.viewPosition = Point(0, 0)
                } catch (e: Exception) {
                    status.text = "Cannot read this page. Use Next or Previous."
                    JOptionPane.showMessageDialog(this@LocalComicWindow,
                        e.cause?.message ?: e.message ?: "Page load failed",
                        "MihonNooir Reader", JOptionPane.WARNING_MESSAGE)
                }
            }
        }.execute()
    }

    private fun repaintPage() {
        canvas.configure(currentImages, rtl.isSelected,
            sizing.selectedItem as String, zoom.value, scroller.viewport.extentSize)
    }
}
