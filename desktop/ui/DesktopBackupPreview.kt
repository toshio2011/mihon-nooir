package dev.nooir.desktop.ui

import dev.nooir.desktop.suwayomi.DesktopBackupInspector
import dev.nooir.desktop.suwayomi.DesktopBackupTransport
import java.io.File
import java.awt.Component
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.SwingWorker
import javax.swing.filechooser.FileNameExtensionFilter

/** Read-only preview; restore/export remain explicitly delegated to the local WebUI. */
object DesktopBackupPreview {
    fun show(owner: Component, endpoint: String, openWebUi: () -> Unit) {
        val actions = arrayOf("Inspect backup…", "Create library backup…", "Restore library…", "Open Suwayomi backup tools", "Cancel")
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
            1 -> {
                val target=choose(owner,true,"mihonnooir-desktop.tachibk") ?: return
                runTask(owner,"Create backup") {
                    DesktopBackupTransport(endpoint).export(target)
                    "Backup saved to "+target.absolutePath+"\nLibrary data only; repositories and Nooir settings not yet included."
                }
            }
            2 -> {
                val file=choose(owner,false,"") ?: return
                val transport=try { DesktopBackupTransport(endpoint) } catch(e:Exception) {
                    JOptionPane.showMessageDialog(owner,"Use a valid local Suwayomi server.");return
                }
                object:SwingWorker<Pair<DesktopBackupInspector.Summary,DesktopBackupTransport.Validation>,Void>(){
                    override fun doInBackground()=DesktopBackupInspector.inspect(file) to transport.validate(file)
                    override fun done(){
                        try {
                            val(s,v)=get()
                            val message=buildString{
                                appendLine("Merge "+s.favorites+" library titles and "+s.chapters+" chapter records into the local server?")
                                appendLine("Missing sources: "+v.missingSources.size+"; missing trackers: "+v.missingTrackers.size)
                                appendLine("Existing categories and progress may change. No extension installations or server settings will be restored.")
                                appendLine("A separate recovery backup is required before proceeding.")
                            }
                            if(JOptionPane.showConfirmDialog(owner,message,"Confirm library restore",JOptionPane.OK_CANCEL_OPTION,JOptionPane.WARNING_MESSAGE)!=JOptionPane.OK_OPTION)return
                            val recovery=choose(owner,true,"mihonnooir-before-restore-"+System.currentTimeMillis()+".tachibk")?:return
                            runTask(owner,"Restore library") {
                                val ticket=transport.beginRestore(file,recovery)
                                val state=transport.await(ticket)
                                "Server restore status: "+state+"\nRecovery backup: "+recovery.absolutePath+
                                    if(state=="SUCCESS") "\nReview missing sources in Suwayomi." else "\nCheck local WebUI before retrying; partial changes may exist."
                            }
                        }catch(e:Exception){JOptionPane.showMessageDialog(owner,"Backup validation failed. No restore was requested.","Restore",JOptionPane.ERROR_MESSAGE)}
                    }
                }.execute()
            }
            3 -> openWebUi()
        }
    }
    private fun choose(owner:Component,save:Boolean,name:String):File? {
        val picker=JFileChooser().apply{
            fileFilter=FileNameExtensionFilter("Mihon / Suwayomi backup","tachibk","gz")
            if(save)selectedFile=File(name)
        }
        val result=if(save)picker.showSaveDialog(owner) else picker.showOpenDialog(owner)
        return if(result==JFileChooser.APPROVE_OPTION)picker.selectedFile else null
    }
    private fun runTask(owner:Component,title:String,task:()->String) {
        val dialog=javax.swing.JDialog(javax.swing.SwingUtilities.getWindowAncestor(owner),title,java.awt.Dialog.ModalityType.APPLICATION_MODAL).apply{
            defaultCloseOperation=javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE
            add(javax.swing.JLabel("Working with the local server…",javax.swing.SwingConstants.CENTER))
            setSize(380,110);setLocationRelativeTo(owner)
        }
        object:SwingWorker<String,Void>(){
            override fun doInBackground()=task()
            override fun done(){
                dialog.dispose()
                try{JOptionPane.showMessageDialog(owner,get(),title,JOptionPane.INFORMATION_MESSAGE)}
                catch(e:Exception){JOptionPane.showMessageDialog(owner,"Operation failed or status unknown. Check the local WebUI before retrying. Any saved recovery file is retained.",title,JOptionPane.ERROR_MESSAGE)}
            }
        }.execute()
        dialog.isVisible=true
    }

}
