import dev.nooir.mangabridge.PdfSaveCopy
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.CancellationException

fun main() {
    val bytes = ByteArray(160 * 1024 + 11) { (it * 17).toByte() }
    val output = ByteArrayOutputStream()
    val copied = PdfSaveCopy.copy(ByteArrayInputStream(bytes), output, bytes.size.toLong()) { false }
    check(copied == bytes.size.toLong() && output.toByteArray().contentEquals(bytes))

    // An interrupted copy must throw before writing the rest of the converted book.
    // The copy helper must never delete or otherwise mutate a destination itself.
    var cancelled = false
    val partial = object : ByteArrayOutputStream() {
        override fun write(b: ByteArray, off: Int, len: Int) {
            super.write(b, off, len)
            cancelled = true
        }
    }
    val cancellation = runCatching {
        PdfSaveCopy.copy(ByteArrayInputStream(bytes), partial, bytes.size.toLong()) { cancelled }
    }.exceptionOrNull()
    check(cancellation is CancellationException)
    check(partial.size() in 1 until bytes.size)

    // Input truncation must not be reported as a successful phone save.
    val short = runCatching {
        PdfSaveCopy.copy(ByteArrayInputStream(bytes.copyOf(12)), ByteArrayOutputStream(),
            bytes.size.toLong()) { false }
    }.exceptionOrNull()
    check(short is IOException)

    // Detect cancellation even when the stream is empty or has finished reading.
    check(runCatching {
        PdfSaveCopy.copy(ByteArrayInputStream(byteArrayOf()), ByteArrayOutputStream(), 0L) { true }
    }.exceptionOrNull() is CancellationException)

    // Simulated provider write failures propagate rather than claiming success.
    val failedProvider = object : ByteArrayOutputStream() {
        override fun write(b: ByteArray, off: Int, len: Int) {
            throw IOException("provider storage exhausted")
        }
    }
    check(runCatching {
        PdfSaveCopy.copy(ByteArrayInputStream(bytes), failedProvider, bytes.size.toLong()) { false }
    }.exceptionOrNull() is IOException)

    println("PASS: full phone copy, cancellation, truncation and SAF provider write failure")
}
