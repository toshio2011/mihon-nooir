package dev.nooir.mangabridge

/** Prefer readable prose rather than large, link-heavy chapter directories. */
internal object NooirWebReadability {
    fun score(textLength: Int, paragraphCount: Int, linkedTextLength: Int): Int {
        require(textLength >= 0 && paragraphCount >= 0 && linkedTextLength >= 0)
        val linked = linkedTextLength.coerceAtMost(textLength)
        val prose = textLength - linked
        // Use long arithmetic to avoid overflow on unexpectedly large pages.
        val score = prose.toLong() + paragraphCount.toLong() * 60L - linked.toLong()
        return score.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
    }
}
