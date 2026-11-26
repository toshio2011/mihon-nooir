package dev.nooir.desktop.suwayomi

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

fun main(){
    val url="https://example.org/repo.json"
    val key="synthetic-key"
    val store=DesktopBackupExtensions.Store(url,"Synthetic","Test",key,"https://example.org",null,true,null)
    val raw=ByteArrayOutputStream().also{b->GZIPOutputStream(b).use{it.write(byteArrayOf(10,5,8,42,18,1,120))}}.toByteArray()
    val summary=DesktopBackupInspector.inspect(DesktopBackupExtensions.withStores(raw,listOf(store)).inputStream())
    val json="""[{"pkg":"synthetic.pkg","code":100,"version":"1.4.100","sources":[{"id":42},{"id":"-9223372036854775808"},{"id":9223372036854775807}]}]"""
    val evidence=DesktopLegacyRepoIndex.parse(json.toByteArray(),summary.stores.single())
    check(evidence.entries.getValue("synthetic.pkg").sources==setOf(42L,Long.MIN_VALUE,Long.MAX_VALUE))
    val catalog=listOf(DesktopBackupExtensions.Extension("synthetic.pkg","Synthetic",url,false,false,emptySet(),100L,"1.4.100"))
    check(DesktopBackupExtensions.recoverable(DesktopBackupExtensions.plan(summary,listOf(store),catalog,setOf(42L))).isEmpty())
    val enriched=DesktopLegacyRepoIndex.enrich(summary,listOf(store),catalog,evidence)
    val approval=DesktopBackupExtensions.recoverable(DesktopBackupExtensions.plan(summary,listOf(store),enriched,setOf(42L))).single()
    check(approval.sourceIds==setOf(42L) && approval.versionCode==100L)
    check(DesktopLegacyRepoIndex.enrich(summary,listOf(store),catalog.map{it.copy(versionCode=101)},evidence).single().sources.isEmpty())
    check(runCatching{DesktopLegacyRepoIndex.enrich(summary,listOf(store.copy(signingKey="different")),catalog,evidence)}.isFailure)
    check(runCatching{DesktopLegacyRepoIndex.enrich(summary,listOf(store.copy(legacy=false)),catalog,evidence)}.isFailure)
    check(runCatching{DesktopLegacyRepoIndex.parse(json.replace("42","42.0").toByteArray(),summary.stores.single())}.isFailure)
    check(runCatching{DesktopLegacyRepoIndex.parse((json.dropLast(1)+","+json.drop(1)).toByteArray(),summary.stores.single())}.isFailure)
    println("PASS: user-selected legacy index resolves previously unseen exact source IDs with version and repository checks")
}
