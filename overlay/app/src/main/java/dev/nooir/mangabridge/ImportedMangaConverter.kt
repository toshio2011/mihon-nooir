package dev.nooir.mangabridge

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.content.Intent
import dev.nooir.bridge.ArchivePage
import dev.nooir.bridge.BookArchiveWriter
import dev.nooir.bridge.BookChapter
import dev.nooir.bridge.ExportFormat
import dev.nooir.bridge.ExtendedMetadata
import dev.nooir.bridge.NooirUpload
import dev.nooir.bridge.XtcCodec
import dev.nooir.bridge.XtcFormat
import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

/** Any SAF-selected file can be sent unchanged.
 * Only known manga image inputs are eligible for image-based conversion.
 */
object ImportedMangaConverter {
    enum class Kind { COMIC_ARCHIVE, IMAGE, DOCUMENT }

    data class SelectedFile(val name: String, val bytes: Long?, val kind: Kind) {
        val isManga get() = kind == Kind.COMIC_ARCHIVE || kind == Kind.IMAGE
    }
    data class Result(val pages: Int, val format: ExportFormat)

    fun inspect(context: Context, uri: Uri): SelectedFile {
        val resolver = context.contentResolver
        var name = "Untitled"
        var size: Long? = null
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val ni = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val si = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (ni >= 0) name = cursor.getString(ni) ?: "Untitled"
                if (si >= 0 && !cursor.isNull(si)) size = cursor.getLong(si)
            }
        }
        val ext = name.substringAfterLast('.', "").lowercase()
        val mime = resolver.getType(uri).orEmpty().lowercase()
        val kind = when {
            ext in setOf("cbz", "zip") -> if (isComicZip(context, uri)) Kind.COMIC_ARCHIVE else Kind.DOCUMENT
            ext in setOf("jpg", "jpeg", "png", "webp", "bmp", "gif") ||
                mime in setOf("image/jpeg", "image/png", "image/webp", "image/bmp", "image/gif") -> Kind.IMAGE
            else -> Kind.DOCUMENT
        }
        return SelectedFile(name, size, kind)
    }

    private fun isComicZip(context: Context, uri: Uri): Boolean {
        // Inspect directory entries without decompressing image payloads.
        // Files larger than this threshold can still be transferred unchanged.
        val temp = File.createTempFile("manganooir-sniff-", ".zip", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
            } ?: return false
            return runCatching {
                ZipFile(temp).use { zip ->
                    zip.entries().asSequence().take(5000).any { entry ->
                        !entry.isDirectory && entry.name.substringAfterLast('.', "").lowercase() in
                            setOf("jpg", "jpeg", "png", "webp", "bmp", "gif")
                    }
                }
            }.getOrDefault(false)
        } finally {
            temp.delete()
        }
    }

    suspend fun convert(
        context: Context,
        input: Uri,
        picked: SelectedFile,
        requestedFormat: ExportFormat,
        device: String,
        saveDestination: Uri? = null,
        sendHost: String? = null,
        sendFolder: String = "/Books",
    ): Result = withContext(Dispatchers.IO) {
        require(picked.isManga) { "This file is not a supported manga image or CBZ archive" }
        if (picked.bytes != null) require(picked.bytes <= 512L * 1024 * 1024) {
            "Manga conversion supports up to 512 MB per input. You can send the original file unchanged."
        }
        require(saveDestination != null || sendHost != null) { "Select a save location or a receiver" }
        val actual = requestedFormat.resolved(device)
        val (width, height) = if (device == "X3") (528 to 792) else (480 to 800)
        val baseTitle = picked.name.substringBeforeLast('.', picked.name).ifBlank { "Manga" }
        val metadata = loadMetadata(context, input, picked, baseTitle)
        val source = AndroidPageSource(context)
        val tempFolder = File(context.cacheDir, "manganooir-import-" + UUID.randomUUID()).apply {
            check(mkdirs()) { "Cannot create conversion cache" }
        }
        val filename = baseTitle.take(70).map { c ->
            if (c.isLetterOrDigit() && c.code < 128 || c in listOf('-', '_')) c else '_'
        }.joinToString("").ifBlank { "Manga" }
        val output = File(tempFolder, filename + "." + actual.extension)
        try {
            val count: Int
            output.outputStream().use { target ->
                if (actual == ExportFormat.XTC || actual == ExportFormat.XTCH) {
                    val depth = if (actual == ExportFormat.XTC) XtcFormat.XTC else XtcFormat.XTCH
                    count = XtcCodec.write(
                        depth,
                        source.pages(listOf(input), width, height, depth.depth).onEach {
                            // Avoid processing a malicious archive with tens of thousands of pages.
                        },
                        target, context.cacheDir, metadata = metadata.xtc(),
                        direction = metadata.direction,
                        chapterResolver = { pages ->
                            require(pages in 1..10000) { "Unsupported page count" }
                            listOf(BookChapter(baseTitle, 0, pages - 1))
                        },
                    ).pageCount
                } else {
                    val archivePages = source.pages(listOf(input), width, height, 2).map { page ->
                        ArchivePage(baseTitle, png(page), page.width, page.height)
                    }
                    count = BookArchiveWriter.write(actual, archivePages, target, metadata)
                }
            }
            if (saveDestination != null) {
                context.contentResolver.openOutputStream(saveDestination, "w")?.use { target ->
                    output.inputStream().use { it.copyTo(target, 64 * 1024) }
                } ?: error("Cannot write output file")
            } else {
                NooirUpload.checkConnected(requireNotNull(sendHost))
                NooirUpload.upload(output, sendHost, sendFolder)
            }
            Result(count, actual)
        } finally {
            output.delete()
            tempFolder.delete()
        }
    }

    fun shareOriginal(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = context.contentResolver.getType(uri) ?: "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Send file to another app or device"))
    }

    private fun loadMetadata(context: Context, uri: Uri, file: SelectedFile, fallbackTitle: String): ExtendedMetadata {
        if (file.kind != Kind.COMIC_ARCHIVE) return ExtendedMetadata(title = fallbackTitle)
        val temp = File.createTempFile("manganooir-comicinfo-", ".cbz", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
            } ?: return ExtendedMetadata(title = fallbackTitle)
            return ZipFile(temp).use { zip ->
                val entry = zip.entries().asSequence().firstOrNull {
                    it.name.equals("ComicInfo.xml", true) || it.name.endsWith("/ComicInfo.xml", true)
                } ?: return@use ExtendedMetadata(title = fallbackTitle)
                if (entry.size < 0 || entry.size > 512 * 1024) return@use ExtendedMetadata(title = fallbackTitle)
                val factory = DocumentBuilderFactory.newInstance().apply {
                    setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                    setFeature("http://xml.org/sax/features/external-general-entities", false)
                    setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                    isXIncludeAware = false
                    isExpandEntityReferences = false
                }
                val doc = zip.getInputStream(entry).use { factory.newDocumentBuilder().parse(it) }
                fun field(tag: String) = doc.getElementsByTagName(tag).item(0)?.textContent?.trim().orEmpty()
                ExtendedMetadata(
                    title = field("Title").ifBlank { field("Series").ifBlank { fallbackTitle } },
                    author = field("Writer"), artist = field("Penciller"),
                    description = field("Summary"),
                    genres = field("Genre").split(',', ';').map(String::trim).filter(String::isNotBlank),
                    publisher = field("Publisher"), language = field("LanguageISO").ifBlank { "und" },
                    series = field("Series").ifBlank { fallbackTitle },
                    sourceUrl = field("Web"),
                )
            }
        } catch (_: Exception) {
            return ExtendedMetadata(title = fallbackTitle)
        } finally {
            temp.delete()
        }
    }

    private fun png(page: dev.nooir.bridge.QuantizedPage): ByteArray {
        val pixels = IntArray(page.levels.size) { index ->
            val level = page.levels[index].toInt() and 255
            val v = if (page.depth == 1) level * 255 else level * 85
            Color.rgb(v, v, v)
        }
        val bitmap = Bitmap.createBitmap(pixels, page.width, page.height, Bitmap.Config.ARGB_8888)
        return try {
            ByteArrayOutputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                it.toByteArray()
            }
        } finally { bitmap.recycle() }
    }
}
