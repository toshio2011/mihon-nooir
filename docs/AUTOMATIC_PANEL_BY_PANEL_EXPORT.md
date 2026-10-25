# MangaNooir — Automatic Panel-by-Panel Export
**Design handoff | 23 October 2026 | Status: APPROVED DIRECTION, NOT IMPLEMENTED**

Repository: https://github.com/toshio2011/mihon-nooir  
Primary device targets: Folio Nooir on XTEink X3 (528×792), X4 (480×800); X4 Pro and other available device profiles should remain supported through existing export infrastructure.

> **User decision:** People exporting hundreds of manga pages will not review every panel. Export must be automatic. If they choose **Panel by Panel**, they want genuinely separate panel screens. Larger exported files are acceptable. **Accuracy, preserved artwork/dialogue, reading order, readability, and device stability outrank file size.** No OCR, paid AI API, server, or heavy detection on the e-reader.

## TL;DR for the main development chat

Implement a **fully automatic, opt-in Panel-by-Panel conversion mode inside MangaNooir (Android Mihon fork)**, using insights from Adenzu's Manga Panel Extractor, Kumiko, and Pannello. Detect panels **on the phone during conversion**, sort by manga reading order, pre-render each detected panel as a device-sized screen, and feed these pages to the **existing** XTC / XTCH / EPUB 2 / EPUB 3 / CBZ writers. No changes to Folio Nooir and no runtime high-resolution crop/pan/zoom.

**DO NOT** turn automatic preview/confirmation into a requirement. **DO NOT** combine multiple detected panels in Panel-by-Panel mode merely to reduce page count. **DO NOT** implement OCR or remote inference as a hidden requirement. Keep existing non-panel behavior unchanged when the feature is off. A separate **Smart Layout** option (multiple panels per output page) can come later, but **Panel by Panel is the priority**.

## Product behavior and modes

- **Original Pages (off):** Precisely the existing export behavior, settings, and output; preserve backward compatibility.
- **Panel by Panel (primary feature):** For each page, try to detect and order the individual panels. Normally produce **one complete panel per output screen**; letterbox rather than stretch. For a very tall/wide panel whose lettering would be unreadably small, produce consecutive overlapping crops **of that same panel** without cutting off artwork; never silently discard a region.
- **Smart Layout (optional later):** Combine several small panels per output screen. Separate choice, never secretly used by Panel-by-Panel mode.

Input scope: manga pages selected from downloaded Mihon chapters, Quick Export when supported, and imported CBZ/ZIP/image collections. Preserve the original chapter selection, cover, metadata, and chapter boundaries. Avoid changing unrelated PDF mixed-text reflow or web novel converters. Once image-page PDF import is routed through the manga converter, it can use the same panel engine; this is not permission to regress selectable PDF prose.

Process: **Choose chapters/files → choose output device + format + Panel by Panel → Export/Send → Read**. The exported book is already panel-optimized. No required per-page review, crop boxes, dialog, or manual intervention. A compact **optional** after-export summary might say: "120 original pages → 587 panel screens; 4 original-page fallbacks."

## Reference research — learn, don't blindly vendor

1. **Adenzu / Manga-Panel-Extractor** — https://github.com/adenzu/Manga-Panel-Extractor  
   MIT source license. The source uses grayscale preprocessing, adaptive thresholding, background masking, contour geometry, optional joined-panel splitting, and more aggressive fallbacks. Some advanced code paths use PyTorch/YOLO: **do not make these mandatory**, and check model-weight/dataset licensing separately before any future incorporation. Extract lessons for manga-specific gutter/joint segmentation and robust fallback, not the Python/PyQt application.
2. **njean42 / kumiko** — https://github.com/njean42/kumiko  
   AGPL-3.0-or-later. Uses OpenCV contour-based panel geometry, right-to-left numbering, panel expansion and JSON coordinate output. Extract general concepts: cheap initial detection, configurable direction, edge/gutter analysis, coordinate representation, and deterministic order. **Do not copy AGPL code** into MangaNooir without a deliberate license compatibility and distribution assessment.
3. **marcopiraccini / pannello** — https://github.com/marcopiraccini/pannello  
   AGPL-3.0-or-later. It **vendors Kumiko**; it is not a completely independent third engine. Valuable ideas include normalized bounding boxes, output page-order alignment, problem-page detection, and fallback only when baseline detection looks wrong. Its optional CPU models (including Magi) may be large, slow, or subject to different licensing; **not required** here. A bad detection can be signalled by empty result, a lone huge rectangle, tiny slivers, excessive overlap, uncovered artwork, or suspicious mergers; validate criteria against actual manga before hardcoding thresholds.

**Licensing policy:** Build a fresh Kotlin/Android implementation of general, published image-processing techniques or selectively use demonstrably license-compatible libraries with proper notices. MIT source can be reused subject to its license; AGPL source cannot just be pasted into a project with different licensing assumptions. No source code, model weights or datasets should be included without reviewing their terms.

## Proposed lightweight automatic detection pipeline

1. **Preflight / classify page.** Detect cover, ordinary manga page, landscape spread, very long webtoon, or possibly non-panel splash/credits page. Only classify based on geometry and pixels; no OCR. Existing spread-split setting and reading direction matter. Cover is always exempt from splitting.
2. **Analyze a bounded downsample.** Generate grayscale thumbnail/scanline statistics on Android; optionally use an efficient compatible contour detector. Detect whitespace gutters, border lines, connected regions and candidate panel rectangles. Source image stays on Android, never transmitted to a service. Keep per-page RAM bounded.
3. **Manga-specific refinement.** Attempt to split touching/joined rectangles; account for borderless layouts, stepped rows, high-contrast illustrations and speech bubbles crossing borders. Do not blindly crop close to visible artwork: configurable safe padding / clamping to source bounds.
4. **Candidate validation & confidence.** Reject invalid rectangles; flag empty/one suspicious full-page region, tiny noise/slivers, implausible overlap, missing large artwork area, or split fragments. If suspicious, try an alternate, more conservative image-based pass. Do not equate a genuine splash page with failure. Never report confidence as a guarantee of correctness.
5. **Choose safe result.** Prefer validated panel regions when they adequately preserve the page; on unresolved ambiguity, **output the intact original page as one fallback screen** (or existing safe slicing if selected) rather than risk destroying artwork/dialogue. Count fallbacks in summary. Do not stall a chapter for manual review.
6. **Sort in reading order.** For manga, use right-to-left *within coherent visual tiers* and top-to-bottom across tiers. Handle spanning panels and staggered boxes; naive sort by y and x alone is insufficient. Support left-to-right direction for other comics. Prefer original order when uncertainty could reorder story.
7. **Render on the phone.** For each ordered panel: crop with guard padding → resize to selected device viewport with aspect ratio preserved and no stretching → optional existing trim/contrast/dither as applicable → if panel too elongated, emit consecutive overlapping sections of **that panel alone**. Use existing XTC/XTCH normal portrait-frame + rotation rules for landscape; never depend on firmware zoom. Avoid materializing every panel as full-resolution source bitmap at once.
8. **Encode with existing writers.** Reuse XTC (1-bit), XTCH (2-bit), EPUB 2, EPUB 3 and CBZ writers, metadata, cover, chapter indexes, and XTEink upload. New panel mode is a **page-region producer**, not an alternate format encoder.

**Design invariants:** (a) every source image region remains reachable via either exported panels or the full-page fallback; (b) no empty output screens; (c) chapter/page ordering is deterministic; (d) cancel/cleanup works; (e) original Mihon downloads remain untouched; (f) export off exactly preserves legacy behavior.

## Device and file-size constraints — size can grow, memory cannot

User **accepts bigger output**: do not merge panels or degrade text solely to reduce filesize. But existing caps still exist:
- X4 XTC bitmap payload is ~48,022 bytes/page; XTCH ~96,022 bytes/page (plus headers/index). Splitting many pages increases file size and page-turn count.
- The current XTC/XTCH format has page-count limits, and MangaNooir has its own per-job guards; large exports may also hit existing Wi-Fi transfer/preflight limits (reported ~180 MiB in docs).
- **Do not silently exceed caps, truncate panels or return a corrupted file.** Calculate projected page count and output size; if needed, design **safe automatic volume splitting** with chapter boundaries and distinct filenames, or fail clearly before heavy processing where splitting isn't yet supported. This is a separate requirement to implement/test, not a claim it already works.
- Detection is carried out on the **Android phone**, preferably sequentially per source page, with bounded decoding and buffers. The X3/X4 only decodes/display the final low-resolution reading pages. No AI model loaded on reader, no high-res runtime crop, no extra Nooir firmware RAM requirement.

## Where to integrate in the existing codebase

Code inspected 23 Oct 2026 (paths reflect current `main`):
- `overlay/app/src/main/java/dev/nooir/mangabridge/AndroidPageSource.kt`: current image decoding, `PageSlicePlanner.plan(...)`, trim/render/quantize. Add an optional detection/region generation hook **before** render, keeping legacy branch untouched.
- `overlay/app/src/main/java/dev/nooir/bridge/PageSlicePlanner.kt`: current geometry for spreads, webtoons, orientation/overlap. Retain existing function behavior; add panel-region planning/composition as new API rather than rewriting defaults.
- `overlay/app/src/main/java/dev/nooir/mangabridge/MangaConversionSettingsCore.kt`: serialize mode and small safety options with **backward-compatible tokens**, including legacy queued jobs/preferences. Keep unknown older values safely defaulting to Off.
- `overlay/app/src/main/java/dev/nooir/mangabridge/MangaConversionOptions.kt`: add a simple **Panel by Panel** control under image/layout settings (Off/Panel by Panel; Smart Layout only when ready); no mandatory preview.
- `MihonNooirExporter.kt` and `ImportedMangaConverter.kt`: pass mode into shared `AndroidPageSource` and report detection/fallback counts; preserve chapters, cover, metadata and queue progress.
- `GrayscaleConverter.kt`, `XtcCodec.kt`, `Epub2Writer.kt`, `BookArchiveWriter.kt`: **reuse** existing writer path. Verify increased page counts and metadata maps.
- New platform-neutral geometry/confidence classes (proposed: `PanelDetectionCore.kt`, `PanelReadingOrder.kt`) plus Android bitmap adapter, rather than a heavyweight separate Python converter.
- No modifications to https://github.com/toshio2011/folio-nooir unless specifically re-authorized later.

Remember that this is an **Android Mihon-based app**, not a browser-only converter. Existing project has already implemented normal margin trim, spread split, layout presets, landscape/portrait, overlap, EPUB 2/3 and portable XTC/XTCH: reuse, don't implement duplicates.

## Acceptance tests before declaring done

- Clean 2×3 grids: exactly six panels in expected manga RTL/top-to-bottom order; LTR toggle reverses within-row order.
- Uneven row heights / spanning panels / borderless panels / intersecting balloons / touching panels: best possible automatic results, safe fallback rather than clipped dialogue.
- Single splash/credit page and wide spread: preserve intact image or split intentionally, never lose half.
- Webtoon long strips: preserve prior webtoon segmentation unless mode and input meaningfully support panels.
- Large batches, e.g. hundreds of pages: **no required individual previews or dialogs**; app resumes/cancels work cleanly; output page count reproducible.
- Device presets X3 (528×792), X4 (480×800), X4 Pro, other devices: correct output dimensions; no orientation regression.
- For each export XTC, XTCH, EPUB 2, EPUB 3, CBZ: verify output opens, cover/index/chapter order correct, byte/OPF constraints met, no placeholders; include sample **real manga pages where permission allows** and synthetic fixture tests.
- Explicit XTC/XTCH size/page-count stress: enforce/preflight caps and test possible volume splitting instead of pretending huge files always work.
- Diff baseline when mode Off: identical old slicing, options migration and exporter behavior. Existing unit / pinned Mihon integration / Android CI must stay green.
- Physical Folio Nooir X3/X4 testing is required for claims about real readability, page-turn speed, and battery behavior. Green CI alone isn't proof.

## Suggested implementation order

**Phase A (P0):** Pure geometry/RTL order + fast basic image detection + safe fallback + synthetic tests; keep mode Off for existing conversions.  
**Phase B (P0):** Add opt-in Panel by Panel to Android source adapter and conversion settings; render 1 panel/screen, elongated-panel overlap, format integration + Android CI.  
**Phase C (P0):** Detect and report failure counts, large-batch memory/size/page-limit handling, read real-format fixtures; produce complete docs/how-to and hardware test matrix.  
**Phase D (P1):** Improve complex manga handling using test corpus and tune thresholds. Optional one-click overview or diagnostic contact sheet for *troubleshooting only*, never mandatory.  
**Phase E (P2):** Separate Smart Layout mode if users ask for it; never bundle into Panel-by-Panel behavior.

**Definition of done:** Users can select chapters, enable Panel by Panel, export without manually approving pages, and read full-page-sized **pre-rendered individual manga panels** in Folio Nooir. Bad detection fails safely without losing content. All five output formats are structurally valid and compatible in tests. Clearly disclose known difficult layouts and physical test results.

## Current status at time of this handoff

This file is **documentation/design only**, **not an implemented panel detector**, and does not assert that the feature currently works. No code changes to Folio Nooir. Do not merge/publish/tag a release on the basis of this design alone.

For the original sources, see the links above. The most relevant MangaNooir docs already in `main` are `docs/CONVERSION.md`, `docs/ORIENTATION_OVERLAP.md`, `docs/EXPORT_FORMATS_EPUB2.md`, and `docs/HANDOVER_2026-10-21.md`.
