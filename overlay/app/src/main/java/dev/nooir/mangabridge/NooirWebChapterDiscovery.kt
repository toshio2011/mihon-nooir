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

    internal fun tocLinks(page: Document): List<NooirWebTocPagination.Candidate> =
        page.select("a[rel=next][href], link[rel=next][href], " +
            ".pagination a[href], [class*=pagination] a[href], " +
            ".page-numbers a[href], nav[aria-label*=agination] a[href]")
            .take(120).map { node ->
                val isPager = node.className().contains("page-numbers", true) ||
                    node.parents().any { parent ->
                        parent.className().contains("pagination", true) ||
                            parent.className().contains("page-numbers", true) ||
                            (parent.tagName().equals("nav", true) &&
                                parent.attr("aria-label").contains("pagination", true))
                    }
                val next = node.attr("rel").split(Regex("\\s+"))
                    .any { it.equals("next", true) }
                NooirWebTocPagination.Candidate(node.attr("href"), isPager, next)
            }

    fun chapters(
        storyUrl: String,
        page: Document,
        fetchPage: (String) -> Document = { NooirWebArticle.load(it) },
        pace: (Long) -> Unit = { Thread.sleep(it) },
    ): List<NooirWebNovel.Chapter> {
        val html = NooirWebNovel.discover(page)
        if (NooirWattpadPublic.storyId(storyUrl) != null) {
            // Wattpad's public metadata already returns a complete inventory
            // where available; do not chase site navigation beside that API.
            return runCatching { NooirWattpadPublic.discover(storyUrl) }
                .getOrElse { error ->
                    if (html.isNotEmpty()) html
                    else throw IllegalStateException(
                        "Wattpad did not expose a public chapter list: " +
                        (error.message ?: "unavailable").take(190), error
                    )
                }
        }
        // Only pages containing a real chapter list are considered TOCs.
        // One-chapter reading pages frequently contain 'next' navigation.
        if (html.size < 2) return html
        val merged = linkedMapOf<String, NooirWebNovel.Chapter>()
        val visited = linkedSetOf(page.location().substringBefore('#'))
        var current = page
        for (index in 1..NooirWebTocPagination.MAX_PAGES) {
            val found = if (index == 1) html else NooirWebNovel.discover(current)
            require(found.isNotEmpty()) {
                "Public table-of-contents page $index contained no visible chapters. " +
                    "Nothing was exported; provide chapter URLs manually."
            }
            var added = 0
            for (chapter in found) {
                if (merged.putIfAbsent(NooirWebChapterIdentity.key(chapter.url), chapter) == null) added++
            }
            require(index == 1 || added > 0) {
                "Table-of-contents page $index repeated earlier chapters. " +
                    "Check the source pagination and choose chapters manually."
            }
            val next = NooirWebTocPagination.next(current.location(), tocLinks(current), visited)
                ?: return merged.values.toList()
            require(index < NooirWebTocPagination.MAX_PAGES) {
                "The public chapter list spans more than " +
                    NooirWebTocPagination.MAX_PAGES + " pages. " +
                    "Nothing was exported: paste chapter URLs manually rather than saving an incomplete book."
            }
            // No burst fetching when a site publishes multiple TOC pages.
            pace(1300L)
            val loaded = fetchPage(next)
            require(NooirWebTocPagination.sameCollection(next, loaded.location())) {
                "Public table-of-contents pagination redirected outside this story. " +
                    "Nothing was exported; provide chapter URLs manually."
            }
            require(visited.add(loaded.location().substringBefore('#'))) {
                "Public table-of-contents pagination loop detected; nothing was exported."
            }
            visited.add(next)
            current = loaded
        }
        return merged.values.toList()
    }

    fun inspect(storyUrl: String): Analysis {
        val checked = NooirWebArticle.publicHttps(storyUrl)
        val page = NooirWebArticle.load(checked)
        val wattpad = if (NooirWattpadPublic.storyId(checked) != null) {
            runCatching { NooirWattpadPublic.storyDetails(checked) }.getOrNull()
        } else null
        val items = wattpad?.chapters ?: chapters(checked, page)
        require(items.isNotEmpty()) {
            "No publicly listed chapters found. Try a story table-of-contents URL, " +
            "or paste readable chapter URLs manually. Dynamic/login-only chapters are not imported."
        }
        val meta = NooirWebArticle.metadata(page)
        return Analysis(
            inputUrl = storyUrl.trim(),
            title = wattpad?.title?.ifBlank { meta.title } ?: meta.title,
            author = wattpad?.author?.ifBlank { meta.author } ?: meta.author,
            description = wattpad?.synopsis?.ifBlank { meta.description } ?: meta.description,
            language = wattpad?.language?.ifBlank { meta.language } ?: meta.language,
            detectedCoverUrl = wattpad?.coverUrl?.ifBlank {
                NooirWebCover.detectedUrl(page).orEmpty()
            } ?: NooirWebCover.detectedUrl(page).orEmpty(),
            chapters = NooirWebChapterPlan.start(items.map {
                NooirWebChapterPlan.Entry(it.url, it.title)
            }),
        )
    }
}
