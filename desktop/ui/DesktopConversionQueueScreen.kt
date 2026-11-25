package dev.nooir.desktop.ui

import dev.nooir.bridge.ExportFormat
import dev.nooir.bridge.NooirDeviceProfiles
import dev.nooir.mangabridge.MangaConversionOptions
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridLayout
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.*

internal object DesktopConversionQueueScreen {
    private var window: ConversionQueueWindow? = null
    fun open() {
        window?.takeIf { it.isDisplayable }?.let { it.toFront(); return }
        window = ConversionQueueWindow().apply { isVisible = true }
    }
}

private class ConversionQueueWindow : JFrame("MihonNooir — Local conversion jobs") {
    private val queue = DesktopConversionQueue()
    private val model = DefaultListModel<DesktopConversionJob>()
    private val list = JList(model)
    private val addFiles = JButton("Add local books")
    private val run = JButton("Run / recover")
    private val pause = JButton("Pause after current")
    private val retry = JButton("Retry selected")
    private val cancel = JButton("Cancel selected")
    private val clear = JButton("Clear finished")
    private val refresh = JButton("Refresh")
    private val status = JLabel()
    private val stop = AtomicBoolean(false)
    private var active = false

    init {
        defaultCloseOperation = DISPOSE_ON_CLOSE
        DesktopWindowSizing.fit(this, Dimension(1000, 580), Dimension(580, 380))
        add(DesktopWindowSizing.toolbar(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            listOf(addFiles, run, pause, retry, cancel, clear, refresh).forEach { add(it) }
        }), BorderLayout.NORTH)
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        list.cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(l: JList<*>?, value: Any?, index: Int,
                selected: Boolean, focus: Boolean): java.awt.Component {
                val label = super.getListCellRendererComponent(l, value, index, selected, focus) as JLabel
                if (value is DesktopConversionJob) {
                    label.text = "[${value.state}] ${java.io.File(value.input).name} → ${java.io.File(value.output).name}" +
                        (if (value.textEpub == null) "" else " · Text EPUB") +
                        (if (value.pdfReflow) " · PDF reflow" else "") +
                        (if (value.error.isBlank()) "" else " — " + value.error.take(80))
                    label.toolTipText = value.output
                }
                return label
            }
        }
        add(JScrollPane(list), BorderLayout.CENTER)
        add(status, BorderLayout.SOUTH)
        addWindowListener(object : WindowAdapter() {
            override fun windowClosed(e: WindowEvent?) { stop.set(true) }
        })
        list.addListSelectionListener { controls() }
        addFiles.addActionListener { choose() }
        run.addActionListener { runJobs() }
        pause.addActionListener { stop.set(true); status.text = "Stopping after current conversion"; controls() }
        retry.addActionListener { action { queue.retry(checkNotNull(list.selectedValue).id) } }
        cancel.addActionListener { action { queue.cancel(checkNotNull(list.selectedValue).id) } }
        clear.addActionListener {
            if (JOptionPane.showConfirmDialog(this, "Forget finished/canceled records? Output files remain unchanged.",
                    "Clear records", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                action { queue.forgetFinished() }
            }
        }
        refresh.addActionListener { reload() }
        reload()
    }

    private fun warn(e: Exception) {
        val cause = (e as? java.util.concurrent.ExecutionException)?.cause ?: e
        JOptionPane.showMessageDialog(this, cause.message ?: "Conversion queue error",
            "Conversion jobs", JOptionPane.WARNING_MESSAGE)
    }
    private fun controls() {
        val state = list.selectedValue?.state
        addFiles.isEnabled = !active
        run.isEnabled = !active && (0 until model.size()).any { model[it].state in
            setOf(ConversionJobState.PENDING, ConversionJobState.RUNNING) }
        pause.isEnabled = active && !stop.get()
        retry.isEnabled = !active && state in setOf(ConversionJobState.FAILED, ConversionJobState.INTERRUPTED)
        cancel.isEnabled = !active && state in setOf(ConversionJobState.PENDING, ConversionJobState.FAILED,
            ConversionJobState.INTERRUPTED)
        clear.isEnabled = !active
        refresh.isEnabled = !active
    }
    private fun reload() {
        val id = list.selectedValue?.id
        try {
            val jobs = queue.list()
            model.clear()
            jobs.forEach(model::addElement)
            val selected = jobs.indexOfFirst { it.id == id }
            if (selected >= 0) list.selectedIndex = selected
            if (!active) status.text = "Local jobs: ${jobs.size} · Run explicitly · no overwrites"
        } catch (e: Exception) { warn(e) }
        controls()
    }
    private fun action(block: () -> Unit) {
        try { block(); reload() } catch (e: Exception) { warn(e) }
    }
    private fun choose() {
        val kinds = arrayOf("Comic archives / image folders", "PDF documents", "Image EPUB", "Mixed supported books", "Text EPUB / novels", "PDF reflow to EPUB")
        val kind = JOptionPane.showInputDialog(this, "Choose input type before browsing",
            "Universal Book Converter — batch", JOptionPane.QUESTION_MESSAGE,
            null, kinds, kinds[3]) as? String ?: return
        val extensions = when (kind) {
            kinds[0] -> arrayOf("cbz", "zip")
            kinds[1], kinds[5] -> arrayOf("pdf")
            kinds[2], kinds[4] -> arrayOf("epub")
            else -> arrayOf("cbz", "zip", "pdf", "epub")
        }
        val folders = kind == kinds[0] || kind == kinds[3]
        val input = JFileChooser().apply {
            dialogTitle = "Choose " + kind
            fileSelectionMode = if (folders) JFileChooser.FILES_AND_DIRECTORIES else JFileChooser.FILES_ONLY
            fileFilter = javax.swing.filechooser.FileNameExtensionFilter(kind, *extensions)
            isAcceptAllFileFilterUsed = false
            isMultiSelectionEnabled = true
        }
        if (input.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return
        val files = input.selectedFiles.toList().ifEmpty { listOfNotNull(input.selectedFile) }
        if (files.isEmpty() || files.any { file ->
                !(folders && file.isDirectory || file.isFile && extensions.any { it.equals(file.extension, true) })
            }) {
            JOptionPane.showMessageDialog(this, "Choose only files matching the selected input type.")
            return
        }
        val prose = kind == kinds[4]
        val reflow = kind == kinds[5]
        val format = JComboBox(if (prose) arrayOf("XTC", "XTCH") else if (reflow) arrayOf("EPUB2","EPUB")
            else arrayOf("EPUB2", "EPUB", "XTC", "XTCH", "CBZ"))
        val font = JComboBox(arrayOf("Serif", "SansSerif", "Monospaced"))
        val fontSize = JSpinner(SpinnerNumberModel(24,14,46,1))
        val margin = JSpinner(SpinnerNumberModel(26,8,80,2))
        val lineHeight = JSpinner(SpinnerNumberModel(118,100,200,5))
        val direction = JComboBox(arrayOf("Auto","Left to right","Right to left"))
        val device = JComboBox(NooirDeviceProfiles.builtins.map { it.id }.toTypedArray())
        val settings = JPanel(GridLayout(0, 2, 6, 6)).apply {
            add(JLabel("Format")); add(format); add(JLabel("Device")); add(device)
        }
        if (reflow) {
            settings.add(JLabel("Text pages reflow")); settings.add(JLabel("Complex/scanned pages retain images"))
        } else if (files.any { it.isFile && it.extension.equals("pdf", true) }) {
            settings.add(JLabel("PDF batch: original pages only")); settings.add(JLabel("No manga panel slicing or text companion"))
        }
        if (prose) {
            settings.add(JLabel("Font family")); settings.add(font)
            settings.add(JLabel("Font size (px)")); settings.add(fontSize)
            settings.add(JLabel("Margin (px)")); settings.add(margin)
            settings.add(JLabel("Line height (%)")); settings.add(lineHeight)
            settings.add(JLabel("Reading direction")); settings.add(direction)
        } else if (files.any { it.isFile && it.extension.equals("epub", true) }) {
            settings.add(JLabel("EPUB: image-only")); settings.add(JLabel("Use Text EPUB for novels"))
        }
        if (JOptionPane.showConfirmDialog(this, settings, "Batch output",
                JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return
        val typography = if (prose) DesktopTextEpubImport.Settings(fontSize.value as Int, margin.value as Int,
            lineHeight.value as Int, font.selectedItem as String, direction.selectedItem as String) else null
        val options = if (reflow) MangaConversionOptions().copy(renderAlgorithm="THRESHOLD", rightToLeft=false)
            else if (prose) MangaConversionOptions().copy(renderAlgorithm="THRESHOLD")
            else DesktopConversionDialog.show(this, MangaConversionOptions.preset("Manga")) ?: return
        val destination = JFileChooser().apply {
            dialogTitle = "Choose an existing output directory"
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        }
        if (destination.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return
        action {
            queue.enqueue(files, destination.selectedFile, ExportFormat.valueOf(format.selectedItem as String),
                device.selectedItem as String, options, typography, pdfReflow = reflow)
        }
    }
    private fun runJobs() {
        if (active) return
        stop.set(false); active = true; controls()
        object : SwingWorker<Unit, DesktopConversionJob>() {
            override fun doInBackground() {
                queue.runPending({ stop.get() }) { publish(it) }
            }
            override fun process(chunks: MutableList<DesktopConversionJob>) {
                if (!isDisplayable) return
                reload()
                status.text = "Converting " + java.io.File(chunks.last().input).name
            }
            override fun done() {
                active = false
                if (!isDisplayable) return
                try { get() } catch (e: Exception) { warn(e) }
                reload()
            }
        }.execute()
    }
}
