import dev.nooir.bridge.XtcBookPreview
import dev.nooir.desktop.NooirDesktopCli
import dev.nooir.desktop.ui.LocalComicPages
import java.io.File
import java.nio.file.Files
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO

/**
 * Original synthetic 64x96 grayscale artwork encoded as VP8 (lossy) and VP8L
 * (lossless). No copyrighted comic, native executable or network input.
 *
 * Use the real Windows CBZ reader, CLI converter, stored XTC reader and
 * bounded image metadata checks. This must pass with the *packaged* WebP SPI.
 */
fun main() {
    val lossy = Base64.getDecoder().decode(
        "UklGRn4AAABXRUJQVlA4IHIAAADwBQCdASpAAGAAPj0ai0OiIaEWBAAgA8S0gAIYz/EAUpzk2fD0PaNPZCMdhtTUJcjqh1tXLad8AAD+/ERBoP/4n8f/lYAAH7/qR//7X//+l1wP4Txi3GqiQHj/J/4g/7A+f/5X6d4yy2UYWMz/xergAAA="
    )
    val lossless = Base64.getDecoder().decode(
        "UklGRjQAAABXRUJQVlA4TCgAAAAvP8AXAA9wkv+T/J/kf/7jAYratoEilT/lhsGOX0T/J0ANwH8Muzon"
    )
    val temp = Files.createTempDirectory("nooir-desktop-webp-cbz-").toFile()
    try {
        val source = File(temp, "original-synthetic-webp.cbz")
        ZipOutputStream(source.outputStream()).use { zip ->
            for ((i, bytes) in listOf(lossy, lossless).withIndex()) {
                zip.putNextEntry(ZipEntry("%03d.webp".format(i + 1)))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        check(ImageIO.getImageReadersByFormatName("WebP").hasNext()) {
            "Packaged WebP ImageIO provider not available"
        }
        check(LocalComicPages.names(source) == listOf("001.webp", "002.webp"))
        for (name in listOf("001.webp", "002.webp")) {
            val page = LocalComicPages.read(source, name)
            check(page.width == 64 && page.height == 96) {
                "WebP decoded wrong dimensions: ${page.width}x${page.height}"
            }
            page.flush()
        }
        for (format in listOf("XTC", "XTCH")) {
            val output = File(temp, "from-webp.${format.lowercase()}")
            NooirDesktopCli.main(arrayOf(
                "--input", source.absolutePath,
                "--output", output.absolutePath,
                "--format", format, "--device", "X4",
            ))
            val inspector = XtcBookPreview(output)
            check(inspector.pageCount == 2) {
                "Two original WebP pages must produce exactly two $format pages"
            }
            for (index in 0..1) {
                val page = inspector.page(index)
                check(page.width == 480 && page.height == 800)
                check(page.levels.any { it.toInt() != 0 } &&
                      page.levels.any { it.toInt() == 0 }) {
                    "Stored WebP-derived page was blank: $format page ${index + 1}"
                }
            }
        }
        // A bogus .webp inside a CBZ must fail without promoting output,
        // deleting the source or leaving orphaned temporary export files.
        val bad = File(temp, "corrupt-webp.cbz")
        ZipOutputStream(bad.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("001.webp"))
            zip.write(lossy.copyOf(20))
            zip.closeEntry()
        }
        val failed = File(temp, "corrupt-output.xtc")
        check(runCatching {
            NooirDesktopCli.main(arrayOf(
                "--input", bad.absolutePath,
                "--output", failed.absolutePath,
                "--format", "XTC", "--device", "X4",
            ))
        }.isFailure)
        check(!failed.exists())
        check(temp.listFiles().orEmpty().none {
            it.name.startsWith(".nooir-export-") && it.name.endsWith(".tmp")
        }) { "Corrupt WebP left temporary conversion file behind" }
        check(source.exists() && bad.exists())
        println("PASS: packaged Windows WebP VP8/VP8L CBZ reader and XTC/XTCH exports")
    } finally {
        temp.deleteRecursively()
    }
}
