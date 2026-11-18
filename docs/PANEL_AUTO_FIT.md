# MihonNooir — Auto Fit (Row by Row and Panel by Panel)

> **Current Android release:** [MihonNooir v1.0.18](https://github.com/toshio2011/mihon-nooir/releases/tag/v1.0.18) (16 November 2026). This guide describes shipped features; see [release notes](releases/v1.0.18.md). Individual inputs, sources and reader firmware may behave differently. Windows is not publicly released.

## User-facing behavior

When **Row by Row** or **Panel by Panel** is enabled, the Page Orientation selector gains **Auto Fit** alongside Portrait and Landscape.

- Row by Row detects horizontal tiers and keeps their columns together. Panel by Panel tries individual boundaries and guarded recovery. Protected artwork and uncertain pages may remain whole.
- The shared geometry compares how much of the display the whole region occupies when fitted in each orientation. Row mode uses the conservative 25% relative improvement and 5 percentage-point margin before landscape; Panel by Panel chooses maximum fitted occupancy for its final views. Exact ties prefer portrait.
- Fit orientation is chosen before safe slicing. Panel by Panel can independently refit final slices, recover valid inner panels and use guarded overlap when appropriate. Row by Row in v1.0.18 does not subdivide oversized confirmed tiers using the proposed new configurable enlargement fallback.
- Auto Fit never crops artwork, text or speech bubbles to force a full-bleed image. White bars can remain when the panel shape does not match the screen.
- EPUB 2/3 and CBZ may contain **mixed portrait and landscape page images** with native target-profile dimensions; each image carries its own width/height.
- XTC/XTCH must retain the target device's fixed portrait framebuffer dimensions. Wide panels are rendered rotated into that framebuffer, so a user may need to **physically turn the X3/X4 sideways** for those panels. Auto Fit is therefore **off by default**.
- Covers remain portrait and bypass panel detection. If panel mode is switched off in Android settings, Auto Fit is reset to Portrait.
- Both Android and Windows compile the **same** `PanelAutoFit.kt` geometry and existing format writers; Android Bitmap and desktop Java2D raster interpolation may still differ.

## Compatibility and testing

The v1.0.18 conversion token includes rendering style and Context Peek (22 fields); older supported tokens migrate. Auto Fit is available in both row and panel modes; Page mode resets Auto Fit to Portrait. Current Windows and Android use shared orientation geometry, but raster and full pipeline parity require separate testing.

Automated tests cover deterministic portrait/wide/square decisions, no-crop geometry, bounded panel counts, token migration, and actual synthetic all-format panel exports with correct XTC/XTCH frame counts and valid EPUB/CBZ image sizes.

**Still pending:** complex real-world manga corpus, exact Android-vs-desktop pixel parity, X3/X4 hardware viewing comfort, mixed-orientation usability and large-volume preflight/splitting. Auto Fit is not a substitute for accurate panel detection; uncertain pages remain whole.

Auto Fit is released on Android. The upcoming configurable oversized-split work is not shipped in v1.0.18. Windows remains in development; Folio Nooir firmware is not modified.
