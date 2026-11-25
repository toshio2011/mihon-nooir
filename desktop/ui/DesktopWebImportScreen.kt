package dev.nooir.desktop.ui

import dev.nooir.bridge.*
import dev.nooir.mangabridge.*
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridLayout
import java.io.File
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import javax.swing.*
import javax.swing.table.DefaultTableModel

/** Explicit user-driven public web import; all HTTP and rendering run off EDT. */
internal object DesktopWebImportScreen {
    fun open(parent: Component) {
        val dialog = JDialog(SwingUtilities.getWindowAncestor(parent), "Web pages & novels → EPUB",
            java.awt.Dialog.ModalityType.MODELESS)
        dialog.defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
        val urls = JTextArea(4, 40).apply { name="web-source-urls" }
        val title = JTextField(30).apply { name="web-book-title" }
        val author = JTextField(25)
        val description = JTextArea(3,40).apply { lineWrap=true; wrapStyleWord=true }
        val language = JTextField("en", 8)
        val cover = JTextField(30)
        var localCover: File? = null
        val localLabel = JLabel("No local cover selected")
        val localPick = JButton("Choose local JPEG/PNG")
        val clearCover = JButton("Clear local cover")
        val collage = JCheckBox("Combine 2–4 detected covers")
        val format = JComboBox(arrayOf(ReflowEpubFormat.EPUB3, ReflowEpubFormat.EPUB2))
        val style = JComboBox(ReflowEpubStyle.values())
        val imageProfile = JComboBox(NooirWebImageProfile.values())
        val includeImages = JCheckBox("Include illustrations", true)
        val css = JTextArea(2,30)
        val contentSelector = JTextField(25)
        val removeSelector = JTextField(25)
        val status = JLabel("Public HTTPS only; no account cookies or locked chapters.")
        val model = object : DefaultTableModel(arrayOf("Include", "Chapter", "Public URL"), 0) {
            override fun isCellEditable(row: Int, column: Int) = column == 0 || column == 1
            override fun getColumnClass(columnIndex: Int): Class<*> =
                if (columnIndex == 0) java.lang.Boolean::class.java else String::class.java
        }
        val table = JTable(model).apply {
            name="web-chapter-table"
            setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
            rowHeight = 25
            columnModel.getColumn(0).maxWidth=70
            preferredScrollableViewportSize=Dimension(640,240)
        }
        val analyze = JButton("Analyze story chapters")
        val manual = JButton("Use pasted chapter/page URLs")
        val preview = JButton("Preview selected chapter")
        val up = JButton("Move up")
        val down = JButton("Move down")
        val reverse = JButton("Reverse order")
        val all = JButton("Select all")
        val none = JButton("Select none")
        val export = JButton("Export selected to EPUB")
        val close = JButton("Close")
        val cancelExport = JButton("Cancel export").apply { isEnabled = false }
        var activeExport: DesktopWebExportSession? = null
        val projects = DesktopWebProjects()
        val saveProject = JButton("Save project")
        val openProject = JButton("Open project")
        val deleteProject = JButton("Delete project")
        val checkUpdates = JButton("Check new chapters")
        var projectId = ""
        var projectName = ""
        var sourceUrl = ""
        var known = emptyList<String>()
        var busy=false
        val controls = listOf<JComponent>(urls,title,author,description,language,cover,localPick,
            clearCover,collage,format,style,imageProfile,includeImages,css,contentSelector,removeSelector,
            table,analyze,manual,preview,up,down,reverse,all,none,export,close,saveProject,openProject,deleteProject,checkUpdates)
        fun fail(e: Throwable) {
            val cause=(e as? ExecutionException)?.cause ?: e
            status.text=(cause.message ?: "Web import failed").take(180)
            JOptionPane.showMessageDialog(dialog,cause.message ?: "Web import failed",
                "Web EPUB",JOptionPane.WARNING_MESSAGE)
        }
        fun <T> work(label: String, task: () -> T, complete: (T) -> Unit) {
            if(busy) return
            busy=true;controls.forEach{it.isEnabled=false};status.text=label
            object : SwingWorker<T,Unit>() {
                override fun doInBackground(): T = task()
                override fun done() {
                    busy=false;controls.forEach{it.isEnabled=true}
                    activeExport=null;cancelExport.isEnabled=false
                    try { complete(get()) } catch(e: Exception) {
                        val cause=(e as? ExecutionException)?.cause ?: e
                        if(cause is CancellationException) status.text="Export canceled; no book was published"
                        else fail(e)
                    }
                }
            }.execute()
        }
        fun entries(): List<NooirWebChapterPlan.Entry> {
            if(table.isEditing) table.cellEditor.stopCellEditing()
            return (0 until model.rowCount).map { i ->
                NooirWebChapterPlan.Entry(model.getValueAt(i,2).toString(),
                    model.getValueAt(i,1).toString(), model.getValueAt(i,0)==true)
            }
        }
        fun display(items: List<NooirWebChapterPlan.Entry>) {
            model.rowCount=0
            items.forEach { model.addRow(arrayOf<Any>(it.included,it.title,it.url)) }
        }
        fun inputUrls()=urls.text.lineSequence().map{it.trim()}.filter{it.isNotBlank()}.toList()
        analyze.addActionListener {
            val link=inputUrls().singleOrNull()
            if(link==null) { fail(IllegalArgumentException("Enter one public story overview URL"));return@addActionListener }
            work("Analyzing public story...",{NooirWebChapterDiscovery.inspect(link)}) { info ->
                projectId="";projectName="";sourceUrl=info.inputUrl;known=info.chapters.map{it.url}
                title.text=info.title;author.text=info.author;description.text=info.description
                language.text=info.language.ifBlank{"en"};cover.text=info.detectedCoverUrl
                localCover=null;localLabel.text="No local cover selected";collage.isSelected=false
                display(info.chapters);status.text="Discovered "+info.chapters.size+" public chapters"
            }
        }
        manual.addActionListener {
            try {
                val items=NooirWebChapterPlan.start(inputUrls().mapIndexed { i, url ->
                    NooirWebChapterPlan.Entry(url,"Chapter "+(i+1))
                })
                NooirWebChapterPlan.export(items)
                projectId="";projectName="";sourceUrl="";known=items.map{it.url}
                display(items)
                status.text="Added "+items.size+" links; edit titles and order before exporting"
            } catch(e:Exception){fail(e)}
        }
        preview.addActionListener {
            val entry=entries().getOrNull(table.selectedRow) ?: return@addActionListener
            val selector=contentSelector.text;val remove=removeSelector.text
            work("Loading public chapter preview...",{
                NooirWebArticle.previewChapterText(NooirWebArticle.load(entry.url),entry.title,selector,remove)
            }) { result ->
                val area=JTextArea(result,16,50).apply{isEditable=false;lineWrap=true;wrapStyleWord=true}
                status.text="Chapter preview loaded"
                JOptionPane.showMessageDialog(dialog,JScrollPane(area),entry.title,JOptionPane.PLAIN_MESSAGE)
            }
        }
        fun move(delta:Int) {
            val i=table.selectedRow
            if(i<0 || i+delta !in 0 until model.rowCount)return
            display(NooirWebChapterPlan.move(entries(),i,i+delta))
            table.setRowSelectionInterval(i+delta,i+delta)
        }
        up.addActionListener{move(-1)};down.addActionListener{move(1)}
        reverse.addActionListener{display(NooirWebChapterPlan.reverse(entries()))}
        all.addActionListener{display(NooirWebChapterPlan.chooseAll(entries(),true))}
        none.addActionListener{display(NooirWebChapterPlan.chooseAll(entries(),false))}
        localPick.addActionListener{
            val picker=JFileChooser().apply {
                fileFilter=javax.swing.filechooser.FileNameExtensionFilter("JPEG or PNG","jpg","jpeg","png")
                isAcceptAllFileFilterUsed=false
            }
            if(picker.showOpenDialog(dialog)==JFileChooser.APPROVE_OPTION){
                localCover=picker.selectedFile;localLabel.text=picker.selectedFile.name
                cover.text="";collage.isSelected=false
            }
        }
        clearCover.addActionListener{localCover=null;localLabel.text="No local cover selected"}
        fun chooseProject():DesktopWebProjects.Project? {
            val items=projects.list()
            if(items.isEmpty()){status.text="No saved web projects";return null}
            return JOptionPane.showInputDialog(dialog,"Choose a saved project","Web projects",
                JOptionPane.PLAIN_MESSAGE,null,items.toTypedArray(),items.first()) as? DesktopWebProjects.Project
        }
        saveProject.addActionListener{
            try{
                val name=JOptionPane.showInputDialog(dialog,"Project name",projectName.ifBlank{title.text.take(100)})?.trim()
                    ?: return@addActionListener
                val saved=projects.save(DesktopWebProjects.Project(projectId,name,sourceUrl,known,entries(),
                    title.text,author.text,description.text,language.text,cover.text.trim(),
                    localCover?.absolutePath.orEmpty(),collage.isSelected,
                    format.selectedItem==ReflowEpubFormat.EPUB2,(style.selectedItem as ReflowEpubStyle).name,
                    (imageProfile.selectedItem as NooirWebImageProfile).name,includeImages.isSelected,
                    css.text,contentSelector.text,removeSelector.text))
                projectId=saved.id;projectName=saved.name;status.text="Saved project: "+saved.name
            }catch(e:Exception){fail(e)}
        }
        openProject.addActionListener{
            try{
                val p=chooseProject() ?: return@addActionListener
                if(JOptionPane.showConfirmDialog(dialog,"Replace the current editor contents with "+p.name+"?",
                    "Open project",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return@addActionListener
                projectId=p.id;projectName=p.name;sourceUrl=p.source;known=p.known
                urls.text=p.source.ifBlank{p.chapters.joinToString("\n"){it.url}}
                display(p.chapters)
                title.text=p.title;author.text=p.author;description.text=p.description;language.text=p.language
                cover.text=p.coverUrl;localCover=p.localCover.takeIf{it.isNotBlank()}?.let{File(it)}
                localLabel.text=localCover?.let{if(it.isFile)it.name else "Missing cover: choose again"} ?: "No local cover selected"
                collage.isSelected=p.collage;format.selectedItem=if(p.epub2)ReflowEpubFormat.EPUB2 else ReflowEpubFormat.EPUB3
                style.selectedItem=ReflowEpubStyle.parse(p.style);imageProfile.selectedItem=NooirWebImageProfile.parse(p.imageProfile)
                includeImages.isSelected=p.includeImages;css.text=p.css;contentSelector.text=p.selector;removeSelector.text=p.remove
                status.text="Opened project: "+p.name
            }catch(e:Exception){fail(e)}
        }
        deleteProject.addActionListener{
            try{
                val p=chooseProject() ?: return@addActionListener
                if(JOptionPane.showConfirmDialog(dialog,"Delete project settings only? Exported books and local covers are untouched.",
                    p.name,JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION){
                    projects.delete(p.id)
                    if(projectId==p.id){projectId="";projectName=""}
                    status.text="Deleted project settings: "+p.name
                }
            }catch(e:Exception){fail(e)}
        }
        checkUpdates.addActionListener{
            val story=sourceUrl;val inventory=known.toList();val current=entries()
            if(story.isBlank()||inventory.isEmpty()){
                status.text="Analyze a story or open a saved story project first";return@addActionListener
            }
            work("Checking public chapter inventory...",{NooirWebChapterDiscovery.inspect(story)}){ info ->
                val additions=NooirWebChapterUpdates.findNew(inventory,info.chapters)
                    .filterNot{item->current.any{NooirWebChapterIdentity.key(it.url)==NooirWebChapterIdentity.key(item.url)}}
                if(additions.isEmpty()){status.text="No new public chapters found"}
                else if(JOptionPane.showConfirmDialog(dialog,"Append "+additions.size+" new chapters to this checklist?",
                    "New chapters",JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION){
                    display(current+additions)
                    known=(inventory+info.chapters.map{it.url}).distinctBy(NooirWebChapterIdentity::key)
                    status.text="Appended "+additions.size+" chapters. Save project to retain changes."
                }else{status.text="New chapters not added; inventory unchanged"}
            }
        }
        cancelExport.addActionListener {
            if(activeExport?.requestCancel()==true) {
                cancelExport.isEnabled=false
                status.text="Cancel requested — finishing current bounded web operation"
            }
        }
        export.addActionListener {
            try {
                val plan=NooirWebChapterPlan.export(entries())
                val bookTitle=title.text.trim();require(bookTitle.isNotBlank()){"Enter a book title"}
                val bookAuthor=author.text;val desc=description.text;val lang=language.text
                val coverUrl=cover.text.trim();val local=localCover;val combine=collage.isSelected
                require(!(local!=null && coverUrl.isNotBlank()) && !(combine && (local!=null||coverUrl.isNotBlank()))) {
                    "Choose one cover source: local, URL or combined"
                }
                val epubFormat=format.selectedItem as ReflowEpubFormat
                val readingStyle=style.selectedItem as ReflowEpubStyle
                val imgProfile=imageProfile.selectedItem as NooirWebImageProfile
                val images=includeImages.isSelected;val custom=css.text
                val selector=contentSelector.text;val remove=removeSelector.text
                val picker=JFileChooser().apply{selectedFile=File(DesktopUiModel.suggestedFile(bookTitle,ExportFormat.EPUB))}
                if(picker.showSaveDialog(dialog)!=JFileChooser.APPROVE_OPTION)return@addActionListener
                val target=picker.selectedFile.canonicalFile
                require(target.extension.equals("epub",true) && !target.exists() && target.parentFile.isDirectory){
                    "Choose a new .epub filename; existing files are never overwritten"
                }
                val session=DesktopWebExportSession()
                activeExport=session
                cancelExport.isEnabled=true
                work("Exporting public chapters...",{
                    session.export(target){temp,checkpoint ->
                        checkpoint()
                        NooirWebPageBook.convert(plan.urls,temp,bookTitle,bookAuthor,plan.titles,
                            coverUrl=coverUrl,localCover=local?.let{NooirWebCover.loadLocal(it)},
                            collageCover=combine,bookDescription=desc,bookLanguage=lang,
                            epubFormat=epubFormat,readingStyle=readingStyle,customCss=custom,
                            includeImages=images,imageProfile=imgProfile,contentSelector=selector,removeSelector=remove,
                            onPage={n,total,message->
                                checkpoint()
                                SwingUtilities.invokeLater{
                                    if(activeExport===session && cancelExport.isEnabled)
                                        status.text="$n / $total · "+message.take(120)
                                }
                            })
                    }
                }) { result ->
                    status.text="Saved "+result.pages+" chapters: "+target.name
                    JOptionPane.showMessageDialog(dialog,status.text)
                }
            }catch(e:Exception){fail(e)}
        }
        close.addActionListener{if(!busy)dialog.dispose()}
        dialog.addWindowListener(object:java.awt.event.WindowAdapter(){
            override fun windowClosing(e:java.awt.event.WindowEvent?){if(!busy)dialog.dispose()}
        })
        // Separate the task into three steps; keep project and export controls visible.
        val tabs = JTabbedPane().apply { name="web-editor-tabs"; DesktopVisualTheme.tabs(this) }
        DesktopVisualTheme.table(table)
        fun section(): JPanel = JPanel().apply {
            layout=BoxLayout(this,BoxLayout.Y_AXIS)
            border=BorderFactory.createEmptyBorder(12,14,12,14)
        }
        fun JPanel.field(label:String,component:Component){
            add(JPanel(BorderLayout(8,5)).apply{
                isOpaque=false
                add(JLabel(label).apply{foreground=DesktopVisualTheme.muted},BorderLayout.NORTH)
                add(component,BorderLayout.CENTER)
                maximumSize=Dimension(Int.MAX_VALUE,preferredSize.height+8)
            })
            add(Box.createVerticalStrut(10))
        }
        fun actions(vararg buttons:JButton): JPanel = JPanel(FlowLayout(FlowLayout.LEFT,6,4)).apply{
            isOpaque=false;buttons.forEach{add(it)}
        }
        val chapters=JPanel(BorderLayout(0,8)).apply{
            border=BorderFactory.createEmptyBorder(12,14,12,14)
            add(JPanel(BorderLayout(0,5)).apply{
                isOpaque=false
                add(JLabel("Story URL or one public chapter URL per line").apply{
                    foreground=DesktopVisualTheme.muted
                },BorderLayout.NORTH)
                add(JScrollPane(urls),BorderLayout.CENTER)
                add(actions(analyze,manual),BorderLayout.SOUTH)
            },BorderLayout.NORTH)
            add(JScrollPane(table),BorderLayout.CENTER)
            add(actions(preview,up,down,reverse,all,none),BorderLayout.SOUTH)
        }
        val book=section().apply{
            field("Book title",title)
            field("Author",author)
            field("Synopsis / description",JScrollPane(description))
            field("Language",language)
            field("Public cover URL (blank = auto detect)",cover)
            add(JPanel(BorderLayout(8,0)).apply{
                isOpaque=false
                add(actions(localPick,clearCover),BorderLayout.WEST)
                add(localLabel,BorderLayout.CENTER)
            })
            add(collage)
            add(Box.createVerticalGlue())
        }
        val settings=section().apply{
            field("EPUB version",format)
            field("Reading style",style)
            field("Image size",imageProfile)
            add(includeImages)
            add(Box.createVerticalStrut(10))
            field("Optional content CSS selector",contentSelector)
            field("Optional remove CSS selector",removeSelector)
            field("Optional safe EPUB CSS",JScrollPane(css))
            add(Box.createVerticalGlue())
        }
        tabs.addTab("1  Chapters",chapters)
        tabs.addTab("2  Book details",JScrollPane(book))
        tabs.addTab("3  Export settings",JScrollPane(settings))
        controls.filterIsInstance<JButton>().forEach{DesktopVisualTheme.action(it,it===export||it===analyze)}
        DesktopVisualTheme.action(cancelExport)
        dialog.contentPane.background=DesktopVisualTheme.background
        dialog.add(JPanel(BorderLayout(0,5)).apply{
            border=BorderFactory.createEmptyBorder(10,12,6,12)
            add(JLabel("Web → EPUB").apply{
                font=java.awt.Font("SansSerif",java.awt.Font.BOLD,20)
                foreground=DesktopVisualTheme.foreground
            },BorderLayout.NORTH)
            add(actions(saveProject,openProject,deleteProject,checkUpdates),BorderLayout.SOUTH)
        },BorderLayout.NORTH)
        dialog.add(tabs,BorderLayout.CENTER)
        dialog.add(JPanel(BorderLayout()).apply{
            border=BorderFactory.createEmptyBorder(5,12,8,12)
            add(status,BorderLayout.CENTER)
            add(JPanel(FlowLayout(FlowLayout.RIGHT)).apply{add(export);add(cancelExport);add(close)},BorderLayout.SOUTH)
        },BorderLayout.SOUTH)
        val bounds=java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
        dialog.setSize(minOf(900,bounds.width-24),minOf(760,bounds.height-24))
        dialog.setLocationRelativeTo(parent);dialog.isVisible=true
    }
}
