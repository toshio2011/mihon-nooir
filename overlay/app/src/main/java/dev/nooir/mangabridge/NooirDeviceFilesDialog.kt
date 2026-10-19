package dev.nooir.mangabridge

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Browse one reader folder at a time, on demand. Full write management is
 * deliberately delegated to Folio's existing /files website so MangaNooir
 * does not duplicate risky SD rename/delete handlers or burden the ESP32.
 */
@Composable
fun NooirDeviceFilesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("manganooir_export", Context.MODE_PRIVATE) }
    val manager = remember { WorkManager.getInstance(context) }
    val scope = rememberCoroutineScope()
    var host by remember { mutableStateOf(prefs.getString("host", "crosspoint.local") ?: "crosspoint.local") }
    var folder by remember { mutableStateOf("/") }
    var files by remember { mutableStateOf<List<NooirDeviceFileClient.Item>>(emptyList()) }
    var listedFolder by remember { mutableStateOf<String?>(null) }
    var fetching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var magmaBusy by remember { mutableStateOf(false) }
    var transferBusy by remember { mutableStateOf(false) }

    LaunchedEffect(manager) {
        manager.getWorkInfosForUniqueWorkFlow(NooirMangaExportWorker.QUEUE_NAME).collect { jobs ->
            magmaBusy = jobs.any { it.state == WorkInfo.State.RUNNING }
        }
    }
    LaunchedEffect(manager) {
        val id = prefs.getString("last_transfer_work", null)?.let {
            runCatching { UUID.fromString(it) }.getOrNull()
        }
        id?.let {
            manager.getWorkInfoByIdFlow(it).collect { job ->
                transferBusy = job?.state == WorkInfo.State.RUNNING
            }
        }
    }

    val busyReader = magmaBusy || transferBusy

    fun openFolder(path: String) {
        if (fetching || busyReader) return
        fetching = true
        error = ""
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { NooirDeviceFileClient.list(host, path) }
            }
            fetching = false
            result.onSuccess {
                files = it
                folder = path
                listedFolder = path
                prefs.edit().putString("host", host.trim()).apply()
            }.onFailure {
                error = it.message ?: "Could not read the XTEink folder"
            }
        }
    }

    fun openWebManager() {
        val target = runCatching { NooirDeviceFileClient.fullWebFilesUrl(host) }
        target.onSuccess { url ->
            prefs.edit().putString("host", host.trim()).apply()
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure { error = it.message.orEmpty() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("XTEink Device Files") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 530.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Read the SD card folder you choose. No continuous scanning or thumbnails; Folio Nooir must be in File Transfer mode.")
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it; listedFolder = null; files = emptyList() },
                    label = { Text("XTEink hostname or IP") },
                    supportingText = { Text("Use the IP shown in File Transfer, without http://") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (busyReader) Text("A manga/file transfer is currently running. Folder refresh is paused to avoid extra SD/Wi-Fi work on the XTEink.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !fetching && !busyReader, onClick = { openFolder(folder) }) {
                        Text(if (listedFolder == null) "Connect & browse" else "Refresh")
                    }
                    TextButton(enabled = folder != "/" && !fetching && !busyReader,
                        onClick = { openFolder(NooirDeviceFileClient.parentPath(folder)) }) {
                        Text("Up folder")
                    }
                }
                Text("Folder: $folder")
                if (fetching) CircularProgressIndicator()
                if (error.isNotBlank()) Text("Cannot browse: $error")
                if (listedFolder == folder && !fetching) {
                    if (files.isEmpty()) Text("Empty folder (or no visible files)")
                    files.forEach { item ->
                        if (item.directory) {
                            TextButton(
                                enabled = !busyReader,
                                onClick = { openFolder(NooirDeviceFileClient.childPath(folder, item.name)) },
                            ) {
                                Text("📁 " + item.name)
                            }
                        } else {
                            val kb = (item.sizeBytes + 1023L) / 1024L
                            Text("📄 " + item.name + " · " + kb + " KiB")
                        }
                    }
                }
                Button(onClick = { openWebManager() }) {
                    Text("Open full file manager in browser")
                }
                Text("In the browser file manager you can download, rename, move, delete, or create folders. Avoid changing a file while it's uploading; confirm deletes carefully.")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
