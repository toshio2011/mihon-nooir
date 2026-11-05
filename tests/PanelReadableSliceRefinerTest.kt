import dev.nooir.bridge.PageRegion
import dev.nooir.bridge.PanelFitSlice
import dev.nooir.bridge.PanelReadableSliceRefiner

private const val W = 800
private const val H = 1200
private fun view(l: Int,t: Int,r: Int,b: Int, landscape: Boolean = false) =
    PanelFitSlice(PageRegion(l,t,r,b),landscape)
private fun refine(items: List<PanelFitSlice>,
    inkAt: (Int,Int)->Boolean = { _,_->true },
    protected: List<PageRegion> = emptyList()
) = PanelReadableSliceRefiner.refine(
    items,W,H,480,800,inkAt,protected)

private fun preservesAll(before: List<PanelFitSlice>, after: List<PanelFitSlice>) {
    for (old in before) {
        for (y in old.region.top until old.region.bottom step 7)
            for (x in old.region.left until old.region.right step 7) {
                check(after.any {
                    x in it.region.left until it.region.right &&
                    y in it.region.top until it.region.bottom
                }) { "Artwork at ($x,$y) disappeared from $old in $after" }
            }
    }
}

fun main() {
    // Exact X4-equivalent FINAL fitted minimum size, not source pixels.
    val narrow = view(390,0,410,700)
    val measure = PanelReadableSliceRefiner.fittedMinimumX4Pixels(
        narrow,480,800)
    check(measure < 43.0 && measure > 20.0) { "Failed 5 mm X4 reference: $measure" }
    val ordinary = view(0,0,390,700)
    val lower = view(0,700,800,1200)
    val hard = listOf(ordinary,narrow,lower)
    val mergedHard = refine(hard)
    check(mergedHard.size==2)
    check(mergedHard[0].region == PageRegion(0,0,410,700))
    preservesAll(hard,mergedHard)

    // Crops with a 5–10 mm final size may contain a real miniature framed
    // panel. Non-sparse artwork must remain independently readable.
    val soft = view(390,0,440,700)
    val size = PanelReadableSliceRefiner.fittedMinimumX4Pixels(soft,480,800)
    check(size in 43.0..87.0) { "Failed 10mm soft threshold: $size" }
    val views = listOf(ordinary,soft,lower)
    check(refine(views) == views) { "Real framed mini panel must not be absorbed" }
    val sparseMerged = refine(views,{x,y-> x==390 && y==2})
    check(sparseMerged.size==2 && sparseMerged[0].region.right==440)
    preservesAll(views,sparseMerged)

    // A tiny panel with no aligned adjoining source cannot safely merge;
    // preserving it is better than discarding speech or artwork.
    val detached = listOf(view(20,0,350,500), view(700,700,720,1140))
    check(refine(detached)==detached)

    // A full-width row taller than 2/3 of the original page is still hard
    // to read. On an already-multi-view comic it becomes two ordered views,
    // sharing 12% of the ORIGINAL oversized row height.
    val huge = listOf(view(0,0,800,900),view(0,900,800,1200))
    val halved=refine(huge)
    check(halved.size==3) { "Large full-width row should get two overlapping halves: $halved" }
    check(halved[0].region==PageRegion(0,0,800,504))
    check(halved[1].region==PageRegion(0,396,800,900))
    check(halved[2].region==huge[1].region)
    check(halved[0].region.bottom-halved[1].region.top==108)
    preservesAll(huge,halved)

    // A narrow genuine portrait panel remains intact even if its height
    // covers most of the source page.
    val narrowTall=listOf(view(20,0,250,900),view(300,0,780,900))
    check(refine(narrowTall)==narrowTall)

    // A tiny border/footer attached to an almost-full-page row must NOT
    // trigger two new reading screens. Absorb it and keep one composition.
    val withTinyFooter=listOf(view(0,0,800,1175),view(0,1175,800,1200))
    val footerResult=refine(withTinyFooter)
    check(footerResult.size==1 && footerResult[0].region==PageRegion(0,0,800,1200))
    preservesAll(withTinyFooter,footerResult)

    // Exact source-197 / source-201 style geometries from private QA6
    // diagnostics: 25px expected safety-padding overlap, and a roughly
    // 2–3mm standalone footer. No actual manga pixels are copied.
    for ((h,firstEnd,lastTop) in listOf(
        Triple(965,947,922), Triple(979,961,936)
    )) {
        val actual = listOf(
            PanelFitSlice(PageRegion(0,0,1200,firstEnd),false),
            PanelFitSlice(PageRegion(0,lastTop,1200,h),true),
        )
        val changed = PanelReadableSliceRefiner.refine(
            actual,1200,h,480,800, { _,_->true })
        check(changed.size == 1 && changed.single().region ==
            PageRegion(0,0,1200,h)) {
            "Do not leave tiny padded footer as a zoom frame: $changed"
        }
    }

    // The app's old sparse/preserveWhole path remains off-limits.
    val protected=listOf(view(0,0,800,900),view(0,900,800,1200))
    check(refine(protected,protected=listOf(protected[0].region))==protected)

    // No cuts on a single whole-page artwork or single final text page.
    check(refine(listOf(view(0,0,800,1200))).size==1)
    check(refine(listOf(view(0,0,800,900),view(0,900,800,1200))
        .map { if(it.region.top==0) it.copy(region=it.region.copy(right=560)) else it })
        .size==2) { "Only almost-full-width rows qualify" }

    // An already-overlapping diagonal pair is not a conventional full-width
    // source row. Avoid creating repeated third-generation viewports.
    val priorOverlap = listOf(view(0,0,800,900),view(0,650,800,1200))
    check(refine(priorOverlap)==priorOverlap)

    println("Readable slices passed: 5mm merge, 10mm sparse guard, huge-row overlap, source coverage")
}
