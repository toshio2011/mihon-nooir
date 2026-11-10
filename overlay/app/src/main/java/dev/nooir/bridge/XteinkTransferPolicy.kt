package dev.nooir.bridge

/**
 * Safety advisory for a single XTEINK Wi-Fi upload. This does not restrict
 * conversion or saving a generated book to phone storage.
 */
object XteinkTransferPolicy {
    const val MAX_UPLOAD_BYTES: Long = 180L * 1024 * 1024

    fun adviseIfTooLarge(bytes: Long): String? {
        require(bytes >= 0L) { "Invalid file length" }
        if (bytes <= MAX_UPLOAD_BYTES) return null
        val mib = bytes / 1048576L + if (bytes % 1048576L == 0L) 0L else 1L
        return "This book is too large for XTEINK Wi-Fi transfer (" + mib +
            " MiB; recommended maximum is 180 MiB per file). " +
            "You can still save the full converted file to your phone. " +
            "To send it to XTEINK, split it into smaller volumes or choose a smaller output format."
    }
}
