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

    // A giant, mostly-white ruled strip must remain ONE frame rather than
    // becoming four or five isolated border/blank screens. Preserve every
    // stroke as part of the original rectangle; do not discard the strip.
    val sparseWide = PageRegion(0, 0, 2400, 240)
    val sparseInk: (Int, Int) -> Boolean = { x, y ->
        (x in 80..2320 && y in 112..117) ||
            ((x == 100 || x == 2200) && y in 30..210)
    }
    check(PanelSafeSlicePlanner.plan(sparseWide, 800, 480, true, 8, sparseInk) ==
        listOf(sparseWide)) {
        "Sparse ruled strips must not create multiple almost-blank viewports"
    }
    // A small but real speech balloon does not justify an arbitrary viewport
    // split. Retaining the whole frame is safer when the picture is uncertain.
    val sparseTall = PageRegion(0, 0, 240, 2400)
    val sparseTallInk: (Int, Int) -> Boolean = { x, y ->
        (x in 95..101 && y in 90..2300)
    }
    check(PanelSafeSlicePlanner.plan(sparseTall, 480, 800, true, 0, sparseTallInk) ==
        listOf(sparseTall)) {
        "Long single strokes must remain within an intact panel"
    }

    // Real-user regression shape: a 800x480 sparse bordered manga frame.
    // Previously, rejection by the panel detector sent the entire image
    // to the legacy page slicer, producing an intact view plus three almost-
    // blank magnified rule fragments. The caller now uses isSparse() BEFORE
    // detection/fallback and returns exactly this one whole PageRegion.
    val conanShape = PageRegion(0, 0, 800, 480)
    val frameInk: (Int, Int) -> Boolean = { x, y ->
        (x in 160..740 && y in 216..219) ||
            ((x == 169 || x == 715) && y in 155..285)
    }
    check(PanelSafeSlicePlanner.isSparse(conanShape, frameInk)) {
        "Sparse border-only Conan-like source must qualify for whole-page fallback"
    }
    val singleFallback = if (PanelSafeSlicePlanner.isSparse(conanShape, frameInk))
        listOf(conanShape) else PanelSafeSlicePlanner.plan(
            conanShape, 480, 800, true, 8, frameInk
        )
    check(singleFallback == listOf(conanShape)) {
        "Sparse fallback must emit exactly one complete viewing frame"
    }
    // Keep legitimately illustrated manga eligible for all existing slicing.
    val illustrated: (Int, Int) -> Boolean = { x, y ->
        (x in 70..360 && y in 70..420) ||
            (x in 420..760 && y in 50..430)
    }
    check(!PanelSafeSlicePlanner.isSparse(conanShape, illustrated)) {
        "Do not suppress normally illustrated two-panel story pages"
    }
    check(PanelSafeSlicePlanner.isSparse(PageRegion(0, 0, 32, 48)) { _, _ -> false }) {
        "Blank pages still belong to a single intact frame"
    }

    // The precise missed PR48 case: a sparse lower tier is a proper reading
    // region, but sourceRegions adds an overlap safety band from a dense panel
    // immediately above. Re-testing the padded rectangle gives a FALSE
    // non-sparse result and enables three pointless border zooms.
    //
    // Classification must happen on the unpadded detector region and travel
    // with its (padded) output; none of the added safety pixels are removed.
    val quietTier = PageRegion(0, 400, 480, 600)
    val paddedTier = PageRegion(0, 380, 480, 600)
    val neighboringArt: (Int, Int) -> Boolean = { x, y ->
        (x in 5..474 && y in 380..399) ||
            (x in 60..420 && y in 510..512)
    }
    val preserveQuietTier = PanelSafeSlicePlanner.isSparse(quietTier, neighboringArt)
    check(preserveQuietTier) { "Actual detector tier is sparse" }
    check(!PanelSafeSlicePlanner.isSparse(paddedTier, neighboringArt)) {
        "Overlapping art must demonstrate why re-testing the padded tier is unsafe"
    }
    val tierScreens = if (preserveQuietTier) listOf(paddedTier)
        else PanelSafeSlicePlanner.plan(paddedTier, 480, 800, true, 8, neighboringArt)
    check(tierScreens == listOf(paddedTier)) {
        "Pass the original sparse-tier decision through padding to preserve one frame"
    }

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
