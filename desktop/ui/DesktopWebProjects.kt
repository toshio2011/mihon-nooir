package dev.nooir.desktop.ui

import dev.nooir.bridge.*
import dev.nooir.desktop.storage.DesktopDataHome
import dev.nooir.mangabridge.*
import org.json.JSONArray
import org.json.JSONObject
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.net.URI
import java.util.UUID

/** App-private local settings and complete chapter inventory, not downloaded books or credentials. */
internal class DesktopWebProjects(private val root: Path = DesktopDataHome.root()) {
    data class Project(
        val id: String = "",
        val name: String,
        val source: String,
        val known: List<String>,
        val chapters: List<NooirWebChapterPlan.Entry>,
        val title: String,
        val author: String,
        val description: String,
        val language: String,
        val coverUrl: String,
        val localCover: String,
        val collage: Boolean,
        val epub2: Boolean,
        val style: String,
        val imageProfile: String,
        val includeImages: Boolean,
        val css: String,
        val selector: String,
        val remove: String,
        val collageUrls: List<String> = emptyList(),
    ) {
        override fun toString() = name
    }
    companion object {
        private val gate=Any()
        private const val MAX_BYTES=1_000_000L
        private const val MAX_PROJECTS=24
    }
    private val index=root.resolve("web-projects-v1.json")
    private fun <T> transaction(block:()->T):T = synchronized(gate) {
        Files.createDirectories(root)
        require(Files.isDirectory(root) && !Files.isSymbolicLink(root))
        val lock=root.resolve("web-projects.lock")
        require(!Files.isSymbolicLink(lock))
        FileChannel.open(lock,StandardOpenOption.CREATE,StandardOpenOption.WRITE).use { channel ->
            channel.lock().use { block() }
        }
    }
    private fun url(value:String):Boolean {
        val uri=runCatching{URI(value)}.getOrNull() ?: return false
        return value.length in 1..600 && uri.scheme.equals("https",true) &&
            !uri.host.isNullOrBlank() && uri.rawUserInfo==null && uri.port==-1
    }
    private fun validate(p:Project) {
        require(p.id.isBlank() || runCatching{UUID.fromString(p.id)}.isSuccess)
        require(p.name.isNotBlank() && p.name.length<=100 && p.title.length<=240)
        require(p.author.length<=120 && p.description.length<=4000 && p.language.length<=32)
        require(p.source.isBlank() || url(p.source))
        require(p.known.all(::url) && p.known.joinToString("\n").toByteArray().size<=120_000)
        NooirWebChapterPlan.start(p.chapters)
        // Validate the full inventory even when every chapter is unchecked.
        NooirWebChapterPlan.export(p.chapters.map{it.copy(included=true)})
        require(p.chapters.all{url(it.url) && it.title.length<=160})
        require(p.coverUrl.isBlank() || url(p.coverUrl))
        require(p.localCover.length<=4096 && (p.localCover.isBlank() || java.io.File(p.localCover).isAbsolute))
        require(!(p.coverUrl.isNotBlank() && p.localCover.isNotBlank()) &&
            (!p.collage || p.coverUrl.isBlank() && p.localCover.isBlank()))
        require(p.collageUrls.isEmpty() || p.collage && p.collageUrls.size in 2..4 && p.collageUrls.distinct().size==p.collageUrls.size && p.collageUrls.all(::url))
        NooirWebExtractRules.validate(p.selector,p.remove)
        NooirWebImageProfile.parse(p.imageProfile)
        ReflowEpubStyle.parse(p.style)
        NooirEpubCssRules.canonicalize(p.css)
    }
    private fun read():List<Project> {
        if(!Files.exists(index))return emptyList()
        require(Files.isRegularFile(index) && !Files.isSymbolicLink(index) && Files.size(index) in 2..MAX_BYTES){
            "Invalid saved-project library; original preserved"
        }
        val rows=JSONArray(Files.readString(index))
        require(rows.length()<=MAX_PROJECTS)
        return (0 until rows.length()).map{ i ->
            val j=rows.getJSONObject(i)
            val known=j.getJSONArray("known")
            val chapters=j.getJSONArray("chapters")
            Project(j.getString("id"),j.getString("name"),j.getString("source"),
                (0 until known.length()).map{known.getString(it)},
                (0 until chapters.length()).map{ k -> chapters.getJSONObject(k).let{
                    NooirWebChapterPlan.Entry(it.getString("url"),it.getString("title"),it.getBoolean("included"))
                }},
                j.getString("title"),j.getString("author"),j.getString("description"),j.getString("language"),
                j.getString("coverUrl"),j.getString("localCover"),j.getBoolean("collage"),j.getBoolean("epub2"),
                j.getString("style"),j.getString("imageProfile"),j.getBoolean("includeImages"),j.getString("css"),
                j.getString("selector"),j.getString("remove"),j.optJSONArray("collageUrls")?.let{a->(0 until a.length()).map{a.getString(it)}}.orEmpty()).also{validate(it);require(it.id.isNotBlank())}
        }.also{require(it.map{p->p.id}.distinct().size==it.size)}
    }
    private fun write(items:List<Project>) {
        require(items.size<=MAX_PROJECTS){"Project library is full (24 projects)"}
        val rows=JSONArray()
        for(p in items){
            val chapters=JSONArray()
            p.chapters.forEach{chapters.put(JSONObject().put("url",it.url).put("title",it.title).put("included",it.included))}
            rows.put(JSONObject().put("id",p.id).put("name",p.name).put("source",p.source)
                .put("known",JSONArray(p.known)).put("chapters",chapters).put("title",p.title)
                .put("author",p.author).put("description",p.description).put("language",p.language)
                .put("coverUrl",p.coverUrl).put("localCover",p.localCover).put("collage",p.collage)
                .put("epub2",p.epub2).put("style",p.style).put("imageProfile",p.imageProfile)
                .put("includeImages",p.includeImages).put("css",p.css).put("selector",p.selector).put("remove",p.remove).put("collageUrls",JSONArray(p.collageUrls)))
        }
        val bytes=rows.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size<=MAX_BYTES){"Saved library exceeds 1 MB"}
        val tmp=Files.createTempFile(root,".web-projects-",".tmp")
        try{
            java.io.FileOutputStream(tmp.toFile()).use{it.write(bytes);it.fd.sync()}
            Files.move(tmp,index,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
        }finally{Files.deleteIfExists(tmp)}
    }
    fun list()=transaction{read()}
    fun save(project:Project):Project=transaction{
        validate(project)
        val existing=read()
        val saved=project.copy(id=project.id.ifBlank{UUID.randomUUID().toString()})
        write(listOf(saved)+existing.filterNot{it.id==saved.id})
        saved
    }
    fun delete(id:String)=transaction{
        val previous=read();val next=previous.filterNot{it.id==id}
        if(next.size!=previous.size)write(next)
    }
}
