package dev.nooir.desktop.ui

import dev.nooir.bridge.*
import dev.nooir.mangabridge.*
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridLayout
import java.io.File
import java.nio.file.Files
import java.util.concurrent.ExecutionException
import javax.swing.*
import javax.swing.table.DefaultTableModel

/** Explicit user-driven public web import; all HTTP and rendering run off EDT. */
internal object DesktopWebImportScreen {
    fun open(parent: Component) {
        val dialog = JDialog(SwingUtilities.getWindowAncestor(parent), "Web pages & novels → EPUB",
            java.awt.Dialog.ModalityType.MODELESS)
        dialog.defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
        val urls = JTextArea(4, 40)
        val title = JTextField(30)
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
            selectionMode = ListSelectionModel.SINGLE_SELECTION
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
        var busy=false
        val controls = listOf<JComponent>(urls,title,author,description,language,cover,localPick,
            clearCover,collage,format,style,imageProfile,includeImages,css,contentSelector,removeSelector,
            table,analyze,manual,preview,up,down,reverse,all,none,export,close)
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
                    try { complete(get()) } catch(e: Exception) { fail(e) }
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
                title.text=info.title;author.text=info.author;description.text=info.description
                language.text=info.language.ifBlank{"en"};cover.text=info.detectedCoverUrl
                display(info.chapters);status.text="Discovered "+info.chapters.size+" public chapters"
            }
        }
        manual.addActionListener {
            try {
                val items=NooirWebChapterPlan.start(inputUrls().mapIndexed { i, url ->
                    NooirWebChapterPlan.Entry(url,"Chapter "+(i+1))
                })
                NooirWebChapterPlan.export(items)
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
                work("Exporting public chapters...",{
                    val temp=File.createTempFile(".nooir-web-",".epub",target.parentFile)
                    try {
                        val result=NooirWebPageBook.convert(plan.urls,temp,bookTitle,bookAuthor,plan.titles,
                            coverUrl=coverUrl,localCover=local?.let{NooirWebCover.loadLocal(it)},
                            collageCover=combine,bookDescription=desc,bookLanguage=lang,
                            epubFormat=epubFormat,readingStyle=readingStyle,customCss=custom,
                            includeImages=images,imageProfile=imgProfile,contentSelector=selector,removeSelector=remove,
                            onPage={n,total,message->SwingUtilities.invokeLater{status.text="$n / $total · "+message.take(120)}})
                        Files.move(temp.toPath(),target.toPath())
                        result
                    } finally {temp.delete()}
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
        val fields=JPanel().apply{
            layout=BoxLayout(this,BoxLayout.Y_AXIS)
            fun field(label:String,component:Component){
                add(JPanel(BorderLayout(8,3)).apply{add(JLabel(label),BorderLayout.NORTH);add(component,BorderLayout.CENTER)})
            }
            field("Story URL or one public chapter URL per line",JScrollPane(urls))
            add(JPanel(FlowLayout(FlowLayout.LEFT)).apply{add(analyze);add(manual)})
            field("Chapters (check, edit titles, reorder)",JScrollPane(table))
            add(JPanel(FlowLayout(FlowLayout.LEFT)).apply{listOf(preview,up,down,reverse,all,none).forEach{add(it)}})
            field("Book title",title);field("Author",author);field("Synopsis / description",JScrollPane(description))
            field("Language",language);field("Public cover URL (blank = auto detect)",cover)
            add(JPanel(FlowLayout(FlowLayout.LEFT)).apply{add(localPick);add(clearCover);add(localLabel)})
            add(collage)
            add(JPanel(GridLayout(0,2,5,5)).apply{
                add(JLabel("EPUB version"));add(format);add(JLabel("Reading style"));add(style)
                add(JLabel("Image size"));add(imageProfile)
            })
            add(includeImages);field("Optional content CSS selector",contentSelector)
            field("Optional remove CSS selector",removeSelector);field("Optional safe EPUB CSS",JScrollPane(css))
        }
        dialog.add(JScrollPane(fields),BorderLayout.CENTER)
        dialog.add(JPanel(BorderLayout()).apply{
            add(status,BorderLayout.CENTER)
            add(JPanel(FlowLayout(FlowLayout.RIGHT)).apply{add(export);add(close)},BorderLayout.SOUTH)
        },BorderLayout.SOUTH)
        val bounds=java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
        dialog.setSize(minOf(900,bounds.width-24),minOf(760,bounds.height-24))
        dialog.setLocationRelativeTo(parent);dialog.isVisible=true
    }
}
