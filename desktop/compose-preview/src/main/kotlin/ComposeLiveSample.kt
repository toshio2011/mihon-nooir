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
internal fun ComposeLiveSample(device:String,format:ExportFormat,options:MangaConversionOptions,enabled:Boolean) {
    var file by remember{mutableStateOf<File?>(null)}
    var first by remember{mutableStateOf(false)}
    var revision by remember{mutableIntStateOf(0)}
    val source=file
    var result by remember(source,device,format,options,first,revision){mutableStateOf<NooirDesktopCli.SamplePreview?>(null)}
    var error by remember(source,device,format,options,first,revision){mutableStateOf(false)}
    var index by remember(source,device,format,options,first,revision){mutableIntStateOf(0)}
    LaunchedEffect(source,device,format,options,first,revision,enabled) {
        if(source==null || !enabled) return@LaunchedEffect
        delay(400)
        try {
            result=sampleSlots.withPermit {
                withContext(Dispatchers.IO) {
                    val ctx=kotlin.coroutines.coroutineContext
                    val bytes=source.inputStream().use{it.readNBytes(25*1024*1024+1)}
                    NooirDesktopCli.previewImage(bytes,device,format,options,first){ctx.ensureActive()}
                }
            }
        } catch(e:CancellationException){throw e}
        catch(e:Exception){error=true}
    }
    HorizontalDivider()
    Text("Live conversion preview",style=MaterialTheme.typography.titleMedium)
    Text("Choose a local manga page. Changes to the settings update actual rendered frames automatically.",style=MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick={
            val picker=JFileChooser().apply{
                fileFilter=FileNameExtensionFilter("Page image","png","jpg","jpeg","webp","bmp","gif")
                isAcceptAllFileFilterUsed=false
            }
            if(picker.showOpenDialog(null)==JFileChooser.APPROVE_OPTION) {
                file=picker.selectedFile; revision++
            }
        },enabled=enabled){Text("Choose sample page")}
        if(source!=null) TextButton(onClick={file=null},enabled=enabled){Text("Clear sample")}
    }
    if(source!=null){
        Text(source.name,maxLines=1)
        ConversionToggle("Treat as chapter first page",first,enabled){first=it}
        val data=result
        if(error) Text("Could not render sample. Choose a supported image under 25 MiB.",color=MaterialTheme.colorScheme.error)
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
