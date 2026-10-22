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
import androidx.work.WorkInfo
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
        /** Shared queue visible in More → Send & Convert. Only one manga worker
         * runs at a time, even when several books are selected from different screens.
         */
        const val QUEUE_NAME = NooirDeviceWorkQueue.NAME
        const val TITLE_TAG_PREFIX = "manganooir_title:"

        const val KEY_STAGE = "stage"
        const val KEY_CHAPTER = "chapter"
        const val KEY_CHAPTERS = "chapters"
        const val KEY_PERCENT = "percent"
        const val KEY_SENT = "sent"
        const val KEY_TOTAL = "total"
        const val KEY_ERROR = "error"
        const val KEY_OUTPUT_NAME = "output_name"
        const val KEY_SELECTED_CHAPTERS = "selected_chapters"
        const val KEY_CHAPTER_PREVIEW = "chapter_preview"
        const val KEY_CONFIRMATION_KIND = "preflight_kind"
        const val KEY_CONFIRMATION_DETAILS = "preflight_details"
        const val KEY_SKIPPED_CHAPTERS = "skipped_chapters"
        const val KEY_MISSING_COVER = "missing_cover"
        private const val PREF_HANDLED = "preflight_handled_ids"

        fun enqueue(context: Context, mangaId: Long, mangaTitle: String, chapterIds: List<Long>,
            destination: Uri?, host: String?, folder: String, format: ExportFormat,
            device: String, quick: Boolean, options: MangaConversionOptions,
            outputName: String = "", chapterNames: List<String> = emptyList()): UUID {
            require(chapterIds.isNotEmpty()) { "Select at least one chapter" }
            require(destination != null || !host.isNullOrBlank()) { "Missing destination" }
            // Avoid WorkManager's 10KB Data limit with a private, durable chapter ID manifest.
            val dir = File(context.filesDir, "manganooir_export_jobs").apply { mkdirs() }
            val manifest = File(dir, UUID.randomUUID().toString() + ".txt")
            manifest.writeText(chapterIds.distinct().joinToString("\n"))
            try {
                val request = OneTimeWorkRequestBuilder<NooirMangaExportWorker>()
                    .addTag(TITLE_TAG_PREFIX + mangaTitle.take(96))
                    .setInputData(workDataOf(
                    "manga_id" to mangaId, "ids_file" to manifest.name,
                    "output_uri" to (destination?.toString() ?: ""),
                    "host" to host.orEmpty(), "folder" to folder,
                    "format" to format.name, "device" to device,
                    "quick" to quick, "options" to options.token(),
                    KEY_OUTPUT_NAME to outputName,
                    KEY_SELECTED_CHAPTERS to chapterIds.distinct().size,
                    KEY_CHAPTER_PREVIEW to chapterNames.take(12).joinToString("\n").take(650)
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


        /** Requeue a preflight decision without blocking the shared FIFO chain.
         * The original job has completed; its chapter manifest is copied before
         * any old data is removed. Explicit decisions affect only this job.
         */
        fun resume(context: Context, job: WorkInfo): UUID {
            val kind = job.outputData.getString(KEY_CONFIRMATION_KIND).orEmpty()
            require(kind in setOf(NooirExportPreflightException.COVER,
                NooirExportPreflightException.CHAPTERS, NooirExportPreflightException.RETRY)) {
                "This job does not need preflight confirmation"
            }
            val oldName = job.inputData.getString("ids_file").orEmpty()
            require(oldName.matches(Regex("[0-9a-f-]{36}\\.txt"))) { "Invalid chapter manifest" }
            val dir = File(context.filesDir, "manganooir_export_jobs")
            val original = File(dir, oldName)
            require(original.isFile) { "Chapter selection expired. Re-select chapters in Mihon." }
            val next = File(dir, UUID.randomUUID().toString() + ".txt")
            original.copyTo(next)
            try {
                val old = MangaConversionOptions.fromToken(job.inputData.getString("options"))
                val chosen = when (kind) {
                    NooirExportPreflightException.COVER -> old.copy(allowMissingCover = true)
                    NooirExportPreflightException.CHAPTERS -> old.copy(skipUnavailableChapters = true)
                    else -> old
                }
                val data = Data.Builder().putAll(job.inputData)
                    .putString("ids_file", next.name)
                    .putString("options", chosen.token()).build()
                val request = OneTimeWorkRequestBuilder<NooirMangaExportWorker>()
                    .setInputData(data).apply {
                        job.tags.filter { it.startsWith(TITLE_TAG_PREFIX) }.forEach(::addTag)
                    }.build()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    QUEUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
                markHandled(context, job.id.toString())
                original.delete()
                return request.id
            } catch (e: Exception) {
                next.delete()
                throw e
            }
        }

        fun dismissPreflight(context: Context, job: WorkInfo) {
            require(job.outputData.getString(KEY_CONFIRMATION_KIND) != null)
            val name = job.inputData.getString("ids_file").orEmpty()
            if (name.matches(Regex("[0-9a-f-]{36}\\.txt"))) {
                File(context.filesDir, "manganooir_export_jobs/" + name).delete()
            }
            markHandled(context, job.id.toString())
        }

        fun handledPreflightIds(context: Context): Set<String> =
            context.getSharedPreferences("manganooir_export", Context.MODE_PRIVATE)
                .getStringSet(PREF_HANDLED, emptySet()).orEmpty().toSet()

        private fun markHandled(context: Context, id: String) {
            val prefs = context.getSharedPreferences("manganooir_export", Context.MODE_PRIVATE)
            val next = prefs.getStringSet(PREF_HANDLED, emptySet()).orEmpty().toMutableSet()
            next += id
            prefs.edit().putStringSet(PREF_HANDLED, next).apply()
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
            return Result.success(NooirDeviceWorkQueue.failed(Data.Builder(), KEY_ERROR, "Invalid export selection"))
        }
        val manifest = File(applicationContext.filesDir, "manganooir_export_jobs/" + name)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(
                NooirTransferWorker.CHANNEL_ID, "MangaNooir file transfers",
                NotificationManager.IMPORTANCE_LOW))
        }
        setForeground(ForegroundInfo(notificationId, notif("Preparing manga export…"),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0))
        var needsDecision = false
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
            val desiredName = inputData.getString(KEY_OUTPUT_NAME).orEmpty().ifBlank { manga.title }
            if (output.isEmpty()) {
                progress("Checking existing XTEink filenames", 0, count)
                withContext(Dispatchers.IO) {
                    NooirDestinationGuard.requireOnReader(host, folder,
                        NooirDestinationGuard.forManga(desiredName, format, device))
                }
            }
            progress("Preparing chapters", 0, count)
            val exported = withContext(Dispatchers.IO) {
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
                        coverCache = coverCache,
                        outputName = desiredName)
                }
            }
            manager.notify(notificationId, notif(if (output.isNotEmpty()) "Manga file saved"
                else "XTEink confirmed manga upload", ongoing = false))
            return Result.success(Data.Builder()
                .putString(KEY_SKIPPED_CHAPTERS, exported.skippedChapters.joinToString("; ").take(1500))
                .putBoolean(KEY_MISSING_COVER, exported.missingCover).build())
        } catch (e: NooirExportPreflightException) {
            needsDecision = true
            manager.notify(notificationId, notif("Action needed: " + e.message.orEmpty().take(80),
                ongoing = false))
            return Result.success(Data.Builder()
                .putString(KEY_CONFIRMATION_KIND, e.kind)
                .putString(KEY_CONFIRMATION_DETAILS, e.message.orEmpty().take(1700)).build())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val error = e.message ?: "Manga export failed"
            manager.notify(notificationId, notif("Export failed: " + error.take(85), ongoing = false))
            return Result.success(NooirDeviceWorkQueue.failed(Data.Builder(), KEY_ERROR, error))
        } finally {
            if (!needsDecision) manifest.delete()
        }
    }
}
