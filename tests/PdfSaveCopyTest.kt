import dev.nooir.mangabridge.PdfSaveCopy
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
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

    // Phone saves are not limited by the 180 MiB XTEINK Wi-Fi policy.
    // Generate >180 MiB lazily with a constant-size buffer, without allocating
    // a huge test PDF or claiming the phone has unlimited physical storage.
    val largeBytes = 181L * 1024 * 1024
    val generated = object : InputStream() {
        var remaining = largeBytes
        override fun read(): Int {
            if (remaining <= 0L) return -1
            remaining--
            return 42
        }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (remaining <= 0L) return -1
            val count = minOf(remaining, len.toLong()).toInt()
            java.util.Arrays.fill(b, off, off + count, 42.toByte())
            remaining -= count
            return count
        }
    }
    val sink = object : OutputStream() {
        var written = 0L
        override fun write(b: Int) { written++ }
        override fun write(b: ByteArray, off: Int, len: Int) { written += len }
    }
    check(PdfSaveCopy.copy(generated, sink, largeBytes) { false } == largeBytes)
    check(sink.written == largeBytes)

    println("PASS: full phone copy, cancellation, truncation, provider failure, and streaming >180 MiB")
}
