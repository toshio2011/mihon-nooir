package dev.nooir.mangabridge

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dev.nooir.bridge.ReflowEpubWriter
import java.io.ByteArrayOutputStream

/**
 * Single-illustration downsample + resize; never holds the full chapter in memory.
 * Resizing is opt-in via a bounded preset and applies to embedded chapter artwork,
 * not covers. Original and animated GIF image bytes stay unchanged.
 */
internal object NooirWebImageOptimizer {
    fun optimize(
        asset: ReflowEpubWriter.ImageAsset,
        profile: NooirWebImageProfile,
    ): ReflowEpubWriter.ImageAsset {
        if (profile == NooirWebImageProfile.ORIGINAL || asset.mime == "image/gif") return asset
        require(asset.mime == "image/jpeg" || asset.mime == "image/png") {
            "Image compression supports JPEG and PNG"
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(asset.bytes, 0, asset.bytes.size, bounds)
        require(bounds.outWidth in 1..12000 && bounds.outHeight in 1..12000 &&
            bounds.outWidth.toLong() * bounds.outHeight.toLong() <= 16_000_000L) {
            "Image dimensions exceed the safe pixel budget"
        }
        val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
        if (maxSide <= profile.maxEdge) return asset

        var sample = 1
        while (maxSide / (sample * 2) >= profile.maxEdge) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val decoded = BitmapFactory.decodeByteArray(asset.bytes, 0, asset.bytes.size, options)
            ?: error("Could not decode chapter illustration")
        try {
            val factor = minOf(
                1.0, profile.maxEdge.toDouble() / maxOf(decoded.width, decoded.height),
            )
            val width = (decoded.width * factor).toInt().coerceAtLeast(1)
            val height = (decoded.height * factor).toInt().coerceAtLeast(1)
            val bitmap = if (width == decoded.width && height == decoded.height) decoded
                else Bitmap.createScaledBitmap(decoded, width, height, true)
            try {
                val keepPng = asset.mime == "image/png" &&
                    BitmapFactory.decodeByteArray(
                        asset.bytes, 0, asset.bytes.size,
                        BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    ) == null && asset.extension == "png"
                // A PNG may carry transparency; decode with ARGB_8888 for it
                // in the next branch instead of corrupting alpha as JPEG.
                val output = ByteArrayOutputStream()
                val encoding = if (asset.mime == "image/png") Bitmap.CompressFormat.PNG
                    else Bitmap.CompressFormat.JPEG
                require(bitmap.compress(encoding, profile.jpegQuality, output)) {
                    "Could not resize chapter illustration"
                }
                val bytes = output.toByteArray()
                require(bytes.size in 1..2_000_000) { "Optimized illustration is too large" }
                return ReflowEpubWriter.ImageAsset(bytes,
                    if (encoding == Bitmap.CompressFormat.PNG) "png" else "jpg",
                    if (encoding == Bitmap.CompressFormat.PNG) "image/png" else "image/jpeg")
            } finally {
                if (bitmap !== decoded) bitmap.recycle()
            }
        } finally {
            decoded.recycle()
        }
    }
}
