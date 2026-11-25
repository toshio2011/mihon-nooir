package dev.nooir.desktop.suwayomi

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

private fun v(n:Long):ByteArray {
    var x=n;val b=ByteArrayOutputStream()
    while(x ushr 7!=0L){b.write((x.toInt() and 127) or 128);x=x ushr 7}
    b.write(x.toInt());return b.toByteArray()
}
private fun num(n:Int,x:Long)=v((n*8).toLong())+v(x)
private fun msg(n:Int,x:ByteArray)=v((n*8+2).toLong())+v(x.size.toLong())+x
private fun str(n:Int,x:String)=msg(n,x.toByteArray())
private fun gz(x:ByteArray)=ByteArrayOutputStream().also{ b->GZIPOutputStream(b).use{it.write(x)} }.toByteArray()
fun main(){
    val chapter=msg(16,str(1,"/chapter")+str(2,"Synthetic")+num(4,1)+num(5,1)+num(6,12))
    val manga=msg(1,num(1,-123)+str(2,"/synthetic")+chapter+msg(104,str(1,"/chapter")+num(2,1234)))
    val store=msg(106,str(1,"https://example.org/index.min.json")+str(2,"Synthetic repo")+str(5,"test-signing-key"))
    val root=manga+msg(2,str(1,"Synthetic category"))+msg(101,str(1,"Synthetic source")+num(2,-123))+
        store+msg(104,str(1,"synthetic preference"))+msg(105,str(1,"synthetic source pref"))+num(999,1)
    val s=DesktopBackupInspector.inspect(gz(root).inputStream())
    check(s.mangas==1&&s.favorites==1&&s.chapters==1&&s.read==1&&s.bookmarks==1&&s.progressed==1&&s.history==1)
    check(s.categories==1&&s.preferences==1&&s.sourcePreferences==1&&s.sources.single().id==-123L)
    check(s.stores.single().reviewableHttps&&s.stores.single().signingKey=="test-signing-key")
    check(s.missingSources(emptySet())==setOf(-123L)&&s.missingSources(setOf(-123L)).isEmpty())
    check(runCatching{DesktopBackupInspector.inspect(gz(root+byteArrayOf(0)).inputStream())}.isFailure)
    check(runCatching{DesktopBackupInspector.inspect(gz(root+msg(106,str(1,"https://example.org/index.min.json")+str(5,"different"))).inputStream())}.isFailure)
    check(runCatching{DesktopBackupInspector.inspect(byteArrayOf(1,2,3).inputStream())}.isFailure)
    println("PASS: bounded backup inspection, signed source IDs, repository preview, duplicate conflict and invalid file rejection")
}
