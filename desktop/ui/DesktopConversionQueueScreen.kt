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
        val input = JFileChooser().apply {
            dialogTitle = "Choose image folders, CBZ/ZIP, PDF or image EPUB"
            fileSelectionMode = JFileChooser.FILES_AND_DIRECTORIES
            isMultiSelectionEnabled = true
        }
        if (input.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return
        val files = input.selectedFiles.toList().ifEmpty { listOfNotNull(input.selectedFile) }
        val format = JComboBox(arrayOf("EPUB2", "EPUB", "XTC", "XTCH", "CBZ"))
        val device = JComboBox(NooirDeviceProfiles.builtins.map { it.id }.toTypedArray())
        val settings = JPanel(GridLayout(0, 2, 6, 6)).apply {
            add(JLabel("Format")); add(format); add(JLabel("Device")); add(device)
        }
        if (files.any { it.isFile && it.extension.equals("pdf", true) }) {
            settings.add(JLabel("PDF batch: original pages only")); settings.add(JLabel("No manga panel slicing or text companion"))
        }
        if (files.any { it.isFile && it.extension.equals("epub", true) }) {
            settings.add(JLabel("EPUB: image-only")); settings.add(JLabel("Prose/protected books will fail safely"))
        }
        if (JOptionPane.showConfirmDialog(this, settings, "Batch output",
                JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return
        val options = DesktopConversionDialog.show(this, MangaConversionOptions.preset("Manga")) ?: return
        val destination = JFileChooser().apply {
            dialogTitle = "Choose an existing output directory"
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        }
        if (destination.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return
        action {
            queue.enqueue(files, destination.selectedFile, ExportFormat.valueOf(format.selectedItem as String),
                device.selectedItem as String, options)
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
