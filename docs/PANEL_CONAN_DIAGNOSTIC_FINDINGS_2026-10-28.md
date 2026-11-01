# Detective Conan — panel export diagnosis and targeted correction brief

**Date:** 28 October 2026  
**Track:** [Draft PR #51 — opt-in private panel diagnostic ZIP](https://github.com/toshio2011/mihon-nooir/pull/51)  
**Status:** Evidence analyzed; proposed detector fixes **not implemented** by this note.  
**Input:** User-provided same-run `Detective Conan panel.epub`, `Detective Conan row.epub`, and `MihonNooir-panel-diagnostics.zip`. **None of the manga files, source image bytes, or screenshots are committed here.**

## Reproduction baseline and indexing

The submitted `summary.json` is complete and says **67 source images, 329 non-cover output frames, 1 separate cover, 330 output frames total, and 24 optional original compressed samples**. The supplied Panel EPUB contains **330 image resources**; the separately supplied Row EPUB contains **235 image resources**. Counts are context, **not** a target for detector optimization. The diagnostic ZIP covers the Panel run.

For the three affected source images, the capture settings were **PANEL_BY_PANEL**, `rtl=true`, `orientation=AUTO_FIT`, `cropMargins=true`, and `showFullPageBeforeSplits=true`. All three traces say `traceTruncated=false`, with zero dropped events.

Frame labels below refer to **this submitted Panel EPUB/diagnostic ZIP**, not permanent page identifiers. Subsequent fixes will shift frame numbers. Match by `sourceOrdinal`, `chapterOrdinal`, `chapterPageOrdinal`, and the matching overview before inspecting newly emitted frames. Rectangles are `[left,top,right,bottom]` with exclusive right/bottom. `outputFrameStartOneBased` includes the separate cover and optional full-page overview.

| Defect | Source image | Chapter image | This run's Panel output | Detector thumbnail | Original sample in ZIP |
|---|---:|---|---|---|---|
| Bottom border/footer exported as two frames | 21 | chapter 2, image 5 | P109–P114; **P113–P114 defective** | 580×900 | `originals/00021.source` |
| Two illustrated upper panels kept as one | 26 | chapter 2, image 10 | P142–P146; **P143 grouped** | 585×900 | `originals/00026.source` |
| Shallow headline split into letters/fragments | 66 | chapter 4, image 18 | P309–P323; **P311–P315 defective** | 581×900 | **not captured**; exact detector thumbnail available |

### Case A — P113–P114: terminal footer-strip false positives

**Observed source:** `sourceOrdinal=21`, 710×1100 source pixels. The planner made full-page overview **P109**; the final two output crops were:

- **P113:** `[342,1035,710,1100]`, rotated for Auto Fit.
- **P114:** `[0,1035,359,1100]`, rotated for Auto Fit.

Both crops cover only **65 of 1,100 source rows** at the foot of the page. The exact thumbnail and original sample show the narrow bottom area contains a border/continuation with the page footer, **not two independent story panels**.

**What the trace actually says:** Row search separated a shallow terminal tier `[0,858,580,900]` (height **42/900**) below a substantive tier. Recursive panel partition rejected the terminal tier (`candidate count outside permitted range`), **but** `column-retry` later accepted a seam at `x=286` after 31 attempts. The trace then reports `accepted 2 substantial columns in reading order`. That recovery accepted two outputs from a thin footer tier.

**Proposed correction (hypothesis to verify):** At the **rejected-row column-recovery stage**, detect too-shallow terminal rows containing only ruled/border-style material and prohibit independent panel splitting. Ensure their pixels remain covered by a neighboring safe viewport/region or, if uncertain, a preserved whole-page/whole-tier fallback. **Do not globally delete low-ink frames or discard footer text/page numbers**. Do not treat a height threshold alone as proof a row is unimportant; some legitimate manga panels are shallow.

**Pass condition:** The source still contains all footer/border pixels somewhere in the book (and in its intact overview), but no standalone P113/P114-like tiny rotated border frames.

### Case B — P143: hard-framed illustrated columns left grouped

**Observed source:** `sourceOrdinal=26`, 715×1100. **P142** is the intended overview. **P143** currently exports the large top crop `[29,0,715,577]` as **one** rotated frame. The source image has **two actual upper illustrated columns** separated by a strong drawn panel boundary; the left/right story panels should be distinct RTL frames when confidently separable.

**What the trace actually says:** The top thumbnail tier was `[0,0,585,461]`. Recursive partition proposed a vertical split around `x=400`, but produced a tiny subregion and rejected the candidate set for **minimum area**. The independent retry then attempted **45** seams and rejected all **45** as `unsafeSeam`; `tier keep no safe alternative column split` left that top tier grouped. This is specifically a **rejected top-row split**, not a missing row separator.

**Proposed correction (hypothesis to verify):** Investigate a **bounded, frame-line-aware vertical-seam retry** for rejected *substantial* illustrated rows, including the case of a continuous dark frame line rather than a perfectly white gutter. Require credible separate illustrated mass on both sides and a safe boundary over the relevant span; the code must not cut through speech balloons, character artwork, diagonal/irregular dividers, or connected ink. A hard framed divider is not automatically a safe seam. Keep the existing grouped-row fallback whenever the evidence is ambiguous.

**Pass condition:** On this source, replace the single P143 upper story crop with **two** fully covered, correctly ordered RTL panel views; retain exactly one optional overview ahead of the split views, without losing speech balloons or artwork.

### Case C — P311–P315: typographic header misclassified as panels

**Observed source:** `sourceOrdinal=66`, 711×1100. **P309** is the intended overview. **P310** is already a wide, shallow upper text crop. **P311–P315**, all from the line immediately below, are five extra tiny text fragments:

| Output | Source rectangle | Auto Fit rotated? |
|---|---|---|
| P311 | `[601,117,711,215]` | yes |
| P312 | `[376,117,618,215]` | yes |
| P313 | `[242,117,393,215]` | yes |
| P314 | `[165,117,259,215]` | no |
| P315 | `[18,117,182,215]` | yes |

**What the trace actually says:** The row `[0,0,581,107]` was rejected as `too narrow or short` and preserved intact; however the adjacent headline band `[0,107,581,165]` (height **58/900**) was **accepted as 5 panel candidates**. Subsequent illustrated tiers were accepted as two columns each. The resulting five fragments correspond to typography, not five comic panels.

**Evidence limitation:** The ZIP contains `thumbnails/00066.png` (the **exact** grayscale detector input) and the complete JSON trace, but **does not include original compressed source bytes** for source 66 because optional originals are capped at 24. Do not claim full-resolution pixel-level verification from this diagnostic alone.

**Proposed correction (hypothesis to verify):** In **Panel by Panel only**, add a conservative guard for a shallow, text-like *horizontal band* whose recursive division produces multiple small fragments. Preserve the entire title band, or combine it with adjacent header material when geometrically safe, rather than yielding a frame for each word/letter group. Do **not** require OCR, classify story language, or automatically suppress all short rows: sparse captions, borderless art and authentic short panels must remain readable and covered.

**Pass condition:** Header text remains readable and uncut; no five independent P311–P315-like word fragments. Keep the following **real illustrated panels** separated in RTL order.

## Suggested implementation boundaries

Keep changes focused on the **pure Kotlin detector** in `overlay/app/src/main/java/dev/nooir/bridge/PanelDetectionCore.kt`:

1. Review `Detector.partitionRows`, `isSparseRow`, recursive candidate validation, and `retryRejectedRowColumns`, especially terminal-row promotion (Case A), safe dark-rule vertical alternatives (Case B), and shallow typographic tier acceptance (Case C).
2. Recheck `AndroidPageSource.detectPanels` and `AndroidPageSource.render` without moving unrelated conversion behavior. Padding, whitespace trimming, `PanelSafeSlicePlanner`, and Auto Fit may make a bad tiny region more conspicuous but **did not invent the bad accepted thumbnail regions** in these traces.
3. Make each change small, with separately named synthetic regression tests. **Do not introduce an unconditional minimum-height cutoff, globally delete sparse rows, or force column splits on any dark line.**

The diagnostic ZIP is a **one-job, opt-in debugging aid**, not a reason to change the exporter by default. Preserve `panelDiagnostics=false` and the existing privacy/storage safeguards. No hardware or Folio Nooir firmware changes.

## Non-negotiable regression/acceptance gates

- **A — terminal tier:** synthetic substantive two-column artwork plus shallow terminal page-number/border strip. The footer is never emitted as two standalone rotated panels; all pixels remain represented. Verify a *real* short illustrated tier is **not** silently lost.
- **B — framed upper columns:** synthetic two genuine ink-rich columns separated by a safe vertical frame line; verify two RTL outputs plus overview (when enabled), with full artwork coverage. Add an intentionally unsafe case with a crossing balloon/ink: it must **remain grouped**.
- **C — title band:** synthetic text-like header above an ordinary two-column, multi-row manga grid; verify no individual title word/stroke viewports while all real illustrated panels still split. A sparse intentional story panel must remain intact.
- **Unchanged modes:** `ROW_BY_ROW` must continue to segment horizontal tiers **without** new vertical splitting; `OFF` must retain legacy rendering; both should remain unaffected by Panel-by-Panel-specific heuristics.
- **Unchanged options:** Retain cover ordering, one **Full Page Before Splits** overview *only when multiple frame slices exist*, RTL reading order, Auto Fit, safety padding, and no-clipping/complete coverage.
- **Formats + device:** Run existing all-format host tests (EPUB2, EPUB3, XTC, XTCH, CBZ), Python offline diagnostic inspector tests, Android FOSS APK build and the actual Android emulator synthetic ZIP smoke. Then export the **same real chapters/settings** and visually check the changed views on the user's phone / target reader before claiming panel quality.
- **Artifacts:** Compare the new Panel EPUB against this submitted Panel EPUB with `tools/panel_epub_diff.py`, match by source ordinals and unchanged surrounding frames. Frame numbers **will change** if fixes work; do not hard-code P113/P143/P311 as future positions. The previous Row EPUB is a quality reference, not an instruction to make Panel output identical.
- **Privacy:** Do not upload or commit these user-supplied EPUBs, ZIP, original samples or thumbnails to GitHub; the synthetic fixtures should be independently generated geometry.

## Status and sequencing

1. **Done:** The opt-in private ZIP path passed a real Android 35 emulator smoke test in [CI #40671049581](https://github.com/toshio2011/mihon-nooir/actions/runs/40671049581), including 1-bit/2-bit render parity and a ZIP inspected as valid. This validates diagnostic collection, **not** Conan panel quality.
2. **Done:** Map these three regressions to their source images, output rectangles and actual detector trace decisions (this brief).
3. **Next:** Create synthetic tests for A, B and C; implement **targeted** detector changes individually; rerun test suite and compare a fresh, private same-source Conan export. Report any remaining false positives or ambiguous rows.
4. **Do not yet:** Merge draft PR #51, publish a release, introduce broad detector behavior changes, or touch Folio Nooir firmware without approval.

**Bottom line:** The detector needs **one false-positive terminal-tier fix**, **one carefully guarded under-split recovery**, and **one text-band over-splitting guard**. These are three independent failure modes; preserve safe fallbacks rather than optimize for a predetermined frame count.
