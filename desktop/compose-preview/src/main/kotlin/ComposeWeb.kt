package dev.nooir.desktop.compose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.nooir.bridge.*
import dev.nooir.mangabridge.*
import dev.nooir.desktop.ui.DesktopWebExportSession
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
    return session.export(target) { temp, checkpoint ->
        checkpoint()
        NooirWebPageBook.convert(plan.urls,temp,options.title.trim(),options.author,plan.titles,
            coverUrl=options.coverUrl,localCover=options.localCover?.let { NooirWebCover.loadLocal(it) },
            collageCover=options.collage,bookDescription=options.synopsis,bookLanguage=options.language,
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
                    listOf("Chapters","Book details","Settings").forEach { name ->
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
                                    entries=info.chapters; title=info.title; author=info.author
                                    synopsis=info.description; language=info.language.ifBlank{"en"}
                                    cover=info.detectedCoverUrl; localCover=null; collage=false
                                    status="Discovered ${entries.size} public chapters. Review selection and book details."
                                }
                            },enabled=!busy && urls.isNotBlank()) { Text("Analyze story") }
                            OutlinedButton(onClick={
                                try {
                                    val proposed=NooirWebChapterPlan.start(links().mapIndexed { i,url ->
                                        NooirWebChapterPlan.Entry(url,"Chapter " + (i+1))
                                    })
                                    NooirWebChapterPlan.export(proposed)
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
                            Text("Combine 2–4 detected covers")
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
                    Button(enabled=!busy && title.isNotBlank() && entries.any{it.included} &&
                        File(output).isAbsolute && File(output).extension.equals("epub",true),onClick={
                        val target=File(output);val plan=entries.toList()
                        val opts=ComposeWebOptions(title,author,synopsis,language,cover,localCover,collage,format,style,imageProfile,images,css,selector,remove)
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
    val preview=chapterPreview
    if(preview!=null) AlertDialog(onDismissRequest={chapterPreview=null},title={Text(preview.first)},
        text={Column(Modifier.heightIn(max=380.dp).verticalScroll(rememberScrollState())){Text(preview.second)}},
        confirmButton={TextButton(onClick={chapterPreview=null}){Text("Close")}})
}
