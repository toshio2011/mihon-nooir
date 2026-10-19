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
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.nooir.bridge.ExportFormat
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.data.cache.CoverCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.app.di.AppGraph
import mihon.core.metro.metroGraph
import tachiyomi.domain.manga.interactor.GetMangaWithChapters
import tachiyomi.domain.source.service.SourceManager
import java.io.File
import java.util.UUID

/** Manga-details export, independent of the lifetime of the manga screen. */
class NooirMangaExportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object {
        const val PREF_WORK_ID = "last_manga_work"
        const val KEY_STAGE = "stage"
        const val KEY_CHAPTER = "chapter"
        const val KEY_CHAPTERS = "chapters"
        const val KEY_PERCENT = "percent"
        const val KEY_SENT = "sent"
        const val KEY_TOTAL = "total"
        const val KEY_ERROR = "error"
        private const val QUEUE_NAME = "manganooir_manga_exports"

        fun enqueue(context: Context, mangaId: Long, chapterIds: List<Long>,
            destination: Uri?, host: String?, folder: String, format: ExportFormat,
            device: String, quick: Boolean, options: MangaConversionOptions): UUID {
            require(chapterIds.isNotEmpty()) { "Select at least one chapter" }
            require(destination != null || !host.isNullOrBlank()) { "Missing destination" }
            // Avoid WorkManager's 10KB Data limit with a private, durable chapter ID manifest.
            val dir = File(context.filesDir, "manganooir_export_jobs").apply { mkdirs() }
            val manifest = File(dir, UUID.randomUUID().toString() + ".txt")
            manifest.writeText(chapterIds.distinct().joinToString("\n"))
            try {
                val request = OneTimeWorkRequestBuilder<NooirMangaExportWorker>().setInputData(workDataOf(
                    "manga_id" to mangaId, "ids_file" to manifest.name,
                    "output_uri" to (destination?.toString() ?: ""),
                    "host" to host.orEmpty(), "folder" to folder,
                    "format" to format.name, "device" to device,
                    "quick" to quick, "options" to options.token()
                )).build()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    QUEUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
                context.getSharedPreferences("manganooir_export", Context.MODE_PRIVATE)
                    .edit().putString(PREF_WORK_ID, request.id.toString()).apply()
                return request.id
            } catch (e: Exception) {
                manifest.delete()
                throw e
            }
        }
    }

    private val graph: AppGraph = applicationContext.metroGraph()
    @Inject private lateinit var getMangaWithChapters: GetMangaWithChapters
    @Inject private lateinit var sourceManager: SourceManager
    @Inject private lateinit var downloadProvider: DownloadProvider
    @Inject private lateinit var coverCache: CoverCache
    init { graph.inject(this) }

    private val manager =
        applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val notificationId get() = 45000 + (id.hashCode() and 0x1fff)
    private var lastStage = ""
    private var lastPercent = -2

    private fun notif(message: String, percent: Int? = null, ongoing: Boolean = true): android.app.Notification {
        val builder = NotificationCompat.Builder(applicationContext, NooirTransferWorker.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("MangaNooir · manga export")
            .setContentText(message).setOnlyAlertOnce(true)
            .setOngoing(ongoing).setPriority(NotificationCompat.PRIORITY_LOW)
        if (percent != null) builder.setProgress(100, percent.coerceIn(0,100), false)
        else if (ongoing) builder.setProgress(0,0,true)
        return builder.build()
    }
    private fun progress(stage: String, chapter: Int = 0, count: Int = 0,
        sent: Long = 0, total: Long = 0, percent: Int = -1) {
        if (lastStage == stage && lastPercent == percent) return
        lastStage = stage
        lastPercent = percent
        setProgressAsync(Data.Builder().putString(KEY_STAGE, stage)
            .putInt(KEY_CHAPTER, chapter).putInt(KEY_CHAPTERS, count)
            .putInt(KEY_PERCENT, percent).putLong(KEY_SENT, sent)
            .putLong(KEY_TOTAL, total).build())
        manager.notify(notificationId, notif(
            if (count > 0) stage + " · chapter " + chapter + "/" + count else stage,
            percent.takeIf { it >= 0 }))
    }

    override suspend fun doWork(): Result {
        val name = inputData.getString("ids_file").orEmpty()
        if (!name.matches(Regex("[0-9a-f-]{36}\\.txt"))) {
            return Result.failure(workDataOf(KEY_ERROR to "Invalid export selection"))
        }
        val manifest = File(applicationContext.filesDir, "manganooir_export_jobs/" + name)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(
                NooirTransferWorker.CHANNEL_ID, "MangaNooir file transfers",
                NotificationManager.IMPORTANCE_LOW))
        }
        setForeground(ForegroundInfo(notificationId, notif("Preparing manga export…"),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0))
        try {
            val mangaId = inputData.getLong("manga_id", -1L)
            val ids = manifest.readLines().mapNotNull(String::toLongOrNull).toSet()
            check(ids.isNotEmpty() && mangaId >= 0L) { "No chapters to export" }
            val manga = getMangaWithChapters.awaitManga(mangaId)
            val chapters = getMangaWithChapters.awaitChapters(mangaId).filter { it.id in ids }
            check(chapters.size == ids.size) { "Selected chapters changed; reselect and retry" }
            val source = sourceManager.getOrStub(manga.source)
            val format = ExportFormat.valueOf(inputData.getString("format") ?: "AUTO")
            val device = inputData.getString("device") ?: "X4"
            val quick = inputData.getBoolean("quick", false)
            val options = MangaConversionOptions.fromToken(inputData.getString("options"))
            val output = inputData.getString("output_uri").orEmpty()
            val host = inputData.getString("host").orEmpty()
            val folder = inputData.getString("folder").orEmpty()
            val count = chapters.size
            progress("Preparing chapters", 0, count)
            withContext(Dispatchers.IO) {
                if (output.isNotEmpty()) {
                    MihonNooirExporter.export(applicationContext, downloadProvider,
                        manga, source, chapters, Uri.parse(output), format, device, quick, options,
                        onStage = { stage, i, total -> progress(stage, i, total) },
                        coverCache = coverCache)
                } else {
                    MihonNooirExporter.sendToDevice(applicationContext, downloadProvider,
                        manga, source, chapters, host, folder, format, device, quick, options,
                        onStage = { stage, i, total -> progress(stage, i, total) },
                        onUploadProgress = { sent, total ->
                            if (isStopped) throw CancellationException("Export cancelled")
                            val pct = (sent * 100L / total.coerceAtLeast(1L)).toInt()
                            progress(if (sent >= total) "Waiting for XTEink confirmation"
                                else "Uploading to XTEink", count, count, sent, total, pct)
                        },
                        coverCache = coverCache)
                }
            }
            manager.notify(notificationId, notif(if (output.isNotEmpty()) "Manga file saved"
                else "XTEink confirmed manga upload", ongoing = false))
            return Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val error = e.message ?: "Manga export failed"
            manager.notify(notificationId, notif("Export failed: " + error.take(85), ongoing = false))
            return Result.failure(workDataOf(KEY_ERROR to error))
        } finally {
            manifest.delete()
        }
    }
}
