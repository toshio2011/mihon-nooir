# Real-book compatibility baseline (21 October 2026)

## Sources inspected locally (NOT included in repository)

- User-supplied **Strange Houses** EPUB 2: approximately 10 MB; 20 XHTML files and 65 image assets, with dialogue tables and floor-plan illustrations. This is a **mixed text + image** source, not a pure image comic. Preserve XHTML reading order and image references for EPUB conversions; do not flatten selectable text unless the requested output is a bitmap format.
- User-supplied **Strange Houses** XTCH: 15,751,792 bytes, 164 pages, 480×800, one chapter. This file reportedly opens on the target device.
- Previously generated original MangaNooir readable XTCH fixture: 288,522 bytes, three pages, 480×800, one chapter. Its device behavior remains unconfirmed.

## Binary observations

Both XTCH files have:
- `XTCH` magic; version 1; 312-byte global header; one 96-byte chapter record.
- 16-byte page index entries; data offset `312 + 96 * chapters + 16 * pages`.
- `XTH\\0` page records with 22-byte per-page header and 96,000 bytes of bitmap data.
- Page width 480 and height 800, with no extra per-page trailer.

The user-provided working XTCH first page has 256 distinct byte values; the original MangaNooir sample first page has 134. This is expected to vary with image content and **is not evidence of an encoder bug**. Both contain nonzero image bytes. Header comparison alone cannot establish that the grayscale planes render correctly or explain the reported device `Memory Error`.

## Required regression gates

1. EPUB 2 and EPUB 3 fixed-image manga: each page's embedded image decodes and is nonblank; OPF/spine/navigation resolve; EPUBCheck passes.
2. EPUB 2 and EPUB 3 mixed/reflowable: selectable XHTML paragraphs, interleaved images, tables and valid image URLs; EPUBCheck passes. A PDF with text and diagrams should not silently become placeholder pages.
3. XTC and XTCH: decode each exported page **independently** using the reader's bit/plane order, compare pixel levels to source, and validate all offsets and bounds.
4. Scanned PDF: preserve every page as a rendered image without requiring OCR. Text PDF: preserve native extractable text where the page is simple; fall back to a rendered image for complex vector/floor-plan pages.
5. Hardware: test both full-size and low-memory diagnostic samples on the same Folio Nooir firmware, recording error stage, free heap if available, and whether the reference XTCH opens. CI cannot replace this test.

**Copyright / scope:** do not commit the supplied book files or their illustrations; do not modify Folio Nooir; do not claim reader compatibility based only on host tests.
