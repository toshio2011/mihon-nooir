package dev.nooir.desktop.ui

import dev.nooir.desktop.suwayomi.DesktopBackupInspector
import dev.nooir.desktop.suwayomi.DesktopBackupTransport
import dev.nooir.desktop.suwayomi.DesktopBackupExtensions
import java.io.File
import java.awt.Component
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.SwingWorker
import javax.swing.filechooser.FileNameExtensionFilter

/** Native backup tools; missing repositories still require manual WebUI review. */
object DesktopBackupPreview {
    fun show(owner: Component, endpoint: String, openWebUi: () -> Unit) {
        val actions = arrayOf("Inspect backup…", "Create portable backup…", "Restore library…", "Review missing extensions…", "Open Suwayomi backup tools", "Cancel")
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
                    DesktopBackupTransport(endpoint).export(target,includeExtensionStores=true)
                    "Backup saved to "+target.absolutePath+"\nLibrary and configured extension-store metadata included. APKs and Nooir settings are not included."
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
                                    if(state=="SUCCESS") "\nServer reports completion; review library records and missing sources in Suwayomi. Individual records may fail." else "\nCheck local WebUI before retrying; partial changes may exist."
                            }
                        }catch(e:Exception){JOptionPane.showMessageDialog(owner,"Backup validation failed. No restore was requested.","Restore",JOptionPane.ERROR_MESSAGE)}
                    }
                }.execute()
            }
            3 -> {
                val file=choose(owner,false,"")?:return
                val api=try{DesktopBackupTransport(endpoint)}catch(e:Exception){
                    JOptionPane.showMessageDialog(owner,"Use a valid local server.");return
                }
                object:SwingWorker<DesktopBackupExtensions.Plan,Void>(){
                    override fun doInBackground()=api.extensionPlan(file)
                    override fun done(){
                        try {
                            val plan=get()
                            val candidates=DesktopBackupExtensions.recoverable(plan)
                            val report=buildString{
                                appendLine("Repository comparison (no changes made):")
                                plan.stores.forEach{appendLine(it.backup.name+": "+it.state.name);appendLine("  "+it.backup.indexUrl)}
                                appendLine("\nSource comparison:")
                                plan.sources.forEach{row->
                                    appendLine(row.name+" ("+row.id+"): "+row.state.name)
                                    row.candidates.forEach{appendLine("  "+it.name+" / "+it.pkgName)}
                                }
                                appendLine("\nMissing or changed repositories must be reviewed and added manually in the local WebUI.")
                                appendLine("The cached catalog may not include every source until explicitly refreshed.")
                            }
                            val text=JTextArea(report,18,65).apply{isEditable=false;lineWrap=true;wrapStyleWord=true;caretPosition=0}
                            JOptionPane.showMessageDialog(owner,JScrollPane(text),"Extension migration review",JOptionPane.INFORMATION_MESSAGE)
                            if(candidates.isEmpty())return
                            val choices=arrayOf("Choose one extension…","Cancel")
                            if(JOptionPane.showOptionDialog(owner,"Exact source matches are available from already configured repositories. Install only extensions you trust.","Recover compatible extensions",JOptionPane.DEFAULT_OPTION,JOptionPane.WARNING_MESSAGE,null,choices,choices[1])!=0)return
                            val labels=candidates.map{it.name+" — "+it.pkgName}.toTypedArray()
                            val selected=JOptionPane.showInputDialog(owner,"Select a package to review:","Recover extension",JOptionPane.QUESTION_MESSAGE,null,labels,labels.first())?:return
                            val approved=candidates[labels.indexOf(selected)]
                            val approval=JTextArea("Package: "+approved.pkgName+"\nRepository: "+approved.indexUrl+
                                "\nSigning identity: "+approved.signingKey+"\nSource IDs: "+approved.sourceIds.joinToString()+
                                "\n\nExtensions run code in your local server. A matching backup key does not prove trust. Install this package only if you trust the repository and its code.",10,58).apply{isEditable=false;lineWrap=true;wrapStyleWord=true;caretPosition=0}
                            if(JOptionPane.showConfirmDialog(owner,JScrollPane(approval),"Approve extension installation",JOptionPane.OK_CANCEL_OPTION,JOptionPane.WARNING_MESSAGE)!=JOptionPane.OK_OPTION)return
                            runTask(owner,"Recover extension"){
                                val restored=api.installReviewedExtension(file,approved)
                                "Server confirmed "+restored.size+" requested source(s) available from "+approved.pkgName+".\nRun Review missing extensions again for remaining sources."
                            }
                        }catch(e:Exception){JOptionPane.showMessageDialog(owner,"Could not review extensions. Check local WebUI.","Extension review",JOptionPane.ERROR_MESSAGE)}
                    }
                }.execute()
            }
            4 -> openWebUi()
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
                try{val report=JTextArea(get(),16,62).apply{isEditable=false;lineWrap=true;wrapStyleWord=true;caretPosition=0};JOptionPane.showMessageDialog(owner,JScrollPane(report),title,JOptionPane.INFORMATION_MESSAGE)}
                catch(e:Exception){JOptionPane.showMessageDialog(owner,"Operation failed or status unknown. Check the local WebUI before retrying. Any saved recovery file is retained.",title,JOptionPane.ERROR_MESSAGE)}
            }
        }.execute()
        dialog.isVisible=true
    }

}
