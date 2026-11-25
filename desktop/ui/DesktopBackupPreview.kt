package dev.nooir.desktop.ui

import dev.nooir.desktop.suwayomi.DesktopBackupInspector
import java.awt.Component
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.SwingWorker
import javax.swing.filechooser.FileNameExtensionFilter

/** Read-only preview; restore/export remain explicitly delegated to the local WebUI. */
object DesktopBackupPreview {
    fun show(owner: Component, openWebUi: () -> Unit) {
        val actions = arrayOf("Inspect backup…", "Open Suwayomi backup tools", "Cancel")
        when (JOptionPane.showOptionDialog(owner,
            "Inspect an Android or desktop backup locally, or use the local Suwayomi WebUI to restore/export library data.\nRepository installation requires separate review.",
            "Backup & restore", JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, actions, actions[0])) {
            0 -> {
                val picker=JFileChooser().apply { fileFilter=FileNameExtensionFilter("Mihon / Suwayomi backup", "tachibk", "gz") }
                if(picker.showOpenDialog(owner)!=JFileChooser.APPROVE_OPTION)return
                val file=picker.selectedFile
                object:SwingWorker<DesktopBackupInspector.Summary,Void>(){
                    override fun doInBackground()=DesktopBackupInspector.inspect(file)
                    override fun done(){
                        try {
                            val s=get()
                            val report=buildString {
                                appendLine("Library titles: "+s.favorites+" / "+s.mangas+" manga records")
                                appendLine("Categories: "+s.categories)
                                appendLine("Chapters: "+s.chapters+"; read: "+s.read+"; bookmarks: "+s.bookmarks)
                                appendLine("Page positions: "+s.progressed+"; history: "+s.history)
                                appendLine("Android preferences: "+s.preferences+"; source preferences: "+s.sourcePreferences)
                                appendLine("\nReferenced source IDs: "+s.referencedSourceIds.size)
                                s.sources.forEach { appendLine(it.name+" ("+it.id+")") }
                                appendLine("\nExtension repositories to review: "+s.stores.size)
                                s.stores.forEach { appendLine(it.name+"\n  "+it.indexUrl+
                                    if(it.reviewableHttps) "\n  HTTPS — trust not yet verified" else "\n  Manual review required") }
                                appendLine("\nNo changes made. Repository URLs are not contacted, no APKs installed.")
                                appendLine("Use the local Suwayomi WebUI for backup/restore. Back up your existing library first.")
                                appendLine("Android preferences and Nooir settings are not automatically migrated.")
                            }
                            val text=JTextArea(report,20,66).apply{isEditable=false;lineWrap=true;wrapStyleWord=true;caretPosition=0}
                            JOptionPane.showMessageDialog(owner,JScrollPane(text),"Backup inspection",JOptionPane.INFORMATION_MESSAGE)
                        }catch(e:Exception){
                            JOptionPane.showMessageDialog(owner,"Invalid or unsupported backup; no changes made.","Backup inspection",JOptionPane.ERROR_MESSAGE)
                        }
                    }
                }.execute()
            }
            1 -> openWebUi()
        }
    }
}
