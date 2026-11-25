# Formats, image conversion and metadata

> **Current Android release:** [MihonNooir v1.0.20](https://github.com/toshio2011/mihon-nooir/releases/tag/v1.0.20) (24 November 2026). This guide describes shipped features; see [release notes](releases/v1.0.20.md). Individual inputs, sources and reader firmware may behave differently. Windows is not publicly released.

[← Back to README](../README.md) · [All guides](README.md) · [Choose chapters](CHAPTER_SELECTION.md) · [Getting started](GETTING_STARTED.md) · [Wi-Fi transfer](WIFI_TRANSFER.md)

MihonNooir exports manga into e-reader-oriented formats while keeping the source chapters and library information in Mihon.

> **Version note:** Quick Export, Nooir Tools, the shared queue and advanced image settings are included in v1.0.20. [Nooir Tools guide](NOOIR_TOOLS_QUICK_START.md).

## Pick an output

| Format | Image/color approach | Navigation and metadata | Typical use |
|---|---|---|---|
| **XTC** | 1-bit black/white | Native title, author, chapter index and other fixed fields | Smaller, quick monochrome output for compatible XTEink firmware |
| **XTCH** | 2-bit / four-level grayscale | Same fixed-format family | Preserve a few shades for compatible XTEink readers |
| **EPUB 2 Classic** | Image-based EPUB 2.0.1 | OPF 2.0, NCX navigation and standard cover metadata | Older or resource-constrained readers |
| **EPUB 3 Modern** | Image-based EPUB 3 | OPF 3, nav XHTML, cover, creator and description | Compatible modern EPUB readers |
| **CBZ** | Ordered image archive | `ComicInfo.xml` plus optional JSON metadata | Comic-capable readers and conversion workflows |
| **Auto** | Preset chosen from device selection | Varies with chosen output | Convenient start; not always the best choice for every comic |

The current release includes X4 (480 × 800), X3 (528 × 792), X4 Pro and additional Kindle, Kobo, Boox, PocketBook and custom presets. These select output dimensions, not universal firmware guarantees. [Device profiles](DEVICE_PROFILES.md).

## Convert from downloaded manga

1. Open manga details in MihonNooir.
2. **Long-press** a chapter, then tap other chapters to select them. In **Normal Export**, selected chapters must be downloaded. If nothing is selected, all downloaded chapters of the manga are included. [Step-by-step chapter-selection guide →](CHAPTER_SELECTION.md)
3. Tap **Export** next to Download.
4. Choose device, format, and **Save file** or **Send to XTEink**.

The original Mihon manga files and metadata remain intact. Re-exporting into a different format is possible without re-downloading content you already have.

## Quick Export

Enable **Quick Export** after selecting chapters that are not permanently downloaded in Mihon. The installed online source is asked to fetch those chapter pages; the converter works **one chapter at a time** and cleans up temporary page images.

There is **no fixed five-chapter ceiling**, but large series still use time and storage. Individual remote images and temporary chapter data have guard limits; sending a single output to the XTEink has a separate **180 MiB** Wi-Fi per-file cap. Source websites can rate-limit, require sign-in or fail, and not every source has been tested.

## Import files already on the phone

Open **More → Nooir Tools → Universal Book Converter → Local files · EPUB, CBZ, ZIP, images**. This opens **Send & Convert files**.

1. Choose an input type before browsing; select up to **25 files**. Use **Browse all** if a file provider reports an incorrect MIME type.
2. Choose the target reader and a supported output. Comic CBZ/ZIP and supported images use the manga conversion settings; EPUB supports experimental XTC/XTCH pagination for text and illustrations.
3. For EPUB → XTC/XTCH, choose the reading preset, font family, font size, page margin, line height and book direction (Auto/LTR/RTL). These affect the actual exported pages.
4. Rename output files before processing if necessary. Choose **Convert & save supported files to phone folder**, **Convert & send**, or **Send original file(s)** where available. Conversion does not silently pass unsupported files through unchanged.
5. Use **Preview finished XTC / XTCH book** in Universal Book Converter (or the saved-output preview in the local tool) to inspect actual exported page pixels.

PDF has a separate **Universal Book Converter → PDF files** tool. Other unsupported documents may be transferred unchanged where the receiver permits them, but that does not imply reader compatibility. A ZIP of arbitrary documents is not automatically a manga archive. Jobs run sequentially; complex CSS, unsupported EPUB resources, and protected books may fail or render differently. [Quick start](NOOIR_TOOLS_QUICK_START.md) · [v1.0.20 release notes](releases/v1.0.20.md).

## Conversion adjustments

Expand **More conversion settings** in the export dialog.

| Control | What it changes |
|---|---|
| Manga / Manhwa / Manhua preset | Sets initial layout, direction and image options |
| Auto / Pages / Webtoon layout | Selects standard pages or long scrolling strips |
| Reading segmentation: Page | Keeps ordinary page processing; no panel detector |
| Row by Row | Detects horizontal tiers and keeps side-by-side panels together |
| Panel by Panel | Attempts individual panels in reading order; protected/uncertain artwork may stay whole |
| Portrait / Landscape / Auto Fit | Fixed orientation or best-fit orientation per detected region in row/panel mode |
| Overlapping page cuts | Repeats part of strips and provides protective padding around detected regions; not OCR |
| Context Peek (Panel by Panel only) | 0–8%; default off; repeats a small edge of a neighboring confirmed panel |
| Enlarge oversized splits | Default on; for eligible row/panel views only. Minimum screen fill 25–80%, default 50%. Attempts verified boundaries, then protective overlap when it improves readability. |
| Show full page before splits | Adds original page before multi-frame detail views, not before single-frame pages |
| Keep first page of each chapter whole | Independently protects chapter opening pages |
| Trim white margins / Split double-page spreads | Removes empty margins or separates facing pages |
| Read right to left | Sets reading order |
| Contrast | 50 = neutral |
| E-ink rendering style | Floyd–Steinberg, Atkinson, Bayer 8×8 or Threshold |
| EPUB / CBZ colors | Preserve original color if chosen; XTC/XTCH remain grayscale |
| Separate series cover / EPUB section grouping | Book packaging options |

### Recommended settings for Japanese manga

Tap **Apply recommended manga panel settings**: Pages layout, Panel by Panel, Auto Fit, ordinary overlap 0%, white-margin trim on, split spreads on, right-to-left on, and full-page overview off.

The shortcut retains your other settings. For a useful starting point, check that **Enlarge oversized splits** is enabled, **Minimum screen fill** is 50%, and **Context Peek** is off. Keep first page of each chapter whole defaults to on; disable it only if you also want the opening source page segmented. Choose the device profile and format independently.

The panel improvements introduced in v1.0.19 and retained in v1.0.20 rejoin weakly supported thin fragments, recovers strongly framed inner panels, recognizes supported slanted/spanning layouts and protects some continuous artwork. A real narrow panel can remain narrow. The new enlargement setting tries verified natural boundaries first; a separate 12% protective overlap is used only on eligible regions when both views improve scale. The ordinary overlap slider is independent. Use Row by Row or Page if a particular layout is not recognized. [Detailed settings and examples](PANEL_AUTO_FIT.md).

### Pick a rendering style

| Rendering | Typical appearance |
|---|---|
| Floyd–Steinberg | Smooth tonal shading; can look grainy; default |
| Atkinson | Crisp line art and clean whites; subtle shading can be lost |
| Bayer 8×8 | Ordered dot pattern; a visible regular texture |
| Threshold | Hard gray-level boundaries; crisp ink but gradients may band |

Try a small sample with your own manga first. The ideal style varies by artwork and display.

### Two different previews

- **Live preview:** choose a manga image or CBZ in Export, then **Preview first page**. Changing settings re-renders the intended first-page conversion before the book is written.
- **Actual book inspection:** use **Inspect an exported XTC / XTCH file**, then choose the saved file. Reads its actual encoded image planes and native resolution; an incomplete or invalid file is rejected. It does not edit the file.

Use optional **Save panel diagnostics after this export** in Panel by Panel only when troubleshooting. Reopen the export dialog to save the latest private ZIP; review the source images before sharing. The option is off by default.

[Orientation and overlap](ORIENTATION_OVERLAP.md) · [Format details](EXPORT_FORMATS_EPUB2.md) · [v1.0.20 release notes](releases/v1.0.20.md)

## What happens to metadata?

MihonNooir uses only the metadata available from Mihon or the imported comic file:

- **EPUB:** title, author, description, cover, language, publisher, identifiers, genres/subjects and navigation through the standard OPF/navigation files; optional extra JSON for Nooir-specific information.
- **CBZ:** image sequence, `ComicInfo.xml` (series/title/writer/artist/summary/genre and related fields when available), and optional supplementary metadata JSON.
- **XTC/XTCH:** native title/author, cover index and chapter navigation. Portable output has no standard synopsis field or private JSON trailer by default. The optional extended-metadata setting can add a private trailer; receiver support varies. [Technical design](XTC_METADATA.md).

Missing fields aren't invented. Readers may ignore optional metadata, especially vendor-specific JSON.

## Safety and compatibility

- The **export result is a different file**; your original manga downloads are not modified.
- A successful **conversion** does not mean the reader can open that format.
- A successful **upload** means the receiver accepted the file, not necessarily that its parser supports it.
- For large output, save locally and copy to SD card, or select fewer chapters for Wi-Fi transfer. One exported book is not automatically divided into separate files for upload.
- Always test a small sample before exporting a complete series.

Format tests and the Android build run in GitHub Actions, but compatibility with individual physical readers and input files still needs verification. If a book renders incorrectly, include the export format, reading direction, device resolution, build commit and a small legally shareable reproducer in your feedback.

## Web/PDF exports use separate converters

The **Web link → EPUB** tool can produce reflowable **selectable article text**, including **multiple public web links into one EPUB**, while **Web novels** can discover publicly visible chapters. **PDF → EPUB** attempts to keep genuine text mixed with illustrations/scans without AI/OCR. They are separate from this **manga image-to-format conversion** tool. See [Web link](WEB_TO_READER.md), [multi-link and novels](WEB_NOVELS.md), and [PDF conversion](PDF_TO_EPUB.md).
