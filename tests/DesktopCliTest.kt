import dev.nooir.desktop.NooirDesktopCli
import dev.nooir.bridge.*
import dev.nooir.mangabridge.MangaConversionOptions
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO

private fun image(seed: Int): ByteArray {
    val img = BufferedImage(128, 192, BufferedImage.TYPE_INT_RGB)
    for (y in 0 until img.height) for (x in 0 until img.width) {
        val c = if ((x + y + seed) % 19 == 0) 0 else 255
        img.setRGB(x, y, (c shl 16) or (c shl 8) or c)
    }
    return ByteArrayOutputStream().also { check(ImageIO.write(img, "png", it)) }.toByteArray()
}
/**
 * Forge a valid PNG IHDR claiming a giant image while leaving a tiny IDAT.
 * No giant raster is allocated; metadata must be rejected BEFORE read().
 */
private fun inflatedPngHeader(input: ByteArray): ByteArray {
    val bytes = input.copyOf()
    require(String(bytes.copyOfRange(12, 16), Charsets.US_ASCII) == "IHDR")
    for (offset in listOf(16, 20)) {
        val number = 50_000
        for (i in 0..3) bytes[offset + i] =
            (number ushr (24 - i * 8)).toByte()
    }
    val crc = CRC32()
    crc.update(bytes, 12, 17)
    for (i in 0..3) bytes[29 + i] =
        (crc.value ushr (24 - i * 8)).toByte()
    return bytes
}
private fun le16(b: ByteArray, i: Int) = (b[i].toInt() and 255) or ((b[i+1].toInt() and 255) shl 8)
private fun zipEntries(file: File): List<String> = ZipFile(file).use { z -> z.entries().asSequence().map { it.name }.toList() }

fun main() {
    val root = Files.createTempDirectory("nooir-desktop-parity-").toFile()
    try {
        val input = File(root, "sample").apply { mkdirs() }
        File(input, "cover.png").writeBytes(image(1))
        val ch1 = File(input, "Chapter 1").apply { mkdirs() }
        File(ch1, "page 10.png").writeBytes(image(10))
        File(ch1, "page 2.png").writeBytes(image(2))
        val ch2 = File(input, "Chapter 2").apply { mkdirs() }
        File(ch2, "page 1.png").writeBytes(image(3))
        val options = MangaConversionOptions(
            layout = "PAGED", cropWhiteMargins = false, splitSpreads = false,
            includeCover = true, epubPagesPerXhtml = 1,
        )
        for (format in listOf(ExportFormat.XTC, ExportFormat.XTCH, ExportFormat.EPUB2, ExportFormat.EPUB, ExportFormat.CBZ)) {
            val output = File(root, "sample-${format.name}.${format.extension}")
            val args = arrayOf("--input", input.absolutePath, "--output", output.absolutePath,
                "--format", format.name, "--device", "X4", "--options-token", options.token(), "--title", "MangaNooir Test")
            NooirDesktopCli.main(args)
            check(output.length() > 100L)
            if (format == ExportFormat.XTC || format == ExportFormat.XTCH) {
                val bytes = output.readBytes()
                check(le16(bytes, 6) == 4) { "Wrong $format page count" }
                check(le16(bytes, 56 + 246) == 2) { "Wrong chapter count" }
                check(le16(bytes, 56 + 244) == 0) { "Cover must be first" }
                check(bytes.copyOfRange(0, 3).contentEquals("XTC".toByteArray()))
            } else {
                val entries = zipEntries(output)
                check(entries.any { it.endsWith("p00004.png") })
                if (format == ExportFormat.CBZ) {
                    check(entries.contains("ComicInfo.xml"))
                } else {
                    check(entries.first() == "mimetype")
                    check(entries.contains("OEBPS/package.opf"))
                    check(entries.contains(if (format == ExportFormat.EPUB2) "OEBPS/toc.ncx" else "OEBPS/nav.xhtml"))
                }
            }
            check(runCatching { NooirDesktopCli.main(args) }.isFailure) { "Existing export must not be overwritten" }
        }
        // Oversized image *metadata* must be rejected before allocating any
        // 50,000 x 50,000 pixel buffer, and must not publish a partial export.
        val malicious = File(root, "oversized-input").apply { mkdirs() }
        File(malicious, "page.png").writeBytes(inflatedPngHeader(image(1)))
        val refused = File(root, "oversized-output.epub")
        val rejection = runCatching {
            NooirDesktopCli.main(arrayOf("--input", malicious.absolutePath,
                "--output", refused.absolutePath, "--format", "EPUB2", "--device", "X4"))
        }.exceptionOrNull()
        check(rejection is IllegalArgumentException &&
            rejection.message?.contains("dimensions exceed safe desktop pixel limit") == true) {
            "CLI must reject dangerous image dimensions before decoding: $rejection"
        }
        check(!refused.exists()) { "Oversized image must not publish partial export" }
        check(root.listFiles().orEmpty().none { it.name.startsWith(".nooir-export-") }) {
            "Failed oversized export must clean up temporary files"
        }

        val cbz = File(root, "archive.cbz")
        ZipOutputStream(cbz.outputStream()).use { zip ->
            for (page in listOf("10.png" to image(10), "2.png" to image(2))) {
                zip.putNextEntry(ZipEntry(page.first))
                zip.write(page.second)
                zip.closeEntry()
            }
        }
        val cbzOutput = File(root, "archive.epub")
        NooirDesktopCli.main(arrayOf("--input", cbz.absolutePath, "--output", cbzOutput.absolutePath,
            "--format", "EPUB2", "--device", "X4"))
        check(zipEntries(cbzOutput).contains("OEBPS/images/p00002.png"))
        val png = GrayscalePng.encode(QuantizedPage(2, 8, ByteArray(16) { if (it % 2 == 0) 3 else 0 }, 2))
        val decoded = ImageIO.read(ByteArrayInputStream(png)) ?: error("Shared PNG not readable")
        check(decoded.width == 2 && decoded.height == 8)
        println("Desktop JVM conversion smoke tests passed for EPUB2/3, XTC, XTCH, CBZ, CBZ import and no-overwrite")
    } finally {
        root.deleteRecursively()
    }
}
