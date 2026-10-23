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
        println("Desktop panel exports passed: XTC, XTCH, EPUB2, EPUB3, CBZ, six screens, legacy OFF, splash fallback")
    } finally { root.deleteRecursively() }
}
