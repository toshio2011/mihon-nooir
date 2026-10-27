package dev.nooir.bridge

/**
 * Optional whitespace reduction *inside* a confidently detected panel.
 *
 * Unlike the gutter detector this never invents another panel. It only
 * reduces bright outer borders around sampled artwork, retains a generous
 * safety band and limits every side to 35% of the original panel. The
 * caller must opt in via cropWhiteMargins; full-page overviews are untouched.
 * Shared, Android-free geometry for future desktop parity.
 */
object PanelWhitespaceCrop {
    fun trim(panel: PageRegion, isInk: (Int, Int) -> Boolean): PageRegion {
        require(panel.width > 0 && panel.height > 0)
        val step = maxOf(2, minOf(panel.width, panel.height) / 240)
        var left = panel.right
        var top = panel.bottom
        var right = panel.left
        var bottom = panel.top
        var marked = 0
        for (y in panel.top until panel.bottom step step) {
            for (x in panel.left until panel.right step step) {
                if (!isInk(x, y)) continue
                left = minOf(left, x)
                top = minOf(top, y)
                right = maxOf(right, x + step)
                bottom = maxOf(bottom, y + step)
                marked++
            }
        }
        // Titles, sparse speech marks and sampling noise are ambiguous.
        if (marked < 12) return panel
        val guard = maxOf(step * 3, minOf(panel.width, panel.height) / 50)
        val maxTrimX = panel.width * 35 / 100
        val maxTrimY = panel.height * 35 / 100
        val candidate = PageRegion(
            maxOf(panel.left, minOf(left - guard, panel.left + maxTrimX)),
            maxOf(panel.top, minOf(top - guard, panel.top + maxTrimY)),
            minOf(panel.right, maxOf(right + guard, panel.right - maxTrimX)),
            minOf(panel.bottom, maxOf(bottom + guard, panel.bottom - maxTrimY)),
        )
        if (candidate.width <= 0 || candidate.height <= 0) return panel
        // The coarse first pass is only a *proposal*. Check every pixel that
        // would be discarded, including tiny lettering and pale pencil strokes
        // between sample lines. An excluded ink pixel makes trimming unsafe.
        // The callback caches one bitmap row; this requires no new full image.
        for (y in panel.top until panel.bottom) {
            for (x in panel.left until panel.right) {
                if (x >= candidate.left && x < candidate.right &&
                    y >= candidate.top && y < candidate.bottom) continue
                if (isInk(x, y)) return panel
            }
        }
        // Ignore negligible changes instead of adding per-panel reflow jitter.
        if (candidate.width.toLong() * candidate.height * 100 >=
            panel.width.toLong() * panel.height * 98) return panel
        return candidate
    }
}
