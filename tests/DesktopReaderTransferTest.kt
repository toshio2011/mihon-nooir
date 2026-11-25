package dev.nooir.desktop.ui

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.charset.StandardCharsets

fun main() {
    check(runCatching { DesktopReaderTransfer.privateReader("8.8.8.8") }.isFailure)
    check(runCatching { DesktopReaderTransfer.privateReader("127.0.0.1") }.isFailure)
    check(runCatching { DesktopReaderTransfer.folder("/Books/../Protected") }.isFailure)
    check(runCatching { DesktopReaderTransfer.filename("../book.xtc") }.isFailure)
    DesktopReaderTransfer.privateReader("192.168.4.1")
    val file = Files.createTempFile("nooir-transfer-", ".xtc").toFile()
    val book = byteArrayOf(88, 84, 67, 0, 1, 2, 3, 4)
    file.writeBytes(book)
    val server = HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
    var uploaded = false
    var posts = 0
    var wrongAck = false
    var wrongSize = false
    fun listing(): String = if (!uploaded) "[]" else
        """[{"name":"test.xtc","size":${if (wrongSize) book.size+1 else book.size},"isDirectory":false,"isEpub":false}]"""
    server.createContext("/api/files") { ex ->
        check(ex.requestMethod == "GET")
        val data = listing().toByteArray(StandardCharsets.UTF_8)
        ex.sendResponseHeaders(200,data.size.toLong())
        ex.responseBody.use { it.write(data) }
    }
    server.createContext("/upload") { ex ->
        check(ex.requestMethod == "POST")
        check(ex.requestHeaders.getFirst("Content-Type").startsWith("multipart/form-data; boundary="))
        val raw = ex.requestBody.use { it.readAllBytes() }.toString(StandardCharsets.ISO_8859_1)
        check(raw.contains("filename=\"test.xtc\"") && raw.contains(book.toString(StandardCharsets.ISO_8859_1)))
        posts++
        uploaded = true
        val data = (if (wrongAck) "OK" else "File uploaded successfully: test.xtc").toByteArray()
        ex.sendResponseHeaders(200,data.size.toLong())
        ex.responseBody.use { it.write(data) }
    }
    server.start()
    try {
        val client = DesktopReaderTransfer.loopbackTest(server.address.port)
        var last = 0L
        client.send(file,"/Books","test.xtc") { sent,total ->
            check(total == book.size.toLong()); last = sent
        }
        check(last == book.size.toLong() && posts == 1)
        check(runCatching { client.send(file,"/Books","test.xtc") }.isFailure && posts == 1)
        uploaded = false; wrongAck = true
        check(runCatching { client.send(file,"/Books","test.xtc") }.isFailure && posts == 2)
        uploaded = false; wrongAck = false; wrongSize = true
        check(runCatching { client.send(file,"/Books","test.xtc") }.isFailure && posts == 3)
        check(file.readBytes().contentEquals(book))
        println("PASS: private reader address validation, multipart, duplicate refusal, exact ACK, size verification and source immutability")
    } finally { server.stop(0); file.delete() }
}
