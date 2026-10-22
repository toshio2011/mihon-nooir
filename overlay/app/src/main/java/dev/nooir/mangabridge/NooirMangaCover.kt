package dev.nooir.mangabridge

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.CancellationException
import okhttp3.Request
import tachiyomi.domain.manga.model.Manga
import java.io.File

/**
 * Find a valid Mihon cover, then refresh source manga details if its saved
 * thumbnail is missing/stale. Do not overwrite the user's custom cover or
 * mutate manga metadata in the library just to export one book.
 */
object NooirMangaCover {
    private const val MAX_COVER_BYTES = 6L * 1024 * 1024

    private fun imageLooksValid(file: File): Boolean {
        if (!file.isFile || file.length() <= 0L) return false
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        return runCatching {
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            bounds.outWidth > 0 && bounds.outHeight > 0
        }.getOrDefault(false)
    }

    /**
     * Refresh is attempted only after valid custom/cache art and the saved
     * thumbnail URL fail. A changed source thumbnail URL is used for this
     * export even if Mihon's library entry hasn't been refreshed yet.
     */
    suspend fun resolve(
        context: Context, manga: Manga, source: Source,
        cache: CoverCache?, exportDir: File,
    ): Uri? {
        cache?.getCustomCoverFile(manga.id)?.takeIf(::imageLooksValid)?.let {
            return Uri.fromFile(it)
        }
        cache?.getCoverFile(manga.thumbnailUrl)?.takeIf(::imageLooksValid)?.let {
            return Uri.fromFile(it)
        }

        val http = source as? HttpSource
        val target = File(exportDir, "manganooir-cover.img")

        fun fetchThumbnail(url: String?): Uri? {
            val value = url?.trim().orEmpty()
            if (value.isBlank()) return null
            if (value.startsWith("content://")) {
                // Leave the provider URI intact; the image decoder validates it.
                return Uri.parse(value)
            }
            if (value.startsWith("file://")) {
                val file = Uri.parse(value).path?.let(::File) ?: return null
                return file.takeIf(::imageLooksValid)?.let(Uri::fromFile)
            }
            if (value.startsWith("/")) {
                return File(value).takeIf(::imageLooksValid)?.let(Uri::fromFile)
            }
            if (!value.startsWith("https://") && !value.startsWith("http://")) return null
            val online = http ?: return null
            return try {
                val request = Request.Builder().url(value).headers(online.headers).get().build()
                online.client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return null
                    val partial = File(exportDir, "manganooir-cover.download")
                    try {
                        response.body.byteStream().use { input ->
                            partial.outputStream().use { output ->
                                val buffer = ByteArray(32 * 1024)
                                var size = 0L
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    size += count
                                    check(size <= MAX_COVER_BYTES) { "Cover image exceeds 6 MiB" }
                                    output.write(buffer, 0, count)
                                }
                            }
                        }
                        if (!imageLooksValid(partial)) return null
                        check(partial.renameTo(target)) { "Unable to prepare refreshed cover" }
                        Uri.fromFile(target)
                    } finally {
                        partial.delete()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }

        // A valid saved URL is enough; avoid unnecessary network detail refresh.
        fetchThumbnail(manga.thumbnailUrl)?.let { return it }

        // Equivalent to Mihon's Refresh manga details, but read-only: the source
        // may return a new thumbnail URL or regenerate a signed/expiring URL.
        // Do not fetch chapters, and don't write changed metadata to the library.
        val updatedUrl = try {
            source.getMangaUpdate(
                manga = manga.toSManga(),
                chapters = emptyList(),
                fetchDetails = true,
                fetchChapters = false,
            ).manga.thumbnail_url
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        if (!updatedUrl.isNullOrBlank()) {
            cache?.getCoverFile(updatedUrl)?.takeIf(::imageLooksValid)?.let {
                return Uri.fromFile(it)
            }
            fetchThumbnail(updatedUrl)?.let { return it }
        }
        return null
    }
}
