# Private One Piece EPUB — output-only audit (2 November 2026)

> **3 November follow-up:** The 735-frame numbers below remain a valid **older private output snapshot** and are not interchangeable with the newer 979-frame EPUB. For current code/CI and release gates see [current Android v1.0.17 handover](../HANDOVER_2026-11-03_ANDROID_PANEL_V1.0.17.md). This document contains derived numerical results only, **not manga images**.

## Follow-up: the 979-frame re-export (distinct from the 735-frame baseline)

The user provided an additional, private exported EPUB containing **979** PNG reading frames, after an intermediate **976**-frame version. The new export also includes an additional chapter, so **735→979 is NOT a 244-frame increase in corrected panel splits**. At least **696 exported PNG byte payloads** exactly match frames in the old 735-frame baseline.

| Check | Observed output-only result |
| --- | --- |
| Previously missed angled layout #884 | Old full-page artwork is retained as overview #884, followed by three NEW actual slanted-row views #885–887 |
| Previously missed angled layout #114 | Still a whole small illustrated source view, byte-identical to old export; detector refusal reason cannot be recovered from exported PNG alone |
| #87, #391, #603 | Intentional whole-page overviews followed by splits; not evidence of newly missed panel boundaries |
| <1% dark pixels (threshold <220 grayscale) | **10/979** frames, **8** byte-for-byte identical to prior export payloads |
| <3% dark pixels | **33/979** frames, some meaningful credits/headings, others almost empty |
| Representative near-white fragments | #278, #281, #287, #344, #379; speech-outline-only or border-heavy #477 and #507; additional chapter #941 and #949 |
| New #884 quality | Safer multi-view coverage but visible **excessive repeated border strips / overlap** still merits review |

**Do not conflate output hashes with detector source decisions.** Most nearly blank output frames were already in the old file; #331's similarly sparse fragment is visually comparable but not byte identical, while #941 is from an added chapter. An unrelated small source-page credit can be legitimately sparse. It is therefore UNSAFE to drop every frame below an arbitrary ink threshold or claim these cases all come from one detector bug.

This newer EPUB was still produced **before** the terminal-strip absorption, preserved single-frame Android handoff, and latest two-test emulator validation. No original source-image-to-output ZIP accompanies it, so even a modified script/algorithm cannot retrospectively establish whether it fixes these exact frames. Fresh original-source export + private diagnostic ZIP are required. Consult [QA checklist](android-v1.0.17-pr57-qa-checklist.md). Do not commit/share the copyrighted originals, EPUBs, thumbnails or contact sheets.



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

## Repeated credit-frame evidence (follow-up)

Full-resolution comparison of frames **276** and **373** (both 800×480):
- **99.7354% of grayscale pixels exactly match** (382,984 of 384,000).
- The only differences are 1,016 pixels with luminance differences of ±85, suggesting near-identical small logos/credit artwork in slightly different positions.
- Both contain only about **0.28%** ink (<220), rather than cut-off illustrated panels.
- This strongly suggests **repeated sparse credit/watermark content**, not proof of detector over-splitting. Other frames (notably 239–248) remain suspicious because unrelated headings have been enlarged into separate screens.
- **Do not suppress low-ink images by threshold alone**. The output-only file cannot prove whether a particular screen came from a separate original source image.

## Fallback crop safety patch

Commit `06a70107` closes a separate confirmed risk in the Android rendering path. When *Panel by Panel* detection is rejected but the older page/spread renderer still applies optional white-margin cropping, it previously used the approximate sample-only `trimWhite()`. That can overlook a faint speech/credit pixel between sample points and then crop it away. Panel-by-Panel, non-WEBTOON fallback now uses the pixel-verified `trimDetectedPanel` / `PanelWhitespaceCrop.trim` with full excluded-pixel veto. The OFF, Row by Row, and legacy WEBTOON paths retain existing behavior. The host test `PanelWhitespaceCropTest` now exercises an off-sampling-grid credit pixel alongside dense illustration. This is a **data preservation fix**, not evidence that old One Piece output frame counts are corrected.

## Risk analysis
The converter can over-fragment a reasonably inked original page via more than one path: independent row/panel detection, long-panel safe corridor slicing, margin trim, or the older generic PageSlicePlanner fallback. An output EPUB **cannot identify which path produced an image** unless the original page index + diagnostic trace and matching export options are available.

PR #57 post-audit patch **coalesces clean-corridor sparse final leftovers** without discarding pixels; it addresses one proven mechanism but **does not yet demonstrate a fix for One Piece's TOC or sparse detector tiers**. Avoid aggressive TOC recognition without a paired original source, since small dialogue and ornate titles may look similarly sparse.

## Exact next validation
1. Re-export the *same permitted manga source pages and options* using a new QA APK produced at the latest PR #57 SHA, and capture the existing **opt-in** panel diagnostic ZIP; do not publish the manga/ZIP.
2. Keep the older export as a baseline. Use `tools/panel_epub_diff.py` with same settings and chapter selection to find changed frames, independently of frame-number drift.
3. Match each suspicious *new* output to diagnostic source-page index, input dimensions and mode, detector stage/decision, source rectangle and safe-slice boundaries. Specifically identify whether 239–248 and 273/276 originate from distinct source pages or a single split image.
4. Validate 0/8/12/20% overlap, Row by Row horizontal-only, panel row-first, portrait/landscape maximum fill AFTER final slices, RTL, and no lost speech or artwork. Inspect actual XTEINK X4 output.
5. Do not merge #57 or release v1.0.17 solely from this output-only audit. Maintain signed Android v1.0.16, Windows #53 and Folio Nooir unchanged.
