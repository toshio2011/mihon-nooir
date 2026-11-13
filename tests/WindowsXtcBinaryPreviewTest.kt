package dev.nooir.bridge

import java.io.File

fun main() {
    val folder = File(System.getProperty("java.io.tmpdir"),
        "nooir-desktop-xtc-preview-" + System.nanoTime())
    check(folder.mkdirs())
    try {
        for (format in XtcFormat.entries) {
            val levels = ByteArray(11 * 16) { i ->
                (i % (if (format == XtcFormat.XTC) 2 else 4)).toByte()
            }
            val first = QuantizedPage(11, 16, levels, format.depth)
            val second = QuantizedPage(11, 16,
                levels.map { ((if (format == XtcFormat.XTC) 1 else 3) -
                    (it.toInt() and 255)).toByte() }.toByteArray(), format.depth)
            val file = File(folder, "test.${format.extension}")
            file.outputStream().use { stream ->
                XtcCodec.write(format, sequenceOf(first, second), stream, folder,
                    BookMetadata("Desktop preview"))
            }
            val reader = XtcBookPreview(file)
            check(reader.format == format && reader.pageCount == 2)
            check(reader.page(0).levels.contentEquals(first.levels))
            check(reader.page(1).levels.contentEquals(second.levels))
            check(runCatching { reader.page(2) }.isFailure)
            val invalid = File(folder, "invalid.${format.extension}")
            invalid.writeBytes(byteArrayOf(0x58, 0x54, 0x43, 0))
            check(runCatching { XtcBookPreview(invalid) }.isFailure)
        }
        println("PASS: desktop finished-book XTC and XTCH readback and corrupt input")
    } finally { folder.deleteRecursively() }
}
