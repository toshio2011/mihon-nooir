import dev.nooir.bridge.PageRegion
import dev.nooir.bridge.PanelAutoFit
import dev.nooir.bridge.PanelSafeSlicePlanner

fun main() {
    val portrait = PageRegion(0, 0, 250, 600)
    val wide = PageRegion(0, 0, 600, 300)
    val square = PageRegion(0, 0, 400, 400)
    check(!PanelAutoFit.landscapeFor(portrait, 480, 800))
    check(PanelAutoFit.landscapeFor(wide, 480, 800))
    check(!PanelAutoFit.landscapeFor(square, 480, 800)) {
        "Auto Fit should prefer portrait when screen use is effectively tied"
    }
    val p = PanelAutoFit.occupiedFraction(wide, 480, 800)
    val l = PanelAutoFit.occupiedFraction(wide, 800, 480)
    check(l > p * 1.25)
    val panels = listOf(portrait, wide)
    val planned = PanelAutoFit.plan(panels, 480, 800, true, 8)
    check(planned.isNotEmpty())
    check(planned.first().region == portrait && !planned.first().landscape)
    check(planned.last().region == wide && planned.last().landscape)
    check(planned.all { it.region.width > 0 && it.region.height > 0 })
    // AUTO_FIT may rotate a panel, but must not slice its contents when
    // Android requests whole-panel preservation.
    val elongated = listOf(PageRegion(0, 0, 3000, 200), PageRegion(0, 0, 180, 3000))
    val intact = PanelAutoFit.plan(elongated, 480, 800, true, 8, preserveWholePanels = true)
    check(intact.map { it.region } == elongated)
    check(intact.first().landscape && !intact.last().landscape)
    // Old Auto Fit selected portrait for mildly wide panels even when a full
    // landscape panel visibly occupied MORE of the screen (but under 25%).
    // Only Panel-by-Panel + AUTO_FIT now opts into the maximum-fill algorithm.
    val mildlyWide = PageRegion(0, 0, 500, 480)
    check(!PanelAutoFit.landscapeFor(mildlyWide, 480, 800)) {
        "Legacy conservative orientation should remain unchanged for Row by Row"
    }
    check(PanelAutoFit.landscapeForMaximumFill(mildlyWide, 480, 800)) {
        "Maximum fill should use landscape even with less than 25% gain"
    }
    val portraitFraction = PanelAutoFit.occupiedFraction(mildlyWide, 480, 800)
    val landscapeFraction = PanelAutoFit.occupiedFraction(mildlyWide, 800, 480)
    check(landscapeFraction > portraitFraction)
    check(landscapeFraction < portraitFraction * 1.25)

    // A tall panel is safely cut at real white corridors, producing a
    // short final section. Refit AFTER cutting so that final section can
    // become landscape even though the original panel was portrait.
    val veryTall = PageRegion(0, 0, 500, 2000)
    val originalFit = PanelAutoFit.plan(listOf(veryTall), 480, 800,
        rightToLeft = true, overlapPercent = 0, preserveWholePanels = true).single()
    check(!originalFit.landscape)
    val pieces = PanelSafeSlicePlanner.plan(
        veryTall, 480, 800, rightToLeft = true, overlapPercent = 0,
    ) { _, y -> y !in 830..836 && y !in 1663..1669 }
    check(pieces.size == 3) { "Two clean seams should produce three pieces: $pieces" }
    check(pieces.first().top == 0 && pieces.last().bottom == veryTall.bottom)
    check(pieces.zipWithNext().all { (a, b) -> a.bottom == b.top }) {
        "Safe source pixels must remain in order with no lost artwork"
    }
    val refitted = PanelAutoFit.refitFinalSlices(pieces, 480, 800)
    check(refitted.map { it.region } == pieces) {
        "Auto Fit cannot change safe cuts, their RTL order or overlap"
    }
    check(refitted.map { it.landscape } == listOf(false, false, true)) {
        "A short final slice must be allowed to turn landscape: $refitted"
    }
    check(PanelAutoFit.occupiedFraction(pieces.last(), 800, 480) >
        PanelAutoFit.occupiedFraction(pieces.last(), 480, 800))
    val mildRefit = PanelAutoFit.refitFinalSlices(
        listOf(portrait, mildlyWide, square), 480, 800)
    check(mildRefit.map { it.landscape } == listOf(false, true, false))
    check(!PanelAutoFit.landscapeForMaximumFill(square, 480, 800))
    check(runCatching {
        PanelAutoFit.refitFinalSlices(List(33) { portrait }, 480, 800)
    }.isFailure)
    check(runCatching {
        PanelAutoFit.refitFinalSlices(emptyList(), 480, 800)
    }.isFailure)
    check(runCatching {
        PanelAutoFit.landscapeForMaximumFill(PageRegion(0, 0, 0, 3), 480, 800)
    }.isFailure)

    check(runCatching { PanelAutoFit.plan(List(33) { portrait }, 480, 800, true, 8) }.isFailure)
    check(runCatching { PanelAutoFit.landscapeFor(PageRegion(0, 0, 0, 3), 480, 800) }.isFailure)
    println("Panel Auto Fit passed: safe splitting followed by independent maximum-fill orientation, legacy row threshold and portrait ties")
}
