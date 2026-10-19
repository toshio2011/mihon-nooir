package dev.nooir.mangabridge

/** One low-impact FIFO WorkManager chain for all heavy local conversions and
 * XTEink transfers. The ESP32 web server should never get competing uploads
 * from different MangaNooir workers.
 *
 * Keep the name stable: WorkManager persists dependency chains across restarts.
 */
object NooirDeviceWorkQueue {
    const val NAME = "manganooir_manga_exports" // existing queue name; preserve pending jobs
}
