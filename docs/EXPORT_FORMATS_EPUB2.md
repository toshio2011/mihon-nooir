# MangaNooir: EPUB 2 Classic, EPUB 3 Modern, XTC Fast, XTCH High Quality

> **Current status (21 October 2026):** v1.0.12 source is [merged into `main`](https://github.com/toshio2011/mihon-nooir/pull/21) with [green Android CI](https://github.com/toshio2011/mihon-nooir/actions/runs/39517669011). **v1.0.12 remains unreleased**; the latest published preview is [v1.0.11](https://github.com/toshio2011/mihon-nooir/releases/tag/v1.0.11). Physical compatibility is not guaranteed.

> **Development preview only — draft [PR #21](https://github.com/toshio2011/mihon-nooir/pull/21).** The publicly downloadable [v1.0.11](https://github.com/toshio2011/mihon-nooir/releases/tag/v1.0.11) predates EPUB 2. This v1.0.12 work has not been merged or released.

## Choose a format

| Format | Container | Cover | Synopsis | Chapters |
| --- | --- | --- | --- | --- |
| **EPUB 2 Classic** | Genuine EPUB 2.0.1, OPF 2.0 / NCX | Native `meta name="cover"` referencing image/png, XHTML cover page and `guide` | Native `dc:description` | OPF spine + NCX navPoints |
| **EPUB 3 Modern** | Existing EPUB 3 fixed-image pages, nav XHTML | Image marked `cover-image` when dedicated artwork exists | Native `dc:description` | OPF spine + EPUB 3 nav |
| **XTC Fast** | Version-1 `XTC\\0`, `XTG\\0` one-bit 1-plane row-major pages | Reader renders and caches **page 0** | No standard synopsis consumed by Folio Nooir | 96-byte chapter records, 1-based indexes |
| **XTCH High Quality** | Version-1 `XTCH`, `XTH\\0` 2-bit 2-plane column-major pages | Reader renders and caches **page 0** | No standard synopsis consumed by Folio Nooir | Same chapter table |

**CBZ** remains available as an image archive. Existing web link, web novel and mixed PDF **reflow** converters still generate EPUB 3. EPUB 2 is selectable for manga and local image/CBZ-to-manga conversion only.

## Reader selection and transfer

XTEink X3 (528×792), X4 and X4 Pro (480×800) have presets. Further presets cover selected Kindle, Kobo, Boox and PocketBook readers. Users may specify custom image dimensions (64–4096 px each edge, up to three million pixels). These are output-canvas profiles, **not firmware compatibility guarantees**. X3/X4 Auto keeps XTCH; other Auto choices depend on the reader (Kindle EPUB 3, Kobo/Boox/PocketBook CBZ). Unsupported formats are hidden/rejected for a selected profile.

Only a compatible XTEink HTTP File Transfer implementation can receive the app's direct **Send to XTEink** action. For Kindle/Kobo/Boox/etc. select **Save file**, then transfer using the device's own supported method. Kindle EPUB import generally uses **Send to Kindle**, not direct USB file copying.

## Series-cover option and metadata

**More conversion settings → Include separate series cover** is enabled by default. For manga from Mihon, the exporter tries actual series artwork (custom/cached/source). If found, it becomes the first book page, while original chapter pages remain in order. An XTC/XTCH first chapter then begins at zero-based page 1 (on-disk record index 2). Disable the setting to begin directly with the first selected reading page; this does not discard any chapter pages.

When artwork is unavailable, XTC/XTCH with cover enabled **stop with an explanatory error**. If no cover is inserted, EPUB 2 deliberately omits fake cover metadata rather than treating the first reading page as a series cover. Local CBZ/image import likewise does not invent a separate series cover.

EPUB 2 writes real title, identifier, language and, when available, creator and description. Missing authors/descriptions are omitted. The OPF is capped at 512 KiB for Folio Nooir's parser; export smaller chapter selections if exceeded. Folio Nooir's manual bookshelf cover refresh guards source image sizes (3 MiB in the inspected source), so very large source covers should be avoided.

## XTC/XTCH portability and synopsis

Both formats use the 56-byte extended container header, 16-byte page table, 22-byte page headers and uncompressed image planes. At 480×800: **48,022 bytes/page for XTC**; **96,022 bytes/page for XTCH**. Folio Nooir reads title/author but **no standard XTC/XTCH synopsis field**. The experimental exporter no longer emits the private `NOOIRMD1` JSON trailer. Direct Wi-Fi transfer may optionally sync a synopsis to Folio Nooir's existing **reader-side** `POST /api/book` API; SD/USB copies alone will not carry a readable synopsis.

A future optional, explicitly versioned `book.nooir.json` sidecar could carry synopsis linked to an exact book filename/identifier, but would require receiver support. **This branch does not create sidecars or change any firmware.**

## Test status and reconversion

The CI workflow builds synthetic, freely redistributable EPUB 2, XTC and XTCH books and tests EPUB XML/NCX/cover links, Folio-compatible binary layout, grayscale bit planes, chapter offsets, Unicode, malformed headers, and oversized limits. It also runs **EPUBCheck 5.4.0** on the produced EPUB 2 fixture. These host tests do not replace actual X3/X4 or third-party firmware tests.

Reconvert existing manga only to **obtain EPUB 2** or to strip a prior private XTC/XTCH JSON trailer for portability. EPUB 3 exports and otherwise working reader files need not automatically be regenerated. Existing MangaNooir preferences and web/PDF converters remain separate.

[Technical XTC synopsis and cover notes](XTC_METADATA.md) · [Orientation / overlap](ORIENTATION_OVERLAP.md) · [Draft PR #21](https://github.com/toshio2011/mihon-nooir/pull/21)
