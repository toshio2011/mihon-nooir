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
    fun export(target:File) {
        require(!target.exists()) { "Choose a new backup filename" }
        val parent=target.absoluteFile.parentFile
        require(parent.isDirectory) { "Backup destination folder is missing" }
        val path=json("mutation NooirExport { createBackup(input: { flags: { $flags } }) { url } }").obj("createBackup").str("url")
        require(path.matches(Regex("/api/graphql/files/backup/[A-Za-z0-9_.-]+\\.tachibk")) && !path.contains("..")) {
            "Unexpected backup download path"
        }
        val bytes=response(builder(path).GET().build(),maxBackup)
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
