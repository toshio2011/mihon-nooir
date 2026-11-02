# Private One Piece EPUB — output-only audit (2 November 2026)

## Source and privacy
The user-supplied `One Piece.epub` was inspected locally from the user's private file library. **Never commit or attach the EPUB, manga images, output image contact sheets, original manga, or diagnostic screenshots to this public repository.** This document contains only derived counts, frame numbers and processing guidance. This export was produced before the latest PR #57 patches. We do **not** have its original uncropped manga pages or settings, so frame-to-source-page correspondence cannot be proven.

## Measured
- ZIP image entries: **735** (sequential `OEBPS/images/p00001.png` through `p00735.png`).
- Exported viewport geometry: **389 images at 480×800** and **346 images at 800×480**.
- Every image was decoded to luminance and assessed at full exported resolution (not a partial sample).
- **9 frames** have <1% pixels with grayscale below **220**: **273, 276, 282, 312, 326, 338, 373, 409, 411**. Most are isolated small text/credit fragments on broad white margins. **13** have <1% pixels below **130**. These metrics alone do not prove an error; retain credits, intentional blank/title pages and full-page overview modes.
- Other representative sparse frames:
  - **43**: 1.97% ink, isolated ruled/banner fragment before illustrated frames 44 and 46–47.
  - **239–248**: table-of-contents headings/lines and page numbers disproportionately spread across multiple largely empty screens (frame 247: **1.83%** ink; 248: **2.12%**).
  - **273**: 0.42% ink; **276**: 0.28% ink alongside otherwise strongly illustrated frames **274–275**.
  - **373**: 0.28% ink, a small isolated fragment.
- Long successive TOC/credit/title screens are a high-priority over-fragmentation concern, not automatically a reason to delete or skip those pixels. Whole original source, chapter order and speech-bubble data must be preserved.

## Risk analysis
The converter can over-fragment a reasonably inked original page via more than one path: independent row/panel detection, long-panel safe corridor slicing, margin trim, or the older generic PageSlicePlanner fallback. An output EPUB **cannot identify which path produced an image** unless the original page index + diagnostic trace and matching export options are available.

PR #57 post-audit patch **coalesces clean-corridor sparse final leftovers** without discarding pixels; it addresses one proven mechanism but **does not yet demonstrate a fix for One Piece's TOC or sparse detector tiers**. Avoid aggressive TOC recognition without a paired original source, since small dialogue and ornate titles may look similarly sparse.

## Exact next validation
1. Re-export the *same permitted manga source pages and options* using a new QA APK produced at the latest PR #57 SHA, and capture the existing **opt-in** panel diagnostic ZIP; do not publish the manga/ZIP.
2. Keep the older export as a baseline. Use `tools/panel_epub_diff.py` with same settings and chapter selection to find changed frames, independently of frame-number drift.
3. Match each suspicious *new* output to diagnostic source-page index, input dimensions and mode, detector stage/decision, source rectangle and safe-slice boundaries. Specifically identify whether 239–248 and 273/276 originate from distinct source pages or a single split image.
4. Validate 0/8/12/20% overlap, Row by Row horizontal-only, panel row-first, portrait/landscape maximum fill AFTER final slices, RTL, and no lost speech or artwork. Inspect actual XTEINK X4 output.
5. Do not merge #57 or release v1.0.17 solely from this output-only audit. Maintain signed Android v1.0.16, Windows #53 and Folio Nooir unchanged.
