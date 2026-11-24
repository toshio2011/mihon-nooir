package dev.nooir.mangabridge

/** Pure geometry shared by preview and EPUB cover rendering. */
internal object NooirCoverCollageLayout {
    data class Tile(val x: Int, val y: Int, val width: Int, val height: Int)

    fun tiles(count: Int, canvasWidth: Int = 800, canvasHeight: Int = 1120): List<Tile> {
        require(count in 2..4) { "A combined cover needs 2–4 distinct source covers" }
        require(canvasWidth >= 200 && canvasHeight >= 200)
        val margin = (canvasWidth / 32).coerceAtLeast(8)
        val gap = margin / 2
        val availableW = canvasWidth - margin * 2
        val availableH = canvasHeight - margin * 2
        val leftW = (availableW - gap) / 2
        val rightW = availableW - gap - leftW
        val upperH = (availableH - gap) / 2
        val lowerH = availableH - gap - upperH
        return when (count) {
            2 -> listOf(
                Tile(margin, margin, leftW, availableH),
                Tile(margin + leftW + gap, margin, rightW, availableH),
            )
            3 -> listOf(
                Tile(margin, margin, leftW, availableH),
                Tile(margin + leftW + gap, margin, rightW, upperH),
                Tile(margin + leftW + gap, margin + upperH + gap, rightW, lowerH),
            )
            else -> listOf(
                Tile(margin, margin, leftW, upperH),
                Tile(margin + leftW + gap, margin, rightW, upperH),
                Tile(margin, margin + upperH + gap, leftW, lowerH),
                Tile(margin + leftW + gap, margin + upperH + gap, rightW, lowerH),
            )
        }
    }
}
