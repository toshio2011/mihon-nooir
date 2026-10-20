package dev.nooir.mangabridge

import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URI

/**
 * Discover only publicly listed Wattpad chapters using Wattpad's public story
 * metadata response. Unlike an in-browser extension, MangaNooir does not use
 * browser cookies, a signed-in session, private APIs, or paid chapter endpoints.
 *
 * The public API is not guaranteed to be available to all clients. A rejected
 * request is an explicit diagnostic; do not silently return a short/empty book.
 * Implemented independently (no WebToEpub GPL source copied).
 */
internal object NooirWattpadPublic {
    private val STORY = Regex("^/story/([0-9]{5,20})(?:-[^/?#]*)?/?$")
    private const val MAX_METADATA_BYTES = 2_000_000

    internal fun storyId(input: String): String? {
        val uri = runCatching { URI(input.trim()) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", true) ||
            uri.host?.lowercase() !in setOf("wattpad.com", "www.wattpad.com") ||
            uri.port != -1 || uri.rawUserInfo != null) return null
        return STORY.matchEntire(uri.path.orEmpty())?.groupValues?.get(1)
    }

    /** Pure parser, also useful with a local recorded public API fixture. */
    internal fun fromPublicStoryJson(json: String): List<NooirWebNovel.Chapter> {
        val story = JSONObject(json)
        val parts = story.optJSONArray("parts")
            ?: error("Wattpad's public metadata has no chapter list")
        val count = parts.length()
        require(count > 0) { "Wattpad story currently lists no public chapters" }
        val chapters = ArrayList<NooirWebNovel.Chapter>(count)
        val seen = HashSet<String>()
        for (index in 0 until count) {
            val part = parts.optJSONObject(index)
                ?: error("Wattpad chapter metadata is incomplete at part ${index + 1}")
            // No paid, draft, deleted or unpublished parts are imported or
            // silently skipped: report an actionable error for the entire book.
            check(!part.optBoolean("draft") && !part.optBoolean("deleted") &&
                !part.optBoolean("isPaywalled") && !part.optBoolean("isPaid") &&
                !part.optBoolean("paid")) {
                "Wattpad part ${index + 1} is not publicly readable. " +
                    "Use a legally supplied EPUB; this importer does not unlock chapters."
            }
            val id = part.optString("id").trim()
            val name = part.optString("title").trim().ifBlank { "Part ${index + 1}" }
            val given = part.optString("url").trim()
            require(id.matches(Regex("[0-9]{4,20}"))) {
                "Invalid public chapter ID at part ${index + 1}"
            }
            val candidate = if (given.isNotBlank()) {
                URI("https://www.wattpad.com/").resolve(given)
            } else URI("https://www.wattpad.com/$id")
            require(candidate.scheme.equals("https", true) &&
                candidate.host?.lowercase() in setOf("www.wattpad.com", "wattpad.com") &&
                candidate.port == -1 && candidate.rawUserInfo == null &&
                Regex("^/[0-9]{4,20}(?:-[^/]*)?/?$").matches(candidate.path.orEmpty())) {
                "Unexpected Wattpad chapter address at part ${index + 1}"
            }
            val href = URI("https", null, candidate.host, -1, candidate.path, null, null).toASCIIString()
            check(seen.add(href)) { "Duplicate chapter ${index + 1} in Wattpad story metadata" }
            chapters += NooirWebNovel.Chapter(href, name.take(160))
        }
        return chapters
    }

    internal fun discover(storyUrl: String): List<NooirWebNovel.Chapter> {
        val id = storyId(storyUrl) ?: return emptyList()
        // Public story metadata only. Avoid query-field projections that
        // may vary between Wattpad deployments or omit public part URLs.
        val endpoint = "https://www.wattpad.com/api/v3/stories/$id"
        val response = try {
            Jsoup.connect(endpoint).ignoreContentType(true)
                .userAgent("Mozilla/5.0 (compatible; MangaNooir experimental reader)")
                .referrer("https://www.wattpad.com/story/$id")
                .timeout(15_000).maxBodySize(MAX_METADATA_BYTES)
                .followRedirects(false).execute()
        } catch (e: Exception) {
            error("Wattpad did not provide its public chapter list to this Android client: " +
                (e.message ?: "request failed").take(180) +
                ". WebToEpub may see more through Firefox; try a permitted EPUB export.")
        }
        require(response.statusCode() == 200 &&
            response.contentType().orEmpty().contains("json", ignoreCase = true)) {
            "Wattpad public chapter metadata unavailable (HTTP ${response.statusCode()}). " +
                "An in-browser extension may have access that this app does not."
        }
        val body = response.body()
        require(body.length in 2..MAX_METADATA_BYTES) {
            "Wattpad returned empty or oversized public chapter metadata"
        }
        return fromPublicStoryJson(body)
    }
}
