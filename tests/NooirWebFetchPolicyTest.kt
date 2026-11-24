import dev.nooir.mangabridge.NooirWebFetchPolicy as Retry

fun main() {
    check(Retry.retryDelayMillis(429, 0, null) == 900L)
    check(Retry.retryDelayMillis(503, 1, "3") == 3000L)
    check(Retry.retryDelayMillis(429, 2, "2") == null)
    check(Retry.retryDelayMillis(403, 0, "1") == null)
    check(Retry.retryDelayMillis(404, 0, null) == null)
    check(Retry.retryDelayMillis(301, 0, null) == null)
    check(Retry.retryDelayMillis(500, 0, "100") == null)
    check(Retry.retryDelayMillis(500, 0, "bogus") == 900L)
    check(Retry.retryDelayMillis(408, 1, "0") == 1000L)
    check(Retry.retryDelayMillis(503, -1, null) == null)
    println("PASS: bounded retry, Retry-After, transient-only responses")
}
