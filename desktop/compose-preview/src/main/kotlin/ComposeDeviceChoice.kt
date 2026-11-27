package dev.nooir.desktop.compose

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.nooir.bridge.NooirDeviceProfile
import dev.nooir.bridge.NooirDeviceProfiles

internal fun validatedCustomReader(width:String,height:String):NooirDeviceProfile? =
    runCatching { NooirDeviceProfiles.forId(NooirDeviceProfiles.custom(width.toInt(),height.toInt())) }.getOrNull()

@Composable
internal fun ComposeDeviceChoice(device:NooirDeviceProfile,enabled:Boolean,change:(NooirDeviceProfile)->Unit) {
    var open by remember{mutableStateOf(false)}
    ExportChoice(device.name,NooirDeviceProfiles.builtins.map{it.name}+"Custom resolution…",enabled){name->
        if(name=="Custom resolution…")open=true else change(NooirDeviceProfiles.builtins.first{it.name==name})
    }
    if(NooirDeviceProfiles.isCustom(device.id))Text("Custom output; verify format compatibility on your reader.",style=MaterialTheme.typography.bodySmall)
    if(open){
        var width by remember{mutableStateOf(device.width.toString())}
        var height by remember{mutableStateOf(device.height.toString())}
        val profile=validatedCustomReader(width,height)
        AlertDialog(onDismissRequest={open=false},title={Text("Custom reader resolution")},
            text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                OutlinedTextField(width,{width=it.filter(Char::isDigit).take(4)},label={Text("Width (px)")},singleLine=true)
                OutlinedTextField(height,{height=it.filter(Char::isDigit).take(4)},label={Text("Height (px)")},singleLine=true)
                Text("64–4096 pixels per edge; maximum 3 million pixels.",style=MaterialTheme.typography.bodySmall)
                if(profile==null)Text("Enter a valid resolution.",color=MaterialTheme.colorScheme.error)
                Text("Custom size does not guarantee reader format or Wi-Fi compatibility.",style=MaterialTheme.typography.bodySmall)
            }},confirmButton={Button(enabled=profile!=null,onClick={profile?.let(change);open=false}){Text("Apply")}},
            dismissButton={TextButton(onClick={open=false}){Text("Cancel")}})
    }
}
