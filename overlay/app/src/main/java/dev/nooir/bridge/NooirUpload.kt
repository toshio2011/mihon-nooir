package dev.nooir.bridge

import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

/** Native HTTP uploader for Folio Nooir / CrossPoint's existing POST /upload endpoint.
 * Device must be in File Transfer mode and phone on the same LAN / device hotspot.
 * Plain HTTP is only suitable for a trusted local network.
 */
object NooirUpload {
    fun checkConnected(deviceHost: String): String {
        val uri = URI("http://" + deviceHost.trim())
        require(uri.scheme == "http" && uri.host != null && uri.rawUserInfo == null &&
            uri.port == -1 && uri.path.isNullOrEmpty() && uri.rawQuery == null) {
            "Use only your XTEink IP address or local hostname (e.g. crosspoint.local)"
        }
        val conn = (URI("http://" + uri.host + "/api/status").toURL().openConnection() as HttpURLConnection)
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 8000
            check(conn.responseCode == 200) {
                "Cannot reach Nooir File Transfer (HTTP " + conn.responseCode + "). Enable File Transfer on XTEink."
            }
            val status = conn.inputStream.bufferedReader().use { it.readText() }
            check(status.contains("\"device\"") && status.contains("\"version\"")) {
                "Connected to host, but it does not appear to be the Nooir file-transfer server"
            }
            return status
        } finally {
            conn.disconnect()
        }
    }

    fun upload(file: File, deviceHost: String, destination: String = "/"): String {
        require(file.isFile && file.length() > 0) { "Missing export file" }
        require(destination.startsWith('/') && !destination.contains("..")) { "Invalid folder" }
        require(file.name.matches(Regex("[A-Za-z0-9_.() -]{1,160}\\.(xtc|xtch|epub|cbz)", RegexOption.IGNORE_CASE))) {
            "Unsupported file name or extension"
        }
        val uri = URI("http://$deviceHost")
        require(uri.scheme == "http" && uri.host != null && uri.rawUserInfo == null && uri.port == -1 && uri.path.isNullOrEmpty()) {
            "Enter an IP address or local hostname, without a path or port"
        }
        val path = URLEncoder.encode(destination, StandardCharsets.UTF_8.name())
        val endpoint = URI("http://${uri.host}/upload?path=$path").toURL()
        val boundary = "Nooir-" + UUID.randomUUID().toString()
        val conn = (endpoint.openConnection() as HttpURLConnection)
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 180000
            // ESP32 upload handlers are not guaranteed to support chunked request bodies.
            // Send a known content length while still streaming from disk.
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            val safeName = file.name.replace("\"", "")
            val prefix = ("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"$safeName\"\r\n" +
                "Content-Type: application/octet-stream\r\n\r\n").toByteArray(StandardCharsets.UTF_8)
            val suffix = "\r\n--$boundary--\r\n".toByteArray(StandardCharsets.US_ASCII)
            conn.setFixedLengthStreamingMode(prefix.size.toLong() + file.length() + suffix.size)
            conn.outputStream.use { out ->
                out.write(prefix)
                file.inputStream().use { input -> input.copyTo(out, 16 * 1024) }
                out.write(suffix)
            }
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            check(code in 200..299) { "Nooir upload failed: HTTP $code ($body)" }
            return body
        } finally { conn.disconnect() }
    }
}