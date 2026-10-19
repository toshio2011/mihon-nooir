# Formats, image conversion and metadata

[← Back to README](../README.md) · [All guides](README.md) · [Choose chapters](CHAPTER_SELECTION.md) · [Getting started](GETTING_STARTED.md) · [Wi-Fi transfer](WIFI_TRANSFER.md)

MangaNooir exports manga into e-reader-oriented formats while keeping the source chapters and library information in Mihon.

## Pick an output

| Format | Image/color approach | Navigation and metadata | Typical use |
|---|---|---|---|
| **XTC** | 1-bit black/white | Native title, author, chapter index and other fixed fields | Smaller, quick monochrome output for compatible XTEink firmware |
| **XTCH** | 2-bit / four-level grayscale | Same fixed-format family | Preserve a few shades for compatible XTEink readers |
| **EPUB** | Fixed-layout EPUB 3 containing page images | OPF creator/title/description/subjects, cover and chapter navigation | Compatible fixed-layout EPUB readers |
| **CBZ** | Ordered image archive | `ComicInfo.xml` plus optional JSON metadata | Comic-capable readers and conversion workflows |
| **Auto** | Preset chosen from device selection | Varies with chosen output | Convenient start; not always the best choice for every comic |

The main branch and the experimental preview both support **X4** (480 × 800) and **X3** (528 × 792) presets. These targets are screen-size presets, **not universal guarantees** for every XTEink production panel or firmware.

## Convert from downloaded manga

1. Open manga details in MangaNooir.
2. **Long-press** a chapter, then tap other chapters to select them. In **Normal Export**, selected chapters must be downloaded. If nothing is selected, all downloaded chapters of the manga are included. [Step-by-step chapter-selection guide →](CHAPTER_SELECTION.md)
3. Tap **Export** next to Download.
4. Choose device, format, and **Save file** or **Send to XTEink**.

The original Mihon manga files and metadata remain intact. Re-exporting into a different format is possible without re-downloading content you already have.

## Quick Export — experimental 1.0.4 only

Enable **Quick Export** after selecting chapters that are not permanently downloaded in Mihon. The installed online source is asked to fetch those chapter pages; the converter works **one chapter at a time** and cleans up temporary page images.

There is **no fixed five-chapter ceiling**, but large series still use time and storage. Individual remote images and temporary chapter data have guard limits; sending a single output to the XTEink has an experimental **180 MiB** cap. Source websites can rate-limit, require sign-in or fail, and the preview has not been hardware-tested on every source.

## Import files already on the phone — experimental 1.0.4 only

**More → Send & Convert files** uses Android's file picker. It recognizes:

- Comic archives: CBZ and ZIP files containing supported page images.
- Standalone images: JPG/JPEG, PNG, WebP, BMP and GIF.
- Other documents (EPUB, PDF, TXT, FB2 and more) for **unchanged transfer**, depending on the destination.

For recognized manga inputs, choose **Convert manga & send** or **Convert & save**. A ZIP holding arbitrary documents is **not** automatically a manga archive. Sending a PDF or EPUB unchanged does not guarantee the XTEink can read it.

The experimental multi-file queue supports up to **25 chosen files** per batch and runs conversion/sending sequentially.

## Conversion adjustments — experimental 1.0.4 only

The first real set of MangaPoint-inspired conversion controls is implemented in **More conversion settings**:

| Control | What it changes |
|---|---|
| Manga / Manhwa / Manhua preset | Sets sensible initial layout, spread/direction and image options |
| Auto / Pages / Webtoon layout | Chooses page handling and long-strip segmentation |
| Trim white margins | Conservatively crops large white borders |
| Split double-page spreads | Separates wider manga scans into reader-sized pages |
| Reading direction | Determines right-to-left or left-to-right split order and metadata |
| Contrast | Changes grayscale contrast (50 is neutral) |
| Dithering | Switches Floyd–Steinberg processing versus direct grayscale-level quantization |

These are **not full MangaPoint parity**. Manual margin coordinates/sensitivity, live before-after preview, zoom/tiling, orientation controls, guide arrows, sophisticated long-strip overlap controls, and more are still on the [roadmap](https://github.com/toshio2011/mihon-nooir/issues/8).

## What happens to metadata?

MangaNooir uses only the metadata available from Mihon or the imported comic file:

- **EPUB:** title, author, description, cover, language, publisher, identifiers, genres/subjects and navigation through the standard OPF/navigation files; optional extra JSON for Nooir-specific information.
- **CBZ:** image sequence, `ComicInfo.xml` (series/title/writer/artist/summary/genre and related fields when available), and optional supplementary metadata JSON.
- **XTC/XTCH:** native fixed-format fields such as title, author, language/publisher where supported, cover-page index and chapter navigation. They do **not** have standardized fields for a full synopsis or every genre. **Experimental 1.0.5** adds an optional bounded JSON trailer and a post-upload metadata sync; reading the trailer on Folio Nooir requires the separate experimental [firmware PR](https://github.com/toshio2011/folio-nooir/pull/1). [Technical design →](https://github.com/toshio2011/mihon-nooir/blob/feature/xtc-cover-synopsis/docs/XTC_METADATA.md)

Missing fields aren't invented. Readers may ignore optional metadata, especially vendor-specific JSON.

## Safety and compatibility

- The **export result is a different file**; your original manga downloads are not modified.
- A successful **conversion** does not mean the reader can open that format.
- A successful **upload** means the receiver accepted the file, not necessarily that its parser supports it.
- For oversized output, prefer fewer chapters or wait for automatic splitting, which is **not implemented** yet.
- Always test a small sample before exporting a complete series.

Format tests and the Android build run in GitHub Actions, but physical XTEink/Android compatibility still needs device testing. If a book renders incorrectly, include the export format, reading direction, device resolution, build commit and a small legally shareable reproducer in your feedback.
