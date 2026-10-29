package dev.nooir.desktop.ui

import dev.nooir.bridge.ExportFormat
import dev.nooir.bridge.NooirDeviceProfiles
import dev.nooir.desktop.NooirDesktopCli
import dev.nooir.mangabridge.MangaConversionOptions
import dev.nooir.desktop.suwayomi.SuwayomiBrowseMode
import dev.nooir.desktop.suwayomi.SuwayomiChapter
import dev.nooir.desktop.suwayomi.SuwayomiClient
import dev.nooir.desktop.suwayomi.SuwayomiManga
import dev.nooir.desktop.suwayomi.SuwayomiMangaCard
import dev.nooir.desktop.suwayomi.SuwayomiSource
import dev.nooir.desktop.suwayomi.SuwayomiToNooir
import dev.nooir.desktop.suwayomi.SuwayomiProcessManager
import dev.nooir.desktop.suwayomi.OfficialSuwayomiCache
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridLayout
import java.awt.event.ActionListener
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
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
        val normalized = title.replace(Regex("[^\\p{L}\\p{N} ._()-]"), "_")
            .trim().take(80).trimEnd(' ', '.').ifBlank { "MangaNooir" }
        // Windows also reserves these names with an extension (for example CON.epub).
        val reserved = Regex("(?i)^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?$")
        val safe = if (reserved.matches(normalized)) "_$normalized" else normalized
        return "$safe.${format.extension}"
    }
}

private class DesktopWindow : JFrame("MihonNooir — Windows development") {
    private val endpoint = JTextField("http://127.0.0.1:4567", 22)
    private val status = JLabel("Offline ready — open Local library or connect to Suwayomi when needed")
    private val busy = AtomicBoolean(false)

    private val sources = DefaultListModel<SuwayomiSource>()
    private val sourceList = JList(sources)
    private val manga = DefaultListModel<SuwayomiMangaCard>()
    private val mangaList = JList(manga)
    private val chapters = DefaultListModel<SuwayomiChapter>()
    private val chapterList = JList(chapters)
    private val search = JTextField(20)
    private val format = JComboBox(arrayOf("EPUB2", "EPUB", "XTC", "XTCH", "CBZ"))
    private val device = JComboBox(NooirDeviceProfiles.builtins.map { it.id }.toTypedArray())
    private val downloadMissing = JCheckBox("Download missing chapters (explicit opt-in)")
    private val conversionSettings = JButton("Conversion settings")
    private val localImport = JButton("Convert local folder / CBZ")
    private val localReader = JButton("Read local comic")
    private val localLibrary = JButton("Local library")
    private val startServer = JButton("Start local Suwayomi")
    private val getServer = JButton("Download & start Suwayomi")
    private val stopServer = JButton("Stop managed server")
    private val managedServer = SuwayomiProcessManager()
    private var options = MangaConversionOptions.preset("Manga")
    private val next = JButton("Next page")
    private val previous = JButton("Previous")
    private val library = JButton("My library")
    private val popular = JButton("Popular")
    private val latest = JButton("Latest")
    private val searchButton = JButton("Search")
    private val refresh = JButton("Refresh chapters")
    private val export = JButton("Export selected chapters")
    private val saveOffline = JButton("Save downloaded chapters offline")
    private val queueOffline = JButton("Queue downloaded chapters")
    private val offlineJobs = JButton("Offline jobs")
    private val queueRemote = JButton("Queue missing chapter downloads")
    private val remoteJobs = JButton("Download requests")
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
        val header = JPanel(BorderLayout()).apply {
            add(JPanel(FlowLayout(FlowLayout.LEFT, 10, 8)).apply {
                add(JLabel("Suwayomi:")); add(endpoint); add(connect); add(library)
                add(startServer); add(stopServer)
            }, BorderLayout.NORTH)
            add(JPanel(FlowLayout(FlowLayout.LEFT, 10, 6)).apply {
                add(localLibrary); add(localImport); add(localReader); add(getServer)
            }, BorderLayout.SOUTH)
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
                add(JLabel("Device:")); add(device); add(conversionSettings)
            })
            add(JPanel(FlowLayout(FlowLayout.LEFT)).apply { add(downloadMissing) })
            add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                add(refresh); add(export); add(saveOffline)
                add(queueOffline); add(offlineJobs)
                add(queueRemote); add(remoteJobs)
            })
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
            next, previous, selected, onlyDownloaded, clear, refresh, export,
            saveOffline, queueOffline, offlineJobs, queueRemote, remoteJobs,
            conversionSettings, localImport, localReader, localLibrary,
            startServer, stopServer, getServer)
        conversionSettings.addActionListener {
            DesktopConversionDialog.show(this, options)?.let { options = it }
        }
        connect.addActionListener { loadSources() }
        startServer.addActionListener { startManagedServer() }
        getServer.addActionListener { downloadAndStartManagedServer() }
        stopServer.addActionListener { stopManagedServer() }
        addWindowListener(object : WindowAdapter() {
            override fun windowClosed(e: WindowEvent?) {
                // Never block AWT while stopping the exact process we launched.
                Thread({ managedServer.close() }, "nooir-managed-server-shutdown").start()
            }
        })
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
        saveOffline.addActionListener { saveSelectedOffline() }
        queueOffline.addActionListener { queueSelectedOffline() }
        offlineJobs.addActionListener { openOfflineJobs() }
        queueRemote.addActionListener { queueSelectedRemoteDownloads() }
        remoteJobs.addActionListener { openRemoteDownloads() }
        localImport.addActionListener { exportLocal() }
        localReader.addActionListener { openLocalReader() }
        localLibrary.addActionListener { LocalLibraryScreen.open() }
        setPaging()
        stopServer.isEnabled = false
        // Do not connect automatically on startup: a local-library-only user
        // must be able to open and read manga with no server or network.
        // The explicit Connect / reload sources button starts online browsing.
    }

    private fun client(baseUrl: String): SuwayomiClient = SuwayomiClient(
        baseUrl, System.getenv("NOOIR_SUWAYOMI_AUTHORIZATION"),
    )

    private fun <T> runTask(label: String, task: () -> T, onSuccess: (T) -> Unit) {
        if (!busy.compareAndSet(false, true)) return
        controls.forEach { it.isEnabled = false }
        // Prevent a new selection or endpoint edit from being lost while a request is in flight.
        listOf(endpoint, search, sourceList, mangaList, chapterList, format, device, downloadMissing)
            .forEach { it.isEnabled = false }
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
                    listOf(endpoint, search, sourceList, mangaList, chapterList, format, device, downloadMissing)
                        .forEach { it.isEnabled = true }
                    setPaging()
                    startServer.isEnabled = !managedServer.running
                    stopServer.isEnabled = managedServer.running
                }
            }
        }.execute()
    }

    /** Explicit user approval is mandatory; no silent binary downloads. */
    private fun downloadAndStartManagedServer() {
        if (managedServer.running) {
            showError("Managed Suwayomi is already running")
            return
        }
        val confirmation = JOptionPane.showConfirmDialog(this,
            "Download the official Suwayomi ${SuwayomiProcessManager.PINNED_VERSION} " +
                "JAR (about 183 MB) from GitHub?\\n\\n" +
                "MihonNooir will validate the published SHA-256 digest before " +
                "starting the server on localhost only.\\n" +
                "No manga, extensions or third-party sources are downloaded automatically.\\n" +
                "You can continue using local comics without this download.",
            "Allow official Suwayomi download and local startup?",
            JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
        )
        if (confirmation != JOptionPane.YES_OPTION) return
        runTask("Downloading and verifying official Suwayomi", {
            val jar = OfficialSuwayomiCache().obtain { bytes, total ->
                val progress = if (total != null && total > 0L)
                    "${(bytes * 100 / total).coerceIn(0, 100)}%"
                else "${bytes / (1024 * 1024)} MiB"
                SwingUtilities.invokeLater {
                    if (isDisplayable) status.text = "Downloading verified Suwayomi — $progress"
                }
            }
            SwingUtilities.invokeLater {
                if (isDisplayable) status.text = "Starting verified local Suwayomi..."
            }
            managedServer.start(jar)
        }, { base ->
            endpoint.text = base
            status.text = "Verified managed Suwayomi ready on $base"
            SwingUtilities.invokeLater { loadSources() }
        })
    }

    private fun startManagedServer() {
        if (managedServer.running) {
            showError("Managed Suwayomi is already running")
            return
        }
        val chooser = JFileChooser().apply {
            dialogTitle = "Choose official Suwayomi-Server-v2.4.2366.jar"
            fileSelectionMode = JFileChooser.FILES_ONLY
        }
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return
        val serverJar = chooser.selectedFile.canonicalFile
        if (serverJar.name != SuwayomiProcessManager.PINNED_JAR) {
            showError("Please choose the official ${SuwayomiProcessManager.PINNED_JAR} file.")
            return
        }
        val consent = JOptionPane.showConfirmDialog(this,
            "Start this local Java server?\\n\\n${serverJar.path}\\n\\n" +
                "Only select a trusted jar from Suwayomi's official release. " +
                "The process uses localhost and a separate MihonNooir data folder. " +
                "We never download, install extensions, or connect remotely automatically.",
            "Confirm local Suwayomi startup", JOptionPane.YES_NO_OPTION,
        )
        if (consent != JOptionPane.YES_OPTION) return
        runTask("Starting local Suwayomi", {
            managedServer.start(serverJar)
        }, { base ->
            endpoint.text = base
            status.text = "Managed local Suwayomi ready on $base"
            SwingUtilities.invokeLater { loadSources() }
        })
    }

    private fun stopManagedServer() {
        if (!managedServer.running) return
        runTask("Stopping only our managed Suwayomi process", {
            managedServer.stop()
        }, {
            sources.clear()
            manga.clear()
            chapters.clear()
            currentManga = null
            status.text = "Managed local Suwayomi stopped. Local reading remains available."
        })
    }

    private fun loadSources() {
        val selectedId = selectedSource?.id
        val baseUrl = endpoint.text.trim()
        runTask("Loading installed sources", {
            val c = client(baseUrl)
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
        val baseUrl = endpoint.text.trim()
        if (mode == "search" && term.isBlank()) {
            showError("Enter a manga title to search"); return
        }
        runTask("Loading $mode manga", {
            val c = client(baseUrl)
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
        val baseUrl = endpoint.text.trim()
        runTask(if (forceRefresh) "Refreshing chapters" else "Loading chapters", {
            val c = client(baseUrl)
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

    /** Browse offline CBZ / image folders without a backend or network. */
    private fun openLocalReader() {
        val picker = JFileChooser().apply {
            dialogTitle = "Open local manga or comic"
            fileSelectionMode = JFileChooser.FILES_AND_DIRECTORIES
        }
        if (picker.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return
        LocalComicReader.open(picker.selectedFile.canonicalFile, this)
    }

    /** Local conversion must work independently of Suwayomi or a network connection. */
    private fun exportLocal() {
        if (busy.get()) return
        val sourcePicker = JFileChooser().apply {
            dialogTitle = "Choose an image folder or CBZ / ZIP archive"
            fileSelectionMode = JFileChooser.FILES_AND_DIRECTORIES
        }
        if (sourcePicker.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return
        val input = sourcePicker.selectedFile.canonicalFile
        if (!input.isDirectory &&
            !(input.isFile && input.extension.lowercase() in setOf("cbz", "zip"))) {
            showError("Choose an image folder or a CBZ / ZIP comic archive.")
            return
        }
        val outputFormat = ExportFormat.valueOf(format.selectedItem as String)
        val deviceId = device.selectedItem as String
        if (!NooirDeviceProfiles.forId(deviceId).accepts(outputFormat)) {
            showError("This device preset does not support ${outputFormat.name}.")
            return
        }
        val targetPicker = JFileChooser().apply {
            dialogTitle = "Save converted manga"
            selectedFile = File(DesktopUiModel.suggestedFile(input.nameWithoutExtension, outputFormat))
        }
        if (targetPicker.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return
        val target = targetPicker.selectedFile.absoluteFile
        if (!target.extension.equals(outputFormat.extension, ignoreCase = true)) {
            showError("File must end with .${outputFormat.extension}")
            return
        }
        if (target.exists()) {
            showError("Output already exists; choose another filename to prevent overwriting.")
            return
        }
        val token = options.copy(panelDiagnostics = false).token()
        runTask("Converting local comic", {
            NooirDesktopCli.main(arrayOf(
                "--input", input.absolutePath,
                "--output", target.absolutePath,
                "--format", outputFormat.name,
                "--device", deviceId,
                "--title", input.nameWithoutExtension.take(120).ifBlank { "Manga" },
                "--options-token", token,
            ))
            check(target.isFile && target.length() > 0L) {
                "Local conversion did not produce a readable output file"
            }
            target
        }, { file ->
            JOptionPane.showMessageDialog(this,
                "Conversion complete:\\n${file.absolutePath}",
                "MihonNooir", JOptionPane.INFORMATION_MESSAGE)
        })
    }

    /** Record missing chapters only. No source download begins here. */
    private fun queueSelectedRemoteDownloads() {
        val manga = currentManga ?: run {
            showError("Select a manga first")
            return
        }
        val missing = chapterList.selectedValuesList.filterNot { it.downloaded }
        if (missing.isEmpty()) {
            showError("Select chapters that are not yet downloaded in Suwayomi")
            return
        }
        if (missing.size > 20) {
            showError("Please queue at most 20 missing chapters per batch")
            return
        }
        val base = endpoint.text.trim()
        try {
            val confirmed = JOptionPane.showConfirmDialog(this,
                "Save ${missing.size} missing chapter(s) as pending requests?\\n" +
                    "Nothing will download until you press Submit pending in Download requests.\\n" +
                    "Suwayomi will use the sources/extensions you configured yourself.",
                "Add remote download requests", JOptionPane.YES_NO_OPTION)
            if (confirmed != JOptionPane.YES_OPTION) return
            val requests = RemoteChapterQueue().add(base,
                manga.id, missing.map { it.id })
            status.text = "${requests.size} download request(s) recorded, not started."
            RemoteChapterQueueScreen.open(base)
        } catch (e: Exception) {
            showError(e.message ?: "Cannot queue download requests")
        }
    }

    private fun openRemoteDownloads() {
        val base = endpoint.text.trim()
        try {
            RemoteChapterQueue.portOf(base)
            RemoteChapterQueueScreen.open(base)
        } catch (e: Exception) {
            showError(e.message ?: "Enter a localhost Suwayomi address")
        }
    }

    private fun openOfflineJobs() {
        try {
            OfflineSaveQueue.portOf(endpoint.text.trim())
            OfflineSaveQueueScreen.open(endpoint.text.trim())
        } catch (e: Exception) {
            showError(e.message ?: "Enter a valid localhost Suwayomi address")
        }
    }

    /** This records jobs only; it does not connect to a source or download. */
    private fun queueSelectedOffline() {
        val selected = currentManga ?: run {
            showError("Choose a manga to add offline jobs")
            return
        }
        val chosen = chapterList.selectedValuesList
        if (chosen.isEmpty() || chosen.size > OfflineChapterSaver.MAX_BATCH) {
            showError("Choose 1 to ${OfflineChapterSaver.MAX_BATCH} chapters for the offline queue")
            return
        }
        if (chosen.any { !it.downloaded }) {
            showError("Only chapters already downloaded in Suwayomi can be queued for offline copying.")
            return
        }
        val baseUrl = endpoint.text.trim()
        try {
            val confirmation = JOptionPane.showConfirmDialog(this,
                "Add ${chosen.size} already-downloaded chapter(s) to the persistent offline queue?\\n" +
                    "No network request starts until you choose Run pending in Offline jobs.",
                "Queue offline chapters", JOptionPane.YES_NO_OPTION)
            if (confirmation != JOptionPane.YES_OPTION) return
            val queued = OfflineSaveQueue().enqueue(baseUrl,
                selected.id, chosen.map { it.id })
            status.text = "${queued.size} offline job(s) recorded. Run them from Offline jobs."
            OfflineSaveQueueScreen.open(baseUrl)
        } catch (e: Exception) {
            showError(e.message ?: "Could not add offline save jobs")
        }
    }

    private fun saveSelectedOffline() {
        val selected = currentManga ?: run {
            showError("Choose a manga before saving offline chapters")
            return
        }
        val chapters = chapterList.selectedValuesList
        if (chapters.isEmpty() || chapters.size > OfflineChapterSaver.MAX_BATCH) {
            showError("Choose 1 to ${OfflineChapterSaver.MAX_BATCH} chapters for this offline batch")
            return
        }
        if (chapters.any { !it.downloaded }) {
            showError("Select downloaded chapters only. Missing chapters must first be downloaded in Suwayomi.")
            return
        }
        val confirm = JOptionPane.showConfirmDialog(this,
            "Save ${chapters.size} downloaded chapters as local CBZ files?\\n" +
                "This copies pages to your private MihonNooir library, using disk space.\\n" +
                "No source downloads will be started.",
            "Save offline manga", JOptionPane.YES_NO_OPTION)
        if (confirm != JOptionPane.YES_OPTION) return
        val selectedIds = chapters.map { it.id }
        val baseUrl = endpoint.text.trim()
        runTask("Saving ${chapters.size} offline chapters", {
            OfflineChapterSaver().save(client(baseUrl), selected.id, selectedIds)
        }, { files ->
            JOptionPane.showMessageDialog(this,
                "Saved ${files.size} offline chapter(s). They are available in Local library.",
                "MihonNooir", JOptionPane.INFORMATION_MESSAGE)
            LocalLibraryScreen.open()
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
        val deviceId = device.selectedItem as String
        if (!NooirDeviceProfiles.forId(deviceId).accepts(outputFormat)) {
            showError("This device preset does not support ${outputFormat.name}. Choose another format.")
            return
        }
        val chooser = JFileChooser().apply {
            dialogTitle = "Export MihonNooir book"
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
        val token = options.token()
        runTask("Exporting ${ids.size} chapters", {
            SuwayomiToNooir.main(arrayOf(
                "--base-url", base, "--mode", "export", "--manga-id", selected.id.toString(),
                "--chapters", ids.joinToString(","), "--format", outputFormat.name,
                "--device", deviceId, "--output", target.absolutePath,
                "--download-missing", (missing > 0).toString(),
                "--options-token", token,
            ))
            target
        }, { file ->
            JOptionPane.showMessageDialog(this,
                "Export complete:\n${file.absolutePath}", "MihonNooir", JOptionPane.INFORMATION_MESSAGE)
            SwingUtilities.invokeLater { loadChapters(selected.id, false) }
        })
    }

    private fun setPaging() {
        pageInfo.text = "Page $page"
        previous.isEnabled = !busy.get() && page > 1
        next.isEnabled = !busy.get() && hasNext
    }

    private fun showError(message: String) {
        JOptionPane.showMessageDialog(this, message, "MihonNooir", JOptionPane.WARNING_MESSAGE)
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
