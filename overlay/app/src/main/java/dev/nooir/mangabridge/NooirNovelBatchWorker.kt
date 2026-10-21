package dev.nooir.mangabridge

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dev.nooir.bridge.NooirUpload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import org.json.JSONArray

/** Sequential multi-story EPUB export: never overload the reader with parallel uploads. */
class NooirNovelBatchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object {
        const val URLS = "novel_urls"
        const val NAMES = "novel_output_names"
        const val COMBINE_PAGES = "combine_webpages"
        const val BOOK_TITLE = "combined_book_title"
        const val BOOK_AUTHOR = "combined_book_author"
        const val BOOK_FILENAME = "combined_book_filename"
        const val URL_MANIFEST = "combined_urls_manifest"
        const val OUTPUT_TREE = "novel_output_tree"
        const val HOST = "novel_host"
        const val FOLDER = "novel_folder"
        const val FIRST = "novel_first"
        const val LAST = "novel_last"
        const val STAGE = "novel_stage"
        const val DONE = "novel_done"
        const val ERRORS = "novel_errors"
        const val LAST_WORK = "last_novel_batch"
    }
    private val notices = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val notificationId get() = 54000 + (id.hashCode() and 0x1fff)

    private fun notice(message: String): android.app.Notification =
        NotificationCompat.Builder(applicationContext, NooirTransferWorker.CHANNEL_ID)
            .setContentTitle("MangaNooir · web novels")
            .setContentText(message.take(110)).setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOnlyAlertOnce(true).setProgress(0, 0, true).build()

    override suspend fun doWork(): Result {
        val combinePages = inputData.getBoolean(COMBINE_PAGES, false)
        val manifestName = inputData.getString(URL_MANIFEST).orEmpty()
        // Keep WorkManager Data small: only open our app-private file.
        val manifestFile = if (combinePages && manifestName.isNotBlank() &&
            Regex("^nooir-webpages-[a-f0-9-]{36}\\.json$").matches(manifestName))
            File(applicationContext.filesDir, manifestName) else null
        val entries = if (combinePages) try {
            requireNotNull(manifestFile) { "Missing chapter-list manifest" }
            require(manifestFile.isFile && manifestFile.length() in 1..(128 * 1024)) {
                "Chapter-list manifest is missing or too large"
            }
            val json = JSONArray(manifestFile.readText(Charsets.UTF_8))
            (0 until json.length()).map { index ->
                val item = json.getJSONObject(index)
                item.getString("url").trim() to item.optString("title").trim()
            }
        } catch (e: Exception) {
            manifestFile?.delete()
            return Result.success(NooirDeviceWorkQueue.failed(Data.Builder(), ERRORS,
                "Could not read combined chapter URLs: " + (e.message ?: "Invalid manifest")))
        } else emptyList()
        val urls = if (combinePages) entries.map { it.first } else
            inputData.getStringArray(URLS)?.map { it.trim() }
                ?.filter { it.isNotBlank() }?.distinct().orEmpty()
        val customNames = if (combinePages) entries.map { it.second }.toTypedArray() else
            inputData.getStringArray(NAMES).orEmpty()
        val destination = inputData.getString(OUTPUT_TREE)?.let(Uri::parse)
        val host = inputData.getString(HOST).orEmpty()
        val folder = inputData.getString(FOLDER) ?: "/Books"
        val first = inputData.getInt(FIRST, 1)
        val last = inputData.getInt(LAST, Int.MAX_VALUE)
        if (urls.isEmpty() || (!combinePages && urls.size > 10) ||
            urls.any { it.length > 600 || !it.startsWith("https://", true) } ||
            first < 1 || last < first ||
            (destination == null && (host.isBlank() || !folder.startsWith("/") || folder.contains("..")))) {
            manifestFile?.delete()
            return Result.success(NooirDeviceWorkQueue.failed(Data.Builder(), ERRORS,
                "Invalid URLs, chapter range or destination"))
        }
        if (Build.VERSION.SDK_INT >= 26) notices.createNotificationChannel(
            NotificationChannel(NooirTransferWorker.CHANNEL_ID,
                "MangaNooir file transfers", NotificationManager.IMPORTANCE_LOW))
        setForeground(ForegroundInfo(notificationId, notice("Preparing " + urls.size + " stories"),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0))
        var done = 0
        val errors = mutableListOf<String>()
        val reservedNames = mutableSetOf<String>()
        try {
            withContext(Dispatchers.IO) {
                if (destination == null) NooirUpload.checkConnected(host)
                if (combinePages) {
                    val temp = File(applicationContext.cacheDir, "nooir-combined-" + id + ".epub")
                    fun progress(message: String) {
                        if (isStopped) throw CancellationException("Combined conversion cancelled")
                        val stage = "Combined EPUB · " + message
                        setProgressAsync(Data.Builder().putString(STAGE, stage)
                            .putInt(DONE, done).build())
                        notices.notify(notificationId, notice(stage))
                    }
                    try {
                        // Inspect the first page and target filename before downloading
                        // all the remaining chapters, preserving a safe rename path.
                        progress("Inspecting first webpage and destination")
                        val firstPage = NooirWebArticle.load(urls.first())
                        val firstMeta = NooirWebArticle.metadata(firstPage)
                        val title = inputData.getString(BOOK_TITLE).orEmpty().trim()
                            .ifBlank { firstMeta.title }
                        val author = inputData.getString(BOOK_AUTHOR).orEmpty().trim()
                        val filename = NooirDestinationGuard.filename(
                            inputData.getString(BOOK_FILENAME).orEmpty().trim()
                                .ifBlank { title }, "epub")
                        if (destination != null) NooirDestinationGuard.requireInPhoneFolder(
                            applicationContext, destination, filename)
                        else NooirDestinationGuard.requireOnReader(host, folder, filename)
                        val converted = NooirWebPageBook.convert(
                            urls = urls, output = temp, bookTitle = title,
                            bookAuthor = author, chapterTitles = customNames.toList(),
                            firstPage = firstPage,
                        ) { index, total, message ->
                            progress("Chapter " + index + "/" + total + " · " + message)
                        }
                        if (destination != null) {
                            // Never expose a partly converted book to Android SAF.
                            val resolver = applicationContext.contentResolver
                            NooirDestinationGuard.requireInPhoneFolder(
                                applicationContext, destination, filename)
                            val parent = DocumentsContract.buildDocumentUriUsingTree(
                                destination, DocumentsContract.getTreeDocumentId(destination))
                            val out = DocumentsContract.createDocument(
                                resolver, parent, "application/epub+zip", filename)
                                ?: error("Cannot create combined EPUB in selected folder")
                            try {
                                resolver.openOutputStream(out, "w")?.use { stream ->
                                    temp.inputStream().use { it.copyTo(stream, 32768) }
                                } ?: error("Cannot save combined EPUB")
                            } catch (e: Exception) {
                                runCatching { DocumentsContract.deleteDocument(resolver, out) }
                                throw e
                            }
                        } else {
                            progress("Uploading combined EPUB to XTEink")
                            NooirUpload.upload(temp, host, folder, onProgress = { sent, size ->
                                if (isStopped) throw CancellationException("Upload cancelled")
                                progress(if (sent >= size) "Waiting for reader confirmation"
                                    else "Uploading " + sent/1024 + "/" + size/1024 + " KiB")
                            }, filenameOverride = filename)
                        }
                        done = 1
                        progress("Saved one EPUB with " + converted.pages + " chapters")
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) {
                        errors += "Combined EPUB: " + (e.message ?: "Failed").take(300)
                        progress("Failed; no incomplete EPUB was saved")
                    } finally {
                        temp.delete()
                    }
                } else {
                urls.forEachIndexed { index, url ->
                    if (isStopped) throw CancellationException("Batch cancelled")
                    val temp = File(applicationContext.cacheDir, "nooir-novel-" + id + "-" + index + ".epub")
                    val label = "Story " + (index + 1) + "/" + urls.size
                    fun progress(message: String) {
                        if (isStopped) throw CancellationException("Batch cancelled")
                        val stage = label + " · " + message
                        setProgressAsync(Data.Builder().putString(STAGE, stage).putInt(DONE, done).build())
                        notices.notify(notificationId, notice(stage))
                    }
                    try {
                        // Inspect title and destination BEFORE fetching chapters or building an EPUB.
                        progress("Inspecting story title and destination")
                        val page = NooirWebArticle.load(url)
                        val metadata = NooirWebArticle.metadata(page)
                        val customBase = customNames.getOrNull(index)?.trim().orEmpty()
                        val suffix = url.hashCode().toUInt().toString(16)
                        val requestedBase = customBase.ifBlank {
                            NooirDestinationGuard.base(metadata.title.take(55), "Novel") + "_" + suffix
                        }
                        val filename = NooirDestinationGuard.filename(requestedBase, "epub")
                        check(reservedNames.add(filename.lowercase())) {
                            "Two selected stories use '$filename'. Rename one story before starting."
                        }
                        if (destination != null) NooirDestinationGuard.requireInPhoneFolder(
                            applicationContext, destination, filename)
                        else NooirDestinationGuard.requireOnReader(host, folder, filename)
                        val result = NooirWebNovel.convert(url, temp, first, last, page) { chapter, total, message ->
                            progress(message + if (total > 0) " (" + chapter + "/" + total + ")" else "")
                        }
                        if (destination != null) {
                            val resolver = applicationContext.contentResolver
                            val parent = DocumentsContract.buildDocumentUriUsingTree(
                                destination, DocumentsContract.getTreeDocumentId(destination))
                            val out = DocumentsContract.createDocument(resolver, parent,
                                "application/epub+zip", filename)
                                ?: error("Cannot create EPUB in selected folder")
                            try {
                                resolver.openOutputStream(out, "w")?.use { stream ->
                                    temp.inputStream().use { it.copyTo(stream, 32768) }
                                } ?: error("Cannot save EPUB")
                            } catch (e: Exception) {
                                runCatching { DocumentsContract.deleteDocument(resolver, out) }
                                throw e
                            }
                        } else {
                            progress("Uploading to XTEink")
                            NooirUpload.upload(temp, host, folder, onProgress = { sent, size ->
                                if (isStopped) throw CancellationException("Upload cancelled")
                                progress(if (sent >= size) "Waiting for reader confirmation"
                                    else "Uploading " + sent/1024 + "/" + size/1024 + " KiB")
                            }, filenameOverride = filename)
                        }
                        done++
                        progress("Saved " + result.exportedChapters + " chapter(s)")
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) {
                        errors += label + ": " + (e.message ?: "Failed").take(130)
                        progress("Failed; continuing to next story")
                    } finally { temp.delete() }
                }
                } // separate-story mode
            }
            val totalBooks = if (combinePages) 1 else urls.size
            val message = done.toString() + "/" + totalBooks +
                (if (combinePages) " combined EPUB saved" else " stories saved") +
                if (errors.isEmpty()) "" else "; " + errors.size + " failed"
            notices.notify(notificationId, NotificationCompat.Builder(applicationContext,
                NooirTransferWorker.CHANNEL_ID)
                .setContentTitle("MangaNooir · novel batch finished")
                .setContentText(message).setSmallIcon(android.R.drawable.stat_sys_upload_done)
                .setAutoCancel(true).build())
            val output = Data.Builder().putInt(DONE, done)
                .putString(ERRORS, errors.joinToString("\n").take(4000)).build()
            return if (errors.isEmpty()) Result.success(output) else Result.success(
                NooirDeviceWorkQueue.failed(Data.Builder().putInt(DONE, done), ERRORS,
                    errors.joinToString("\n").take(4000)))
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            return Result.success(NooirDeviceWorkQueue.failed(Data.Builder().putInt(DONE, done),
                ERRORS, e.message ?: "Novel batch failed"))
        } finally {
            manifestFile?.delete()
        }
    }
}
