package dev.nooir.mangabridge

/** Bounded chapter illustration presets. Book covers retain their source quality. */
enum class NooirWebImageProfile(val maxEdge: Int, val jpegQuality: Int) {
    ORIGINAL(0, 100),
    BALANCED(1600, 82),
    SMALL(1000, 70);

    companion object {
        fun parse(value: String): NooirWebImageProfile =
            entries.firstOrNull { it.name == value }
                ?: error("Unsupported image size preset")
    }
}
