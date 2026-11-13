import dev.nooir.desktop.NooirDesktopCli
import dev.nooir.mangabridge.MangaConversionOptions
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import javax.imageio.ImageIO

private fun panelGrid(): BufferedImage {
    val image = BufferedImage(300, 420, BufferedImage.TYPE_INT_RGB)
    val g = image.createGraphics()
    try {
        g.color = java.awt.Color.WHITE
        g.fillRect(0, 0, 300, 420)
        val columns = listOf(0 to 145, 155 to 300)
        val rows = listOf(0 to 132, 144 to 274, 286 to 420)
        for ((row, ys) in rows.withIndex()) for ((col, xs) in columns.withIndex()) {
            val shade = 35 + row * 35 + col * 13
            g.color = java.awt.Color(shade, shade, shade)
            g.fillRect(xs.first + 4, ys.first + 4,
                xs.second - xs.first - 8, ys.second - ys.first - 8)
        }
    } finally { g.dispose() }
    return image
}
private fun mixedOrientationGrid(): BufferedImage {
    val image = BufferedImage(300, 420, BufferedImage.TYPE_INT_RGB)
    val g = image.createGraphics()
    try {
        g.color = java.awt.Color.WHITE
        g.fillRect(0, 0, 300, 420)
        g.color = java.awt.Color(45, 45, 45)
        // Wide top panel and two portrait bottom panels, with full-span gutters.
        g.fillRect(5, 5, 290, 98)
        g.color = java.awt.Color(85, 85, 85)
        g.fillRect(5, 127, 135, 288)
        g.color = java.awt.Color(125, 125, 125)
        g.fillRect(160, 127, 135, 288)
    } finally { g.dispose() }
    return image
}
private fun widePanelStrip(): BufferedImage {
    val image = BufferedImage(480, 240, BufferedImage.TYPE_INT_RGB)
    val g = image.createGraphics()
    try {
        g.color = java.awt.Color.WHITE
        g.fillRect(0, 0, 480, 240)
        g.color = java.awt.Color(78, 78, 78)
        g.fillRect(12, 12, 213, 216)
        g.color = java.awt.Color(98, 98, 98)
        g.fillRect(255, 12, 213, 216)
    } finally { g.dispose() }
    return image
}

private fun slantedPanelFixture(): BufferedImage {
    // Two substantial illustrated trapezoids with one continuous bright
    // diagonal gutter. Every output should keep rectangular safety overlap.
    val image = BufferedImage(300, 420, BufferedImage.TYPE_INT_RGB)
    val white = java.awt.Color.WHITE.rgb
    val left = java.awt.Color(70, 70, 70).rgb
    val right = java.awt.Color(100, 100, 100).rgb
    for (y in 0 until 420) {
        val divider = 126 + (48L * y / 419).toInt()
        for (x in 0 until 300) {
            image.setRGB(x, y, when {
                y !in 8..411 -> white
                x in 8 until divider - 8 -> left
                x in divider + 8 until 292 -> right
                else -> white
            })
        }
    }
    return image
}

private fun promotionalMontageGrid(): BufferedImage {
    val image = BufferedImage(600, 475, BufferedImage.TYPE_INT_RGB)
    val g = image.createGraphics()
    try {
        g.color = java.awt.Color.WHITE
        g.fillRect(0, 0, 600, 475)
        // Independent, wholly synthetic character montage and large footer.
        // No copyrighted manga, title strings, image or diagnostics in Git.
        for (column in 0..3) {
            val x = 30 + column * 136
            g.color = java.awt.Color(10, 10, 10)
            g.drawRect(x, 52, 123, 293)
            g.color = java.awt.Color(25, 25, 25)
            g.fillOval(x + 19, 95, 86, 85)
            g.color = java.awt.Color(40, 40, 40)
            g.fillRect(x + 16, 210, 91, 120)
        }
        g.color = java.awt.Color(8, 8, 8)
        for (letter in 0..6)
            g.fillRect(130 + letter * 57, 395, 46, 53)
    } finally { g.dispose() }
    return image
}

private fun u16(bytes: ByteArray, at: Int) =
    (bytes[at].toInt() and 255) or ((bytes[at + 1].toInt() and 255) shl 8)

// Compare the actual packed first XTC screen, not just the total page count.
private fun firstXtcPixels(file: File): ByteArray {
    val bytes = file.readBytes()
    fun u64(at: Int) = (0..7).fold(0L) { value, i ->
        value or ((bytes[at + i].toLong() and 255L) shl (i * 8))
    }
    val index = u64(24).toInt()
    val start = u64(index).toInt()
    val length = (0..3).fold(0) { value, i ->
        value or ((bytes[index + 8 + i].toInt() and 255) shl (i * 8))
    }
    check(length > 22 && start >= 0 && start.toLong() + length <= bytes.size)
    return bytes.copyOfRange(start + 22, start + length)
}

fun main() {
    val root = Files.createTempDirectory("manganooir-panel-export-").toFile()
    try {
        val input = File(root, "manga").apply { mkdirs() }
        check(ImageIO.write(panelGrid(), "png", File(input, "01.png")))
        val panelOptions = MangaConversionOptions(
            panelMode = "PANEL_BY_PANEL", includeCover = false,
            cropWhiteMargins = true, layout = "PAGED", dither = false,
            keepChapterFirstPageWhole = false,
        )
        // The Windows converter must honor the same independent row mode
        // as Android. Internal white columns must NOT split a horizontal row.
        val rowOptions = panelOptions.copy(panelMode = "ROW_BY_ROW")
        // A four-character promotional montage over a bold decorative footer
        // must remain ONE intact screen. Never turn its letters into enlarged
        // reading frames, even with Overview and Auto Fit enabled.
        val promoInput = File(root, "promotional-layout").apply { mkdirs() }
        check(ImageIO.write(promotionalMontageGrid(), "png", File(promoInput, "01.png")))
        val promoOptions = panelOptions.copy(
            panelMode = "PANEL_BY_PANEL", orientation = "AUTO_FIT",
            keepChapterFirstPageWhole = false, showFullPageBeforeSplits = true,
        )
        for (format in listOf("XTC", "XTCH")) {
            val result = File(root, "promo-guard.$"+"{format.lowercase()}")
            NooirDesktopCli.main(arrayOf(
                "--input", promoInput.absolutePath, "--output", result.absolutePath,
                "--format", format, "--device", "X4",
                "--options-token", promoOptions.token(),
            ))
            check(u16(result.readBytes(), 6) == 1) {
                "Framed promotional illustration plus graphic title must stay one $"+"format page"
            }
        }
        // The same optional first-page and overview flags used by Android
        // must affect real Windows output, not just roundtrip in settings.
        val wholeOutput = File(root, "whole-intact.xtc")
        NooirDesktopCli.main(arrayOf(
            "--input", input.absolutePath, "--output", wholeOutput.absolutePath,
            "--format", "XTC", "--device", "X4",
            "--options-token", panelOptions.copy(panelMode = "OFF",
                cropWhiteMargins = false, splitSpreads = false).token(),
        ))
        check(u16(wholeOutput.readBytes(), 6) == 1)
        val overviewOutput = File(root, "overview.xtc")
        NooirDesktopCli.main(arrayOf(
            "--input", input.absolutePath, "--output", overviewOutput.absolutePath,
            "--format", "XTC", "--device", "X4",
            "--options-token", panelOptions.copy(showFullPageBeforeSplits = true).token(),
        ))
        check(u16(overviewOutput.readBytes(), 6) == 7) {
            "Explicit full-page overview must precede six safely detected panels"
        }
        check(firstXtcPixels(overviewOutput).contentEquals(firstXtcPixels(wholeOutput))) {
            "Overview must preserve original whole-page pixels"
        }
        val rowOverview = File(root, "row-overview.xtc")
        NooirDesktopCli.main(arrayOf(
            "--input", input.absolutePath, "--output", rowOverview.absolutePath,
            "--format", "XTC", "--device", "X4",
            "--options-token", rowOptions.copy(showFullPageBeforeSplits = true).token(),
        ))
        check(u16(rowOverview.readBytes(), 6) == 4) {
            "Explicit row-only overview must add one screen, not extra row cuts"
        }
        val chapterInput = File(root, "chapter-first").apply { mkdirs() }
        check(ImageIO.write(panelGrid(), "png", File(chapterInput, "01.png")))
        check(ImageIO.write(panelGrid(), "png", File(chapterInput, "02.png")))
        val chapterOutput = File(root, "chapter-first.xtc")
        NooirDesktopCli.main(arrayOf(
            "--input", chapterInput.absolutePath, "--output", chapterOutput.absolutePath,
            "--format", "XTC", "--device", "X4",
            "--options-token", panelOptions.copy(keepChapterFirstPageWhole = true).token(),
        ))
        check(u16(chapterOutput.readBytes(), 6) == 7) {
            "First page in a chapter must stay whole; second may yield six panels"
        }
        check(firstXtcPixels(chapterOutput).contentEquals(firstXtcPixels(wholeOutput))) {
            "Keep-chapter-first must retain the intact opening page"
        }
        val rowOutput = File(root, "rows.xtc")
        NooirDesktopCli.main(arrayOf(
            "--input", input.absolutePath, "--output", rowOutput.absolutePath,
            "--format", "XTC", "--device", "X4", "--options-token", rowOptions.token(),
        ))
        check(u16(rowOutput.readBytes(), 6) == 3) {
            "Row by Row must retain exactly three horizontal tiers, not six panels"
        }
        val rowAuto = File(root, "rows-auto.epub")
        NooirDesktopCli.main(arrayOf(
            "--input", input.absolutePath, "--output", rowAuto.absolutePath,
            "--format", "EPUB2", "--device", "X4",
            "--options-token", rowOptions.copy(orientation = "AUTO_FIT").token(),
        ))
        ZipFile(rowAuto).use { zip ->
            check(zip.entries().asSequence().count { it.name.endsWith(".png") } == 3) {
                "Auto Fit must not split horizontal rows back into vertical panels"
            }
        }

        // End-to-end export regression: irregular-but-confident diagonal
        // geometry must reach the actual on-device XTC renderer and EPUB2
        // images without being rejected for overlapping source bounding boxes.
        val angledInput = File(root, "slanted-panels").apply { mkdirs() }
        check(ImageIO.write(slantedPanelFixture(), "png",
            File(angledInput, "01.png")))
        val angledOptions = panelOptions.copy(orientation = "AUTO_FIT")
        val angledXtc = File(root, "slanted.xtc")
        NooirDesktopCli.main(arrayOf(
            "--input", angledInput.absolutePath, "--output", angledXtc.absolutePath,
            "--format", "XTC", "--device", "X4",
            "--options-token", angledOptions.token(),
        ))
        check(u16(angledXtc.readBytes(), 6) == 2) {
            "A safe diagonal separator should yield two complete XTC pages"
        }
        val angledEpub = File(root, "slanted.epub")
        NooirDesktopCli.main(arrayOf(
            "--input", angledInput.absolutePath, "--output", angledEpub.absolutePath,
            "--format", "EPUB2", "--device", "X4",
            "--options-token", angledOptions.token(),
        ))
        ZipFile(angledEpub).use { zip ->
            check(zip.entries().asSequence().count { it.name.endsWith(".png") } == 2) {
                "Irregular panel bounds must survive EPUB2 conversion"
            }
        }
        val angledRow = File(root, "slanted-row.xtc")
        NooirDesktopCli.main(arrayOf(
            "--input", angledInput.absolutePath, "--output", angledRow.absolutePath,
            "--format", "XTC", "--device", "X4",
            "--options-token", rowOptions.token(),
        ))
        check(u16(angledRow.readBytes(), 6) == 1) {
            "Row by Row must not adopt diagonal panel-only recovery"
        }

        // Strict ROW_BY_ROW must recover pale-gray full-width seams without
        // switching to individual panel detection. Actual XTC output is checked.
        val grayRowsInput = File(root, "pale-gray-rows").apply { mkdirs() }
        val grayPage = BufferedImage(300, 420, BufferedImage.TYPE_INT_RGB)
        val ggray = grayPage.createGraphics()
        try {
            ggray.color = java.awt.Color(230, 230, 230)
            ggray.fillRect(0, 0, 300, 420)
            ggray.color = java.awt.Color(75, 75, 75)
            ggray.fillRect(12, 12, 276, 178)
            ggray.color = java.awt.Color(90, 90, 90)
            ggray.fillRect(12, 229, 276, 180)
        } finally { ggray.dispose() }
        check(ImageIO.write(grayPage, "png", File(grayRowsInput, "01.png")))
        val grayRowOutput = File(root, "pale-gray-row.xtc")
        NooirDesktopCli.main(arrayOf(
            "--input", grayRowsInput.absolutePath, "--output", grayRowOutput.absolutePath,
            "--format", "XTC", "--device", "X4",
            "--options-token", rowOptions.copy(layout = "AUTO").token(),
        ))
        check(u16(grayRowOutput.readBytes(), 6) == 2) {
            "Row by Row must recover two safely separated gray-gap rows"
        }

        // If row detection fails, even AUTO/WEBTOON and spread settings
        // may not invent a new viewport cut in strict Row by Row mode.
        val unsplitInput = File(root, "unsplittable-row").apply { mkdirs() }
        val solidPage = BufferedImage(900, 240, BufferedImage.TYPE_INT_RGB)
        val gsolid = solidPage.createGraphics()
        try {
            gsolid.color = java.awt.Color(90, 90, 90)
            gsolid.fillRect(0, 0, 900, 240)
        } finally { gsolid.dispose() }
        check(ImageIO.write(solidPage, "png", File(unsplitInput, "01.png")))
        for (layout in listOf("AUTO", "WEBTOON", "PAGED")) {
            val untouched = File(root, "unsplittable-row-$layout.xtc")
            NooirDesktopCli.main(arrayOf(
                "--input", unsplitInput.absolutePath, "--output", untouched.absolutePath,
                "--format", "XTC", "--device", "X4",
                "--options-token", rowOptions.copy(layout = layout,
                    splitSpreads = true, orientation = "AUTO_FIT").token(),
            ))
            check(u16(untouched.readBytes(), 6) == 1) {
                "Unsplit Row by Row must retain one full page even in $layout"
            }
        }

        // Both platforms must recognize clear panel gutters in wide strips.
        val wideInput = File(root, "wide").apply { mkdirs() }
        check(ImageIO.write(widePanelStrip(), "png", File(wideInput, "wide.png")))
        val wideOutput = File(root, "wide-panels.xtch")
        NooirDesktopCli.main(arrayOf(
            "--input", wideInput.absolutePath, "--output", wideOutput.absolutePath,
            "--format", "XTCH", "--device", "X4", "--options-token", panelOptions.token(),
        ))
        check(u16(wideOutput.readBytes(), 6) == 2) {
            "Wide Japanese manga strip should become two RTL panel frames"
        }

        for ((format, ext) in listOf("XTC" to "xtc", "XTCH" to "xtch",
            "EPUB2" to "epub", "EPUB" to "epub", "CBZ" to "cbz")) {
            val output = File(root, "panels-$format.$ext")
            NooirDesktopCli.main(arrayOf(
                "--input", input.absolutePath, "--output", output.absolutePath,
                "--format", format, "--device", "X4", "--options-token", panelOptions.token(),
            ))
            check(output.length() > 200) { "$format empty" }
            if (format in listOf("XTC", "XTCH")) {
                val bytes = output.readBytes()
                check(u16(bytes, 6) == 6) { "$format should have six distinct panel screens" }
            } else ZipFile(output).use { zip ->
                val names = zip.entries().asSequence().map { it.name }.toList()
                val imageCount = names.count { it.endsWith(".png") }
                check(imageCount == 6) { "$format should contain six panel images, got $imageCount" }
            }
        }
        // Auto Fit is a new opt-in orientation on top of detected panel mode.
        // It must preserve all six panels and native XTC/XTCH framebuffer sizes.
        val autoOptions = panelOptions.copy(orientation = "AUTO_FIT")
        for ((format, ext) in listOf("XTC" to "xtc", "XTCH" to "xtch",
            "EPUB2" to "epub", "EPUB" to "epub", "CBZ" to "cbz")) {
            val output = File(root, "auto-$format.$ext")
            NooirDesktopCli.main(arrayOf(
                "--input", input.absolutePath, "--output", output.absolutePath,
                "--format", format, "--device", "X4", "--options-token", autoOptions.token(),
            ))
            check(output.length() > 200) { "Auto Fit $format empty" }
            if (format in listOf("XTC", "XTCH")) {
                val bytes = output.readBytes()
                check(u16(bytes, 6) == 6) { "Auto Fit $format must retain all panels" }
            } else ZipFile(output).use { zip ->
                val entries = zip.entries().asSequence()
                    .filter { it.name.endsWith(".png") }.toList()
                check(entries.size == 6) { "Auto Fit $format lost detected panels" }
                for (entry in entries) {
                    val img = zip.getInputStream(entry).use { ImageIO.read(it) }
                        ?: error("Unreadable Auto Fit image in $format")
                    check((img.width == 480 && img.height == 800) ||
                        (img.width == 800 && img.height == 480)) {
                        "Unexpected adaptive Auto Fit page dimensions: ${img.width}x${img.height}"
                    }
                }
            }
        }
        // Mixed shape panels: a wide panel should fill a landscape EPUB page,
        // while the tall panels retain portrait pages. XTC stays fixed 480x800.
        val mixed = File(root, "mixed").apply { mkdirs() }
        check(ImageIO.write(mixedOrientationGrid(), "png", File(mixed, "01.png")))
        val mixedEpub = File(root, "mixed-auto.epub")
        NooirDesktopCli.main(arrayOf(
            "--input", mixed.absolutePath, "--output", mixedEpub.absolutePath,
            "--format", "EPUB2", "--device", "X4", "--options-token", autoOptions.token(),
        ))
        ZipFile(mixedEpub).use { zip ->
            val entries = zip.entries().asSequence().filter { it.name.endsWith(".png") }.toList()
            check(entries.size == 3) { "Expected three safely detected mixed-shape panels" }
            val sizes = entries.map { entry ->
                zip.getInputStream(entry).use { stream ->
                    val img = ImageIO.read(stream) ?: error("Unreadable adaptive page")
                    img.width to img.height
                }
            }
            check(sizes.count { it == (800 to 480) } == 1) {
                "Wide panel should use landscape: $sizes"
            }
            check(sizes.count { it == (480 to 800) } == 2) {
                "Tall panels should use portrait: $sizes"
            }
        }
        val mixedXtc = File(root, "mixed-auto.xtc")
        NooirDesktopCli.main(arrayOf(
            "--input", mixed.absolutePath, "--output", mixedXtc.absolutePath,
            "--format", "XTC", "--device", "X4", "--options-token", autoOptions.token(),
        ))
        check(u16(mixedXtc.readBytes(), 6) == 3) { "Auto Fit XTC must retain three fixed-size frames" }
        val original = File(root, "original.xtch")
        NooirDesktopCli.main(arrayOf(
            "--input", input.absolutePath, "--output", original.absolutePath,
            "--format", "XTCH", "--device", "X4",
        ))
        check(u16(original.readBytes(), 6) == 1) { "Mode OFF must retain one original manga page" }
        val fallback = File(root, "splash").apply { mkdirs() }
        val splash = BufferedImage(300, 420, BufferedImage.TYPE_INT_RGB)
        val g = splash.createGraphics()
        try {
            g.color = java.awt.Color(110, 110, 110)
            g.fillRect(0, 0, 300, 420)
        } finally { g.dispose() }
        check(ImageIO.write(splash, "png", File(fallback, "splash.png")))
        val safe = File(root, "splash.xtc")
        NooirDesktopCli.main(arrayOf(
            "--input", fallback.absolutePath, "--output", safe.absolutePath,
            "--format", "XTC", "--device", "X4", "--options-token", panelOptions.token(),
        ))
        check(u16(safe.readBytes(), 6) == 1) { "Splash pages must remain intact" }
        println("Desktop panel exports passed: XTC, XTCH, EPUB2, EPUB3, CBZ, six screens, all-format Auto Fit, legacy OFF, splash fallback")
    } finally { root.deleteRecursively() }
}
