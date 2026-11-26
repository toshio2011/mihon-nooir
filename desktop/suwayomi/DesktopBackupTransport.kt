package dev.nooir.desktop.suwayomi

import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.time.Duration
import java.util.UUID

/** Pinned v2.4.2366 GraphQL backup API, local only. Explicit library-only operations. */
class DesktopBackupTransport(endpoint: String) {
    private val base: String
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
        .followRedirects(HttpClient.Redirect.NEVER).build()
    init {
        val u=URI(endpoint)
        require(u.scheme in setOf("http","https") && u.host in setOf("localhost","127.0.0.1","[::1]","::1") &&
            u.port in 1..65535 && u.rawUserInfo==null && u.rawQuery==null && u.rawFragment==null &&
            (u.rawPath.isNullOrEmpty() || u.rawPath=="/")) { "Use a localhost Suwayomi endpoint" }
        base=u.scheme+"://"+u.rawAuthority
    }
    data class Validation(val missingSources: List<DesktopBackupInspector.Source>, val missingTrackers: List<String>)
    data class RestoreTicket(val id:String, val state:String)
    private val maxBackup=32*1024*1024
    private val flags="includeManga: true, includeCategories: true, includeChapters: true, includeHistory: true, includeTracking: true, includeClientData: false, includeServerSettings: false"
    private fun builder(path:String)=HttpRequest.newBuilder(URI.create(base+path))
        .timeout(Duration.ofSeconds(120)).header("User-Agent","MihonNooir-Desktop-Backup")
    private fun response(req:HttpRequest, max:Int):ByteArray {
        val res=http.send(req,HttpResponse.BodyHandlers.ofInputStream())
        return res.body().use { body ->
            require(res.statusCode()==200) { "Suwayomi backup API HTTP "+res.statusCode() }
            body.readNBytes(max+1).also { require(it.size<=max) { "Backup API response exceeds size limit" } }
        }
    }
    private fun data(req:HttpRequest):Map<String,Any?> {
        val root=MiniJson.parse(response(req,2_000_000).toString(Charsets.UTF_8)).asObject()
        require((root["errors"] as? List<*>).isNullOrEmpty()) { "Suwayomi rejected the backup API request" }
        return root.obj("data")
    }
    private fun json(query:String)=data(builder("/api/graphql").header("Content-Type","application/json")
        .POST(HttpRequest.BodyPublishers.ofString("{\"query\":"+MiniJson.quote(query)+"}")).build())
    private fun input(file:File):ByteArray {
        require(file.isFile && file.length() in 1L..maxBackup.toLong()) { "Invalid backup file size" }
        return file.inputStream().use{it.readNBytes(maxBackup+1)}.also{
            require(it.size<=maxBackup)
            DesktopBackupInspector.inspect(it.inputStream())
        }
    }
    private fun upload(query:String,bytes:ByteArray):Map<String,Any?> {
        val boundary="nooir-"+UUID.randomUUID()
        val operations="{\"query\":"+MiniJson.quote(query)+",\"variables\":{\"file\":null}}"
        val prefix="--$boundary\r\nContent-Disposition: form-data; name=\"operations\"\r\n\r\n"+operations+
            "\r\n--$boundary\r\nContent-Disposition: form-data; name=\"map\"\r\n\r\n{\"0\":[\"variables.file\"]}"+
            "\r\n--$boundary\r\nContent-Disposition: form-data; name=\"0\"; filename=\"backup.tachibk\"\r\nContent-Type: application/octet-stream\r\n\r\n"
        val suffix="\r\n--$boundary--\r\n"
        return data(builder("/api/graphql").header("Content-Type","multipart/form-data; boundary=$boundary")
            .POST(HttpRequest.BodyPublishers.ofByteArrays(listOf(prefix.toByteArray(Charsets.UTF_8),bytes,suffix.toByteArray(Charsets.UTF_8)))).build())
    }
    private fun validateBytes(bytes:ByteArray):Validation {
        val query="query NooirValidate(\$file: Upload!) { validateBackup(input: { backup: \$file }) { missingSources { id name } missingTrackers { name } } }"
        val v=upload(query,bytes).obj("validateBackup")
        return Validation(v.list("missingSources").map { val s=it.asObject(); DesktopBackupInspector.Source(s.long("id"),s.str("name")) },
            v.list("missingTrackers").map { it.asObject().str("name") })
    }
    fun validate(file:File):Validation=validateBytes(input(file))
    /** Read only the configured stores and cached extension catalog. Never refresh repositories here. */
    fun extensionStores():List<DesktopBackupExtensions.Store> {
        val stores=mutableListOf<DesktopBackupExtensions.Store>()
        var offset=0
        while(true){
            val page=json("query NooirBackupStores { extensionStores(first: 100, offset: $offset) { totalCount nodes { name badgeLabel signingKey contactWebsite contactDiscord indexUrl isLegacy extensionListUrl } } }").obj("extensionStores")
            val total=page.int("totalCount");require(total in 0..1000)
            val rows=page.list("nodes");require(rows.size<=100 && offset+rows.size<=total)
            rows.forEach { raw ->
                val s=raw.asObject()
                stores.add(DesktopBackupExtensions.Store(s.str("indexUrl"),s.str("name"),s.str("badgeLabel"),
                    s.str("signingKey"),s.str("contactWebsite"),s["contactDiscord"] as? String,
                    s["isLegacy"] as? Boolean ?: error("Missing isLegacy"),s["extensionListUrl"] as? String))
            }
            offset+=rows.size
            if(offset==total)break
            require(rows.isNotEmpty()) { "Incomplete extension store listing" }
        }
        require(stores.map{it.indexUrl}.toSet().size==stores.size) { "Duplicate extension store listing" }
        return stores
    }
    fun extensionCatalog():List<DesktopBackupExtensions.Extension> {
        val all=mutableListOf<DesktopBackupExtensions.Extension>()
        var offset=0
        while(true){
            val page=json("query NooirBackupCatalog { extensions(first: 50, offset: $offset) { totalCount nodes { pkgName name storeIndexUrl isInstalled isObsolete versionCodeLong versionName source { nodes { id } } } } }").obj("extensions")
            val total=page.int("totalCount");require(total in 0..20_000)
            val rows=page.list("nodes");require(rows.size<=50 && offset+rows.size<=total)
            rows.forEach{raw->
                val e=raw.asObject()
                val ids=e.obj("source").list("nodes").map{it.asObject().long("id")}.toSet()
                all.add(DesktopBackupExtensions.Extension(e.str("pkgName"),e.str("name"),e["storeIndexUrl"] as? String,
                    e["isInstalled"] as? Boolean ?: error("Missing install status"),
                    e["isObsolete"] as? Boolean ?: error("Missing obsolete status"),ids,
                    (e["versionCodeLong"] as? String)?.toLongOrNull(),e["versionName"] as? String))
            }
            offset+=rows.size
            if(offset==total)break
            require(rows.isNotEmpty()) { "Incomplete extension catalog" }
        }
        require(all.map{it.pkgName}.toSet().size==all.size) { "Duplicate extension package" }
        return all
    }
    fun extensionPlan(file:File,evidence:DesktopLegacyRepoIndex.Evidence?=null):DesktopBackupExtensions.Plan {
        val summary=DesktopBackupInspector.inspect(file)
        val missing=validate(file).missingSources.map{it.id}.toSet()
        val stores=extensionStores()
        val raw=extensionCatalog()
        val catalog=if(evidence==null)raw else DesktopLegacyRepoIndex.enrich(summary,stores,raw,evidence)
        return DesktopBackupExtensions.plan(summary,stores,catalog,missing)
    }
    /** Install only a separately approved cached exact match, never add or refresh a repository.
     * Suwayomi performs the install; its package execution is a trust decision, not a sandbox.
     * A lost response may mean a partial install. No retries or automatic rollback.
     */
    fun installReviewedExtension(file:File, approved:DesktopBackupExtensions.Recovery,evidence:DesktopLegacyRepoIndex.Evidence?=null):Set<Long> {
        val fresh=DesktopBackupExtensions.recoverable(extensionPlan(file,evidence))
        require(fresh.any{it==approved}) { "Extension plan changed; review again before installing" }
        val result=json("mutation NooirRecoverExtension { updateExtension(input: { id: "+
            MiniJson.quote(approved.pkgName)+", patch: { install: true } }) { extension { pkgName storeIndexUrl isInstalled source { nodes { id } } } } }")
            .obj("updateExtension").obj("extension")
        require(result.str("pkgName")==approved.pkgName && result["storeIndexUrl"]==approved.indexUrl &&
            result["isInstalled"]==true) { "Server did not confirm expected extension; inspect WebUI" }
        val exposed=result.obj("source").list("nodes").map{it.asObject().long("id")}.toSet()
        val installed=extensionCatalog().singleOrNull{it.pkgName==approved.pkgName}
        require(installed!=null && installed.installed && installed.indexUrl==approved.indexUrl &&
            installed.versionCode==approved.versionCode && installed.versionName==approved.versionName) { "Installed package not confirmed" }
        val store=extensionStores().singleOrNull{it.indexUrl==approved.indexUrl}
        require(store?.signingKey==approved.signingKey) { "Repository identity changed; inspect WebUI" }
        val missing=validate(file).missingSources.map{it.id}.toSet()
        val restored=approved.sourceIds.intersect(exposed).intersect(installed.sources)-missing
        require(restored==approved.sourceIds) { "Package installed, but some requested sources remain unavailable; inspect WebUI" }
        return restored
    }
    fun export(target:File, includeExtensionStores:Boolean=false) {
        require(!target.exists()) { "Choose a new backup filename" }
        val parent=target.absoluteFile.parentFile
        require(parent.isDirectory) { "Backup destination folder is missing" }
        val path=json("mutation NooirExport { createBackup(input: { flags: { $flags } }) { url } }").obj("createBackup").str("url")
        require(path.matches(Regex("/api/graphql/files/backup/[A-Za-z0-9_.-]+\\.tachibk")) && !path.contains("..")) {
            "Unexpected backup download path"
        }
        val raw=response(builder(path).GET().build(),maxBackup)
        DesktopBackupInspector.inspect(raw.inputStream(),allowEmpty=true)
        val bytes=if(includeExtensionStores) DesktopBackupExtensions.withStores(raw,extensionStores()) else raw
        DesktopBackupInspector.inspect(bytes.inputStream(),allowEmpty=true)
        val temp=Files.createTempFile(parent.toPath(),".nooir-backup-",".tmp")
        try {
            Files.newOutputStream(temp).use{it.write(bytes)}
            // No replacement, including if a file appeared while downloading.
            Files.move(temp,target.toPath())
        } finally { Files.deleteIfExists(temp) }
    }
    /** Save a separate recovery snapshot first; never automatically retry an ambiguous restore. */
    fun beginRestore(file:File,recovery:File):RestoreTicket {
        require(file.canonicalFile!=recovery.canonicalFile) { "Recovery backup must be separate" }
        val bytes=input(file)
        validateBytes(bytes)
        export(recovery)
        val query="mutation NooirRestore(\$file: Upload!) { restoreBackup(input: { backup: \$file, flags: { $flags } }) { id status { state } } }"
        val result=upload(query,bytes).obj("restoreBackup")
        val id=result.str("id")
        require(id.matches(Regex("[0-9]{1,30}"))) { "Restore started but returned an invalid ID; check WebUI" }
        val state=(result["status"] as? Map<*,*>)?.get("state") as? String ?: "UNKNOWN"
        return RestoreTicket(id,state)
    }
    fun await(ticket:RestoreTicket,timeoutSeconds:Long=300):String {
        require(ticket.id.matches(Regex("[0-9]{1,30}")) && timeoutSeconds in 1..600)
        var state=ticket.state
        val deadline=System.nanoTime()+Duration.ofSeconds(timeoutSeconds).toNanos()
        while(state!="SUCCESS" && state!="FAILURE"){
            if(System.nanoTime()>=deadline)return "UNKNOWN"
            Thread.sleep(1000)
            val result=json("query NooirRestoreStatus { restoreStatus(id: "+MiniJson.quote(ticket.id)+") { state } }")
            state=(result["restoreStatus"] as? Map<*,*>)?.get("state") as? String ?: return "UNKNOWN"
        }
        return state
    }
}
