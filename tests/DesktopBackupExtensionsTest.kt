package dev.nooir.desktop.suwayomi

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

fun main(){
    val raw=ByteArrayOutputStream().also{b->GZIPOutputStream(b).use{it.write(byteArrayOf(10,5,8,42,18,1,120))}}.toByteArray()
    val store=DesktopBackupExtensions.Store("https://example.org/repo.json","Synthetic","Test","synthetic-key","https://example.org",null,true,"https://example.org/index.min.json")
    val bytes=DesktopBackupExtensions.withStores(raw,listOf(store))
    val parsed=DesktopBackupInspector.inspect(bytes.inputStream())
    check(parsed.mangas==1 && parsed.stores.single().signingKey=="synthetic-key" && parsed.stores.single().legacy)
    val ext=DesktopBackupExtensions.Extension("synthetic.pkg","Synthetic source",store.indexUrl,false,false,setOf(42))
    val candidate=DesktopBackupExtensions.plan(parsed,listOf(store),listOf(ext),setOf(42))
    check(candidate.stores.single().state==DesktopBackupExtensions.StoreState.PRESENT_MATCH)
    check(candidate.sources.single().state==DesktopBackupExtensions.SourceState.CANDIDATE_REVIEW)
    val conflict=DesktopBackupExtensions.plan(parsed,listOf(store.copy(signingKey="changed")),listOf(ext),setOf(42))
    check(conflict.stores.single().state==DesktopBackupExtensions.StoreState.KEY_CONFLICT)
    check(conflict.sources.single().state==DesktopBackupExtensions.SourceState.UNRESOLVED)
    val ambiguous=DesktopBackupExtensions.plan(parsed,listOf(store),listOf(ext,ext.copy(pkgName="other.pkg")),setOf(42))
    check(ambiguous.sources.single().state==DesktopBackupExtensions.SourceState.AMBIGUOUS)
    check(DesktopBackupExtensions.plan(parsed,listOf(store),listOf(ext.copy(installed=true)),emptySet()).sources.single().state==DesktopBackupExtensions.SourceState.PRESENT)
    check(runCatching{DesktopBackupExtensions.withStores(bytes,listOf(store))}.isFailure)
    println("PASS: portable Mihon store metadata, exact source matching, signing conflict, ambiguity and duplicate refusal")
}
