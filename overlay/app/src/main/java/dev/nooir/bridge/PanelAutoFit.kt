package dev.nooir.bridge

/**
 * Conservative, deterministic orientation choice for a *detected* manga panel.
 *
 * AUTO_FIT is opt-in: legacy PORTRAIT/LANDSCAPE jobs are unchanged. It never
 * invents panel boundaries or crops artwork to fill the viewport. The same
 * decision and per-panel slice ordering run on Android and Windows.
 */
data class PanelFitSlice(val region: PageRegion, val landscape: Boolean)

object PanelAutoFit {
    private const val MIN_IMPROVEMENT_PERCENT = 125L

    /** Occupied screen fraction if the entire source rectangle is scaled to fit. */
    fun occupiedFraction(region: PageRegion, viewportWidth: Int, viewportHeight: Int): Double {
        require(region.width > 0 && region.height > 0 && viewportWidth > 0 && viewportHeight > 0)
        val scale = minOf(
            viewportWidth.toDouble() / region.width,
            viewportHeight.toDouble() / region.height,
        )
        return (region.width * scale) * (region.height * scale) /
            (viewportWidth.toDouble() * viewportHeight)
    }

    /**
     * Choose landscape only for a substantial, measurable improvement.
     * Prefer portrait on ties to avoid needless orientation switching.
     */
    fun landscapeFor(region: PageRegion, portraitWidth: Int, portraitHeight: Int): Boolean {
        require(portraitWidth > 0 && portraitHeight > 0)
        val portrait = occupiedFraction(region, portraitWidth, portraitHeight)
        val landscape = occupiedFraction(region, portraitHeight, portraitWidth)
        return landscape * 100.0 >= portrait * MIN_IMPROVEMENT_PERCENT.toDouble() &&
            landscape > portrait + 0.05
    }

    /**
     * Each confidently detected panel chooses its own viewport before
     * PanelViewportPlanner decides whether a very long panel needs slices.
     * Panels are never merged, reordered or silently cropped.
     */
    fun plan(
        panels: List<PageRegion>,
        portraitWidth: Int,
        portraitHeight: Int,
        rightToLeft: Boolean,
        overlapPercent: Int,
        preserveWholePanels: Boolean = false,
    ): List<PanelFitSlice> {
        require(panels.size <= 32) { "Too many detected panels" }
        return panels.flatMap { panel ->
            val landscape = landscapeFor(panel, portraitWidth, portraitHeight)
            val vw = if (landscape) portraitHeight else portraitWidth
            val vh = if (landscape) portraitWidth else portraitHeight
            PanelViewportPlanner.plan(panel, vw, vh, rightToLeft, overlapPercent, preserveWholePanels)
                .map { PanelFitSlice(it, landscape) }
        }
    }
}
