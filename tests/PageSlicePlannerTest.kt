import dev.nooir.bridge.PageRegion
import dev.nooir.bridge.PageSlicePlanner

private fun plan(
    sourceWidth: Int, sourceHeight: Int,
    viewWidth: Int = 480, viewHeight: Int = 800,
    layout: String = "WEBTOON", overlap: Int = 8,
    split: Boolean = false, rtl: Boolean = true,
) = PageSlicePlanner.plan(sourceWidth, sourceHeight, viewWidth, viewHeight,
    layout, split, rtl, overlap)

fun main() {
    val overlapping = plan(600, 3600)
    check(overlapping.first().top == 0 && overlapping.last().bottom == 3600)
    check(overlapping.size > 2)
    check(overlapping.all { it.left == 0 && it.right == 600 && it.height in 1..1000 })
    check(overlapping.zipWithNext().all { (a, b) ->
        b.top < a.bottom && b.top > a.top && b.bottom > a.bottom
    }) { "Every continuation should repeat a few rows with guaranteed forward progress" }

    val seamless = plan(600, 3600, overlap = 0)
    check(seamless.zipWithNext().all { (a, b) -> a.bottom == b.top })
    check(seamless.first().top == 0 && seamless.last().bottom == 3600)

    val landscape = plan(600, 3600, viewWidth = 800, viewHeight = 480)
    check(landscape.size > overlapping.size) { "Sideways reading displays shorter vertical slices" }
    check(landscape.first().height == 360)
    check(overlapping.first().height == 1000)

    // Small images are never cut or duplicated; zero-height and invalid plans fail.
    check(plan(500, 400, overlap = 50) == listOf(PageRegion(0, 0, 500, 400)))
    check(runCatching { plan(0, 200) }.isFailure)
    check(plan(600, 3600, overlap = 500).size > 2) // clamp at 50%, no infinite loop

    // Portrait manga pages in landscape mode must NOT shrink to a single
    // sideways screen; they are turned into repeated top/bottom panels.
    val sidewaysPage = PageSlicePlanner.plan(
        480, 800, 800, 480, "PAGED", false, true, 12, "LANDSCAPE",
    )
    check(sidewaysPage.size >= 3)
    check(sidewaysPage.first().top == 0 && sidewaysPage.last().bottom == 800)
    check(sidewaysPage.zipWithNext().all { (a, b) ->
        b.top < a.bottom && b.top > a.top
    })
    val sidewaysFit = PageSlicePlanner.plan(
        1600, 800, 800, 480, "PAGED", false, true, 12, "LANDSCAPE",
    )
    check(sidewaysFit.size == 1) { "Wide panorama must remain one page if spread split is off" }

    val sidewaysSpreads = PageSlicePlanner.plan(
        2400, 1700, 800, 480, "PAGED", true, true, 10, "LANDSCAPE",
    )
    check(sidewaysSpreads.size > 2)
    check(sidewaysSpreads.first().left == 1200)
    check(sidewaysSpreads.last().left == 0)

    val rightFirst = plan(1800, 1100, layout = "PAGED", split = true)
    check(rightFirst == listOf(PageRegion(900, 0, 1800, 1100), PageRegion(0, 0, 900, 1100)))
    val leftFirst = plan(1800, 1100, layout = "PAGED", split = true, rtl = false)
    check(leftFirst == rightFirst.reversed())
    check(plan(1800, 1100, layout = "PAGED", split = false).size == 1)

    val autoStrip = plan(480, 3000, layout = "AUTO")
    check(autoStrip.size > 1)
    check(plan(480, 800, layout = "AUTO").size == 1)
    println("Page slicing passed: portrait, landscape, overlap, gap-free, spreads and legacy paged mode")
}
