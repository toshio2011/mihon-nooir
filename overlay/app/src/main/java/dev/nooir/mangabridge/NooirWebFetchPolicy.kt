package dev.nooir.mangabridge

/** Transient HTTP retry policy for ordinary, publicly readable HTML.
 * Authentication failures and missing pages must never be retried.
 */
internal object NooirWebFetchPolicy {
    private val transientStatuses = setOf(408, 429, 500, 502, 503, 504)

    /** At most two retries per URL. A long server retry hint is respected by
     * failing for now, not by ignoring the hint or blocking for minutes.
     */
    fun retryDelayMillis(status: Int, attempt: Int, retryAfter: String?): Long? {
        if (status !in transientStatuses || attempt !in 0..1) return null
        val serverSeconds = retryAfter?.trim()?.toLongOrNull()
        if (serverSeconds != null && serverSeconds > 8L) return null
        return if (serverSeconds != null && serverSeconds >= 0L) {
            serverSeconds.coerceAtLeast(1L) * 1000L
        } else if (attempt == 0) 900L else 1800L
    }
}
