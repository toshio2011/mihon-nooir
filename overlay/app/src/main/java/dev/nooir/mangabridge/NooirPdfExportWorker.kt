package dev.nooir.mangabridge

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
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

/** Convert a selected PDF to an EPUB with real text + embedded pictures where possible. */
class NooirPdfExportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object {
        const val PDF = "pdf_document_uri"
        const val SAVE_URI = "pdf_epub_save_uri"
        const val HOST = "pdf_host"
        const val FOLDER = "pdf_folder"
        const val MODE = "pdf_mode"
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
            ?: return Result.failure(Data.Builder().putString(ERROR, "Select a PDF first").build())
        val save = inputData.getString(SAVE_URI)?.let(Uri::parse)
        val host = inputData.getString(HOST).orEmpty()
        val folder = inputData.getString(FOLDER) ?: "/Books"
        val mode = if (inputData.getString(MODE) == "ORIGINAL_PAGES")
            NooirPdfToEpub.Mode.ORIGINAL_PAGES else NooirPdfToEpub.Mode.MIXED
        if (save == null && (host.isBlank() || !folder.startsWith("/") || folder.contains(".."))) {
            return Result.failure(Data.Builder().putString(ERROR, "Invalid XTEink address or folder").build())
        }
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(
            NooirTransferWorker.CHANNEL_ID, "MangaNooir file transfers", NotificationManager.IMPORTANCE_LOW,
        ))
        setForeground(ForegroundInfo(notificationId,
            notice("MangaNooir · PDF to EPUB", "Preparing PDF"),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0))
        val inputFile = File(applicationContext.cacheDir, "pdf-source-" + id + ".pdf")
        var name = "Converted PDF"
        try {
            withContext(Dispatchers.IO) {
                if (save == null) NooirUpload.checkConnected(host)
                val cursor = applicationContext.contentResolver.query(pdfUri,
                    arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                cursor?.use { if (it.moveToFirst()) name = it.getString(0) ?: name }
                val incoming = applicationContext.contentResolver.openInputStream(pdfUri)
                    ?: error("Cannot open PDF")
                incoming.use { input ->
                    inputFile.outputStream().use { output ->
                        val buffer = ByteArray(32 * 1024)
                        var size = 0L
                        while (true) {
                            if (isStopped) throw CancellationException("PDF conversion cancelled")
                            val n = input.read(buffer)
                            if (n < 0) break
                            size += n
                            require(size <= 100L * 1024 * 1024) {
                                "PDF is larger than 100 MiB experimental limit"
                            }
                            output.write(buffer, 0, n)
                        }
                    }
                }
                val shortName = name.substringBeforeLast('.', name)
                    .map { c -> if (c.isLetterOrDigit() && c.code < 128 || c in " -_") c else '_' }
                    .joinToString("").take(80).ifBlank { "Converted-PDF" }
                val epubFile = File(applicationContext.cacheDir, shortName + "-" +
                    id.toString().take(8) + ".epub")
                try {
                    NooirPdfToEpub.convert(applicationContext, inputFile, epubFile, mode, name) { page, total, stage ->
                        if (isStopped) throw CancellationException("PDF conversion cancelled")
                        val percent = page * 100 / total.coerceAtLeast(1)
                        val info = "Page " + page + "/" + total + " · " + stage
                        setProgressAsync(Data.Builder().putString(PHASE, info)
                            .putInt(PERCENT, percent).build())
                        manager.notify(notificationId, notice("MangaNooir · PDF to EPUB", info, percent))
                    }
                    if (save != null) {
                        applicationContext.contentResolver.openOutputStream(save, "w")?.use { dest ->
                            epubFile.inputStream().use { it.copyTo(dest, 32 * 1024) }
                        } ?: error("Could not save EPUB file")
                    } else {
                        manager.notify(notificationId, notice("MangaNooir · PDF to EPUB", "Sending to XTEink"))
                        NooirUpload.upload(epubFile, host, folder) { sent, total ->
                            if (isStopped) throw CancellationException("PDF transfer cancelled")
                            setProgressAsync(Data.Builder().putString(PHASE,
                                if (sent >= total) "Waiting for XTEink confirmation" else "Uploading EPUB")
                                .putInt(PERCENT, (sent * 100 / total.coerceAtLeast(1L)).toInt()).build())
                        }
                    }
                } finally { epubFile.delete() }
            }
            manager.notify(notificationId, NotificationCompat.Builder(
                applicationContext, NooirTransferWorker.CHANNEL_ID,
            ).setSmallIcon(android.R.drawable.stat_sys_upload_done)
                .setContentTitle("MangaNooir · PDF converted")
                .setContentText(if (save == null) "Confirmed by XTEink" else "EPUB saved to phone")
                .setAutoCancel(true).build())
            return Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = e.message.orEmpty().ifBlank { "PDF conversion failed" }
            manager.notify(notificationId, NotificationCompat.Builder(
                applicationContext, NooirTransferWorker.CHANNEL_ID,
            ).setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("MangaNooir · PDF conversion failed")
                .setContentText(message.take(100)).setAutoCancel(true).build())
            return Result.failure(Data.Builder().putString(ERROR, message).build())
        } finally { inputFile.delete() }
    }
}
