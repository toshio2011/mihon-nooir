import dev.nooir.mangabridge.NooirChapterAccess
import dev.nooir.mangabridge.NooirExportPreflightException

fun main() {
    val unavailable = listOf(401, 402, 403, 404, 410, 423, 451)
    for (code in unavailable) {
        check(NooirChapterAccess.classify(code) == NooirChapterAccess.Issue.UNAVAILABLE) {
            "HTTP $code must be marked unavailable"
        }
    }
    for (code in listOf(408, 429, 500, 502, 503)) {
        check(NooirChapterAccess.classify(code) == NooirChapterAccess.Issue.RETRY) {
            "HTTP $code must be retryable"
        }
    }
    check(NooirChapterAccess.classify(message = "Premium chapter locked") ==
        NooirChapterAccess.Issue.UNAVAILABLE)
    check(NooirChapterAccess.classify(message = "Connection timed out") ==
        NooirChapterAccess.Issue.RETRY)
    check(NooirExportPreflightException.COVER != NooirExportPreflightException.CHAPTERS)
    check(NooirExportPreflightException.CHAPTERS != NooirExportPreflightException.RETRY)
    println("Preflight classification checks passed")
}
