import dev.nooir.bridge.PageRegion
import dev.nooir.bridge.PanelViewportPlanner

private fun covered(regions: List<PageRegion>, area: PageRegion) {
    check(regions.isNotEmpty())
    check(regions.all { it.left >= area.left && it.right <= area.right &&
        it.top >= area.top && it.bottom <= area.bottom &&
        it.width > 0 && it.height > 0 })
    for (y in area.top until area.bottom step maxOf(1, area.height / 50))
        for (x in area.left until area.right step maxOf(1, area.width / 50))
            check(regions.any { x in it.left until it.right && y in it.top until it.bottom }) {
                "Panel slice omitted artwork at $x,$y"
            }
}
fun main() {
    val screenW = 480
    val screenH = 800
    val ordinary = PageRegion(0, 0, 300, 400)
    check(PanelViewportPlanner.plan(ordinary, screenW, screenH, true, 8) == listOf(ordinary))
    val tall = PageRegion(5, 7, 305, 2507)
    val tallPages = PanelViewportPlanner.plan(tall, screenW, screenH, true, 8)
    check(tallPages.size > 1 && tallPages.first().top == 7 && tallPages.last().bottom == 2507)
    check(tallPages.zipWithNext().all { (a, b) -> b.top < a.bottom && b.top > a.top })
    check(tallPages.all { it.left == 5 && it.right == 305 })
    covered(tallPages, tall)
    val wide = PageRegion(7, 9, 3007, 309)
    val rtl = PanelViewportPlanner.plan(wide, screenW, screenH, true, 8)
    val ltr = PanelViewportPlanner.plan(wide, screenW, screenH, false, 8)
    check(rtl.size > 1 && rtl == ltr.reversed())
    check(rtl.first().right == wide.right && rtl.last().left == wide.left)
    check(ltr.zipWithNext().all { (a, b) -> b.left < a.right && b.left > a.left })
    covered(rtl, wide)
    val horizontal = PanelViewportPlanner.plan(ordinary, 800, 480, true, 8)
    check(horizontal.isNotEmpty())
    check(runCatching { PanelViewportPlanner.plan(PageRegion(0, 0, 1, 300000), 480, 800, true, 8) }.isFailure) {
        "Unbounded extreme panel should fail clearly rather than generate infinite output"
    }
    println("Panel viewport Phase B passed: one panel/screen, tall overlap, wide RTL/LTR, full coverage, segment cap")
}
