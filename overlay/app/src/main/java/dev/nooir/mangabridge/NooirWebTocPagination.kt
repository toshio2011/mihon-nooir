package dev.nooir.mangabridge

import java.net.URI

/** Conservative public table-of-contents pagination. This never guesses
 * chapter URLs or follows ordinary 'next chapter' links. It only follows an
 * explicitly numbered same-site TOC page linked from pagination controls.
 */
internal object NooirWebTocPagination {
    const val MAX_PAGES = 12

    data class Candidate(val href: String, val inPager: Boolean, val relNext: Boolean = false)

    private val pathPage = Regex("/page/([1-9][0-9]{0,2})/?$", RegexOption.IGNORE_CASE)
    private val queryPage = Regex("(?:^|&)(?:page|paged|page_number|pagenum)=([1-9][0-9]{0,2})(?=&|$)", RegexOption.IGNORE_CASE)

    private fun pageNumber(uri: URI): Int = queryPage.find(uri.rawQuery.orEmpty())
        ?.groupValues?.get(1)?.toIntOrNull()
        ?: pathPage.find(uri.rawPath.orEmpty())?.groupValues?.get(1)?.toIntOrNull()
        ?: 1

    private fun basePath(uri: URI): String = uri.rawPath.orEmpty()
        .trimEnd('/').ifEmpty { "/" }.replace(pathPage, "").trimEnd('/').ifEmpty { "/" }

    private fun stableQuery(uri: URI): List<String> = uri.rawQuery.orEmpty().split('&')
        .filter { it.isNotBlank() && !queryPage.matches(it) }
        .filterNot {
            val name = it.substringBefore('=').lowercase()
            name in setOf("fbclid", "gclid", "mc_cid", "mc_eid") || name.startsWith("utm_")
        }.sorted()

    /** Confirm a fetched page did not redirect from TOC pagination to a chapter. */
    fun sameCollection(firstUrl: String, secondUrl: String): Boolean {
        val first = runCatching { URI(firstUrl).normalize() }.getOrNull() ?: return false
        val second = runCatching { URI(secondUrl).normalize() }.getOrNull() ?: return false
        return first.scheme.equals("https", true) &&
            second.scheme.equals("https", true) &&
            !first.host.isNullOrBlank() && first.host.equals(second.host, true) &&
            first.port == -1 && second.port == -1 &&
            first.rawUserInfo == null && second.rawUserInfo == null &&
            basePath(first) == basePath(second) &&
            stableQuery(first) == stableQuery(second) &&
            pageNumber(first) == pageNumber(second)
    }

    fun next(pageUrl: String, links: List<Candidate>, visited: Set<String> = emptySet()): String? {
        val source = runCatching { URI(pageUrl).normalize() }.getOrNull() ?: return null
        if (!source.scheme.equals("https", true) || source.host.isNullOrBlank() ||
            source.port != -1 || source.rawUserInfo != null) return null
        val expected = pageNumber(source) + 1
        return links.take(120).firstNotNullOfOrNull { link ->
            if (!link.inPager && !link.relNext) return@firstNotNullOfOrNull null
            val target = runCatching {
                val href = link.href.trim()
                // Java URI.resolve("?page=2") unexpectedly drops the last
                // path segment, so preserve the current document path here.
                if (href.startsWith("?")) URI(
                    source.toASCIIString().substringBefore('#').substringBefore('?') + href
                ).normalize() else source.resolve(href).normalize()
            }.getOrNull() ?: return@firstNotNullOfOrNull null
            if (!target.scheme.equals("https", true) ||
                !target.host.equals(source.host, true) || target.port != -1 ||
                target.rawUserInfo != null || stableQuery(target) != stableQuery(source) ||
                basePath(target) != basePath(source)) return@firstNotNullOfOrNull null
            val fromQuery = queryPage.find(target.rawQuery.orEmpty())
                ?.groupValues?.get(1)?.toIntOrNull()
            val fromPath = pathPage.find(target.rawPath.orEmpty())
                ?.groupValues?.get(1)?.toIntOrNull()
            val targetPage = if (fromPath != null) fromPath else fromQuery
            if (targetPage != expected) return@firstNotNullOfOrNull null
            val url = target.toASCIIString().substringBefore('#')
            require(url !in visited && url != pageUrl.substringBefore('#')) {
                "Table-of-contents pagination loop detected; nothing was exported."
            }
            url
        }
    }
}
