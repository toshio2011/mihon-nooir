package dev.nooir.mangabridge

import dev.nooir.bridge.ReflowEpubWriter
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.IIOImage

/** JVM image adapters for the released Android web engine. No network or Android runtime. */
internal object DesktopWebImageCodec {
    fun decode(asset: ReflowEpubWriter.ImageAsset): BufferedImage {
        require(asset.bytes.size in 1..2_000_000)
        require(asset.mime in setOf("image/png", "image/jpeg"))
        val stream = ImageIO.createImageInputStream(asset.bytes.inputStream()) ?: error("Invalid image stream")
        stream.use {
            val readers = ImageIO.getImageReaders(stream)
            require(readers.hasNext()) { "Unsupported image" }
            val reader = readers.next()
            try {
                reader.setInput(stream, true, true)
                val expected = if (asset.mime == "image/png") "png" else "jpeg"
                require(reader.formatName.lowercase() == expected) { "Image MIME mismatch" }
                val w = reader.getWidth(0); val h = reader.getHeight(0)
                require(w in 1..12000 && h in 1..12000 && w.toLong()*h <= 16_000_000L) {
                    "Image dimensions exceed safe pixel budget"
                }
                return reader.read(0) ?: error("Cannot decode image")
            } finally { reader.dispose() }
        }
    }

    fun encode(image: BufferedImage, png: Boolean, quality: Int): ReflowEpubWriter.ImageAsset {
        val bytes = ByteArrayOutputStream()
        if (png) check(ImageIO.write(image, "png", bytes))
        else {
            val writers = ImageIO.getImageWritersByFormatName("jpeg")
            check(writers.hasNext())
            val writer = writers.next()
            try {
                ImageIO.createImageOutputStream(bytes).use { out ->
                    writer.output = out
                    val param = writer.defaultWriteParam
                    param.compressionMode = ImageWriteParam.MODE_EXPLICIT
                    param.compressionQuality = quality.coerceIn(1, 100) / 100f
                    writer.write(null, IIOImage(image, null, null), param)
                }
            } finally { writer.dispose() }
        }
        return ReflowEpubWriter.ImageAsset(bytes.toByteArray(),
            if (png) "png" else "jpg", if (png) "image/png" else "image/jpeg")
    }
}

/** Matches the Android web engine's adapter API, backed by JVM ImageIO. */
internal object NooirWebImageOptimizer {
    fun optimize(asset: ReflowEpubWriter.ImageAsset, profile: NooirWebImageProfile): ReflowEpubWriter.ImageAsset {
        if (profile == NooirWebImageProfile.ORIGINAL || asset.mime == "image/gif") return asset
        val decoded = DesktopWebImageCodec.decode(asset)
        try {
            val edge = maxOf(decoded.width, decoded.height)
            if (edge <= profile.maxEdge) return asset
            val scale = profile.maxEdge.toDouble() / edge
            val png = asset.mime == "image/png"
            val resized = BufferedImage((decoded.width*scale).toInt().coerceAtLeast(1),
                (decoded.height*scale).toInt().coerceAtLeast(1),
                if (png) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB)
            try {
                val g = resized.createGraphics()
                try {
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                    g.drawImage(decoded,0,0,resized.width,resized.height,null)
                } finally { g.dispose() }
                val result = DesktopWebImageCodec.encode(resized, png, profile.jpegQuality)
                return if (result.bytes.size in 1 until asset.bytes.size && result.bytes.size <= 2_000_000) result else asset
            } finally { resized.flush() }
        } finally { decoded.flush() }
    }
}

internal object NooirCoverCollage {
    fun compose(covers: List<ReflowEpubWriter.ImageAsset>): ReflowEpubWriter.ImageAsset {
        val tiles = NooirCoverCollageLayout.tiles(covers.size)
        val canvas = BufferedImage(800,1120,BufferedImage.TYPE_INT_RGB)
        try {
            val g = canvas.createGraphics()
            try {
                g.color = Color(20,23,33); g.fillRect(0,0,800,1120)
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                for ((index, asset) in covers.withIndex()) {
                    require(asset.bytes.size <= 1_500_000)
                    val t = tiles[index]
                    val decoded = DesktopWebImageCodec.decode(asset)
                    try {
                        g.color = Color(39,44,58)
                        g.fillRoundRect(t.x,t.y,t.width,t.height,17,17)
                        val factor = minOf((t.width-14).toDouble()/decoded.width,(t.height-14).toDouble()/decoded.height)
                        val w = (decoded.width*factor).toInt().coerceAtLeast(1)
                        val h = (decoded.height*factor).toInt().coerceAtLeast(1)
                        g.drawImage(decoded,t.x+(t.width-w)/2,t.y+(t.height-h)/2,w,h,null)
                    } finally { decoded.flush() }
                }
            } finally { g.dispose() }
            for (quality in listOf(88,78,68)) {
                val result = DesktopWebImageCodec.encode(canvas,false,quality)
                if (result.bytes.size in 1..1_500_000) return result
            }
            error("Combined cover exceeds safe EPUB image budget")
        } finally { canvas.flush() }
    }
}
