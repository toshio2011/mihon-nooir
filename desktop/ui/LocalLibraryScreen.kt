package dev.nooir.desktop.ui

import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ExecutionException
import javax.swing.ImageIcon
import javax.swing.JSplitPane
import javax.swing.JTextField
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.BorderFactory
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ListSelectionModel
import javax.swing.SwingWorker

/**
 * Pure local-library presentation helpers shared by UI and headless JVM tests.
 * No network calls, implicit directory scans, or persistent picture cache.
 */
internal object LocalLibraryPresentation {
    fun filter(books: List<LocalLibraryBook>, query: String): List<LocalLibraryBook> {
        val needle = query.trim()
        if (needle.isEmpty()) return books
        return books.filter {
            it.title.contains(needle, ignoreCase = true) ||
                it.file.name.contains(needle, ignoreCase = true)
        }
    }

    fun cover(file: File, width: Int = 190, height: Int = 260): BufferedImage {
        require(width in 32..512 && height in 32..768) { "Invalid preview dimensions" }
        val pages = LocalComicPages.names(file)
        // A named cover beats page one, but no inferred network artwork is fetched.
        val preferred = pages.firstOrNull {
            it.substringAfterLast('/').substringBeforeLast('.')
                .equals("cover", ignoreCase = true)
        } ?: pages.first()
        val source = LocalComicPages.read(file, preferred)
        val result = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val graphics = result.createGraphics()
        try {
            graphics.color = Color(35, 37, 42)
            graphics.fillRect(0, 0, width, height)
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            val scale = minOf(width.toDouble() / source.width,
                height.toDouble() / source.height)
            val w = (source.width * scale).toInt().coerceAtLeast(1)
            val h = (source.height * scale).toInt().coerceAtLeast(1)
            graphics.drawImage(source, (width - w) / 2, (height - h) / 2,
                w, h, null)
        } finally {
            graphics.dispose()
            source.flush()
        }
        return result
    }
}

/** Offline-first, user-curated bookshelf. No auto scans, downloads or background server. */
internal object LocalLibraryScreen {
    fun open() {
        LocalLibraryWindow().isVisible = true
    }
}

private class LocalLibraryWindow : JFrame("MihonNooir — My local library") {
    private val store = LocalLibraryStore()
    private val model = DefaultListModel<LocalLibraryBook>()
    private val list = JList(model)
    private val search = JTextField(23)
    private val cover = JLabel("Select a comic to preview", JLabel.CENTER)
    private val previewStatus = JLabel("Cover preview is local and on demand")
    private var snapshot: List<LocalLibraryBook> = emptyList()
    private var previewRequest = 0L
    private var previewWorker: SwingWorker<BufferedImage, Unit>? = null
    private val add = JButton("Add comic")
    private val read = JButton("Read selected")
    private val remove = JButton("Remove from library")
    private val reload = JButton("Refresh")
    private val series = JButton("Downloaded series")
    private val resumeDownloaded = JButton("Continue last downloaded manga")
    private val information = JLabel("Choose a folder or CBZ comic to add")
    private val date = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
    private var busy = false

    init {
        defaultCloseOperation = DISPOSE_ON_CLOSE
        minimumSize = Dimension(580, 380)
        setSize(920, 580)
        setLocationRelativeTo(null)
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        list.cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                l: JList<*>?, value: Any?, index: Int,
                selected: Boolean, focus: Boolean,
            ): java.awt.Component {
                val label = super.getListCellRendererComponent(
                    l, value, index, selected, focus) as JLabel
                if (value is LocalLibraryBook) {
                    val visited = if (value.lastOpenedAt == 0L) "Not opened yet"
                        else "Last read: ${date.format(Instant.ofEpochMilli(value.lastOpenedAt))}"
                    label.text = "${if (value.available) "📖" else "⚠"}  ${value.title}  —  $visited"
                    label.toolTipText = value.file.path
                    label.border = BorderFactory.createEmptyBorder(7, 10, 7, 10)
                }
                return label
            }
        }
        val buttons = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            add(this@LocalLibraryWindow.add)
            add(this@LocalLibraryWindow.read)
            add(this@LocalLibraryWindow.remove)
            add(this@LocalLibraryWindow.reload)
            add(this@LocalLibraryWindow.series)
            add(this@LocalLibraryWindow.resumeDownloaded)
        }
        add(JPanel(BorderLayout()).apply {
            add(buttons, BorderLayout.NORTH)
            add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                add(JLabel("Find manga:"))
                add(search)
            }, BorderLayout.SOUTH)
        }, BorderLayout.NORTH)
        val coverPanel = JPanel(BorderLayout()).apply {
            preferredSize = Dimension(250, 350)
            border = BorderFactory.createEmptyBorder(12, 12, 12, 12)
            add(cover, BorderLayout.CENTER)
            add(previewStatus, BorderLayout.SOUTH)
        }
        add(JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
            JScrollPane(list), coverPanel).apply {
            resizeWeight = 0.74
            dividerLocation = 610
        }, BorderLayout.CENTER)
        add(information.apply {
            border = BorderFactory.createEmptyBorder(8, 12, 10, 12)
        }, BorderLayout.SOUTH)
        list.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                updateControls()
                loadPreview()
            }
        }
        search.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = showFilteredBooks()
            override fun removeUpdate(e: DocumentEvent?) = showFilteredBooks()
            override fun changedUpdate(e: DocumentEvent?) = showFilteredBooks()
        })
        addWindowListener(object : java.awt.event.WindowAdapter() {
            override fun windowClosed(e: java.awt.event.WindowEvent?) {
                previewRequest++
                previewWorker?.cancel(true)
            }
        })
        add.addActionListener { addComic() }
        read.addActionListener { readComic() }
        remove.addActionListener { removeComic() }
        reload.addActionListener { refresh() }
        series.addActionListener { OfflineSeriesScreen.open() }
        resumeDownloaded.addActionListener {
            if (OfflineSeriesScreen.resumeLast(this)) refresh()
        }
        refresh()
    }

    private fun updateControls() {
        add.isEnabled = !busy
        reload.isEnabled = !busy
        resumeDownloaded.isEnabled = !busy
        val selected = list.selectedValue
        read.isEnabled = !busy && selected?.available == true
        remove.isEnabled = !busy && selected != null
    }

    private fun refresh() {
        try {
            snapshot = store.books()
            showFilteredBooks()
        } catch (e: Exception) {
            information.text = "Cannot load local library"
            showError(e.message ?: "Library index could not be read")
        }
        updateControls()
    }

    private fun showFilteredBooks() {
        val selectedPath = list.selectedValue?.file?.path
        val visible = LocalLibraryPresentation.filter(snapshot, search.text)
        model.clear()
        visible.forEach(model::addElement)
        val index = visible.indexOfFirst { it.file.path == selectedPath }
        if (index >= 0) list.selectedIndex = index
        information.text = "${visible.size} of ${snapshot.size} local comic(s). " +
            "Offline search only; original files are never changed."
        updateControls()
        loadPreview()
    }

    private fun loadPreview() {
        previewRequest++
        val request = previewRequest
        previewWorker?.cancel(true)
        previewWorker = null
        cover.icon = null
        val selected = list.selectedValue
        if (selected == null) {
            cover.text = "Select a comic to preview"
            previewStatus.text = "No cover selected"
            return
        }
        if (!selected.available) {
            cover.text = "File missing"
            previewStatus.text = "Restore the file or remove this entry"
            return
        }
        cover.text = "Loading local cover..."
        previewStatus.text = "First page (or named cover) · offline"
        previewWorker = object : SwingWorker<BufferedImage, Unit>() {
            override fun doInBackground(): BufferedImage =
                LocalLibraryPresentation.cover(selected.file)

            override fun done() {
                if (request != previewRequest || isCancelled) return
                try {
                    cover.icon = ImageIcon(get())
                    cover.text = ""
                    previewStatus.text = "Preview from this comic · no network requests"
                } catch (e: Exception) {
                    val cause = if (e is ExecutionException) e.cause ?: e else e
                    cover.icon = null
                    cover.text = "Preview unavailable"
                    previewStatus.text = cause.message?.take(75)
                        ?: "Unsupported or unreadable cover image"
                }
            }
        }.also { it.execute() }
    }

    private fun addComic() {
        val chooser = JFileChooser().apply {
            dialogTitle = "Add an existing comic folder or CBZ"
            fileSelectionMode = JFileChooser.FILES_AND_DIRECTORIES
        }
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return
        val selected = chooser.selectedFile
        busy = true
        information.text = "Checking the selected comic..."
        updateControls()
        object : SwingWorker<Unit, Unit>() {
            override fun doInBackground() {
                store.add(selected)
            }
            override fun done() {
                busy = false
                try {
                    get()
                    refresh()
                } catch (e: Exception) {
                    val cause = if (e is ExecutionException) e.cause ?: e else e
                    showError(cause.message ?: "Could not add comic")
                }
                updateControls()
            }
        }.execute()
    }

    private fun readComic() {
        val book = list.selectedValue ?: return
        if (!book.available) {
            showError("This file or directory no longer exists. Remove it or restore it to the same location.")
            return
        }
        if (LocalComicReader.open(book.file, this)) {
            try {
                store.markOpened(book.file)
                refresh()
            } catch (e: Exception) {
                showError("Comic opened, but progress index could not be updated: ${e.message}")
            }
        }
    }

    private fun removeComic() {
        val book = list.selectedValue ?: return
        val confirm = JOptionPane.showConfirmDialog(this,
            "Remove ${book.title} from the local library?\nThe original comic is NOT deleted.",
            "Remove library entry", JOptionPane.YES_NO_OPTION)
        if (confirm != JOptionPane.YES_OPTION) return
        try {
            store.remove(book.file)
            refresh()
        } catch (e: Exception) {
            showError(e.message ?: "Could not remove library entry")
        }
    }

    private fun showError(message: String) {
        JOptionPane.showMessageDialog(this, message,
            "MihonNooir local library", JOptionPane.WARNING_MESSAGE)
    }
}
