package dev.nooir.desktop.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import dev.nooir.bridge.ExportFormat
import dev.nooir.desktop.NooirDesktopCli
import dev.nooir.mangabridge.MangaConversionOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

private val sampleSlots = Semaphore(1)
@Composable
internal fun ComposeLiveSample(device:String,format:ExportFormat,options:MangaConversionOptions,enabled:Boolean,localInput:File?=null) {
    var file by remember(localInput){mutableStateOf<File?>(null)}
    var useInput by remember(localInput){mutableStateOf(false)}
    var first by remember{mutableStateOf(false)}
    var revision by remember{mutableIntStateOf(0)}
    var sourceIndex by remember(localInput){mutableIntStateOf(0)}
    val source=if(useInput)localInput else file
    val automatic=useInput && localInput!=null
    var sample by remember(source,automatic,sourceIndex,options.includeCover,revision){mutableStateOf<NooirDesktopCli.LocalSample?>(null)}
    var readError by remember(source,automatic,sourceIndex,options.includeCover,revision){mutableStateOf(false)}
    LaunchedEffect(source,automatic,sourceIndex,options.includeCover,revision,enabled){
        if(source==null || !enabled)return@LaunchedEffect
        try {
            sample=sampleSlots.withPermit{withContext(Dispatchers.IO){
                if(automatic) NooirDesktopCli.localSample(source,sourceIndex,options.includeCover)
                else {
                    val bytes=source.inputStream().use{it.readNBytes(25*1024*1024+1)}
                    require(bytes.isNotEmpty() && bytes.size<=25*1024*1024)
                    NooirDesktopCli.LocalSample(bytes,source.name,0,1,false,false)
                }
            }}
        }catch(e:CancellationException){throw e}
        catch(e:Exception){readError=true}
    }
    val picked=sample
    var result by remember(picked,device,format,options,first,automatic){mutableStateOf<NooirDesktopCli.SamplePreview?>(null)}
    var error by remember(picked,device,format,options,first,automatic){mutableStateOf(false)}
    var index by remember(picked,device,format,options,first,automatic){mutableIntStateOf(0)}
    LaunchedEffect(picked,device,format,options,first,automatic,enabled) {
        if(picked==null || !enabled) return@LaunchedEffect
        delay(400)
        try {
            result=sampleSlots.withPermit {
                withContext(Dispatchers.IO) {
                    val ctx=kotlin.coroutines.coroutineContext
                    NooirDesktopCli.previewImage(picked.bytes,device,format,options,
                        if(automatic)picked.firstChapterPage else first,cover=automatic && picked.cover){ctx.ensureActive()}
                }
            }
        } catch(e:CancellationException){throw e}
        catch(e:Exception){error=true}
    }
    HorizontalDivider()
    Text("Live conversion preview",style=MaterialTheme.typography.titleMedium)
    Text("Preview real rendered frames as settings change. Use the selected comic input or choose a separate sample image.",style=MaterialTheme.typography.bodySmall)
    if(localInput!=null)OutlinedButton(onClick={useInput=true;sourceIndex=0;revision++},enabled=enabled){Text("Preview selected book")}
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick={
            val picker=JFileChooser().apply{
                fileFilter=FileNameExtensionFilter("Page image","png","jpg","jpeg","webp","bmp","gif")
                isAcceptAllFileFilterUsed=false
            }
            if(picker.showOpenDialog(null)==JFileChooser.APPROVE_OPTION) {
                file=picker.selectedFile;useInput=false; revision++
            }
        },enabled=enabled){Text("Choose sample page")}
        if(source!=null) TextButton(onClick={file=null;useInput=false},enabled=enabled){Text("Clear sample")}
    }
    if(source!=null){
        Text(source.name,maxLines=1)
        if(automatic && picked!=null){
            Text(picked.name,style=MaterialTheme.typography.bodySmall,maxLines=1)
            Text(if(picked.cover)"Cover · kept intact" else if(picked.firstChapterPage)"Chapter opening page" else "Content page",style=MaterialTheme.typography.labelSmall)
            Row(verticalAlignment=Alignment.CenterVertically){
                TextButton(onClick={sourceIndex=picked.index-1},enabled=enabled && picked.index>0){Text("Previous source")}
                Text((picked.index+1).toString()+" / "+picked.count)
                TextButton(onClick={sourceIndex=picked.index+1},enabled=enabled && picked.index+1<picked.count){Text("Next source")}
            }
        }else if(!automatic)ConversionToggle("Treat as chapter first page",first,enabled){first=it}
        val data=result
        if(error || readError) Text("Could not read or render the sample. Check input format and image size.",color=MaterialTheme.colorScheme.error)
        else if(data==null) LinearProgressIndicator(Modifier.fillMaxWidth())
        else {
            var bitmap by remember(data,index){mutableStateOf<ImageBitmap?>(null)}
            LaunchedEffect(data,index){
                bitmap=withContext(Dispatchers.IO){
                    data.frames[index].inputStream().use{ImageIO.read(it)}?.let{img->
                        try{img.toComposeImageBitmap()}finally{img.flush()}
                    }
                }
            }
            bitmap?.let{Image(it,contentDescription="Rendered sample frame",modifier=Modifier.fillMaxWidth().height(260.dp),contentScale=ContentScale.Fit)}
            Row(verticalAlignment=Alignment.CenterVertically){
                TextButton(onClick={index--},enabled=index>0){Text("Previous")}
                Text((index+1).toString()+" / "+data.frames.size+(if(data.truncated)"+" else ""))
                TextButton(onClick={index++},enabled=index+1<data.frames.size){Text("Next")}
            }
            if(data.truncated) Text("Preview is limited to the first 12 frames or 24 MiB. Export is not subject to this preview limit.",style=MaterialTheme.typography.bodySmall)
        }
    }
}
