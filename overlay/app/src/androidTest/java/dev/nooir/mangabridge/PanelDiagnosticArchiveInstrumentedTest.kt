package dev.nooir.mangabridge

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Android-device verification, not an image-only host fixture: decodes a real
 * CBZ through ContentResolver -> AndroidPageSource -> detector -> quantizer,
 * saves the ZIP privately, reopens it and checks exact source/frame mapping.
 * Artwork is generated geometry; no manga copyright material is bundled.
 */
@RunWith(AndroidJUnit4::class)
class PanelDiagnosticArchiveInstrumentedTest {
    private fun image(right: Boolean): ByteArray {
        val bitmap = Bitmap.createBitmap(480, 800, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.WHITE)
            val canvas = Canvas(bitmap)
            val paint = Paint().apply { color = Color.rgb(90, 90, 90) }
            // An artificial two-column page separated by a white gutter.
            canvas.drawRect(Rect(15, 20, 220, 360), paint)
            canvas.drawRect(Rect(250, 20, 465, 360), paint)
            paint.color = if (right) Color.rgb(120, 120, 120) else Color.rgb(140, 140, 140)
            canvas.drawRect(Rect(15, 410, 465, 770), paint)
            val out = ByteArrayOutputStream()
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out))
            return out.toByteArray()
        } finally { bitmap.recycle() }
    }

    private fun scratch(context: Context, key: String): File =
        File(context.cacheDir, "nooir-panel-instrumented-$key-${System.nanoTime()}")
            .apply { check(mkdirs()) }

    private fun makeCbz(folder: File): File {
        val cbz = File(folder, "synthetic-sources.cbz")
        ZipOutputStream(cbz.outputStream()).use { zip ->
            // Intentionally add entries out of order; parser must use natural order.
            for ((name, data) in listOf("page10.png" to image(false), "page2.png" to image(true))) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(data)
                zip.closeEntry()
            }
        }
        return cbz
    }

    /**
     * The host detector can safely join a short terminal ruled footer, but
     * that alone does not prove Android will respect the decision. Landscape
     * output is intentionally used here: the old PAGED fallback would cut
     * this portrait source into several vertically paginated screens.
     *
     * This is fully synthetic gray geometry, not copyrighted manga artwork.
     */
    @Test fun shortRuledFooterStaysOneFrameThroughAndroidRender() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val temp = scratch(context, "merged-footer")
        try {
            val bitmap = Bitmap.createBitmap(480, 800, Bitmap.Config.ARGB_8888)
            val input = File(temp, "ruled-footer.png")
            try {
                bitmap.eraseColor(Color.WHITE)
                val canvas = Canvas(bitmap)
                val ink = Paint().apply { color = Color.rgb(100, 100, 100) }
                canvas.drawRect(Rect(12, 12, 468, 720), ink)
                ink.color = Color.rgb(30, 30, 30)
                canvas.drawRect(Rect(30, 768, 451, 772), ink)
                ByteArrayOutputStream().use { bytes ->
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes))
                    input.writeBytes(bytes.toByteArray())
                }
            } finally { bitmap.recycle() }

            val uri = Uri.fromFile(input)
            val pages = AndroidPageSource(context)
            val settings = MangaConversionOptions(
                panelMode = "PANEL_BY_PANEL",
                layout = "PAGED", orientation = "LANDSCAPE",
                rightToLeft = true, cropWhiteMargins = true,
                overlapPercent = 8, showFullPageBeforeSplits = true,
                keepChapterFirstPageWhole = false, panelDiagnostics = true,
            )
            // Proof that an unprotected landscape PAGED full source would
            // really create unwanted extra slices, not merely one page.
            val unsafeBaseline = pages.pages(listOf(uri), 480, 800, 1,
                settings.copy(panelMode = "OFF", panelDiagnostics = false,
                    cropWhiteMargins = false)).toList()
            assertTrue("Fixture must expose the landscape pagination trap",
                unsafeBaseline.size > 1)

            val archiveDir = scratch(context, "merged-footer-diagnostics")
            try {
                val diagnostic = PanelDiagnosticArchive(archiveDir)
                val exported = pages.pages(listOf(uri), 480, 800, 1,
                    settings, diagnostics = diagnostic).toList()
                assertEquals("Merged footer must never become a blank reading frame",
                    1, exported.size)
                assertEquals(480, exported[0].width)
                assertEquals(800, exported[0].height)
                assertTrue("The rendered frame must still contain artwork",
                    exported[0].levels.any { (it.toInt() and 255) == 0 })

                val status = diagnostic.publish(context)
                assertTrue(status, status.startsWith("Ready:"))
                ZipFile(PanelDiagnosticArchive.latestFile(context)).use { zip ->
                    val report = JSONObject(zip.getInputStream(zip.getEntry("pages/00001.json"))
                        .bufferedReader().use { it.readText() })
                    assertTrue("Detector must join the short footer itself",
                        report.getBoolean("detectorPreserveSingleFrame"))
                    assertFalse("Joined footer is NOT an uncertain detector fallback",
                        report.getBoolean("detectorFallback"))
                    assertEquals(1, report.getInt("outputFrameCount"))
                    assertEquals(1, report.getJSONArray("preOverviewSlicesSource").length())
                    assertEquals(1, report.getJSONArray("outputSlicesSource").length())
                    val rect = report.getJSONArray("outputSlicesSource").getJSONArray(0)
                    assertEquals(listOf(0, 0, 480, 800),
                        (0..3).map { rect.getInt(it) })
                    assertTrue("Landscape Auto Fit is not required for this manual mode",
                        report.getJSONArray("outputRotated").getBoolean(0))
                }
            } finally { archiveDir.deleteRecursively() }
        } finally { temp.deleteRecursively() }
    }

    @Test fun realAndroidCbzZipAndRenderParity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val temp = scratch(context, "cbz")
        try {
            val source = AndroidPageSource(context)
            val cbz = makeCbz(temp)
            val options = MangaConversionOptions(
                panelMode = "PANEL_BY_PANEL", panelDiagnostics = true,
                orientation = "AUTO_FIT", rightToLeft = true,
                showFullPageBeforeSplits = true,
                keepChapterFirstPageWhole = false,
                overlapPercent = 8,
            )
            val uri = Uri.fromFile(cbz)
            for (depth in listOf(1, 2)) {
                val plain = source.pages(listOf(uri), 480, 800, depth, options.copy(panelDiagnostics = false))
                    .toList()
                val archiveDir = scratch(context, "zip-$depth")
                try {
                    val debug = PanelDiagnosticArchive(archiveDir)
                    debug.setHasSeparateCover(true)
                    val traced = source.pages(listOf(uri), 480, 800, depth, options,
                        diagnostics = debug, chapterOrdinal = 3).toList()
                    assertEquals("Diagnostics cannot add or remove book frames", plain.size, traced.size)
                    for (index in plain.indices) {
                        assertEquals(plain[index].width, traced[index].width)
                        assertEquals(plain[index].height, traced[index].height)
                        assertEquals(plain[index].depth, traced[index].depth)
                        assertArrayEquals("book pixels changed for depth $depth, frame $index",
                            plain[index].levels, traced[index].levels)
                    }
                    val status = debug.publish(context)
                    assertTrue(status, status.startsWith("Ready:"))
                    val latest = PanelDiagnosticArchive.latestFile(context)
                    assertTrue(latest.isFile)
                    ZipFile(latest).use { zip ->
                        val summary = JSONObject(zip.getInputStream(zip.getEntry("summary.json"))
                            .bufferedReader().use { it.readText() })
                        assertTrue(summary.getBoolean("complete"))
                        assertEquals(2, summary.getInt("sourcePageCount"))
                        assertEquals(1, summary.getInt("separateCoverFrameCount"))
                        assertEquals(traced.size, summary.getInt("outputFrameCountExcludingCover"))
                        assertEquals(traced.size + 1, summary.getInt("outputFrameCountIncludingCover"))
                        var nextFrame = 2
                        for (n in 1..2) {
                            val id = n.toString().padStart(5, '0')
                            val page = JSONObject(zip.getInputStream(zip.getEntry("pages/$id.json"))
                                .bufferedReader().use { it.readText() })
                            assertEquals(3, page.getInt("chapterOrdinal"))
                            assertEquals(n, page.getInt("chapterPageOrdinal"))
                            assertEquals(n, page.getInt("sourceOrdinal"))
                            assertEquals(480, page.getInt("thumbWidth"))
                            assertEquals(800, page.getInt("thumbHeight"))
                            assertTrue(page.getBoolean("rtl"))
                            assertTrue(page.getBoolean("showFullPageBeforeSplits"))
                            assertEquals(nextFrame, page.getInt("outputFrameStartOneBased"))
                            val count = page.getInt("outputFrameCount")
                            assertTrue(count >= 1)
                            assertEquals(count, page.getJSONArray("outputSlicesSource").length())
                            assertEquals(count, page.getJSONArray("outputRotated").length())
                            assertFalse(page.has("mangaTitle"))
                            assertFalse(page.has("sourceUrl"))
                            val thumb = BitmapFactory.decodeStream(
                                zip.getInputStream(zip.getEntry("thumbnails/$id.png")))
                            assertNotNull(thumb)
                            try {
                                assertEquals(480, thumb.width)
                                assertEquals(800, thumb.height)
                                // The detector observes unquantized source pixels.
                                assertEquals(Color.rgb(90, 90, 90), thumb.getPixel(80, 80))
                                assertEquals(Color.WHITE, thumb.getPixel(235, 100))
                                // page2.png must precede page10.png regardless of ZIP insertion order.
                                val expectedGray = if (n == 1) 120 else 140
                                assertEquals(Color.rgb(expectedGray, expectedGray, expectedGray),
                                    thumb.getPixel(80, 500))
                            } finally { thumb.recycle() }
                            nextFrame += count
                        }
                        assertEquals(traced.size + 2, nextFrame)
                    }
                    // A partial second diagnostic MUST NOT replace a good one.
                    val goodBytes = latest.readBytes()
                    val incompleteDir = scratch(context, "incomplete-$depth")
                    try {
                        val bad = PanelDiagnosticArchive(incompleteDir)
                        val sourceBitmap = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
                        try {
                            assertNotNull(bad.beginPage(1, 0, sourceBitmap))
                            assertTrue(bad.publish(context).contains("incomplete"))
                        } finally { sourceBitmap.recycle() }
                        assertArrayEquals(goodBytes, latest.readBytes())
                    } finally { incompleteDir.deleteRecursively() }
                } finally { archiveDir.deleteRecursively() }
            }
        } finally { temp.deleteRecursively() }
    }
}
