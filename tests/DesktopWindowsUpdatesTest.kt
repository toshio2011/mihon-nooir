package dev.nooir.desktop.ui

import dev.nooir.desktop.suwayomi.MiniJson

fun main() {
    val root="https://github.com/toshio2011/mihon-nooir"
    fun release(v:String, draft:Boolean=false, preview:Boolean=false, malicious:Boolean=false,
        omitManifest:Boolean=false):String {
        val tag="windows-v$v"
        val names=listOf("MihonNooir-Windows-$v-x64.msi","SHA256SUMS-windows.txt")
            .filterNot { omitManifest && it == "SHA256SUMS-windows.txt" }
        val assets=names.joinToString(",") { name ->
            """{"name":${MiniJson.quote(name)},"state":"uploaded","size":123,"browser_download_url":"$root/releases/download/$tag/$name"}"""
        }
        val page=if(malicious) "https://example.invalid/fake" else "$root/releases/tag/$tag"
        return """{"tag_name":"$tag","draft":$draft,"prerelease":$preview,"html_url":"$page","assets":[$assets]}"""
    }
    val current=checkNotNull(WindowsReleaseVersion.parse("0.1.0"))
    val android="""{"tag_name":"v99.0.0","draft":false,"prerelease":false}"""
    val json=listOf(android,release("0.1.0"),release("0.2.0"),release("0.10.0",preview=true),
        release("9.0.0",draft=true),release("8.0.0",malicious=true),release("7.0.0",omitManifest=true))
        .joinToString(",", "[", "]")
    check(DesktopWindowsUpdates.select(json,current,false)?.version.toString()=="0.2.0")
    check(DesktopWindowsUpdates.select(json,current,true)?.version.toString()=="0.10.0")
    check(DesktopWindowsUpdates.select("[$android]",current,true)==null)
    check(WindowsReleaseVersion.parse("001.1.0")==null)
    check(WindowsReleaseVersion.parse("256.1.0")==null)
    println("PASS: separate Windows update channel, numeric ordering, draft/Android exclusion, preview opt-in, trusted asset names and checksum manifest")
}
