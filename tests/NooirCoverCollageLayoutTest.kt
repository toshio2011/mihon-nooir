import dev.nooir.mangabridge.NooirCoverCollageLayout

fun main() {
    for (count in 2..4) {
        val list = NooirCoverCollageLayout.tiles(count)
        check(list.size == count)
        for ((i, a) in list.withIndex()) {
            check(a.x >= 0 && a.y >= 0 && a.width > 0 && a.height > 0)
            check(a.x + a.width <= 800 && a.y + a.height <= 1120)
            for (b in list.drop(i + 1)) {
                val overlapX = maxOf(a.x, b.x) < minOf(a.x + a.width, b.x + b.width)
                val overlapY = maxOf(a.y, b.y) < minOf(a.y + a.height, b.y + b.height)
                check(!(overlapX && overlapY)) { "Overlapping book-cover tiles" }
            }
        }
        check(list.sumOf { it.width * it.height } > 500_000)
    }
    check(runCatching { NooirCoverCollageLayout.tiles(1) }.isFailure)
    check(runCatching { NooirCoverCollageLayout.tiles(5) }.isFailure)
    check(NooirCoverCollageLayout.tiles(2) == NooirCoverCollageLayout.tiles(2))
    println("PASS: 2–4 book-cover montage layouts, page bounds and independent tiles")
}
