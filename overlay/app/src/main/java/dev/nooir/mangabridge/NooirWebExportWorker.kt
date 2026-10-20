package dev.nooir.mangabridge

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
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

/** A URL is rendered to an offline EPUB in background; network upload is optional. */
class NooirWebExportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object {
        const val URL = "web_url"
        const val SAVE_URI = "web_save_uri"
        const val HOST = "web_host"
        const val FOLDER = "web_folder"
        const val PHASE = "web_phase"
        const val ERROR = "web_error"
        const val PERCENT = "web_percent"
        const val LAST_WORK = "last_web_work"
    }

    private val notices = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val idForNotification get() = 32000 + (id.hashCode() and 0x3fff)
    private fun note(title: String, content: String) = NotificationCompat.Builder(
        applicationContext, NooirTransferWorker.CHANNEL_ID,
    ).setContentTitle(title).setContentText(content)
        .setSmallIcon(android.R.drawable.stat_sys_upload)
        .setOnlyAlertOnce(true).setPriority(NotificationCompat.PRIORITY_LOW)
        .setProgress(0, 0, true).build()

    override suspend fun doWork(): Result {
        val link = inputData.getString(URL).orEmpty()
        val destination = inputData.getString(SAVE_URI)?.let(Uri::parse)
        val host = inputData.getString(HOST).orEmpty()
        val folder = inputData.getString(FOLDER) ?: "/Books"
        if (link.isBlank() || (destination == null && (host.isBlank() ||
            !folder.startsWith("/") || folder.contains("..")))) {
            return Result.success(NooirDeviceWorkQueue.failed(Data.Builder(), ERROR, "Missing URL or destination"))
        }
        if (Build.VERSION.SDK_INT >= 26) notices.createNotificationChannel(NotificationChannel(
            NooirTransferWorker.CHANNEL_ID, "MangaNooir file transfers", NotificationManager.IMPORTANCE_LOW,
        ))
        val notification = idForNotification
        setForeground(ForegroundInfo(notification,
            note("MangaNooir · Web to E-reader", "Preparing article EPUB"),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0))
        val temp = File(applicationContext.cacheDir, "web-to-reader-" + id + ".epub")
        try {
            withContext(Dispatchers.IO) {
                if (destination == null) NooirUpload.checkConnected(host)
                NooirWebArticle.convert(link, temp) { stage ->
                    if (isStopped) throw CancellationException("Article conversion cancelled")
                    setProgressAsync(Data.Builder().putString(PHASE, stage).build())
                    notices.notify(notification, note("MangaNooir · Web to E-reader", stage))
                }
                if (destination != null) {
                    val output = applicationContext.contentResolver.openOutputStream(destination, "w")
                        ?: error("Cannot save EPUB in selected location")
                    output.use { destinationStream ->
                        temp.inputStream().use { input -> input.copyTo(destinationStream, 32 * 1024) }
                    }
                } else {
                    setProgressAsync(Data.Builder().putString(PHASE, "Sending EPUB to XTEink").build())
                    NooirUpload.upload(temp, host, folder) { sent, total ->
                        if (isStopped) throw CancellationException("Article transfer cancelled")
                        setProgressAsync(Data.Builder().putString(PHASE,
                            if (sent >= total) "Waiting for XTEink confirmation" else "Uploading EPUB")
                            .putInt(PERCENT, (sent * 100 / total.coerceAtLeast(1L)).toInt()).build())
                    }
                }
            }
            notices.notify(notification, NotificationCompat.Builder(
                applicationContext, NooirTransferWorker.CHANNEL_ID,
            ).setContentTitle("MangaNooir · Article saved")
                .setContentText(if (destination == null) "Confirmed by XTEink" else "EPUB saved to phone")
                .setSmallIcon(android.R.drawable.stat_sys_upload_done)
                .setAutoCancel(true).build())
            return Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = e.message.orEmpty().ifBlank { "Could not export article" }
            notices.notify(notification, NotificationCompat.Builder(
                applicationContext, NooirTransferWorker.CHANNEL_ID,
            ).setContentTitle("MangaNooir · Web export failed")
                .setContentText(message.take(100))
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setAutoCancel(true).build())
            return Result.success(NooirDeviceWorkQueue.failed(Data.Builder(), ERROR, message))
        } finally { temp.delete() }
    }
}
