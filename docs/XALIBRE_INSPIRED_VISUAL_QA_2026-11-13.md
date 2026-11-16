# MihonNooir visual QA and rendering experiments — 13 Nov 2026

**STATUS: EXPERIMENTAL, NOT RELEASED.** The work is isolated from public Android v1.0.17, Android updater PR #80, the PDF release track PR #82, Windows draft PR #81, and **all Folio Nooir firmware code**. This experiment is based on PDF draft #82 only to share its recent save/format safety work; it is not a change request to merge that PR.

## Confirmed existing preview vs actual exported-file inspection

Android's `NooirExportDialog` **already has a live first-source-page preview** via `AndroidPageSource.previewFirstPage`. It reflects panel splitting and conversion settings, allows navigating up to 64 preview frames, and supports image or CBZ inputs. **Do not replace it.**

That live preview does not reopen the final disk-written XTC/XTCH; a mismatch during binary packing could go unnoticed. The new `XtcBookPreview` reads a completed XTC/XTCH binary's 56-byte header, 16-byte indexed page entries and packed XTG/XTH page planes, validates offset/length/dimensions, and decodes **only the requested page** into its native grayscale levels. It supports 1-bit row-major XTC and two-plane column-major XTC-H/XTCH with the existing compatibility gray mapping, requiring a safe one-page bitmap size. `NooirXtcBinaryPreviewDialog` lets Android select a saved file through SAF, copies it as a temporary seekable private file with a bounded RAM buffer, previews individual decoded pages and deletes the temporary file on close. This view does not upload or modify any reader/book. It is **not** the same as looking at the source.

**Windows:** its current development GUI already has thumbnail and offline comic-reading previews, but no finished XTC/XTCH file inspector. A Windows-native UI for this same pure Kotlin binary reader remains a separate milestone and must be integrated against the isolated Windows draft without modifying its existing Android guard.

## Rendering modes (opt-in; preserve legacy conversions)

The shared settings token appends `renderAlgorithm`, then `contextPeekPercent`; old 7..20-field tokens retain existing Floyd–Steinberg when `dither=true` and threshold when `dither=false`. No current book changes unless a user chooses a new setting.

| Mode | Benefit | Trade-off |
|---|---|---|
| **Floyd–Steinberg** | Existing default; good gradient fidelity | Diffuse noise may make paper/grays look grainy |
| **Atkinson** | Cleaner whites, sharp text and linework | Discards part of quantization error; may lose subtle shading |
| **Bayer 8×8** | Stable ordered dots, useful for manga screentones | Regular dot/grid pattern can be visible |
| **Threshold** | Pure black/white or nearest 4-level tone, crisp flat art | Smooth gradients can posterize/band |

XTC always writes 1-bit; XTCH always writes four grayscale levels. Colored EPUB/CBZ exports can still retain their source color when enabled; choosing a grayscale mode does not recolor those pages. The Android GUI explains the above differences. Windows rendering uses the same core on its experimental CLI but needs its GUI selector as an isolated Windows follow-on. Compare all modes on the same reference crops with a stable 1:1 device zoom and check speech bubble readability.

## Smarter framing without speculative cuts

The current `PanelDetectionCore`, `PanelSafeSlicePlanner` and `PanelReadableSliceRefiner` already have protections for faint or dark gutters, speech bubbles, sparse strips, overlapping artwork and conservative fallbacks. We **must not copy** another project's aggressive fallback that splits a splash page without a trustworthy boundary.

The experimental `PanelContextPeek` is an **opt-in post-detector source-rectangle expansion**, not a new detector. It repeats a limited sliver (1–8%) around immediately neighboring accepted panel regions; it creates **no new frame**, deletes **no pixels**, never reaches outside the source, and does not alter row-only, text-dominant/whole-page fallback, or known sparse protected fragments. The Android adapter only uses it when verified panels and final slices still correspond one-for-one. The existing overlap control remains separate. It is distinct from Xalibre's broader peek mode; no algorithm source code was copied.

The next independently evaluated detector experiment can compare per-row white/black gutter confidence, staggered gutter evidence and dialogue-busyness penalties against known Conan/One Piece failing pages, with explicit fail-closed gates. Do not silently change global thresholds.

## Test matrix and release gates

- Pure Kotlin tests: exact XTC/XTCH saved-binary readback of generated pages; corrupt file refusal; rendering modes constrained to the intended 1-/2-bit levels; byte-identical legacy Floyd/threshold; Bayer's deterministic ordered pattern; context peek bounds/coverage and distant-panel refusal; 7–22-field token migration.
- Android: confirm live first-page preview still works; file-picker saved-binary preview for X3/X4 XTC and XTCH; inspect first/middle/last frames; switching files and cancellation cleans the temp file; SAF denial and damaged/large/corrupt input show actionable errors without a crash.
- Visual review: real manga page with gray/dark gutters, disconnected speech bubbles, crossing linework, RTL order and nonrectangular panels; compare original vs each algorithm; optionally enable Context Peek and confirm the original artwork remains uncut.
- Windows: use independently reviewable desktop adapter + real packaged Windows UI; compare XTC/XTCH decoded pixels with Android on identical output; no Android source change in Windows PR.
- Hardware: only after safe phone and Windows QA, physically inspect the exported files on XTEINK X4/X3. **No Folio Nooir firmware source change, flashing or BLE changes.**
- Licensing: Xalibre is a source of design references; README claims MIT but a standalone license file was not found at v1.0.0. This work is original code built around MihonNooir's established codec; do not copy Xalibre implementation or bundled font/assets without separately checking terms.

**Never merge, publish, or call a new render mode stable until the exact-head Android CI and physical reading gates are satisfied.**
