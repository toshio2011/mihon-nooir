package dev.nooir.bridge

import java.io.File
import java.io.InputStream
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.text.Normalizer
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import org.json.JSONObject

/** Native HTTP uploader for Folio Nooir / CrossPoint's existing POST /upload endpoint.
 * Device must be in File Transfer mode and phone on the same LAN / device hotspot.
 * Plain HTTP is only suitable for a trusted local network.
 */
object NooirUpload {

    /** Send *any* SAF document without copying large files to app storage.
     * The receiver determines whether it can display/open the file.
     */
    /** Conservative default for the ESP32 HTTP/SD path: don't start giant single-file transfers. */
    private const val MAX_SAFE_UPLOAD_BYTES = 180L * 1024 * 1024

    fun uploadDocument(
        context: Context, document: Uri, deviceHost: String, destination: String = "/Books",
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): String {
        val resolver = context.contentResolver
        var filename = "document"
        var length: Long? = null
        resolver.query(document, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) filename = cursor.getString(nameIndex) ?: "document"
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) length = cursor.getLong(sizeIndex)
            }
        }
        val safeName = sanitizeFileName(filename)
        if (length != null && length!! > MAX_SAFE_UPLOAD_BYTES) {
            error("File is over 180 MB. Split it into smaller files for reliable XTEink transfer.")
        }
        if (length == null || length!! < 0L) {
            // Some SAF/cloud providers omit SIZE. Nooir doesn't reliably support
            // chunked uploads, so spool only these uncommon inputs with a hard cap.
            val staging = File.createTempFile("manganooir-unknown-size-", ".tmp", context.cacheDir)
            try {
                resolver.openInputStream(document)?.use { input ->
                    staging.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var count = 0L
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            count += n
                            require(count <= MAX_SAFE_UPLOAD_BYTES) {
                                "Cloud file exceeds 180 MB safe transfer limit"
                            }
                            output.write(buffer, 0, n)
                        }
                    }
                } ?: error("Cannot read selected file")
                require(staging.length() > 0L) { "Selected file is empty" }
                return uploadContent(safeName, staging.length(), deviceHost, destination, onProgress) {
                    staging.inputStream()
                }
            } finally {
                staging.delete()
            }
        }
        require(length!! > 0) { "Selected file is empty" }
        return uploadContent(safeName, length!!, deviceHost, destination, onProgress) {
            resolver.openInputStream(document) ?: error("Cannot read selected file")
        }
    }

    private fun sanitizeFileName(value: String): String {
        val ascii = Normalizer.normalize(value, Normalizer.Form.NFKD)
            .replace(Regex("[^A-Za-z0-9_.() -]"), "_")
            .trim().trim('.')
            .take(150)
        require(ascii.isNotBlank() && ascii != "." && ascii != "..") { "Invalid filename" }
        return ascii
    }

    private fun uploadContent(
        filename: String,
        fileLength: Long,
        deviceHost: String,
        destination: String,
        onProgress: (Long, Long) -> Unit,
        open: () -> InputStream,
    ): String {
        require(fileLength in 1..MAX_SAFE_UPLOAD_BYTES) {
            "File is over 180 MB. Split it into smaller files before sending to XTEink."
        }
        require(destination.startsWith("/") && !destination.contains("..")) { "Invalid destination folder" }
        val uri = URI("http://" + deviceHost.trim())
        require(uri.scheme == "http" && uri.host != null && uri.port == -1 &&
            uri.rawUserInfo == null && uri.path.isNullOrEmpty() && uri.rawQuery == null) {
            "Enter XTEink IP or hostname only"
        }
        val folder = URLEncoder.encode(destination, StandardCharsets.UTF_8.name())
        val boundary = "MangaNooir-" + UUID.randomUUID()
        val url = URI("http://" + uri.host + "/upload?path=" + folder).toURL()
        val prefix = ("--" + boundary + "\r\n" +
            "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n" +
            "Content-Type: application/octet-stream\r\n\r\n").toByteArray(StandardCharsets.UTF_8)
        val suffix = ("\r\n--" + boundary + "--\r\n").toByteArray(StandardCharsets.US_ASCII)
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 180000
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary)
            conn.setFixedLengthStreamingMode(prefix.size.toLong() + fileLength + suffix.size)
            conn.outputStream.use { output ->
                output.write(prefix)
                open().use { input ->
                    val buffer = ByteArray(32 * 1024)
                    var written = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        written += count
                        require(written <= fileLength) { "File grew during upload" }
                        output.write(buffer, 0, count)
                        onProgress(written, fileLength)
                    }
                    check(written == fileLength) { "File changed during upload" }
                    onProgress(written, fileLength)
                }
                output.write(suffix)
            }
            val status = conn.responseCode
            val response = (if (status in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            check(status in 200..299) { "Upload rejected: HTTP " + status + " (" + response.take(160) + ")" }
            return response
        } finally {
            conn.disconnect()
        }
    }

    /** Update Folio Nooir's existing per-book metadata override after upload.
     * Older firmware without /api/book may reject the call; the uploaded book
     * remains valid and callers must treat this as a nonfatal metadata warning.
     */
    fun updateBookInfo(
        deviceHost: String, destination: String, filename: String,
        title: String, author: String, synopsis: String,
    ): Boolean {
        if (synopsis.isBlank() && title.isBlank() && author.isBlank()) return true
        require(destination.startsWith("/") && !destination.contains("..")) { "Invalid destination" }
        require(!filename.contains("/") && !filename.contains("\\\\")) { "Invalid filename" }
        val host = URI("http://" + deviceHost.trim())
        require(host.host != null && host.port == -1 && host.rawUserInfo == null &&
            host.path.isNullOrEmpty() && host.rawQuery == null) { "Invalid Nooir host" }
        val fullPath = (if (destination == "/") "" else destination.trimEnd('/')) + "/" + filename
        val data = JSONObject().put("path", fullPath).put("title", title.take(192))
            .put("author", author.take(128)).put("synopsis", synopsis.take(4096))
            .toString().toByteArray(StandardCharsets.UTF_8)
        val conn = URI("http://" + host.host + "/api/book").toURL().openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 5000
            conn.readTimeout = 8000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setFixedLengthStreamingMode(data.size)
            conn.outputStream.use { it.write(data) }
            return conn.responseCode in 200..299
        } finally {
            conn.disconnect()
        }
    }

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

    /** Generated manga files share the same bounded HTTP upload/progress path. */
    fun upload(
        file: File, deviceHost: String, destination: String = "/",
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): String {
        require(file.isFile && file.length() > 0) { "Missing export file" }
        require(file.length() <= MAX_SAFE_UPLOAD_BYTES) { "Manga is over 180 MB; export fewer chapters per file." }
        require(file.name.matches(Regex("[A-Za-z0-9_.() -]{1,160}\\.(xtc|xtch|epub|cbz)", RegexOption.IGNORE_CASE))) {
            "Unsupported manga output name"
        }
        return uploadContent(file.name, file.length(), deviceHost, destination, onProgress) {
            file.inputStream()
        }
    }
}
