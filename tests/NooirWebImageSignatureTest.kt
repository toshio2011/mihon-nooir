import dev.nooir.mangabridge.NooirWebImageSignature

fun main() {
    val jpeg = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xe0.toByte(), 0)
    val png = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0)
    val gif87 = "GIF87a".toByteArray()
    val gif89 = "GIF89a".toByteArray()
    val bogus = "<html>temporarily unavailable</html>".toByteArray()
    check(NooirWebImageSignature.valid(jpeg, "image/jpeg"))
    check(NooirWebImageSignature.valid(png, "image/png; charset=binary"))
    check(NooirWebImageSignature.valid(gif87, "image/gif"))
    check(NooirWebImageSignature.valid(gif89, "image/gif"))
    check(!NooirWebImageSignature.valid(bogus, "image/jpeg"))
    check(!NooirWebImageSignature.valid(bogus, "image/png"))
    check(!NooirWebImageSignature.valid(jpeg, "image/png"))
    check(!NooirWebImageSignature.valid(png.copyOf(7), "image/png"))
    check(!NooirWebImageSignature.valid(gif89.copyOf(5), "image/gif"))
    check(!NooirWebImageSignature.valid(jpeg, "image/svg+xml"))
    check(!NooirWebImageSignature.valid(byteArrayOf(), "image/jpeg"))
    println("PASS: JPEG/PNG/GIF public image signatures reject HTML, wrong MIME and truncated headers")
}
