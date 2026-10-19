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
import dev.nooir.bridge.ExportFormat
import dev.nooir.bridge.NooirUpload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * WorkManager queue: leaving the screen or switching apps won't cancel uploads.
 * A batch is sequential because the reader's ESP32 accepts one upload at once.
 * SAF permissions are persisted by the picker before this worker is enqueued.
 */
class NooirTransferWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object {
        const val KEY_URIS = "uris"
        const val KEY_HOST = "host"
        const val KEY_FOLDER = "folder"
        const val KEY_CONVERT = "convert"
        const val KEY_FORMAT = "format"
        const val KEY_DEVICE = "device"
        const val KEY_OPTIONS = "options"
        const val KEY_PHASE = "phase"
        const val KEY_PERCENT = "percent"
        const val KEY_SENT = "sent"
        const val KEY_TOTAL_BYTES = "total_bytes"
        const val KEY_FILE = "file"
        const val KEY_INDEX = "index"
        const val KEY_COUNT = "count"
        const val KEY_ERROR = "error"
        const val CHANNEL_ID = "manganooir_transfer"
    }

    private val notifications =
        applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val notificationId get() = 24000 + (id.hashCode() and 0x3fff)
    private var lastPercent = -1
    private var lastPhase = ""
    private var lastIndex = -1

    private fun notification(message: String, percent: Int?): android.app.Notification {
        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("MangaNooir · file transfer")
            .setContentText(message)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        if (percent != null) builder.setProgress(100, percent.coerceIn(0, 100), false)
        else builder.setProgress(0, 0, true)
        return builder.build()
    }

    private fun emit(
        phase: String, index: Int, count: Int, file: String,
        sent: Long = 0L, total: Long = 0L, percent: Int = -1,
    ) {
        setProgressAsync(
            Data.Builder()
                .putString(KEY_PHASE, phase)
                .putString(KEY_FILE, file)
                .putInt(KEY_INDEX, index)
                .putInt(KEY_COUNT, count)
                .putLong(KEY_SENT, sent)
                .putLong(KEY_TOTAL_BYTES, total)
                .putInt(KEY_PERCENT, percent)
                .build()
        )
        if (lastPercent != percent || lastPhase != phase || lastIndex != index) {
            lastPercent = percent
            lastPhase = phase
            lastIndex = index
            notifications.notify(notificationId, notification(
                "File " + index + "/" + count + " · " + phase + " · " + file,
                percent.takeIf { it >= 0 },
            ))
        }
    }

    override suspend fun doWork(): Result {
        val files = inputData.getStringArray(KEY_URIS).orEmpty()
        val host = inputData.getString(KEY_HOST).orEmpty()
        val folder = inputData.getString(KEY_FOLDER).orEmpty()
        val convert = inputData.getBoolean(KEY_CONVERT, false)
        val format = runCatching { ExportFormat.valueOf(inputData.getString(KEY_FORMAT) ?: "AUTO") }
            .getOrDefault(ExportFormat.AUTO)
        val device = inputData.getString(KEY_DEVICE) ?: "X4"
        val options = MangaConversionOptions.fromToken(inputData.getString(KEY_OPTIONS))
        if (files.isEmpty() || host.isBlank() || !folder.startsWith('/') || folder.contains("..")) {
            return Result.failure(Data.Builder().putString(KEY_ERROR, "Invalid files, host or folder").build())
        }
        if (Build.VERSION.SDK_INT >= 26) {
            notifications.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "MangaNooir file transfers", NotificationManager.IMPORTANCE_LOW)
            )
        }
        setForeground(ForegroundInfo(
            notificationId,
            notification("Preparing " + files.size + " file(s)…", null),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        ))
        var completed = 0
        try {
            withContext(Dispatchers.IO) {
                NooirUpload.checkConnected(host)
                for ((offset, raw) in files.withIndex()) {
                    currentCoroutineContext().ensureActive()
                    if (isStopped) throw CancellationException("Transfer cancelled")
                    val index = offset + 1
                    val uri = Uri.parse(raw)
                    emit("Inspecting", index, files.size, "file " + index)
                    val selected = ImportedMangaConverter.inspect(applicationContext, uri)
                    val title = selected.name.take(70)
                    if (convert && selected.isManga) {
                        emit("Converting", index, files.size, title)
                        ImportedMangaConverter.convert(
                            applicationContext, uri, selected, format, device,
                            sendHost = host, sendFolder = folder,
                            onStage = { step -> emit(step, index, files.size, title) },
                            options = options,
                            onUploadProgress = { sent, total ->
                                if (isStopped) throw CancellationException("Transfer cancelled")
                                val percent = (sent * 100L / total.coerceAtLeast(1L)).toInt()
                                emit(if (sent >= total) "Waiting for XTEink confirmation" else "Uploading",
                                    index, files.size, title, sent, total, percent)
                            },
                        )
                    } else {
                        emit("Uploading", index, files.size, title)
                        NooirUpload.uploadDocument(applicationContext, uri, host, folder) { sent, total ->
                            if (isStopped) throw CancellationException("Transfer cancelled")
                            val percent = (sent * 100L / total.coerceAtLeast(1L)).toInt()
                            emit(if (sent >= total) "Waiting for XTEink confirmation" else "Uploading",
                                index, files.size, title, sent, total, percent)
                        }
                    }
                    completed = index
                    emit("Confirmed by XTEink", index, files.size, title, percent = 100)
                }
            }
            notifications.notify(notificationId,
                NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_upload_done)
                    .setContentTitle("MangaNooir · transfer complete")
                    .setContentText(completed.toString() + " file(s) confirmed by XTEink")
                    .setOnlyAlertOnce(true).setAutoCancel(true).build()
            )
            return Result.success(Data.Builder().putInt(KEY_COUNT, completed).build())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = e.message ?: "Unknown transfer error"
            notifications.notify(notificationId,
                NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_notify_error)
                    .setContentTitle("MangaNooir · transfer failed")
                    .setContentText(completed.toString() + "/" + files.size + " complete · " + msg.take(100))
                    .setAutoCancel(true).build()
            )
            return Result.failure(Data.Builder().putString(KEY_ERROR, msg).putInt(KEY_COUNT, completed).build())
        }
    }
}
