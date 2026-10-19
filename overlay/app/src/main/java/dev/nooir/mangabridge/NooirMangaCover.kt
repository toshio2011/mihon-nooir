package dev.nooir.mangabridge

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Request
import tachiyomi.domain.manga.model.Manga
import java.io.File

/** Prefer Mihon's custom/library cover; fetch a source-authenticated thumbnail if missing.
 * Cover failures are nonfatal: a chapter's first page remains the fallback.
 */
object NooirMangaCover {
    fun resolve(
        context: Context, manga: Manga, source: Source,
        cache: CoverCache?, exportDir: File,
    ): Uri? {
        cache?.getCustomCoverFile(manga.id)?.takeIf { it.isFile && it.length() > 0 }?.let {
            return Uri.fromFile(it)
        }
        cache?.getCoverFile(manga.thumbnailUrl)?.takeIf { it.isFile && it.length() > 0 }?.let {
            return Uri.fromFile(it)
        }
        val cover = manga.thumbnailUrl?.trim().orEmpty()
        if (cover.startsWith("content://") || cover.startsWith("file://")) {
            return Uri.parse(cover)
        }
        if (cover.startsWith("/")) return Uri.fromFile(File(cover).takeIf { it.isFile } ?: return null)
        if (!cover.startsWith("https://") && !cover.startsWith("http://")) return null
        val http = source as? HttpSource ?: return null
        return runCatching {
            val request = Request.Builder().url(cover).headers(http.headers).get().build()
            http.client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "Cover request failed" }
                val target = File(exportDir, "manganooir-cover.img")
                response.body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        var length = 0L
                        val buf = ByteArray(32 * 1024)
                        while (true) {
                            val count = input.read(buf)
                            if (count == -1) break
                            length += count
                            check(length <= 6L * 1024 * 1024) { "Cover image is too large" }
                            output.write(buf, 0, count)
                        }
                    }
                }
                require(target.length() > 0) { "Empty cover" }
                Uri.fromFile(target)
            }
        }.getOrNull()
    }
}
