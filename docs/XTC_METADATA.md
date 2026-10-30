# XTC and XTCH — format metadata, covers, and synopsis

> **Current release (29 October 2026):** [MihonNooir v1.0.16](https://github.com/toshio2011/mihon-nooir/releases/tag/v1.0.16) is the official Android release with persistent-key-signed APKs. References to v1.0.11/v1.0.12 below are historical; the Windows app is still in development.

> **v1.0.12 merged source, not yet released** · [Export comparison](EXPORT_FORMATS_EPUB2.md) · [Merged PR #21](https://github.com/toshio2011/mihon-nooir/pull/21)

## Native data Folio Nooir actually reads

The XTC/XTCH header contains title/author metadata and optional chapters. Folio Nooir reads a **128-byte UTF-8 title** and **64-byte UTF-8 author** from the 256-byte metadata block. Chapter table records are **96 bytes**, with start/end values **1-based**. A 16-byte page-table entry points to each uncompressed page.

- **XTC 1-bit**: magic `XTC\\0`, page magic `XTG\\0`, MSB-first row-major bitmap; at 480×800 the page is **48,022 bytes** including the 22-byte image header.
- **XTCH 2-bit**: magic `XTCH`, page magic `XTH\\0`, two right-to-left column-major vertical-bit planes; at 480×800 the page is **96,022 bytes**.
- Both use binary format v1.0 and a 56-byte extended header. The reader expects valid absolute page offsets, image dimensions, and matching chapter indexes.
- Folio Nooir uses **page 0** as its bookshelf thumbnail source; it does not require an embedded thumbnail payload.

## Choose whether to insert a dedicated cover

**More conversion settings → Include separate series cover** defaults to on. MihonNooir takes genuine Mihon series artwork and adds it before the selected manga pages. The first selected chapter then starts at page index 1 (on-disk start number 2). No reading pages are discarded. When the option is off, the first selected manga page is page 0. If the cover was requested but no source artwork can be obtained, XTC/XTCH report a clear error rather than inventing artwork.

MihonNooir XTCH output has been reported to display correctly on a repaired Folio Nooir X4; other firmware implementations need independent testing. Refresh thumbnails using the reader's **Refresh Book Cache** feature when appropriate.

## Synopsis is not standard XTC/XTCH metadata

Folio Nooir's binary parser does not consume a standard synopsis field from XTC/XTCH. The earlier `NOOIRMD1` JSON trailer was private to MihonNooir and is **removed from normal v1.0.12 XTC/XTCH export** to keep books portable. The app still retains the source synopsis internally during conversion.

For compatible Folio Nooir versions, **direct Wi-Fi transfer** can independently call the reader's `POST /api/book` endpoint to store title, author and synopsis as a reader-side metadata override. That is **not embedded synopsis support**, and SD/USB copies cannot depend on it.

A future optional, versioned `book.nooir.json` sidecar could associate synopsis with a filename/identifier, but **this is only a proposal**. The reader would need to implement sidecar lookup and validation. This project does **not** change Folio Nooir firmware.

For cover and synopsis that work through standard EPUB metadata, choose **EPUB 2 Classic** or **EPUB 3 Modern**.

## Re-exporting older books

Previously exported XTC/XTCH files that open correctly can keep working. To obtain a strictly portable file **without the older private JSON footer**, re-export it with the v1.0.12 implementation once tested. Files transferred to other readers need the receiving firmware to understand XTC/XTCH regardless of their extension.
