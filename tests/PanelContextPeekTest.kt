package dev.nooir.bridge

fun main() {
    val first = PageRegion(0, 0, 100, 80)
    val second = PageRegion(0, 82, 100, 180)
    val base = listOf(first, second)
    check(PanelContextPeek.expand(base, 100, 200, 0) == base)
    check(PanelContextPeek.expand(listOf(first), 100, 200, 8) == listOf(first))
    val peek = PanelContextPeek.expand(base, 100, 200, 8)
    check(peek.size == 2)
    check(peek[0].bottom > first.bottom && peek[1].top < second.top)
    check(peek[0].bottom <= first.bottom + first.height * 8 / 100 * 2)
    check(peek[1].top >= second.top - second.height * 8 / 100 * 2)
    check(peek.all { it.left >= 0 && it.top >= 0 && it.right <= 100 && it.bottom <= 200 })
    check(peek[0].left <= first.left && peek[0].right >= first.right)
    check(peek[1].top <= second.top && peek[1].bottom >= second.bottom)

    val far = listOf(PageRegion(0, 0, 100, 40), PageRegion(0, 160, 100, 200))
    check(PanelContextPeek.expand(far, 100, 200, 8) == far) {
        "Must not invent framing across a large undetected gap"
    }
    val side = listOf(PageRegion(0, 0, 90, 100), PageRegion(94, 0, 190, 100))
    val x = PanelContextPeek.expand(side, 200, 100, 8)
    check(x[0].right > 90 && x[1].left < 94)
    check(x[0].top == 0 && x[0].bottom == 100)
    check(x[1].top == 0 && x[1].bottom == 100)
    println("PASS: context peek repeats only narrow neighboring edges, never cuts original regions")
}
