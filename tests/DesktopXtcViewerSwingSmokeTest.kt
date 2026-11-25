package dev.nooir.desktop.ui

import dev.nooir.bridge.BookMetadata
import dev.nooir.bridge.QuantizedPage
import dev.nooir.bridge.XtcCodec
import dev.nooir.bridge.XtcFormat
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Window
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.prefs.Preferences
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO
import dev.nooir.desktop.storage.DesktopDataHome
import java.util.concurrent.atomic.AtomicReference
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JSpinner
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JTextField
import javax.swing.JTabbedPane
import javax.swing.JScrollPane
import javax.swing.SwingUtilities

/** Real Swing dialog acceptance on a display-capable JVM (Xvfb or Windows), never headless. */
fun main() {
    check(!GraphicsEnvironment.isHeadless()) { "A display-capable JVM is required (Xvfb or Windows desktop)" }
    val screen = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration
    val scale = screen.defaultTransform
    println("GUI environment: " + System.getProperty("os.name") +
        " logical screen=" + screen.bounds + " transform=" + scale)
    System.getProperty("nooir.test.expectedUiScale")?.let { value ->
        val expected = value.toDouble()
        check(kotlin.math.abs(scale.scaleX - expected) <= 0.05 &&
            kotlin.math.abs(scale.scaleY - expected) <= 0.05) {
            "Requested Java2D UI scale $expected not active: $scale"
        }
    }
    val folder = Files.createTempDirectory("nooir-saved-book-swing-").toFile()
    try {
        for ((device, dims) in listOf("X4" to (480 to 800), "X3" to (528 to 792))) {
            val (width, height) = dims
            for (format in XtcFormat.entries) {
                val levels = if (format == XtcFormat.XTC) 2 else 4
                val pages = (0..2).map { index ->
                    QuantizedPage(width, height, ByteArray(width * height) { pixel ->
                        ((pixel % width / 41 + pixel / width / 67 + index) % levels).toByte()
                    }, format.depth)
                }
                val saved = folder.resolve("gui-" + device + "." + format.extension)
                saved.outputStream().use { target ->
                    XtcCodec.write(format, pages.asSequence(), target, folder,
                        BookMetadata("Original synthetic GUI fixture"))
                }
                val originalBytes = saved.readBytes()
                val parent = onSwingThread {
                    JFrame("Nooir viewer test parent").apply {
                        size = Dimension(700, 650)
                        isVisible = true
                    }
                }
                try {
                    val dialog = onSwingThread {
                        checkNotNull(DesktopXtcBinaryPreview.showSavedBook(parent, saved))
                    }
                    try {
                        val labels = onSwingThread {
                            descendants(dialog).filterIsInstance<JLabel>()
                        }
                        val details = labels.first { it.text.orEmpty().startsWith(format.name + " · page") }
                        val scroll = onSwingThread {
                            descendants(dialog).filterIsInstance<JScrollPane>().first()
                        }
                        val preview = onSwingThread {
                            scroll.viewport.view as JLabel
                        }
                        val buttons = onSwingThread {
                            descendants(dialog).filterIsInstance<JButton>()
                        }
                        val previous = buttons.first { it.text == "Previous" }
                        val next = buttons.first { it.text == "Next" }
                        val fit = onSwingThread {
                            descendants(dialog).filterIsInstance<JCheckBox>().single()
                        }
                        check(onSwingThread { fit.isSelected && !previous.isEnabled && next.isEnabled })
                        awaitPage(dialog, details, preview, 1, "fitted display")
                        onSwingThread {
                            dialog.size = Dimension(560, 520)
                            dialog.validate()
                        }
                        awaitPage(dialog, details, preview, 1, "fitted display")
                        awaitFitInsideViewport(scroll, preview)
                        check(onSwingThread { (preview.icon as ImageIcon).iconHeight < height }) {
                            "Saved page did not fit reduced window: $device $format"
                        }
                        onSwingThread { fit.doClick() }
                        awaitPage(dialog, details, preview, 1, "1:1 pixels")
                        check(onSwingThread {
                            (preview.icon as ImageIcon).iconWidth == width &&
                                (preview.icon as ImageIcon).iconHeight == height
                        }) { "Native inspection changed dimensions: $device $format" }
                        onSwingThread {
                            next.doClick()
                            next.doClick()
                        }
                        awaitPage(dialog, details, preview, 3, "1:1 pixels")
                        captureWindow(dialog, "saved-book-" + device.lowercase() + "-" + format.name.lowercase())
                        check(onSwingThread { previous.isEnabled && !next.isEnabled })
                        onSwingThread {
                            previous.doClick()
                            previous.doClick()
                        }
                        awaitPage(dialog, details, preview, 1, "1:1 pixels")
                        check(onSwingThread { !previous.isEnabled && next.isEnabled })
                        onSwingThread { fit.doClick() }
                        awaitPage(dialog, details, preview, 1, "fitted display")
                    } finally {
                        onSwingThread { dialog.dispose() }
                        check(!onSwingThread { dialog.isDisplayable })
                    }
                    verifyCorruptBookError(parent, folder, device, format)
                } finally {
                    onSwingThread { parent.dispose() }
                }
                check(saved.readBytes().contentEquals(originalBytes)) {
                    "Swing viewer must never modify its selected saved book: $device $format"
                }
            }
        }
        println("PASS: real Swing X3/X4 XTC/XTCH viewer, fit, native, navigation, resize, close, corrupt-file warning")
        if (System.getProperty("nooir.test.offlineLibrary") == "true") verifyOfflineLibrary(folder)
    } finally {
        folder.deleteRecursively()
    }
}

/**
 * The production viewer must reject a corrupt saved XTC/XTCH file using a
 * visible modal error, without opening a reader or changing source bytes.
 * Dismiss the modal from a non-EDT test thread.
 */
private fun verifyCorruptBookError(
    parent: JFrame, folder: java.io.File, device: String, format: XtcFormat,
) {
    val broken = folder.resolve("damaged-$device.${format.extension}")
    val bytes = byteArrayOf(0x58, 0x54, 0x43, 0)
    broken.writeBytes(bytes)
    val completed = AtomicReference<Boolean?>(null)
    val failure = AtomicReference<Throwable?>()
    SwingUtilities.invokeLater {
        try {
            check(DesktopXtcBinaryPreview.showSavedBook(parent, broken) == null) {
                "Corrupt binary must not open a page reader"
            }
            completed.set(true)
        } catch (e: Throwable) {
            failure.set(e)
            completed.set(false)
        }
    }
    val deadline = System.nanoTime() + 10_000_000_000L
    var dismissed = false
    try {
        while (System.nanoTime() < deadline) {
            val warning = onSwingThread {
                java.awt.Window.getWindows().filterIsInstance<JDialog>()
                    .firstOrNull { it.isShowing && it.title == "Binary book preview" }
            }
            if (warning != null) {
                val errorShown = onSwingThread {
                    descendants(warning).filterIsInstance<JLabel>().any {
                        it.text.orEmpty().contains("Cannot read saved XTC/XTCH book:")
                    }
                }
                check(errorShown) { "Corrupt source did not show the expected error" }
                onSwingThread { warning.dispose() }
                dismissed = true
                break
            }
            failure.get()?.let { throw it }
            Thread.sleep(30)
        }
        check(dismissed) { "Timed out waiting for corrupt-file warning: $device $format" }
        while (System.nanoTime() < deadline && completed.get() == null) Thread.sleep(30)
        failure.get()?.let { throw it }
        check(completed.get() == true) { "Corrupt-file handler failed to return cleanly" }
        check(broken.readBytes().contentEquals(bytes)) { "Damaged source was modified" }
    } finally {
        onSwingThread {
            java.awt.Window.getWindows().filterIsInstance<JDialog>()
                .filter { it.isShowing && it.title == "Binary book preview" }
                .forEach { it.dispose() }
        }
    }
}

private fun descendants(component: Component): List<Component> =
    listOf(component) + if (component is Container) {
        component.components.flatMap { descendants(it) }
    } else emptyList()

private fun awaitPage(
    dialog: JDialog, details: JLabel, preview: JLabel, page: Int, mode: String,
) {
    val deadline = System.nanoTime() + 10_000_000_000L
    while (System.nanoTime() < deadline) {
        val passed = onSwingThread {
            dialog.isDisplayable && details.text.orEmpty().contains("page $page / 3") &&
                details.text.orEmpty().contains(mode) && preview.icon is ImageIcon
        }
        if (passed) return
        Thread.sleep(30)
    }
    val state = onSwingThread { details.text + " / icon=" + (preview.icon != null) }
    error("Timed out waiting for saved page $page ($mode): $state")
}

/**
 * Check actual post-resize rendered pixels, not only a stale label from the
 * previous window size. The viewport event must have reached the viewer.
 */
private fun awaitFitInsideViewport(scroll: JScrollPane, preview: JLabel) {
    val deadline = System.nanoTime() + 10_000_000_000L
    while (System.nanoTime() < deadline) {
        val passed = onSwingThread {
            val extent = scroll.viewport.extentSize
            val icon = preview.icon as? ImageIcon
            icon != null && extent.width > 50 && extent.height > 50 &&
                icon.iconWidth <= (extent.width - 8).coerceAtLeast(1) &&
                icon.iconHeight <= (extent.height - 8).coerceAtLeast(1)
        }
        if (passed) return
        Thread.sleep(30)
    }
    val sizes = onSwingThread {
        "viewport=" + scroll.viewport.extentSize + " icon=" +
            (preview.icon as? ImageIcon)?.let { Dimension(it.iconWidth, it.iconHeight) }
    }
    error("Fit-to-window failed after dialog resize: $sizes")
}

private fun <T> onSwingThread(block: () -> T): T {
    if (SwingUtilities.isEventDispatchThread()) return block()
    val result = AtomicReference<T>()
    val failure = AtomicReference<Throwable?>()
    SwingUtilities.invokeAndWait {
        try { result.set(block()) } catch (t: Throwable) { failure.set(t) }
    }
    failure.get()?.let { throw it }
    return result.get()
}


/** Exercise the actual local bookshelf and reader on a display-capable packaged JVM. */
private fun verifyOfflineLibrary(folder: File) {
    val explicitRoot = System.getenv(DesktopDataHome.ENV_NAME)
    check(!explicitRoot.isNullOrBlank()) { "Offline GUI QA requires an isolated data directory" }
    val root = DesktopDataHome.root().toFile()
    check(!root.exists() || (root.isDirectory && root.list()?.isEmpty() == true)) {
        "Offline GUI QA refuses to write to an existing nonempty data directory"
    }
    val comic = folder.resolve("Original-GUI-Fixture.cbz")
    ZipOutputStream(comic.outputStream()).use { zip ->
        for (page in 1..4) {
            val image = BufferedImage(96, 144, BufferedImage.TYPE_INT_RGB)
            val graphics = image.createGraphics()
            try {
                graphics.color = Color.WHITE
                graphics.fillRect(0, 0, 96, 144)
                graphics.color = Color.BLACK
                graphics.fillRect(page * 4, 10, 50, 20 + page * 12)
            } finally { graphics.dispose() }
            val bytes = ByteArrayOutputStream()
            check(ImageIO.write(image, "png", bytes))
            image.flush()
            zip.putNextEntry(ZipEntry("page-$page.png"))
            zip.write(bytes.toByteArray())
            zip.closeEntry()
        }
    }
    val original = comic.readBytes()
    val prefs = Preferences.userRoot().node("dev/nooir/mihonnooir/reader")
    val keys = listOf("layout-rtl", "layout-two-page", "layout-webtoon", "layout-first-alone")
    val beforePrefs = keys.associateWith { prefs.get(it, null) }
    val positionKey = MessageDigest.getInstance("SHA-256")
        .digest(comic.canonicalPath.toByteArray(Charsets.UTF_8))
        .take(24).joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    try {
        prefs.putBoolean("layout-rtl", true)
        prefs.putBoolean("layout-two-page", false)
        prefs.putBoolean("layout-webtoon", false)
        prefs.putBoolean("layout-first-alone", true)
        // Seed the local index with original art. This tests the real read
        // UI, not native file chooser interaction, which remains manual QA.
        LocalLibraryStore().add(comic)
        // Launch through the exact production main function, including its
        // look and feel and offline startup. Do not contact Suwayomi.
        MangaNooirDesktop.main(emptyArray())
        val main = awaitFrame("MihonNooir — Desktop preview")
        verifyFitsWorkArea(main)
        awaitUi("offline main startup without backend") {
            descendants(main).filterIsInstance<JLabel>().any {
                it.text.orEmpty().startsWith("Offline ready")
            }
        }
        val tabs = onSwingThread {
            descendants(main).filterIsInstance<JTabbedPane>().singleOrNull()
        }
        if (onSwingThread { main.width < 1100 }) {
            checkNotNull(tabs) { "Compact main layout requires tabs" }
            check(onSwingThread {
                (0 until tabs.tabCount).map { tabs.getTitleAt(it) } ==
                    listOf("Sources", "Browse", "Chapters")
            })
            onSwingThread { tabs.selectedIndex = 0 }
        } else {
            check(tabs == null) { "Wide main layout should keep the three panes" }
        }
        captureWindow(main, "main-offline")
        verifyExtensionSetup(main)
        verifyToolbarReachable(findButton(main, "Open full Suwayomi WebUI"))
        verifyToolbarReachable(findButton(main, "Universal Book Converter"))
        check(onSwingThread { !findButton(main,"Connect / reload sources").isContentAreaFilled &&
            !findButton(main,"Search").isContentAreaFilled }) { "Main toolbar must use the flat desktop theme" }
        verifyWebEditor(main)
        if (tabs != null) onSwingThread { tabs.selectedIndex = 1 }
        verifyToolbarReachable(findButton(main, "Search"))
        if (tabs != null) onSwingThread { tabs.selectedIndex = 2 }
        verifyToolbarReachable(findButton(main, "Download requests"))
        verifyConversionSettings(main)
        verifyToolbarReachable(findButton(main, "Inspect XTC / XTCH output"))
        val libraryButton = findButton(main, "Local library")
        verifyToolbarReachable(libraryButton)
        onSwingThread { libraryButton.doClick() }
        val library = awaitFrame("MihonNooir — My local library")
        verifyFitsWorkArea(library)
        val list = onSwingThread {
            descendants(library).filterIsInstance<JList<*>>().single()
        }
        val search = onSwingThread {
            descendants(library).filterIsInstance<JTextField>().single()
        }
        val read = findButton(library, "Read selected")
        val continueButton = findButton(library, "Continue last downloaded manga")
        verifyToolbarReachable(continueButton)
        onSwingThread { search.text = "no matching book" }
        awaitUi("filter hides nonmatching books") { list.model.size == 0 && !read.isEnabled }
        onSwingThread { search.text = "original-gui" }
        awaitUi("filter finds saved comic") { list.model.size == 1 }
        onSwingThread { list.selectedIndex = 0 }
        awaitUi("offline cover and reader selection") {
            read.isEnabled && descendants(library).filterIsInstance<JLabel>()
                .any { it.icon is ImageIcon }
        }

        captureWindow(library, "local-library")
        onSwingThread { read.doClick() }
        val reader = awaitFrame("MihonNooir Reader — " + comic.name)
        verifyFitsWorkArea(reader)
        awaitReaderPage(reader, 1)
        captureWindow(reader, "offline-reader")
        val next = findButton(reader, "Next")
        onSwingThread { next.doClick() }
        awaitReaderPage(reader, 2)
        val bookmark = findButton(reader, "Bookmark page")
        verifyToolbarReachable(bookmark)
        onSwingThread { bookmark.doClick() }
        awaitUi("bookmark button updates") { bookmark.text == "Remove bookmark" }
        check(ReaderBookmarkStore().list(comic).map { it.pageIndex } == listOf(1))
        check(LocalLibraryStore().books().single().lastOpenedAt > 0)

        onSwingThread { reader.dispose(); library.dispose() }
        onSwingThread { libraryButton.doClick() }
        val reopenedLibrary = awaitFrame("MihonNooir — My local library")
        val reopenedList = onSwingThread {
            descendants(reopenedLibrary).filterIsInstance<JList<*>>().single()
        }
        awaitUi("persisted library entry") { reopenedList.model.size == 1 }
        onSwingThread { reopenedList.selectedIndex = 0 }
        onSwingThread { findButton(reopenedLibrary, "Read selected").doClick() }
        val reopened = awaitFrame("MihonNooir Reader — " + comic.name)
        awaitReaderPage(reopened, 2)
        check(onSwingThread {
            descendants(reopened).filterIsInstance<JButton>().any { it.text == "Remove bookmark" }
        }) { "Saved bookmark did not survive reopening the reader" }
        check(comic.readBytes().contentEquals(original)) { "Offline reader changed original CBZ bytes" }
        println("PASS: packaged offline main startup, compact/wide layout, library filter, cover, reader page turn, bookmark, reopen/resume and source unchanged; windows/toolbar accessible")
        onSwingThread { Window.getWindows().filter { it.isDisplayable }.forEach { it.dispose() } }
        MangaNooirDesktop.main(emptyArray())
        val restarted = awaitFrame("MihonNooir — Desktop preview")
        onSwingThread {
            descendants(restarted).filterIsInstance<JTabbedPane>().singleOrNull()?.selectedIndex = 2
        }
        verifyConversionSettings(restarted, restored = true)
        verifyOnlineReading(restarted)
    } finally {
        onSwingThread {
            Window.getWindows().filter { it.isDisplayable }.forEach { it.dispose() }
        }
        beforePrefs.forEach { (key, value) ->
            if (value == null) prefs.remove(key) else prefs.put(key, value)
        }
        prefs.remove(positionKey)
        prefs.flush()
    }
}

/** Open the actual production main settings and verify saved/canceled edits. */
/** Exercise the real toolbar and modal without launching a browser or installing an extension. */
/** Real editor controls, entirely synthetic/manual links; no network. */
private fun verifyWebEditor(main: JFrame) {
    onSwingThread { DesktopWebImportScreen.open(main) }
    val dialog=onSwingThread {
        Window.getWindows().filterIsInstance<JDialog>().single { it.isShowing && it.title=="Web pages & novels → EPUB" }
    }
    try {
        val input=onSwingThread { descendants(dialog).filterIsInstance<javax.swing.JTextArea>().single { it.name=="web-source-urls" } }
        val table=onSwingThread { descendants(dialog).filterIsInstance<javax.swing.JTable>().single { it.name=="web-chapter-table" } }
        fun click(title:String)=onSwingThread {
            descendants(dialog).filterIsInstance<JButton>().single{it.text==title}.doClick()
        }
        onSwingThread {
            check(GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds.contains(dialog.bounds))
            input.text="https://example.org/one\nhttps://example.org/two\nhttps://example.org/three"
        }
        click("Use pasted chapter/page URLs")
        onSwingThread {
            check(table.rowCount==3)
            table.setValueAt("First original chapter",0,1)
            table.setValueAt(false,1,0)
            table.setRowSelectionInterval(2,2)
        }
        click("Move up")
        check(onSwingThread { table.getValueAt(1,2)=="https://example.org/three" && table.getValueAt(2,0)==false })
        click("Reverse order")
        check(onSwingThread { table.getValueAt(0,2)=="https://example.org/two" && table.getValueAt(2,1)=="First original chapter" })
        click("Select none")
        check(onSwingThread { (0 until table.rowCount).none{table.getValueAt(it,0)==true} })
        click("Select all")
        check(onSwingThread { (0 until table.rowCount).all{table.getValueAt(it,0)==true} })
        check(onSwingThread { !descendants(dialog).filterIsInstance<JButton>().single{it.text=="Cancel export"}.isEnabled })
        val tabs=onSwingThread { descendants(dialog).filterIsInstance<JTabbedPane>().single{it.name=="web-editor-tabs"} }
        check(onSwingThread { tabs.tabCount==3 && tabs.selectedIndex==0 })
        captureWindow(dialog,"web-chapter-editor")
        onSwingThread { tabs.selectedIndex=1 }
        val bookTitle=onSwingThread { descendants(dialog).filterIsInstance<JTextField>().single{it.name=="web-book-title"} }
        onSwingThread { bookTitle.text="Original QA novel" }
        captureWindow(dialog,"web-book-details")
        onSwingThread { tabs.selectedIndex=2 }
        captureWindow(dialog,"web-export-settings")
        onSwingThread { tabs.selectedIndex=0 }
        check(onSwingThread { table.rowCount==3 && bookTitle.text=="Original QA novel" })
        println("PASS: native web editor manual links, editable titles, selection, reorder and idle cancellation controls")
    } finally { onSwingThread { dialog.dispose() } }
}

private fun verifyExtensionSetup(main: JFrame) {
    val button = findButton(main, "Manage extensions…")
    verifyToolbarReachable(button)
    val endpoint = onSwingThread {
        descendants(main).filterIsInstance<JTextField>().single { it.text == "http://127.0.0.1:4567" }
    }
    fun showDialog(title: String): JDialog {
        onSwingThread { SwingUtilities.invokeLater { button.doClick() } }
        awaitUi("extension setup dialog: " + title) {
            Window.getWindows().filterIsInstance<JDialog>().any { it.isShowing && it.title == title }
        }
        return onSwingThread {
            Window.getWindows().filterIsInstance<JDialog>().single { it.isShowing && it.title == title }
        }
    }
    val dialog = showDialog("Mihon-compatible extensions")
    captureWindow(dialog, "extension-setup")
    check(onSwingThread {
        GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds.contains(dialog.bounds)
    }) { "Extension setup modal exceeds desktop work area" }
    check(onSwingThread {
        descendants(dialog).filterIsInstance<JTextField>().single().let {
            !it.isEditable && it.text == "http://127.0.0.1:4567/"
        }
    })
    check(onSwingThread {
        descendants(dialog).filterIsInstance<JButton>().any { it.text == "Open Suwayomi in browser" }
    })
    onSwingThread {
        descendants(dialog).filterIsInstance<JButton>().single { it.text == "Close" }.doClick()
    }
    awaitUi("extension setup closes without a browser") { !dialog.isShowing }
    onSwingThread { Unit }
    check(onSwingThread {
        descendants(main).filterIsInstance<JLabel>().any { it.text.orEmpty().startsWith("Offline ready") }
    }) { "Cancelling setup must not connect to the backend" }

    // Reject remote endpoints at the actual UI boundary, without making a request.
    onSwingThread { endpoint.text = "https://example.com:4567" }
    onSwingThread { SwingUtilities.invokeLater { button.doClick() } }
    awaitUi("invalid extension setup address warning") {
        Window.getWindows().filterIsInstance<JDialog>().any {
            it.isShowing && descendants(it).filterIsInstance<JLabel>().any { label ->
                label.text.orEmpty().contains("localhost Suwayomi server only")
            }
        }
    }
    val errorDialog = onSwingThread {
        Window.getWindows().filterIsInstance<JDialog>().single {
            it.isShowing && descendants(it).filterIsInstance<JLabel>().any { label ->
                label.text.orEmpty().contains("localhost Suwayomi server only")
            }
        }
    }
    onSwingThread {
        descendants(errorDialog).filterIsInstance<JButton>().single { it.text == "OK" }.doClick()
        endpoint.text = "http://127.0.0.1:4567"
    }
    awaitUi("invalid-address warning dismissed") { !errorDialog.isShowing }
    onSwingThread { Unit }
    println("PASS: native extension setup toolbar, local address, cancel and remote endpoint rejection; no browser launched")
}

/** Full GUI journey against synthetic localhost API, no external manga. */
private fun verifyOnlineReading(main: JFrame) {
    val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1",0),0)
    val requests = java.util.concurrent.CopyOnWriteArrayList<Int>()
    val manifestIds = java.util.concurrent.CopyOnWriteArrayList<Int>()
    val failedOnce = java.util.concurrent.atomic.AtomicBoolean(false)
    val png = ByteArrayOutputStream().also {
        val art = BufferedImage(96,144,BufferedImage.TYPE_INT_RGB)
        val g = art.createGraphics()
        try {
            g.color = Color(53, 66, 105); g.fillRect(0,0,96,144)
            g.color = Color(206, 188, 128); g.fillRect(10,16,76,102)
            g.color = Color(49, 54, 74); g.fillRect(23,32,50,60)
            g.color = Color(237, 224, 193); g.fillOval(33,42,30,30)
        } finally { g.dispose() }
        check(ImageIO.write(art, "png", it)); art.flush()
    }.toByteArray()
    server.createContext("/api/graphql") { ctx ->
        try {
            val body = ctx.requestBody.bufferedReader().use { it.readText() }
            val response = when {
                "NooirSources" in body -> """{"data":{"sources":{"totalCount":0,"nodes":[]}}}"""
                "NooirLibrary" in body -> """{"data":{"mangas":{"totalCount":1,"nodes":[{"id":9,"title":"Original online fixture","author":"QA","description":null,"thumbnailUrl":"/api/v1/manga/9/thumbnail","inLibrary":true}]}}}"""
                "NooirManga" in body -> """{"data":{"manga":{"id":9,"title":"Original online fixture","author":"QA","chapters":{"nodes":[{"id":17,"name":"Chapter 1","chapterNumber":1,"sourceOrder":3,"isDownloaded":false},{"id":18,"name":"Chapter 2","chapterNumber":2,"sourceOrder":4,"isDownloaded":false}]}}}}"""
                "fetchChapterPages" in body -> {
                    val id = if ("chapterId: 18" in body) 18 else 17
                    manifestIds.add(id)
                    val order = if (id==18) 4 else 3
                    """{"data":{"fetchChapterPages":{"pages":["/api/v1/manga/9/chapter/$order/page/0","/api/v1/manga/9/chapter/$order/page/1","/api/v1/manga/9/chapter/$order/page/2"],"chapter":{"id":$id,"mangaId":9,"sourceOrder":$order,"pageCount":3,"lastPageRead":0},"syncConflict":null}}}"""
                }
                else -> """{"errors":[{"message":"Unexpected fixture request"}]}"""
            }.toByteArray()
            ctx.responseHeaders.add("Content-Type","application/json")
            ctx.sendResponseHeaders(200,response.size.toLong())
            ctx.responseBody.use { it.write(response) }
        } finally { ctx.close() }
    }
    server.createContext("/api/v1/manga/9/chapter/") { ctx ->
        try {
            val page = ctx.requestURI.path.substringAfterLast('/').toInt()
            check(page in 0..2)
            val order = ctx.requestURI.path.split('/')[6].toInt()
            check(order in 3..4)
            requests.add(order*10+page)
            if (order==3 && page==1 && failedOnce.compareAndSet(false,true)) {
                ctx.responseHeaders.add("Content-Type","text/html")
                ctx.sendResponseHeaders(200,1)
                ctx.responseBody.use { it.write(65) }
            } else {
                ctx.responseHeaders.add("Content-Type","image/png")
                ctx.sendResponseHeaders(200,png.size.toLong())
                ctx.responseBody.use { it.write(png) }
            }
        } finally { ctx.close() }
    }
    server.createContext("/api/v1/manga/9/thumbnail") { ctx ->
        try {
            check(ctx.requestMethod == "GET" && ctx.requestURI.rawQuery == null)
            ctx.responseHeaders.add("Content-Type","image/png")
            ctx.sendResponseHeaders(200,png.size.toLong())
            ctx.responseBody.use { it.write(png) }
        } finally { ctx.close() }
    }
    server.start()
    try {
        val endpoint = onSwingThread { descendants(main).filterIsInstance<JTextField>().single { it.text.orEmpty().startsWith("http://127.0.0.1:") } }
        onSwingThread { endpoint.text = "http://127.0.0.1:${server.address.port}"; findButton(main,"Connect / reload sources").doClick() }
        awaitUi("online fixture manga loaded") {
            descendants(main).filterIsInstance<JList<*>>().any {
                it.model.size==1 && it.model.getElementAt(0) is dev.nooir.desktop.suwayomi.SuwayomiMangaCard
            } && findButton(main,"Connect / reload sources").isEnabled
        }
        val manga = onSwingThread { descendants(main).filterIsInstance<JList<*>>().single {
            it.model.size==1 && it.model.getElementAt(0) is dev.nooir.desktop.suwayomi.SuwayomiMangaCard
        } }
        check(onSwingThread { manga.cellRenderer is dev.nooir.desktop.ui.DesktopMangaCardRenderer })
        val layoutChoice = onSwingThread {
            descendants(main).filterIsInstance<JComboBox<*>>().single {
                it.itemCount == 2 && it.getItemAt(0) == "Cover grid" && it.getItemAt(1) == "Detailed list"
            }
        }
        check(onSwingThread { manga.layoutOrientation == JList.HORIZONTAL_WRAP })
        captureWindow(main, "suwayomi-inspired-manga-browse")
        onSwingThread { layoutChoice.selectedIndex = 1 }
        check(onSwingThread { manga.layoutOrientation == JList.VERTICAL && manga.model.size == 1 })
        captureWindow(main, "manga-detailed-list")
        onSwingThread { layoutChoice.selectedIndex = 0 }
        check(onSwingThread { manga.layoutOrientation == JList.HORIZONTAL_WRAP && manga.model.size == 1 })
        onSwingThread { manga.selectedIndex=0 }
        awaitUi("library action available for selected manga") {
            findButton(main, "Remove from library").isEnabled
        }
        awaitUi("online chapter list loaded") {
            descendants(main).filterIsInstance<JList<*>>().any {
                it.model.size==2 && it.model.getElementAt(0) is dev.nooir.desktop.suwayomi.SuwayomiChapter
            } && findButton(main,"Read chapter online").isEnabled
        }
        val chapters = onSwingThread { descendants(main).filterIsInstance<JList<*>>().single {
            it.model.size==2 && it.model.getElementAt(0) is dev.nooir.desktop.suwayomi.SuwayomiChapter
        } }
        val open = findButton(main,"Read chapter online")
        verifyToolbarReachable(open)
        captureWindow(main, "online-chapters")
        onSwingThread { chapters.selectedIndex=0; open.doClick() }
        val reader = awaitFrame("MihonNooir Online Reader — Original online fixture / Chapter 1")
        try {
            verifyFitsWorkArea(reader)
            fun page(n:Int) = awaitUi("online page $n displayed") {
                descendants(reader).filterIsInstance<JLabel>().any { it.text=="Page $n / 3" }
            }
            page(1)
            captureWindow(reader, "online-reader")
            check(requests.toList()==listOf(30)) { "Reader must fetch a single initial page" }
            onSwingThread { findButton(reader,"Next").doClick() }
            awaitUi("recoverable page failure") { findButton(reader,"Retry").isEnabled }
            captureWindow(reader, "online-retry")
            onSwingThread { findButton(reader,"Retry").doClick() }
            page(2)
            onSwingThread { findButton(reader,"Next").doClick() }
            page(3)
            check(onSwingThread { !findButton(reader,"Next").isEnabled })
            onSwingThread { findButton(reader,"Previous").doClick() }
            page(2)
            check(requests.toList()==listOf(30,31,31,32,31))
            check(manifestIds.toList()==listOf(17))
            onSwingThread { findButton(reader,"Next chapter").doClick() }
            awaitUi("second chapter opened in same window") { reader.title.endsWith("Chapter 2") }
            page(1)
            captureWindow(reader, "online-next-chapter")
            onSwingThread { findButton(reader,"Next").doClick() }
            page(2)
            onSwingThread { findButton(reader,"Previous chapter").doClick() }
            awaitUi("first chapter reopened in same window") { reader.title.endsWith("Chapter 1") }
            page(2)
            check(manifestIds.toList()==listOf(17,18,17))
            check(requests.toList()==listOf(30,31,31,32,31,40,41,31))
            onSwingThread { findButton(reader,"Close reader").doClick() }
            awaitUi("online reader closed") { !reader.isDisplayable }
            onSwingThread { open.doClick() }
            val resumed = awaitFrame("MihonNooir Online Reader — Original online fixture / Chapter 1")
            try {
                awaitUi("local position restored on reopening") {
                    descendants(resumed).filterIsInstance<JLabel>().any { it.text=="Page 2 / 3" }
                }
                captureWindow(resumed, "online-resumed")
                check(requests.last()==31 && manifestIds.toList()==listOf(17,18,17,17))
                println("PASS: native online chapter reading, retry, in-window chapter transitions and local resume; synthetic localhost only")
            } finally { onSwingThread { resumed.dispose() } }
        } finally { onSwingThread { reader.dispose() } }
    } finally { server.stop(0) }
}

private fun verifyConversionSettings(main: JFrame, restored: Boolean = false) {
    val button = findButton(main, "Conversion settings")
    verifyToolbarReachable(button)
    fun open(): JDialog {
        onSwingThread { SwingUtilities.invokeLater { button.doClick() } }
        awaitUi("conversion settings modal") {
            Window.getWindows().filterIsInstance<JDialog>().any {
                it.isShowing && it.title == "MihonNooir conversion settings"
            }
        }
        return onSwingThread { Window.getWindows().filterIsInstance<JDialog>().first {
            it.isShowing && it.title == "MihonNooir conversion settings"
        } }
    }
    fun control(dialog: JDialog, label: String): Component = onSwingThread {
        descendants(dialog).filterIsInstance<JLabel>().single { it.text == label }.labelFor
    }
    fun toggle(dialog: JDialog): JCheckBox = onSwingThread {
        descendants(dialog).filterIsInstance<JCheckBox>().single { it.text == "Enlarge oversized splits" }
    }
    fun finish(dialog: JDialog, action: String) {
        onSwingThread { descendants(dialog).filterIsInstance<JButton>().single { it.text == action }.doClick() }
        awaitUi("settings modal dismissed") { !dialog.isShowing }
        // Flush the main action listener after the modal event loop returns.
        onSwingThread { Unit }
    }
    fun reachable(control: javax.swing.JComponent) {
        val scroll = onSwingThread {
            SwingUtilities.getAncestorOfClass(JScrollPane::class.java, control) as JScrollPane
        }
        onSwingThread { control.scrollRectToVisible(Rectangle(0,0,control.width,control.height)) }
        awaitUi("settings control scrolls into view") {
            val rect = SwingUtilities.convertRectangle(control.parent,control.bounds,scroll.viewport.view)
            scroll.viewport.viewRect.contains(rect)
        }
    }
    val dialog = open()
    check(onSwingThread { GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds.contains(dialog.bounds) }) {
        "Conversion settings modal exceeds desktop work area"
    }
    val mode = control(dialog,"Page extraction") as JComboBox<*>
    val enlarge = toggle(dialog)
    val fill = control(dialog,"Minimum screen fill %") as JSpinner
    if (restored) {
        check(onSwingThread { mode.selectedItem == "ROW_BY_ROW" && enlarge.isSelected && fill.value == 65 })
        finish(dialog,"Cancel")
        println("PASS: new production main window restores saved conversion preferences")
        return
    }
    onSwingThread { mode.selectedItem = "OFF" }
    check(onSwingThread { !enlarge.isEnabled && !fill.isEnabled })
    onSwingThread { mode.selectedItem = "ROW_BY_ROW" }
    check(onSwingThread { enlarge.isEnabled && enlarge.isSelected && fill.isEnabled && fill.value == 50 })
    reachable(enlarge); reachable(fill)
    captureWindow(dialog, "conversion-settings")
    onSwingThread { enlarge.doClick() }
    check(onSwingThread { !fill.isEnabled })
    onSwingThread { enlarge.doClick() }
    check(onSwingThread { fill.isEnabled })
    // Deliberately type the value without firing Enter; OK must commit it.
    onSwingThread { (fill.editor as JSpinner.DefaultEditor).textField.text = "65" }
    finish(dialog,"OK")
    awaitUi("conversion preferences persisted") {
        DesktopConversionSettingsStore().load().minimumScreenFillPercent == 65
    }
    val saved = open()
    val retained = control(saved,"Minimum screen fill %") as JSpinner
    check(onSwingThread { toggle(saved).isSelected && retained.value == 65 &&
        (control(saved,"Page extraction") as JComboBox<*>).selectedItem == "ROW_BY_ROW" })
    onSwingThread { toggle(saved).doClick() }
    finish(saved,"Cancel")
    val reopened = open()
    check(onSwingThread { toggle(reopened).isSelected &&
        (control(reopened,"Minimum screen fill %") as JSpinner).value == 65 })
    finish(reopened,"Cancel")
    println("PASS: native scrollable conversion settings, oversized toggle/threshold, typed value commit and cancel rollback")
}

/** Capture only the application's actual painted window, never the runner desktop.
 *  Opt-in output directory is supplied by the packaged Windows CI job.
 *  All windows in this test contain synthetic fixtures and isolated test data.
 */
private fun captureWindow(window: Window, name: String) {
    val output = System.getProperty("nooir.test.screenshotDir") ?: return
    require(name.matches(Regex("[a-z0-9-]+")))
    val dir = java.nio.file.Path.of(output)
    Files.createDirectories(dir)
    val image = onSwingThread {
        check(window.isShowing && window.width > 0 && window.height > 0)
        val pixels = BufferedImage(window.width, window.height, BufferedImage.TYPE_INT_RGB)
        val g = pixels.createGraphics()
        try { window.printAll(g) } finally { g.dispose() }
        pixels
    }
    try {
        check(ImageIO.write(image, "png", dir.resolve("$name.png").toFile()))
        println("CAPTURE: $name.png")
    } finally { image.flush() }
}

private fun awaitFrame(title: String): JFrame {
    awaitUi("open frame $title") {
        Window.getWindows().filterIsInstance<JFrame>().any { it.isShowing && it.title == title }
    }
    return onSwingThread {
        Window.getWindows().filterIsInstance<JFrame>().first { it.isShowing && it.title == title }
    }
}

private fun findButton(frame: JFrame, title: String): JButton =
    onSwingThread { descendants(frame).filterIsInstance<JButton>().first { it.text == title } }

private fun verifyFitsWorkArea(frame: JFrame) {
    awaitUi("window fits the available logical screen") {
        GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds.contains(frame.bounds)
    }
}

private fun verifyToolbarReachable(button: JButton) {
    val scroll = onSwingThread {
        SwingUtilities.getAncestorOfClass(JScrollPane::class.java, button) as? JScrollPane
            ?: error("Toolbar control has no scrollable row: " + button.text)
    }
    onSwingThread {
        button.scrollRectToVisible(Rectangle(0, 0, button.width, button.height))
    }
    awaitUi("toolbar control can scroll fully into view: " + button.text) {
        val bounds = SwingUtilities.convertRectangle(button.parent, button.bounds, scroll.viewport.view)
        scroll.viewport.viewRect.contains(bounds)
    }
}

private fun awaitReaderPage(frame: JFrame, page: Int) {
    awaitUi("reader decoded page $page") {
        descendants(frame).filterIsInstance<JLabel>().any {
            it.text.orEmpty().startsWith("Page $page / 4")
        }
    }
}

private fun awaitUi(label: String, condition: () -> Boolean) {
    val deadline = System.nanoTime() + 10_000_000_000L
    while (System.nanoTime() < deadline) {
        if (onSwingThread(condition)) return
        Thread.sleep(30)
    }
    error("Timed out: $label")
}
