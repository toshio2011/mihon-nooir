package dev.nooir.desktop.compose

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.nooir.desktop.storage.DesktopDataHome
import dev.nooir.desktop.suwayomi.OfficialSuwayomiCache
import dev.nooir.desktop.suwayomi.SuwayomiClient
import dev.nooir.desktop.suwayomi.SuwayomiProcessManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

internal sealed interface BackendState {
    data object Checking : BackendState
    data class Ready(val owned: Boolean) : BackendState
    data object SetupRequired : BackendState
    data class Unavailable(val explanation: String) : BackendState
}

/** Reuses the verified pinned backend and existing data root; never takes ownership
 * of an already-bound service. Download is separate from automatic cached startup. */
internal class ComposeBackendSession : Closeable {
    private val manager = SuwayomiProcessManager()
    private val cache = OfficialSuwayomiCache()
    private val closed = AtomicBoolean(false)
    fun connectOrStartCached(): BackendState {
        check(!closed.get())
        if (SuwayomiProcessManager.portOccupied(SuwayomiProcessManager.DEFAULT_PORT)) {
            return if (runCatching { SuwayomiClient().library(0, 1) }.isSuccess) {
                BackendState.Ready(manager.running)
            } else BackendState.Unavailable("Port 4567 is occupied, but the local library API is unavailable. MihonNooir will not terminate that service.")
        }
        val jar = cache.cachedOrNull() ?: return BackendState.SetupRequired
        check(!closed.get())
        manager.start(jar)
        return BackendState.Ready(true)
    }

    /** Must only be called following explicit first-time setup confirmation. */
    fun downloadAndStart(): BackendState {
        check(!closed.get())
        if (SuwayomiProcessManager.portOccupied(SuwayomiProcessManager.DEFAULT_PORT)) return connectOrStartCached()
        cache.obtain()
        check(!closed.get())
        return connectOrStartCached()
    }

    override fun close() {
        closed.set(true)
        manager.close()
    }
}

@Composable
internal fun ComposeBackendSetup(session: ComposeBackendSession, state: BackendState,
                                 update: (BackendState) -> Unit, retry: () -> Unit) {
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Text("MihonNooir", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(16.dp))
        when (state) {
            BackendState.Checking -> {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("Preparing your library…")
            }
            BackendState.SetupRequired -> {
                Text("Set up your reading service", style = MaterialTheme.typography.titleLarge)
                Text("First-time setup downloads the pinned official Suwayomi server. Once verified and cached, it can start automatically.")
                Text("Existing library data remains in the configured data folder.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { confirm = true }) { Text("Set up MihonNooir") }
                TextButton(onClick = retry) { Text("Connect to existing service") }
            }
            is BackendState.Unavailable -> {
                Text(state.explanation, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(12.dp))
                Button(onClick = retry) { Text("Retry connection") }
            }
            is BackendState.Ready -> Unit
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false },
        title = { Text("Download reading service?") },
        text = {
            Text("Download official Suwayomi ${SuwayomiProcessManager.PINNED_VERSION} from GitHub and verify its pinned SHA-256 before starting it. The backend listens only on localhost. No extensions are installed automatically. Existing data is not moved or deleted.")
        },
        confirmButton = { Button(onClick = {
            confirm = false
            update(BackendState.Checking)
            scope.launch {
                try { update(withContext(Dispatchers.IO) { session.downloadAndStart() }) }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { update(BackendState.Unavailable("Setup failed. Check the official server cache, network and local service before retrying.")) }
            }
        }) { Text("Download & start") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } })
}
