package dev.nooir.desktop.ui

import dev.nooir.desktop.suwayomi.MiniJson
import dev.nooir.desktop.suwayomi.asList
import dev.nooir.desktop.suwayomi.asObject
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets

internal data class WindowsReleaseVersion(val major: Int,val minor: Int,val patch: Int) : Comparable<WindowsReleaseVersion> {
    override fun compareTo(other: WindowsReleaseVersion): Int =
        compareValuesBy(this,other,{it.major},{it.minor},{it.patch})
    override fun toString() = "$major.$minor.$patch"
    companion object {
        fun parse(raw: String): WindowsReleaseVersion? {
            if (!raw.matches(Regex("(0|[1-9][0-9]{0,2})\\.(0|[1-9][0-9]{0,2})\\.(0|[1-9][0-9]{0,4})"))) return null
            val parts=raw.split('.').map(String::toInt)
            // Windows MSI's major/minor/patch version field limits.
            if(parts[0]>255 || parts[1]>255 || parts[2]>65535) return null
            return WindowsReleaseVersion(parts[0],parts[1],parts[2])
        }
    }
}

internal data class DesktopWindowsRelease(
    val version: WindowsReleaseVersion, val page: URI, val preview: Boolean,
)

internal object DesktopWindowsUpdates {
    const val CURRENT_VERSION = "0.1.0"
    private const val PREFIX = "windows-v"
    private const val GITHUB = "https://github.com/toshio2011/mihon-nooir"
    private const val API = "https://api.github.com/repos/toshio2011/mihon-nooir/releases?per_page=100"

    /** No Android releases, drafts, or arbitrary remote URLs are eligible. */
    fun select(json: String, current: WindowsReleaseVersion,
        includePreview: Boolean): DesktopWindowsRelease? =
        MiniJson.parse(json).asList().mapNotNull { item ->
            val release=item.asObject()
            val tag=release["tag_name"] as? String ?: return@mapNotNull null
            if(!tag.startsWith(PREFIX) || release["draft"] != false) return@mapNotNull null
            val preview=release["prerelease"] as? Boolean ?: return@mapNotNull null
            if(preview && !includePreview) return@mapNotNull null
            val version=WindowsReleaseVersion.parse(tag.removePrefix(PREFIX)) ?: return@mapNotNull null
            if(version<=current) return@mapNotNull null
            val page="$GITHUB/releases/tag/$tag"
            if(release["html_url"] != page) return@mapNotNull null
            val assets=(release["assets"] as? List<*>)?.map { it.asObject() } ?: return@mapNotNull null
            val installer="MihonNooir-Windows-$version-x64.msi"
            val portable="MihonNooir-Windows-$version-x64.zip"
            val hashes="SHA256SUMS-windows.txt"
            fun present(name:String):Boolean = assets.count {
                it["name"] == name && it["state"] == "uploaded" &&
                    (it["size"] as? Number)?.toLong()?.let { size -> size>0 } == true &&
                    it["browser_download_url"] == "$GITHUB/releases/download/$tag/$name"
            } == 1
            if(!present(hashes) || !(present(installer)||present(portable))) return@mapNotNull null
            DesktopWindowsRelease(version,URI(page),preview)
        }.maxByOrNull { it.version }

    /** Explicit user-initiated request only; no automatic downloads/execution. */
    fun check(includePreview:Boolean):DesktopWindowsRelease? {
        val c=URI(API).toURL().openConnection() as HttpURLConnection
        try {
            c.instanceFollowRedirects=false
            c.connectTimeout=8_000;c.readTimeout=15_000
            c.setRequestProperty("Accept","application/vnd.github+json")
            c.setRequestProperty("User-Agent","MihonNooir-Windows/$CURRENT_VERSION")
            require(c.responseCode==200) { "Windows update check failed (HTTP ${c.responseCode})" }
            val bytes=c.inputStream.use { it.readNBytes(4_000_001) }
            require(bytes.size<=4_000_000) { "GitHub response exceeded the safe size limit" }
            return select(String(bytes,StandardCharsets.UTF_8),
                checkNotNull(WindowsReleaseVersion.parse(CURRENT_VERSION)),includePreview)
        } finally { c.disconnect() }
    }
}
