package dev.nooir.desktop.ui

import java.awt.Component
import java.awt.Desktop
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.*

/** User-mediated Windows update check. Never installs or executes remote code. */
internal object DesktopWindowsUpdateScreen {
    private val busy=AtomicBoolean(false)
    fun open(parent:Component) {
        if(busy.get()) return
        val preview=JCheckBox("Include Windows preview releases",true)
        val panel=JPanel(java.awt.GridLayout(0,1,6,6)).apply {
            add(JLabel("Installed Windows version: " + DesktopWindowsUpdates.CURRENT_VERSION + " (development)"))
            add(preview)
            add(JLabel("Android versions are ignored. Opens official GitHub only."))
        }
        if(JOptionPane.showConfirmDialog(parent,panel,"Windows update check",
                JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION) return
        val include=preview.isSelected
        if(!busy.compareAndSet(false,true)) return
        object:SwingWorker<DesktopWindowsRelease?,Unit>() {
            override fun doInBackground()=DesktopWindowsUpdates.check(include)
            override fun done() {
                busy.set(false)
                try {
                    val next=get()
                    if(next==null) {
                        JOptionPane.showMessageDialog(parent,
                            "No newer eligible Windows release found among the latest 100 published releases.")
                        return
                    }
                    val msg="Windows " + next.version + (if(next.preview) " (preview)" else "") +
                        " is available. Open official release notes?\n" +
                        "Back up your library before updating. Verify SHA256SUMS-windows.txt and the installer publisher. " +
                        "Do not bypass Windows security warnings. Nothing downloads or installs automatically."
                    if(JOptionPane.showConfirmDialog(parent,msg,"Windows updates",
                            JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION) {
                        require(Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
                        Desktop.getDesktop().browse(next.page)
                    }
                } catch(e:Exception) {
                    val cause=(e as? ExecutionException)?.cause ?: e
                    JOptionPane.showMessageDialog(parent,cause.message ?: "Could not check Windows releases",
                        "Windows updates",JOptionPane.WARNING_MESSAGE)
                }
            }
        }.execute()
    }
}
