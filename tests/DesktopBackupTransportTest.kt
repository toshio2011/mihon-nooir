package dev.nooir.desktop.suwayomi

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

fun main(){
    val tmp=Files.createTempDirectory("nooir-backup-transport").toFile()
    val gz=ByteArrayOutputStream().also{b->GZIPOutputStream(b).use{it.write(byteArrayOf(10,5,8,0,18,1,120))}}.toByteArray()
    val input=tmp.resolve("sample.tachibk").apply{writeBytes(gz)}
    val recovery=tmp.resolve("recovery.tachibk")
    val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
    val calls=mutableListOf<String>()
    server.createContext("/api/graphql"){ex->
        val body=ex.requestBody.readBytes().toString(Charsets.ISO_8859_1)
        val result=when{
            "NooirValidate" in body->{calls.add("validate");"""{"data":{"validateBackup":{"missingSources":[{"id":"-123","name":"Synthetic"}],"missingTrackers":[]}}}"""}
            "NooirExport" in body->{calls.add("export");check("includeServerSettings: false" in body);"""{"data":{"createBackup":{"url":"/api/graphql/files/backup/synthetic.tachibk"}}}"""}
            "NooirRestore(" in body->{calls.add("restore");check(recovery.readBytes().contentEquals(gz));check("includeServerSettings: false" in body);"""{"data":{"restoreBackup":{"id":"123","status":{"state":"SUCCESS"}}}}"""}
            else->error("Unexpected request")
        }.toByteArray()
        ex.sendResponseHeaders(200,result.size.toLong());ex.responseBody.use{it.write(result)}
    }
    server.createContext("/api/graphql/files/backup/synthetic.tachibk"){ex->ex.sendResponseHeaders(200,gz.size.toLong());ex.responseBody.use{it.write(gz)}}
    server.start()
    try{
        val api=DesktopBackupTransport("http://127.0.0.1:"+server.address.port)
        check(api.validate(input).missingSources.single().id==-123L)
        val ticket=api.beginRestore(input,recovery)
        check(api.await(ticket)=="SUCCESS")
        check(calls==listOf("validate","validate","export","restore"))
        check(runCatching{api.export(recovery)}.isFailure)
        check(runCatching{DesktopBackupTransport("https://example.com:443")}.isFailure)
        check(recovery.readBytes().contentEquals(gz))
        println("PASS: local backup validate, recovery snapshot before restore, flags, no overwrite and nonlocal refusal")
    }finally{server.stop(0);tmp.deleteRecursively()}
}
