import dev.nooir.bridge.PageRegion
import dev.nooir.bridge.PanelDetectionCore

/**
 * Only independent geometry and generated grayscale pixels; the user's
 * copyrighted One Piece thumbnails/EPUB never enter the repository.
 */
private data class EdgeFixture(
    val source: Int, val width: Int, val height: Int,
    val strip: PageRegion, val touching: List<PageRegion>,
    val top: Boolean,
)
private fun page(w: Int, h: Int, band: PageRegion, darkRate: Int = 0,
    faintRate: Int = 31): ByteArray {
    val g = ByteArray(w * h) { 255.toByte() }
    for (y in band.top until band.bottom)
        for (x in band.left until band.right) {
            val n = (y - band.top) * band.width + x - band.left
            if (darkRate > 0 && n % 100 < darkRate)
                g[y * w + x] = 120.toByte()
            else if (faintRate > 0 && n % 100 < faintRate / 10)
                g[y * w + x] = 205.toByte()
        }
    return g
}
private fun coversAllSource(before: List<PageRegion>, after: List<PageRegion>) {
    for (r in before) for (y in r.top until r.bottom step 3)
        for (x in r.left until r.right step 3)
            check(after.any { x in it.left until it.right &&
                y in it.top until it.bottom }) {
                "Lost source pixel ($x,$y) from $r in $after"
            }
}

fun main() {
    val w=593
    val h=900
    val strip=PageRegion(0,0,w,41)
    val right=PageRegion(284,41,w,291)
    val left=PageRegion(0,41,284,291)
    val expected=listOf(PageRegion(284,0,w,291),PageRegion(0,0,284,291))
    val rtl=listOf(strip,right,left)
    val gray=page(w,h,strip)
    val merged=PanelDetectionCore.coalesceSparsePageEdgeStrip(w,h,gray,rtl)
    check(merged==expected) { "RTL adjacent columns: $merged" }
    coversAllSource(rtl,merged)
    val ltr=listOf(strip,left,right)
    check(PanelDetectionCore.coalesceSparsePageEdgeStrip(w,h,gray,ltr) ==
        expected.reversed()) { "LTR order must remain unchanged" }

    // Real QA6 output region *shapes*, without any manga source pixels.
    val edgeCases=listOf(
        EdgeFixture(60,593,900,PageRegion(0,0,593,40),
            listOf(PageRegion(0,40,593,900)),true),
        EdgeFixture(61,593,900,PageRegion(0,0,593,46),
            listOf(PageRegion(0,46,593,224)),true),
        EdgeFixture(62,593,900,PageRegion(0,0,593,41),
            listOf(PageRegion(204,41,593,357),PageRegion(0,41,204,68)),true),
        EdgeFixture(68,593,900,PageRegion(0,0,593,41),
            listOf(PageRegion(284,41,593,291),PageRegion(0,41,284,291)),true),
        EdgeFixture(70,593,900,PageRegion(0,0,593,36),
            listOf(PageRegion(229,36,593,309),PageRegion(0,36,229,309)),true),
        EdgeFixture(72,593,900,PageRegion(0,0,593,42),
            listOf(PageRegion(294,42,593,277),PageRegion(0,42,294,71)),true),
        EdgeFixture(77,593,900,PageRegion(0,0,593,47),
            listOf(PageRegion(289,47,593,236),PageRegion(0,47,289,236)),true),
        EdgeFixture(83,593,900,PageRegion(0,858,593,900),
            listOf(PageRegion(245,658,593,858),PageRegion(0,495,245,858)),false),
        EdgeFixture(84,593,900,PageRegion(0,0,593,39),
            listOf(PageRegion(0,39,593,395)),true)
    )
    for (f in edgeCases) {
        val pixels=page(f.width,f.height,f.strip,
            darkRate=if(f.source==83) 2 else 0,
            faintRate=30)
        // Source rectangles preserve manga reading order of the adjacent
        // tier; the unframed scan-edge strip is the extra first/last frame.
        val input=if(f.top) listOf(f.strip)+f.touching
            else f.touching+f.strip
        val changed=PanelDetectionCore.coalesceSparsePageEdgeStrip(
            f.width,f.height,pixels,input)
        check(changed.size==f.touching.size) {
            "Source-${f.source} still has false standalone strip: $changed"
        }
        check(changed == f.touching.map { r ->
            if(f.top) r.copy(top=0) else r.copy(bottom=f.height)
        }) { "Source-${f.source} mapping or reading order changed: $changed" }
        coversAllSource(input,changed)
    }

    // A real illustrated/lettered standalone title must remain, even when
    // physically shallow. A faint but wider paper stamp also blocks merging.
    val meaningful=page(w,h,strip,darkRate=4,faintRate=0)
    check(PanelDetectionCore.coalesceSparsePageEdgeStrip(
        w,h,meaningful,rtl)==rtl)
    val substantialFaint=page(w,h,strip,darkRate=0,faintRate=90)
    check(PanelDetectionCore.coalesceSparsePageEdgeStrip(
        w,h,substantialFaint,rtl)==rtl)

    // Real dark panel divider: never join independently framed sections.
    val ruled=gray.copyOf()
    for (x in 0 until w)
        ruled[41*w+x]=0
    check(PanelDetectionCore.coalesceSparsePageEdgeStrip(
        w,h,ruled,rtl)==rtl)

    // Incomplete neighboring tier or interior strip: never lose a solitary
    // speech, title or other part of the original source.
    val halfNext=listOf(strip,PageRegion(310,41,w,291))
    check(PanelDetectionCore.coalesceSparsePageEdgeStrip(
        w,h,gray,halfNext)==halfNext)
    val interior=listOf(PageRegion(0,100,w,140),PageRegion(0,140,w,600))
    check(PanelDetectionCore.coalesceSparsePageEdgeStrip(
        w,h,page(w,h,interior.first()),interior)==interior)
    val tallBand=listOf(PageRegion(0,0,w,90),PageRegion(0,90,w,900))
    check(PanelDetectionCore.coalesceSparsePageEdgeStrip(
        w,h,page(w,h,tallBand[0]),tallBand)==tallBand)
    // A single source page isn't evidence of safe coalescence.
    check(PanelDetectionCore.coalesceSparsePageEdgeStrip(
        w,h,gray,listOf(PageRegion(0,0,w,h))) ==
        listOf(PageRegion(0,0,w,h)))

    println("Sparse page edge passed: nine QA6 edge shapes, RTL/LTR, all pixels, bordered and meaningful title veto")
}
