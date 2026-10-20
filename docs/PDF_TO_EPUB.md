# PDF → EPUB with real text and illustrations (experimental)

This feature targets the **mixed-content novel** use case: extract **real, selectable/resizable text** from a PDF, while preserving diagrams, floor plans and pictures near their original paragraphs where possible. Think of a mystery novel with text above and below floor-plan drawings, rather than turning the entire book into page screenshots.

**Status:** Experimental stacked draft; NOT in MangaNooir's released/main APK. The implementation requires a successful full Android build, representative book tests and physical XTEink EPUB verification before release.

## Steps

1. Open **MangaNooir → More → PDF to EPUB** (only in the experimental PDF branch/build).
2. Choose a PDF from your phone's file picker.
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
- This is a separate feature from **PDF → XTC/XTCH/CBZ (entire pages rendered as images)**, which can reuse MangaNooir's existing manga renderer later.
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


## Experimental 1.0.12 — real-novel layout fixes

A locally supplied, **313-page Malay novel PDF** was inspected privately as a fidelity test. The book itself was **not** committed to the repository. Source document characteristics:

- **7 full-image pages** (illustrated front matter and closing artwork); these need full-page image preservation.
- **25 mixed text+image pages**, including illustrated chapter/prologue headers and oversized decorative first letters.
- **281 text-only pages**, which should remain selectable/reflowable in EPUB.
- Source text includes centered printed page numbers and justified Malay paragraphs with line hyphenation.

The previous fixed 22-point line-gap threshold could split normal 28-point-spaced visual lines into dozens of false EPUB paragraphs. PDFBox may also yield oversized initial letters out of their natural order, making first words appear truncated. The v1.0.12 layout helper addresses these cases without OCR:

- Infer paragraph spacing from each page's actual text-line geometry rather than a hardcoded 22 pt.
- Reattach large initial drop caps to an overlapping opening line; preserve an opening quotation mark where present.
- Remove only probable **centered page-number footers**, retaining ordinary numbers within the text.
- Preserve true hyphens in Malay reduplications rather than deleting every end-of-line hyphen.
- Preserve extracted decorative image blocks with nearby real text; image-only pages continue through page-render fallback.
- Run pure-JVM synthetic geometry regression tests in CI without publishing copyrighted book pages or content.

**Still to test on Android hardware:** output EPUB body order, 313-page runtime and memory use, decorative heading image placement, rendered text and original artwork quality, and physical X4 reader compatibility. Current generated TOC still lists PDF pages, not semantic chapter titles; automatic chapter detection and editor preview remain future work.

