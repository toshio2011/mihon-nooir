package dev.nooir.mangabridge

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.nooir.bridge.ReflowEpubFormat
import dev.nooir.bridge.ReflowEpubStyle
import dev.nooir.bridge.ReflowEpubWriter
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Offline public-HTML → actual EPUB ZIP → packaged XHTML/navigation round-trip.
 * This exercises the production article extractor and reflow writer on Android,
 * not just a URL/TOC planner or a mock converter. No network, private page
 * content, screenshots or protected third-party books.
 */
@RunWith(AndroidJUnit4::class)
class NooirWebEpubEndToEndInstrumentedTest {
    private val chapterOne = """
        <html lang="en"><head><title>Public Tiny Novel</title></head><body>
        <header>Website navigation and other links that are not book text.</header>
        <article class="chapter-content">
          <h1>Chapter One</h1>
          <p>The little observatory overlooked the town. Everyone looked through its
          windows to watch the lights move across the quiet evening sky.</p>
          <p>There was a folded note waiting on the table, and a reader opened it.</p>
          <aside>Unrelated reading recommendation</aside>
          <div class="advertisement">Buy our unrelated advertising products</div>
        </article></body></html>
    """.trimIndent()
    private val chapterTwo = """
        <html lang="en"><head><title>Public Tiny Novel</title></head><body>
        <article class="chapter-content">
          <h1>Chapter Two</h1>
          <p>A second page carried the story onward. The next morning brought the
          promise of a long journey and several new chapters to discover.</p>
          <p>Nothing needed a private account or a live network request.</p>
        </article></body></html>
    """.trimIndent()

    @Test fun wattpadBookCoverAndSynopsisArePackagedInEpub2And3() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = Bitmap.createBitmap(32, 48, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(100, 80, 160))
        val png = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            .toByteArray()
        bitmap.recycle()
        for (format in listOf(ReflowEpubFormat.EPUB2, ReflowEpubFormat.EPUB3)) {
            val output = File.createTempFile("nooir-wattpad-cover-", ".epub", context.cacheDir)
            try {
                val storyTitle = "The Lantern Observatory"
                val synopsis = "A small town kept its lights on.\n\nThe keeper waited for dawn."
                ReflowEpubWriter.write(
                    output,
                    dev.nooir.bridge.ReflowMetadata(storyTitle, "Example Novelist", synopsis, "en"),
                    format,
                ) { book ->
                    NooirWebCover.embedImage(
                        book, ReflowEpubWriter.ImageAsset(png, "png", "image/png"))
                    NooirWebNovel.addSynopsis(book, synopsis)
                    book.addSection("Part One", "<h1>Part One</h1><p>A safe synthetic chapter.</p>")
                }
                ZipFile(output).use { zip ->
                    val opf = read(zip, "OEBPS/package.opf")
                    assertTrue(opf.contains(storyTitle))
                    assertTrue(opf.contains("Example Novelist"))
                    assertTrue(opf.contains("<dc:description>A small town kept its lights on."))
                    assertTrue(opf.contains("<dc:description>"))
                    assertTrue(opf.contains("The keeper waited for dawn."))
                    assertTrue(zip.getEntry("OEBPS/images/img00001.png") != null)
                    assertEquals(3, Regex("<itemref\\b").findAll(opf).count())
                    assertTrue(read(zip, "OEBPS/text/s00001.xhtml").contains("Book cover"))
                    val about = read(zip, "OEBPS/text/s00002.xhtml")
                    assertTrue(about.contains("<h1>Synopsis</h1>"))
                    assertTrue(about.contains("The keeper waited for dawn."))
                    val chapter = read(zip, "OEBPS/text/s00003.xhtml")
                    assertTrue(chapter.contains("A safe synthetic chapter."))
                    if (format == ReflowEpubFormat.EPUB2) {
                        assertTrue(opf.contains("<meta name=\"cover\""))
                        assertTrue(read(zip, "OEBPS/toc.ncx").contains("Synopsis"))
                    } else {
                        assertTrue(opf.contains("properties=\"cover-image\""))
                        assertTrue(read(zip, "OEBPS/nav.xhtml").contains("Synopsis"))
                    }
                }
            } finally { output.delete() }
        }
    }

    @Test fun actualHtmlExportsValidEpub2SectionsAndNcx() =
        verify(ReflowEpubFormat.EPUB2)

    @Test fun actualHtmlExportsValidEpub3SectionsAndNavigation() =
        verify(ReflowEpubFormat.EPUB3)

    private fun verify(format: ReflowEpubFormat) {
        val output = File.createTempFile("nooir-public-html-", ".epub",
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir)
        try {
            val one = Jsoup.parse(chapterOne, "https://example.org/story/chapter/1")
            val two = Jsoup.parse(chapterTwo, "https://example.org/story/chapter/2")
            ReflowEpubWriter.write(
                output,
                NooirWebArticle.metadata(one).copy(
                    title = "Original Tiny Public Story",
                    author = "Nooir QA",
                ),
                format,
            ) { book ->
                book.setReadingStyle(ReflowEpubStyle.COMPACT)
                NooirWebArticle.appendChapter(one, book, "Chapter One", maxImages = 0)
                NooirWebArticle.appendChapter(two, book, "Chapter Two", maxImages = 0)
            }
            assertTrue("A real EPUB file was written", output.length() > 1000L)
            ZipFile(output).use { zip ->
                val mimetype = zip.entries().asSequence().first()
                assertEquals("mimetype", mimetype.name)
                assertEquals(ZipEntry.STORED, mimetype.method)
                assertEquals("application/epub+zip", read(zip, mimetype.name))
                val opf = read(zip, "OEBPS/package.opf")
                assertTrue(opf.contains("Original Tiny Public Story"))
                assertTrue(opf.contains("Nooir QA"))
                assertEquals(2, Regex("<itemref\\b").findAll(opf).count())
                val a = read(zip, "OEBPS/text/s00001.xhtml")
                val b = read(zip, "OEBPS/text/s00002.xhtml")
                assertTrue(a.contains("Chapter One"))
                assertTrue(a.contains("The little observatory overlooked the town"))
                assertTrue(b.contains("Chapter Two"))
                assertTrue(b.contains("A second page carried the story onward"))
                assertFalse("Advertisements must be stripped", a.contains("advertising products"))
                assertFalse("Site navigation must be stripped", a.contains("Website navigation"))
                if (format == ReflowEpubFormat.EPUB2) {
                    val ncx = read(zip, "OEBPS/toc.ncx")
                    assertTrue(ncx.indexOf("Chapter One") < ncx.indexOf("Chapter Two"))
                    assertTrue(opf.contains("version=\"2.0\""))
                } else {
                    val nav = read(zip, "OEBPS/nav.xhtml")
                    assertTrue(nav.indexOf("Chapter One") < nav.indexOf("Chapter Two"))
                    assertTrue(opf.contains("version=\"3.0\""))
                }
            }
        } finally {
            output.delete()
        }
    }

    private fun read(zip: ZipFile, path: String): String =
        zip.getInputStream(zip.getEntry(path)
            ?: error("Missing EPUB ZIP entry: " + path)).bufferedReader().use { it.readText() }
}
