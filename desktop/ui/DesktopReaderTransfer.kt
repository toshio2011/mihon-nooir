package dev.nooir.desktop.ui

import dev.nooir.bridge.XteinkTransferPolicy
import dev.nooir.desktop.suwayomi.MiniJson
import dev.nooir.desktop.suwayomi.asList
import dev.nooir.desktop.suwayomi.asObject
import java.io.File
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

/** Folio Nooir documented HTTP transfer API. No scan, deletion, or automatic retry. */
internal class DesktopReaderTransfer private constructor(private val base: URI) {
    companion object {
        fun privateReader(ip: String): DesktopReaderTransfer {
            val octets = ip.trim().split('.')
            require(octets.size == 4 && octets.all {
                it.matches(Regex("0|[1-9][0-9]{0,2}")) && it.toInt() in 0..255
            }) { "Enter the private IPv4 address displayed by the reader, without http:// or a port" }
            val a = octets.map(String::toInt)
            require(a[0] == 10 || a[0] == 172 && a[1] in 16..31 ||
                a[0] == 192 && a[1] == 168) { "Only private IPv4 LAN reader addresses are supported" }
            return DesktopReaderTransfer(URI("http://" + a.joinToString(".")))
        }
        /** Internal local mock contract, not exposed in UI. */
        internal fun loopbackTest(port: Int): DesktopReaderTransfer {
            require(port in 1..65535)
            return DesktopReaderTransfer(URI("http://127.0.0.1:$port"))
        }
        fun folder(value: String): String {
            val path = value.trim().trimEnd('/').ifBlank { "/" }
            require(path.length <= 240 && path.startsWith('/') &&
                path.split('/').drop(1).all { part ->
                    (path == "/" && part.isEmpty()) ||
                        (part.isNotBlank() && !part.startsWith('.') &&
                        part.matches(Regex("[A-Za-z0-9_ ()-]+")) &&
                        !part.equals("XTCache", true) &&
                        !part.equals("System Volume Information", true))
                }) { "Use an existing reader folder such as /Books or / (ASCII letters, numbers, spaces, _()-)" }
            return path
        }
        fun filename(value: String): String {
            val name = value.trim()
            require(name.length in 1..120 && name.matches(Regex("[A-Za-z0-9_ ()-][A-Za-z0-9_. ()-]*")) &&
                !name.endsWith('.') && name.substringAfterLast('.', "").lowercase() in
                setOf("epub","cbz","xtc","xtch","pdf","txt")) {
                "Use a safe ASCII book filename with its supported extension"
            }
            return name
        }
    }

    private data class Entry(val name: String, val size: Long, val directory: Boolean)
    private fun connection(path: String, folder: String): HttpURLConnection {
        val query = URLEncoder.encode(folder, StandardCharsets.UTF_8)
        return (URI(base.toString() + path + "?path=" + query).toURL().openConnection(Proxy.NO_PROXY)
            as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = 8_000
            readTimeout = 60_000
            useCaches = false
        }
    }

    private fun response(c: HttpURLConnection, cap: Int): String {
        val status = c.responseCode
        require(status in 200..299) { "Reader returned HTTP $status; check File Transfer mode" }
        return c.inputStream.use { stream ->
            val bytes = stream.readNBytes(cap + 1)
            require(bytes.size <= cap) { "Reader response exceeds the safe limit" }
            String(bytes, StandardCharsets.UTF_8)
        }
    }

    private fun list(path: String): List<Entry> {
        val c = connection("/api/files", path)
        try {
            return MiniJson.parse(response(c, 4_000_000)).asList().map { item ->
                val obj = item.asObject()
                val name = obj["name"] as? String ?: error("Invalid reader file listing")
                val size = obj["size"] as? Long ?: error("Invalid reader file size")
                val dir = obj["isDirectory"] as? Boolean ?: error("Invalid reader directory flag")
                require(size >= 0)
                Entry(name, size, dir)
            }
        } finally { c.disconnect() }
    }

    /**
     * Refuse an observed filename collision; the actual firmware endpoint can
     * overwrite, so concurrent writers must be avoided. A response and a
     * matching re-list size are required, not just completed byte transmission.
     */
    fun send(file: File, destination: String, outputName: String, progress: (Long, Long) -> Unit = { _, _ -> }) {
        val name = filename(outputName)
        val path = folder(destination)
        require(file.isFile && file.canRead() && file.length() > 0) { "Select a readable nonempty local file" }
        require(file.extension.equals(name.substringAfterLast('.'), true)) { "Keep the original file extension" }
        val length = file.length()
        XteinkTransferPolicy.adviseIfTooLarge(length)?.let { error(it) }
        require(list(path).none { it.name.equals(name, true) }) {
            "Destination name already exists; nothing was uploaded"
        }
        val boundary = "NooirWindows-" + UUID.randomUUID().toString()
        val head = ("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"$name\"\r\n" +
            "Content-Type: application/octet-stream\r\n\r\n").toByteArray(StandardCharsets.US_ASCII)
        val tail = "\r\n--$boundary--\r\n".toByteArray(StandardCharsets.US_ASCII)
        val c = connection("/upload", path)
        try {
            c.requestMethod = "POST"
            c.doOutput = true
            c.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            c.setFixedLengthStreamingMode(length + head.size + tail.size)
            c.outputStream.buffered().use { output ->
                output.write(head)
                file.inputStream().buffered().use { input ->
                    val buffer = ByteArray(32 * 1024)
                    var sent = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        require(sent + n <= length) { "Source changed during upload" }
                        output.write(buffer, 0, n)
                        sent += n
                        progress(sent, length)
                    }
                    require(sent == length && file.length() == length) { "Source changed during upload" }
                }
                output.write(tail)
            }
            val ack = response(c, 4096).trim()
            require(ack == "File uploaded successfully: $name") {
                "Reader did not return the expected upload confirmation"
            }
        } finally { c.disconnect() }
        val verified = list(path).singleOrNull { it.name == name && !it.directory }
        check(verified?.size == length) { "Reader file listing did not verify the uploaded file size" }
    }
}
