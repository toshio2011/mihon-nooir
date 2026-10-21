package dev.nooir.mangabridge

/** One low-impact FIFO WorkManager chain for all heavy local conversions and
 * XTEink transfers. The ESP32 web server should never get competing uploads
 * from different MangaNooir workers.
 *
 * Keep the name stable: WorkManager persists dependency chains across restarts.
 */
object NooirDeviceWorkQueue {
    // WorkManager success means only that the FIFO dependency can continue.
    // Actual per-book failure lives in outputData so it cannot poison following jobs.
    const val KEY_FAILED = "nooir_job_failed"

    fun failed(data: androidx.work.Data.Builder, errorKey: String, message: String): androidx.work.Data =
        data.putBoolean(KEY_FAILED, true).putString(errorKey, message).build()

    fun hasFailed(info: androidx.work.WorkInfo): Boolean =
        info.state == androidx.work.WorkInfo.State.FAILED ||
            info.outputData.getBoolean(KEY_FAILED, false)
    const val NAME = "manganooir_manga_exports" // existing queue name; preserve pending jobs
}
