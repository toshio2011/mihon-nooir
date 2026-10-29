package dev.nooir.desktop.ui

import dev.nooir.bridge.NaturalPageOrder
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
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

private class LocalComicWindow(
    private val source: File,
    private val pages: List<String>,
) : JFrame("MihonNooir Reader — ${source.name}") {
    private val bookmarks = Preferences.userRoot().node("dev/nooir/mihonnooir/reader")
    private val bookmarkId = MessageDigest.getInstance("SHA-256")
        .digest(source.canonicalPath.toByteArray(Charsets.UTF_8))
        .take(24).joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    private var pageIndex = bookmarks.getInt(bookmarkId, 0).coerceIn(0, pages.lastIndex)
    private var currentImage: BufferedImage? = null
    private val pageLabel = JLabel("Loading...", JLabel.CENTER)
    private val scroller = JScrollPane(pageLabel)
    private val status = JLabel()
    private val rtl = JCheckBox("RTL arrows", true)
    private val sizing = JComboBox(arrayOf("Fit page", "Fit width", "Original pixels"))
    private val zoom = JSlider(50, 200, 100)
    private val previous = JButton("Previous")
    private val next = JButton("Next")
    private var generation = 0

    init {
        defaultCloseOperation = DISPOSE_ON_CLOSE
        minimumSize = Dimension(600, 440)
        setSize(1000, 760)
        setLocationRelativeTo(null)
        pageLabel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        add(scroller, BorderLayout.CENTER)
        val controls = JPanel().apply {
            add(previous); add(next); add(rtl); add(sizing); add(JLabel("Zoom")); add(zoom)
        }
        add(controls, BorderLayout.NORTH)
        add(status, BorderLayout.SOUTH)
        previous.addActionListener { move(-1) }
        next.addActionListener { move(1) }
        sizing.addActionListener { repaintPage() }
        zoom.addChangeListener { if (!zoom.valueIsAdjusting) repaintPage() }
        scroller.viewport.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent?) = repaintPage()
        })
        bindKey("RIGHT", "next-or-prev") { move(if (rtl.isSelected) -1 else 1) }
        bindKey("LEFT", "prev-or-next") { move(if (rtl.isSelected) 1 else -1) }
        bindKey("PAGE_DOWN", "next-page") { move(1) }
        bindKey("PAGE_UP", "previous-page") { move(-1) }
        bindKey("SPACE", "space-next") { move(1) }
        showPage()
    }

    private fun bindKey(key: String, name: String, action: () -> Unit) {
        rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key), name)
        rootPane.actionMap.put(name, object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) = action()
        })
    }

    private fun move(delta: Int) {
        val nextIndex = (pageIndex + delta).coerceIn(0, pages.lastIndex)
        if (nextIndex == pageIndex) return
        pageIndex = nextIndex
        bookmarks.putInt(bookmarkId, pageIndex)
        showPage()
    }

    private fun showPage() {
        val index = pageIndex
        val selected = pages[index]
        val request = ++generation
        currentImage = null
        pageLabel.icon = null
        pageLabel.text = "Loading page ${index + 1}..."
        status.text = "Page ${index + 1} / ${pages.size} — ${selected.take(100)}"
        previous.isEnabled = index > 0
        next.isEnabled = index < pages.lastIndex
        object : SwingWorker<BufferedImage, Unit>() {
            override fun doInBackground(): BufferedImage = LocalComicPages.read(source, selected)
            override fun done() {
                if (!this@LocalComicWindow.isDisplayable || request != generation) return
                try {
                    currentImage = get()
                    repaintPage()
                } catch (e: Exception) {
                    pageLabel.text = "Cannot read this page. Try Next or Previous."
                    JOptionPane.showMessageDialog(this@LocalComicWindow,
                        e.cause?.message ?: e.message ?: "Page load failed",
                        "MihonNooir Reader", JOptionPane.WARNING_MESSAGE)
                }
            }
        }.execute()
    }

    private fun repaintPage() {
        val src = currentImage ?: return
        val viewport = scroller.viewport.extentSize
        if (viewport.width <= 0 || viewport.height <= 0) return
        val factor = when (sizing.selectedItem as String) {
            "Fit width" -> viewport.width.toDouble() / src.width
            "Original pixels" -> 1.0
            else -> minOf(viewport.width.toDouble() / src.width,
                viewport.height.toDouble() / src.height)
        } * (zoom.value / 100.0)
        val width = (src.width * factor).toInt().coerceIn(1, 12_000)
        val height = (src.height * factor).toInt().coerceIn(1, 12_000)
        val scaled = src.getScaledInstance(width, height, Image.SCALE_SMOOTH)
        pageLabel.icon = ImageIcon(scaled)
        pageLabel.text = ""
    }
}
