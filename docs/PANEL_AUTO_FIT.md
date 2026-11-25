# MihonNooir — Auto Fit (Row by Row and Panel by Panel)

> **Current Android release:** [MihonNooir v1.0.20](https://github.com/toshio2011/mihon-nooir/releases/tag/v1.0.20) (24 November 2026). This guide describes shipped features; see [release notes](releases/v1.0.20.md). Individual inputs, sources and reader firmware may behave differently. Windows is not publicly released.

## Recommended manga settings

1. Select chapters in the manga details screen and open Export; for local comics use Nooir Tools → Universal Book Converter → Local files.
2. Choose the matching reader profile and output format.
3. Open **More conversion settings → Apply recommended manga panel settings**.
4. Verify **Enlarge oversized splits: on** (default), **Minimum screen fill: 50%** (default), **Context Peek: off** (default). These three settings are retained by the shortcut, so check any previous choices.
5. Preview a sample before exporting. Save locally for large books; inspect completed XTC/XTCH pages with the actual saved-file preview.

| Setting | Starting point |
|---|---|
| Layout / segmentation / orientation | Pages / Panel by Panel / Auto Fit |
| Read right to left / trim margins / split spreads | On / On / On |
| Overlapping page cuts | 0% (ordinary overlap) |
| Enlarge oversized splits | On; 50% minimum screen fill, adjustable 25–80% |
| Context Peek | Off; optional 0–8% |
| Show full page before splits | Off |
| Keep first page of each chapter whole | On by default; retained by shortcut |

The 50% threshold is a guideline for poorly fitting regions, not a guarantee of full-screen coverage. The separate 12% protective overlap is considered only when an eligible split improves both views. Narrow fragments lacking credible boundaries may be joined; genuine narrow panels can remain separate. Strong internal paired borders may recover previously grouped panels. Original artwork is not rewritten.

## User-facing behavior

When **Row by Row** or **Panel by Panel** is enabled, the Page Orientation selector gains **Auto Fit** alongside Portrait and Landscape.

- Row by Row detects horizontal tiers and keeps their columns together. Panel by Panel tries individual boundaries and guarded recovery. Protected artwork and uncertain pages may remain whole.
- The shared geometry compares how much of the display the whole region occupies when fitted in each orientation. Row mode uses the conservative 25% relative improvement and 5 percentage-point margin before landscape; Panel by Panel chooses maximum fitted occupancy for its final views. Exact ties prefer portrait.
- Fit orientation is chosen before safe slicing. Panel by Panel can independently refit final slices, recover valid inner panels and use guarded overlap when appropriate. With enlargement enabled, Row by Row may subdivide an oversized tier into overlapping full-width horizontal views; it does not intentionally separate side-by-side panels into columns.
- Auto Fit never crops artwork, text or speech bubbles to force a full-bleed image. White bars can remain when the panel shape does not match the screen.
- EPUB 2/3 and CBZ may contain **mixed portrait and landscape page images** with native target-profile dimensions; each image carries its own width/height.
- XTC/XTCH must retain the target device's fixed portrait framebuffer dimensions. Wide panels are rendered rotated into that framebuffer, so a user may need to **physically turn the X3/X4 sideways** for those panels. Auto Fit is therefore **off by default**.
- Covers remain portrait and bypass panel detection. If panel mode is switched off in Android settings, Auto Fit is reset to Portrait.
- Both Android and Windows compile the **same** `PanelAutoFit.kt` geometry and existing format writers; Android Bitmap and desktop Java2D raster interpolation may still differ.

## Compatibility and testing

The v1.0.19 conversion token includes rendering style, Context Peek, enlargement and minimum screen fill (24 fields); older supported tokens migrate. Auto Fit is available in both row and panel modes; Page mode resets Auto Fit to Portrait. Current Windows and Android use shared orientation geometry, but raster and full pipeline parity require separate testing.

Automated tests cover deterministic portrait/wide/square decisions, no-crop geometry, bounded panel counts, token migration, and actual synthetic all-format panel exports with correct XTC/XTCH frame counts and valid EPUB/CBZ image sizes.

**Verified on representative inputs:** 147 source pages, 588 desktop renders across the RC1/RC2 Panel and Row modes, and five Android manga instrumentation tests. All 147 Row cases and 130 of 147 Panel cases were pixel-identical; seven Panel cases consolidated fragments and ten recovered inner panels. Owner reviewed the RC2 phone output. Exact Android-vs-desktop pixel parity, every possible manga layout, and every physical reader/firmware combination are not established.

These improvements were shipped in Android v1.0.19 and are retained in v1.0.20. Recognition remains heuristic, not a semantic guarantee. If two panels remain grouped or an unexpected thin frame appears, try Row by Row or Page mode, enable the optional full-page overview, or save a private diagnostics ZIP for troubleshooting. Switching ordinary overlap does not repair a detector boundary. Windows remains in development; Folio Nooir firmware is not modified. [Original release evidence](releases/v1.0.19.md) · [Current release](releases/v1.0.20.md).
