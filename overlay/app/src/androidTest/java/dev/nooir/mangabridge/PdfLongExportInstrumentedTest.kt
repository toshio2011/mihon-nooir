package dev.nooir.mangabridge

import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.nooir.bridge.ReflowEpubFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
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
            PDFBoxResourceLoader.init(context.applicationContext)
            val source = File(folder, "text-257.pdf")
            PDDocument().use { book ->
                for (n in 1..257) {
                    val page = PDPage(PDRectangle.LETTER)
                    book.addPage(page)
                    PDPageContentStream(book, page).use { content ->
                        content.beginText()
                        content.setFont(PDType1Font.HELVETICA, 11f)
                        content.newLineAtOffset(36f, 740f)
                        content.showText("PX" + n.toString().padStart(4, '0') +
                            " This PDF page contains real selectable text for reliable large book conversion.")
                        content.endText()
                    }
                }
                book.save(source)
            }
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
}
