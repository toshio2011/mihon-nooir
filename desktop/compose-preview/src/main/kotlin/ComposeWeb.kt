package dev.nooir.desktop.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.nooir.bridge.*
import dev.nooir.mangabridge.*
import dev.nooir.desktop.ui.DesktopWebExportSession
import dev.nooir.desktop.ui.DesktopWebProjects
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

internal data class ComposeWebOptions(
    val title: String,
    val author: String,
    val synopsis: String,
    val language: String,
    val coverUrl: String,
    val localCover: File?,
    val collage: Boolean,
    val format: ReflowEpubFormat,
    val style: ReflowEpubStyle,
    val imageProfile: NooirWebImageProfile,
    val images: Boolean,
    val css: String,
    val selector: String,
    val remove: String,
    val collageUrls: List<String> = emptyList(),
)

/** The same ordered chapter plan, shared web engine and publication guard as Swing. */
internal fun runComposeWebExport(entries: List<NooirWebChapterPlan.Entry>, target: File,
                                 options: ComposeWebOptions, session: DesktopWebExportSession,
                                 progress: (Int, Int, String) -> Unit = { _,_,_ -> }): NooirWebPageBook.Result {
    val plan = NooirWebChapterPlan.export(entries)
    require(options.title.isNotBlank())
    require(!(options.localCover != null && options.coverUrl.isNotBlank()) &&
        !(options.collage && (options.localCover != null || options.coverUrl.isNotBlank()))) {
        "Choose one cover source"
    }
    require(options.collageUrls.isEmpty() || options.collage && options.collageUrls.size in 2..4 && options.collageUrls.distinct().size==options.collageUrls.size) { "Choose 2–4 distinct cover URLs" }
    return session.export(target) { temp, checkpoint ->
        checkpoint()
        NooirWebPageBook.convert(plan.urls,temp,options.title.trim(),options.author,plan.titles,
            coverUrl=options.coverUrl,localCover=options.localCover?.let { NooirWebCover.loadLocal(it) },
            collageCover=options.collage,collageCoverUrls=options.collageUrls,bookDescription=options.synopsis,bookLanguage=options.language,
            epubFormat=options.format,readingStyle=options.style,customCss=options.css,
            includeImages=options.images,imageProfile=options.imageProfile,
            contentSelector=options.selector,removeSelector=options.remove,
            onPage={ n,total,message -> checkpoint(); progress(n,total,message) })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ComposeWeb(close: () -> Unit) {
    val operations = LocalOperations.current
    val sessionConversions = LocalSessionConversions.current
    val scope = rememberCoroutineScope()
    var urls by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<NooirWebChapterPlan.Entry>>(emptyList()) }
    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var synopsis by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("en") }
    var cover by remember { mutableStateOf("") }
    var localCover by remember { mutableStateOf<File?>(null) }
    var collage by remember { mutableStateOf(false) }
    var collageLinks by remember { mutableStateOf("") }
    var coverPreview by remember { mutableStateOf<ImageBitmap?>(null) }
    val projects=remember{DesktopWebProjects()}
    var projectRows by remember{mutableStateOf<List<DesktopWebProjects.Project>>(emptyList())}
    var projectId by remember{mutableStateOf("")}
    var projectName by remember{mutableStateOf("")}
    var sourceUrl by remember{mutableStateOf("")}
    var known by remember{mutableStateOf<List<String>>(emptyList())}
    var loadProject by remember{mutableStateOf<DesktopWebProjects.Project?>(null)}
    var deleteProject by remember{mutableStateOf<DesktopWebProjects.Project?>(null)}
    var saveConfirm by remember{mutableStateOf(false)}
    var newChapters by remember{mutableStateOf<List<NooirWebChapterPlan.Entry>?>(null)}
    var format by remember { mutableStateOf(ReflowEpubFormat.EPUB3) }
    var style by remember { mutableStateOf(ReflowEpubStyle.STANDARD) }
    var imageProfile by remember { mutableStateOf(NooirWebImageProfile.ORIGINAL) }
    var images by remember { mutableStateOf(true) }
    var css by remember { mutableStateOf("") }
    var selector by remember { mutableStateOf("") }
    var remove by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf("Chapters") }
    var output by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Enter a public HTTPS story URL or paste chapter links.") }
    var chapterPreview by remember { mutableStateOf<Pair<String,String>?>(null) }
    var session by remember { mutableStateOf<DesktopWebExportSession?>(null) }
    var cancelRequested by remember { mutableStateOf(false) }
    fun links() = urls.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
    fun extraCovers()=if(collage)collageLinks.lineSequence().map{it.trim()}.filter{it.isNotBlank()}.toList() else emptyList()
    fun snapshot()=DesktopWebProjects.Project(projectId,projectName.trim(),sourceUrl,known,entries.toList(),title,author,synopsis,language,cover,localCover?.absolutePath.orEmpty(),collage,format==ReflowEpubFormat.EPUB2,style.name,imageProfile.name,images,css,selector,remove,extraCovers())
    fun work(label:String, action:suspend ()->Unit) {
        if(busy) return
        busy = true; status = label
        scope.launchProtected(operations) {
            try { action() }
            catch(e: CancellationException) { throw e }
            catch(e: Exception) { status = (e.message ?: "Web operation failed").take(240) }
            finally { busy = false; session = null; cancelRequested = false }
        }
    }
    LaunchedEffect(Unit) {
        if(System.getenv("NOOIR_COMPOSE_QA_CAPTURE")=="1") println("QA_WEB_EDITOR_OPEN")
    }
    BasicAlertDialog(onDismissRequest={ if(!busy) close() },
        properties=DialogProperties(usePlatformDefaultWidth=false),
        modifier=Modifier.widthIn(max=850.dp).fillMaxWidth(0.93f)) {
        Surface(shape=MaterialTheme.shapes.extraLarge,tonalElevation=6.dp) {
            Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Web to EPUB",style=MaterialTheme.typography.headlineSmall)
                Text("Public chapters only · no login, cookies or access-control bypass",style=MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf("Chapters","Book details","Settings","Projects").forEach { name ->
                        FilterChip(selected=tab==name,onClick={ tab=name },label={ Text(name) },enabled=!busy)
                    }
                }
                when(tab) {
                    "Chapters" -> {
                        OutlinedTextField(urls,{ urls=it.take(120000) },label={ Text("Story URL or chapter links") },
                            modifier=Modifier.fillMaxWidth(),maxLines=3,enabled=!busy)
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            Button(onClick={
                                val url=links().singleOrNull()
                                if(url==null) status="Enter exactly one story overview URL."
                                else work("Analyzing public story…") {
                                    val info=withContext(Dispatchers.IO) { NooirWebChapterDiscovery.inspect(url) }
                                    projectId="";projectName="";sourceUrl=info.inputUrl;known=info.chapters.map{it.url}
                                    entries=info.chapters; title=info.title; author=info.author
                                    synopsis=info.description; language=info.language.ifBlank{"en"}
                                    cover=info.detectedCoverUrl; localCover=null; collage=false;collageLinks=""
                                    status="Discovered ${entries.size} public chapters. Review selection and book details."
                                }
                            },enabled=!busy && urls.isNotBlank()) { Text("Analyze story") }
                            OutlinedButton(onClick={
                                try {
                                    val proposed=NooirWebChapterPlan.start(links().mapIndexed { i,url ->
                                        NooirWebChapterPlan.Entry(url,"Chapter " + (i+1))
                                    })
                                    NooirWebChapterPlan.export(proposed)
                                    projectId="";projectName="";sourceUrl="";known=proposed.map{it.url}
                                    title="";author="";synopsis="";language="en";cover="";localCover=null;collage=false;collageLinks=""
                                    entries=proposed; status="Added ${entries.size} chapter links."
                                } catch(e:Exception) { status=(e.message?:"Invalid chapter links").take(200) }
                            },enabled=!busy && urls.isNotBlank()) { Text("Use pasted links") }
                            TextButton(onClick={ entries=NooirWebChapterPlan.reverse(entries) },enabled=!busy && entries.size>1) { Text("Reverse") }
                        }
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Text("${entries.count{it.included}} / ${entries.size} selected",Modifier.weight(1f))
                            TextButton(onClick={ entries=NooirWebChapterPlan.chooseAll(entries,true) },enabled=!busy) { Text("All") }
                            TextButton(onClick={ entries=NooirWebChapterPlan.chooseAll(entries,false) },enabled=!busy) { Text("None") }
                        }
                        LazyColumn(Modifier.fillMaxWidth().heightIn(min=100.dp,max=220.dp)) {
                            itemsIndexed(entries,key={ _,entry -> entry.url }) { index,entry ->
                                Row(verticalAlignment=Alignment.CenterVertically) {
                                    Checkbox(entry.included,{ entries=NooirWebChapterPlan.toggle(entries,index) },enabled=!busy)
                                    Column(Modifier.weight(1f)) {
                                        OutlinedTextField(entry.title,{ value ->
                                            entries=entries.mapIndexed { i,item -> if(i==index) item.copy(title=value.take(160)) else item }
                                        },singleLine=true,enabled=!busy,modifier=Modifier.fillMaxWidth())
                                        Text(entry.url,style=MaterialTheme.typography.labelSmall,maxLines=1)
                                    }
                                    TextButton(onClick={ entries=NooirWebChapterPlan.move(entries,index,index-1) },enabled=!busy && index>0) { Text("↑") }
                                    TextButton(onClick={ entries=NooirWebChapterPlan.move(entries,index,index+1) },enabled=!busy && index+1<entries.size) { Text("↓") }
                                    TextButton(onClick={
                                        val contentSelector=selector; val removeSelector=remove
                                        work("Loading public chapter preview…") {
                                            val content=withContext(Dispatchers.IO) {
                                                NooirWebArticle.previewChapterText(NooirWebArticle.load(entry.url),entry.title,contentSelector,removeSelector)
                                            }
                                            chapterPreview=entry.title to content; status="Chapter preview loaded."
                                        }
                                    },enabled=!busy) { Text("Preview") }
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                    "Book details" -> Column(Modifier.fillMaxWidth().heightIn(max=390.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(title,{ title=it.take(240) },label={ Text("Book title") },modifier=Modifier.fillMaxWidth(),enabled=!busy)
                        OutlinedTextField(author,{ author=it.take(120) },label={ Text("Author") },modifier=Modifier.fillMaxWidth(),enabled=!busy)
                        OutlinedTextField(synopsis,{ synopsis=it.take(4000) },label={ Text("Synopsis") },modifier=Modifier.fillMaxWidth(),maxLines=4,enabled=!busy)
                        OutlinedTextField(language,{ language=it.take(32) },label={ Text("Language") },enabled=!busy)
                        OutlinedTextField(cover,{ cover=it.take(600); localCover=null; collage=false },
                            label={ Text("Detected or custom cover URL") },modifier=Modifier.fillMaxWidth(),enabled=!busy)
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            OutlinedButton(onClick={
                                val picker=JFileChooser().apply {
                                    fileFilter=FileNameExtensionFilter("JPEG / PNG","jpg","jpeg","png")
                                    isAcceptAllFileFilterUsed=false
                                }
                                if(picker.showOpenDialog(null)==JFileChooser.APPROVE_OPTION) {
                                    localCover=picker.selectedFile.absoluteFile; cover=""; collage=false
                                }
                            },enabled=!busy) { Text(localCover?.name ?: "Choose local cover") }
                            TextButton(onClick={ localCover=null; cover=""; collage=false },enabled=!busy) { Text("Clear cover") }
                        }
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Checkbox(collage,{ collage=it; if(it){cover="";localCover=null} },enabled=!busy)
                            Text("Combine 2–4 book covers")
                        }
                        if(collage) OutlinedTextField(collageLinks,{collageLinks=it.take(2400)},label={Text("Optional 2–4 public cover URLs, one per line")},modifier=Modifier.fillMaxWidth(),enabled=!busy)
                        OutlinedButton(onClick={
                            val local=localCover;val single=cover;val combined=collage;val extras=extraCovers()
                            val links=entries.filter{it.included}.map{it.url}
                            work("Loading actual cover preview…"){
                                coverPreview=withContext(Dispatchers.IO){
                                    val asset=if(local!=null)NooirWebCover.loadLocal(local) else NooirWebPageBook.previewCoverAsset(links,single,combined,extras)
                                    val decoded=DesktopWebImageCodec.decode(requireNotNull(asset){"No public cover detected. Choose a local cover or a direct image URL."})
                                    try{decoded.toComposeImageBitmap()}finally{decoded.flush()}
                                }
                                status="Cover preview loaded; remote artwork can change before export."
                            }
                        },enabled=!busy && (localCover!=null || entries.any{it.included})){Text("Preview actual cover")}
                    }
                    "Projects" -> Column(Modifier.fillMaxWidth().heightIn(max=390.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
                        LaunchedEffect(Unit){work("Loading saved projects…"){projectRows=withContext(Dispatchers.IO){projects.list()};status="Saved projects loaded."}}
                        Text("Saved web projects",style=MaterialTheme.typography.titleMedium)
                        OutlinedTextField(projectName,{projectName=it.take(100)},label={Text("Project name")},singleLine=true,enabled=!busy,modifier=Modifier.fillMaxWidth())
                        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                            Button(onClick={saveConfirm=true},enabled=!busy && projectName.isNotBlank() && entries.isNotEmpty()){Text(if(projectId.isBlank())"Save project" else "Update project")}
                            TextButton(onClick={projectId="";projectName=""},enabled=!busy){Text("Save as new")}
                            TextButton(onClick={
                                val story=sourceUrl;val inventory=known.toList();val current=entries.map{NooirWebChapterIdentity.key(it.url)}.toSet()
                                work("Checking public chapter inventory…"){
                                    val info=withContext(Dispatchers.IO){NooirWebChapterDiscovery.inspect(story)}
                                    val additions=NooirWebChapterUpdates.findNew(inventory,info.chapters).filterNot{NooirWebChapterIdentity.key(it.url) in current}
                                    if(additions.isEmpty()) status="No new chapters found."
                                    else{newChapters=additions;status="Review "+additions.size+" new chapters."}
                                }
                            },enabled=!busy && sourceUrl.isNotBlank() && known.isNotEmpty()){Text("Check new chapters")}
                        }
                        Text("Projects store local settings and chapter lists, not downloaded content. Check updates explicitly; nothing is exported automatically.",style=MaterialTheme.typography.bodySmall)
                        if(projectRows.isEmpty())Text("No saved projects.")
                        projectRows.forEach{saved->
                            ListItem(headlineContent={Text(saved.name)},supportingContent={Text(saved.chapters.size.toString()+" chapters")},
                                trailingContent={Row{
                                    TextButton(onClick={loadProject=saved},enabled=!busy){Text("Load")}
                                    TextButton(onClick={deleteProject=saved},enabled=!busy){Text("Delete")}
                                }})
                            HorizontalDivider()
                        }
                    }
                    else -> Column(Modifier.fillMaxWidth().heightIn(max=390.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text("EPUB format")
                        ExportChoice(format.name,ReflowEpubFormat.entries.map{it.name},!busy){format=ReflowEpubFormat.valueOf(it)}
                        Text("Reading style")
                        ExportChoice(style.name,ReflowEpubStyle.entries.map{it.name},!busy){style=ReflowEpubStyle.valueOf(it)}
                        Text("Illustration quality")
                        ExportChoice(imageProfile.name,NooirWebImageProfile.entries.map{it.name},!busy){imageProfile=NooirWebImageProfile.valueOf(it)}
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Checkbox(images,{images=it},enabled=!busy); Text("Include illustrations")
                        }
                        OutlinedTextField(selector,{selector=it.take(2000)},label={Text("Content selector (optional)")},modifier=Modifier.fillMaxWidth(),enabled=!busy)
                        OutlinedTextField(remove,{remove=it.take(2000)},label={Text("Remove selector (optional)")},modifier=Modifier.fillMaxWidth(),enabled=!busy)
                        OutlinedTextField(css,{css=it.take(16000)},label={Text("Custom CSS (optional)")},modifier=Modifier.fillMaxWidth(),enabled=!busy)
                    }
                }
                HorizontalDivider()
                Row(verticalAlignment=Alignment.CenterVertically) {
                    OutlinedTextField(output,{output=it},label={Text("New .epub file")},singleLine=true,modifier=Modifier.weight(1f),enabled=!busy)
                    TextButton(onClick={
                        val picker=JFileChooser().apply { selectedFile=File("MihonNooir.epub") }
                        if(picker.showSaveDialog(null)==JFileChooser.APPROVE_OPTION) output=picker.selectedFile.absolutePath
                    },enabled=!busy){Text("Browse")}
                }
                if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(status,style=MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    if(session!=null) TextButton(onClick={
                        if(session?.requestCancel()==true){cancelRequested=true;status="Cancel requested; finishing current web operation…"}
                    },enabled=!cancelRequested){Text("Cancel export")}
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick=close,enabled=!busy){Text("Close")}
                    TextButton(enabled=!busy && title.isNotBlank() && entries.any{it.included} &&
                        File(output).isAbsolute && File(output).extension.equals("epub",true),onClick={
                        val target=File(output);val plan=entries.toList()
                        val opts=ComposeWebOptions(title,author,synopsis,language,cover,localCover,collage,format,style,imageProfile,images,css,selector,remove,extraCovers())
                        try {
                            NooirWebChapterPlan.export(plan)
                            sessionConversions.submit(target,title){runComposeWebExport(plan,target,opts,DesktopWebExportSession())}
                            close()
                        }catch(e:Exception){status=(e.message ?: "Unable to queue EPUB").take(240)}
                    }){Text("Queue & close")}
                    Button(enabled=!busy && !sessionConversions.active && title.isNotBlank() && entries.any{it.included} &&
                        File(output).isAbsolute && File(output).extension.equals("epub",true),onClick={
                        val target=File(output);val plan=entries.toList()
                        val opts=ComposeWebOptions(title,author,synopsis,language,cover,localCover,collage,format,style,imageProfile,images,css,selector,remove,extraCovers())
                        val active=DesktopWebExportSession();session=active
                        work("Exporting selected chapters…") {
                            try {
                                val result=withContext(Dispatchers.IO) {
                                    runComposeWebExport(plan,target,opts,active) { n,total,message ->
                                        scope.launch { if(session===active && !cancelRequested) status="$n / $total · " + message.take(100) }
                                    }
                                }
                                status="Saved ${result.pages} chapters: ${target.name}";output=""
                            } catch(e:java.util.concurrent.CancellationException) {
                                if(cancelRequested) status="Export canceled. No new EPUB was published."
                                else throw e
                            }
                        }
                    }){Text("Export EPUB")}
                }
            }
        }
    }
    if(saveConfirm)AlertDialog(onDismissRequest={saveConfirm=false},title={Text(if(projectId.isBlank())"Save web project?" else "Replace saved project?")},
        text={Text("Save the current chapter selection and conversion settings locally? No book files will be changed.")},
        confirmButton={Button(onClick={saveConfirm=false;val draft=snapshot();work("Saving project…"){
            val saved=withContext(Dispatchers.IO){projects.save(draft)}
            projectId=saved.id;projectRows=withContext(Dispatchers.IO){projects.list()};status="Saved "+saved.name
        }}){Text("Save")}},dismissButton={TextButton(onClick={saveConfirm=false}){Text("Cancel")}})
    loadProject?.let{saved->AlertDialog(onDismissRequest={loadProject=null},title={Text("Load "+saved.name+"?")},
        text={Text("Replace the current editor contents? Unsaved changes will be lost; saved books remain untouched.")},
        confirmButton={Button(onClick={
            projectId=saved.id;projectName=saved.name;sourceUrl=saved.source;known=saved.known;entries=saved.chapters
            urls=if(saved.source.isNotBlank())saved.source else saved.chapters.joinToString("\n"){it.url}
            title=saved.title;author=saved.author;synopsis=saved.description;language=saved.language;cover=saved.coverUrl
            localCover=saved.localCover.takeIf{it.isNotBlank()}?.let(::File);collage=saved.collage;collageLinks=saved.collageUrls.joinToString("\n")
            format=if(saved.epub2)ReflowEpubFormat.EPUB2 else ReflowEpubFormat.EPUB3
            style=ReflowEpubStyle.parse(saved.style);imageProfile=NooirWebImageProfile.parse(saved.imageProfile)
            images=saved.includeImages;css=saved.css;selector=saved.selector;remove=saved.remove;output=""
            loadProject=null;tab="Chapters";status="Loaded "+saved.name
        }){Text("Load")}},dismissButton={TextButton(onClick={loadProject=null}){Text("Cancel")}})}
    deleteProject?.let{saved->AlertDialog(onDismissRequest={deleteProject=null},title={Text("Delete saved project?")},
        text={Text(saved.name+" · only saved settings are removed. EPUB files remain untouched.")},
        confirmButton={Button(onClick={deleteProject=null;work("Removing project settings…"){
            withContext(Dispatchers.IO){projects.delete(saved.id)}
            if(projectId==saved.id){projectId="";projectName=""}
            projectRows=withContext(Dispatchers.IO){projects.list()};status="Removed project settings."
        }}){Text("Delete")}},dismissButton={TextButton(onClick={deleteProject=null}){Text("Cancel")}})}
    newChapters?.let{found->AlertDialog(onDismissRequest={newChapters=null},title={Text("Review new chapters")},
        text={Column(Modifier.heightIn(max=320.dp).verticalScroll(rememberScrollState())){
            Text("Choose which newly discovered chapters to append.")
            found.forEachIndexed{index,entry->Row(verticalAlignment=Alignment.CenterVertically){
                Checkbox(entry.included,{newChapters=NooirWebChapterPlan.toggle(found,index)})
                Text(entry.title)
            }}
        }},
        confirmButton={Button(enabled=found.any{it.included},onClick={
            try{
                val selected=found.filter{it.included}
                val merged=NooirWebChapterPlan.start(entries+selected)
                NooirWebChapterPlan.export(merged.map{it.copy(included=true)})
                entries=merged
                known=(known+selected.map{it.url}).distinctBy(NooirWebChapterIdentity::key)
                status="Added "+selected.size+" chapters. Save project to retain this update.";newChapters=null
            }catch(e:Exception){status=(e.message?:"Chapter list exceeds limit").take(240);newChapters=null}
        }){Text("Add selected")}},dismissButton={TextButton(onClick={newChapters=null}){Text("Cancel")}})}
    coverPreview?.let{bitmap->AlertDialog(onDismissRequest={coverPreview=null},title={Text("Actual cover preview")},
        text={Image(bitmap,contentDescription="Selected EPUB cover",modifier=Modifier.fillMaxWidth().height(320.dp),contentScale=ContentScale.Fit)},
        confirmButton={TextButton(onClick={coverPreview=null}){Text("Close")}})}
    val preview=chapterPreview
    if(preview!=null) AlertDialog(onDismissRequest={chapterPreview=null},title={Text(preview.first)},
        text={Column(Modifier.heightIn(max=380.dp).verticalScroll(rememberScrollState())){Text(preview.second)}},
        confirmButton={TextButton(onClick={chapterPreview=null}){Text("Close")}})
}
