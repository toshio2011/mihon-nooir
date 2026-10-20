# Offline illustrated-novel PDF → EPUB QA (experimental v1.0.11)

This guide describes a *private, user-supplied PDF test case*. **Do not commit the actual copyrighted PDF, extracted paragraphs, cover images, or generated EPUB to the public repository.**

## Source characteristics (local inspection, 20 October 2026)

- 313 pages, all 420×595 PDF points; 2.8 MB source.
- 281 pages with extractable text only, 25 with both extractable text and embedded artwork, 7 visual-only pages.
- The first six pages and final page must remain visually readable in the EPUB.
- Most narrative pages contain real text; no AI and no OCR are needed.
- Section-opening pages contain ornamental header illustrations and large drop capitals, sometimes preceded by an opening quotation mark.
- Body line spacing is approximately **28.1 PDF points**, but actual paragraph gaps are approximately **36.1 points**. A hardcoded 22-point paragraph threshold introduces a false paragraph break on nearly every text line.
- Regular printed numeric page labels are at the bottom. They should not become navigable EPUB prose.
- The PDF does not expose an explicit structural TOC; do not falsely claim to preserve a nonexistent chapter index.

## Required checks on the Android conversion

- [ ] Text paragraphs remain actual selectable XHTML, not page screenshots (except visual-only pages).
- [ ] Ordinary consecutive book lines join into paragraphs; larger original paragraph gaps remain separate.
- [ ] Decorative dropped initials rejoin correctly, including quoted ones and capitalized sound effects.
- [ ] Chapter/header illustrations and the last character illustration survive, without reordering.
- [ ] Cover illustration remains an EPUB cover asset, not just a randomly named first image.
- [ ] Printed footer page numbers are removed from reflowable XHTML (still visible on explicitly image-preserved pages).
- [ ] Text sections are grouped around decorated openings rather than generating 300+ entries named “Page N”.
- [ ] No repeated artwork, missing text or silently truncated pages.
- [ ] Verify EPUB ZIP mimetype (first, stored), XHTML/OPF/nav validity, cover link and image href integrity.
- [ ] Try font-size changes, line spacing, search and table-of-contents navigation in Folio Nooir.
- [ ] Try saving to Android first, followed by XTEink transfer with duplicate-name preflight.
- [ ] Real RedMagic/Android conversion and X4 reader behavior remain unverified until performed.

## Current implementation precautions

The extractor is **best-effort** and the source PDF may have fonts, images or positioning that do not fully describe the intended semantic structure. Keep full-page fallback only when text is absent or unusually complex rather than flattening all mixed-content pages.

All real-sample test files remain private. The local Python conversion prototype is useful for crosschecking observable layout traits, **not** proof that the Android PDFBox path produces identical EPUBs.
