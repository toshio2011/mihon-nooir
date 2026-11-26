package dev.nooir.desktop.suwayomi

import java.io.File
import java.io.InputStream
import java.net.URI
import java.util.zip.GZIPInputStream

/** Bounded read-only protobuf inspector. No connection, installation or restore. */
object DesktopBackupInspector {
    private const val MAX_COMPRESSED = 32 * 1024 * 1024
    private const val MAX_EXPANDED = 64 * 1024 * 1024
    data class Source(val id: Long, val name: String)
    data class Store(val name: String, val indexUrl: String, val signingKey: String, val legacy: Boolean,
                     val extensionListUrl: String) {
        val reviewableHttps: Boolean get() = runCatching {
            val u = URI(indexUrl)
            u.scheme == "https" && !u.host.isNullOrBlank() && u.rawUserInfo == null &&
                u.rawFragment == null && u.rawQuery == null && (u.port == -1 || u.port == 443)
        }.getOrDefault(false)
    }
    data class Summary(val mangas: Int, val favorites: Int, val categories: Int, val chapters: Int,
                       val read: Int, val bookmarks: Int, val progressed: Int, val history: Int,
                       val preferences: Int, val sourcePreferences: Int,
                       val sources: List<Source>, val stores: List<Store>, val referencedSourceIds: Set<Long>) {
        fun missingSources(installed: Set<Long>): Set<Long> = referencedSourceIds - installed
    }
    private data class Field(val n: Int, val wire: Int, val value: Long, val start: Int, val end: Int)
    private class Wire(private val data: ByteArray, private var p: Int = 0, private val end: Int = data.size) {
        private fun number(): Long {
            var x = 0L
            for (i in 0..9) {
                require(p < end) { "Truncated backup" }
                val b = data[p++].toInt() and 255
                require(i < 9 || b <= 1) { "Invalid protobuf varint" }
                x = x or ((b and 127).toLong() shl (i * 7))
                if (b and 128 == 0) return x
            }
            error("Invalid protobuf varint")
        }
        private fun skip(n: Int) { require(n >= 0 && n <= end - p) { "Truncated backup field" }; p += n }
        fun next(): Field? {
            if (p == end) return null
            val tag = number()
            require(tag > 0 && tag ushr 3 <= 536870911L) { "Invalid backup field" }
            val n = (tag ushr 3).toInt()
            val wire = (tag and 7).toInt()
            return when(wire) {
                0 -> Field(n, wire, number(), p, p)
                1 -> { val s = p; skip(8); Field(n,wire,0,s,p) }
                2 -> { val len = number(); require(len in 0..(end-p).toLong()) { "Invalid backup field length" }
                    val s=p; skip(len.toInt()); Field(n,wire,0,s,p) }
                5 -> { val s=p; skip(4); Field(n,wire,0,s,p) }
                else -> error("Unsupported backup wire type")
            }
        }
        fun nested(f: Field): Wire { require(f.wire == 2); return Wire(data,f.start,f.end) }
        fun string(f: Field): String {
            require(f.wire == 2 && f.end-f.start <= 8192) { "Invalid backup metadata length" }
            return data.copyOfRange(f.start,f.end).toString(Charsets.UTF_8)
        }
        fun scalar(f: Field): Long { require(f.wire == 0); return f.value }
    }
    fun inspect(file: File): Summary {
        require(file.isFile && file.length() in 1L..MAX_COMPRESSED.toLong()) { "Backup exceeds compressed size limit or is missing" }
        return file.inputStream().use { inspect(it) }
    }
    fun inspect(input: InputStream, allowEmpty: Boolean = false): Summary {
        val compressed = input.readNBytes(MAX_COMPRESSED+1)
        require(compressed.size <= MAX_COMPRESSED) { "Backup exceeds compressed size limit" }
        val data = GZIPInputStream(compressed.inputStream()).use { it.readNBytes(MAX_EXPANDED+1) }
        require(data.size <= MAX_EXPANDED) { "Backup exceeds expanded size limit" }
        var mangas=0; var favorites=0; var categories=0; var chapters=0; var read=0
        var bookmarks=0; var progressed=0; var history=0; var preferences=0; var sourcePreferences=0
        val sources=linkedMapOf<Long,Source>(); val stores=linkedMapOf<String,Store>(); val refs=linkedSetOf<Long>()
        val root=Wire(data)
        while(true) {
            val f=root.next()?:break
            when(f.n) {
                1 -> {
                    val m=root.nested(f); mangas++; require(mangas<=100_000)
                    var favorite=true; var source:Long?=null; var url=false
                    while(true) {
                        val k=m.next()?:break
                        when(k.n) {
                            1 -> source=m.scalar(k)
                            2 -> { require(k.wire==2); url=k.end>k.start }
                            100 -> favorite=m.scalar(k)!=0L
                            16 -> {
                                val c=m.nested(k); chapters++; require(chapters<=2_000_000)
                                var wasRead=false; var marked=false; var page=0L
                                while(true) { val v=c.next()?:break; when(v.n) {
                                    4 -> wasRead=c.scalar(v)!=0L
                                    5 -> marked=c.scalar(v)!=0L
                                    6 -> page=c.scalar(v)
                                }}
                                if(wasRead)read++; if(marked)bookmarks++; if(page>0)progressed++
                            }
                            104 -> { m.nested(k); history++ }
                        }
                    }
                    require(source!=null && url) { "Backup manga is missing source or URL" }
                    refs.add(source)
                    if(favorite)favorites++
                }
                2 -> {root.nested(f);categories++}
                101 -> {
                    val s=root.nested(f);var name="";var id:Long?=null
                    while(true){val k=s.next()?:break;when(k.n){1->name=s.string(k);2->id=s.scalar(k)}}
                    if(id!=null)sources[id]=Source(id,name)
                    require(sources.size<=10_000)
                }
                104 -> {root.nested(f);preferences++}
                105 -> {root.nested(f);sourcePreferences++}
                106 -> {
                    val s=root.nested(f);var url="";var name="";var key="";var legacy=false;var list=""
                    while(true){val k=s.next()?:break;when(k.n){
                        1->url=s.string(k);2->name=s.string(k);5->key=s.string(k);7->legacy=s.scalar(k)!=0L;8->list=s.string(k)
                    }}
                    require(url.isNotBlank()) { "Extension repository missing URL" }
                    val store=Store(name,url,key,legacy,list)
                    val old=stores.putIfAbsent(url,store)
                    require(old==null || old==store) { "Conflicting repository records" }
                    require(stores.size<=1000)
                }
            }
        }
        require((allowEmpty && data.isEmpty()) || mangas>0 || categories>0 || sources.isNotEmpty() || stores.isNotEmpty() ||
            preferences>0 || sourcePreferences>0) { "No recognized backup records" }
        return Summary(mangas,favorites,categories,chapters,read,bookmarks,progressed,history,
            preferences,sourcePreferences,sources.values.toList(),stores.values.toList(),refs)
    }
}
