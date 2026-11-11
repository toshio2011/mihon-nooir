package dev.nooir.mangabridge

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.provider.DocumentsContract
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dev.nooir.bridge.NooirUpload
import dev.nooir.bridge.ReflowEpubFormat
import dev.nooir.bridge.XtcFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Convert a selected PDF to an EPUB with real text + embedded pictures where possible. */
class NooirPdfExportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object {
        const val PDF = "pdf_document_uri"
        const val SAVE_URI = "pdf_epub_save_uri"
        const val SAVE_TREE = "pdf_output_folder_uri"
        const val HOST = "pdf_host"
        const val FOLDER = "pdf_folder"
        const val MODE = "pdf_mode"
        const val EPUB_FORMAT = "pdf_epub_format"
        const val OUTPUT_FORMAT = "pdf_output_format"
        const val DEVICE = "pdf_device"
        const val OUTPUT_NAME = "pdf_output_name"
        const val BOOK_TITLE = "pdf_book_title"
        const val BOOK_AUTHOR = "pdf_book_author"
        const val PHASE = "pdf_phase"
        const val PERCENT = "pdf_percent"
        const val ERROR = "pdf_error"
        const val LAST_WORK = "last_pdf_work"
    }
    private val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val notificationId get() = 44000 + (id.hashCode() and 0x1fff)

    private fun notice(title: String, msg: String, percent: Int = -1) =
        NotificationCompat.Builder(applicationContext, NooirTransferWorker.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(title).setContentText(msg).setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(if (percent < 0) 0 else 100, percent.coerceAtLeast(0), percent < 0).build()

    override suspend fun doWork(): Result {
        val pdfUri = inputData.getString(PDF)?.let(Uri::parse)
            ?: return Result.success(NooirDeviceWorkQueue.failed(Data.Builder(), ERROR, "Select a PDF first"))
        val save = inputData.getString(SAVE_URI)?.let(Uri::parse)
        val saveTree = inputData.getString(SAVE_TREE)?.let(Uri::parse)
        require(save == null || saveTree == null) { "Select one PDF save destination" }
        val host = inputData.getString(HOST).orEmpty()
        val folder = inputData.getString(FOLDER) ?: "/Books"
        val epubFormat = if (inputData.getString(EPUB_FORMAT) == "EPUB2")
            ReflowEpubFormat.EPUB2 else ReflowEpubFormat.EPUB3
        val target = inputData.getString(OUTPUT_FORMAT).orEmpty()
        val xtcFormat = when (target) {
            "XTC" -> XtcFormat.XTC
            "XTCH" -> XtcFormat.XTCH
            else -> null
        }
        val device = inputData.getString(DEVICE) ?: "X4"
        val extension = xtcFormat?.extension ?: "epub"
        val mode = if (inputData.getString(MODE) == "ORIGINAL_PAGES")
            NooirPdfToEpub.Mode.ORIGINAL_PAGES else NooirPdfToEpub.Mode.MIXED
        if (save == null && saveTree == null && (host.isBlank() || !folder.startsWith("/") || folder.contains(".."))) {
            return Result.success(NooirDeviceWorkQueue.failed(Data.Builder(), ERROR, "Invalid XTEink address or folder"))
        }
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(
            NooirTransferWorker.CHANNEL_ID, "MangaNooir file transfers", NotificationManager.IMPORTANCE_LOW,
        ))
        setForeground(ForegroundInfo(notificationId,
            notice("MangaNooir · PDF conversion", "Preparing PDF"),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0))
        val inputFile = File(applicationContext.cacheDir, "pdf-source-" + id + ".pdf")
        var name = "Converted PDF"
        try {
            withContext(Dispatchers.IO) {
                if (save == null && saveTree == null) NooirUpload.checkConnected(host)
                val cursor = applicationContext.contentResolver.query(pdfUri,
                    arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                cursor?.use { if (it.moveToFirst()) name = it.getString(0) ?: name }
                val desiredName = inputData.getString(OUTPUT_NAME).orEmpty()
                    .ifBlank { name.substringBeforeLast('.', name) }
                val outputName = NooirDestinationGuard.filename(desiredName, extension)
                if (save == null && saveTree == null) {
                    setProgressAsync(Data.Builder().putString(PHASE, "Checking existing XTEink filenames").build())
                    NooirDestinationGuard.requireOnReader(host, folder, outputName)
                } else if (saveTree != null) {
                    NooirDestinationGuard.requireInPhoneFolder(applicationContext, saveTree, outputName)
                }
                val incoming = applicationContext.contentResolver.openInputStream(pdfUri)
                    ?: error("Cannot open PDF")
                incoming.use { input ->
                    inputFile.outputStream().use { output ->
                        val buffer = ByteArray(32 * 1024)
                        var size = 0L
                        var nextDiskCheck = 0L
                        while (true) {
                            if (isStopped) throw CancellationException("PDF conversion cancelled")
                            val n = input.read(buffer)
                            if (n < 0) break
                            size += n
                            // Source is copied to app-private disk, not kept in
                            // WorkManager Data or as a giant in-memory ByteArray.
                            // Stop only on actual storage exhaustion, not a
                            // preset input size or page count.
                            if (size >= nextDiskCheck) {
                                require(applicationContext.cacheDir.usableSpace >= 96L * 1024 * 1024) {
                                    "Insufficient phone storage for PDF input, output and scratch files"
                                }
                                nextDiskCheck = size + 4L * 1024 * 1024
                            }
                            output.write(buffer, 0, n)
                        }
                    }
                }
                val shortName = name.substringBeforeLast('.', name)
                    .map { c -> if (c.isLetterOrDigit() && c.code < 128 || c in " -_") c else '_' }
                    .joinToString("").take(80).ifBlank { "Converted-PDF" }
                val convertedFile = File(applicationContext.cacheDir, shortName + "-" +
                    id.toString().take(8) + "." + extension)
                try {
                    if (xtcFormat != null) {
                        NooirPdfToXtc.convert(inputFile, convertedFile, xtcFormat, device,
                            inputData.getString(BOOK_TITLE).orEmpty().ifBlank { desiredName },
                            inputData.getString(BOOK_AUTHOR).orEmpty()) { page, total ->
                            if (isStopped) throw CancellationException("PDF conversion cancelled")
                            val percent = page * 100 / total.coerceAtLeast(1)
                            val info = "PDF page " + page + "/" + total + " · " + extension.uppercase()
                            setProgressAsync(Data.Builder().putString(PHASE, info)
                                .putInt(PERCENT, percent).build())
                            manager.notify(notificationId, notice("MangaNooir · PDF to " +
                                extension.uppercase(), info, percent))
                        }
                    } else {
                        NooirPdfToEpub.convert(applicationContext, inputFile, convertedFile, mode, name,
                            inputData.getString(BOOK_TITLE), inputData.getString(BOOK_AUTHOR), epubFormat = epubFormat) { page, total, stage ->
                            if (isStopped) throw CancellationException("PDF conversion cancelled")
                            val percent = page * 100 / total.coerceAtLeast(1)
                            val info = "Page " + page + "/" + total + " · " + stage
                            setProgressAsync(Data.Builder().putString(PHASE, info)
                                .putInt(PERCENT, percent).build())
                            manager.notify(notificationId, notice("MangaNooir · PDF to EPUB", info, percent))
                        }
                    }
                    if (save != null) {
                        // CreateDocument returns a user-chosen URI. Never delete it
                        // automatically: the provider may own pre-existing data.
                        // Detect interruption and incomplete copies before success.
                        setProgressAsync(Data.Builder().putString(PHASE, "Saving converted book to phone").build())
                        applicationContext.contentResolver.openOutputStream(save, "w")?.use { dest ->
                            convertedFile.inputStream().use { source ->
                                PdfSaveCopy.copy(source, dest, convertedFile.length()) { isStopped }
                            }
                        } ?: error("Could not save converted PDF file")
                    } else if (saveTree != null) {
                        val resolver = applicationContext.contentResolver
                        NooirDestinationGuard.requireInPhoneFolder(applicationContext, saveTree, outputName)
                        val parent = DocumentsContract.buildDocumentUriUsingTree(
                            saveTree, DocumentsContract.getTreeDocumentId(saveTree))
                        val out = DocumentsContract.createDocument(resolver, parent,
                            if (xtcFormat == null) "application/epub+zip" else "application/octet-stream",
                            outputName) ?: error("Could not create output in selected folder")
                        try {
                            // SAF providers may auto-rename on a collision; do not
                            // silently export under an unexpected filename.
                            val actual = resolver.query(out,
                                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                                null, null, null)?.use { c ->
                                if (c.moveToFirst()) c.getString(0) else null
                            } ?: error("Cannot verify saved PDF conversion filename")
                            require(actual == outputName) {
                                "Destination changed the filename; refusing to overwrite or rename '$outputName'"
                            }
                            resolver.openOutputStream(out, "w")?.use { dest ->
                                convertedFile.inputStream().use { input ->
                                    PdfSaveCopy.copy(input, dest, convertedFile.length()) { isStopped }
                                }
                            } ?: error("Could not save PDF conversion")
                        } catch (error: Exception) {
                            runCatching { DocumentsContract.deleteDocument(resolver, out) }
                            throw error
                        }
                    } else {
                        manager.notify(notificationId, notice("MangaNooir · PDF to EPUB", "Sending to XTEink"))
                        NooirUpload.upload(convertedFile, host, folder, onProgress = { sent, total ->
                            if (isStopped) throw CancellationException("PDF transfer cancelled")
                            setProgressAsync(Data.Builder().putString(PHASE,
                                if (sent >= total) "Waiting for XTEink confirmation" else "Uploading " + extension.uppercase())
                                .putInt(PERCENT, (sent * 100 / total.coerceAtLeast(1L)).toInt()).build())
                        }, filenameOverride = outputName)
                    }
                } finally { convertedFile.delete() }
            }
            manager.notify(notificationId, NotificationCompat.Builder(
                applicationContext, NooirTransferWorker.CHANNEL_ID,
            ).setSmallIcon(android.R.drawable.stat_sys_upload_done)
                .setContentTitle("MangaNooir · PDF converted")
                .setContentText(if (save == null && saveTree == null) "Confirmed by XTEink" else extension.uppercase() + " saved to phone")
                .setAutoCancel(true).build())
            return Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = e.message.orEmpty().ifBlank { "PDF conversion failed" }
            val oversizedTransfer = save == null && saveTree == null &&
                message.contains("too large for XTEINK Wi-Fi transfer", ignoreCase = true)
            manager.notify(notificationId, NotificationCompat.Builder(
                applicationContext, NooirTransferWorker.CHANNEL_ID,
            ).setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle(if (oversizedTransfer) "MangaNooir · XTEINK transfer too large"
                    else "MangaNooir · PDF conversion failed")
                .setContentText(message.take(100)).setAutoCancel(true).build())
            return Result.success(NooirDeviceWorkQueue.failed(Data.Builder(), ERROR, message))
        } finally { inputFile.delete() }
    }
}
