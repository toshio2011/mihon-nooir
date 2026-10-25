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
    check(runCatching { PanelAutoFit.plan(List(33) { portrait }, 480, 800, true, 8) }.isFailure)
    check(runCatching { PanelAutoFit.landscapeFor(PageRegion(0, 0, 0, 3), 480, 800) }.isFailure)
    println("Panel Auto Fit passed: per-panel portrait/landscape selection, no-crop bounds, conservative tie and limits")
}
