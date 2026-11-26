# Manga export and conversion gallery

[All user guides](README.md) · [Choose chapters](CHAPTER_SELECTION.md) · [Conversion settings](CONVERSION.md) · [Auto Fit](PANEL_AUTO_FIT.md)

This evergreen guide explains how to export manga and compare conversion choices. The linked Android captures are release-source emulator evidence, not signed-APK or physical-reader captures. Desktop is a development preview. The comparison gallery below is a capture backlog, not a claim that the comparison images already exist.

## Export manga on Android

1. Open the manga details page, long-press a chapter and tap additional chapters to select them.
2. Tap the Export toolbar action. Normal Export uses downloaded chapters; Quick Export can fetch selected missing chapters from a compatible online source. If no chapters are selected, Normal Export uses all downloaded chapters.
3. Choose the reader profile and output format independently.
4. Expand **More conversion settings** and choose **Apply recommended manga panel settings** for Japanese manga.
5. Check the segmentation, orientation, reading direction and enlargement settings. Preview the first page; disable **Keep first page of each chapter whole** for a sample intended to demonstrate splitting.
6. Save the output to a new local file. For large exports, copy the saved book to SD/USB where supported instead of sending a large single file over Wi-Fi.
7. Open **More → Nooir Tools → Universal Book Converter → Preview finished XTC / XTCH book** and inspect the actual saved file.

See [chapter selection and Quick Export](CHAPTER_SELECTION.md) and [transfer instructions](WIFI_TRANSFER.md). The chapter-selection and export dialog screenshots are still pending.

## Recommended starting point for XTEink

| Setting | Starting point |
|---|---|
| Reader | Matching device profile; X4 480×800, X3 528×792 |
| Format | XTC for monochrome, XTCH for four gray levels |
| Layout | Pages |
| Segmentation | Panel by Panel |
| Orientation | Auto Fit |
| Direction | Right to left for Japanese manga |
| Ordinary overlapping page cuts | 0% for recommended panel preset |
| Enlarge oversized splits | On |
| Minimum screen fill | 50% |
| Context Peek | Off |
| Full-page overview | Optional; preset off |

The preset retains some earlier preferences, so check them explicitly. The detector is heuristic; uncertain artwork can stay together. Eligible oversized views may use a separate 12% protective overlap even with ordinary overlap at 0%. Auto Fit does not stretch or crop art to fill the screen. For native XTC/XTCH, turn the reader physically sideways for rotated wide views.

## Existing actual output evidence

The two images below show decoded pages of a finished synthetic XTCH in the Android release-source emulator. They demonstrate saved-file inspection, **not** manga panel detection or an XTC/XTCH quality comparison.

| Actual saved XTCH: image page | Actual saved XTCH: text page |
|---|---|
| ![Decoded image fixture](screenshots/android-v1.0.20/universal/01c-actual-decoded-xtch-page.png) | ![Decoded text fixture](screenshots/android-v1.0.20/universal/01d-actual-decoded-xtch-next-page.png) |

[Capture provenance and viewer walkthrough](ANDROID_VISUAL_GUIDE.md#d-inspect-a-finished-xtcxtch).

## Compare actual manga results

The planned comparison gallery uses the same legally shareable source page in each pair or sequence. Keep the reader profile and all unrelated settings constant; show every generated view in reading order, including the full-page original. Do not substitute live-preview thumbnails, synthetic checkerboards or diagrams for actual decoded manga output.

| Comparison | What to inspect | Capture status |
|---|---|---|
| Page vs Row by Row vs Panel by Panel | View count, reading order, scale and grouped columns | Pending |
| Portrait vs Landscape vs Auto Fit | Fitted occupancy, rotation and white margins | Pending |
| Ordinary overlap 0% vs 10% | Repeated area and continuity on long strips | Pending |
| Enlargement off vs on, minimum fill 50% | Natural boundaries or guarded overlap on oversized views | Pending |
| Context Peek off vs 4% | Adjacent confirmed panel context | Pending |
| Full-page overview off vs on | Original overview followed by detail views | Pending |
| Keep first chapter page whole off vs on | Independent protection of opening artwork | Pending |
| XTC vs XTCH | Decoded monochrome vs four-level gray on same artwork | Pending |
| Floyd–Steinberg, Atkinson, Bayer 8×8, Threshold | Line art, shading and dithering texture | Pending |
| Right-to-left vs left-to-right spread split | Reading order of facing pages | Pending |

## Capture and publication checklist

- Use original or explicitly licensed artwork with ordinary grid, wide/oversized, sparse, continuous long-strip, diagonal and double-page examples. Record creator, license and source checksum. Never include a private library or commercially distributed manga without permission.
- Capture real Android and desktop screens with a clean fixture library. Record platform, app version/commit, build type, test run, source page ID, profile and complete conversion settings.
- Export actual books through each platform's conversion path. Decode the finished XTC/XTCH files and preserve the full frame sequence. For EPUB/CBZ examples, inspect actual packaged images and navigation. Record output filename, SHA-256, frame count and dimensions.
- Keep source and exported frame images lossless; present annotated copies separately from unmodified captures. Label rotation and any repeated region. Show failures or limitations honestly.
- Verify reading order, no missing content at splits, dimensions, output opens successfully and comparison settings differ only as intended. Record physical-reader verification separately from emulator/desktop evidence.
- Keep image paths stable, add descriptive alt text and captions, and update affected examples when behavior changes. Do not publish a completed-comparison label until the corresponding files have been captured and checked.

## Other workflows

[Android visual guide and coverage](ANDROID_VISUAL_GUIDE.md) · [Local files and queue](NOOIR_TOOLS_QUICK_START.md) · [PDF](PDF_TO_EPUB.md) · [Web novels](WEB_NOVELS.md) · [Troubleshooting](FAQ.md)

For the desktop development preview, see its [separate user guide](https://github.com/toshio2011/mihon-nooir/blob/desktop/final-acceptance-20261126/desktop/DESKTOP_GUIDE.md). Android and desktop can differ in UI, raster rendering and migration support; do not claim parity from shared geometry alone.
