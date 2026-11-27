package dev.nooir.desktop.compose

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

internal enum class UniversalInput(val label:String,val extensions:List<String>,val folders:Boolean=false) {
    COMIC("CBZ / ZIP",listOf("cbz","zip")),
    IMAGE("Image files / folders",listOf("png","jpg","jpeg","webp","bmp","gif"),true),
    EPUB("EPUB",listOf("epub")),
    PDF("PDF",listOf("pdf"));
    fun accepts(file:File):Boolean = folders && file.isDirectory ||
        file.isFile && file.extension.lowercase() in extensions
    fun mode():LocalConversionMode=when(this){
        EPUB->LocalConversionMode.EPUB_TEXT
        PDF->LocalConversionMode.PDF
        else->LocalConversionMode.MANGA
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ComposeUniversalHub(close:()->Unit,openBatch:()->Unit) {
    var route by remember{mutableStateOf("hub")}
    var book by remember{mutableStateOf<File?>(null)}
    when(route){
        "local"->ComposeConverter(initialInput=UniversalInput.COMIC,close={route="hub"})
        "pdf"->ComposeConverter(initialInput=UniversalInput.PDF,close={route="hub"})
        "web","novels"->ComposeWeb{route="hub"}
        "preview"->{
            val file=book
            if(file==null){LaunchedEffect(Unit){route="hub"}}
            else BasicAlertDialog(onDismissRequest={route="hub"},properties=DialogProperties(usePlatformDefaultWidth=false),
                modifier=Modifier.widthIn(max=680.dp).fillMaxWidth(0.85f)){
                Surface(shape=MaterialTheme.shapes.extraLarge,tonalElevation=6.dp){
                    Column(Modifier.padding(24.dp)){
                        Text("Finished book inspector",style=MaterialTheme.typography.headlineSmall)
                        ConvertedBookPreview(file){route="hub"}
                    }
                }
            }
        }
        else->{
            LaunchedEffect(Unit){if(System.getenv("NOOIR_COMPOSE_QA_CAPTURE")=="1")println("QA_UNIVERSAL_HUB_OPEN")}
            BasicAlertDialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false),
                modifier=Modifier.width(560.dp)){
                Surface(shape=MaterialTheme.shapes.extraLarge,tonalElevation=6.dp){
                    Column(Modifier.padding(20.dp).height(390.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                        Text("Universal Book Converter",style=MaterialTheme.typography.headlineSmall)
                        Text("Choose where your book comes from",style=MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick={route="local"},modifier=Modifier.fillMaxWidth()){Text("Local files · EPUB, CBZ, ZIP, images")}
                        OutlinedButton(onClick={route="pdf"},modifier=Modifier.fillMaxWidth()){Text("PDF files · original pages / reflow")}
                        OutlinedButton(onClick={route="web"},modifier=Modifier.fillMaxWidth()){Text("Web link · public page to EPUB")}
                        OutlinedButton(onClick={route="novels"},modifier=Modifier.fillMaxWidth()){Text("Web novels · combine chapters")}
                        OutlinedButton(onClick={
                            val picker=JFileChooser().apply{
                                fileFilter=FileNameExtensionFilter("XTC / XTCH books","xtc","xtch")
                                isAcceptAllFileFilterUsed=false
                            }
                            if(picker.showOpenDialog(null)==JFileChooser.APPROVE_OPTION){
                                val chosen=picker.selectedFile
                                if(chosen.isFile && chosen.extension.lowercase() in setOf("xtc","xtch")){
                                    book=chosen;route="preview"
                                }
                            }
                        },modifier=Modifier.fillMaxWidth()){Text("Preview finished XTC / XTCH book")}
                        TextButton(onClick={close();openBatch()}){Text("Multiple books · batch conversion")}
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){TextButton(onClick=close){Text("Close")}}
                    }
                }
            }
        }
    }
}
