package dev.nooir.mangabridge

import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Very light reader-side filesystem view. No recursive indexing, thumbnails,
 * local copies, polling or XTEink firmware changes. One GET scans only the
 * requested directory via Folio Nooir's existing /api/files?path endpoint.
 */
object NooirDeviceFileClient {
    data class Item(val name: String, val sizeBytes: Long, val directory: Boolean,
        val book: Boolean, val epub: Boolean)
    private const val MAX_LISTING_BYTES = 512 * 1024

    private fun hostValue(value: String): String {
        val input = value.trim()
        require(input.matches(Regex("[A-Za-z0-9.-]{1,253}")) &&
            !input.startsWith('.') && !input.endsWith('.')) {
            "Enter the reader hostname/IP only, without http:// or a path"
        }
        return input
    }

    fun normalizedPath(path: String): String {
        require(path.startsWith('/') && path.length <= 512 &&
            !path.contains('\\') && !path.contains('\u0000') &&
            path.split('/').none { it == ".." || it == "." }) {
            "Invalid device folder path"
        }
        return path.trimEnd('/').ifEmpty { "/" }
    }

    fun childPath(folder: String, name: String): String {
        require(name.isNotBlank() && name.length <= 255 &&
            name != "." && name != ".." && !name.startsWith('.') &&
            !name.contains('/') && !name.contains('\\') && !name.contains('\u0000')) {
            "Invalid file name"
        }
        val parent = normalizedPath(folder)
        return (if (parent == "/") "" else parent) + "/" + name
    }

    fun parentPath(path: String): String {
        val p = normalizedPath(path)
        return if (p == "/") "/" else p.substringBeforeLast('/').ifEmpty { "/" }
    }

    fun fullWebFilesUrl(host: String): String = "http://" + hostValue(host) + "/files"

    private fun get(host: String, endpoint: String): ByteArray {
        val h = hostValue(host)
        require(endpoint.startsWith("/api/files?path="))
        val url = URL("http://" + h + endpoint)
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 3500
            conn.readTimeout = 8500
            conn.instanceFollowRedirects = false
            conn.requestMethod = "GET"
            val status = conn.responseCode
            check(status == 200) { "Reader responded HTTP $status; open Folio Nooir File Transfer mode" }
            val contentType = conn.contentType.orEmpty()
            check(contentType.startsWith("application/json")) { "Reader did not return a file list" }
            conn.inputStream.use { stream ->
                val buffer = ByteArray(8192)
                val result = ByteArrayOutputStream()
                while (true) {
                    val n = stream.read(buffer)
                    if (n < 0) break
                    check(result.size() + n <= MAX_LISTING_BYTES) {
                        "Folder contains too many entries; use the Folio Nooir web file manager"
                    }
                    result.write(buffer, 0, n)
                }
                return result.toByteArray()
            }
        } finally { conn.disconnect() }
    }

    fun list(host: String, folder: String): List<Item> {
        val safe = normalizedPath(folder)
        val query = "/api/files?path=" + URLEncoder.encode(safe, "UTF-8")
        val array = JSONArray(String(get(host, query), StandardCharsets.UTF_8))
        require(array.length() <= 2500) { "Folder has too many entries" }
        return (0 until array.length()).mapNotNull { i ->
            val entry = array.optJSONObject(i) ?: return@mapNotNull null
            val name = entry.optString("name")
            if (name.isBlank() || name.startsWith('.') ||
                name == "XTCache" || name == "System Volume Information" ||
                name.contains('/') || name.contains('\\')) return@mapNotNull null
            Item(
                name = name.take(255),
                sizeBytes = entry.optLong("size", 0L).coerceAtLeast(0),
                directory = entry.optBoolean("isDirectory"),
                book = entry.optBoolean("isBook"),
                epub = entry.optBoolean("isEpub"),
            )
        }.sortedWith(compareByDescending<Item> { it.directory }.thenBy { it.name.lowercase() })
    }
}
