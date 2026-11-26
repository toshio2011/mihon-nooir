package dev.nooir.desktop.ui

import dev.nooir.desktop.suwayomi.DesktopBackupInspector
import dev.nooir.desktop.suwayomi.DesktopBackupTransport
import dev.nooir.desktop.suwayomi.DesktopBackupExtensions
import dev.nooir.desktop.suwayomi.DesktopLegacyRepoIndex
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
        val actions = arrayOf("Inspect backup…", "Create portable backup…", "Restore library…", "Review missing extensions…", "Review downloaded repo index…", "Open Suwayomi backup tools")
        // A vertical list avoids a seven-button-wide dialog at high DPI.
        val choices=javax.swing.JList(actions).apply{
            name="backup-action-list"
            selectionMode=javax.swing.ListSelectionModel.SINGLE_SELECTION
            selectedIndex=0
            visibleRowCount=actions.size
        }
        val panel=javax.swing.JPanel(java.awt.BorderLayout(0,8)).apply{
            add(javax.swing.JLabel("Choose a backup or migration action."),java.awt.BorderLayout.NORTH)
            add(JScrollPane(choices).apply{preferredSize=java.awt.Dimension(330,160)},java.awt.BorderLayout.CENTER)
            add(javax.swing.JLabel("Repository installation requires separate approval."),java.awt.BorderLayout.SOUTH)
        }
        if(JOptionPane.showConfirmDialog(owner,panel,"Backup & restore",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)!=JOptionPane.OK_OPTION)return
        when (val choice=choices.selectedIndex) {
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
                                appendLine("Use Create portable backup or Restore library in the backup menu. Back up your existing library first.")
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
            3,4 -> {
                // Separate menu action below opens an explicitly selected local index.
                val file=choose(owner,false,"")?:return
                val api=try{DesktopBackupTransport(endpoint)}catch(e:Exception){
                    JOptionPane.showMessageDialog(owner,"Use a valid local server.");return
                }
                reviewExtensions(owner,api,file,choice==4)
            }
            5 -> openWebUi()
        }
    }
    private fun reviewExtensions(owner:Component,api:DesktopBackupTransport,file:File,withIndex:Boolean) {
        object:SwingWorker<DesktopBackupExtensions.Plan,Void>(){
            override fun doInBackground()=api.extensionPlan(file)
            override fun done(){
                try{
                    val plan=get()
                    if(!withIndex){showPlan(owner,api,file,plan,null);return}
                    val stores=plan.stores.filter{it.state==DesktopBackupExtensions.StoreState.PRESENT_MATCH}.map{it.backup}
                    if(stores.isEmpty()){
                        JOptionPane.showMessageDialog(owner,"No matching configured repository. Add a trusted repository manually in the local WebUI, then review again.");return
                    }
                    val labels=stores.map{it.name+" — "+it.indexUrl}.toTypedArray()
                    val selected=JOptionPane.showInputDialog(owner,
                        "Select the configured repository whose legacy index you downloaded yourself.",
                        "Choose trusted repository",JOptionPane.QUESTION_MESSAGE,null,labels,labels.first())?:return
                    val store=stores[labels.indexOf(selected)]
                    if(JOptionPane.showConfirmDialog(owner,
                            "Choose an index.min.json obtained directly from this trusted repository.\n"+
                            store.indexUrl+"\nThe index is not cryptographically verified. Never use a file from an unknown source.",
                            "Local repository evidence",JOptionPane.OK_CANCEL_OPTION,JOptionPane.WARNING_MESSAGE)!=JOptionPane.OK_OPTION)return
                    val picker=JFileChooser().apply{fileFilter=FileNameExtensionFilter("Legacy extension index JSON","json")}
                    if(picker.showOpenDialog(owner)!=JFileChooser.APPROVE_OPTION)return
                    val indexFile=picker.selectedFile
                    object:SwingWorker<Pair<DesktopBackupExtensions.Plan,DesktopLegacyRepoIndex.Evidence>,Void>(){
                        override fun doInBackground():Pair<DesktopBackupExtensions.Plan,DesktopLegacyRepoIndex.Evidence>{
                            val evidence=DesktopLegacyRepoIndex.read(indexFile,store)
                            return api.extensionPlan(file,evidence) to evidence
                        }
                        override fun done(){
                            try{val(p,e)=get();showPlan(owner,api,file,p,e)}
                            catch(ex:Exception){JOptionPane.showMessageDialog(owner,"Invalid index or repository no longer matches. No changes requested.")}
                        }
                    }.execute()
                }catch(e:Exception){JOptionPane.showMessageDialog(owner,"Could not review extensions. Check local WebUI.","Extension review",JOptionPane.ERROR_MESSAGE)}
            }
        }.execute()
    }
    private fun showPlan(owner:Component,api:DesktopBackupTransport,file:File,plan:DesktopBackupExtensions.Plan,evidence:DesktopLegacyRepoIndex.Evidence?) {
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
                                appendLine("Never-installed packages may lack source IDs. Use a trusted downloaded legacy index when needed.")
                                if(evidence!=null)appendLine("Local index SHA-256: "+evidence.sha256+" (user-supplied, not signature verified)")
                            }
                            val text=JTextArea(report,18,65).apply{isEditable=false;lineWrap=true;wrapStyleWord=true;caretPosition=0}
                            JOptionPane.showMessageDialog(owner,JScrollPane(text),"Extension migration review",JOptionPane.INFORMATION_MESSAGE)
                            if(candidates.isEmpty())return
                            val labels=candidates.map{it.name+" — "+it.pkgName}.toTypedArray()
                            val selection=javax.swing.JList(labels).apply{
                                selectionMode=javax.swing.ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
                                visibleRowCount=minOf(10,labels.size)
                                selectedIndex=0
                            }
                            val panel=javax.swing.JPanel(java.awt.BorderLayout(0,8)).apply{
                                add(javax.swing.JLabel("Select compatible packages you trust (up to 100)."),java.awt.BorderLayout.NORTH)
                                add(JScrollPane(selection),java.awt.BorderLayout.CENTER)
                                add(javax.swing.JLabel("Unsupported or unresolved sources remain in your library."),java.awt.BorderLayout.SOUTH)
                            }
                            if(JOptionPane.showConfirmDialog(owner,panel,"Recover compatible extensions",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)!=JOptionPane.OK_OPTION)return
                            val approved=selection.selectedIndices.map{candidates[it]}
                            if(approved.isEmpty() || approved.size>100)return
                            val approval=JTextArea(approved.joinToString("\n\n"){
                                "Package: "+it.pkgName+"\nRepository: "+it.indexUrl+
                                "\nSigning identity: "+it.signingKey+"\nSource IDs: "+it.sourceIds.joinToString()
                            }+"\n\nExtensions run code on your local server. Matching backup keys do not prove trust. Approve only packages and repositories you trust. Each selection will be checked again before installation; failed or changed packages will be skipped without retry.",16,65).apply{isEditable=false;lineWrap=true;wrapStyleWord=true;caretPosition=0}
                            if(JOptionPane.showConfirmDialog(owner,JScrollPane(approval),"Approve selected extension installations",JOptionPane.OK_CANCEL_OPTION,JOptionPane.WARNING_MESSAGE)!=JOptionPane.OK_OPTION)return
                            runTask(owner,"Recover selected extensions"){
                                val results=DesktopBackupExtensions.recoverBatch(approved){api.installReviewedExtension(file,it,evidence)}
                                buildString{
                                    appendLine("Confirmed packages: "+results.count{!it.needsReview}+" / "+results.size)
                                    appendLine("Confirmed source IDs: "+results.flatMap{it.confirmed}.distinct().size)
                                    results.forEach{appendLine(it.packageName+": "+if(it.needsReview)"Not confirmed — check local WebUI before retrying" else "Recovered")}
                                    appendLine("\nUnsupported, unselected or failed sources remain in your library. No manga or backup records were removed.")
                                    appendLine("Review remaining sources separately. No failed installation was retried.")
                                }
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
