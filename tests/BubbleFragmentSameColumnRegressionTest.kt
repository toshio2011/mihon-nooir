import dev.nooir.bridge.PanelDetectionCore
import dev.nooir.bridge.PageRegion

/**
 * Independently authored source-72 SHAPE regression.
 * No manga pixels, characters, image or quoted dialogue are embedded.
 */
fun main() {
    val w = 593
    val h = 900
    val pale = ByteArray(w * h) { 255.toByte() }
    val right = PageRegion(425,607,593,900)
    val middle = PageRegion(196,607,425,900)
    val brokenTop = PageRegion(0,607,196,642)
    val sameArtworkBelow = PageRegion(0,642,196,900)
    val before = listOf(right,middle,brokenTop,sameArtworkBelow)
    // A direct shape of source 72: four accepted regions in one story tier.
    // A 35-pixel text strip and the remaining 258px drawing share EXACT
    // source column, but AUTO_FIT would turn the 35px into a huge new page.
    val merged = PanelDetectionCore.coalesceShallowSameColumnFragment(
        w,h,pale,before)
    check(merged == listOf(right,middle,PageRegion(0,607,196,900))) {
        "One illustrated panel cannot leave its speech strip orphaned: $merged"
    }
    // Every original pixel remains present at source resolution.
    for (r in before) {
        for (y in r.top until r.bottom step 4)
            for (x in r.left until r.right step 4)
                check(merged.any { x in it.left until it.right &&
                    y in it.top until it.bottom })
    }
    val ltr = listOf(brokenTop,sameArtworkBelow,middle,right)
    check(PanelDetectionCore.coalesceShallowSameColumnFragment(w,h,pale,ltr) ==
        listOf(PageRegion(0,607,196,900),middle,right)) {
        "Coalescing must not change RTL/LTR story order"
    }

    // A strong ruled boundary means a real separate framed mini-panel.
    // Even matching column dimensions must never override such evidence.
    val ruled = pale.copyOf()
    for (y in 640..644)
        for (x in 0 until 196) ruled[y*w+x] = 0
    check(PanelDetectionCore.coalesceShallowSameColumnFragment(
        w,h,ruled,before) == before) { "True panel frame must veto the union" }

    // A healthy 55-pixel-high comic panel, a tiny strip in a different
    // column, and a genuine full-width row are never silently coalesced.
    val taller = listOf(right,middle,
        PageRegion(0,607,196,669),PageRegion(0,669,196,900))
    check(PanelDetectionCore.coalesceShallowSameColumnFragment(
        w,h,pale,taller) == taller)
    val staggered = listOf(right,middle,
        PageRegion(0,607,180,642),sameArtworkBelow)
    check(PanelDetectionCore.coalesceShallowSameColumnFragment(
        w,h,pale,staggered) == staggered)
    val wide = listOf(PageRegion(0,0,w,35),PageRegion(0,35,w,900))
    check(PanelDetectionCore.coalesceShallowSameColumnFragment(
        w,h,pale,wide) == wide)

    // A short GENUINE 2-column slice with a different left/right boundary
    // must not be merged across characters or adjacent source columns.
    val differentColumns = listOf(PageRegion(0,607,180,642),
        PageRegion(180,642,360,900))
    check(PanelDetectionCore.coalesceShallowSameColumnFragment(
        w,h,pale,differentColumns) == differentColumns)
    println("Bubble fragment guard passed: same-column whole speech, ruled veto, complete source coverage, RTL/LTR")
}
