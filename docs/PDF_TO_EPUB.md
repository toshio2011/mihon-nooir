# PDF to e-reader: EPUB 2/3, XTC and XTCH

> **Current Android release:** [MihonNooir v1.0.19](https://github.com/toshio2011/mihon-nooir/releases/tag/v1.0.19) (19 November 2026). This guide describes shipped features; see [release notes](releases/v1.0.19.md). Individual inputs, sources and reader firmware may behave differently. Windows is not publicly released.

This feature targets the **mixed-content novel** use case: extract **real, selectable/resizable text** from a PDF, while preserving diagrams, floor plans and pictures near their original paragraphs where possible. Think of a mystery novel with text above and below floor-plan drawings, rather than turning the entire book into page screenshots.



## Steps

1. Open **MihonNooir → More → Nooir Tools → PDF → EPUB · Text + illustrations**.
2. Tap **Choose PDF file(s) from phone** and select one or more documents.
3. Choose **EPUB 2**, **EPUB 3**, **XTC · 1-bit**, or **XTCH · 2-bit**. For EPUB, select **Reflowable text + illustrations** or **Keep original pages as images**; for XTC/XTCH choose the target reader.
4. For one PDF, choose optional title, author and output filename. For a batch, each PDF becomes a separate book with a distinct name; select a destination folder for local saving.
5. Save to your phone, or send to a compatible XTEink via File Transfer if supported. Check **Nooir Tools → Activity Queue** for progress and errors. For large books, local saving followed by SD-card copy is recommended.
6. Inspect the output before relying on it. EPUB can preserve real selectable text; XTC/XTCH render original pages to the reader's native dimensions as pixels.

The manga Panel by Panel and oversized-split settings do not apply to the separate PDF converter. v1.0.19 passed the seven Android long-PDF regression tests, including large original-image and reflowable books; this is not a guarantee that every PDF can be rendered.

## Large files and batches

The old fixed 600-page, 100 MiB source, and 300 MiB output cutoffs were removed in v1.0.18 and remain removed in v1.0.19. [Long PDF acceptance](PDF_ANDROID_DEVICE_ACCEPTANCE_2026-11-12.md) and [release verification](releases/v1.0.19.md) describe tests beyond 1,000 and 2,000 pages. Multiple inputs are processed sequentially, not combined into one book. Local saving is still bounded by storage, real output-format limits and decoding/rendering resources; enormous or unusual PDFs can fail. Wi-Fi transfer has a separate **180 MiB per-file** application safety limit. The original PDF is not changed.

## What happens on different source pages?

| Page content | Reflowable mode result |
|---|---|
| Real novel paragraphs | Real XHTML paragraphs you can select or resize |
| Text mixed with embedded bitmap diagrams | Text and embedded diagrams, sorted by approximate page position |
| Whole-page scan with no text layer | Full-page image inserted inside the same EPUB, no fake OCR text |
| A page with complex vector floor plans | Fallback to full-page rendering to avoid silently losing diagrams; that page won't be reflowable |
| Scanned page with an existing OCR text layer | The parser inspects the layer; if content and figures cannot safely be reconstructed, page-image fallback may still be preferred |
| Two-column/table/rotated-heavy page | Best-effort text reading order; image fallback for complex graphics; visual verification strongly recommended |

You can have a **single EPUB** containing genuinely reflowable text chapters **and image-only pages**, without any AI API or OCR engine.

## Limitations worth knowing

- **PDF is a page/presentation format, not a semantic novel format.** Headings, paragraphs, exact figure anchors and multi-column reading order are reconstructed heuristically and will not always be perfect.
- Native text is extracted locally using **PDFBox-Android** (Apache-2.0), and image-only/complex PDF pages are rendered locally using Android's **PdfRenderer**; no server subscription or OCR is required.
- Some diagrams are vector graphics rather than embedded pictures. In difficult cases, the converter protects the content by retaining that complete page as an image, rather than discarding the diagram.
- **Scanned-only PDFs remain image-based where no text layer exists.** No native OCR or AI service is included.
- Some embedded image formats, unsupported fonts or encryption may fail. Password-protected PDFs are unsupported; low storage and genuine format/processing limits may prevent completion.
- Layout inspection/preview, manual paragraph/image-position adjustments, headings/TOC detection, and reliably processing forms/math/tables are **future enhancements**.
- PDF → XTC/XTCH is now available. It uses page rendering rather than selectable text; direct PDF-to-CBZ is not documented as a released option.
- Original PDF stays untouched; the converted EPUB is saved as a new file.

## Suggested checks for your own PDFs

- [ ] Text-only PDF: selectable/reflowable paragraphs in exported EPUB.
- [ ] Novel PDF with actual text and a bitmap illustration between paragraphs: verify illustration order.
- [ ] PDF with no text, scanned pages: image fallback with no missing pages.
- [ ] Vector floor-plan PDF: preserve drawing in page-image fallback.
- [ ] Mixed text/scans: one EPUB with both types.
- [ ] Sideways/rotated pages, tables, multi-column documents: warn or preserve content.
- [ ] Physical X4 EPUB display, page images, navigation, and transfer.
- [ ] Memory use, file cleanup, cancel during large document conversion.

For Android implementation history, see [PR #82](https://github.com/toshio2011/mihon-nooir/pull/82) and the [current release notes](releases/v1.0.19.md). Track follow-up layout improvements at the [roadmap](https://github.com/toshio2011/mihon-nooir/issues/8).

## EPUB version selector

The PDF converter now offers **EPUB 2 · older readers** and **EPUB 3 · newer readers**. Both can contain reflowable selectable text, inline images and tables; the **Reflowable text + illustrations** versus **Keep original pages as images** setting is independent of the EPUB version. EPUB 2 writes an NCX table of contents; EPUB 3 writes a navigation XHTML. The choice is forwarded from the dialog through the background worker to the writer. Both EPUB versions are released; physical reader compatibility still varies.

A scanned PDF remains image-based in either EPUB version (no OCR). Complex vector drawings and floor plans may be rendered as complete pages rather than partially extracted. Review resulting page order and illustration placement before sharing.


## PDF → XTC / XTCH

The PDF converter now also offers **XTC (1-bit)** and **XTCH (2-bit)** for supported XTEink profiles. Select the output format and device before saving or sending. This path uses Android `PdfRenderer` and converts one PDF page at a time to the native panel dimensions; it does **not** use OCR or an AI service. Scanned pages, embedded images and visible text are all rendered as pixels.

**Important distinction:** EPUB 2/3 still use the existing text-and-illustration extraction path and preserve selectable/reflowable text where possible. Choosing XTC/XTCH deliberately rasterizes text; the output cannot be reflowed or selected. Large, password-protected and unusual PDFs may fail. The previous 600-page cutoff is removed; real binary format limits still apply. Physical reader acceptance depends on the device and firmware.

The XTC/XTCH first PDF page is treated as page zero/cover; a PDF without a dedicated cover will display its first content page instead.
