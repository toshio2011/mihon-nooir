package dev.nooir.mangabridge

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CancellationException

/**
 * Copies an already converted book to a user-chosen Android SAF output.
 *
 * Both single-file CreateDocument and multi-file folder saves use the same
 * bounded 32 KiB buffer, cancellation checks and completed-length validation.
 * Does not delete destinations: only the caller who created a new document
 * can decide whether deleting it is safe.
 *
 * No 180 MiB e-reader upload limit applies to local phone saving.
 */
object PdfSaveCopy {
    fun copy(
        source: InputStream,
        destination: OutputStream,
        expectedBytes: Long,
        cancelled: () -> Boolean,
    ): Long {
        require(expectedBytes >= 0L) { "Invalid converted file length" }
        val buffer = ByteArray(32 * 1024)
        var copied = 0L
        while (true) {
            if (cancelled()) throw CancellationException("PDF phone save cancelled; selected output may be incomplete")
            val count = source.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            destination.write(buffer, 0, count)
            copied += count
        }
        if (cancelled()) throw CancellationException("PDF phone save cancelled; selected output may be incomplete")
        if (copied != expectedBytes) {
            throw IOException("Incomplete PDF phone save: copied $copied of $expectedBytes bytes")
        }
        destination.flush()
        if (cancelled()) throw CancellationException("PDF phone save cancelled; selected output may be incomplete")
        return copied
    }
}
