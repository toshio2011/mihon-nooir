package dev.nooir.desktop.ui

import dev.nooir.desktop.suwayomi.SuwayomiClient
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.BorderFactory
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ListSelectionModel
import javax.swing.SwingWorker

/**
 * A persistent queue UI for copies of chapters already downloaded in Suwayomi.
 * No job starts automatically on application startup or when a queue is opened.
 * The user must explicitly click Run pending.
 */
internal object OfflineSaveQueueScreen {
    fun open(endpoint: String) {
        OfflineSaveQueueWindow(endpoint).isVisible = true
    }
}

private class OfflineSaveQueueWindow(private val endpoint: String) :
    JFrame("MihonNooir — Offline save jobs") {
    private val queue = OfflineSaveQueue()
    private val currentPort = OfflineSaveQueue.portOf(endpoint)
    private val model = DefaultListModel<OfflineSaveJob>()
    private val jobs = JList(model)
    private val run = JButton("Run pending")
    private val pause = JButton("Pause after current chapter")
    private val retry = JButton("Retry selected")
    private val cancel = JButton("Cancel selected")
    private val refresh = JButton("Refresh")
    private val clearFinished = JButton("Clear finished/canceled records")
    private val openLibrary = JButton("Downloaded series")
    private val message = JLabel()
    private val stopAfterCurrent = AtomicBoolean(false)
    private var active = false

    init {
        minimumSize = Dimension(690, 380)
        setSize(1000, 580)
        setLocationRelativeTo(null)
        defaultCloseOperation = DISPOSE_ON_CLOSE
        addWindowListener(object : WindowAdapter() {
            override fun windowClosed(e: WindowEvent?) {
                // Never start more network work after the user closes this UI.
                // The current CBZ copy is allowed to finish its atomic publish.
                stopAfterCurrent.set(true)
            }
        })
        jobs.selectionMode = ListSelectionModel.SINGLE_SELECTION
        jobs.cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?, value: Any?, index: Int, selected: Boolean, focus: Boolean,
            ): java.awt.Component {
                val label = super.getListCellRendererComponent(list, value, index, selected, focus)
                    as JLabel
                if (value is OfflineSaveJob) {
                    label.text = "Manga ${value.mangaId} · chapter ${value.chapterId}" +
                        "  [${value.state}]  attempts ${value.attempts}" +
                        (if (value.error.isBlank()) "" else " — ${value.error.take(75)}")
                    label.toolTipText = "${value.endpoint} · Job ${value.id}"
                    label.border = BorderFactory.createEmptyBorder(8, 10, 8, 10)
                }
                return label
            }
        }
        add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            add(this@OfflineSaveQueueWindow.run)
            add(this@OfflineSaveQueueWindow.pause)
            add(this@OfflineSaveQueueWindow.retry)
            add(this@OfflineSaveQueueWindow.cancel)
            add(this@OfflineSaveQueueWindow.refresh)
            add(this@OfflineSaveQueueWindow.clearFinished)
            add(this@OfflineSaveQueueWindow.openLibrary)
        }, BorderLayout.NORTH)
        add(JScrollPane(jobs), BorderLayout.CENTER)
        add(message.apply { border = BorderFactory.createEmptyBorder(10, 14, 10, 14) },
            BorderLayout.SOUTH)
        jobs.addListSelectionListener { if (!it.valueIsAdjusting) updateControls() }
        run.addActionListener { runPending() }
        pause.addActionListener { stopAfterCurrent.set(true); pause.isEnabled = false }
        retry.addActionListener { changeSelected { queue.retry(it.id) } }
        cancel.addActionListener { changeSelected { queue.cancel(it.id) } }
        refresh.addActionListener { reload() }
        clearFinished.addActionListener { forgetFinished() }
        openLibrary.addActionListener { OfflineSeriesScreen.open() }
        reload()
    }

    private fun updateControls() {
        val selected = jobs.selectedValue
        run.isEnabled = !active && model.elements().asSequence().any {
            it.state == OfflineJobState.PENDING && it.port == currentPort
        }
        pause.isEnabled = active && !stopAfterCurrent.get()
        retry.isEnabled = !active && selected?.state in setOf(
            OfflineJobState.FAILED, OfflineJobState.INTERRUPTED)
        cancel.isEnabled = !active && selected?.state in setOf(
            OfflineJobState.PENDING, OfflineJobState.FAILED, OfflineJobState.INTERRUPTED)
        refresh.isEnabled = !active
        clearFinished.isEnabled = !active && model.elements().asSequence().any {
            it.port == currentPort && it.state in setOf(
                OfflineJobState.COMPLETED, OfflineJobState.CANCELED)
        }
    }

    private fun reload() {
        try {
            val selectedId = jobs.selectedValue?.id
            val entries = queue.list()
            model.clear()
            entries.forEach(model::addElement)
            val selectedIndex = entries.indexOfFirst { it.id == selectedId }
            if (selectedIndex >= 0) jobs.selectedIndex = selectedIndex
            if (!active) {
                val waiting = entries.count {
                    it.port == currentPort && it.state == OfflineJobState.PENDING
                }
                message.text = "$waiting pending offline save(s). Only Run pending starts copying."
            }
        } catch (e: Exception) {
            warn(e.message ?: "Cannot read the saved offline job queue")
        }
        updateControls()
    }

    private fun changeSelected(change: (OfflineSaveJob) -> Unit) {
        val selected = jobs.selectedValue ?: return
        try {
            change(selected)
            reload()
        } catch (e: Exception) {
            warn(e.message ?: "Cannot update offline job")
        }
    }

    private fun forgetFinished() {
        if (active) return
        try {
            val count = queue.list().count {
                it.port == currentPort && it.state in setOf(
                    OfflineJobState.COMPLETED, OfflineJobState.CANCELED)
            }
            if (count == 0) return
            val answer = JOptionPane.showConfirmDialog(this,
                "Forget $count completed or canceled LOCAL copy-queue record(s)?\n" +
                    "This does NOT delete saved CBZ files or downloaded manga.\n" +
                    "Pending, running, failed and interrupted jobs stay in the queue.\n" +
                    "Only records for this Suwayomi localhost port are affected.",
                "Clear finished offline copy jobs", JOptionPane.YES_NO_OPTION)
            if (answer != JOptionPane.YES_OPTION) return
            val cleared = queue.forgetFinished(endpoint)
            reload()
            message.text = "Cleared $cleared finished/canceled job record(s); comic files untouched."
        } catch (e: Exception) {
            warn(e.message ?: "Could not clear completed jobs")
        }
    }

    private fun runPending() {
        if (active) return
        val count = queue.list().count {
            it.port == currentPort && it.state == OfflineJobState.PENDING
        }
        if (count == 0) return
        val consent = JOptionPane.showConfirmDialog(this,
            "Copy $count already-downloaded Suwayomi chapter(s) to your local CBZ library?\n" +
                "This contacts only the configured local Suwayomi server ($endpoint).\n" +
                "It will not enqueue or download missing manga from external sources.\n" +
                "Pause stops between chapters; interruption requires manual Retry.",
            "Run saved offline jobs", JOptionPane.YES_NO_OPTION,
        )
        if (consent != JOptionPane.YES_OPTION) return
        active = true
        stopAfterCurrent.set(false)
        updateControls()
        object : SwingWorker<Pair<Int, Int>, String>() {
            override fun doInBackground(): Pair<Int, Int> {
                val suwayomi = SuwayomiClient(
                    endpoint, System.getenv("NOOIR_SUWAYOMI_AUTHORIZATION"))
                val saver = OfflineChapterSaver()
                var completed = 0
                var failed = 0
                while (!stopAfterCurrent.get()) {
                    val job = queue.claimNext(endpoint) ?: break
                    publish("Saving manga ${job.mangaId}, chapter ${job.chapterId}...")
                    try {
                        saver.save(suwayomi, job.mangaId, listOf(job.chapterId)) { chapter, copied, total ->
                            publish(LocalCbzCopyProgress.label(chapter.id, copied, total))
                        }
                        queue.complete(job.id)
                        completed++
                    } catch (e: Exception) {
                        queue.fail(job.id, e.message ?: "Offline CBZ import failed")
                        failed++
                    }
                    publish("Saved $completed chapter(s), failed $failed")
                }
                return completed to failed
            }

            override fun process(chunks: MutableList<String>) {
                if (!isDisplayable) return
                message.text = chunks.lastOrNull() ?: "Processing offline saves..."
                reload()
            }

            override fun done() {
                active = false
                stopAfterCurrent.set(false)
                try {
                    val (saved, failed) = get()
                    reload()
                    message.text = "Finished: $saved saved, $failed failed. " +
                        "Use Retry selected for failures."
                } catch (e: Exception) {
                    val cause = if (e is ExecutionException) e.cause ?: e else e
                    reload()
                    warn(cause.message ?: "Offline queue processing failed")
                }
                updateControls()
            }
        }.execute()
    }

    private fun warn(message: String) {
        JOptionPane.showMessageDialog(this, message,
            "MihonNooir offline saves", JOptionPane.WARNING_MESSAGE)
    }
}
