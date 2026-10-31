package dev.nooir.desktop.ui

import dev.nooir.desktop.suwayomi.SuwayomiClient

/**
 * Execute one explicitly claimed chapter request using the existing verified
 * GraphQL contract. No byte progress is claimed: Suwayomi's isDownloaded
 * indicates complete or not complete, not how many bytes arrived.
 *
 * Stop means STOP MONITORING, not "cancel remote download". Once accepted by
 * Suwayomi a request may continue on its own regardless of this app window.
 */
internal class RemoteChapterRunner(
    private val queue: RemoteChapterQueue,
    private val client: SuwayomiClient,
    private val pollIntervalMs: Long = 2_000,
    private val maxPolls: Int = 90,
    private val pause: (Long) -> Unit = { Thread.sleep(it) },
) {
    init {
        require(pollIntervalMs in 0..30_000 && maxPolls in 1..3_600)
    }

    fun runClaimed(
        job: RemoteChapterJob,
        stop: () -> Boolean,
        update: (String) -> Unit = {},
    ): Boolean {
        require(job.state == RemoteChapterState.SUBMITTING)
        try {
            // Confirm that this is still a valid chapter of the selected
            // series, and avoid duplicate enqueues if Suwayomi finished it.
            val manga = client.manga(job.mangaId)
            val chapter = manga.chapters.firstOrNull { it.id == job.chapterId }
                ?: error("Selected chapter is not in this manga")
            if (chapter.downloaded) {
                queue.completed(job.id)
                update("Chapter ${job.chapterId} already downloaded")
                return true
            }
            if (stop()) {
                // No enqueue mutation has been attempted: it is safe to
                // return to PENDING. A subsequent submit still needs consent.
                queue.stoppedBeforeSubmission(job.id)
                return false
            }
            update("Submitting chapter ${job.chapterId} to local Suwayomi")
            client.enqueue(listOf(job.chapterId))
            queue.submitted(job.id)
            return monitor(job, stop, update)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            runCatching { queue.interrupted(job.id) }
            return false
        } catch (e: Exception) {
            runCatching { queue.failed(job.id, e.message ?: "Remote chapter request failed") }
            update("Chapter ${job.chapterId}: ${e.message ?: "request failed"}")
            return false
        }
    }

    /** Only monitors server state. Never resends an uncertain request. */
    fun resumeMonitoring(
        job: RemoteChapterJob,
        stop: () -> Boolean,
        update: (String) -> Unit = {},
    ): Boolean {
        require(job.state == RemoteChapterState.WAITING)
        return monitor(job, stop, update)
    }

    private fun monitor(job: RemoteChapterJob,
        stop: () -> Boolean, update: (String) -> Unit): Boolean {
        try {
            repeat(maxPolls) { attempt ->
                if (stop()) {
                    queue.interrupted(job.id)
                    update("Stopped monitoring chapter ${job.chapterId}; Suwayomi may continue")
                    return false
                }
                val chapter = client.manga(job.mangaId).chapters.firstOrNull {
                    it.id == job.chapterId
                } ?: error("Chapter no longer found in Suwayomi")
                if (chapter.downloaded) {
                    queue.completed(job.id)
                    update("Chapter ${job.chapterId} downloaded")
                    return true
                }
                update("Waiting for chapter ${job.chapterId} (check ${attempt + 1}/$maxPolls)")
                if (attempt + 1 < maxPolls) pause(pollIntervalMs)
            }
            queue.failed(job.id,
                "Timed out checking Suwayomi; remote download may still be running")
            return false
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            runCatching { queue.interrupted(job.id) }
            return false
        } catch (e: Exception) {
            runCatching { queue.failed(job.id, e.message ?: "Remote monitoring error") }
            update("Chapter ${job.chapterId}: ${e.message ?: "monitoring failed"}")
            return false
        }
    }
}
