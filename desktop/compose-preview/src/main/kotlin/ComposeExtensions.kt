package dev.nooir.desktop.compose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.nooir.desktop.suwayomi.DesktopBackupExtensions
import dev.nooir.desktop.suwayomi.DesktopBackupTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class ExtensionSnapshot(
    val stores: List<DesktopBackupExtensions.Store>,
    val packages: List<DesktopBackupExtensions.Extension>,
)
private data class ExtensionApproval(
    val item: DesktopBackupExtensions.Extension,
    val store: DesktopBackupExtensions.Store,
)

@Composable
internal fun ComposeExtensions(back: () -> Unit) {
    val api = remember { DesktopBackupTransport("http://127.0.0.1:4567") }
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf<ExtensionSnapshot?>(null) }
    var loading by remember { mutableStateOf(true) }
    var installing by remember { mutableStateOf(false) }
    var revision by remember { mutableStateOf(0) }
    var search by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf("Installed") }
    var approval by remember { mutableStateOf<ExtensionApproval?>(null) }
    var status by remember { mutableStateOf("") }
    var repositoryUrl by remember { mutableStateOf("") }
    var addConfirmation by remember { mutableStateOf(false) }
    var refreshConfirmation by remember { mutableStateOf(false) }
    var addedStore by remember { mutableStateOf<DesktopBackupExtensions.Store?>(null) }
    LaunchedEffect(revision) {
        loading = true
        snapshot = null
        try {
            snapshot = withContext(Dispatchers.IO) {
                ExtensionSnapshot(api.extensionStores(), api.extensionCatalog())
            }
            if (System.getenv("NOOIR_COMPOSE_QA_CAPTURE") == "1") {
                println("QA_EXTENSION_CATALOG_READY")
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { status = "Could not read the local catalogue. Check the server and retry." }
        finally { loading = false }
    }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = back, enabled = !installing) { Text("‹ Browse") }
            Text("Extensions", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { revision++ }, enabled = !loading && !installing) { Text("Reload") }
        }
        OutlinedTextField(search, { search = it.take(200) }, label = { Text("Search extensions or repositories") },
            modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !installing)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Installed", "Available", "Repositories").forEach { name ->
                FilterChip(selected = tab == name, onClick = { tab = name }, label = { Text(name) }, enabled = !installing)
            }
        }
        if (loading || installing) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall)
        val data = snapshot
        if (data != null) {
            if (tab == "Repositories") {
                val rows = data.stores.filter { it.name.contains(search, true) || it.indexUrl.contains(search, true) }
                Text("${rows.size} configured repositories")
                LazyColumn(Modifier.weight(1f)) {
                    items(rows, key = { it.indexUrl }) { store ->
                        ListItem(headlineContent = { Text(store.name) }, supportingContent = {
                            Column {
                                Text(store.indexUrl)
                                Text(if (store.legacy) "Legacy repository" else "Modern repository")
                                Text("Signing identity: ${store.signingKey.ifBlank { "Unavailable" }}")
                            }
                        })
                        HorizontalDivider()
                    }
                }
                HorizontalDivider()
                OutlinedTextField(repositoryUrl, { repositoryUrl = it.take(2048) },
                    label = { Text("Trusted repository index URL") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), enabled = !installing)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { addConfirmation = true },
                        enabled = repositoryUrl.isNotBlank() && !installing && !loading) { Text("Add repository") }
                    OutlinedButton(onClick = { refreshConfirmation = true },
                        enabled = !installing && !loading && data.stores.isNotEmpty()) { Text("Refresh catalogue") }
                }
                Text("Review repository identity before installing any package. A refresh contacts configured repositories.", style = MaterialTheme.typography.bodySmall)
            } else {
                val rows = remember(data, search, tab) {
                    data.packages.filter { it.installed == (tab == "Installed") &&
                        (it.name.contains(search, true) || it.pkgName.contains(search, true)) }
                        .sortedBy { it.name.lowercase() }
                }
                Text("${rows.size} extensions")
                LazyColumn(Modifier.weight(1f)) {
                    items(rows, key = { it.pkgName }) { item ->
                        val store = data.stores.singleOrNull { it.indexUrl == item.indexUrl }
                        val valid = store != null && !item.obsolete && item.versionCode != null &&
                            !item.versionName.isNullOrBlank() && store.signingKey.isNotBlank() &&
                            run {
                                dev.nooir.desktop.suwayomi.DesktopBackupInspector.Store(store.name, store.indexUrl,
                                    store.signingKey, store.legacy, store.extensionListUrl.orEmpty()).reviewableHttps
                            }
                        ListItem(headlineContent = { Text(item.name) }, supportingContent = {
                            Column {
                                Text(item.pkgName)
                                Text("${item.versionName ?: "Unknown version"} · ${store?.name ?: "Unverified repository"}")
                                if (item.obsolete) Text("Obsolete")
                            }
                        }, trailingContent = {
                            if (item.installed) Text("Installed")
                            else TextButton(onClick = { if (store != null) approval = ExtensionApproval(item, store) },
                                enabled = valid && !installing) { Text(if (valid) "Install" else "Needs review") }
                        })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
    if (addConfirmation) AlertDialog(onDismissRequest = { addConfirmation = false },
        title = { Text("Add trusted repository?") },
        text = { Text("Contact this repository and add its metadata to the local reading service?\n\n${repositoryUrl.trim()}\n\nThe server may follow redirects or normalize a legacy index, including updating an existing canonical repository. Only proceed with a URL you trust. No extension is installed by this action.") },
        confirmButton = { Button(onClick = {
            val url = repositoryUrl.trim()
            addConfirmation = false
            installing = true
            addedStore = null
            status = "Contacting repository…"
            scope.launch {
                try {
                    addedStore = withContext(Dispatchers.IO) { api.addReviewedRepository(url) }
                    repositoryUrl = ""
                    status = "Repository confirmed. Review its identity, then refresh the catalogue."
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { status = "Repository addition not confirmed. Check the catalogue, URL and server TLS trust before retrying. No automatic retry." }
                finally { installing = false; revision++ }
            }
        }) { Text("Approve repository") } },
        dismissButton = { TextButton(onClick = { addConfirmation = false }) { Text("Cancel") } })
    val added = addedStore
    if (added != null) AlertDialog(onDismissRequest = { addedStore = null },
        title = { Text("Review repository identity") },
        text = { Column {
            Text(added.name)
            Text(added.indexUrl)
            Text("Signing identity: ${added.signingKey}")
            Text("Matching metadata does not prove trust. Compare this identity with the repository's official information.")
        } },
        confirmButton = { TextButton(onClick = { addedStore = null }) { Text("Close") } })
    if (refreshConfirmation) AlertDialog(onDismissRequest = { refreshConfirmation = false },
        title = { Text("Refresh extension catalogue?") },
        text = { Text("The local reading service will contact all configured repositories. Metadata and available versions may change. No extension is automatically installed.") },
        confirmButton = { Button(onClick = {
            refreshConfirmation = false
            installing = true
            status = "Refreshing catalogue…"
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { api.refreshExtensionCatalogue() }
                    status = "Catalogue refresh completed. Review current identities and available packages."
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { status = "Refresh not confirmed. Reload to inspect the current catalogue." }
                finally { installing = false; revision++ }
            }
        }) { Text("Refresh") } },
        dismissButton = { TextButton(onClick = { refreshConfirmation = false }) { Text("Cancel") } })
    val pending = approval
    if (pending != null) AlertDialog(
        onDismissRequest = { approval = null },
        title = { Text("Install trusted extension?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(pending.item.name, style = MaterialTheme.typography.titleMedium)
                Text(pending.item.pkgName)
                Text("Version: ${pending.item.versionName} (${pending.item.versionCode})")
                Text("Repository: ${pending.store.indexUrl}")
                Text("Signing identity: ${pending.store.signingKey}")
                Text("Extensions execute code in the local backend. Only install packages from repositories you trust.")
            }
        },
        confirmButton = { Button(onClick = {
            approval = null
            installing = true
            status = "Installing…"
            scope.launch {
                try {
                    val sources = withContext(Dispatchers.IO) { api.installCatalogExtension(pending.item, pending.store) }
                    status = "Installation confirmed · ${sources.size} exposed sources."
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { status = "Installation not confirmed. Review the catalogue before retrying." }
                finally { installing = false; revision++ }
            }
        }) { Text("Approve installation") } },
        dismissButton = { TextButton(onClick = { approval = null }) { Text("Cancel") } })
}
