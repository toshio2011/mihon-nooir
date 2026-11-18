import dev.nooir.bridge.PageRegion
import dev.nooir.bridge.PanelFitSlice
import dev.nooir.bridge.PanelReadableSliceRefiner

private const val W = 800
private const val H = 1200
private fun view(l: Int,t: Int,r: Int,b: Int, landscape: Boolean = false) =
    PanelFitSlice(PageRegion(l,t,r,b),landscape)
private fun refine(items: List<PanelFitSlice>,
    inkAt: (Int,Int)->Boolean = { x,y ->
        x in 72..720 && y in 65..1100 && (x+y)%7 < 4
    },
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
    configurableZoom()
    contentAwareZoom()
    naturalTiersBeforeOverlap()
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

    // Explicit PORTRAIT/LANDSCAPE choices must remain authoritative;
    // only AUTO_FIT may independently refit the new reading views.
    val illustratedNotFramed: (Int, Int) -> Boolean = { x,y ->
        x in 72..720 && y in 65..1100 && (x+y)%7 < 4
    }
    val manualPortrait = PanelReadableSliceRefiner.refine(
        huge,W,H,480,800,illustratedNotFramed,autoFit=false)
    check(manualPortrait.size==3 && manualPortrait.none { it.landscape })
    val manualLandscape = PanelReadableSliceRefiner.refine(
        huge.map { it.copy(landscape=true) },W,H,480,800,
        illustratedNotFramed,autoFit=false)
    check(manualLandscape.size==3 && manualLandscape.all { it.landscape })

    // Over-60%-source row is eligible, not only the former 2/3 threshold.
    val sixty = listOf(view(0,0,800,735),view(0,735,800,1200))
    val sixtyResult = refine(sixty)
    check(sixtyResult.size==3 && sixtyResult[0].region.top==0 &&
        sixtyResult[1].region.bottom==735) {
        "60% broad row should get conservative overlapping halves: $sixtyResult"
    }
    preservesAll(sixty,sixtyResult)

    // 50–60% is CONDITIONAL: an image that occupies less than 55% of
    // its chosen portrait viewport is still hard to read.
    val fifty = listOf(view(0,0,800,680),view(0,680,800,1200))
    val poorPortrait = refine(fifty)
    check(poorPortrait.size==3) { "Poor-fitting 50–60% row not rescued: $poorPortrait" }
    preservesAll(fifty,poorPortrait)
    // When AUTO_FIT has selected landscape and the same row fits well,
    // preserve it. Do not cut for the sake of the 50% number.
    val goodLandscape = fifty.map { it.copy(landscape=true) }
    check(refine(goodLandscape)==goodLandscape) {
        "Adequately filled landscape row is NOT an overlap candidate"
    }

    // A genuine second-look structure beats any speculative half-cut.
    // Both panels cover the entire row, in the supplied manga order.
    val real = listOf(PageRegion(400,0,800,900),PageRegion(0,0,400,900))
    val realRecovered = PanelReadableSliceRefiner.refine(
        huge,W,H,480,800,{ _,_->false },recoverInnerPanels={ row ->
            if (row==huge[0].region) real else null
        })
    check(realRecovered.size==3 && realRecovered.take(2).map { it.region }==real) {
        "Recovery of real panels must precede generic overlapping halves: $realRecovered"
    }
    preservesAll(huge,realRecovered)

    // An apparent panel result with a missing 100px column is rejected:
    // no stray sound effect or balloon may disappear from the output.
    val incomplete = listOf(PageRegion(0,0,300,900),PageRegion(500,0,800,900))
    check(!PanelReadableSliceRefiner.coversSourceRow(huge[0].region,incomplete))
    check(PanelReadableSliceRefiner.coversSourceRow(huge[0].region,real))
    val gapped = PanelReadableSliceRefiner.refine(
        huge,W,H,480,800,{ x,y -> x in 72..720 && y in 65..850 &&
            (x+y)%7 < 4 },recoverInnerPanels={ incomplete })
    check(gapped.size==3 && gapped[0].region.right==800) {
        "Invalid inner panels must not steal part of the original row: $gapped"
    }
    preservesAll(huge,gapped)

    // Four strong outer sides signal one intentional big framed panel:
    // keep its composition even when it crosses the 60% threshold.
    check(PanelReadableSliceRefiner.looksLikeOneFramedPanel(
        huge[0].region,{ _,_->true }))
    val framed = PanelReadableSliceRefiner.refine(
        huge,W,H,480,800,{ _,_->true })
    check(framed==huge) { "Do not cut a confidently framed scene: $framed" }

    // QA9 real One Piece source60 had ONE detector region remaining after
    // merging a 40px near-empty header into 860px illustrated row.
    // Its previous preserveSingleFrame suppressed BOTH useful close-ups.
    // A caller-authorized *edge-only* single region must still offer zooms,
    // with a 12% overlapping content band and independent Auto Fit.
    val qa9Page60 = listOf(view(0,0,725,1100))
    check(refine(qa9Page60) == qa9Page60) {
        "Do not fragment normal single-page manga or text pages"
    }
    val edgeZoom = PanelReadableSliceRefiner.refine(
        qa9Page60,725,1100,480,800,
        { x,y -> x in 18..700 && y in 62..1090 &&
            ((x+y)%7 < 4) },
        allowSingleEdgeZoom = true,
    )
    check(edgeZoom.size == 2 &&
        edgeZoom[0].region == PageRegion(0,0,725,616) &&
        edgeZoom[1].region == PageRegion(0,484,725,1100)) {
        "Source60 should have two protected close-ups instead of 1: $edgeZoom"
    }
    check(edgeZoom.all { it.landscape }) {
        "Wide halves should independently select landscape on X4"
    }
    preservesAll(qa9Page60, edgeZoom)
    // Explicit user Portrait/Landscape modes remain authoritative.
    val manualEdgeZoom = PanelReadableSliceRefiner.refine(
        qa9Page60,725,1100,480,800,
        { x,y -> x in 18..700 && y in 62..1090 && (x+y)%7 < 4 },
        autoFit = false, allowSingleEdgeZoom = true)
    check(manualEdgeZoom.size==2 && manualEdgeZoom.none { it.landscape })
    // An enclosed illustrated page or a protected single text/splash page
    // is NOT a fallback target, even with the edge-only flag.
    val framedEdge = PanelReadableSliceRefiner.refine(
        qa9Page60,725,1100,480,800,{ _,_->true },
        allowSingleEdgeZoom = true)
    check(framedEdge == qa9Page60)
    val protectedEdge = PanelReadableSliceRefiner.refine(
        qa9Page60,725,1100,480,800,{ x,y ->
            x in 18..700 && y in 62..1090 && (x+y)%7 < 4
        },protectedRegions=listOf(qa9Page60[0].region),
        allowSingleEdgeZoom = true)
    check(protectedEdge == qa9Page60)

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

private fun configurableZoom() {
    val broad=listOf(view(0,0,600,1050,true),view(0,1050,600,1400,true))
    val ink:(Int,Int)->Boolean={x,y -> x in 65..535 && y in 45..1330 && (x+y)%7<4}
    val rows=PanelReadableSliceRefiner.refineRows(broad,600,1400,480,800,ink,false,50)
    check(rows.size==3 && rows[0].region==PageRegion(0,0,600,588) &&
        rows[1].region==PageRegion(0,462,600,1050) && rows[2]==broad[1])
    preservesAll(broad,rows)
    check(PanelReadableSliceRefiner.refineRows(broad,600,1400,480,800,ink,false,25)==broad)
    val portrait=broad.map { it.copy(landscape=false) }
    check(PanelReadableSliceRefiner.refineRows(portrait,600,1400,480,800,ink,false,50)==portrait)
    check(PanelReadableSliceRefiner.refineRows(broad,600,1400,480,800,{_,_->true},false,50)==broad)
    check(PanelReadableSliceRefiner.refineRows(listOf(broad[0]),600,1400,480,800,ink,false,80)==listOf(broad[0]))
    check(PanelReadableSliceRefiner.refine(broad,600,1400,480,800,ink,autoFit=false,
        enlargeOversizedSplits=false,requireZoomGain=true,minimumScreenFillPercent=50)==broad)
    check(PanelReadableSliceRefiner.refine(broad,600,1400,480,800,ink,autoFit=false,
        enlargeOversizedSplits=true,requireZoomGain=true,minimumScreenFillPercent=50)==rows)
    println("PASS: configurable oversize zoom, full width/order, 12 percent repeat, coverage and disable")
}

private fun contentAwareZoom() {
    val items = listOf(view(0,0,600,1050,true),view(0,1050,600,1400,true))
    val p = items.first().region
    val dense: (Int, Int) -> Boolean = { x,y ->
        x in 65..535 && y in 45..1330 && (x+y)%7<4
    }
    // Synthetic gutter below the midpoint. A dense middle band stands in
    // for dialogue/art; the quiet band is wide enough to be verified.
    val quiet: (Int, Int) -> Boolean = { x,y -> y !in 590..625 && dense(x,y) }
    val base = PanelReadableSliceRefiner.refineRows(items,600,1400,480,800,dense,false,50)
    check(base[0].region==PageRegion(0,0,600,588) && base[1].region.top==462)
    val shifted = PanelReadableSliceRefiner.refineRows(items,600,1400,480,800,quiet,false,50)
    check(shifted.size==3 && shifted[2]==items[1])
    val a = shifted[0].region
    val b = shifted[1].region
    val center = (a.bottom+b.top)/2
    check(center in 590..625) { "Expected verified quiet band, got center $center" }
    check(a.left==p.left && b.left==p.left && a.right==p.right && b.right==p.right)
    check(a.bottom-b.top==126 && a.top==p.top && b.bottom==p.bottom)
    check(PanelReadableSliceRefiner.coversSourceRow(p,listOf(a,b)))
    check(shifted.all { it.landscape })
    // An entire central dialogue strip is still in the first view.
    check(a.top<=490 && a.bottom>=560)
    val panel = PanelReadableSliceRefiner.refine(items,600,1400,480,800,quiet,
        autoFit=false,requireZoomGain=true,minimumScreenFillPercent=50)
    check(panel==shifted) { "Row and panel fallbacks must share the same safe seam" }
    check(PanelReadableSliceRefiner.refine(items,600,1400,480,800,quiet,
        autoFit=false,requireZoomGain=true,enlargeOversizedSplits=false)==items)
    // Don't treat a moving single-pixel white line as a real band.
    val thin: (Int,Int)->Boolean = { x,y -> y!=600 && dense(x,y) }
    check(PanelReadableSliceRefiner.refineRows(items,600,1400,480,800,thin,false,50)==base)
    // A candidate with dark intervening scanlines can fool five-row
    // sampling; full-band verification must reject it.
    val interrupted: (Int,Int)->Boolean = { x,y ->
        if (y in 590..625 && y%3!=0) false else dense(x,y)
    }
    check(PanelReadableSliceRefiner.refineRows(items,600,1400,480,800,interrupted,false,50)==base)
    // Central quiet band wins over an asymmetric alternate.
    val centerQuiet: (Int,Int)->Boolean = { x,y -> y !in 505..545 && quiet(x,y) }
    check(PanelReadableSliceRefiner.refineRows(items,600,1400,480,800,centerQuiet,false,50)==base)
    // Sparse/uncertain/preserved results are not newly eligible.
    check(PanelReadableSliceRefiner.refineRows(items,600,1400,480,800,quiet,false,50,
        protectedRegions=listOf(p))==items)
    val portrait=items.map { it.copy(landscape=false) }
    check(PanelReadableSliceRefiner.refineRows(portrait,600,1400,480,800,quiet,true,50)==portrait)
    println("PASS: bounded quiet-band zoom, exact overlap/coverage, shared mode parity, noisy/center/protected fallback")
}

private fun naturalTiersBeforeOverlap() {
    val items = listOf(view(0,0,600,1050,true),view(0,1050,600,1400,true))
    val p = items.first().region
    val dense: (Int, Int) -> Boolean = { x,y ->
        x in 65..535 && y in 45..1330 && (x+y)%7<4
    }
    // Two independent gutters with long boundary rails, not a white patch
    // in an illustration. No source pixels are cropped or duplicated.
    val framed: (Int,Int)->Boolean = { x,y ->
        when {
            y in 331..369 || y in 681..719 -> false
            y in listOf(330,370,680,720) -> x in 30..570
            else -> dense(x,y)
        }
    }
    val rows=PanelReadableSliceRefiner.refineRows(items,600,1400,480,800,framed,false,50)
    check(rows.size==4 && rows.last()==items.last()) { "Two missed gutters must recover three natural tiers: $rows" }
    val parts=rows.take(3).map { it.region }
    check(parts.first().top==p.top && parts.last().bottom==p.bottom)
    check(parts.all { it.left==p.left && it.right==p.right })
    check(parts.zipWithNext().all { (a,b)->a.bottom==b.top })
    check(parts[0].bottom in 340..360 && parts[1].bottom in 690..710)
    check(PanelReadableSliceRefiner.coversSourceRow(p,parts))
    val panels=PanelReadableSliceRefiner.refine(items,600,1400,480,800,framed,
        autoFit=false,requireZoomGain=true,minimumScreenFillPercent=50)
    check(panels==rows)
    // White gaps without independent framing must not be treated as
    // actual panel boundaries; retain the original overlapping strategy.
    val unframed: (Int,Int)->Boolean = { x,y ->
        y !in 331..369 && y !in 681..719 && dense(x,y)
    }
    val overlap=PanelReadableSliceRefiner.refineRows(items,600,1400,480,800,unframed,false,50)
    check(overlap.size==3 && overlap[0].region.bottom-overlap[1].region.top==126)
    check(PanelReadableSliceRefiner.refineRows(items,600,1400,480,800,framed,false,50,
        protectedRegions=listOf(p))==items)
    check(PanelReadableSliceRefiner.refine(items,600,1400,480,800,framed,
        autoFit=false,requireZoomGain=true,enlargeOversizedSplits=false)==items)
    // Normal three readable horizontal rows stay exactly three.
    val normal=listOf(view(0,0,600,450,true),view(0,450,600,900,true),view(0,900,600,1400,true))
    check(PanelReadableSliceRefiner.refineRows(normal,600,1400,480,800,dense,false,50)==normal)
    println("PASS: natural tiers before overlap, exact coverage/order, row/panel parity and fallback")
}
