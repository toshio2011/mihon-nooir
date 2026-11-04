import dev.nooir.bridge.PageRegion
import dev.nooir.bridge.PanelDetectionCore
import dev.nooir.bridge.PanelDebugEvent

/**
 * Synthetic geometry matching ONLY the failure shape from an opt-in private
 * One Piece diagnostic. No source manga images, copyrighted text or pixels
 * are embedded. This test drives the pure post-pass directly.
 */
fun main() {
    val w = 593
    val h = 900
    val rows = listOf(
        PageRegion(0, 0, w, 288),
        PageRegion(0, 288, w, 563),
        PageRegion(0, 563, w, 624),
        PageRegion(0, 624, w, 664),
        PageRegion(0, 664, w, 900),
    )
    val panels = listOf(
        PageRegion(317, 0, w, 288),
        PageRegion(0, 0, 317, 288),
        PageRegion(0, 288, w, 563),
        PageRegion(330, 563, w, 624),
        PageRegion(0, 563, 330, 624),
        PageRegion(454, 624, w, 664),
        PageRegion(344, 624, 454, 664),
        PageRegion(144, 624, 344, 664),
        PageRegion(0, 624, 144, 664),
        PageRegion(328, 664, w, 900),
        PageRegion(0, 664, 328, 900),
    )

    val gray = ByteArray(w * h) { 110.toByte() }
    // The full-height story-panel gutter is clear across the shallow
    // dialogue row, even though that row has fragmented candidates.
    for (y in 563 until h) for (x in 328..330)
        gray[y * w + x] = 255.toByte()

    fun coverageOnce(parts: List<PageRegion>) {
        check(parts.sumOf { it.width.toLong() * it.height } == w.toLong() * h)
        for (y in 0 until h step 7) for (x in 0 until w step 5) {
            check(parts.count { x in it.left until it.right &&
                y in it.top until it.bottom } == 1) {
                "Missing/overlapping source at ($x,$y): $parts"
            }
        }
    }

    coverageOnce(panels)
    val trace = mutableListOf<PanelDebugEvent>()
    val fixed = PanelDetectionCore.consolidateShallowDialogueStrip(
        w, h, gray, rows, panels, rightToLeft = true, trace = trace::add)
    check(fixed.size == 5) { "Two original story panels should replace 8 dialogue fragments: $fixed" }
    check(fixed.take(3) == panels.take(3))
    check(fixed[3] == PageRegion(329, 563, w, 900))
    check(fixed[4] == PageRegion(0, 563, 329, 900))
    coverageOnce(fixed)
    check(trace.any { it.stage == "tier" && it.decision == "coalesce" })

    // Safe LTR also keeps top-to-bottom story order, but left before right.
    val ltr = PanelDetectionCore.consolidateShallowDialogueStrip(
        w, h, gray, rows, panels, rightToLeft = false)
    check(ltr.size == 5)
    check(ltr[3] == PageRegion(0, 563, 329, 900))
    check(ltr[4] == PageRegion(329, 563, w, 900))
    coverageOnce(ltr)

    // A speech balloon or painted cross-panel stroke interrupts the entire
    // shared corridor: NEVER slice it simply because the nearby tiers align.
    val crossing = gray.copyOf()
    for (y in 633 until 660) for (x in 324..335)
        crossing[y * w + x] = 25.toByte()
    val refused = PanelDetectionCore.consolidateShallowDialogueStrip(
        w, h, crossing, rows, panels, rightToLeft = true)
    check(refused == panels) { "Crossing bubble/art must veto new cut" }

    // Staggered/split-frame columns are not the same uninterrupted story.
    val misaligned = panels.toMutableList()
    misaligned[9] = PageRegion(430, 664, w, 900)
    misaligned[10] = PageRegion(0, 664, 430, 900)
    check(PanelDetectionCore.consolidateShallowDialogueStrip(
        w, h, gray, rows, misaligned, true) == misaligned)

    // A substantive illustrated center row is not a shallow dialogue strip.
    val notShallowRows = rows.toMutableList()
    notShallowRows[2] = PageRegion(0, 563, w, 612)
    notShallowRows[3] = PageRegion(0, 612, w, 664)
    check(PanelDetectionCore.consolidateShallowDialogueStrip(
        w, h, gray, notShallowRows, panels, true) == panels)

    // Never touch ordinary 2x3 comics, or any layout without a short
    // triple-tier two-column match.
    val ordinaryRows = listOf(
        PageRegion(0, 0, w, 300),
        PageRegion(0, 300, w, 600),
        PageRegion(0, 600, w, 900),
    )
    check(PanelDetectionCore.consolidateShallowDialogueStrip(
        w, h, gray, ordinaryRows, panels, true) == panels)

    println("Speech-tier guard passed: complete coverage, RTL/LTR, crossings, and staggered vetoes")
}
