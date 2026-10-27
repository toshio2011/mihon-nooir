import dev.nooir.bridge.PageRegion
import dev.nooir.bridge.PanelSafeSlicePlanner

private fun covered(parts: List<PageRegion>, whole: PageRegion) {
    check(parts.isNotEmpty())
    check(parts.all { it.width > 0 && it.height > 0 &&
        it.left >= whole.left && it.top >= whole.top &&
        it.right <= whole.right && it.bottom <= whole.bottom })
    for (y in whole.top until whole.bottom step maxOf(1, whole.height / 95))
        for (x in whole.left until whole.right step maxOf(1, whole.width / 95))
            check(parts.any { x in it.left until it.right && y in it.top until it.bottom }) {
                "Unsafe panel gap ($x, $y)"
            }
}

fun main() {
    val normal = PageRegion(0, 0, 250, 450)
    check(PanelSafeSlicePlanner.plan(normal, 480, 800, true, 8) { _, _ -> true } == listOf(normal))

    val tall = PageRegion(0, 0, 300, 2600)
    val tallCut = PanelSafeSlicePlanner.plan(tall, 480, 800, true, 0) { _, y ->
        y % 500 !in 496..504
    }
    check(tallCut.size >= 5) { "Clearly empty horizontal gutters must allow readable sections" }
    check(tallCut.first().top == 0 && tallCut.last().bottom == tall.bottom)
    check(tallCut.zipWithNext().all { (a, b) -> a.bottom == b.top })
    covered(tallCut, tall)

    val overlapped = PanelSafeSlicePlanner.plan(tall, 480, 800, true, 12) { _, y ->
        y % 500 !in 496..504
    }
    check(overlapped.size > 1)
    check(overlapped.zipWithNext().all { (a, b) -> b.top < a.bottom && b.top > a.top })
    covered(overlapped, tall)

    // A one-pixel strand across the middle of a white strip must not be cut.
    // The planner may use a different clean row, but never the inked row.
    val protected = PanelSafeSlicePlanner.plan(tall, 480, 800, true, 0) { x, y ->
        y % 500 !in 496..504 || (x == 133 && y == 500)
    }
    check(protected.all { part ->
        part.bottom == tall.bottom || kotlin.math.abs(part.bottom - 500) > 1
    }) { "Never cut through a speech balloon tail" }
    covered(protected, tall)

    // No entirely clean strip -> return original art, not an arbitrary slice.
    check(PanelSafeSlicePlanner.plan(tall, 480, 800, true, 8) { _, _ -> true } == listOf(tall))

    val wide = PageRegion(0, 0, 2600, 300)
    val ink: (Int, Int) -> Boolean = { x, _ -> x % 500 !in 496..504 }
    val ltr = PanelSafeSlicePlanner.plan(wide, 800, 480, false, 0, ink)
    val rtl = PanelSafeSlicePlanner.plan(wide, 800, 480, true, 0, ink)
    check(ltr.size >= 5 && rtl == ltr.reversed())
    check(rtl.first().right == wide.right && rtl.last().left == wide.left)
    covered(rtl, wide)

    // Horizontal stitching must not reverse the top-to-bottom reading order.
    check(tallCut.first().top < tallCut.last().top)

    // Too many potential sections must not create unbounded allocations.
    val enormous = PageRegion(0, 0, 300, 100_000)
    check(PanelSafeSlicePlanner.plan(enormous, 480, 800, true, 0) { _, y ->
        y % 500 !in 496..504
    } == listOf(enormous))

    check(runCatching {
        PanelSafeSlicePlanner.plan(PageRegion(0, 0, 0, 100), 480, 800, true, 8) { _, _ -> false }
    }.isFailure)
    println("Shared pixel-safe slicing passed: horizontal/vertical, RTL, overlap, no missing art, refused unsafe cuts")
}
