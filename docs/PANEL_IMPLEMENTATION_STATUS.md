# Automatic Panel-by-Panel Export — implementation status

**Design source:** [PR #29 — approved handoff](https://github.com/toshio2011/mihon-nooir/pull/29) / `docs/AUTOMATIC_PANEL_BY_PANEL_EXPORT.md` on `docs/automatic-panel-export-design`.

**Status:** Phase A geometry implementation **in review**, **not available in the Android app yet**. The design PR is intentionally still draft. No change to Folio Nooir.

## User-approved behavior (non-negotiable)

- Android phone performs automatic detection, reading-order planning and page rendering. XTEink X3/X4 firmware only displays already-sized pages.
- **Panel by Panel** is opt-in, one panel per screen, no OCR, paid AI, remote service or required preview/approval. Larger outputs are acceptable.
- Do not crop off dialogue/artwork; uncertain layouts fall back to intact source page or the existing safe webtoon/spread handling. No fabricated confidence guarantee.
- Reuse existing XTC (1-bit), XTCH (2-bit), EPUB 2, EPUB 3 and CBZ writers, chapter metadata, cover, profiles, orientation, overlap, dithering and transfer.
- With mode Off, original conversions, queued-job tokens, PDFs and web novels must behave as before.
- Preserve downloaded source images and respect large-book format/transfer limits. No silent truncation or corrupt output.

## Phase A — first isolated pure-Kotlin implementation

`overlay/app/src/main/java/dev/nooir/bridge/PanelDetectionCore.kt` provides:

1. Bounded grayscale thumbnail geometry with **no Android/ML/OCR dependency**.
2. Conservative detection of **clear full-span whitespace gutters**, recursively splitting horizontal tiers first and then RTL/LTR columns.
3. Deterministic reading order, safe fallback for splash pages, long webtoons, wide spreads, ambiguous staggered columns, empty/sliver candidates and oversized detection.
4. **Lossless geometry partition**: detected regions cover every pixel of the thumbnail, not just dark pixels. `sourceRegions` maps them back to full source dimensions with bounded overlapping guard padding.
5. Synthetic regression tests for 2×3 manga grids, RTL/LTR order, coverage, guard padding, splash pages, ambiguous staggered panels, webtoons/spreads, invalid input and deterministic ordering.

**Important limitation:** This is a deliberately conservative **first detector**. Touching/borderless/diagonal panels, complex speech balloons and uneven panel arrangements may fall back to a full page. No real-manga corpus, Android rendering integration, output validation or physical-device testing is claimed.

## Phase B — Android integration (P0, NOT IMPLEMENTED)

- [ ] Add backward-compatible `panelMode=OFF|PANEL_BY_PANEL` (and safety settings) to `MangaConversionSettingsCore.kt` token and `MangaConversionOptions.kt` Compose settings. Old tokens must decode to OFF; unchanged mode must preserve existing outputs.
- [ ] In `AndroidPageSource.kt`, downsample each decoded Bitmap to a **bounded** grayscale analysis buffer; invoke Phase A detector **only** when enabled; map regions to source; crop with guard padding; render **one panel per screen** through the current image/quantization pipeline.
- [ ] Handle overly tall/wide **single panels** with consecutive overlapping crops of that panel only; avoid slicing one panel into mixed layouts. Keep cover exempt.
- [ ] Preserve selected chapters, metadata, covers and Quick Export; integrate both `MihonNooirExporter.kt` and `ImportedMangaConverter.kt` without rewriting format writers.
- [ ] Add Android instrumentation/unit integration and export-format smoke fixtures. Keep Windows shared converter compiling; desktop panel parity is a follow-up.

## Phase C — safety and large batches (P0, NOT IMPLEMENTED)

- [ ] Report original page count, emitted screens, fallback count and reasons without mandatory review.
- [ ] Bound decode memory and temporary output, check cancellation and cleanup, and **preflight projected XTC/XTCH page count and Wi-Fi size limits**.
- [ ] Add automatic multi-volume split **with chapter boundaries** if feasible; otherwise fail clearly before heavy work. Do not silently cap panels.
- [ ] Verify actual EPUB 2/3, XTC, XTCH, CBZ outputs and cover/chapter navigation; test hundreds of pages.
- [ ] Verify X3 (528×792), X4 (480×800), X4 Pro and other device profiles; Android/XTEink physical tests before claiming readable output.

## Phase D / E — quality improvements and optional modes

- [ ] Evaluate **Adenzu Manga-Panel-Extractor (MIT)** concepts for contour/connected-panel segmentation; study **Kumiko** and **Pannello** concepts without copying AGPL code or model weights.
- [ ] Add real permitted test pages covering borderless layouts, crossing balloons, touching panels, uneven tiers, spreads and single splash pages. Prefer safe fallback over forced precision.
- [ ] Optional troubleshooting contact sheet, **never mandatory preview**.
- [ ] Optional separate **Smart Layout** mode only after true Panel-by-Panel is reliable; never combine panels automatically in Panel-by-Panel mode.

## Parallel work — keep previous roadmap active

- [x] Windows D0.2 source browsing is merged in [PR #25](https://github.com/toshio2011/mihon-nooir/pull/25).
- [ ] [PR #28](https://github.com/toshio2011/mihon-nooir/pull/28) Windows Swing GUI preview: CI passed, **still draft/unmerged**. Real Windows UI and Suwayomi live-server tests remain; do not confuse successful build with complete desktop app.
- [ ] [PR #26](https://github.com/toshio2011/mihon-nooir/pull/26) and [PR #27](https://github.com/toshio2011/mihon-nooir/pull/27) are also open draft desktop branches. Audit overlaps against merged #25 before merging; avoid duplicate APIs.
- [ ] Android stable signing and physical device compatibility; no new release is implied by Phase A.
- [ ] Desktop managed Suwayomi backend, chapter queue/offline reader, backup restore, PDF/web parity and installer remain separate milestones.

**Regression gate:** `bash tests/run.sh`, Python overlay tests, EPUBCheck, Android FOSS APK CI, and later physical X3/X4 checks. The panel feature is **not done** until the Android UI and all five export formats pass actual tests.
