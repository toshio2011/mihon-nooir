package dev.nooir.mangabridge

import java.net.URI

/** Comparison key for public chapter inventories; never rewrites fetch URLs.
 * Preserves meaningful query parameters such as ?chapter=2, while removing
 * fragments and ordinary marketing parameters that vary between visits.
 */
internal object NooirWebChapterIdentity {
    fun key(value: String): String {
        val raw = value.trim()
        val uri = runCatching { URI(raw).normalize() }.getOrNull() ?: return raw
        val host = uri.host?.lowercase() ?: return raw
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.rawUserInfo != null) return raw
        val path = uri.rawPath.orEmpty().ifBlank { "/" }
            .let { if (it.length > 1) it.trimEnd('/').ifEmpty { "/" } else it }
        val filteredQuery = uri.rawQuery?.split('&')?.filter { pair ->
            val param = pair.substringBefore('=').lowercase()
            !param.startsWith("utm_") && param !in setOf("fbclid", "gclid", "mc_cid", "mc_eid")
        }?.joinToString("&").orEmpty()
        val port = if (uri.port != -1) ":${uri.port}" else ""
        return "https://$host$port$path" + if (filteredQuery.isEmpty()) "" else "?$filteredQuery"
    }
}
