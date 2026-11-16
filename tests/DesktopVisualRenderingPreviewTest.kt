import dev.nooir.bridge.XtcBookPreview
import dev.nooir.desktop.NooirDesktopCli
import dev.nooir.mangabridge.MangaConversionOptions
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO

/**
 * Actual source image -> desktop converter -> stored XTC/XTCH binary -> decoded
 * pixels, not an unencoded renderer mock. All artwork is synthetic and authored
 * for this test; no manga scans or external art are bundled.
 *
 * On GitHub Actions an optional contact sheet is saved to NOOIR_OUTPUT_DIR so
 * visual reviewers can compare all modes without using a real e-reader.
 */
fun main() {
    val dir = Files.createTempDirectory("nooir-visual-render-modes-").toFile()
    try {
        val source = File(dir, "source").apply { check(mkdirs()) }
        val sourceImage = File(source, "01.png")
        val image = BufferedImage(480, 800, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        try {
            g.color = Color.WHITE
            g.fillRect(0, 0, 480, 800)
            // Smooth gradient, manga-like parallel screentones, dark gutters,
            // faint hairline/text edges, and speech-bubble-like white outline.
            for (y in 0 until 400) for (x in 0 until 480) {
                val v = (x * 255 / 479).coerceIn(0, 255)
                image.setRGB(x, y, Color(v, v, v).rgb)
            }
            for (y in 415 until 790) for (x in 0 until 480) {
                val tone = if ((x + y * 2) % 13 < 6) 180 else 226
                image.setRGB(x, y, Color(tone, tone, tone).rgb)
            }
            g.color = Color(12, 12, 12)
            g.drawLine(15, 300, 460, 430)
            g.drawRect(8, 8, 463, 780)
            g.color = Color(252, 252, 252)
            g.fillOval(90, 505, 310, 178)
            g.color = Color(14, 14, 14)
            g.drawOval(90, 505, 310, 178)
            g.drawString("SYNTHETIC DIALOGUE", 145, 588)
            g.color = Color(238, 238, 238)
            g.drawString("faint lettering", 28, 185)
        } finally { g.dispose() }
        check(ImageIO.write(image, "png", sourceImage))

        val formats = listOf("XTC", "XTCH")
        val algorithms = listOf("FLOYD_STEINBERG", "ATKINSON", "BAYER_8X8", "THRESHOLD")
        val outputDir = System.getenv("NOOIR_OUTPUT_DIR")?.let { File(it) }
            ?.apply { if (!isDirectory) check(mkdirs()) }
        val sheet = BufferedImage(4 * 240, 2 * 430, BufferedImage.TYPE_INT_RGB)
        val canvas = sheet.createGraphics()
        val decodedByFormat = mutableMapOf<String, MutableList<ByteArray>>()
        try {
            canvas.color = Color.WHITE
            canvas.fillRect(0, 0, sheet.width, sheet.height)
            canvas.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            for ((row, format) in formats.withIndex()) {
                val allModes = mutableListOf<ByteArray>()
                for ((column, algorithm) in algorithms.withIndex()) {
                    val output = File(dir, "${format.lowercase()}-${algorithm.lowercase()}.${format.lowercase()}")
                    val settings = MangaConversionOptions(
                        panelMode = "OFF", layout = "PAGED", includeCover = false,
                        cropWhiteMargins = false, splitSpreads = false,
                        renderAlgorithm = algorithm, dither = algorithm != "THRESHOLD",
                        contrast = 50, contextPeekPercent = 0
                    )
                    NooirDesktopCli.main(arrayOf(
                        "--input", source.absolutePath, "--output", output.absolutePath,
                        "--format", format, "--device", "X4",
                        "--options-token", settings.token(), "--title", "Visual QA ${algorithm}"
                    ))
                    val inspector = XtcBookPreview(output)
                    check(inspector.pageCount == 1)
                    check(inspector.format.name == format)
                    val page = inspector.page(0)
                    check(page.width == 480 && page.height == 800) {
                        "Native X4 ${format} dimensions drifted"
                    }
                    check(page.levels.all {
                        (it.toInt() and 255) in 0..(if (format == "XTC") 1 else 3)
                    })
                    allModes += page.levels
                    val rgb = IntArray(page.levels.size) { i ->
                        val lum = (page.levels[i].toInt() and 255) *
                            (if (format == "XTC") 255 else 85)
                        (lum shl 16) or (lum shl 8) or lum
                    }
                    val bitmap = BufferedImage(480, 800, BufferedImage.TYPE_INT_RGB)
                    bitmap.setRGB(0, 0, 480, 800, rgb, 0, 480)
                    canvas.drawImage(bitmap, column * 240, row * 430 + 25,
                        240, 400, null)
                    canvas.color = Color.BLACK
                    canvas.drawString("$format — ${algorithm.replace('_', ' ')}",
                        column * 240 + 6, row * 430 + 16)
                }
                check(allModes.distinctBy { it.contentHashCode() }.size >= 3) {
                    "Rendering settings did not reach the stored ${format} file"
                }
                decodedByFormat[format] = allModes
            }
        } finally { canvas.dispose() }

        // We are not claiming real visual quality from synthetic art; reviewers
        // must inspect the generated contact sheet and original manga samples.
        if (outputDir != null) {
            val result = File(outputDir, "Nooir_Visual_4Modes_XTC_XTCH.png")
            check(ImageIO.write(sheet, "png", result))
            val original = File(outputDir, "Nooir_Visual_Source_Synthetic.png")
            check(ImageIO.write(image, "png", original))
            println("Visual QA image artifact: ${result.absolutePath}")
        }
        check(decodedByFormat.size == 2)
        println("PASS: actual X4 XTC/XTCH file pixels differ for chosen four rendering modes")
    } finally { dir.deleteRecursively() }
}
