# Nooir Tools: local file conversion capability and EPUB → XTC/XTCH design

> **Experimental first-pass native EPUB renderer is now on this draft branch, not in any public release.**
> The current public MihonNooir v1.0.19 is unchanged. Do not merge or release without review.
> Folio Nooir firmware, Windows desktop, and Web→EPUB draft PR #110 are separate tracks.

## User-facing contract

**Send originals**: copies supported local files byte for byte to a compatible XTEink
HTTP file-transfer endpoint. This does not transcode them. Reader-side support
still depends on the firmware.

**Convert supported files**: accepts local JPEG/PNG/WebP/BMP/GIF images or
verified CBZ/ZIP manga and uses the existing image conversion engine.
It now **experimentally** supports EPUB 2/3 text and illustrated books to real
XTC or XTCH, using offline spine parsing and Android font/image rasterization.
No EPUB-to-PDF, EPUB-to-CBZ or image-only EPUB re-export is implied.

An EPUB, PDF, XTC/XTCH or unrelated document must not be silently copied
when the user explicitly pressed Convert. Instead the input/output selector
limits choices and the worker validates again before creating or uploading
the output. A manga ZIP is additionally inspected for image contents.
Original-file sending remains explicitly separate.

PDF uses the existing dedicated **Nooir Tools → PDF to e-reader** pipeline for
EPUB2/EPUB3 or XTC/XTCH output. Web source links use Web→EPUB, not this screen.
No conversion path should change file extensions without validating real bytes.

## Proposed input/output matrix

| Input | Existing tested path | Future native conversion |
| --- | --- | --- |
| CBZ / supported image | XTC, XTCH, EPUB2/3, CBZ through manga exporter | Better text-heavy page handling |
| PDF | Dedicated PDF→EPUB2/3/XTC/XTCH | Optional CBZ page export |
| EPUB text / mixed illustrations | Send unchanged in public release; **experimental EPUB→XTC/XTCH** in draft | Rich CSS/floats/embedded-font fidelity, EPUB→PDF |
| EPUB fixed-layout illustrated book | Send unchanged in public release; **experimental PNG/JPEG art layout** in draft | SVG, specialized fixed-layout semantics, panel-aware CBZ |
| XTC / XTCH | Inspect or send unchanged | Decode to CBZ/EPUB fixed-image pages; no false text recovery |
| Other archives or documents | Send original only, if reader can handle it | Explicit adapters only after format validation |

“Any input to any output” is a **long-term product direction**, not a claim
that every conversion is lossless, legal, structurally meaningful or available.
Image formats lose editable/selectable text when rasterized. Do not advertise
XTC→reflowable EPUB as possible without original textual content or a separate,
explicit OCR step.

## EPUB → XTC/XTCH implementation gate

1. **Preflight EPUB without DRM bypass.** Verify ZIP central directory bounds,
   safe relative entry paths, mimetype/container/OPF, ordered spine, navigation,
   metadata, image references and supported text/image content. Reject
   encryption/DRM, malformed packages, decompression bombs, external scripts
   and unsupported media with useful errors. Support EPUB 2 and EPUB 3.
2. **Distinguish reflow and fixed layout.** For text-heavy novels use a
   controlled page renderer with a chosen device viewport, font, line height,
   margins, language, RTL direction, and Unicode fonts. For image/comic EPUBs
   keep source image order, aspect ratio, panel reading orientation, and large
   page split policies. Don't route text EPUBs through CBZ image heuristics.
3. **Render accurately before encoding.** Build one stable, page-sized grayscale
   bitmap per reading viewport; keep actual inline illustrations, headings,
   chapter boundaries and optional real cover. Avoid duplicate pages, image
   clipping, blank pages and glyph substitution. Allow dark/light output and
   comparison previews before export.
4. **Encode real XTC / XTCH bytes** with the existing shared XTC codec, using
   XTEink profile resolutions (X4: 480×800, X3: 528×792), 1-bit XTC or 2-bit
   XTCH dithering, correct 1-based chapter offsets and a bounded output spool.
   Clear limits on total pages, storage and RAM, with cancellation and
   atomic-on-success save/upload. Never emit a partial book as success.
5. **Test both types of real books:** paragraph-only multilingual text,
   CSS-heavy text, illustrated prose with floated/full-page figures, RTL,
   image-only fixed layout, long books, damaged EPUB files and invalid ZIPs.
   Compare preview pixels to decoded XTC/XTCH bytes; check page/TOC/cover
   counts and physical X3/X4 readability.

**Implementation checkpoint:** `NooirEpubBook` now bounds and reads local
EPUB ZIP/container/OPF, ordered spine, title/author, raster cover and assets.
`NooirEpubToXtc` renders text paragraphs and embedded PNG/JPEG/WebP/GIF/BMP
images into e-reader canvases with font/margin/RTL settings. The shared
`XtcCodec` writes real XTC or XTCH; the WorkManager transfer worker saves or
uploads the resulting bytes. This is a *first-pass text-and-image renderer*,
not a browser/CSS engine. Complicated typography, writing modes, tables,
SVG/MathML, embedded fonts, fixed-layout SVG, art cropping and CSS floats may
fail or appear different. Preview and physical X3/X4 acceptance remain undone.

## Acceptance for this experimental EPUB import PR

- EPUB is offered **only** for XTC/XTCH via the new EPUB renderer;
  PDF still redirects users to the existing dedicated converter.
- Both **Convert & send** and **Convert & save** refuse unsupported input
  before creating a file or transfer. **Send originals** remains available.
- Mixed batches do not produce deceptively named original documents.
- ZIP input receives a second real-archive/bitmap check in the worker.
- Synthetic Kotlin package/URL safety tests pass, and Android instrumentation
  generates genuine EPUB→XTC and EPUB→XTCH with independently decoded output.
  Full updated Android compile/device CI is pending; real book CSS/image
  fidelity, screenshots of this particular screen and physical XTEink testing
  are not yet accepted.

No public stable release or Folio Nooir firmware change is authorized here.
