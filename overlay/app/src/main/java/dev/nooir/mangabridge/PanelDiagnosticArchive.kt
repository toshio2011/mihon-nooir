package dev.nooir.mangabridge

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import dev.nooir.bridge.PageRegion
import dev.nooir.bridge.PanelDebugEvent
import dev.nooir.bridge.PanelFitSlice
import dev.nooir.bridge.PanelPlan
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.zip.ZipFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Private, explicit opt-in export diagnostic. Never uploaded to a server and
 * never included in EPUB/XTC/XTCH/CBZ. Stores the EXACT byte-for-byte grayscale
 * detector thumbnail, both coordinate systems and the actual render decisions.
 * Optionally preserves bounded ORIGINAL compressed page bytes for suspicious
 * pages to investigate resizing/padding artefacts.
 *
 * No manga title, chapter name, URI or source URL is written into this archive.
 * Users must explicitly export the ZIP from the dialog to share these images.
 */
class PanelDiagnosticArchive(workDir: File) : AutoCloseable {
    companion object {
        private const val MAX_PAGES = 3000
        private const val MAX_TRACE_EVENTS = 384
        private const val MAX_ORIGINAL_PAGES = 24
        private const val MAX_ORIGINAL_SIZE = 8 * 1024 * 1024
        private const val MAX_ORIGINAL_TOTAL = 80L * 1024 * 1024
        private const val MAX_ZIP_SIZE = 120L * 1024 * 1024
        fun latestFile(context: Context): File =
            File(context.filesDir, "manganooir_panel_debug/latest.zip")
    }

    // Enforce the bound on compressed bytes actually written, including ZIP headers
    // and the final directory. File.length() alone lags buffered writes.
    private class CappedOutputStream(output: OutputStream, private val limit: Long) :
        FilterOutputStream(output) {
        var written = 0L
            private set

        override fun write(value: Int) {
            if (written >= limit) throw IOException("Diagnostic ZIP size limit reached")
            out.write(value)
            written++
        }

        override fun write(bytes: ByteArray, offset: Int, count: Int) {
            if (count < 0 || count.toLong() > limit - written)
                throw IOException("Diagnostic ZIP size limit reached")
            out.write(bytes, offset, count)
            written += count
        }
    }

    private val file = File(workDir, "panel-diagnostics.zip")
    private val bounded = CappedOutputStream(BufferedOutputStream(file.outputStream()), MAX_ZIP_SIZE)
    private val zip = ZipOutputStream(bounded)
    private var sourceCount = 0
    private var completedPages = 0
    private var outputCount = 0
    private var coverCount = 0
    private var originals = 0
    private var originalBytes = 0L
    private var closed = false
    var error: String? = null
        private set

    init {
        entry("README.txt") {
            write((
                "MihonNooir panel diagnostic ZIP v1\n" +
                "Only created when the user enables Panel diagnostics in conversion settings.\n" +
                "Contains source manga artwork: DO NOT share publicly without permission.\n" +
                "No OCR or AI: the detector analyzes grayscale thumbnail pixels.\n" +
                "thumbnails/*.png contains the EXACT grayscale pixels sent to the detector (PNG is lossless).\n" +
                "summary.json contains the completed source and output frame counts.\n" +
                "pages/*.json contains chapter/page ordinals (not titles), source dimensions,\n" +
                "detector regions, padding, accepted/rejected tier reasons, final render regions,\n" +
                "and one-based output frame numbers including any separate cover.\n" +
                "originals/*.source contains selected unmodified original image BYTES, up to\n" +
                "24 pages / 8 MiB each / 80 MiB total. File extensions are intentionally generic.\n" +
                "A page may have no .source entry if it was not suspicious or exceeded the limit.\n" +
                "All pages still have a lossless detector thumbnail, including those without originals.\n" +
                "The JSON mode marks opening pages intentionally kept whole. No device/network IDs.\n" +
                "This ZIP does not affect conversion, and is kept private until exported.\n"
            ).toByteArray(Charsets.UTF_8))
        }
    }

    fun setHasSeparateCover(hasCover: Boolean) {
        coverCount = if (hasCover) 1 else 0
    }

    private fun entry(name: String, writeBody: java.io.OutputStream.() -> Unit) {
        zip.putNextEntry(ZipEntry(name))
        try { zip.writeBody() } finally { zip.closeEntry() }
    }

    fun beginPage(chapter: Int, withinChapter: Int, src: Bitmap): Page? {
        if (error != null || sourceCount >= MAX_PAGES || bounded.written >= MAX_ZIP_SIZE) {
            if (error == null) error = "Diagnostic size/page limit reached; incomplete archive"
            return null
        }
        sourceCount++
        return Page(sourceCount, chapter, withinChapter, src.width, src.height)
    }

    inner class Page(
        private val ordinal: Int,
        private val chapter: Int,
        private val withinChapter: Int,
        private val sourceWidth: Int,
        private val sourceHeight: Int,
    ) {
        private val events = ArrayList<PanelDebugEvent>()
        private var thumbWidth = 0
        private var thumbHeight = 0
        private var gray: ByteArray? = null
        private var plan: PanelPlan? = null
        private var padded: List<PageRegion> = emptyList()
        private var actualMode = "unexamined"
        private var fallback = ""
        val trace: (PanelDebugEvent) -> Unit = { event ->
            if (events.size < MAX_TRACE_EVENTS) events += event
        }

        fun noteSkipped(reason: String) {
            actualMode = "whole-page"
            fallback = reason
            trace(PanelDebugEvent("android", PageRegion(0, 0, sourceWidth, sourceHeight),
                "keep", reason))
        }

        fun noteAnalysis(width: Int, height: Int, bytes: ByteArray,
            detected: PanelPlan, paddedRegions: List<PageRegion>) {
            thumbWidth = width
            thumbHeight = height
            gray = bytes
            plan = detected
            padded = paddedRegions
            actualMode = "detector"
            fallback = detected.reason
        }

        private fun arr(region: PageRegion) = JSONArray()
            .put(region.left).put(region.top).put(region.right).put(region.bottom)

        private fun arrayOfRegions(regions: List<PageRegion>) = JSONArray().also { array ->
            regions.forEach { array.put(arr(it)) }
        }

        private fun grayscale(src: Bitmap) {
            if (gray != null) return
            val scale = minOf(1.0, 600.0 / src.width, 900.0 / src.height)
            val w = (src.width * scale).toInt().coerceAtLeast(1)
            val h = (src.height * scale).toInt().coerceAtLeast(1)
            val thumb = if (w == src.width && h == src.height) src
                else Bitmap.createScaledBitmap(src, w, h, true)
            try {
                val rgb = IntArray(w * h)
                thumb.getPixels(rgb, 0, w, 0, 0, w, h)
                gray = ByteArray(rgb.size) { i ->
                    val c = rgb[i]
                    val a = Color.alpha(c)
                    val lum = (Color.red(c) * 299 + Color.green(c) * 587 +
                        Color.blue(c) * 114) / 1000
                    ((lum * a + 255 * (255 - a)) / 255).toByte()
                }
                thumbWidth = w
                thumbHeight = h
            } finally { if (thumb !== src) thumb.recycle() }
        }

        fun finish(src: Bitmap, options: MangaConversionOptions,
            slices: List<PanelFitSlice>, rendered: List<PanelFitSlice>,
            rawInput: (() -> InputStream)?) {
            if (error != null) return
            try {
                grayscale(src)
                val id = ordinal.toString().padStart(5, '0')
                val bytes = requireNotNull(gray)
                // Store EXACT detector luminance values. PNG is lossless and
                // bounded at <=600x900 pixels, no full-image cache.
                val colors = IntArray(bytes.size) { i ->
                    val y = bytes[i].toInt() and 255
                    Color.rgb(y, y, y)
                }
                val bitmap = Bitmap.createBitmap(colors, thumbWidth, thumbHeight,
                    Bitmap.Config.ARGB_8888)
                try {
                    entry("thumbnails/$id.png") {
                        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, this)) {
                            "Cannot encode detector thumbnail"
                        }
                    }
                } finally { bitmap.recycle() }

                val start = coverCount + outputCount + 1
                outputCount += rendered.size
                val snapshot = JSONObject()
                    .put("schema", 1)
                    .put("sourceOrdinal", ordinal)
                    .put("chapterOrdinal", chapter)
                    .put("chapterPageOrdinal", withinChapter + 1)
                    .put("sourceWidth", sourceWidth)
                    .put("sourceHeight", sourceHeight)
                    .put("thumbWidth", thumbWidth)
                    .put("thumbHeight", thumbHeight)
                    .put("detectorThumbnail", "thumbnails/$id.png")
                    .put("detectorEvaluated", plan != null)
                    .put("mode", options.panelMode)
                    .put("layout", options.layout)
                    .put("orientation", options.orientation)
                    .put("rtl", options.rightToLeft)
                    .put("overlapPercent", options.overlapPercent)
                    .put("cropMargins", options.cropWhiteMargins)
                    .put("showFullPageBeforeSplits", options.showFullPageBeforeSplits)
                    .put("path", actualMode)
                    .put("reason", fallback)
                    .put("detectorFallback", plan?.usedFullPageFallback ?: true)
                    .put("detectorRegionsThumbnail", arrayOfRegions(plan?.regions.orEmpty()))
                    .put("sourceRegionsWithPadding", arrayOfRegions(padded))
                    .put("preOverviewSlicesSource", arrayOfRegions(slices.map { it.region }))
                    .put("outputSlicesSource", arrayOfRegions(rendered.map { it.region }))
                    .put("outputRotated", JSONArray().also { arr ->
                        rendered.forEach { arr.put(it.landscape) }
                    })
                    .put("outputFrameStartOneBased", start)
                    .put("outputFrameCount", rendered.size)
                    .put("events", JSONArray().also { eventArray ->
                        events.forEach { event ->
                            eventArray.put(JSONObject().put("stage", event.stage)
                                .put("region", arr(event.region))
                                .put("decision", event.decision)
                                .put("detail", event.detail))
                        }
                    })

                // Preserve original *compressed source bytes* for high-risk
                // pages, not just a recreation from the rendered EPUB.
                // Always cap memory, file size and count; never commit them.
                val suspected = slices.size >= 4 ||
                    events.any { it.detail.contains("sparse", true) ||
                        it.detail.contains("border", true) ||
                        it.decision == "reject" }
                if (suspected && rawInput != null && originals < MAX_ORIGINAL_PAGES &&
                    originalBytes < MAX_ORIGINAL_TOTAL) {
                    // Some SAF providers cannot reopen an image stream.
                    // An unavailable optional original MUST NOT invalidate
                    // the exact detector thumbnail or the useful trace ZIP.
                    val data = runCatching {
                        ByteArrayOutputStream().also { output ->
                            rawInput().use { input ->
                                val buffer = ByteArray(32 * 1024)
                                while (output.size() <= MAX_ORIGINAL_SIZE) {
                                    val n = input.read(buffer)
                                    if (n < 0) break
                                    if (n > 0) output.write(buffer, 0, n)
                                }
                            }
                        }
                    }.getOrNull()
                    when {
                        data == null -> snapshot.put("originalImageSkipped", "source cannot be reopened")
                        data.size() > MAX_ORIGINAL_SIZE ||
                            originalBytes + data.size() > MAX_ORIGINAL_TOTAL ->
                            snapshot.put("originalImageSkipped", "size limit")
                        else -> {
                            entry("originals/$id.source") { write(data.toByteArray()) }
                            originals++
                            originalBytes += data.size()
                            snapshot.put("originalImage", "originals/$id.source")
                            snapshot.put("originalBytes", data.size())
                        }
                    }
                }
                entry("pages/$id.json") { write(snapshot.toString(2).toByteArray(Charsets.UTF_8)) }
                completedPages++
            } catch (e: Exception) {
                error = "Diagnostic capture failed: " + e.javaClass.simpleName
            }
        }
    }

    /** Writes only a complete, verifiable ZIP to app-private storage.
     * The last successful archive stays intact if validation or replacement fails.
     */
    fun publish(context: Context): String {
        if (error == null && (sourceCount == 0 || completedPages != sourceCount)) {
            error = "Diagnostic ZIP is incomplete; previous capture preserved"
        }
        if (error == null) {
            try {
                entry("summary.json") {
                    write(JSONObject()
                        .put("schema", 1)
                        .put("complete", true)
                        .put("sourcePageCount", sourceCount)
                        .put("outputFrameCountExcludingCover", outputCount)
                        .put("separateCoverFrameCount", coverCount)
                        .put("outputFrameCountIncludingCover", outputCount + coverCount)
                        .put("originalImageCount", originals)
                        .toString(2).toByteArray(Charsets.UTF_8))
                }
            } catch (e: Exception) {
                error = "Diagnostic summary failed: " + e.javaClass.simpleName
            }
        }
        close()
        if (error != null) return requireNotNull(error)
        return try {
            check(file.length() in 1..MAX_ZIP_SIZE) { "Diagnostic ZIP exceeds size bound" }
            ZipFile(file).use { archive ->
                check(archive.getEntry("summary.json") != null)
                check(archive.getEntry("README.txt") != null)
                for (page in 1..sourceCount) {
                    val id = page.toString().padStart(5, '0')
                    check(archive.getEntry("pages/$id.json") != null)
                    check(archive.getEntry("thumbnails/$id.png") != null)
                }
            }
            val destination = latestFile(context)
            check(destination.parentFile?.mkdirs() == true || destination.parentFile?.isDirectory == true)
            val fresh = File(destination.parentFile, "latest-new.zip")
            try {
                file.copyTo(fresh, overwrite = true)
                // POSIX rename within the same private directory replaces atomically.
                // Never fall back to direct overwrite: an interrupted copy could
                // destroy the user's previous successful diagnostic archive.
                check(fresh.renameTo(destination)) { "Atomic diagnostic replacement failed" }
            } finally {
                fresh.delete()
            }
            "Ready: Save latest panel diagnostics ZIP from the Export dialog"
        } catch (e: Exception) {
            "Diagnostic ZIP could not be stored; previous capture preserved: " +
                e.javaClass.simpleName
        }
    }

    override fun close() {
        if (!closed) {
            closed = true
            try { zip.close() } catch (e: Exception) {
                error = "Cannot finalize diagnostic ZIP: " + e.javaClass.simpleName
            }
        }
    }
}
