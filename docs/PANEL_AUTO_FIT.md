# MangaNooir — Panel Auto Fit (Phase C, opt-in)

## User-facing behavior

When **Panel by Panel (automatic)** is enabled, the Page Orientation selector gains **Auto Fit** alongside Portrait and Landscape.

- The OCR-free Phase A detector first attempts to identify panel rectangles; uncertain/splash/borderless pages fall back to the existing safe portrait layout. No panel is invented.
- For each confidently detected panel, the shared `PanelAutoFit` compares **how much of the screen the entire panel would occupy** in portrait vs landscape. It only selects landscape if the screen occupancy improves by at least **25%**, with a small absolute margin to avoid noisy switches. A near-square panel stays portrait.
- The selected viewport is passed into `PanelViewportPlanner` *before* splitting exceptionally long/wide panels. One detected panel remains one screen where possible; unusually elongated panels still use overlapping slices **of that panel only**.
- Auto Fit never crops artwork, text or speech bubbles to force a full-bleed image. White bars can remain when the panel shape does not match the screen.
- EPUB 2/3 and CBZ may contain **mixed portrait and landscape page images** with native target-profile dimensions; each image carries its own width/height.
- XTC/XTCH must retain the target device's fixed portrait framebuffer dimensions. Wide panels are rendered rotated into that framebuffer, so a user may need to **physically turn the X3/X4 sideways** for those panels. Auto Fit is therefore **off by default**.
- Covers remain portrait and bypass panel detection. If panel mode is switched off in Android settings, Auto Fit is reset to Portrait.
- Both Android and Windows compile the **same** `PanelAutoFit.kt` geometry and existing format writers; Android Bitmap and desktop Java2D raster interpolation may still differ.

## Compatibility and testing

The conversion options token remains **17 fields**: `orientation=AUTO_FIT` is an additional allowed value of the existing orientation field. All older seven-to-sixteen-field tokens and all existing portrait/landscape jobs retain their original meaning. Auto Fit only takes effect when `panelMode=PANEL_BY_PANEL`; otherwise the renderer falls back to portrait.

Automated tests cover deterministic portrait/wide/square decisions, no-crop geometry, bounded panel counts, token migration, and actual synthetic all-format panel exports with correct XTC/XTCH frame counts and valid EPUB/CBZ image sizes.

**Still pending:** complex real-world manga corpus, exact Android-vs-desktop pixel parity, X3/X4 hardware viewing comfort, mixed-orientation usability and large-volume preflight/splitting. Auto Fit is not a substitute for accurate panel detection; uncertain pages remain whole.

This work is stacked on the existing Phase B panel export PR, **not released**, and does not modify Folio Nooir.
