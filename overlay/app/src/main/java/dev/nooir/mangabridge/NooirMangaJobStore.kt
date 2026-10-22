package dev.nooir.mangabridge

import android.content.Context
import androidx.work.Data
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * WorkInfo intentionally does not expose WorkRequest.inputData.
 * Keep a small private snapshot so the Activity Queue can show job details
 * and explicitly requeue a preflight decision after the worker completes.
 * Chapter image bytes and credentials are never stored here.
 */
object NooirMangaJobStore {
    private fun directory(context: Context) =
        File(context.filesDir, "manganooir_export_jobs").apply { mkdirs() }

    private fun file(context: Context, id: UUID) = File(directory(context), "$id.job.json")

    fun save(context: Context, id: UUID, input: Data) {
        val obj = JSONObject()
            .put("manga_id", input.getLong("manga_id", -1L))
            .put("ids_file", input.getString("ids_file").orEmpty())
            .put("output_uri", input.getString("output_uri").orEmpty())
            .put("host", input.getString("host").orEmpty())
            .put("folder", input.getString("folder").orEmpty())
            .put("format", input.getString("format").orEmpty())
            .put("device", input.getString("device").orEmpty())
            .put("quick", input.getBoolean("quick", false))
            .put("options", input.getString("options").orEmpty())
            .put(NooirMangaExportWorker.KEY_OUTPUT_NAME,
                input.getString(NooirMangaExportWorker.KEY_OUTPUT_NAME).orEmpty())
            .put(NooirMangaExportWorker.KEY_SELECTED_CHAPTERS,
                input.getInt(NooirMangaExportWorker.KEY_SELECTED_CHAPTERS, 0))
            .put(NooirMangaExportWorker.KEY_CHAPTER_PREVIEW,
                input.getString(NooirMangaExportWorker.KEY_CHAPTER_PREVIEW).orEmpty())
        val target = file(context, id)
        val temp = File(target.parentFile, target.name + ".tmp")
        temp.writeText(obj.toString())
        check(temp.renameTo(target)) { "Could not save manga queue details" }
    }

    fun load(context: Context, id: UUID): Data? {
        val target = file(context, id)
        if (!target.isFile) return null
        return runCatching {
            val obj = JSONObject(target.readText())
            Data.Builder()
                .putLong("manga_id", obj.optLong("manga_id", -1L))
                .putString("ids_file", obj.optString("ids_file"))
                .putString("output_uri", obj.optString("output_uri"))
                .putString("host", obj.optString("host"))
                .putString("folder", obj.optString("folder"))
                .putString("format", obj.optString("format"))
                .putString("device", obj.optString("device"))
                .putBoolean("quick", obj.optBoolean("quick"))
                .putString("options", obj.optString("options"))
                .putString(NooirMangaExportWorker.KEY_OUTPUT_NAME,
                    obj.optString(NooirMangaExportWorker.KEY_OUTPUT_NAME))
                .putInt(NooirMangaExportWorker.KEY_SELECTED_CHAPTERS,
                    obj.optInt(NooirMangaExportWorker.KEY_SELECTED_CHAPTERS))
                .putString(NooirMangaExportWorker.KEY_CHAPTER_PREVIEW,
                    obj.optString(NooirMangaExportWorker.KEY_CHAPTER_PREVIEW))
                .build()
        }.getOrNull()
    }
}
