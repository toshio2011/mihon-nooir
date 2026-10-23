package dev.nooir.desktop.ui

import dev.nooir.bridge.ExportFormat
import dev.nooir.desktop.suwayomi.SuwayomiBrowseMode
import dev.nooir.desktop.suwayomi.SuwayomiChapter
import dev.nooir.desktop.suwayomi.SuwayomiClient
import dev.nooir.desktop.suwayomi.SuwayomiManga
import dev.nooir.desktop.suwayomi.SuwayomiMangaCard
import dev.nooir.desktop.suwayomi.SuwayomiSource
import dev.nooir.desktop.suwayomi.SuwayomiToNooir
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridLayout
import java.awt.event.ActionListener
import java.io.File
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JTextField
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.SwingWorker
import javax.swing.UIManager

/**
 * D2 desktop interaction prototype. Uses the already-tested Suwayomi API and
 * shared MangaNooir conversion engine; no Android code or duplicate codec.
 * Requires the user's separately running, configured localhost Suwayomi.
 */
object MangaNooirDesktop {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.isEmpty()) { "The GUI accepts no command-line credentials or flags" }
        require(!java.awt.GraphicsEnvironment.isHeadless()) { "Windows GUI requires a display" }
        SwingUtilities.invokeLater {
            runCatching {
                UIManager.setLookAndFeel(
                    UIManager.getInstalledLookAndFeels().firstOrNull { it.name == "Nimbus" }?.className
                        ?: UIManager.getSystemLookAndFeelClassName(),
                )
            }
            DesktopWindow().isVisible = true
        }
    }
}

internal object DesktopUiModel {
    fun selectedChapterIds(chapters: List<SuwayomiChapter>): List<Int> {
        require(chapters.isNotEmpty()) { "Select at least one chapter" }
        require(chapters.size <= 500) { "Export up to 500 chapters per batch" }
        require(chapters.map { it.id }.distinct().size == chapters.size) { "Duplicate chapter selection" }
        return chapters.sortedWith(compareBy<SuwayomiChapter> {
            if (it.chapterNumber >= 0) it.chapterNumber else Double.MAX_VALUE
        }.thenByDescending { it.sourceOrder }.thenBy { it.id }).map { it.id }
    }

    fun suggestedFile(title: String, format: ExportFormat): String {
        val safe = title.replace(Regex("[^\\p{L}\\p{N} ._()-]"), "_")
            .trim().trim('.').take(80).ifBlank { "MangaNooir" }
        return "$safe.${format.extension}"
    }
}

private class DesktopWindow : JFrame("MangaNooir — Windows prototype") {
    private val endpoint = JTextField("http://127.0.0.1:4567", 22)
    private val status = JLabel("Connect to your locally running Suwayomi server")
    private val busy = AtomicBoolean(false)

    private val sources = DefaultListModel<SuwayomiSource>()
    private val sourceList = JList(sources)
    private val manga = DefaultListModel<SuwayomiMangaCard>()
    private val mangaList = JList(manga)
    private val chapters = DefaultListModel<SuwayomiChapter>()
    private val chapterList = JList(chapters)
    private val search = JTextField(20)
    private val format = JComboBox(arrayOf("EPUB2", "EPUB", "XTC", "XTCH", "CBZ"))
    private val device = JComboBox(arrayOf("X4", "X3", "X4_PRO"))
    private val downloadMissing = JCheckBox("Download missing chapters (explicit opt-in)")
    private val next = JButton("Next page")
    private val previous = JButton("Previous")
    private val library = JButton("My library")
    private val popular = JButton("Popular")
    private val latest = JButton("Latest")
    private val searchButton = JButton("Search")
    private val refresh = JButton("Refresh chapters")
    private val export = JButton("Export selected chapters")
    private val details = JLabel("Select a manga to view its chapters")
    private val pageInfo = JLabel("Page 1")
    private val controls = mutableListOf<JButton>()
    private var page = 1
    private var hasNext = false
    private var browsing = "library"
    private var currentManga: SuwayomiManga? = null
    private var selectedSource: SuwayomiSource? = null

    init {
        defaultCloseOperation = DISPOSE_ON_CLOSE
        minimumSize = Dimension(980, 600)
        setSize(1280, 760)
        setLocationRelativeTo(null)

        val connect = JButton("Connect / reload sources")
        val header = JPanel(FlowLayout(FlowLayout.LEFT, 10, 8)).apply {
            add(JLabel("Suwayomi:")); add(endpoint); add(connect); add(library)
        }
        add(header, BorderLayout.NORTH)

        sourceList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        sourceList.cellRenderer = itemRenderer { s: SuwayomiSource ->
            "${s.displayName} (${s.language})"
        }
        sourceList.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) selectedSource = sourceList.selectedValue
        }
        val sourcePanel = JPanel(BorderLayout(4, 4)).apply {
            border = BorderFactory.createTitledBorder("Installed sources")
            add(JScrollPane(sourceList), BorderLayout.CENTER)
        }

        mangaList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        mangaList.cellRenderer = itemRenderer { m: SuwayomiMangaCard ->
            "${m.title}${if (m.inLibrary) "  [Library]" else ""}"
        }
        mangaList.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) mangaList.selectedValue?.let { loadChapters(it.id, false) }
        }
        val mangaActions = JPanel(FlowLayout(FlowLayout.LEFT, 5, 4)).apply {
            add(popular); add(latest); add(search); add(searchButton)
        }
        val paging = JPanel(FlowLayout(FlowLayout.LEFT, 6, 4)).apply {
            add(previous); add(pageInfo); add(next)
        }
        val browsePanel = JPanel(BorderLayout(4, 4)).apply {
            border = BorderFactory.createTitledBorder("Browse manga")
            add(mangaActions, BorderLayout.NORTH)
            add(JScrollPane(mangaList), BorderLayout.CENTER)
            add(paging, BorderLayout.SOUTH)
        }

        chapterList.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        chapterList.cellRenderer = itemRenderer { c: SuwayomiChapter ->
            "${if (c.downloaded) "●" else "○"}  ${c.name}"
        }
        val selected = JButton("Select all chapters")
        val onlyDownloaded = JButton("Select downloaded")
        val clear = JButton("Clear")
        val selection = JPanel(FlowLayout(FlowLayout.LEFT, 5, 4)).apply {
            add(selected); add(onlyDownloaded); add(clear)
        }
        val exportOptions = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                add(JLabel("Format:")); add(format)
                add(JLabel("Device:")); add(device)
            })
            add(JPanel(FlowLayout(FlowLayout.LEFT)).apply { add(downloadMissing) })
            add(JPanel(FlowLayout(FlowLayout.LEFT)).apply { add(refresh); add(export) })
        }
        val chapterPanel = JPanel(BorderLayout(4, 4)).apply {
            border = BorderFactory.createTitledBorder("Chapters — Ctrl/Shift to select")
            add(JPanel(BorderLayout()).apply {
                add(details, BorderLayout.NORTH); add(selection, BorderLayout.SOUTH)
            }, BorderLayout.NORTH)
            add(JScrollPane(chapterList), BorderLayout.CENTER)
            add(exportOptions, BorderLayout.SOUTH)
        }
        val left = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sourcePanel, browsePanel).apply {
            resizeWeight = 0.32
            dividerLocation = 260
        }
        add(JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, chapterPanel).apply {
            resizeWeight = 0.62
            dividerLocation = 770
        }, BorderLayout.CENTER)
        add(JPanel(BorderLayout()).apply {
            border = BorderFactory.createEmptyBorder(8, 12, 8, 12)
            add(status, BorderLayout.CENTER)
            add(JLabel("● downloaded   ○ not downloaded"), BorderLayout.EAST)
        }, BorderLayout.SOUTH)

        controls += listOf(connect, library, popular, latest, searchButton,
            next, previous, selected, onlyDownloaded, clear, refresh, export)
        connect.addActionListener { loadSources() }
        library.addActionListener { browse("library", 1) }
        popular.addActionListener { browse("popular", 1) }
        latest.addActionListener { browse("latest", 1) }
        searchButton.addActionListener { browse("search", 1) }
        search.addActionListener { browse("search", 1) }
        next.addActionListener { if (hasNext) browse(browsing, page + 1) }
        previous.addActionListener { if (page > 1) browse(browsing, page - 1) }
        selected.addActionListener {
            if (!chapters.isEmpty) chapterList.setSelectionInterval(0, chapters.size() - 1)
        }
        onlyDownloaded.addActionListener {
            chapterList.selectedIndices = (0 until chapters.size())
                .filter { chapters[it].downloaded }.toIntArray()
        }
        clear.addActionListener { chapterList.clearSelection() }
        refresh.addActionListener {
            currentManga?.let { loadChapters(it.id, true) }
                ?: showError("Select a manga first")
        }
        export.addActionListener { exportSelected() }
        setPaging()
        loadSources()
    }

    private fun client(): SuwayomiClient = SuwayomiClient(
        endpoint.text.trim(), System.getenv("NOOIR_SUWAYOMI_AUTHORIZATION"),
    )

    private fun <T> runTask(label: String, task: () -> T, onSuccess: (T) -> Unit) {
        if (!busy.compareAndSet(false, true)) return
        controls.forEach { it.isEnabled = false }
        status.text = "$label..."
        object : SwingWorker<T, Unit>() {
            override fun doInBackground(): T = task()
            override fun done() {
                try {
                    val result = get()
                    onSuccess(result)
                    status.text = "$label — complete"
                } catch (e: Exception) {
                    val cause = if (e is ExecutionException) e.cause ?: e else e
                    status.text = "$label — failed"
                    showError(cause.message ?: cause.javaClass.simpleName)
                } finally {
                    busy.set(false)
                    controls.forEach { it.isEnabled = true }
                    setPaging()
                }
            }
        }.execute()
    }

    private fun loadSources() {
        val selectedId = selectedSource?.id
        runTask("Loading installed sources", {
            val c = client()
            val result = mutableListOf<SuwayomiSource>()
            var offset = 0
            while (true) {
                val batch = c.sources(offset, 100)
                result += batch.sources
                if (result.size >= batch.totalCount || batch.sources.isEmpty() || result.size >= 2_000) break
                offset += batch.sources.size
            }
            result
        }, { result ->
            sources.clear()
            result.forEach { sources.addElement(it) }
            if (!sources.isEmpty) {
                val found = (0 until sources.size()).firstOrNull { sources[it].id == selectedId } ?: 0
                sourceList.selectedIndex = found
            }
            SwingUtilities.invokeLater { browse("library", 1) }
        })
    }

    private fun browse(mode: String, targetPage: Int) {
        if (busy.get()) return
        val source = selectedSource
        if (mode != "library" && source == null) {
            showError("Choose an installed source first"); return
        }
        val term = search.text.trim()
        if (mode == "search" && term.isBlank()) {
            showError("Enter a manga title to search"); return
        }
        runTask("Loading $mode manga", {
            val c = client()
            if (mode == "library") {
                val result = c.library((targetPage - 1) * 50, 50)
                Pair(result.mangas, (targetPage - 1) * 50 + result.mangas.size < result.totalCount)
            } else {
                val type = when (mode) {
                    "popular" -> SuwayomiBrowseMode.POPULAR
                    "latest" -> SuwayomiBrowseMode.LATEST
                    else -> SuwayomiBrowseMode.SEARCH
                }
                val result = c.browse(source!!.id, type, targetPage, if (type == SuwayomiBrowseMode.SEARCH) term else null)
                Pair(result.mangas, result.hasNextPage)
            }
        }, { (items, more) ->
            browsing = mode
            page = targetPage
            hasNext = more
            manga.clear()
            chapters.clear()
            currentManga = null
            details.text = "Select a manga to view its chapters"
            items.forEach { manga.addElement(it) }
            setPaging()
        })
    }

    private fun loadChapters(id: Int, forceRefresh: Boolean) {
        runTask(if (forceRefresh) "Refreshing chapters" else "Loading chapters", {
            val c = client()
            if (forceRefresh) c.refreshManga(id)
            c.manga(id)
        }, { result ->
            if (mangaList.selectedValue?.id != id) return@runTask
            currentManga = result
            details.text = "${result.title.take(45)} — ${result.chapters.size} chapters"
            chapters.clear()
            result.chapters.sortedWith(compareBy<SuwayomiChapter> {
                if (it.chapterNumber >= 0) it.chapterNumber else Double.MAX_VALUE
            }.thenByDescending { it.sourceOrder }).forEach { chapters.addElement(it) }
        })
    }

    private fun exportSelected() {
        val chosen = chapterList.selectedValuesList
        val ids = try { DesktopUiModel.selectedChapterIds(chosen) }
        catch (e: IllegalArgumentException) { showError(e.message ?: "Select chapters"); return }
        val selected = currentManga ?: run { showError("Select a manga first"); return }
        val missing = chosen.count { !it.downloaded }
        if (missing > 0 && !downloadMissing.isSelected) {
            showError("$missing selected chapters are not downloaded. Tick the explicit download option or select downloaded chapters only.")
            return
        }
        if (missing > 0) {
            val confirm = JOptionPane.showConfirmDialog(this,
                "Queue $missing missing chapters for download using your configured Suwayomi source?",
                "Confirm chapter downloads", JOptionPane.YES_NO_OPTION)
            if (confirm != JOptionPane.YES_OPTION) return
        }
        val outputFormat = ExportFormat.valueOf(format.selectedItem as String)
        val chooser = JFileChooser().apply {
            dialogTitle = "Export MangaNooir book"
            selectedFile = File(DesktopUiModel.suggestedFile(selected.title, outputFormat))
        }
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return
        val target = chooser.selectedFile.absoluteFile
        if (!target.extension.equals(outputFormat.extension, ignoreCase = true)) {
            showError("File must end with .${outputFormat.extension}"); return
        }
        if (target.exists()) {
            showError("The destination file already exists. Choose another filename to avoid overwriting.")
            return
        }
        val base = endpoint.text.trim()
        val deviceId = device.selectedItem as String
        runTask("Exporting ${ids.size} chapters", {
            SuwayomiToNooir.main(arrayOf(
                "--base-url", base, "--mode", "export", "--manga-id", selected.id.toString(),
                "--chapters", ids.joinToString(","), "--format", outputFormat.name,
                "--device", deviceId, "--output", target.absolutePath,
                "--download-missing", (missing > 0).toString(),
            ))
            target
        }, { file ->
            JOptionPane.showMessageDialog(this,
                "Export complete:\n${file.absolutePath}", "MangaNooir", JOptionPane.INFORMATION_MESSAGE)
            SwingUtilities.invokeLater { loadChapters(selected.id, false) }
        })
    }

    private fun setPaging() {
        pageInfo.text = "Page $page"
        previous.isEnabled = !busy.get() && page > 1
        next.isEnabled = !busy.get() && hasNext
    }

    private fun showError(message: String) {
        JOptionPane.showMessageDialog(this, message, "MangaNooir", JOptionPane.WARNING_MESSAGE)
    }

    private fun <T> itemRenderer(label: (T) -> String) = object : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean,
        ): java.awt.Component {
            val component = super.getListCellRendererComponent(
                list, value, index, isSelected, cellHasFocus,
            ) as JLabel
            @Suppress("UNCHECKED_CAST")
            if (value != null) component.text = label(value as T)
            component.border = BorderFactory.createEmptyBorder(6, 8, 6, 8)
            return component
        }
    }
}
