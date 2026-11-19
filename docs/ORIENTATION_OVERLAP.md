# Landscape, portrait, Auto Fit and overlapping page cuts

> **Current Android release:** [MihonNooir v1.0.18](https://github.com/toshio2011/mihon-nooir/releases/tag/v1.0.18) (16 November 2026). This guide describes shipped features; see [release notes](releases/v1.0.18.md). Individual inputs, sources and reader firmware may behave differently. Windows is not publicly released.



## Why this exists

Traditional portrait manga pages are too small if you simply rotate and fit the entire page on a landscape-held XTEink X3/X4. A better approach divides the tall page into smaller **overlapping top-to-bottom sections**, making dialogue easier to read.

Inspired by the setting ideas from [MangaPoint](https://github.com/W1seGit/MangaPoint) and [xtcjs](https://github.com/varo6/xtcjs). The geometry and rendering code is implemented in Kotlin for MihonNooir, not a line-for-line port. Both upstream projects are MIT licensed.

## Choose the settings

1. In MihonNooir, open a manga, **long-press** a chapter, and select the chapters you want.
2. Tap **Export** in the manga toolbar (or open **More → Nooir Tools → Send & Convert files** for local CBZ/images).
3. Expand **More conversion settings**.
4. Choose **Include separate series cover** if you want the actual cover ahead of the first selected manga page; disable it to read directly from the chapter.
5. Choose **Reading segmentation**: Page, Row by Row or Panel by Panel. Pick **Portrait**, **Landscape**, or **Auto Fit** (available with row/panel detection).
6. When using **Landscape**, choose the **Rotate content clockwise** direction. Turn it off if you prefer the opposite physical turn.
7. Select **Auto / Pages / Webtoon** layout. For a long strip or a portrait page being read in landscape, choose **Overlapping page cuts** from **0% (no repeat)** to **50%**. Start around **8–12%**.
8. Optionally set white-margin trimming, split spreads, manga reading direction, contrast, rendering style, Context Peek (Panel by Panel only), and full-page overview. Use the live preview before exporting.
9. Save as XTC, XTCH, EPUB 2 Classic, EPUB 3 Modern or CBZ, or send to your XTEink through Folio Nooir's File Transfer mode.

The settings are saved for your next export, including a legacy migration path for jobs/settings created in 1.0.4/1.0.5. The chapter selection and format/device are separate from these image settings.

## How output behaves

| Input and chosen setting | Behavior |
|---|---|
| Portrait + Pages | Existing one-page-per-image conversion, or split a wide spread when enabled |
| Portrait + Webtoon | Slice long image into top-to-bottom screen-sized pages, with adjustable overlap |
| Landscape + Pages | Split a tall portrait page into several overlapping sections, each rotated into the XTC/XTCH portrait framebuffer |
| Landscape + Webtoon | Slice long scroll images into shorter overlapping sections suitable for sideways reading |
| Landscape + double-page spread splitting | Split right/left (or left/right) first, then slice tall halves when useful |
| Landscape + spread splitting OFF | Keep a panoramic image whole instead of forcing it into multiple panels |
| Row by Row + Auto Fit | Preserve horizontal tiers; wide rows may be rotated for a larger fitted view |
| Panel by Panel + Auto Fit | Detect individual panels, protect uncertain/sparse content, and fit each final view in its better orientation |
| Export from MihonNooir with real series cover | Keep the actual cover upright and unsplit even when chapter pages use landscape |

**XTC/XTCH:** Pages still have the reader's normal physical resolution (X4 480×800 or X3 528×792). The actual artwork is rotated within that frame so the user turns the physical reader sideways. This avoids pretending the installed firmware supports a new page resolution.

**EPUB/CBZ:** In landscape mode the generated manga content images have sideways dimensions (X4 800×480 or X3 792×528). Reader orientation support varies; test on your firmware.

**Overlap:** Duplicates the last 0–50% of a previous image slice on the next slice. The original chapter images are preserved; no pixels between cuts are discarded. Overlap is a **reading aid**, not panel/speech-bubble detection. An optional zero-overlap mode preserves the non-repeating behavior.

## Panel and row reading

**Row by Row** preserves each confirmed horizontal tier, including its side-by-side panels. **Panel by Panel** tries actual panel boundaries, may re-check a broad region for inner panels and can use guarded overlapping views as a fallback. It also protects sparse panels, single-frame artwork and short decorative footers when supported by detector evidence.

Auto Fit compares fitted screen occupancy rather than stretching an image. Wider regions often benefit from landscape; tall ones usually fit portrait. Near-square rows may stay portrait to avoid unnecessary rotation. White margins can remain if the source and display shapes differ. Use the separate **Context Peek** option (0–8%, default off) for small neighboring-panel context; changing normal overlap does not force the detector to invent panel boundaries.

**Show full page before splits** provides an optional original-page overview; **Keep first page of each chapter whole** is independent. See [Panel Auto Fit](PANEL_AUTO_FIT.md) and [conversion settings](CONVERSION.md).

The configurable **Enlarge oversized splits** option is **not in the published v1.0.18**. In the v1.0.19 release candidate, it is on by default in Row by Row and Panel by Panel. Minimum screen fill defaults to 50% (25–80%); this is a guideline for eligible poorly fitting views, not a promise to split every panel below that threshold. The detector tries supported natural boundaries first, then uses a separate 12% protective overlap when both resulting views improve scale. Ordinary overlap remains independent. Auto Fit may preserve a wide illustration without extra zoom; fixed Landscape can produce different frame counts. Manual crop coordinates, advanced zoom/tiling and other proposed controls are not guaranteed available.

The separate [PDF converter](PDF_TO_EPUB.md) uses its own text/image mode, not manga panel segmentation.

## Validate on device

- [ ] Normal portrait manga with no overlap reads exactly like earlier builds.
- [ ] Landscape portrait pages have multiple readable top-to-bottom panels, without tiny whole-page scaling.
- [ ] On each landscape page, the words are upright when holding X4 sideways in the intended rotation direction.
- [ ] 0% overlap gives touching slices; 10% gives repeated lines; no missing content or infinite loops.
- [ ] Double-page spreads obey the chosen right-to-left / left-to-right order.
- [ ] The XTC/XTCH file opens and cover/synopsis navigation still works in Folio Nooir.
- [ ] EPUB/CBZ landscape output renders correctly in a supporting reader.
- [ ] Legacy 1.0.4/1.0.5 conversion setting preferences and queued jobs still deserialize.
- [ ] Physical device battery/memory/performance looks acceptable.

These are suggested device checks for the released settings, not a claim that every device/input has passed.

## Format support

The [EPUB 2 / EPUB 3 / XTC / XTCH guide](EXPORT_FORMATS_EPUB2.md) explains format selection and reader-specific presets. EPUB 2 uses the same chapter-image slicing and orientation controls as EPUB 3; dedicated covers stay upright and unsplit. The EPUB 2 standard does not define the EPUB 3 `page-progression-direction` OPF spine attribute, so it is **not emitted**: navigation remains in selected chapter order, while right-to-left reading behavior must be tested/configured in each EPUB 2 reader.
