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
        verifyNativeProfileReadback(folder)
        println("PASS: desktop finished-book XTC and XTCH readback and corrupt input")
    } finally { folder.deleteRecursively() }
}


/**
 * Exercise the shared saved-book decoder at native X3/X4 resolutions.
 * Host-side readback only; this is NOT a physical-device or UI acceptance.
 * Stream five pages from the real writer then seek first/middle/last out of order.
 */
private fun verifyNativeProfileReadback(folder: File) {
    val profiles = listOf("X4" to (480 to 800), "X3" to (528 to 792))
    for ((device, dimensions) in profiles) for (format in XtcFormat.entries) {
        val (width, height) = dimensions
        val tones = if (format == XtcFormat.XTC) 2 else 4
        fun pageFor(index: Int): QuantizedPage =
            QuantizedPage(width, height, ByteArray(width * height) { pixel ->
                val x = pixel % width
                val y = pixel / width
                ((x / 37 + y / 29 + index * 3) % tones).toByte()
            }, format.depth)
        val source = File(folder, "native-$device.${format.extension}")
        source.outputStream().use { sink ->
            XtcCodec.write(format, (0 until 5).asSequence().map { pageFor(it) },
                sink, folder, BookMetadata("Native $device preview test"))
        }
        val beforeSize = source.length()
        val inspector = XtcBookPreview(source)
        check(inspector.pageCount == 5 && inspector.format == format)
        for (index in listOf(4, 2, 0, 4, 1, 3, 0)) {
            val decoded = inspector.page(index)
            check(decoded.width == width && decoded.height == height)
            check(decoded.depth == format.depth)
            check(decoded.levels.contentEquals(pageFor(index).levels)) {
                "Saved $device $format frame $index differs from source pixels"
            }
        }
        check(runCatching { inspector.page(-1) }.isFailure)
        check(runCatching { inspector.page(5) }.isFailure)

        // Corrupt COPIES only; the original export must remain intact.
        fun readU64(file: java.io.RandomAccessFile, at: Long): Long {
            file.seek(at)
            var n = 0L
            repeat(8) { i ->
                n = n or (file.readUnsignedByte().toLong() shl (8 * i))
            }
            return n
        }
        val (table, data) = java.io.RandomAccessFile(source, "r").use { file ->
            readU64(file, 24L) to readU64(file, 32L)
        }
        fun mustReject(
            case: String,
            mutate: (java.io.RandomAccessFile) -> Unit,
            read: (XtcBookPreview) -> Unit = { it.page(0) },
        ) {
            val copy = File(folder, "corrupt-$device-${format.extension}-$case")
            source.copyTo(copy, overwrite = true)
            try {
                java.io.RandomAccessFile(copy, "rw").use(mutate)
                check(runCatching { read(XtcBookPreview(copy)) }.isFailure) {
                    "$device $format accepted a corrupt saved book ($case)"
                }
            } finally {
                check(copy.delete())
            }
        }
        mustReject("overflow-header-offset", { file ->
            file.seek(31L); file.write(0x80)
        })
        mustReject("overflow-page-offset", { file ->
            file.seek(table + 7); file.write(0x80)
        })
        mustReject("huge-dimensions", { file ->
            file.seek(table + 12)
            repeat(2) { file.write(0xff); file.write(0x1f) }
        })
        mustReject("zero-page-length", { file ->
            file.seek(table + 8); file.writeInt(0)
        })
        mustReject("wrong-embedded-magic", { file ->
            file.seek(data); file.write('Q'.code)
        })
        mustReject("truncated-final-page", { file ->
            file.setLength(file.length() - 1)
        }, { it.page(4) })
        check(source.length() == beforeSize)
        check(XtcBookPreview(source).page(4).levels.contentEquals(pageFor(4).levels)) {
            "Malformed-file tests must not modify the original export"
        }
    }
    println("PASS: X3/X4 native first/middle/last XTC/XTCH readback and bounded corruption rejection")
}
