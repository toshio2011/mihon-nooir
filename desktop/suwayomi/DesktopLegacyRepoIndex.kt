package dev.nooir.desktop.suwayomi

import java.io.File
import java.security.MessageDigest

/**
 * Explicitly selected local legacy index.min.json. No repository fetching, execution or
 * inference from a source name. The file is user-supplied evidence, NOT signature verification.
 * The actual server catalog must independently match store, package and exact version.
 */
object DesktopLegacyRepoIndex {
    private const val MAX_BYTES=4_000_000
    data class Entry(val pkg:String,val code:Long,val version:String,val sources:Set<Long>)
    data class Evidence internal constructor(val storeUrl:String,val signingKey:String,val sha256:String,val entries:Map<String,Entry>)
    private fun integer(raw:Any?):Long=when(raw){
        is Long->raw
        is String->raw.takeIf{it.matches(Regex("-?(0|[1-9][0-9]*)"))}?.toLongOrNull()?:error("Invalid source ID")
        else->error("Expected exact integer")
    }
    fun read(file:File,store:DesktopBackupInspector.Store):Evidence {
        require(file.isFile && file.length() in 1..MAX_BYTES.toLong())
        val bytes=file.inputStream().use{it.readNBytes(MAX_BYTES+1)}
        require(bytes.size<=MAX_BYTES)
        return parse(bytes,store)
    }
    fun parse(bytes:ByteArray,store:DesktopBackupInspector.Store):Evidence {
        require(bytes.size in 1..MAX_BYTES && store.reviewableHttps && store.signingKey.isNotBlank())
        val rows=MiniJson.parse(bytes.toString(Charsets.UTF_8)).asList()
        require(rows.size<=20_000)
        val entries=linkedMapOf<String,Entry>()
        for(raw in rows){
            val row=raw.asObject()
            val pkg=row.str("pkg")
            require(pkg.length<=250 && pkg.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")))
            val code=integer(row["code"])
            val version=row.str("version")
            require(code>=0 && version.isNotBlank() && version.length<=200)
            val sources=(row["sources"]?.asList()?:emptyList())
            require(sources.size<=2000)
            val ids=sources.map{integer(it.asObject()["id"])}.filter{it!=0L}.toSet()
            require(entries.put(pkg,Entry(pkg,code,version,ids))==null) { "Duplicate package in index" }
        }
        val digest=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it.toInt() and 255)}
        return Evidence(store.indexUrl,store.signingKey,digest,entries.toMap())
    }
    fun enrich(summary:DesktopBackupInspector.Summary,stores:List<DesktopBackupExtensions.Store>,
               catalog:List<DesktopBackupExtensions.Extension>,evidence:Evidence):List<DesktopBackupExtensions.Extension>{
        val backup=summary.stores.singleOrNull{it.indexUrl==evidence.storeUrl}
        val store=stores.singleOrNull{it.indexUrl==evidence.storeUrl}
        require(backup!=null && backup.reviewableHttps && backup.signingKey==evidence.signingKey &&
            store!=null && store.legacy && store.signingKey==evidence.signingKey) {
            "Repository must be a configured matching legacy store; review again"
        }
        return catalog.map{e->
            val row=evidence.entries[e.pkgName]
            if(e.installed || e.obsolete || e.indexUrl!=evidence.storeUrl || row==null ||
                e.versionCode!=row.code || e.versionName!=row.version)e
            else if(e.sources.isNotEmpty() && e.sources!=row.sources)e
            else e.copy(sources=row.sources)
        }
    }
}
