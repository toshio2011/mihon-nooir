package dev.nooir.desktop.suwayomi

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Mihon extension store field 106, matching pinned Android BackupExtensionStore.
 * Preserve backend metadata without touching credentials, source preferences or APKs.
 */
object DesktopBackupExtensions {
    data class Store(val indexUrl:String,val name:String,val badgeLabel:String,val signingKey:String,
                     val contactWebsite:String,val contactDiscord:String?,val legacy:Boolean,val extensionListUrl:String?)
    data class Extension(val pkgName:String,val name:String,val indexUrl:String?,val installed:Boolean,
                         val obsolete:Boolean,val sources:Set<Long>,val versionCode:Long?=null,val versionName:String?=null)
    enum class StoreState { PRESENT_MATCH, KEY_CONFLICT, MISSING_REVIEW, UNVERIFIED }
    data class StoreReview(val backup:DesktopBackupInspector.Store,val state:StoreState)
    enum class SourceState { PRESENT, CANDIDATE_REVIEW, AMBIGUOUS, UNRESOLVED }
    data class SourceReview(val id:Long,val name:String,val state:SourceState,val candidates:List<Extension>)
    data class Plan(val stores:List<StoreReview>,val sources:List<SourceReview>)
    fun plan(summary:DesktopBackupInspector.Summary,stores:List<Store>,catalog:List<Extension>,missing:Set<Long>):Plan {
        val byUrl=stores.associateBy{it.indexUrl}
        val review=summary.stores.map{ s->
            val current=byUrl[s.indexUrl]
            val state=when{
                !s.reviewableHttps || s.signingKey.isBlank()->StoreState.UNVERIFIED
                current==null->StoreState.MISSING_REVIEW
                current.signingKey!=s.signingKey->StoreState.KEY_CONFLICT
                else->StoreState.PRESENT_MATCH
            }
            StoreReview(s,state)
        }
        val matched=review.filter{it.state==StoreState.PRESENT_MATCH}.map{it.backup.indexUrl}.toSet()
        val names=summary.sources.associate{it.id to it.name}
        val sources=summary.referencedSourceIds.map{id->
            // Exact signed ID match only. A similarly named extension is not proof of compatibility.
            val candidates=catalog.filter{!it.obsolete && id in it.sources && it.indexUrl in matched}
            val state=when{
                id !in missing && (id==0L || catalog.any{it.installed && id in it.sources})->SourceState.PRESENT
                candidates.size==1 && !candidates.single().installed->SourceState.CANDIDATE_REVIEW
                candidates.size>1->SourceState.AMBIGUOUS
                else->SourceState.UNRESOLVED
            }
            SourceReview(id,names[id]?:id.toString(),state,candidates)
        }
        return Plan(review,sources)
    }
    /** Approval is tied to exact package, store URL, signing identity and source IDs. */
    data class Recovery(val pkgName:String,val name:String,val indexUrl:String,val signingKey:String,val sourceIds:Set<Long>,val versionCode:Long?=null,val versionName:String?=null)
    fun recoverable(plan:Plan):List<Recovery> {
        val matched=plan.stores.filter{it.state==StoreState.PRESENT_MATCH}.associateBy{it.backup.indexUrl}
        return plan.sources.filter{it.state==SourceState.CANDIDATE_REVIEW}
            .groupBy{it.candidates.single().pkgName}.values.map{rows->
                val e=rows.first().candidates.single()
                val url=requireNotNull(e.indexUrl)
                val store=matched.getValue(url)
                require(rows.all{it.candidates.single()==e})
                require(e.pkgName.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")) && e.pkgName.length<=250)
                Recovery(e.pkgName,e.name,url,store.backup.signingKey,rows.map{it.id}.toSet(),e.versionCode,e.versionName)
            }
    }
    private fun varint(n:Long):ByteArray {
        var x=n;val b=ByteArrayOutputStream()
        while(x ushr 7!=0L){b.write((x.toInt() and 127) or 128);x=x ushr 7}
        b.write(x.toInt());return b.toByteArray()
    }
    private fun field(n:Int,b:ByteArray)=varint((n*8+2).toLong())+varint(b.size.toLong())+b
    private fun text(n:Int,s:String):ByteArray {
        val b=s.toByteArray(Charsets.UTF_8);require(b.size<=8192){"Oversized repository metadata"}
        return field(n,b)
    }
    fun withStores(raw:ByteArray,stores:List<Store>):ByteArray {
        require(stores.size<=1000 && stores.map{it.indexUrl}.toSet().size==stores.size)
        val original=DesktopBackupInspector.inspect(raw.inputStream(),allowEmpty=true)
        require(original.stores.isEmpty()) {"Backend already exported extension stores; review compatibility"}
        val limit=64*1024*1024
        val data=GZIPInputStream(raw.inputStream()).use{it.readNBytes(limit+1)}
        require(data.size<=limit)
        val appended=ByteArrayOutputStream()
        stores.forEach{s->
            val url=DesktopBackupInspector.Store(s.name,s.indexUrl,s.signingKey,s.legacy,s.extensionListUrl.orEmpty())
            require(url.reviewableHttps && s.signingKey.isNotBlank()) {"Configured store requires manual review before portable backup"}
            var entry=text(1,s.indexUrl)+text(2,s.name)+text(3,s.badgeLabel)+text(4,s.contactWebsite)+text(5,s.signingKey)
            s.contactDiscord?.let{entry+=text(6,it)}
            entry+=varint(7*8L)+varint(if(s.legacy)1 else 0)
            s.extensionListUrl?.let{entry+=text(8,it)}
            appended.write(field(106,entry))
        }
        require(data.size.toLong()+appended.size()<=limit)
        val output=ByteArrayOutputStream()
        GZIPOutputStream(output).use{it.write(data);appended.writeTo(it)}
        val result=output.toByteArray();require(result.size<=32*1024*1024)
        val parsed=DesktopBackupInspector.inspect(result.inputStream(),allowEmpty=true)
        require(parsed.stores.size==stores.size && parsed.mangas==original.mangas && parsed.chapters==original.chapters)
        return result
    }
}
