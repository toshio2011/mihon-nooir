package dev.nooir.mangabridge

/**
 * A preflight decision is required BEFORE any output file is written.
 * Workers record this as an actionable queue result instead of blocking
 * the sequential WorkManager chain waiting for an Activity.
 */
class NooirExportPreflightException(
    val kind: String,
    details: String,
) : IllegalStateException(details) {
    companion object {
        const val COVER = "cover"
        const val CHAPTERS = "chapters"
        const val RETRY = "retry"
    }
}

/** Never confuse temporary network errors with genuinely inaccessible chapters. */
object NooirChapterAccess {
    enum class Issue { UNAVAILABLE, RETRY }

    fun classify(httpStatus: Int? = null, message: String = ""): Issue {
        if (httpStatus in setOf(401, 402, 403, 404, 410, 423, 451)) return Issue.UNAVAILABLE
        if (httpStatus != null) return Issue.RETRY
        val reason = message.lowercase()
        return if (listOf("locked", "premium", "paywall", "subscription",
                "unauthorized", "forbidden", "login required", "chapter removed",
                "http 401", "http 402", "http 403", "http 404", "http 410")
                .any(reason::contains)) Issue.UNAVAILABLE else Issue.RETRY
    }
}
