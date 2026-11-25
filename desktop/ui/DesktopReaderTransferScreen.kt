package dev.nooir.desktop.ui

import java.awt.Component
import java.awt.GridLayout
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.*

internal object DesktopReaderTransferScreen {
    private val busy = AtomicBoolean(false)
    fun open(parent: Component) {
        if (busy.get()) {
            JOptionPane.showMessageDialog(parent, "A reader transfer is already running.")
            return
        }
        val chooser = JFileChooser().apply { dialogTitle = "Select an existing local book to send" }
        if (chooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) return
        val file = chooser.selectedFile
        val ip = JTextField(20)
        val folder = JTextField("/Books", 20)
        val name = JTextField(file.name, 24)
        val fields = JPanel(GridLayout(0, 2, 6, 6)).apply {
            add(JLabel("Reader private IPv4")); add(ip)
            add(JLabel("Existing folder")); add(folder)
            add(JLabel("Destination filename")); add(name)
            add(JLabel("Enable File Transfer on XTEINK"))
            add(JLabel("Trusted network only"))
        }
        if (JOptionPane.showConfirmDialog(parent, fields, "Send to compatible Folio Nooir reader",
                JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return
        val client: DesktopReaderTransfer
        val target: String
        val filename: String
        try {
            client = DesktopReaderTransfer.privateReader(ip.text)
            target = DesktopReaderTransfer.folder(folder.text)
            filename = DesktopReaderTransfer.filename(name.text)
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(parent, e.message, "Invalid transfer settings", JOptionPane.WARNING_MESSAGE)
            return
        }
        if (JOptionPane.showConfirmDialog(parent,
                "Send " + file.name + " to " + target + "/" + filename +
                    "?\nThe reader HTTP server is unencrypted. Do not send another file to the same name concurrently." +
                    "\nOn error, inspect the reader before retrying; a partial file may exist.",
                "Confirm Wi-Fi transfer", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return
        if (!busy.compareAndSet(false, true)) return
        val meter = JProgressBar(0, 100)
        meter.isStringPainted = true
        meter.string = "Checking reader destination"
        val dialog = JDialog(SwingUtilities.getWindowAncestor(parent), "XTEINK transfer",
            java.awt.Dialog.ModalityType.MODELESS).apply {
            defaultCloseOperation = JDialog.DO_NOTHING_ON_CLOSE
            add(meter)
            setSize(360, 100)
            setLocationRelativeTo(parent)
        }
        object : SwingWorker<Unit, Int>() {
            override fun doInBackground() {
                var last = -1
                client.send(file, target, filename) { sent, total ->
                    val percent = (sent * 100L / total).toInt()
                    if (percent != last) { publish(percent); last = percent }
                }
            }
            override fun process(chunks: MutableList<Int>) {
                val p = chunks.last()
                meter.value = p
                meter.string = if (p == 100) "Waiting for reader confirmation" else "Sending $p%"
            }
            override fun done() {
                busy.set(false)
                dialog.dispose()
                try {
                    get()
                    JOptionPane.showMessageDialog(parent, "Reader confirmed upload and file size.\n" +
                        "Open the book on your XTEINK to check readability.")
                } catch (e: Exception) {
                    val cause = (e as? ExecutionException)?.cause ?: e
                    JOptionPane.showMessageDialog(parent,
                        (cause.message ?: "Transfer failed") +
                            "\nTransfer not confirmed. Inspect reader for a partial file before retrying.",
                        "XTEINK transfer", JOptionPane.WARNING_MESSAGE)
                }
            }
        }.execute()
        dialog.isVisible = true
    }
}
