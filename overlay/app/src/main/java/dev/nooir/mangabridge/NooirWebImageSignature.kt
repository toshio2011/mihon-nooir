package dev.nooir.mangabridge

/**
 * Small, deterministic media-signature gate for public HTML chapter pictures.
 * A CDN may send a 200 HTML error document with an image/* Content-Type.
 * Reject that body so the caller can try the next public image candidate.
 *
 * This checks format signatures, not full image decoding or decompression.
 */
internal object NooirWebImageSignature {
    fun valid(bytes: ByteArray, declaredMime: String): Boolean {
        val mime = declaredMime.substringBefore(';').trim().lowercase()
        return when (mime) {
            "image/jpeg" ->
                bytes.size >= 4 &&
                    (bytes[0].toInt() and 255) == 0xff &&
                    (bytes[1].toInt() and 255) == 0xd8 &&
                    (bytes[2].toInt() and 255) == 0xff
            "image/png" ->
                bytes.size >= 8 &&
                    byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
                        .indices.all { bytes[it] == byteArrayOf(
                            0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)[it] }
            "image/gif" ->
                bytes.size >= 6 &&
                    String(bytes, 0, 6, Charsets.US_ASCII) in setOf("GIF87a", "GIF89a")
            else -> false
        }
    }
}
