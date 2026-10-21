import dev.nooir.bridge.*
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile
import javax.imageio.ImageIO

/**
 * ORIGINAL, readable, copyright-safe device interoperability fixtures.
 *
 * These are not device-verified books: this test exercises the SAME production
 * EPUB 2, EPUB 3, XTC and XTCH writers as the Android exporter.
 *
 * Two XTC/XTCH sizes are generated intentionally:
 *   - X4 native 480x800 (48,000 / 96,000 bitmap bytes per page)
 *   - diagnostic 240x400 (12,000 / 24,000 bitmap bytes per page)
 * The diagnostic pages are smaller and render in the upper-left corner of the
 * reader: they are NOT an alternative for reading full-size manga.
 */
private object StoryPages {
    const val count = 3

    fun draw(w: Int, h: Int, index: Int): BufferedImage {
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        try {
            g.scale(w / 480.0, h / 800.0)
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g.color = Color.WHITE
            g.fillRect(0, 0, 480, 800)

            fun rectangle(x: Int, y: Int, width: Int, height: Int, shade: Int = 255) {
                g.color = Color(shade, shade, shade)
                g.fillRect(x, y, width, height)
                g.color = Color.BLACK
                g.stroke = BasicStroke(4f)
                g.drawRect(x, y, width, height)
            }
            fun heading(text: String, x: Int, y: Int, size: Int) {
                g.color = Color.BLACK
                g.font = Font("SansSerif", Font.BOLD, size)
                g.drawString(text, x, y)
            }
            fun writing(text: String, x: Int, y: Int, size: Int = 25) {
                g.color = Color.BLACK
                g.font = Font("SansSerif", Font.PLAIN, size)
                g.drawString(text, x, y)
            }

            heading("MANGA NOOIR", 30, 54, 35)
            g.stroke = BasicStroke(3f)
            g.drawLine(26, 70, 454, 70)

            when (index) {
                0 -> {
                    heading("READER", 42, 155, 55)
                    heading("COMPATIBILITY", 42, 211, 41)
                    rectangle(44, 245, 388, 330, 230)
                    g.color = Color.WHITE
                    g.fillOval(132, 289, 213, 213)
                    g.color = Color.BLACK
                    g.stroke = BasicStroke(8f)
                    g.drawOval(132, 289, 213, 213)
                    heading("01", 189, 422, 62)
                    heading("A REAL TEST BOOK", 58, 623, 29)
                    writing("Three illustrated pages.", 61, 667, 24)
                    writing("Not blank placeholder art.", 61, 705, 21)
                }
                1 -> {
                    heading("CHAPTER ONE", 34, 127, 34)
                    heading("THE LOST PAGE", 34, 169, 28)
                    rectangle(30, 206, 420, 207)
                    writing("Mira opened the book.", 48, 275, 25)
                    writing("Every page was white!", 48, 315, 25)
                    g.color = Color.BLACK
                    g.drawOval(324, 328, 65, 65)
                    rectangle(30, 429, 420, 215, 225)
                    heading("NOT TODAY.", 62, 518, 34)
                    writing("She drew a story.", 62, 570, 27)
                    writing("The pages came alive.", 62, 612, 23)
                }
                2 -> {
                    heading("CHAPTER TWO", 34, 126, 34)
                    heading("THE FOUR SHADES", 34, 164, 28)
                    val shades = intArrayOf(0, 85, 170, 255)
                    for (i in shades.indices) {
                        val x = 30 + i * 107
                        rectangle(x, 215, 96, 160, shades[i])
                        heading((i + 1).toString(), x + 29, 419, 27)
                    }
                    rectangle(30, 452, 420, 166, 241)
                    writing("Black, dark, light, white.", 48, 519, 24)
                    writing("Now turn to the end.", 48, 564, 25)
                    heading("THE END", 142, 694, 38)
                }
                else -> error("Unexpected story page")
            }
            g.color = Color.BLACK
            g.drawLine(30, 736, 450, 736)
            writing("MangaNooir  -  Page " + (index + 1) + " / 3", 34, 775, 18)
        } finally { g.dispose() }
        return img
    }

    fun png(w: Int, h: Int, index: Int): ByteArray {
        val out = ByteArrayOutputStream()
        check(ImageIO.write(draw(w, h, index), "png", out))
        return out.toByteArray()
    }

    fun page(w: Int, h: Int, index: Int, depth: Int): QuantizedPage {
        val source = draw(w, h, index)
        val shades = ByteArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val rgb = source.getRGB(x, y)
            val r = rgb ushr 16 and 255
            val green = rgb ushr 8 and 255
            val b = rgb and 255
            val gray = (r * 299 + green * 587 + b * 114) / 1000
            shades[y * w + x] = if (depth == 1) {
                (if (gray >= 128) 1 else 0).toByte()
            } else {
                (when {
                    gray > 212 -> 3
                    gray > 127 -> 2
                    gray > 42 -> 1
                    else -> 0
                }).toByte()
            }
        }
        return QuantizedPage(w, h, shades, depth)
    }
}

private fun u16(b: ByteArray, at: Int): Int =
    (b[at].toInt() and 255) or ((b[at + 1].toInt() and 255) shl 8)

private fun u32(b: ByteArray, at: Int): Long = (0..3).fold(0L) { a, j ->
    a or ((b[at + j].toLong() and 255L) shl (j * 8))
}

private fun u64(b: ByteArray, at: Int): Long = (0..7).fold(0L) { a, j ->
    a or ((b[at + j].toLong() and 255L) shl (j * 8))
}

/** Independently unpack reader-style pixels from the actual file bytes. */
private fun readerPixel(page: ByteArray, w: Int, h: Int, depth: Int, x: Int, y: Int): Int {
    if (depth == 1) {
        val index = 22 + y * ((w + 7) / 8) + x / 8
        return (page[index].toInt() ushr (7 - x % 8)) and 1
    }
    val bytesPerColumn = (h + 7) / 8
    val planeLength = w * bytesPerColumn
    val index = 22 + (w - 1 - x) * bytesPerColumn + y / 8
    val hi = (page[index].toInt() ushr (7 - y % 8)) and 1
    val lo = (page[index + planeLength].toInt() ushr (7 - y % 8)) and 1
    val stored = (hi shl 1) or lo
    return when (stored) { 3 -> 0; 1 -> 1; 2 -> 2; 0 -> 3; else -> error("Bad XTH color") }
}

private fun verifyNative(file: File, w: Int, h: Int, depth: Int) {
    val bytes = file.readBytes()
    check(String(bytes.copyOfRange(0, 4), Charsets.ISO_8859_1) ==
        (if (depth == 1) "XTC\u0000" else "XTCH"))
    check(u16(bytes, 6) == StoryPages.count)
    check(bytes[9].toInt() == 1 && bytes[11].toInt() == 1)
    check(u32(bytes, 12) == 1L)
    val chaptersAt = u32(bytes, 48).toInt()
    val tableAt = u64(bytes, 24).toInt()
    val dataAt = u64(bytes, 32).toInt()
    check(chaptersAt == 312 && tableAt == 408 && dataAt == 456)
    check(u16(bytes, chaptersAt + 80) == 2 && u16(bytes, chaptersAt + 82) == 3)
    val bitmapBytes = if (depth == 1) ((w + 7) / 8) * h else (w * h / 8) * 2
    var lastEnd = dataAt
    for (pageIndex in 0 until StoryPages.count) {
        val at = tableAt + pageIndex * 16
        val start = u64(bytes, at).toInt()
        val size = u32(bytes, at + 8).toInt()
        check(start == lastEnd && size == bitmapBytes + 22)
        check(u16(bytes, at + 12) == w && u16(bytes, at + 14) == h)
        check(start >= dataAt && start + size <= bytes.size)
        val page = bytes.copyOfRange(start, start + size)
        check(String(page.copyOfRange(0, 4), Charsets.ISO_8859_1) ==
            (if (depth == 1) "XTG\u0000" else "XTH\u0000"))
        check(u16(page, 4) == w && u16(page, 6) == h)
        check(u32(page, 10) == bitmapBytes.toLong())
        val original = StoryPages.page(w, h, pageIndex, depth).levels
        // Sampling across text, artwork, backgrounds and four-shade panels.
        var dark = 0
        for (y in 0 until h step 7) for (x in 0 until w step 7) {
            val pixel = readerPixel(page, w, h, depth, x, y)
            check(pixel == (original[y * w + x].toInt() and 255)) {
                "Reader pixel mismatch: " + file.name + " page=" + pageIndex + " at " + x + "," + y
            }
            if (pixel < (if (depth == 1) 1 else 2)) dark++
        }
        check(dark > 100) { "Almost empty page in " + file.name + " index=" + pageIndex }
        lastEnd = start + size
    }
    check(lastEnd == bytes.size) { "Unexpected private trailer / truncated binary" }
}

private fun writeArchive(file: File, format: ExportFormat) {
    val meta = ExtendedMetadata(
        title = "MangaNooir - A Real Test Story",
        author = "MangaNooir Test Suite",
        description = "An original three-page test comic with visible text, panels and grayscale. Not a blank placeholder.",
        language = "en",
        direction = ReadingDirection.LEFT_TO_RIGHT,
    )
    file.outputStream().use { sink ->
        check(BookArchiveWriter.write(format, sequence {
            for (index in 0 until StoryPages.count) {
                val chapter = when (index) { 0 -> "Cover"; 1 -> "Chapter One"; else -> "Chapter Two" }
                yield(ArchivePage(chapter, StoryPages.png(480, 800, index), 480, 800, isCover = index == 0))
            }
        }, sink, meta) == StoryPages.count)
    }
    ZipFile(file).use { zip ->
        val opf = zip.getInputStream(zip.getEntry("OEBPS/package.opf")).readBytes().toString(Charsets.UTF_8)
        check(opf.contains(if (format == ExportFormat.EPUB2) "version=\"2.0\"" else "version=\"3.0\""))
        check(opf.contains("MangaNooir - A Real Test Story"))
        check(zip.getEntry(if (format == ExportFormat.EPUB2) "OEBPS/toc.ncx" else "OEBPS/nav.xhtml") != null)
        var distinctPixels = 0
        for (index in 1..StoryPages.count) {
            val n = "%05d".format(index)
            val image = ImageIO.read(ByteArrayInputStream(
                zip.getInputStream(zip.getEntry("OEBPS/images/p" + n + ".png")).readBytes()
            )) ?: error("Unreadable image page " + index)
            check(image.width == 480 && image.height == 800)
            var ink = 0
            for (y in 0 until 800 step 9) for (x in 0 until 480 step 9) {
                if ((image.getRGB(x, y) and 255) < 180) ink++
            }
            check(ink > 70) { "EPUB page is empty: " + file.name + " #" + index }
            distinctPixels += ink
        }
        check(distinctPixels > 500)
    }
}

private fun writeReflow(file: File, format: ReflowEpubFormat) {
    // The PDF/web writer emits selectable XHTML prose, NOT fixed image-page text.
    // Leave author, summary and source URL blank to exercise optional EPUB 3 metadata.
    ReflowEpubWriter.write(file, ReflowMetadata(
        title = "MangaNooir - Selectable Text and Illustrations",
    ), format) { epub ->
        val href = epub.addImage(
            ReflowEpubWriter.ImageAsset(StoryPages.png(480, 800, 1), "png", "image/png"),
            isCover = true,
        )
        epub.addSection("Chapter One", "<h1>Chapter One: A Real Page</h1>" +
            "<p>On a quiet morning, Mira opened her e-reader and found a book with real words, " +
            "real sentences, and an illustration. Every paragraph in this chapter is selectable text.</p>" +
            "<figure><img src=\"" + href + "\" alt=\"An illustrated story page\"/></figure>" +
            "<p>She turned the page. This EPUB did not pretend a white rectangle was a story.</p>" +
            "<table><tr><th>Room</th><th>Clue</th></tr><tr><td>Study</td><td>Floor plan</td></tr></table>")
        epub.addSection("Chapter Two", "<h1>Chapter Two: The Test</h1>" +
            "<p>Changing the font size should reflow this prose. The image remains separate " +
            "and correctly referenced through the EPUB manifest.</p>")
    }
    ZipFile(file).use { zip ->
        val opf = zip.getInputStream(zip.getEntry("OEBPS/package.opf")).readBytes()
            .toString(Charsets.UTF_8)
        check(opf.contains(if (format == ReflowEpubFormat.EPUB2) "version=\"2.0\"" else "version=\"3.0\""))
        for (tag in listOf("creator", "description", "source")) {
            check(!opf.contains("<dc:" + tag + ">")) { "Empty EPUB 3 metadata: " + tag }
        }
        val body1 = zip.getInputStream(zip.getEntry("OEBPS/text/s00001.xhtml")).readBytes()
            .toString(Charsets.UTF_8)
        val body2 = zip.getInputStream(zip.getEntry("OEBPS/text/s00002.xhtml")).readBytes()
            .toString(Charsets.UTF_8)
        check(body1.contains("Every paragraph in this chapter is selectable text."))
        check(body1.contains("../images/img00001.png"))
        check(body1.contains("<table>") && body1.contains("Floor plan"))
        if (format == ReflowEpubFormat.EPUB2) {
            check(!body1.contains("<figure>") && body1.contains("class=\"illustration\""))
            check(zip.getEntry("OEBPS/toc.ncx") != null)
        } else check(zip.getEntry("OEBPS/nav.xhtml") != null)
        check(body2.contains("Changing the font size should reflow this prose."))
        check(zip.getEntry("OEBPS/images/img00001.png") != null)
    }
}

private fun writeNative(file: File, w: Int, h: Int, fmt: XtcFormat) {
    file.outputStream().use { out ->
        val result = XtcCodec.write(fmt, sequence {
            for (index in 0 until StoryPages.count) yield(StoryPages.page(w, h, index, fmt.depth))
        }, out, file.parentFile,
            metadata = BookMetadata(
                title = "MangaNooir - A Real Test Story",
                author = "MangaNooir",
                coverPage = 0,
            ),
            chapters = listOf(BookChapter("Chapter One", 1, 2)),
            direction = ReadingDirection.LEFT_TO_RIGHT,
        )
        check(result.pageCount == StoryPages.count && result.fileBytes == file.length())
    }
    verifyNative(file, w, h, fmt.depth)
}

fun main() {
    val configured = System.getenv("NOOIR_OUTPUT_DIR")
    val folder = configured?.takeIf { it.isNotBlank() }?.let { File(it) }
        ?: java.nio.file.Files.createTempDirectory("nooir-readable-").toFile()
    check(folder.isDirectory || folder.mkdirs())
    try {
        writeArchive(File(folder, "MangaNooir_Readable_EPUB2_X4.epub"), ExportFormat.EPUB2)
        writeArchive(File(folder, "MangaNooir_Readable_EPUB3_X4.epub"), ExportFormat.EPUB)
        writeReflow(File(folder, "MangaNooir_Reflow_EPUB3_SelectableText.epub"), ReflowEpubFormat.EPUB3)
        writeReflow(File(folder, "MangaNooir_Reflow_EPUB2_SelectableText.epub"), ReflowEpubFormat.EPUB2)
        for (fmt in XtcFormat.entries) {
            writeNative(File(folder, "MangaNooir_Readable_" + fmt.name + "_X4_480x800." + fmt.extension),
                480, 800, fmt)
            writeNative(File(folder, "MangaNooir_LowMemDiagnostic_" + fmt.name + "_240x400." + fmt.extension),
                240, 400, fmt)
        }
        File(folder, "READ_ME_FIRST.txt").writeText(
            "MangaNooir ORIGINAL three-page reader compatibility test (21 October 2026)\n\n" +
            "All files contain visible original story text, panels and a dedicated cover.\n" +
            "Generated through the real MangaNooir production writers; verified by a\n" +
            "separate host-side binary reader and by decoding every EPUB image.\n\n" +
            "EPUB2 / EPUB3 are fixed-image comic EPUBs (not reflowable text novels).\n" +
            "Native X4 XTC and XTCH pages are 480x800: bitmaps require about 48 KiB\n" +
            "and 96 KiB respectively when a reader buffers the entire page.\n" +
            "LowMemDiagnostic pages are 240x400: 12 KiB XTC / 24 KiB XTCH.\n" +
            "They appear smaller at the top-left on X4 because the reader does not\n" +
            "scale XTC pixel buffers. They are diagnostic only, not quality presets.\n\n" +
            "Try a full-size file and its smaller diagnostic counterpart on the SAME\n" +
            "reader, with the SAME brightness/dark-mode settings. If only the smaller\n" +
            "one opens, inspect firmware contiguous heap allocation and reader mode.\n" +
            "If both fail, capture the exact error and firmware version.\n" +
            "A successful host test DOES NOT prove Android or device compatibility.\n" +
            "No Folio Nooir firmware was modified. No copyrighted manga included.\n"
        )
        val all = folder.listFiles().orEmpty().filter { it.isFile }
        for (file in all.sortedBy { it.name }) {
            val digest = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
                .joinToString("") { "%02x".format(it.toInt() and 255) }
            println("fixture " + file.name + ": " + file.length() + " bytes, sha256=" + digest)
        }
        println("Readable EPUB2 / EPUB3 / XTC / XTCH full-X4 and low-memory diagnostic validation passed")
    } finally {
        if (configured.isNullOrBlank()) folder.deleteRecursively()
    }
}
