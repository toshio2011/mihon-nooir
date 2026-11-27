package dev.nooir.desktop.compose

import com.sun.net.httpserver.HttpServer
import dev.nooir.desktop.suwayomi.SuwayomiClient
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean

fun main() {
    val page = AtomicInteger(0)
    val writes = AtomicInteger(0)
    val conflict = AtomicBoolean(false)
    val server = HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
    server.createContext("/api/graphql") { ex ->
        try {
            val q = ex.requestBody.readNBytes(100001).toString(Charsets.UTF_8)
            val record = """{"id":101,"mangaId":1,"sourceOrder":1,"pageCount":3,"lastPageRead":${page.get()}}"""
            val data = when {
                "NooirChapterPages" in q -> {
                    val sync = if (conflict.get()) """{"deviceName":"QA other reader","remotePage":2}""" else "null"
                    """{"data":{"fetchChapterPages":{"chapter":$record,"pages":["/api/v1/manga/1/chapter/1/page/0","/api/v1/manga/1/chapter/1/page/1","/api/v1/manga/1/chapter/1/page/2"],"syncConflict":$sync}}}"""
                }
                "NooirCurrentPosition" in q -> """{"data":{"chapter":$record}}"""
                "NooirSavePosition" in q -> {
                    check("lastPageRead: 1" in q)
                    writes.incrementAndGet()
                    page.set(1)
                    """{"data":{"updateChapter":{"chapter":{"id":101,"mangaId":1,"lastPageRead":1}}}}"""
                }
                else -> error("Unexpected request")
            }.toByteArray()
            ex.sendResponseHeaders(200,data.size.toLong())
            ex.responseBody.use { it.write(data) }
        } finally { ex.close() }
    }
    server.start()
    try {
        val api=SuwayomiClient("http://127.0.0.1:"+server.address.port)
        val chapter=api.openOnlineChapter(1,101)
        check(chapter.lastPageRead==0 && writes.get()==0)
        check(api.saveOnlinePosition(chapter,1)==1 && writes.get()==1)
        check(api.openOnlineChapter(1,101).lastPageRead==1)
        check(api.saveOnlinePosition(chapter,1)==1 && writes.get()==1)
        check(runCatching { api.saveOnlinePosition(chapter,0) }.isFailure && writes.get()==1)
        conflict.set(true)
        val other=api.openOnlineChapter(1,101)
        check(other.syncConflict!=null)
        check(runCatching { api.saveOnlinePosition(other,2) }.isFailure && writes.get()==1)
        println("PASS: explicit position save, reopen, conflict and rewind refusal")
    } finally { server.stop(0) }
}
