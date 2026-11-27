package dev.nooir.desktop.compose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.nooir.desktop.suwayomi.SuwayomiClient
import dev.nooir.desktop.suwayomi.SuwayomiDownloads
import dev.nooir.desktop.suwayomi.SuwayomiDownload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ComposeDownloads(client:SuwayomiClient,close:()->Unit) {
    val operations=LocalOperations.current
    val scope=rememberCoroutineScope()
    var status by remember{mutableStateOf<SuwayomiDownloads?>(null)}
    var error by remember{mutableStateOf("")}
    var revision by remember{mutableIntStateOf(0)}
    var busy by remember{mutableStateOf(false)}
    var confirmRunning by remember{mutableStateOf<Boolean?>(null)}
    var remove by remember{mutableStateOf<SuwayomiDownload?>(null)}
    LaunchedEffect(client,revision,busy) {
        if(busy)return@LaunchedEffect
        while(true) {
            try{status=withContext(Dispatchers.IO){client.downloadStatus()};error=""}
            catch(e:CancellationException){throw e}
            catch(e:Exception){error="Could not refresh download queue. Previous status may be stale."}
            delay(5000)
        }
    }
    fun action(work:()->Unit) {
        busy=true
        scope.launchProtected(operations){
            try{withContext(Dispatchers.IO){work()};error=""}
            catch(e:CancellationException){throw e}
            catch(e:Exception){error="Operation not confirmed. Inspect refreshed queue before retrying."}
            finally{busy=false;revision++}
        }
    }
    BasicAlertDialog(onDismissRequest={if(!busy)close()},properties=DialogProperties(usePlatformDefaultWidth=false),
        modifier=Modifier.widthIn(max=840.dp).fillMaxWidth(0.9f)){
        Surface(shape=MaterialTheme.shapes.extraLarge,tonalElevation=6.dp){
            Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                Text("Chapter download queue",style=MaterialTheme.typography.headlineSmall)
                Text("Live reading-service queue. Status is refreshed every five seconds while open.")
                val data=status
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    Text(data?.let{"Downloader: "+it.state.lowercase()} ?: "Loading",Modifier.weight(1f))
                    OutlinedButton(onClick={confirmRunning=true},enabled=!busy && error.isBlank() && data?.state=="STOPPED"){Text("Start")}
                    OutlinedButton(onClick={confirmRunning=false},enabled=!busy && error.isBlank() && data?.state=="STARTED"){Text("Stop")}
                    TextButton(onClick={revision++},enabled=!busy){Text("Reload")}
                }
                if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
                if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
                LazyColumn(Modifier.fillMaxWidth().heightIn(min=160.dp,max=400.dp)){
                    if(data?.queue?.isEmpty()==true)item{Text("No queued chapter downloads.")}
                    items(data?.queue.orEmpty(),key={it.chapterId}){row->
                        ListItem(headlineContent={Text(row.mangaTitle)},supportingContent={Text(row.chapterName+" · "+row.state.lowercase()+" · tries "+row.tries)},
                            trailingContent={
                                if(row.state in setOf("QUEUED","ERROR"))TextButton(onClick={remove=row},enabled=!busy && error.isBlank()){Text("Remove")}
                            })
                        HorizontalDivider()
                    }
                }
                Text("Removing a queued request does not delete downloaded books. Finished chapters are confirmed in manga details.",style=MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){TextButton(onClick=close,enabled=!busy){Text("Close")}}
            }
        }
    }
    val target=confirmRunning
    if(target!=null)AlertDialog(onDismissRequest={confirmRunning=null},title={Text(if(target)"Start downloader?" else "Stop downloader?")},
        text={Text("This affects the entire connected reading service, including its other clients.")},
        confirmButton={Button(onClick={confirmRunning=null;action{client.setDownloaderRunning(target)}}){Text("Confirm")}},
        dismissButton={TextButton(onClick={confirmRunning=null}){Text("Cancel")}})
    val row=remove
    if(row!=null)AlertDialog(onDismissRequest={remove=null},title={Text("Remove queued chapter?")},text={Text(row.mangaTitle+" · "+row.chapterName)},
        confirmButton={Button(onClick={remove=null;action{client.removeQueuedChapter(row.mangaId,row.chapterId)}}){Text("Remove")}},
        dismissButton={TextButton(onClick={remove=null}){Text("Cancel")}})
}
