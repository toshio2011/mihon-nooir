import dev.nooir.bridge.XteinkTransferPolicy

fun main() {
    val max = XteinkTransferPolicy.MAX_UPLOAD_BYTES
    check(XteinkTransferPolicy.adviseIfTooLarge(0) == null)
    check(XteinkTransferPolicy.adviseIfTooLarge(max) == null)
    val oversized = checkNotNull(XteinkTransferPolicy.adviseIfTooLarge(max + 1))
    check("too large" in oversized)
    check("save the full converted file to your phone" in oversized)
    check("smaller volumes" in oversized)
    check("180 MiB" in oversized)
    check(runCatching { XteinkTransferPolicy.adviseIfTooLarge(-1) }.isFailure)
    println("PASS: 180 MiB advisory applies only to XTEINK uploads; local saving is unrestricted")
}
