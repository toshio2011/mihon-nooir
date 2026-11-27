package dev.nooir.desktop.compose

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/** UI-thread owned count. Acquire before suspending, release only after the
 * underlying bounded operation returns. Normal window close/navigation cannot
 * dispose an active operation. Process kill and OS shutdown are not covered. */
internal class OperationGuard {
    private var count by mutableIntStateOf(0)
    val active: Boolean get() = count > 0
    suspend fun <T> protect(block: suspend () -> T): T {
        count++
        try { return block() } finally { count-- }
    }
}
internal val LocalOperations = staticCompositionLocalOf<OperationGuard> {
    error("Missing application operation guard")
}
internal fun CoroutineScope.launchProtected(guard: OperationGuard, block: suspend CoroutineScope.() -> Unit) =
    launch(start = CoroutineStart.UNDISPATCHED) {
        guard.protect { block() }
    }
