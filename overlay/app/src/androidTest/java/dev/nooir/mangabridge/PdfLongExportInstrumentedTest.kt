package dev.nooir.mangabridge

import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.nooir.bridge.ReflowEpubFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.CancellationException
import java.util.zip.ZipFile

/**
 * Real Android PDFBox + PdfRenderer path, not just a desktop EPUB writer mock.
 * All PDF pages and artwork are generated here; no third-party content.
 */
@RunWith(AndroidJUnit4::class)
class PdfLongExportInstrumentedTest {
    @Test fun converts1001PageOriginalPdfWithoutTruncation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.cacheDir, "nooir-pdf-1001-" + System.nanoTime())
        assertTrue(folder.mkdirs())
        try {
            val source = File(folder, "original-1001.pdf")
            val book = PdfDocument()
            try {
                val ink = Paint().apply { color = Color.BLACK; strokeWidth = 1f }
                for (n in 0 until 1001) {
                    // Tiny pages keep a 1,001-page test within the emulator's
                    // memory/disk budget while testing the full real PDF path.
                    val info = PdfDocument.PageInfo.Builder(24, 32, n + 1).create()
                    val page = book.startPage(info)
                    page.canvas.drawColor(Color.WHITE)
                    page.canvas.drawLine(2f, 2f, 22f, 30f, ink)
                    book.finishPage(page)
                }
                source.outputStream().use { book.writeTo(it) }
            } finally {
                book.close()
            }
            assertTrue(source.length() > 0)
            val result = File(folder, "converted-1001.epub")
            var lastPage = 0
            NooirPdfToEpub.convert(
                context, source, result,
                mode = NooirPdfToEpub.Mode.ORIGINAL_PAGES,
                epubFormat = ReflowEpubFormat.EPUB3,
                onPage = { page, total, _ ->
                    assertEquals(1001, total)
                    lastPage = maxOf(lastPage, page)
                },
            )
            assertEquals("PDF conversion stopped before source page 1001", 1001, lastPage)
            assertTrue(result.length() > 0)
            ZipFile(result).use { archive ->
                val entries = archive.entries().asSequence().toList()
                assertEquals(1001, entries.count {
                    it.name.startsWith("OEBPS/text/") && it.name.endsWith(".xhtml")
                })
                assertEquals(1001, entries.count {
                    it.name.startsWith("OEBPS/images/") && it.name.endsWith(".jpg")
                })
                assertNotNull(archive.getEntry("OEBPS/text/s01001.xhtml"))
                assertNotNull(archive.getEntry("OEBPS/package.opf"))
                assertNotNull(archive.getEntry("OEBPS/nav.xhtml"))
            }
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test fun mixedModeKeepsBlankAndVectorPages() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.cacheDir, "nooir-pdf-mixed-" + System.nanoTime())
        assertTrue(folder.mkdirs())
        try {
            val source = File(folder, "mixed.pdf")
            val book = PdfDocument()
            try {
                val pen = Paint().apply { color = Color.BLACK; strokeWidth = 2f }
                for (n in 1..5) {
                    val page = book.startPage(PdfDocument.PageInfo.Builder(120, 160, n).create())
                    page.canvas.drawColor(Color.WHITE)
                    if (n % 2 == 1) page.canvas.drawLine(10f, 12f, 110f, 148f, pen)
                    book.finishPage(page)
                }
                source.outputStream().use { book.writeTo(it) }
            } finally { book.close() }

            val result = File(folder, "mixed.epub")
            var lastPage = 0
            NooirPdfToEpub.convert(context, source, result,
                mode = NooirPdfToEpub.Mode.MIXED,
                epubFormat = ReflowEpubFormat.EPUB3,
                onPage = { page, total, _ ->
                    assertEquals(5, total)
                    lastPage = maxOf(lastPage, page)
                })
            assertEquals(5, lastPage)
            ZipFile(result).use { archive ->
                val entries = archive.entries().asSequence().toList()
                assertEquals(5, entries.count {
                    it.name.startsWith("OEBPS/text/") && it.name.endsWith(".xhtml")
                })
                assertEquals(5, entries.count {
                    it.name.startsWith("OEBPS/images/") && it.name.endsWith(".jpg")
                })
                for (n in 1..5) {
                    val name = "OEBPS/text/s" + n.toString().padStart(5, '0') + ".xhtml"
                    val entry = archive.getEntry(name)
                    assertNotNull("Missing page " + n, entry)
                    val markup = archive.getInputStream(entry).bufferedReader().use { it.readText() }
                    assertTrue("Lost PDF page " + n, markup.contains("PDF page " + n + " (image content)"))
                }
            }
        } finally { folder.deleteRecursively() }
    }

    @Test fun mixedTextLayerKeeps257SelectablePagesInOrder() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.cacheDir, "nooir-pdf-text-" + System.nanoTime())
        assertTrue(folder.mkdirs())
        try {
            val source = File(folder, "text-257.pdf")
            // Generate the test PDF with Android's own PDF writer. The minified
            // FOSS app need not keep PDFBox's public document-authoring APIs
            // solely for an independently compiled instrumentation test.
            val book = PdfDocument()
            try {
                val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    textSize = 12f
                }
                for (n in 1..257) {
                    val page = book.startPage(
                        PdfDocument.PageInfo.Builder(400, 550, n).create())
                    page.canvas.drawColor(Color.WHITE)
                    page.canvas.drawText(
                        "PX" + n.toString().padStart(4, '0') +
                            " This PDF page contains real selectable text for reliable conversion.",
                        18f, 48f, pen)
                    book.finishPage(page)
                }
                source.outputStream().use { book.writeTo(it) }
            } finally { book.close() }
            assertTrue(source.length() > 0L)
            val result = File(folder, "text-257.epub")
            var lastPage = 0
            var textPages = 0
            var imageFallbacks = 0
            NooirPdfToEpub.convert(context, source, result,
                mode = NooirPdfToEpub.Mode.MIXED,
                epubFormat = ReflowEpubFormat.EPUB3,
                onPage = { page, total, stage ->
                    assertEquals(257, total)
                    lastPage = maxOf(lastPage, page)
                    if (stage == "Converting real text and illustrations") textPages++
                    if (stage == "Preserving complex/scanned page as image") imageFallbacks++
                })
            assertEquals("Not all text-layer source pages were visited", 257, lastPage)
            assertEquals("Text-layer pages unexpectedly fell back to images", 257, textPages)
            assertEquals("Extractable text was lost", 0, imageFallbacks)
            ZipFile(result).use { archive ->
                val text = archive.entries().asSequence()
                    .filter { it.name.startsWith("OEBPS/text/") && it.name.endsWith(".xhtml") }
                    .joinToString("\n") { archive.getInputStream(it).bufferedReader().use { reader -> reader.readText() } }
                for (n in 1..257) {
                    val marker = "PX" + n.toString().padStart(4, '0')
                    assertTrue("Missing source text marker " + marker, text.contains(marker))
                    if (n < 257) {
                        val next = "PX" + (n + 1).toString().padStart(4, '0')
                        assertTrue("Source text is not in order", text.indexOf(marker) < text.indexOf(next))
                    }
                }
            }
        } finally { folder.deleteRecursively() }
    }

    @Test fun phoneSaveToAndroidDownloadsAndCancelWithoutDeleting() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val created = mutableListOf<Uri>()
        fun createDownload(): Uri {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME,
                    "nooir-pdf-save-qa-" + System.nanoTime() + ".epub")
                put(MediaStore.MediaColumns.MIME_TYPE, "application/epub+zip")
                // Keep synthetic test artifacts private until deleted.
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Emulator MediaStore Downloads provider refused a test document")
            created += uri
            return uri
        }
        val payload = ByteArray(96 * 1024 + 17) { ((it * 13) % 251).toByte() }
        try {
            // Test an actual Android ContentResolver provider stream, rather
            // than only a ByteArrayOutputStream. We create/delete our own test
            // documents; no real user's chosen SAF file is accessed.
            val completeUri = createDownload()
            resolver.openOutputStream(completeUri, "w")!!.use { dest ->
                assertEquals(payload.size.toLong(), PdfSaveCopy.copy(
                    ByteArrayInputStream(payload), dest, payload.size.toLong()) { false })
            }
            val complete = resolver.openInputStream(completeUri)!!.use { it.readBytes() }
            assertTrue("Provider changed saved EPUB bytes", complete.contentEquals(payload))

            // Cancellation during a copy must propagate, leave only a prefix
            // in the user-selected URI and NEVER delete that URI itself.
            val partialUri = createDownload()
            var stopped = false
            val trigger = object : ByteArrayInputStream(payload) {
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    val n = super.read(b, off, len)
                    if (n > 0) stopped = true
                    return n
                }
            }
            val error = runCatching {
                resolver.openOutputStream(partialUri, "w")!!.use { dest ->
                    PdfSaveCopy.copy(trigger, dest, payload.size.toLong()) { stopped }
                }
            }.exceptionOrNull()
            assertTrue("Interrupted save did not report cancellation", error is CancellationException)
            val partial = resolver.openInputStream(partialUri)!!.use { it.readBytes() }
            assertTrue("Provider did not retain the partial file", partial.size in 1 until payload.size)
            assertTrue("Partial bytes changed", partial.contentEquals(payload.copyOf(partial.size)))
        } finally {
            // The test owns these newly inserted MediaStore URIs.
            // This deletion does NOT occur in the production CreateDocument path.
            created.forEach { runCatching { resolver.delete(it, null, null) } }
        }
    }

    @Test fun mixedTextLayerPreserves2001PagesWithoutTruncation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.cacheDir, "nooir-pdf-2001-" + System.nanoTime())
        assertTrue(folder.mkdirs())
        try {
            // >2,000 real Android-authored, selectable-text PDF pages. Unlike
            // the tiny original-layout fixture, this exercises PDFBox's
            // per-page text extraction over a long page tree.
            val source = File(folder, "selectable-2001.pdf")
            val pdf = PdfDocument()
            try {
                val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    textSize = 10f
                }
                for (n in 1..2001) {
                    val page = pdf.startPage(
                        PdfDocument.PageInfo.Builder(440, 560, n).create())
                    page.canvas.drawColor(Color.WHITE)
                    page.canvas.drawText(
                        "LONG" + n.toString().padStart(4, '0') +
                            " Each PDF page keeps selectable words and original reading order.",
                        18f, 48f, ink)
                    pdf.finishPage(page)
                }
                source.outputStream().use { pdf.writeTo(it) }
            } finally { pdf.close() }
            assertTrue(source.length() > 0L)

            val output = File(folder, "long-text.epub")
            var visited = 0
            var textPages = 0
            var imageFallbacks = 0
            val start = android.os.SystemClock.elapsedRealtime()
            NooirPdfToEpub.convert(context, source, output,
                mode = NooirPdfToEpub.Mode.MIXED,
                epubFormat = ReflowEpubFormat.EPUB3,
                onPage = { page, total, stage ->
                    assertEquals(2001, total)
                    visited = maxOf(visited, page)
                    if (stage == "Converting real text and illustrations") textPages++
                    if (stage == "Preserving complex/scanned page as image") imageFallbacks++
                })
            val elapsed = android.os.SystemClock.elapsedRealtime() - start
            println("PDF_2001_SELECTABLE_TEXT_CONVERSION_MS=" + elapsed)
            assertEquals("Long PDF was truncated", 2001, visited)
            assertEquals("Selectable text became page images", 2001, textPages)
            assertEquals("Unexpected image fallback", 0, imageFallbacks)
            ZipFile(output).use { zip ->
                val xhtml = zip.entries().asSequence()
                    .filter { it.name.startsWith("OEBPS/text/") && it.name.endsWith(".xhtml") }
                    .joinToString("\n") { zip.getInputStream(it).bufferedReader().use { r -> r.readText() } }
                var last = -1
                for (n in 1..2001) {
                    val marker = "LONG" + n.toString().padStart(4, '0')
                    val next = xhtml.indexOf(marker, last + 1)
                    assertTrue("Missing or out-of-order long PDF page " + n, next > last)
                    last = next
                }
            }
        } finally { folder.deleteRecursively() }
    }
}
