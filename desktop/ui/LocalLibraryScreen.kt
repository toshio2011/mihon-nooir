package dev.nooir.desktop.ui

import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ExecutionException
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
    private val add = JButton("Add comic")
    private val read = JButton("Read selected")
    private val remove = JButton("Remove from library")
    private val reload = JButton("Refresh")
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
        }
        add(buttons, BorderLayout.NORTH)
        add(JScrollPane(list), BorderLayout.CENTER)
        add(information.apply {
            border = BorderFactory.createEmptyBorder(8, 12, 10, 12)
        }, BorderLayout.SOUTH)
        list.addListSelectionListener {
            if (!it.valueIsAdjusting) updateControls()
        }
        add.addActionListener { addComic() }
        read.addActionListener { readComic() }
        remove.addActionListener { removeComic() }
        reload.addActionListener { refresh() }
        refresh()
    }

    private fun updateControls() {
        add.isEnabled = !busy
        reload.isEnabled = !busy
        val selected = list.selectedValue
        read.isEnabled = !busy && selected?.available == true
        remove.isEnabled = !busy && selected != null
    }

    private fun refresh() {
        try {
            val selectedPath = list.selectedValue?.file?.path
            val books = store.books()
            model.clear()
            books.forEach(model::addElement)
            val index = books.indexOfFirst { it.file.path == selectedPath }
            if (index >= 0) list.selectedIndex = index
            information.text = "${books.size} local comic(s). These links stay private; original files are never copied."
        } catch (e: Exception) {
            information.text = "Cannot load local library"
            showError(e.message ?: "Library index could not be read")
        }
        updateControls()
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
