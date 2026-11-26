package dev.nooir.desktop.suwayomi

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import java.util.concurrent.atomic.AtomicInteger

fun main(){
    val dir=Files.createTempDirectory("nooir-extension-recovery").toFile()
    val url="https://example.org/repo.json"
    val store=DesktopBackupExtensions.Store(url,"Synthetic","Test","synthetic-key","https://example.org",null,true,null)
    val raw=ByteArrayOutputStream().also{b->GZIPOutputStream(b).use{it.write(byteArrayOf(10,5,8,42,18,1,120))}}.toByteArray()
    val file=dir.resolve("sample.tachibk").apply{writeBytes(DesktopBackupExtensions.withStores(raw,listOf(store)))}
    val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
    val installed=AtomicInteger()
    var key="synthetic-key"
    var ambiguous=false
    val mutations=AtomicInteger()
    server.createContext("/api/graphql"){ex->
        try{
            val q=ex.requestBody.readBytes().toString(Charsets.ISO_8859_1)
            fun node(pkg:String)="""{"pkgName":"$pkg","name":"Synthetic","storeIndexUrl":"$url","isInstalled":"""+(installed.get()>0)+""","isObsolete":false,"source":{"nodes":[{"id":"42"}]}}"""
            val body=when{
                "NooirValidate" in q->"""{"data":{"validateBackup":{"missingSources":"""+(if(installed.get()==0) """[{"id":"42","name":"Synthetic"}]""" else "[]")+""","missingTrackers":[]}}}"""
                "NooirBackupStores" in q->"""{"data":{"extensionStores":{"totalCount":1,"nodes":[{"name":"Synthetic","badgeLabel":"Test","signingKey":"$key","contactWebsite":"https://example.org","contactDiscord":null,"indexUrl":"$url","isLegacy":true,"extensionListUrl":null}]}}}"""
                "NooirBackupCatalog" in q->"""{"data":{"extensions":{"totalCount":"""+(if(ambiguous) "2" else "1")+""","nodes":["""+node("synthetic.pkg")+(if(ambiguous) ","+node("other.pkg") else "")+"]}}}"
                "NooirRecoverExtension" in q->{check("synthetic.pkg" in q && "install: true" in q);mutations.incrementAndGet();installed.set(1);"""{"data":{"updateExtension":{"extension":"""+node("synthetic.pkg")+"}}}"}
                else->error("Unexpected operation")
            }.toByteArray()
            ex.sendResponseHeaders(200,body.size.toLong());ex.responseBody.use{it.write(body)}
        }finally{ex.close()}
    }
    server.start()
    try{
        val api=DesktopBackupTransport("http://127.0.0.1:"+server.address.port)
        val approval=DesktopBackupExtensions.recoverable(api.extensionPlan(file)).single()
        check(mutations.get()==0)
        key="changed"
        check(runCatching{api.installReviewedExtension(file,approval)}.isFailure && mutations.get()==0)
        key="synthetic-key";ambiguous=true
        check(runCatching{api.installReviewedExtension(file,approval)}.isFailure && mutations.get()==0)
        ambiguous=false
        check(api.installReviewedExtension(file,approval)==setOf(42L) && mutations.get()==1)
        check(runCatching{api.installReviewedExtension(file,approval)}.isFailure && mutations.get()==1)
        val requested=listOf(approval.copy(pkgName="first.pkg"),approval.copy(pkgName="bad.pkg"),approval.copy(pkgName="last.pkg"))
        val attempted=mutableListOf<String>()
        val results=DesktopBackupExtensions.recoverBatch(requested){
            attempted.add(it.pkgName)
            if(it.pkgName=="bad.pkg")error("Synthetic package unavailable")
            it.sourceIds
        }
        check(attempted==requested.map{it.pkgName})
        check(results.map{it.needsReview}==listOf(false,true,false))
        check(results[0].confirmed==setOf(42L) && results[1].confirmed.isEmpty() && results[2].confirmed==setOf(42L))
        check(runCatching{DesktopBackupExtensions.recoverBatch(listOf(approval,approval)){error("Must not run")}}.isFailure)
        println("PASS: best-effort approved extension batch continues after failure, no duplicate or retry")
        println("PASS: explicitly approved exact-source extension recovery, key conflict, ambiguity, post-install verification and repeat refusal")
    }finally{server.stop(0);dir.deleteRecursively()}
}
