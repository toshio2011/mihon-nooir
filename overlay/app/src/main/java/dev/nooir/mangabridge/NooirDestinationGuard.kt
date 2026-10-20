package dev.nooir.mangabridge

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import dev.nooir.bridge.ExportFormat

/** Destination collision checks, executed before expensive conversion and again before upload.
 * The reader uses a single-folder listing (no recursive SD scan). Never silently overwrite.
 */
object NooirDestinationGuard {
    fun base(value: String, fallback: String = "Book"): String =
        value.trim().take(70).map { ch ->
            if (ch.code < 128 && (ch.isLetterOrDigit() || ch == '-' || ch == '_')) ch else '_'
        }.joinToString("").trim('_').ifBlank { fallback }

    fun filename(title: String, extension: String): String =
        base(title) + "." + extension.lowercase()

    fun forManga(title: String, format: ExportFormat, device: String): String =
        filename(title, format.resolved(device).extension)

    fun requireOnReader(host: String, folder: String, filename: String) {
        require(filename.isNotBlank() && !filename.contains('/') && !filename.contains('\\')) {
            "Invalid output filename"
        }
        val collision = NooirDeviceFileClient.list(host, folder).firstOrNull {
            it.name.equals(filename, ignoreCase = true)
        }
        check(collision == null) {
            "A file named '$filename' already exists on XTEink in $folder. Rename it before sending; nothing was overwritten."
        }
    }

    fun requireInPhoneFolder(context: Context, tree: Uri, filename: String) {
        val resolver = context.contentResolver
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            tree, DocumentsContract.getTreeDocumentId(tree))
        resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null)?.use { cursor ->
            val col = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            require(col >= 0) { "Cannot inspect names in selected phone folder" }
            while (cursor.moveToNext()) {
                if (filename.equals(cursor.getString(col), ignoreCase = true)) {
                    error("A file named '$filename' already exists in the selected phone folder. Rename it before converting.")
                }
            }
        } ?: error("Cannot inspect destination folder; refusing to risk overwriting a book")
    }
}
