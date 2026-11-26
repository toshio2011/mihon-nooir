package dev.nooir.desktop.compose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.nooir.desktop.suwayomi.DesktopBackupInspector
import dev.nooir.desktop.suwayomi.DesktopBackupTransport
import dev.nooir.desktop.suwayomi.DesktopBackupExtensions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

private fun backupFile(save: Boolean, suggested: String = ""): File? {
    val picker = JFileChooser().apply {
        dialogTitle = if (save) "Save new backup" else "Choose Mihon backup"
        fileFilter = FileNameExtensionFilter("Mihon / Suwayomi backup", "tachibk", "gz")
        if (save) selectedFile = File(suggested)
    }
    val answer = if (save) picker.showSaveDialog(null) else picker.showOpenDialog(null)
    return if (answer == JFileChooser.APPROVE_OPTION) picker.selectedFile.absoluteFile else null
}

private data class ReviewedBackup(
    val file: File,
    val summary: DesktopBackupInspector.Summary,
    val validation: DesktopBackupTransport.Validation
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ComposeBackup(close: () -> Unit) {
    val api = remember { DesktopBackupTransport("http://127.0.0.1:4567") }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var review by remember { mutableStateOf<ReviewedBackup?>(null) }
    var plan by remember { mutableStateOf<DesktopBackupExtensions.Plan?>(null) }
    var selected by remember { mutableStateOf(setOf<DesktopBackupExtensions.Recovery>()) }
    var confirmRestore by remember { mutableStateOf(false) }
    var confirmExtensions by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Choose a backup to review. Nothing is installed automatically.") }
    val candidates = remember(plan) { plan?.let { DesktopBackupExtensions.recoverable(it) }.orEmpty() }

    fun work(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = "Operation failed or status is unknown. Review the local server before retrying." }
            finally { busy = false }
        }
    }
    BasicAlertDialog(onDismissRequest = { if (!busy) close() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(0.88f)) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Backup & restore", style = MaterialTheme.typography.headlineSmall)
                Text("Restore your library first. Missing extensions do not prevent restoration.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = !busy, onClick = {
                        val file = backupFile(false) ?: return@OutlinedButton
                        review = null; plan = null; selected = emptySet()
                        work {
                            review = withContext(Dispatchers.IO) {
                                ReviewedBackup(file, DesktopBackupInspector.inspect(file), api.validate(file))
                            }
                            message = "Backup reviewed. No changes made."
                        }
                    }) { Text("Choose backup") }
                    OutlinedButton(enabled = !busy, onClick = {
                        val target = backupFile(true, "mihonnooir-desktop.tachibk") ?: return@OutlinedButton
                        work {
                            withContext(Dispatchers.IO) { api.export(target, includeExtensionStores = true) }
                            message = "Backup saved: ${target.name}. Extension repository metadata included; APKs are not included."
                        }
                    }) { Text("Create backup") }
                }
                Column(Modifier.fillMaxWidth().heightIn(max = 350.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val current = review
                    if (current != null) {
                        Text(current.file.name, style = MaterialTheme.typography.titleMedium)
                        val summary = current.summary
                        Text("${summary.favorites} library titles · ${summary.categories} categories")
                        Text("${summary.chapters} chapters · ${summary.read} read · ${summary.bookmarks} bookmarks")
                        Text("${summary.progressed} saved positions · ${summary.history} history records")
                        Text("${current.validation.missingSources.size} missing sources · ${summary.stores.size} repository records")
                        Text("Android preferences and Nooir-specific settings are not restored automatically.",
                            style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { confirmRestore = true }, enabled = !busy) { Text("Restore library") }
                            OutlinedButton(onClick = {
                                plan = null; selected = emptySet()
                                work {
                                    plan = withContext(Dispatchers.IO) { api.extensionPlan(current.file) }
                                    message = "Select compatible packages you trust. Unsupported sources are retained."
                                }
                            }, enabled = !busy) { Text("Review extensions") }
                        }
                    }
                    val recoveryPlan = plan
                    if (recoveryPlan != null) {
                        HorizontalDivider()
                        Text("Extension recovery", style = MaterialTheme.typography.titleMedium)
                        Text("${candidates.size} compatible packages available · ${recoveryPlan.sources.count { it.state == DesktopBackupExtensions.SourceState.UNRESOLVED || it.state == DesktopBackupExtensions.SourceState.AMBIGUOUS }} unresolved sources")
                        recoveryPlan.stores.take(100).forEach { store ->
                            Text("${store.backup.name}: ${store.state.name}", style = MaterialTheme.typography.bodySmall)
                        }
                        candidates.take(100).forEach { item ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(item in selected, { checked ->
                                    selected = if (checked) selected + item else selected - item
                                }, enabled = !busy)
                                Column(Modifier.weight(1f)) {
                                    Text(item.name)
                                    Text(item.pkgName, style = MaterialTheme.typography.bodySmall)
                                    Text(item.indexUrl, style = MaterialTheme.typography.bodySmall)
                                    Text("Signing identity: ${item.signingKey}", style = MaterialTheme.typography.bodySmall)
                                    Text("Source IDs: ${item.sourceIds.joinToString()}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        Text("Only exact matches from already configured repositories are offered. Missing or changed repositories still require separate review.")
                        Button(onClick = { confirmExtensions = true }, enabled = selected.isNotEmpty() && !busy) {
                            Text("Recover selected (${selected.size})")
                        }
                    }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Column(Modifier.fillMaxWidth().heightIn(max = 110.dp).verticalScroll(rememberScrollState())) {
                    Text(message, style = MaterialTheme.typography.bodySmall)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = close, enabled = !busy) { Text("Close") }
                }
            }
        }
    }
    if (confirmRestore) AlertDialog(onDismissRequest = { confirmRestore = false },
        title = { Text("Restore library?") },
        text = { Text("This merges library records, categories and progress into the local server. Existing records may change. A separate recovery backup is required. Missing sources will not be deleted. Extensions are not installed by this action.") },
        confirmButton = { Button(onClick = {
            confirmRestore = false
            val current = review ?: return@Button
            val recovery = backupFile(true, "mihonnooir-before-restore-${System.currentTimeMillis()}.tachibk") ?: return@Button
            work {
                val state = withContext(Dispatchers.IO) {
                    val fresh = DesktopBackupInspector.inspect(current.file)
                    require(fresh == current.summary && api.validate(current.file) == current.validation) {
                        "Backup review changed"
                    }
                    api.await(api.beginRestore(current.file, recovery))
                }
                plan = null; selected = emptySet()
                message = "Server restore status: $state. Recovery backup: ${recovery.name}. Review the library before retrying; individual records may fail."
            }
        }) { Text("Choose recovery backup") } },
        dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Cancel") } })
    if (confirmExtensions) AlertDialog(onDismissRequest = { confirmExtensions = false },
        title = { Text("Recover trusted extensions?") },
        text = { Text("Install ${selected.size} explicitly selected packages? Extensions execute code in the local server. Matching repository metadata is not proof of trust. Failed or changed packages will be skipped, and no automatic retries will occur.") },
        confirmButton = { Button(onClick = {
            confirmExtensions = false
            val current = review ?: return@Button
            val approved = selected.toList()
            work {
                val results = withContext(Dispatchers.IO) {
                    DesktopBackupExtensions.recoverBatch(approved) { api.installReviewedExtension(current.file, it) }
                }
                selected = emptySet(); plan = null
                message = buildString {
                    append("${results.count { !it.needsReview }} of ${results.size} packages confirmed. No library records were deleted by recovery.")
                    results.forEach { result ->
                        append("\n${result.packageName}: ${if (result.needsReview) "Not confirmed — review before retrying" else "Recovered"}")
                    }
                }
            }
        }) { Text("Approve installation") } },
        dismissButton = { TextButton(onClick = { confirmExtensions = false }) { Text("Cancel") } })
}
