package dev.nooir.mangabridge

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.nooir.bridge.XtcBookPreview
import dev.nooir.bridge.XtcFormat
import dev.nooir.bridge.QuantizedPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * On-device end-to-end test: OPF EPUB spine + actual text + PNG illustration →
 * bitmap pagination → real XTC / XTCH bytes → independent decoder inspection.
 * Uses generated non-copyright test content only; never touches a network.
 */
@RunWith(AndroidJUnit4::class)
class NooirEpubXtcInstrumentedTest {
    /**
     * Visual QA proof: these PNGs come from the independent XTC/XTCH decoder,
     * never from the Android canvas before conversion. The device-smoke job
     * pulls them into an artifact so page quality can be inspected separately.
     */
    private fun saveDecodedPage(fileName: String, page: QuantizedPage) {
        require(fileName.matches(Regex("[A-Za-z0-9_-]+\\.png"))) {
            "Unsafe preview filename"
        }
        val maxLevel = (1 shl page.depth) - 1
        val argb = IntArray(page.levels.size) { i ->
            val level = (page.levels[i].toInt() and 255).coerceIn(0, maxLevel)
            val gray = level * 255 / maxLevel
            Color.rgb(gray, gray, gray)
        }
        val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.setPixels(argb, 0, page.width, 0, 0, page.width, page.height)
            val directory = File(
                InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
                "nooir_epub_xteink_previews",
            )
            check(directory.isDirectory || directory.mkdirs()) {
                "Could not create decoded EPUB preview directory"
            }
            FileOutputStream(File(directory, fileName)).use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    "Could not save decoded EPUB output preview"
                }
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun makeBook(file: File, rtl: Boolean = false, emphasis: Boolean = false) {
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
        val styledText = if (emphasis) longText.replace(
            "Paragraph 1 contains sample plain text",
            "<strong>Paragraph 1 contains sample plain text</strong>",
        ) else longText
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
            "OEBPS/Text/one.xhtml" to (if (rtl)
                "<html><body><p>مرحبا بالعالم، كتاب جميل للقراءة.</p></body></html>"
                else "<html><body><h1>First Chapter</h1><p>" +
                    styledText + "</p><img src='../Images/figure.png'/>" +
                    "<p>A concluding line after the illustration.</p></body></html>"
            ).toByteArray(),
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

    @Test fun inlineIllustrationsStayBetweenTheirSurroundingWords() {
        val xhtml = """
            <html><body><section><h1>Chapter <em>One</em></h1>
            <p>Before <em>the picture</em><img src="../Images/figure.png"/>
            After <strong>the picture</strong>.</p>
            <p>Another <span>normal paragraph</span>.</p>
            <figure><img src="../Images/second.png"/></figure>
            </section></body></html>
        """.trimIndent()
        assertEquals(listOf(
            "HEADING:Chapter One",
            "TEXT:Before the picture",
            "IMAGE:../Images/figure.png",
            "TEXT:After the picture.",
            "TEXT:Another normal paragraph.",
            "IMAGE:../Images/second.png",
        ), NooirEpubToXtc.readingOrderForDiagnostics(xhtml))
    }

    @Test fun inlineEmphasisSurvivesNestedTagsAndImageBoundary() {
        val xhtml = """
            <html><body><p>Before <strong>bold <em>both</em></strong>
            <img src="../Images/figure.png"/>After <em>soft</em>.</p></body></html>
        """.trimIndent()
        assertEquals(listOf(
            "TEXT:Before bold both", "IMAGE:../Images/figure.png", "TEXT:After soft."
        ), NooirEpubToXtc.readingOrderForDiagnostics(xhtml))
        assertEquals(listOf("1:bold ", "3:both", "2:soft"),
            NooirEpubToXtc.inlineStylesForDiagnostics(xhtml))
    }

    @Test fun inlineEmphasisChangesRealEncodedXtchPage() {
        val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        val plainInput = File.createTempFile("nooir-plain-", ".epub", cache)
        val styledInput = File.createTempFile("nooir-bold-", ".epub", cache)
        val plainOutput = File.createTempFile("nooir-plain-", ".xtch", cache)
        val styledOutput = File.createTempFile("nooir-bold-", ".xtch", cache)
        try {
            makeBook(plainInput)
            makeBook(styledInput, emphasis = true)
            NooirEpubToXtc.convert(plainInput, plainOutput, XtcFormat.XTCH, "X4")
            NooirEpubToXtc.convert(styledInput, styledOutput, XtcFormat.XTCH, "X4")
            val plain = XtcBookPreview(plainOutput).page(1)
            val bold = XtcBookPreview(styledOutput).page(1)
            assertTrue("Semantic bold must change final decoded e-ink pixels",
                !plain.levels.contentEquals(bold.levels))
            saveDecodedPage("X4-xtch-inline-bold.png", bold)
        } finally {
            plainInput.delete()
            styledInput.delete()
            plainOutput.delete()
            styledOutput.delete()
        }
    }

    @Test fun realXtcContainsDecodedTextAndArtPages() = checkFormat(XtcFormat.XTC)
    @Test fun realXtchContainsDecodedTextAndArtPages() = checkFormat(XtcFormat.XTCH)

    @Test fun typographyOptionsChangeActualDecodedXtchPages() {
        val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        val input = File.createTempFile("nooir-epub-style-", ".epub", cache)
        val ordinary = File.createTempFile("nooir-xtch-style-default-", ".xtch", cache)
        val styled = File.createTempFile("nooir-xtch-style-serif-", ".xtch", cache)
        try {
            makeBook(input)
            NooirEpubToXtc.convert(input, ordinary, XtcFormat.XTCH, "X4")
            NooirEpubToXtc.convert(input, styled, XtcFormat.XTCH, "X4",
                settings = NooirEpubToXtc.Settings(
                    fontPx = 32, marginPx = 20,
                    lineSpacing = 1.35f, fontFamily = "serif",
                ))
            val originalPage = XtcBookPreview(ordinary).page(1)
            val styledPage = XtcBookPreview(styled).page(1)
            assertEquals(480, styledPage.width)
            assertEquals(800, styledPage.height)
            assertEquals(2, styledPage.depth)
            assertTrue("Typography settings must change decoded pixels, not just UI labels",
                !originalPage.levels.contentEquals(styledPage.levels))
            assertTrue("Styled prose page should retain readable ink",
                styledPage.levels.any { it.toInt() == 0 })
            saveDecodedPage("X4-xtch-serif-large.png", styledPage)
        } finally {
            input.delete()
            ordinary.delete()
            styled.delete()
        }
    }

    @Test fun arabicParagraphsAlignRightAndMarkBookRtl() {
        val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        val input = File.createTempFile("nooir-epub-arabic-", ".epub", cache)
        val output = File.createTempFile("nooir-xtc-arabic-", ".xtc", cache)
        try {
            makeBook(input, rtl = true)
            NooirEpubToXtc.convert(input, output, XtcFormat.XTC, "X4", rtl = true)
            RandomAccessFile(output, "r").use { header ->
                header.seek(8L)
                assertEquals("XTC reading-order flag", 1, header.readUnsignedByte())
            }
            val prose = XtcBookPreview(output).page(1) // page zero is cover
            saveDecodedPage("X4-xtc-arabic.png", prose)
            var leftInk = 0
            var rightInk = 0
            for (y in 26 until 155) for (x in 30 until 450) {
                if (prose.levels[y * prose.width + x].toInt() == 0) {
                    if (x < 160) leftInk++
                    if (x > 320) rightInk++
                }
            }
            assertTrue("Arabic glyphs should render on the right/leading edge",
                rightInk > leftInk * 2 + 20)
        } finally {
            input.delete()
            output.delete()
        }
    }

    @Test fun x3RasterPagesHaveTheirOwnResolutionAndDecodableGrayPlanes() {
        val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        val input = File.createTempFile("nooir-epub-x3-", ".epub", cache)
        val output = File.createTempFile("nooir-epub-x3-", ".xtch", cache)
        try {
            makeBook(input)
            val count = NooirEpubToXtc.convert(input, output, XtcFormat.XTCH, "X3")
            val finished = XtcBookPreview(output)
            assertEquals(count, finished.pageCount)
            assertTrue(count >= 3)
            saveDecodedPage("X3-xtch-cover.png", finished.page(0))
            saveDecodedPage("X3-xtch-prose.png", finished.page(1))
            for (index in listOf(0, 1, count - 1)) {
                val page = finished.page(index)
                assertEquals(528, page.width)
                assertEquals(792, page.height)
                assertEquals(2, page.depth)
                assertTrue(page.levels.any { it.toInt() == 0 })
            }
        } finally {
            input.delete()
            output.delete()
        }
    }

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
            // Two OPF spine sections must survive as 1-based XTC chapter
            // entries. The separate cover occupies actual page index zero.
            RandomAccessFile(output, "r").use { book ->
                val header = ByteArray(56).also { book.readFully(it) }
                val offset = (0..3).fold(0L) { total, i ->
                    total or ((header[48 + i].toLong() and 255L) shl (i * 8))
                }
                assertTrue("Writer must keep chapter index", offset >= 56L)
                val first = ByteArray(96)
                val second = ByteArray(96)
                book.seek(offset)
                book.readFully(first)
                book.readFully(second)
                assertTrue(first.toString(Charsets.UTF_8).startsWith("First Chapter"))
                assertTrue(second.toString(Charsets.UTF_8).startsWith("Second Chapter"))
                val firstIndex = (first[80].toInt() and 255) or
                    ((first[81].toInt() and 255) shl 8)
                val secondIndex = (second[80].toInt() and 255) or
                    ((second[81].toInt() and 255) shl 8)
                assertEquals("Cover precedes first chapter", 2, firstIndex)
                assertTrue("Spine order must be monotonic", secondIndex > firstIndex)
            }
            val cover = decoded.page(0)
            assertEquals(480, cover.width)
            assertEquals(800, cover.height)
            assertEquals(format.depth, cover.depth)
            assertTrue("Real cover contains dark and light pixels",
                cover.levels.any { it.toInt() == 0 } &&
                    cover.levels.any { (it.toInt() and 255) == if (format.depth == 1) 1 else 3 })
            val prose = decoded.page(1)
            val label = if (format == XtcFormat.XTC) "xtc" else "xtch"
            saveDecodedPage("X4-" + label + "-cover.png", cover)
            saveDecodedPage("X4-" + label + "-prose.png", prose)
            saveDecodedPage("X4-" + label + "-last.png", decoded.page(count - 1))
            assertTrue("Rendered chapter text must not be all white",
                prose.levels.any { it.toInt() == 0 })
        } finally {
            input.delete()
            output.delete()
        }
    }
}
