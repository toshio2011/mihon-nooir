package dev.nooir.desktop.compose

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.nooir.bridge.XteinkTransferPolicy
import dev.nooir.desktop.ui.DesktopReaderTransfer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

private data class TransferApproval(val file: File, val address: String, val folder: String, val name: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ComposeTransfer(initialFile: File? = null, close: () -> Unit) {
    val operations = LocalOperations.current
    val scope = rememberCoroutineScope()
    var file by remember { mutableStateOf(initialFile) }
    var address by remember { mutableStateOf("") }
    var folder by remember { mutableStateOf("/Books") }
    var name by remember { mutableStateOf(initialFile?.name.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var status by remember { mutableStateOf("Enable File Transfer on your compatible Folio Nooir reader.") }
    var approval by remember { mutableStateOf<TransferApproval?>(null) }
    val tooLarge = file?.takeIf { it.isFile && it.length() > XteinkTransferPolicy.MAX_UPLOAD_BYTES } != null
    LaunchedEffect(Unit) {
        if (System.getenv("NOOIR_COMPOSE_QA_CAPTURE") == "1") println("QA_READER_TRANSFER_OPEN")
    }

    BasicAlertDialog(onDismissRequest = { if (!busy) close() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth(0.85f)) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Send to XTEink", style = MaterialTheme.typography.headlineSmall)
                Text("Compatible Folio Nooir Wi-Fi transfer · trusted local network only",
                    style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(file?.name ?: "No book selected", Modifier.weight(1f))
                    OutlinedButton(onClick = {
                        val picker = JFileChooser().apply {
                            dialogTitle = "Choose a local book"
                            fileFilter = FileNameExtensionFilter("Books", "epub","cbz","xtc","xtch","pdf","txt")
                            isAcceptAllFileFilterUsed = false
                        }
                        if (picker.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                            file = picker.selectedFile.absoluteFile
                            name = picker.selectedFile.name
                            status = "Review destination before sending."
                            progress = 0
                        }
                    }, enabled = !busy) { Text("Choose book") }
                }
                file?.let { selected ->
                    Text("File size: " + String.format(java.util.Locale.ROOT, "%.2f MiB", selected.length() / 1048576.0))
                }
                if (tooLarge) Text("Over 180 MiB. Keep the full book and copy it via SD card or USB where supported, or split it into smaller volumes.",
                    color = MaterialTheme.colorScheme.error)
                OutlinedTextField(address,{ address = it.take(15) },label={Text("Reader private IPv4 address")},
                    placeholder={Text("192.168.4.1")},singleLine=true,modifier=Modifier.fillMaxWidth(),enabled=!busy)
                OutlinedTextField(folder,{ folder = it.take(240) },label={Text("Existing reader folder")},
                    singleLine=true,modifier=Modifier.fillMaxWidth(),enabled=!busy)
                OutlinedTextField(name,{ name = it.take(120) },label={Text("Destination filename")},
                    singleLine=true,modifier=Modifier.fillMaxWidth(),enabled=!busy)
                if (busy) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                Text(status,style=MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                    TextButton(onClick=close,enabled=!busy){Text("Close")}
                    Button(enabled=!busy && !tooLarge && file!=null && address.isNotBlank() && name.isNotBlank(),
                        onClick={
                            try {
                                val chosen=checkNotNull(file)
                                require(chosen.isFile && chosen.canRead() && chosen.length()>0) { "Choose a readable nonempty book" }
                                DesktopReaderTransfer.privateReader(address)
                                val dest=DesktopReaderTransfer.folder(folder)
                                val filename=DesktopReaderTransfer.filename(name)
                                require(chosen.extension.equals(filename.substringAfterLast('.'),true)) { "Keep the book's original extension" }
                                require(chosen.length()<=XteinkTransferPolicy.MAX_UPLOAD_BYTES) { "Book exceeds the Wi-Fi transfer limit" }
                                approval=TransferApproval(chosen,address.trim(),dest,filename)
                            } catch(e:Exception){status=(e.message ?: "Invalid transfer settings").take(200)}
                        }) {Text("Send")}
                }
            }
        }
    }
    val pending=approval
    if(pending!=null) AlertDialog(onDismissRequest={approval=null},title={Text("Confirm Wi-Fi transfer")},
        text={Text("Send ${pending.file.name} to ${pending.address}${pending.folder}/${pending.name}?\n\nThis local HTTP connection is unencrypted. Avoid simultaneous uploads to the same destination. If transfer is not confirmed, inspect the reader before retrying.")},
        confirmButton={Button(onClick={
            approval=null;busy=true;progress=0;status="Checking reader destination…"
            scope.launchProtected(operations) {
                try {
                    withContext(Dispatchers.IO) {
                        var last=-1
                        DesktopReaderTransfer.privateReader(pending.address).send(pending.file,pending.folder,pending.name) { sent,total ->
                            val percent=(sent*100L/total).toInt()
                            if(percent!=last) {
                                last=percent
                                scope.launch {
                                    if(busy) {
                                        progress=percent
                                        status=if(percent==100) "Waiting for reader confirmation…" else "Sending $percent%"
                                    }
                                }
                            }
                        }
                    }
                    status="Reader confirmed upload and matching file size. Open the book on your reader to check readability."
                    progress=100
                }catch(e:CancellationException){throw e}
                catch(e:Exception){status=(e.message ?: "Transfer not confirmed").take(180)+". Inspect the reader before retrying; a partial file may exist."}
                finally{busy=false}
            }
        }){Text("Confirm send")}},
        dismissButton={TextButton(onClick={approval=null}){Text("Cancel")}})
}
