package dev.nooir.desktop

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

/** Bounded image decoder used by the Windows reader, separated from the current
 * Android-main Desktop CLI to preserve the newer shared panel conversion path. */
internal object DesktopImageDecoder {
    fun decode(bytes: ByteArray, maxPixels: Long = 25_000_000L): BufferedImage {
        require(bytes.isNotEmpty() && maxPixels in 1L..25_000_000L) {
            "Invalid image data or desktop pixel budget"
        }
        val imageInput = ImageIO.createImageInputStream(ByteArrayInputStream(bytes))
            ?: error("Cannot inspect the page image")
        imageInput.use { stream ->
            val readers = ImageIO.getImageReaders(stream)
            require(readers.hasNext()) { "Unsupported image codec" }
            val decoder = readers.next()
            try {
                decoder.setInput(stream, false, true)
                val width = decoder.getWidth(0)
                val height = decoder.getHeight(0)
                require(width > 0 && height > 0 &&
                    width.toLong() * height.toLong() <= maxPixels) {
                    "Image dimensions exceed safe desktop pixel limit"
                }
                val image = decoder.read(0)
                    ?: error("Cannot decode the requested image")
                require(image.width > 0 && image.height > 0 &&
                    image.width.toLong() * image.height.toLong() <= maxPixels) {
                    image.flush()
                    "Decoded image exceeds safe desktop pixel limit"
                }
                return image
            } finally {
                decoder.dispose()
            }
        }
    }
}
