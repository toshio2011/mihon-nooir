# Reader interoperability debugging — readable test books (21 October 2026)

## What went wrong with the old CI samples?

The earlier EPUB 2 fixture drew a gradient cover and used white image pages. The old XTC and XTCH fixtures were synthetic bit patterns and blank white pages. They were useful for validating binary structure, NOT for testing readability or real-device memory. A green Android build or EPUBCheck result never established that a user could read those samples on X3/X4.

The revised CI fixture bundle is `manganooir-readable-reader-interop-fixtures`. It is generated from **the real MangaNooir EPUB 2, EPUB 3, XTC and XTCH writers**, using three original, visibly illustrated pages: a cover, a short story page and a four-gray-level demonstration page. No real copyrighted manga, AI/OCR or third-party fonts are included. The test decodes EPUB image payloads, reads native page-table offsets and unpacks XTG/XTH pixel data independently, comparing sampled pixels to the original pages.

| File | Purpose |
| --- | --- |
| `MangaNooir_Readable_EPUB2_X4.epub` | Genuine EPUB 2.0.1 fixed-image manga with NCX, cover and real illustrated pages |
| `MangaNooir_Readable_EPUB3_X4.epub` | EPUB 3 fixed-image manga with XHTML nav, cover and the same pages |
| `MangaNooir_Readable_XTC_X4_480x800.xtc` | Full-size X4, 1-bit B/W (48,000 bitmap bytes/page) |
| `MangaNooir_Readable_XTCH_X4_480x800.xtch` | Full-size X4, four-level grayscale (96,000 bitmap bytes/page) |
| `MangaNooir_LowMemDiagnostic_XTC_240x400.xtc` | Low-memory diagnostic, 1-bit (12,000 bitmap bytes/page) |
| `MangaNooir_LowMemDiagnostic_XTCH_240x400.xtch` | Low-memory diagnostic, 2-bit (24,000 bitmap bytes/page) |

These are **diagnostic samples**, not benchmarks or proof of compatibility. The 240×400 file is intentionally smaller and is not scaled by Folio Nooir's native XTC renderer: it may occupy only the upper-left quarter of an X4 screen. Do not use it as a default manga profile.

## Why memory errors occur

The Folio Nooir `XtcReaderActivity::renderPage` implementation has a full-buffer path that calls `malloc(bitmapBytes)`. A 480×800 XTC page requires 48,000 bytes of **contiguous available heap** and a 480×800 XTCH page requires 96,000 bytes. The reader shows *Memory Error* if allocation fails. Available contiguous memory depends on active reader mode, prior allocations, firmware version and other features. File byte size on the SD card or page count does not reduce the per-page bitmap allocation.

Current Folio Nooir has a streaming XTCH rendering path in normal (non-dark) mode; other reader versions and modes may still allocate the full 96 KB. Its thumbnail generation also allocates buffers and can fail separately. MangaNooir cannot solve a failing firmware `malloc` merely by adjusting metadata, recompressing a page, or changing the exported extension; XTC/XTCH page data are normally uncompressed.

### Test steps

1. Back up the reader's SD card and record firmware version/model; do not flash new firmware to run this test.
2. Open the EPUB 2 and EPUB 3 sample, verifying legible words and three distinct images. Refresh the book cache if the thumbnail was previously cached as blank.
3. With the reader in ordinary/light mode, open the full XTC, then full XTCH sample. Note whether the failure is **while listing/refreshing the cover** or **opening a page**.
4. Repeat using the matching 240×400 low-memory diagnostic file. A reduced-file success alongside full-size failure strongly suggests a peak buffer/heap limitation, **not proven malformed XTC metadata**.
5. Repeat once with dark mode off (if applicable), and record whether each format worked. Dark-mode XTCH may use a different memory path.
6. If **even the 240×400 files fail**, report the exact on-screen message, device, firmware version, reader mode and whether real XTC/XTCH files from another converter work on the same firmware.
7. If another converter's 480×800 XTC/XTCH file works while the matching MangaNooir one fails, supply **only a copyright-safe diagnostic sample** so headers, metadata, chapter tables and page bytes can be compared directly.

Do not assume an X4 Pro, stock XTEink firmware, CrossInk, CrossPoint or Matcha Reader behaves identically. Do not claim physical-device acceptance based on JVM tests or an emulator alone.

## What the converters actually do

- **MangaPoint** works from manga/image pages: it plans orientation/crops/splits, scales to device dimensions, quantizes/dithers each grayscale page, then packs 1-bit XTG (XTC) or 2-bit XTH (XTCH), with index, metadata and optional chapters. The MangaNooir codec uses the same documented little-endian layout and disk spooling approach.
- **X4Converter** first renders **text EPUB** pages using a CREngine WebAssembly renderer, then makes bitmaps and encodes XTC/XTCH. It is *not* a reflowable EPUB writer; it rasterizes the rendered novel pages for the target display.
- **Matcha Reader Tools** uses a browser image/manga pipeline, writes EPUB 3 with image XHTML and cover/navigation, and has a separate native XTC/XTCH encoder that follows the firmware's binary contract. Its XTCH path similarly requires page height divisible by eight.
- **MangaNooir** keeps all original Mihon features, exports selected downloaded/online manga chapters or imported local images/CBZ as image-page EPUB 2/3, XTC/XTCH, or CBZ. Its *separate* PDF/web novel tools aim for **reflowable EPUB 3 containing real extractable text and pictures**, not EPUB 2 manga pages or an EPUB-to-XTC text renderer. Those are different pipelines; do not mistake the manga image EPUB for reflowable prose.

## Limits still requiring an actual phone and reader

Full-size X4 XTC/XTCH may still hit a firmware memory limit, even with perfect output. Need physical tests with the reader's exact current firmware, original book files, real chapter navigation, refresh/cache, grayscale orientation, and long-job performance. No Folio Nooir code has been modified. The user's experimental APK installability and signing-key continuity also need verification. A new MangaNooir public release remains blocked on explicit authorization.
