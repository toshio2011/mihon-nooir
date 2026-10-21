# Landscape, portrait and overlapping page cuts — experimental preview

> **Preview only:** Included in the [v1.0.11 draft build](https://github.com/toshio2011/mihon-nooir/actions/runs/39363272479), which passed Android CI but needs real device testing. The v1.0.1 main app does not expose these settings.

## Why this exists

Traditional portrait manga pages are too small if you simply rotate and fit the entire page on a landscape-held XTEink X3/X4. A better approach divides the tall page into smaller **overlapping top-to-bottom sections**, making dialogue easier to read.

Inspired by the setting ideas from [MangaPoint](https://github.com/W1seGit/MangaPoint) and [xtcjs](https://github.com/varo6/xtcjs). The geometry and rendering code is implemented in Kotlin for MangaNooir, not a line-for-line port. Both upstream projects are MIT licensed.

## Choose the settings

1. In MangaNooir, open a manga, **long-press** a chapter, and select the chapters you want.
2. Tap **Export** in the manga toolbar (or open **More → Nooir Tools → Send & Convert files** for local CBZ/images).
3. Expand **More conversion settings**.
4. Choose **Include separate series cover** if you want the actual cover ahead of the first selected manga page; disable it to read directly from the chapter.
5. Pick **Portrait** (normal upright X3/X4 reading) or **Landscape** (hold the reader sideways).
6. When using **Landscape**, choose the **Rotate content clockwise** direction. Turn it off if you prefer the opposite physical turn.
7. Select **Auto / Pages / Webtoon** layout. For a long strip or a portrait page being read in landscape, choose **Overlapping page cuts** from **0% (no repeat)** to **50%**. Start around **8–12%**.
8. Optionally set white-margin trimming, split spreads, manga reading direction, contrast and dithering.
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
| Export from MangaNooir with real series cover | Keep the actual cover upright and unsplit even when chapter pages use landscape |

**XTC/XTCH:** Pages still have the reader's normal physical resolution (X4 480×800 or X3 528×792). The actual artwork is rotated within that frame so the user turns the physical reader sideways. This avoids pretending the installed firmware supports a new page resolution.

**EPUB/CBZ:** In landscape mode the generated manga content images have sideways dimensions (X4 800×480 or X3 792×528). Reader orientation support varies; test on your firmware.

**Overlap:** Duplicates the last 0–50% of a previous image slice on the next slice. The original chapter images are preserved; no pixels between cuts are discarded. Overlap is a **reading aid**, not panel/speech-bubble detection. An optional zero-overlap mode preserves the non-repeating behavior.

## Not available yet

This does **not** add all MangaPoint settings. In particular: fixed manual margins, sensitivity/padding controls, rotation per page, interactive before/after crop preview, zoom and tiling, auto-levels/histogram stretch, content-aware quiet-row cutting, additional dithering kernels, arrows, and fully remembered per-manga/series presets remain on the roadmap.

**PDF → reflowable mixed text-and-images EPUB** is available in the experimental preview as a **separate tool**. These manga image/CBZ settings do not automatically apply to PDF text extraction.

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

These are experimental image settings until the actual phone and XTEink tests pass.

## v1.0.12 format support

The experimental [EPUB 2 / EPUB 3 / XTC / XTCH guide](EXPORT_FORMATS_EPUB2.md) explains the new format selection and reader-specific presets. EPUB 2 uses the same chapter-image slicing and orientation controls as EPUB 3; dedicated covers stay upright and unsplit. The EPUB 2 standard does not define the EPUB 3 `page-progression-direction` OPF spine attribute, so it is **not emitted**: navigation remains in selected chapter order, while right-to-left reading behavior must be tested/configured in each EPUB 2 reader.
