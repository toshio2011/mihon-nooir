package dev.nooir.desktop.compose

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import dev.nooir.desktop.ui.DesktopConversionSettingsStore
import dev.nooir.mangabridge.MangaConversionOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

@Composable
internal fun MangaPreferenceActions(options:MangaConversionOptions,enabled:Boolean,change:(MangaConversionOptions)->Unit) {
    val scope=rememberCoroutineScope()
    val operations=LocalOperations.current
    val store=remember{DesktopConversionSettingsStore()}
    var working by remember{mutableStateOf(false)}
    var status by remember{mutableStateOf("")}
    var confirm by remember{mutableStateOf(false)}
    fun perform(save:Boolean){
        working=true
        scope.launchProtected(operations){
            try {
                if(save) {withContext(Dispatchers.IO){store.save(options)};status="Saved default manga settings."}
                else {change(withContext(Dispatchers.IO){store.load()});status="Loaded default manga settings."}
            }catch(e:CancellationException){throw e}
            catch(e:Exception){status="Settings unavailable or invalid. Existing settings file preserved."}
            finally{working=false}
        }
    }
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
        OutlinedButton(onClick={perform(false)},enabled=enabled&&!working){Text("Load saved settings")}
        OutlinedButton(onClick={confirm=true},enabled=enabled&&!working){Text("Save as default")}
    }
    if(status.isNotBlank()) Text(status,style=MaterialTheme.typography.bodySmall)
    if(confirm) AlertDialog(onDismissRequest={confirm=false},title={Text("Save default manga settings?")},
        text={Text("Replace the shared desktop conversion preferences with these settings? Queued jobs and Android settings are unchanged.")},
        confirmButton={Button(onClick={confirm=false;perform(true)}){Text("Save")}},
        dismissButton={TextButton(onClick={confirm=false}){Text("Cancel")}})
}
