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


## 23 October 2026 — Mihon upstream panel detector review

**Upstream reference:** [mihonapp/mihon PR #4045 — Add panel-by-panel zoom navigation to paged reader](https://github.com/mihonapp/mihon/pull/4045), still **open/unmerged** when reviewed. This is not part of stable Mihon v0.21.2. Reviewed actual files: `core/common/.../panel/ContourPanelDetector.kt`, `PanelOrder.kt`, `PanelModel.kt`, the unit tests and the PR description. Mihon upstream is Apache-2.0; **no upstream code was copied into Phase A**.

| Upstream component | What it does | MangaNooir decision |
|---|---|---|
| `GrayImage` / `PanelDetector` interface | Pure Kotlin grayscale input and normalized panel rectangles | Useful shape for a future detector adapter, but keep our current `PanelPlan` fallback semantics and bounded thumbnails |
| `ContourPanelDetector` | Iterative connected components, ink masks, gutter splitting, loose-art/lettering handling, borderless regions, slanted-frame corners | **Evaluate in Phase D**, especially for artwork crossing gutters; first validate memory/time and no-content-loss before enabling for exports |
| `PanelOrder` | Row/column overlap grouping, RTL/LTR, tall panel next to stacks, spread-half ordering | **Good candidate for comparison tests**; never silently accept ambiguous ordering without an explicit fallback |
| Android `PagerPageHolder`, `PagerViewer`, `PanelMaskView` | Reader-only zoom navigation, optional mask, configurable animation | **Do not port into conversion**; export must produce standalone X3/X4-sized image pages, with no new firmware processing |
| Upstream tests | Synthetic gutter, borderless, spill and ordering cases | Add independently authored equivalent test *scenarios* to our regression suite; do not blindly copy source/tests |

**Key distinction:** upstream PR #4045 detects panels for **interactive Android reader zoom**. MangaNooir requires **offline preprocessing and physical per-panel page export** for XTC/XTCH/EPUB2/EPUB3/CBZ. Upstream returns bounded rectangles around ink; our export's no-artwork-loss guarantee needs full-image coverage or deliberate overlap/whole-page fallback. A detector that omits whitespace/loose artwork cannot be plugged straight into `MihonNooirExporter` without additional coverage checks.

**Near-term order:**

1. Keep Phase A as the safe baseline and integrate Android opt-in settings, thumbnail analysis and screen-sized per-panel rendering in Phase B.
2. Add fixtures for panels joined by speech balloons, black gutters, staggered layouts and unusual page ratios; compare our conservative output with upstream #4045's algorithm **without assuming upstream is always right**.
3. Improve geometry only where validated against real permitted pages and preserved coverage; no OCR, paid AI, mandatory preview or device-side panel analysis.
4. After five-format Android export smoke tests and format/page-count preflight, validate actual X3/X4 readability. Preserve original conversion behavior when Panel Mode is off.

**Upstream pin audit:** `config/mihon-upstream.sha` currently pins `deeb9b2584b6e246135cbd31da49a39e6b6e1e52` (17 October 2026). At review, Mihon upstream `main` was **15 commits ahead** of that pin, affecting 65 files; notably logging header redaction, WebGPU viewer dependency, library update ordering and a Compose download queue migration. Do not confuse this with the unmerged panel PR #4045; it is not in those 15 commits. Review individual upstream changes and Android overlay compatibility before updating the pin.\n\n**Other upstream changes worth tracking separately:** stable Mihon v0.21.2 (13 October 2026) contains bug fixes around migration/database updates and extension installation; do not rebase MangaNooir blindly because the fork overlays Android-specific export code. Review targeted fixes against existing fork behavior first. Open upstream PRs for download queue Compose migration, extension image decoding and reader settings are *not* merged features and should be tracked separately.
