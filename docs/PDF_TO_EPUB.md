# PDF → EPUB with real text and illustrations (experimental)

> **Current release (29 October 2026):** [MihonNooir v1.0.16](https://github.com/toshio2011/mihon-nooir/releases/tag/v1.0.16) is the official Android release with persistent-key-signed APKs. References to v1.0.11/v1.0.12 below are historical; the Windows app is still in development.

This feature targets the **mixed-content novel** use case: extract **real, selectable/resizable text** from a PDF, while preserving diagrams, floor plans and pictures near their original paragraphs where possible. Think of a mystery novel with text above and below floor-plan drawings, rather than turning the entire book into page screenshots.

> **Historical preview note:** These workflows began in earlier v1.0.11/v1.0.12 builds. Use official v1.0.16 for the current Android release; physical compatibility still varies.

## Steps

1. Open **MihonNooir → More → Nooir Tools → PDF → EPUB · Text + illustrations** (experimental v1.0.11).
2. Choose a PDF from your phone's file picker, then optionally edit the resulting **book title, author and EPUB filename**.
3. Select **Reflowable text + illustrations** (the intended default) or **Keep original pages as images**.
4. Tap **Convert PDF & save EPUB to phone**, select a destination, then check the progress notification.
5. Or turn on **Folio Nooir → File Transfer** on your XTEink, enter its IP/destination and choose **Convert PDF & send EPUB to XTEink**.
6. Read the exported EPUB. Text from a PDF text layer can resize; embedded illustrations are interleaved by approximate vertical positions.

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
- Some embedded image formats, unsupported fonts or PDF encryption may fail. The initial converter rejects encrypted PDF files and uses a conservative **100 MiB/600-page** guard.
- Layout inspection/preview, manual paragraph/image-position adjustments, headings/TOC detection, and reliably processing forms/math/tables are **future enhancements**.
- This is a separate feature from **PDF → XTC/XTCH/CBZ (entire pages rendered as images)**, which can reuse MihonNooir's existing manga renderer later.
- Original PDF stays untouched; the converted EPUB is saved as a new file.

## Test before release

- [ ] Text-only PDF: selectable/reflowable paragraphs in exported EPUB.
- [ ] Novel PDF with actual text and a bitmap illustration between paragraphs: verify illustration order.
- [ ] PDF with no text, scanned pages: image fallback with no missing pages.
- [ ] Vector floor-plan PDF: preserve drawing in page-image fallback.
- [ ] Mixed text/scans: one EPUB with both types.
- [ ] Sideways/rotated pages, tables, multi-column documents: warn or preserve content.
- [ ] Physical X4 EPUB display, page images, navigation, and transfer.
- [ ] Memory use, file cleanup, cancel during large document conversion.

For Android-source details, see the experimental [PDF conversion PR](https://github.com/toshio2011/mihon-nooir/pulls). Track follow-up layout improvements at the [roadmap](https://github.com/toshio2011/mihon-nooir/issues/8).

## EPUB version selector (draft PR #22)

The PDF converter now offers **EPUB 2 · older readers** and **EPUB 3 · newer readers**. Both can contain reflowable selectable text, inline images and tables; the **Reflowable text + illustrations** versus **Keep original pages as images** setting is independent of the EPUB version. EPUB 2 writes an NCX table of contents; EPUB 3 writes a navigation XHTML. The choice is forwarded from the dialog through the background worker to the writer. This is **draft code** pending Android CI and physical XTEink testing, not a released feature.

A scanned PDF remains image-based in either EPUB version (no OCR). Complex vector drawings and floor plans may be rendered as complete pages rather than partially extracted. Review resulting page order and illustration placement before sharing.


## Draft PR #22: PDF → XTC / XTCH (experimental)

The PDF converter now also offers **XTC (1-bit)** and **XTCH (2-bit)** for supported XTEink profiles. Select the output format and device before saving or sending. This path uses Android `PdfRenderer` and converts one PDF page at a time to the native panel dimensions; it does **not** use OCR or an AI service. Scanned pages, embedded images and visible text are all rendered as pixels.

**Important distinction:** EPUB 2/3 still use the existing text-and-illustration extraction path and preserve selectable/reflowable text where possible. Choosing XTC/XTCH deliberately rasterizes text; the output cannot be reflowed or selected. Large, password-protected and unusual PDFs may fail. The XTC/XTCH path has a 600-page limit and has not yet been physically verified on X3/X4.

The XTC/XTCH first PDF page is treated as page zero/cover; a PDF without a dedicated cover will display its first content page instead.
