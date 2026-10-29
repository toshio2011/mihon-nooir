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
import javax.swing.JCheckBox
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ListSelectionModel
import javax.swing.SwingWorker

/**
 * Remote Suwayomi download requests: explicit submission, honest completion
 * polling, manual restart of *monitoring* after interruption.
 * No remote cancel API is assumed, and no work auto-starts on app startup.
 */
internal object RemoteChapterQueueScreen {
    fun open(endpoint: String) {
        RemoteChapterQueueWindow(endpoint).isVisible = true
    }
}

private class RemoteChapterQueueWindow(private val endpoint: String) :
    JFrame("MihonNooir — Suwayomi download requests") {
    private val port = RemoteChapterQueue.portOf(endpoint)
    private val queue = RemoteChapterQueue()
    private val entries = DefaultListModel<RemoteChapterJob>()
    private val list = JList(entries)
    private val submit = JButton("Submit pending")
    private val stop = JButton("Stop monitoring")
    private val recheck = JButton("Recheck selected")
    private val cancel = JButton("Cancel pending request")
    private val refresh = JButton("Refresh")
    private val localJobs = JButton("Offline CBZ copy jobs")
    private val queueCompleted = JButton("Queue selected as offline CBZ")
    private val stageAfterDownload = JCheckBox("Also prepare offline CBZ jobs after verified downloads (no copying)")
    private val status = JLabel("No source download requests run without your approval")
    private val stopRequested = AtomicBoolean(false)
    private var active = false

    init {
        defaultCloseOperation = DISPOSE_ON_CLOSE
        minimumSize = Dimension(720, 400)
        setSize(1050, 590)
        setLocationRelativeTo(null)
        addWindowListener(object : WindowAdapter() {
            override fun windowClosed(e: WindowEvent?) {
                stopRequested.set(true)
            }
        })
        list.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        list.cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                l: JList<*>?, value: Any?, index: Int, selected: Boolean,
                focus: Boolean,
            ): java.awt.Component {
                val label = super.getListCellRendererComponent(
                    l, value, index, selected, focus) as JLabel
                if (value is RemoteChapterJob) {
                    label.text = "Manga ${value.mangaId} / chapter ${value.chapterId}" +
                        " — ${value.state} (requests: ${value.attempts})" +
                        if (value.error.isBlank()) "" else " — ${value.error.take(75)}"
                    label.toolTipText = value.id
                    label.border = BorderFactory.createEmptyBorder(9, 12, 9, 12)
                }
                return label
            }
        }
        add(JPanel(BorderLayout()).apply {
            add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                add(this@RemoteChapterQueueWindow.submit)
                add(this@RemoteChapterQueueWindow.stop)
                add(this@RemoteChapterQueueWindow.recheck)
                add(this@RemoteChapterQueueWindow.cancel)
                add(this@RemoteChapterQueueWindow.refresh)
                add(this@RemoteChapterQueueWindow.localJobs)
                add(this@RemoteChapterQueueWindow.queueCompleted)
            }, BorderLayout.NORTH)
            add(stageAfterDownload, BorderLayout.SOUTH)
        }, BorderLayout.NORTH)
        add(JScrollPane(list), BorderLayout.CENTER)
        add(status.apply {
            border = BorderFactory.createEmptyBorder(10, 14, 10, 14)
        }, BorderLayout.SOUTH)
        list.addListSelectionListener { if (!it.valueIsAdjusting) updateControls() }
        submit.addActionListener { submitPending() }
        stop.addActionListener { stopRequested.set(true); updateControls() }
        recheck.addActionListener { recheckSelected() }
        cancel.addActionListener {
            val job = list.selectedValue ?: return@addActionListener
            try {
                queue.cancelPending(job.id)
                reload()
            } catch (e: Exception) { warn(e.message ?: "Cannot cancel pending request") }
        }
        refresh.addActionListener { reload() }
        localJobs.addActionListener { OfflineSaveQueueScreen.open(endpoint) }
        queueCompleted.addActionListener { queueSelectedCompleted() }
        reload()
    }

    private fun updateControls() {
        val selectedJobs = list.selectedValuesList
        val selected = selectedJobs.singleOrNull()
        queueCompleted.isEnabled = !active && selectedJobs.size in 1..20 &&
            selectedJobs.all { it.port == port && it.state == RemoteChapterState.DOWNLOADED }
        submit.isEnabled = !active && entries.elements().asSequence().any {
            it.state == RemoteChapterState.PENDING
        }
        stop.isEnabled = active && !stopRequested.get()
        recheck.isEnabled = !active && selected?.state in setOf(
            RemoteChapterState.INTERRUPTED, RemoteChapterState.FAILED)
        cancel.isEnabled = !active && selected?.state == RemoteChapterState.PENDING
        refresh.isEnabled = !active
        stageAfterDownload.isEnabled = !active
    }

    private fun reload() {
        try {
            val selectedIds = list.selectedValuesList.map { it.id }.toSet()
            val jobs = queue.list().filter { it.port == port }
            entries.clear()
            jobs.forEach(entries::addElement)
            list.selectedIndices = jobs.indices.filter { jobs[it].id in selectedIds }.toIntArray()
            if (!active) status.text = "${jobs.count {
                it.state == RemoteChapterState.PENDING
            }} pending. DOWNLOADED means stored in Suwayomi; use Offline jobs to copy CBZ."
        } catch (e: Exception) {
            warn(e.message ?: "Cannot load download queue")
        }
        updateControls()
    }

    /** Save no comic files here: enqueue user-selected *completed* jobs only. */
    private fun queueSelectedCompleted() {
        if (active) return
        val selected = list.selectedValuesList
        if (selected.size !in 1..20 || selected.any {
                it.port != port || it.state != RemoteChapterState.DOWNLOADED
            }) {
            warn("Select 1 to 20 downloaded chapters from this server first")
            return
        }
        val approved = JOptionPane.showConfirmDialog(this,
            "Add " + selected.size + " completed chapter(s) to offline CBZ copy jobs?\n" +
                "This does not contact a manga source or copy any files yet.\n" +
                "Open Offline CBZ copy jobs and press Run pending to start copying.",
            "Prepare offline CBZ copies", JOptionPane.YES_NO_OPTION)
        if (approved != JOptionPane.YES_OPTION) return
        try {
            val queued = RemoteChapterOfflineHandoff(queue, OfflineSaveQueue())
                .queueDownloaded(endpoint, selected.map { it.id })
            status.text = queued.size.toString() +
                " offline CBZ job(s) ready. Open Offline CBZ copy jobs and click Run pending."
            reload()
            status.text = queued.size.toString() +
                " offline CBZ job(s) queued; copying will not start until Run pending."
        } catch (e: Exception) {
            warn(e.message ?: "Could not prepare offline CBZ copy jobs")
        }
    }

    private fun prepareAfterVerifiedDownload(job: RemoteChapterJob): Boolean =
        try {
            RemoteChapterOfflineHandoff(queue, OfflineSaveQueue())
                .queueDownloaded(endpoint, listOf(job.id))
            true
        } catch (e: Exception) {
            publishStatus("Chapter ${job.chapterId} downloaded, but offline CBZ job could not be saved: " +
                (e.message ?: "unknown error"))
            false
        }

    private fun client(): SuwayomiClient = SuwayomiClient(
        endpoint, System.getenv("NOOIR_SUWAYOMI_AUTHORIZATION"))

    private fun submitPending() {
        if (active) return
        val total = queue.list().count {
            it.port == port && it.state == RemoteChapterState.PENDING
        }
        if (total == 0) return
        val count = minOf(20, total)
        val saveJobsAfter = stageAfterDownload.isSelected
        val approved = JOptionPane.showConfirmDialog(this,
            "Submit the next $count of $total pending chapter request(s) to Suwayomi?\n" +
                "Suwayomi may contact your configured manga sources and use disk space.\n" +
                (if (saveJobsAfter)
                    "Completed chapters will prepare offline CBZ jobs, but no CBZ copies start.\n"
                 else "Offline CBZ jobs will NOT be prepared automatically.\n") +
                "Stop monitoring does NOT cancel accepted downloads.\n" +
                "Interrupted submissions are checked, never blindly re-submitted.",
            "Approve Suwayomi source downloads", JOptionPane.YES_NO_OPTION)
        if (approved != JOptionPane.YES_OPTION) return
        runTasks {
            val client = client()
            val runner = RemoteChapterRunner(queue, client)
            var completed = 0
            var prepared = 0
            var checked = 0
            while (!stopRequested.get() && checked < count) {
                val job = queue.claim(endpoint) ?: break
                checked++
                val ok = runner.runClaimed(job, { stopRequested.get() }) {
                    progress -> publishStatus(progress)
                }
                if (ok) {
                    completed++
                    if (saveJobsAfter && prepareAfterVerifiedDownload(job)) prepared++
                }
            }
            "Checked $checked request(s); $completed verified downloaded in Suwayomi; " +
                "$prepared offline CBZ jobs prepared. No CBZ files were copied."
        }
    }

    private fun recheckSelected() {
        if (active) return
        val job = list.selectedValue ?: return
        if (job.state !in setOf(RemoteChapterState.INTERRUPTED,
                RemoteChapterState.FAILED)) return
        val consent = JOptionPane.showConfirmDialog(this,
            "Check Suwayomi again for chapter ${job.chapterId}?\n" +
                "This will NOT submit another source download.\n" +
                "If the original download was never accepted, this may time out.",
            "Resume status checking only", JOptionPane.YES_NO_OPTION)
        if (consent != JOptionPane.YES_OPTION) return
        val saveJobsAfter = stageAfterDownload.isSelected
        try {
            queue.resumeMonitoring(job.id)
            val resumed = checkNotNull(queue.list().firstOrNull { it.id == job.id })
            runTasks {
                val done = RemoteChapterRunner(queue, client()).resumeMonitoring(
                    resumed, { stopRequested.get() }) { progress -> publishStatus(progress) }
                if (done) {
                    val prepared = saveJobsAfter && prepareAfterVerifiedDownload(job)
                    "Chapter ${job.chapterId} downloaded in Suwayomi." +
                        if (prepared) " Offline CBZ job prepared (not copied)." else ""
                } else "Monitoring ended. Check the request state before retrying."
            }
        } catch (e: Exception) {
            warn(e.message ?: "Cannot recheck this request")
        }
    }

    private var publishStatus: (String) -> Unit = {}

    private fun runTasks(work: () -> String) {
        active = true
        stopRequested.set(false)
        updateControls()
        object : SwingWorker<String, String>() {
            override fun doInBackground(): String {
                publishStatus = { text -> publish(text) }
                return work()
            }

            override fun process(chunks: MutableList<String>) {
                if (!isDisplayable) return
                status.text = chunks.lastOrNull() ?: "Checking downloads..."
                reload()
            }

            override fun done() {
                active = false
                stopRequested.set(false)
                publishStatus = {}
                try {
                    val result = get()
                    reload()
                    status.text = result
                } catch (e: Exception) {
                    val cause = if (e is ExecutionException) e.cause ?: e else e
                    reload()
                    warn(cause.message ?: "Download request failed")
                }
                updateControls()
            }
        }.execute()
    }

    private fun warn(message: String) {
        if (isDisplayable) JOptionPane.showMessageDialog(this, message,
            "MihonNooir downloads", JOptionPane.WARNING_MESSAGE)
    }
}
