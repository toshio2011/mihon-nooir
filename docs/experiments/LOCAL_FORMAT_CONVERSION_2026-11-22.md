# Nooir Tools: local file conversion capability and EPUB → XTC/XTCH design

> **Experimental design and honest-input guard only. No new EPUB renderer has shipped.**
> The current public MihonNooir v1.0.19 is unchanged. Do not merge or release without review.
> Folio Nooir firmware, Windows desktop, and Web→EPUB draft PR #110 are separate tracks.

## User-facing contract

**Send originals**: copies supported local files byte for byte to a compatible XTEink
HTTP file-transfer endpoint. This does not transcode them. Reader-side support
still depends on the firmware.

**Convert supported files**: currently takes standalone local JPEG/PNG/WebP/BMP/GIF
images or verified image-containing CBZ/ZIP archives, then exports through the
existing manga conversion engine to a selected reader format.
A chosen EPUB, PDF, XTC/XTCH, DOCX or unrelated document is **not** converted
by this path. Previously it could silently copy the source despite a selected
XTC/XTCH output; that is misleading and is now rejected on the experimental
branch before creating a destination file or uploading bytes. The file picker
disables conversion if any chosen item appears unsupported. The worker remains
the authoritative gate, including distinguishing comic ZIP from a random ZIP.

PDF uses the existing dedicated **Nooir Tools → PDF to e-reader** pipeline for
EPUB2/EPUB3 or XTC/XTCH output. Web source links use Web→EPUB, not this screen.
No conversion path should change file extensions without validating real bytes.

## Proposed input/output matrix

| Input | Existing tested path | Future native conversion |
| --- | --- | --- |
| CBZ / supported image | XTC, XTCH, EPUB2/3, CBZ through manga exporter | Better text-heavy page handling |
| PDF | Dedicated PDF→EPUB2/3/XTC/XTCH | Optional CBZ page export |
| EPUB text / mixed illustrations | Send unchanged; read on supported firmware | Reflowable EPUB→XTC/XTCH page rendering and EPUB→PDF |
| EPUB fixed-layout illustrated book | Send unchanged | Fixed-layout EPUB→XTC/XTCH and CBZ with panel-safe scaling |
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

The first experimental milestone should be **EPUB import/inspection +
fixed-layout/image-only EPUB → XTC/XTCH**, where source fidelity can be
checked against existing image conversion behavior. Text reflow is the more
substantial follow-up because CSS/layout and font rendering need proper
pagination. Do not pretend an image-only prototype supports text novels.

## Acceptance for this first guard PR

- EPUB and PDF are never presented as supported in the local manga converter.
- Both **Convert & send** and **Convert & save** refuse unsupported input
  before creating a file or transfer. **Send originals** remains available.
- Mixed batches do not produce deceptively named original documents.
- ZIP input receives a second real-archive/bitmap check in the worker.
- Pure Kotlin format classification regression passes, plus complete Android
  compilation and device UI verification on a debug-signed test build.

No public stable release or Folio Nooir firmware change is authorized here.
