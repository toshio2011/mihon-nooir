import dev.nooir.bridge.PageRegion
import dev.nooir.bridge.PanelAutoFit

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

    // One detected panel = exactly one screen, even if an old viewport
    // planner or safe-corridor slicer would have cut this elongated panel.
    val veryWide = PageRegion(15, 30, 3515, 260)
    val veryTall = PageRegion(17, 27, 230, 3627)
    val planLargest = PanelAutoFit.planWholePanelsForMaximumFill(
        listOf(portrait, mildlyWide, veryWide, veryTall, square), 480, 800,
    )
    check(planLargest.size == 5)
    check(planLargest.map { it.region } ==
        listOf(portrait, mildlyWide, veryWide, veryTall, square)) {
        "A detected panel must not be cropped, merged, reordered or re-sliced"
    }
    check(planLargest.map { it.landscape } == listOf(false, true, true, false, false))
    check(!PanelAutoFit.landscapeForMaximumFill(square, 480, 800))
    check(runCatching {
        PanelAutoFit.planWholePanelsForMaximumFill(List(33) { portrait }, 480, 800)
    }.isFailure)
    check(runCatching {
        PanelAutoFit.landscapeForMaximumFill(PageRegion(0, 0, 0, 3), 480, 800)
    }.isFailure)

    check(runCatching { PanelAutoFit.plan(List(33) { portrait }, 480, 800, true, 8) }.isFailure)
    check(runCatching { PanelAutoFit.landscapeFor(PageRegion(0, 0, 0, 3), 480, 800) }.isFailure)
    println("Panel Auto Fit passed: maximum whole-panel screen fill, legacy behavior, portrait tie, no-crop bounds and limits")
}
