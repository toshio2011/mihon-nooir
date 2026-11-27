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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

internal enum class SessionJobState { QUEUED, RUNNING, COMPLETED, FAILED, CANCELED }
internal data class SessionConversionJob(val id:String,val target:File,val label:String,
    val state:SessionJobState,val detail:String="")
internal class SessionConversions(private val scope:CoroutineScope) {
    private data class Task(val job:SessionConversionJob,val convert:()->Unit)
    private val pending=ArrayDeque<Task>()
    private val records=mutableStateListOf<SessionConversionJob>()
    val jobs:List<SessionConversionJob> get()=records.toList()
    var running by mutableStateOf(false)
        private set
    val active:Boolean get()=running || pending.isNotEmpty()
    private fun replace(id:String,state:SessionJobState,detail:String=""){
        val i=records.indexOfFirst{it.id==id}
        if(i>=0)records[i]=records[i].copy(state=state,detail=detail)
    }
    fun submit(target:File,label:String,convert:()->Unit):String {
        val out=target.canonicalFile
        require(out.isAbsolute && out.parentFile.isDirectory && !out.exists()){"Choose a new output file in an existing folder"}
        require(records.none{it.state in setOf(SessionJobState.QUEUED,SessionJobState.RUNNING) && it.target.path.equals(out.path,true)}){"Output already reserved by an active job"}
        require(records.size<500){"Clear finished activity records before adding jobs"}
        val job=SessionConversionJob(UUID.randomUUID().toString(),out,label,SessionJobState.QUEUED)
        records.add(job);pending.addLast(Task(job,convert))
        if(!running){
            running=true
            scope.launch {
                try {
                    while(pending.isNotEmpty()){
                        val task=pending.removeFirst()
                        replace(task.job.id,SessionJobState.RUNNING,"Converting…")
                        try {
                            withContext(Dispatchers.IO){
                                require(!task.job.target.exists()){"Output already exists"}
                                task.convert()
                                check(task.job.target.isFile && task.job.target.length()>0){"No output created"}
                            }
                            replace(task.job.id,SessionJobState.COMPLETED)
                        }catch(e:CancellationException){throw e}
                        catch(e:Exception){replace(task.job.id,SessionJobState.FAILED,(e.message ?: "Conversion failed").take(240))}
                    }
                }finally{running=false}
            }
        }
        return job.id
    }
    fun cancel(id:String){
        val task=pending.firstOrNull{it.job.id==id} ?: return
        pending.remove(task);replace(id,SessionJobState.CANCELED)
    }
    fun clearFinished(){records.removeAll{it.state in setOf(SessionJobState.COMPLETED,SessionJobState.FAILED,SessionJobState.CANCELED)}}
}
internal val LocalSessionConversions=staticCompositionLocalOf<SessionConversions>{error("Missing session conversion owner")}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SessionActivity(close:()->Unit,openBatch:()->Unit){
    val controller=LocalSessionConversions.current
    var preview by remember{mutableStateOf<File?>(null)}
    val jobs=controller.jobs
    BasicAlertDialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false),
        modifier=Modifier.widthIn(max=840.dp).fillMaxWidth(.9f)){
        Surface(shape=MaterialTheme.shapes.extraLarge,tonalElevation=6.dp){
            Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                Text("Activity Queue",style=MaterialTheme.typography.headlineSmall)
                val book=preview
                if(book!=null) ConvertedBookPreview(book){preview=null}
                else {
                    Text("Conversions continue while you read or browse. Close this dialog whenever you like.")
                    Text("Single-export activity is session-only; completed files stay on disk. Durable local batches are available separately.",style=MaterialTheme.typography.bodySmall)
                    if(controller.running) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(jobs.count{it.state==SessionJobState.QUEUED}.toString()+" queued · "+jobs.count{it.state==SessionJobState.RUNNING}+" running · "+jobs.count{it.state==SessionJobState.COMPLETED}+" completed")
                    LazyColumn(Modifier.fillMaxWidth().heightIn(min=160.dp,max=370.dp)){
                        if(jobs.isEmpty())item{Text("No conversions in this session.")}
                        items(jobs,key={it.id}){job->
                            ListItem(headlineContent={Text(job.label)},supportingContent={Column{
                                Text(job.target.name+" · "+job.state.name.lowercase())
                                if(job.detail.isNotBlank())Text(job.detail,style=MaterialTheme.typography.bodySmall)
                            }},trailingContent={
                                if(job.state==SessionJobState.QUEUED)TextButton(onClick={controller.cancel(job.id)}){Text("Cancel")}
                                if(job.state==SessionJobState.COMPLETED && job.target.extension.lowercase() in setOf("xtc","xtch"))
                                    TextButton(onClick={preview=job.target}){Text("Preview")}
                            })
                            HorizontalDivider()
                        }
                    }
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                        TextButton(onClick={controller.clearFinished()}){Text("Clear finished")}
                        TextButton(onClick=openBatch,enabled=!controller.active){Text("Local batch queue")}
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick=close){Text("Close")}
                    }
                }
            }
        }
    }
}
