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
import javax.swing.Box
import java.awt.Desktop
import java.net.URI
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
import javax.swing.JTabbedPane
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.SwingWorker
import javax.swing.UIManager

/**
 * D2 desktop interaction prototype. Uses the already-tested Suwayomi API and
 * shared MihonNooir conversion engine; no Android code or duplicate codec.
 * Requires the user's separately running, configured localhost Suwayomi.
 */
object MangaNooirDesktop {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.isEmpty()) { "The GUI accepts no command-line credentials or flags" }
        require(!java.awt.GraphicsEnvironment.isHeadless()) { "Desktop GUI requires a display" }
        SwingUtilities.invokeLater {
            runCatching {
                UIManager.setLookAndFeel(
                    UIManager.getInstalledLookAndFeels().firstOrNull { it.name == "Nimbus" }?.className
                        ?: UIManager.getSystemLookAndFeelClassName(),
                )
            }
            DesktopVisualTheme.install()
            DesktopWindow().isVisible = true
        }
    }
}

internal object DesktopUiModel {
    /** Browser handoff only: match the app's localhost/root endpoint policy.
     * Never put authorization headers, user info, query parameters or tokens
     * into a browser URL, and never open an arbitrary remote repository.
     */
    fun extensionSetupUri(endpoint: String): URI {
        val uri = try { URI(endpoint.trim()) } catch (e: Exception) {
            throw IllegalArgumentException("Enter a valid localhost Suwayomi HTTP(S) address.", e)
        }
        require(uri.scheme in setOf("http", "https")) { "Use HTTP or HTTPS." }
        require(uri.host?.lowercase() in setOf("localhost", "127.0.0.1")) {
            "Extension setup opens a localhost Suwayomi server only."
        }
        require(uri.port in 1..65535) { "Supply the local Suwayomi port." }
        require(uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null) {
            "Do not embed credentials, query parameters or fragments in the URL."
        }
        require(uri.rawPath.isNullOrEmpty() || uri.rawPath == "/") { "Use the server root address." }
        return URI.create("${uri.scheme}://${uri.rawAuthority}/")
    }

    fun offlineReadyChoices(count: Int): List<String> {
        require(count in 1..OfflineChapterSaver.MAX_BATCH) { "No successfully saved offline chapters" }
        // Only a single chapter can be opened without making the user choose
        // another one. Never start another source request or reader implicitly.
        return if (count == 1) listOf("Read now", "Open library", "Close")
        else listOf("Open library", "Close")
    }

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
            .trim().take(80).trimEnd(' ', '.').ifBlank { "MihonNooir" }
        // Windows also reserves these names with an extension (for example CON.epub).
        val reserved = Regex("(?i)^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?$")
        val safe = if (reserved.matches(normalized)) "_$normalized" else normalized
        return "$safe.${format.extension}"
    }
}

private class DesktopWindow : JFrame("MihonNooir — Desktop preview") {
    private val endpoint = JTextField("http://127.0.0.1:4567", 22)
    private val status = JLabel("Offline ready — open Local library or connect to Suwayomi when needed")
    private val busy = AtomicBoolean(false)
    private val coverCache = DesktopMangaCoverCache()

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
    private val inspectBinary = JButton("Inspect XTC / XTCH output")
    private val localLibrary = JButton("Local library")
    private val conversionJobs = JButton("Conversion jobs")
    private val sendReader = JButton("Send to XTEINK")
    private val documentImport = JButton("Import PDF / EPUB")
    private val universalConverter = JButton("Universal Book Converter")
    private val browseLayout = JComboBox(arrayOf("Cover grid", "Detailed list"))
    private val checkWindowsUpdates = JButton("Windows updates")
    private val windowsX64 = System.getProperty("os.name").startsWith("Windows", true) &&
        System.getProperty("os.arch").lowercase() in setOf("amd64", "x86_64")
    private val manageExtensions = JButton("Manage extensions…")
    private val backupRestore = JButton("Backup & restore…")
    private val openSuwayomiUi = JButton("Open full Suwayomi WebUI")
    private val startServer = JButton("Start local Suwayomi")
    private val getServer = JButton("Download & start Suwayomi")
    private val stopServer = JButton("Stop managed server")
    private val managedServer = SuwayomiProcessManager()
    private val conversionSettingsStore = DesktopConversionSettingsStore()
    private var options = MangaConversionOptions.preset("Manga")
    private val next = JButton("Next page")
    private val previous = JButton("Previous")
    private val library = JButton("My library")
    private val popular = JButton("Popular")
    private val latest = JButton("Latest")
    private val searchButton = JButton("Search")
    private val libraryToggle = JButton("Add to library").apply { isEnabled = false }
    private val refresh = JButton("Refresh chapters")
    private val readOnline = JButton("Read chapter online")
    private val export = JButton("Export selected chapters")
    private val saveOffline = JButton("Save downloaded chapters offline")
    private val queueOffline = JButton("Queue downloaded chapters")
    private val offlineJobs = JButton("Offline jobs")
    private val queueRemote = JButton("Queue missing chapter downloads")
    private val queueAllRemote = JButton("Queue all missing (max 500)")
    private val remoteJobs = JButton("Download requests")
    private val details = JLabel("Select a manga to view its chapters")
    private val pageInfo = JLabel("Page 1")
    private val controls = mutableListOf<JButton>()
    private var page = 1
    private var hasNext = false
    private var browsing = "library"
    private var currentManga: SuwayomiManga? = null
    private var currentMangaEndpoint: String? = null
    private var mangaListEndpoint: String? = null
    private var selectedSource: SuwayomiSource? = null
    private var compactTabs: JTabbedPane? = null

    init {
        try {
            options = conversionSettingsStore.load()
        } catch (e: Exception) {
            status.text = "Offline ready — saved conversion settings unreadable; using defaults. Original preserved."
        }
        defaultCloseOperation = DISPOSE_ON_CLOSE
        DesktopWindowSizing.fit(this, Dimension(1280, 760), Dimension(600, 440))
        // A narrow work area (including high DPI) uses tabs instead of
        // squeezing three list columns and their controls into tiny panes.
        val compact = width < 1100

        val connect = JButton("Connect / reload sources")
        val header = JPanel(BorderLayout()).apply {
            background = DesktopVisualTheme.background
            border = BorderFactory.createEmptyBorder(8, 12, 8, 12)
            add(DesktopVisualTheme.brand(), BorderLayout.NORTH)
            add(DesktopWindowSizing.toolbar(JPanel(FlowLayout(FlowLayout.LEFT, 8, 4)).apply {
                background = DesktopVisualTheme.surface
                add(JLabel("LOCAL SUWAYOMI").apply {
                    foreground = DesktopVisualTheme.muted
                    font = Font("SansSerif", Font.BOLD, 11)
                })
                add(endpoint); add(connect)
                add(startServer); add(stopServer); add(getServer)
            }), BorderLayout.CENTER)
        }
        add(header, BorderLayout.NORTH)

        val navigation = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            background = DesktopVisualTheme.surface
            border = BorderFactory.createEmptyBorder(10, 10, 15, 10)
            fun nav(button: JButton, primary: Boolean = false) {
                DesktopVisualTheme.nav(button, primary)
                add(button)
                add(Box.createVerticalStrut(6))
            }
            add(DesktopVisualTheme.section("MANGA"))
            nav(library, true)
            nav(localLibrary)
            nav(openSuwayomiUi)
            add(DesktopVisualTheme.section("READ & CONVERT"))
            nav(localReader)
            nav(universalConverter, true)
            nav(conversionJobs)
            nav(inspectBinary)
            nav(sendReader)
            add(DesktopVisualTheme.section("MANAGE"))
            nav(manageExtensions)
            nav(backupRestore)
            nav(offlineJobs)
            nav(remoteJobs)
            if (windowsX64) nav(checkWindowsUpdates)
        }
        add(JScrollPane(navigation,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER).apply {
            // Reserve room for the longest action label and native scrollbar.
            // An under-sized view causes buttons to be unscrollably clipped.
            preferredSize = Dimension(264, 1)
            minimumSize = Dimension(240, 1)
            border = BorderFactory.createMatteBorder(0, 0, 0, 1, DesktopVisualTheme.divider)
            verticalScrollBar.unitIncrement = 22
        }, BorderLayout.WEST)

        sourceList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        sourceList.cellRenderer = itemRenderer { s: SuwayomiSource ->
            "${s.displayName} (${s.language})"
        }
        sourceList.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) selectedSource = sourceList.selectedValue
        }
        val sourcePanel = JPanel(BorderLayout(4, 4)).apply {
            border = DesktopVisualTheme.pane("INSTALLED SOURCES")
            add(JScrollPane(sourceList), BorderLayout.CENTER)
        }

        mangaList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        val mangaRenderer = DesktopMangaCardRenderer(coverCache, { mangaListEndpoint }) {
            mangaList.repaint()
        }
        mangaList.cellRenderer = mangaRenderer
        fun applyBrowseLayout() {
            val grid = browseLayout.selectedIndex == 0
            mangaRenderer.setGrid(grid)
            mangaList.layoutOrientation = if (grid) JList.HORIZONTAL_WRAP else JList.VERTICAL
            mangaList.visibleRowCount = if (grid) -1 else 8
            mangaList.fixedCellWidth = if (grid) 164 else -1
            mangaList.fixedCellHeight = if (grid) 218 else 154
            mangaList.revalidate()
            mangaList.repaint()
            if (mangaList.selectedIndex >= 0) mangaList.ensureIndexIsVisible(mangaList.selectedIndex)
        }
        browseLayout.addActionListener { applyBrowseLayout() }
        applyBrowseLayout()
        mangaList.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                updateLibraryToggle()
                mangaList.selectedValue?.let { loadChapters(it.id, false) }
            }
        }
        val mangaActions = JPanel(FlowLayout(FlowLayout.LEFT, 5, 4)).apply {
            add(popular); add(latest); add(search); add(searchButton); add(libraryToggle)
        }
        val paging = JPanel(FlowLayout(FlowLayout.LEFT, 6, 4)).apply {
            add(previous); add(pageInfo); add(next); add(browseLayout)
        }
        val browsePanel = JPanel(BorderLayout(4, 4)).apply {
            border = DesktopVisualTheme.pane("DISCOVER MANGA")
            add(DesktopWindowSizing.toolbar(mangaActions), BorderLayout.NORTH)
            add(JScrollPane(mangaList), BorderLayout.CENTER)
            add(DesktopWindowSizing.toolbar(paging), BorderLayout.SOUTH)
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
            add(DesktopWindowSizing.toolbar(JPanel(FlowLayout(FlowLayout.LEFT, 5, 2)).apply {
                add(JLabel("Format:")); add(format)
                add(JLabel("Device:")); add(device); add(conversionSettings)
            }))
            add(DesktopWindowSizing.toolbar(JPanel(FlowLayout(FlowLayout.LEFT, 5, 2)).apply {
                add(readOnline); add(downloadMissing); add(refresh); add(export)
            }))
            add(DesktopWindowSizing.toolbar(JPanel(FlowLayout(FlowLayout.LEFT, 5, 2)).apply {
                add(saveOffline); add(queueOffline); add(offlineJobs)
                add(queueRemote); add(queueAllRemote); add(remoteJobs)
            }))
        }
        val chapterPanel = JPanel(BorderLayout(4, 4)).apply {
            border = DesktopVisualTheme.pane("CHAPTERS — CTRL/SHIFT TO SELECT")
            add(JPanel(BorderLayout()).apply {
                add(details, BorderLayout.NORTH)
                add(DesktopWindowSizing.toolbar(selection), BorderLayout.SOUTH)
            }, BorderLayout.NORTH)
            add(JScrollPane(chapterList), BorderLayout.CENTER)
            add(exportOptions, BorderLayout.SOUTH)
        }
        if (compact) {
            val tabs = JTabbedPane().apply {
                addTab("Sources", sourcePanel)
                addTab("Browse", browsePanel)
                addTab("Chapters", chapterPanel)
            }
            compactTabs = tabs
            add(tabs, BorderLayout.CENTER)
        } else {
            listOf(sourcePanel, browsePanel, chapterPanel).forEach {
                it.minimumSize = Dimension(100, 100)
            }
            val left = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sourcePanel, browsePanel).apply {
                resizeWeight = 0.32
                dividerLocation = (this@DesktopWindow.width * 0.20).toInt()
            }
            add(JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, chapterPanel).apply {
                resizeWeight = 0.62
                dividerLocation = (this@DesktopWindow.width * 0.60).toInt()
            }, BorderLayout.CENTER)
        }
        add(JPanel(BorderLayout()).apply {
            border = BorderFactory.createEmptyBorder(8, 12, 8, 12)
            add(status, BorderLayout.CENTER)
            status.toolTipText = "● downloaded   ○ not downloaded"
            if (!compact) add(JLabel("● downloaded   ○ not downloaded"), BorderLayout.EAST)
        }, BorderLayout.SOUTH)

        controls += listOf(connect, library, popular, latest, searchButton,
            next, previous, selected, onlyDownloaded, clear, refresh, readOnline, export,
            saveOffline, queueOffline, offlineJobs, queueRemote, queueAllRemote, remoteJobs,
            conversionSettings, localImport, localReader, localLibrary, conversionJobs, documentImport, universalConverter, inspectBinary, sendReader, libraryToggle,
            startServer, stopServer, getServer, checkWindowsUpdates, manageExtensions, openSuwayomiUi, backupRestore)
        inspectBinary.addActionListener { DesktopXtcBinaryPreview.open(this) }
        conversionSettings.addActionListener {
            DesktopConversionDialog.show(this, options)?.let { proposed ->
                try {
                    conversionSettingsStore.save(proposed)
                    options = proposed
                    status.text = "Conversion settings saved for next launch."
                } catch (e: Exception) {
                    showError("Could not save conversion settings. Existing file and previously selected options are unchanged. " +
                        "Check storage or back up/move " + DesktopConversionSettingsStore.FILE_NAME +
                        " in the app data folder before retrying. " + (e.message ?: ""))
                }
            }
        }
        connect.addActionListener { loadSources() }
        manageExtensions.addActionListener { openExtensionSetup() }
        backupRestore.addActionListener { DesktopBackupPreview.show(this) { openSuwayomiUi.doClick() } }
        openSuwayomiUi.addActionListener {
            try {
                val target = DesktopUiModel.extensionSetupUri(endpoint.text)
                require(Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    "A browser is unavailable. Open $target manually."
                }
                Desktop.getDesktop().browse(target)
            } catch (e: Exception) {
                showError(e.message ?: "Cannot open the local Suwayomi WebUI")
            }
        }
        startServer.addActionListener { startManagedServer() }
        getServer.addActionListener { downloadAndStartManagedServer() }
        stopServer.addActionListener { stopManagedServer() }
        addWindowListener(object : WindowAdapter() {
            override fun windowClosed(e: WindowEvent?) {
                coverCache.close()
                // Never block AWT while stopping the exact process we launched.
                Thread({ managedServer.close() }, "nooir-managed-server-shutdown").start()
            }
        })
        library.addActionListener { browse("library", 1) }
        popular.addActionListener { browse("popular", 1) }
        latest.addActionListener { browse("latest", 1) }
        searchButton.addActionListener { browse("search", 1) }
        libraryToggle.addActionListener { toggleSelectedLibrary() }
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
        readOnline.addActionListener { openOnlineReader() }
        export.addActionListener { exportSelected() }
        saveOffline.addActionListener { saveSelectedOffline() }
        queueOffline.addActionListener { queueSelectedOffline() }
        offlineJobs.addActionListener { openOfflineJobs() }
        queueRemote.addActionListener { queueSelectedRemoteDownloads() }
        queueAllRemote.addActionListener { queueAllMissingDownloads() }
        remoteJobs.addActionListener { openRemoteDownloads() }
        localImport.addActionListener { exportLocal() }
        localReader.addActionListener { openLocalReader() }
        localLibrary.addActionListener { LocalLibraryScreen.open() }
        conversionJobs.addActionListener { DesktopConversionQueueScreen.open() }
        sendReader.addActionListener { DesktopReaderTransferScreen.open(this) }
        documentImport.addActionListener { DesktopDocumentImportScreen.open(this) }
        universalConverter.addActionListener {
            val actions = arrayOf("Comic / image folder", "PDF / EPUB",
                "Batch conversion jobs", "Preview finished XTC / XTCH", "Web → EPUB", "Cancel")
            val choice = JOptionPane.showOptionDialog(this,
                "Choose your source or open an existing conversion workflow.\n" +
                    "Text EPUB to XTC/XTCH and public web import are experimental.",
                "Universal Book Converter", JOptionPane.DEFAULT_OPTION,
                JOptionPane.PLAIN_MESSAGE, null, actions, actions[0])
            when (choice) {
                0 -> exportLocal()
                1 -> DesktopDocumentImportScreen.open(this)
                2 -> DesktopConversionQueueScreen.open()
                3 -> DesktopXtcBinaryPreview.open(this)
                4 -> DesktopWebImportScreen.open(this)
            }
        }
        checkWindowsUpdates.addActionListener { if (windowsX64) DesktopWindowsUpdateScreen.open(this) }
        // The sidebar already uses flat navigation buttons. Apply the same
        // painter to the remaining toolbar/reader controls after construction,
        // keeping their original action listeners, enabled state and focus.
        controls.filter { it.isContentAreaFilled }.forEach {
            DesktopVisualTheme.action(it, primary = it === connect || it === searchButton || it === readOnline || it === export)
        }
        setPaging()
        stopServer.isEnabled = false
        // Inputs keep native focus and selection behavior; the navigation and cover cards are themed.
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
        listOf(endpoint, search, sourceList, mangaList, chapterList, format, device, downloadMissing, browseLayout)
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
                    updateLibraryToggle()
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
                "JAR (about 183 MB) from GitHub?\n\n" +
                "MihonNooir will validate the published SHA-256 digest before " +
                "starting the server on localhost only.\n" +
                "No manga, extensions or third-party sources are downloaded automatically.\n" +
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
            "Start this local Java server?\n\n${serverJar.path}\n\n" +
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

    /** Suwayomi owns extension installs, trust and updates. We only open its
     * local WebUI after an explicit action; offline reading stays independent.
     */
    private fun openExtensionSetup() {
        val uri = try {
            DesktopUiModel.extensionSetupUri(endpoint.text)
        } catch (e: IllegalArgumentException) {
            showError(e.message ?: "Enter a valid localhost Suwayomi address.")
            return
        }
        val address = JTextField(uri.toString(), 30).apply { isEditable = false }
        val help = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(JLabel("Start or connect to your local Suwayomi server first."))
            add(JLabel("In its web interface, add an extension repository you trust."))
            add(JLabel("Install compatible extensions you choose; none are bundled."))
            add(JLabel("Return here and click Connect / reload sources."))
            add(JLabel("Your Android extensions are not automatically copied."))
            add(JLabel("Local server address (copy if the browser cannot open):"))
            add(address)
        }
        val choice = JOptionPane.showOptionDialog(this, help, "Mihon-compatible extensions",
            JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null,
            arrayOf("Open Suwayomi in browser", "Close"), "Open Suwayomi in browser")
        if (choice != 0) return
        try {
            require(Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                "Browser opening is unavailable. Copy the displayed local address into your browser."
            }
            Desktop.getDesktop().browse(uri)
            status.text = "Configure extensions in Suwayomi, then Connect / reload sources."
        } catch (e: Exception) {
            showError("Could not open the browser. Open ${uri} manually. " + (e.message ?: ""))
        }
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
            updateLibraryToggle()
            currentMangaEndpoint = null
            mangaListEndpoint = baseUrl
            details.text = "Select a manga to view its chapters"
            items.forEach { manga.addElement(it) }
            compactTabs?.selectedIndex = 1
            setPaging()
        })
    }

    private fun updateLibraryToggle() {
        val selected = mangaList.selectedValue
        libraryToggle.isEnabled = selected != null && !busy.get() && mangaListEndpoint == endpoint.text.trim()
        libraryToggle.text = if (selected?.inLibrary == true) "Remove from library" else "Add to library"
    }

    /** Suwayomi owns the library database; never change local files or pretend a failed mutation succeeded. */
    private fun toggleSelectedLibrary() {
        if (busy.get()) return
        val selected = mangaList.selectedValue ?: return
        val server = endpoint.text.trim()
        if (mangaListEndpoint != server) {
            showError("Server changed. Reload the manga list first."); return
        }
        val keep = !selected.inLibrary
        if (!keep) {
            val answer = JOptionPane.showConfirmDialog(this,
                "Remove ${selected.title.take(100)} from your Suwayomi library?\n" +
                "Downloaded files are not deleted by this action.",
                "Remove from library", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE)
            if (answer != JOptionPane.YES_OPTION) return
        }
        runTask(if (keep) "Adding manga to library" else "Removing manga from library", {
            client(server).setMangaInLibrary(selected.id, keep)
        }, { confirmed ->
            // Don't replace a different user's selection or another server's list.
            if (mangaListEndpoint != server) return@runTask
            val index = (0 until manga.size()).firstOrNull { manga[it].id == selected.id }
                ?: return@runTask
            manga.set(index, manga[index].copy(inLibrary = confirmed))
            if (browsing == "library" && !confirmed) {
                manga.remove(index)
                chapters.clear(); currentManga = null; currentMangaEndpoint = null
            }
            updateLibraryToggle()
        })
    }

    private fun loadChapters(id: Int, forceRefresh: Boolean) {
        val baseUrl = endpoint.text.trim()
        if (mangaListEndpoint != baseUrl) {
            showError("Server address changed. Reload sources and select manga again."); return
        }
        runTask(if (forceRefresh) "Refreshing chapters" else "Loading chapters", {
            val c = client(baseUrl)
            if (forceRefresh) c.refreshManga(id)
            c.manga(id)
        }, { result ->
            if (mangaList.selectedValue?.id != id) return@runTask
            currentManga = result
            currentMangaEndpoint = baseUrl
            details.text = "${result.title.take(45)} — ${result.chapters.size} chapters"
            chapters.clear()
            result.chapters.sortedWith(compareBy<SuwayomiChapter> {
                if (it.chapterNumber >= 0) it.chapterNumber else Double.MAX_VALUE
            }.thenByDescending { it.sourceOrder }).forEach { chapters.addElement(it) }
            compactTabs?.selectedIndex = 2
        })
    }

    /** Open the selected chapter explicitly; never enqueue a whole-chapter download. */
    private fun openOnlineReader() {
        if (busy.get()) return
        val manga = currentManga
        val chapter = chapterList.selectedValuesList.singleOrNull()
        val baseUrl = endpoint.text.trim()
        if (manga == null || chapter == null || mangaList.selectedValue?.id != manga.id ||
            manga.chapters.none { it.id == chapter.id } || currentMangaEndpoint != baseUrl) {
            showError("Select exactly one chapter from the current server. Reload sources if the address changed.")
            return
        }
        runTask("Opening online chapter", {
            val c = client(baseUrl)
            Pair(c, c.openOnlineChapter(manga.id, chapter.id))
        }, { (c, manifest) ->
            if (isDisplayable) DesktopOnlineReader(c, manifest, manga.title, chapter.name, baseUrl,
                manga.chapters.sortedWith(compareBy<SuwayomiChapter> {
                    if (it.chapterNumber >= 0) it.chapterNumber else Double.MAX_VALUE
                }.thenByDescending { it.sourceOrder }.thenBy { it.id })).isVisible = true
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
                "Conversion complete:\n${file.absolutePath}",
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
                "Save ${missing.size} missing chapter(s) as pending requests?\n" +
                    "Nothing will download until you press Submit pending in Download requests.\n" +
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

    /**
     * Plans all currently loaded missing chapters, without contacting any
     * manga source. Suwayomi request submission stays a separate approval.
     */
    private fun queueAllMissingDownloads() {
        val selectedManga = currentManga ?: run {
            showError("Select and load a manga first")
            return
        }
        val missing = (0 until chapters.size()).map { chapters[it] }
            .filterNot { it.downloaded }
        if (missing.isEmpty()) {
            showError("All currently loaded chapters are already downloaded")
            return
        }
        if (missing.size > RemoteChapterQueue.MAX_JOBS) {
            showError("This series has ${missing.size} missing chapters. " +
                "The Windows preview can queue at most 500 at once; select a smaller range.")
            return
        }
        val base = endpoint.text.trim()
        try {
            val answer = JOptionPane.showConfirmDialog(this,
                "Queue ${missing.size} currently loaded missing chapters for ${selectedManga.title.take(90)}?\n" +
                    "This saves requests locally only. No downloads start now.\n" +
                    "The Download requests window submits at most 20 chapters per explicit approval.\n" +
                    "Existing pending/completed requests are reused; queue capacity is 500 total.",
                "Queue all loaded missing chapters", JOptionPane.YES_NO_OPTION)
            if (answer != JOptionPane.YES_OPTION) return
            val jobs = RemoteChapterQueue().addAllMissing(
                base, selectedManga.id, missing.map { it.id })
            status.text = "${jobs.size} chapter requests recorded, not submitted to Suwayomi."
            RemoteChapterQueueScreen.open(base)
        } catch (e: Exception) {
            showError(e.message ?: "Could not queue missing chapters")
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
                "Add ${chosen.size} already-downloaded chapter(s) to the persistent offline queue?\n" +
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
            "Save ${chapters.size} downloaded chapters as local CBZ files?\n" +
                "This copies pages to your private MihonNooir library, using disk space.\n" +
                "No source downloads will be started.",
            "Save offline manga", JOptionPane.YES_NO_OPTION)
        if (confirm != JOptionPane.YES_OPTION) return
        val selectedIds = chapters.map { it.id }
        val baseUrl = endpoint.text.trim()
        runTask("Saving ${chapters.size} offline chapters", {
            OfflineChapterSaver().save(client(baseUrl), selected.id, selectedIds) { chapter, copied, total ->
                val label = LocalCbzCopyProgress.label(chapter.id, copied, total)
                SwingUtilities.invokeLater {
                    if (isDisplayable) status.text = label
                }
            }
        }, { files ->
            status.text = "Saved ${files.size} offline chapter(s). Available without Suwayomi."
            val choices = DesktopUiModel.offlineReadyChoices(files.size)
            val action = JOptionPane.showOptionDialog(this,
                "Saved ${files.size} downloaded chapter(s) to your local CBZ library.\\n" +
                    "You can read them offline without connecting to Suwayomi.",
                "Offline manga ready", JOptionPane.DEFAULT_OPTION,
                JOptionPane.INFORMATION_MESSAGE, null, choices.toTypedArray(), choices.first())
            when (choices.getOrNull(action)) {
                "Read now" -> LocalComicReader.open(files.single(), this)
                "Open library" -> LocalLibraryScreen.open()
                // Choosing Close or dismissing the dialog preserves the saved
                // CBZ and library records. Nothing is auto-launched or deleted.
            }
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
