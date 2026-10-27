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

private fun u16(bytes: ByteArray, at: Int) =
    (bytes[at].toInt() and 255) or ((bytes[at + 1].toInt() and 255) shl 8)

fun main() {
    val root = Files.createTempDirectory("manganooir-panel-export-").toFile()
    try {
        val input = File(root, "manga").apply { mkdirs() }
        check(ImageIO.write(panelGrid(), "png", File(input, "01.png")))
        val panelOptions = MangaConversionOptions(
            panelMode = "PANEL_BY_PANEL", includeCover = false,
            cropWhiteMargins = true, layout = "PAGED", dither = false,
        )
        // The Windows converter must honor the same independent row mode
        // as Android. Internal white columns must NOT split a horizontal row.
        val rowOptions = panelOptions.copy(panelMode = "ROW_BY_ROW")
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
