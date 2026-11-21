package dev.nooir.mangabridge

import org.jsoup.nodes.Document

/**
 * Lightweight inspect-before-export. Public HTML/story metadata only.
 * The same chapter discovery path is used by the normal novel converter.
 */
internal object NooirWebChapterDiscovery {
    data class Analysis(
        val inputUrl: String,
        val title: String,
        val author: String,
        val description: String,
        val language: String,
        val detectedCoverUrl: String,
        val chapters: List<NooirWebChapterPlan.Entry>,
    )

    fun chapters(storyUrl: String, page: Document): List<NooirWebNovel.Chapter> {
        val html = NooirWebNovel.discover(page)
        return if (NooirWattpadPublic.storyId(storyUrl) == null) html
        else runCatching { NooirWattpadPublic.discover(storyUrl) }
            .getOrElse { error ->
                if (html.isNotEmpty()) html
                else throw IllegalStateException(
                    "Wattpad did not expose a public chapter list: " +
                    (error.message ?: "unavailable").take(190), error
                )
            }
    }

    fun inspect(storyUrl: String): Analysis {
        val checked = NooirWebArticle.publicHttps(storyUrl)
        val page = NooirWebArticle.load(checked)
        val items = chapters(checked, page)
        require(items.isNotEmpty()) {
            "No publicly listed chapters found. Try a story table-of-contents URL, " +
            "or paste readable chapter URLs manually. Dynamic/login-only chapters are not imported."
        }
        val meta = NooirWebArticle.metadata(page)
        return Analysis(
            inputUrl = storyUrl.trim(),
            title = meta.title,
            author = meta.author,
            description = meta.description,
            language = meta.language,
            detectedCoverUrl = NooirWebCover.detectedUrl(page).orEmpty(),
            chapters = NooirWebChapterPlan.start(items.map {
                NooirWebChapterPlan.Entry(it.url, it.title)
            }),
        )
    }
}
