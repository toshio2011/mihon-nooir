package dev.nooir.mangabridge

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.nooir.bridge.XtcBookPreview
import dev.nooir.bridge.XtcFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * On-device end-to-end test: OPF EPUB spine + actual text + PNG illustration →
 * bitmap pagination → real XTC / XTCH bytes → independent decoder inspection.
 * Uses generated non-copyright test content only; never touches a network.
 */
@RunWith(AndroidJUnit4::class)
class NooirEpubXtcInstrumentedTest {
    private fun makeBook(file: File) {
        val image = Bitmap.createBitmap(100, 120, Bitmap.Config.ARGB_8888)
        val png = try {
            image.eraseColor(Color.WHITE)
            for (y in 20..99) for (x in 12..87) image.setPixel(x, y, Color.BLACK)
            ByteArrayOutputStream().use {
                check(image.compress(Bitmap.CompressFormat.PNG, 100, it))
                it.toByteArray()
            }
        } finally { image.recycle() }
        val longText = (1..30).joinToString(" ") { n ->
            "Paragraph " + n + " contains sample plain text for proper EPUB pagination. "
        }
        val resources = linkedMapOf(
            "mimetype" to "application/epub+zip".toByteArray(),
            "META-INF/container.xml" to ("""
                <?xml version="1.0" encoding="UTF-8"?>
                <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles><rootfile full-path="OEBPS/content.opf"
                     media-type="application/oebps-package+xml"/></rootfiles>
                </container>
            """.trimIndent()).toByteArray(),
            "OEBPS/content.opf" to ("""
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf"
                  xmlns:dc="http://purl.org/dc/elements/1.1/" version="3.0">
                  <metadata><dc:title>Original Tiny Test</dc:title>
                    <dc:creator>Nooir QA</dc:creator></metadata>
                  <manifest>
                    <item id="a" href="Text/one.xhtml" media-type="application/xhtml+xml"/>
                    <item id="b" href="Text/two.xhtml" media-type="application/xhtml+xml"/>
                    <item id="cover" href="Images/cover.png" media-type="image/png" properties="cover-image"/>
                    <item id="figure" href="Images/figure.png" media-type="image/png"/>
                  </manifest>
                  <spine><itemref idref="a"/><itemref idref="b"/></spine>
                </package>
            """.trimIndent()).toByteArray(),
            "OEBPS/Text/one.xhtml" to ("<html><body><h1>First Chapter</h1><p>" +
                longText + "</p><img src='../Images/figure.png'/>" +
                "<p>A concluding line after the illustration.</p></body></html>").toByteArray(),
            "OEBPS/Text/two.xhtml" to
                "<html><body><h1>Second Chapter</h1><p>Last paragraph.</p></body></html>".toByteArray(),
            "OEBPS/Images/cover.png" to png,
            "OEBPS/Images/figure.png" to png,
        )
        ZipOutputStream(file.outputStream()).use { zip ->
            for ((name, bytes) in resources) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    @Test fun realXtcContainsDecodedTextAndArtPages() = checkFormat(XtcFormat.XTC)
    @Test fun realXtchContainsDecodedTextAndArtPages() = checkFormat(XtcFormat.XTCH)

    private fun checkFormat(format: XtcFormat) {
        val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        val input = File.createTempFile("nooir-epub-instrumentation-", ".epub", cache)
        val output = File.createTempFile("nooir-epub-book-", "." + format.extension, cache)
        try {
            makeBook(input)
            val reported = mutableListOf<Int>()
            val count = NooirEpubToXtc.convert(input, output, format, "X4",
                onPage = { page, _ -> reported += page })
            val decoded = XtcBookPreview(output)
            assertEquals(format, decoded.format)
            assertEquals(count, decoded.pageCount)
            assertTrue("Cover, prose and illustration should span several pages", count >= 3)
            assertEquals(count, reported.lastOrNull())
            val cover = decoded.page(0)
            assertEquals(480, cover.width)
            assertEquals(800, cover.height)
            assertEquals(format.depth, cover.depth)
            assertTrue("Real cover contains dark and light pixels",
                cover.levels.any { it.toInt() == 0 } &&
                    cover.levels.any { (it.toInt() and 255) == if (format.depth == 1) 1 else 3 })
            val prose = decoded.page(1)
            assertTrue("Rendered chapter text must not be all white",
                prose.levels.any { it.toInt() == 0 })
        } finally {
            input.delete()
            output.delete()
        }
    }
}
