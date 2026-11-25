import com.sun.net.httpserver.HttpServer
import dev.nooir.desktop.suwayomi.MiniJson
import dev.nooir.desktop.suwayomi.SuwayomiClient
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.imageio.ImageIO

fun main() {
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val mode = AtomicReference("ok")
    val manifests = AtomicInteger()
    val pages = AtomicInteger()
    val png = ByteArrayOutputStream().also {
        check(ImageIO.write(BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png", it))
    }.toByteArray()
    server.createContext("/api/graphql") { ctx ->
        try {
            check(ctx.requestMethod == "POST")
            check(ctx.requestHeaders.getFirst("Authorization") == "Bearer synthetic-only")
            val body = ctx.requestBody.bufferedReader().use { it.readText() }
            val q = (MiniJson.parse(body) as Map<*, *>)["query"] as String
            check("fetchChapterPages(input: { chapterId: 17 })" in q)
            check("enqueue" !in q && "updateChapter" !in q)
            manifests.incrementAndGet()
            val paths = when (mode.get()) {
                "remote" -> listOf("https://example.com/api/v1/manga/9/chapter/3/page/0", "/api/v1/manga/9/chapter/3/page/1")
                "wrongPath" -> listOf("/api/v1/manga/9/chapter/4/page/0", "/api/v1/manga/9/chapter/3/page/1")
                "duplicate" -> List(2) { "/api/v1/manga/9/chapter/3/page/0" }
                "query" -> listOf("/api/v1/manga/9/chapter/3/page/0?token=x", "/api/v1/manga/9/chapter/3/page/1")
                "short" -> listOf("/api/v1/manga/9/chapter/3/page/0")
                else -> listOf("/api/v1/manga/9/chapter/3/page/0", "/api/v1/manga/9/chapter/3/page/1")
            }.joinToString(",") { MiniJson.quote(it) }
            val manga = if (mode.get() == "wrongManga") 8 else 9
            val count = if (mode.get() == "tooMany") 5001 else 2
            val conflict = if (mode.get() == "conflict") """{"deviceName":"Test reader","remotePage":1}""" else "null"
            val response = """{"data":{"fetchChapterPages":{"pages":[$paths],"chapter":{"id":17,"mangaId":$manga,"sourceOrder":3,"pageCount":$count,"lastPageRead":0},"syncConflict":$conflict}}}""".toByteArray()
            ctx.responseHeaders.add("Content-Type","application/json")
            ctx.sendResponseHeaders(200,response.size.toLong())
            ctx.responseBody.use { it.write(response) }
        } finally { ctx.close() }
    }
    server.createContext("/api/v1/manga/9/chapter/3/page/") { ctx ->
        try {
            check(ctx.requestMethod == "GET")
            check(ctx.requestHeaders.getFirst("Authorization") == "Bearer synthetic-only")
            check(ctx.requestURI.rawPath in setOf("/api/v1/manga/9/chapter/3/page/0","/api/v1/manga/9/chapter/3/page/1"))
            check(ctx.requestURI.rawQuery == null)
            pages.incrementAndGet()
            when (mode.get()) {
                "redirect" -> {
                    ctx.responseHeaders.add("Location","https://example.com/image.png")
                    ctx.sendResponseHeaders(302,-1)
                }
                "html" -> {
                    ctx.responseHeaders.add("Content-Type","text/html")
                    ctx.sendResponseHeaders(200,1)
                    ctx.responseBody.use { it.write(65) }
                }
                "oversize" -> {
                    ctx.responseHeaders.add("Content-Type","image/png")
                    ctx.sendResponseHeaders(200,25L*1024*1024+1)
                }
                else -> {
                    ctx.responseHeaders.add("Content-Type","image/png")
                    ctx.sendResponseHeaders(200,png.size.toLong())
                    ctx.responseBody.use { it.write(png) }
                }
            }
        } finally { ctx.close() }
    }
    server.start()
    try {
        val client = SuwayomiClient("http://127.0.0.1:${server.address.port}", "Bearer synthetic-only")
        val chapter = client.openOnlineChapter(9,17)
        check(chapter.pageCount==2 && chapter.lastPageRead==0 && chapter.syncConflict==null)
        check(manifests.get()==1 && pages.get()==0) { "Manifest must not fetch page images" }
        check(client.onlinePage(chapter,1).contentEquals(png) && pages.get()==1)
        check(runCatching { client.onlinePage(chapter,-1) }.isFailure)
        check(runCatching { client.onlinePage(chapter,2) }.isFailure && pages.get()==1)
        for (bad in listOf("remote","wrongPath","duplicate","query","short","wrongManga","tooMany")) {
            mode.set(bad)
            check(runCatching { client.openOnlineChapter(9,17) }.isFailure) { "Accepted invalid manifest: $bad" }
        }
        mode.set("conflict")
        check(client.openOnlineChapter(9,17).syncConflict?.remotePage==1)
        check(pages.get()==1)
        for (bad in listOf("redirect","html","oversize")) {
            mode.set(bad)
            check(runCatching { client.onlinePage(chapter,0) }.isFailure) { "Accepted invalid page: $bad" }
        }
        check(pages.get()==4)
        println("PASS: pinned online chapter manifest, explicit on-demand bounded page fetch, no redirects, wrong chapter/path/media/size rejection, sync conflict surfaced")
    } finally { server.stop(0) }
}
