# PDF large-document and multi-file conversion — draft, November 10 2026

**Status:** isolated Android source candidate, not merged or released. Original official Android **v1.0.17** is untouched.

## Goal

A real book can have 1,000, 2,000 or more PDF pages. MihonNooir should not reject it just because it crosses an arbitrary 600-page or 100 MiB threshold. Likewise, converting a folder of legally owned PDFs should not mean repeating 50 separate picker flows.

## Changes on the isolated branch

- Remove the artificial **600-page cap** for PDF→reflowable EPUB2/EPUB3 and preserve-page EPUB.
- Remove the **100 MiB PDF-input guard** from the PDF worker and parser. Copy source files in 32 KiB pieces, checking for **96 MiB actual free space** periodically, not allocating the input as one ByteArray.
- Use PDFBox-Android's supported **16 MiB mixed-memory plus on-disk scratch** setting for mixed PDF text/illustration processing; keep page-by-page `PdfRenderer` image fallback, close resources and preserve original PDF.
- Remove the arbitrary **300 MiB generated EPUB cutoff**. ZIP entries are already added individually by `ReflowEpubWriter`; require at least 64 MiB available storage as pages are appended.
- For XTC/XTCH, replace 600 pages with the codec's **real 65,535-page representation bound**, with independent offset/size checks and 64 MiB available storage reserve.
- Add **Choose PDF file(s)** via Android document picker, supporting up to **50 PDF selections per queue action** (repeat selections for more books). Each selection is converted into its **own** target book, maintaining source order, original per-file PDF title/author metadata, and distinct collision-safe filenames.
- Reuse WorkManager's existing persistent `manganooir_manga_exports` **serial FIFO chain**, one PDF job at a time. Do not process PDFBox documents simultaneously or compete with e-reader uploads.
- For local multi-output, ask the user to pick a folder via Android SAF. Check existing filenames **before converting and before saving**, reject any collision or SAF auto-renaming, and delete only a newly created incomplete destination on failure. Single-file output still uses Android CreateDocument.
- For XTEINK, queue one upload per converted PDF; leave its **180 MiB per-upload safety policy** and duplicate-name checks intact.

**Limits are not the same thing as arbitrary cutoffs:** Android memory, temporary storage, the selected e-reader's own resource capacities, EPUB2's 512 KiB OPF reader compatibility, and the XTC header/data-size representation can still prevent an exceptionally large conversion. A failure must report the cause and never silently truncate pages. This does **not** guarantee 20,000 pages or an enormous scanned PDF works on every device.

## Tests and release gates

- [x] Host test code added: 1,201-page selectable EPUB2/EPUB3 synthetic ZIP stress and collision-safe 50-file batch naming.
- [ ] Host tests run green against this branch.
- [ ] Android FOSS build/overlay tests compile the PDF picker, WorkManager serial continuation, scratch setting and SAF save.
- [ ] Android instrumentation smoke and regression tests green, with no changes to panel conversion.
- [ ] Real 1,001-, 2,000- and 5,000-page text/mixed/scanned PDF cases on REDMAGIC with memory and cancellation logs. Use **original or privately owned** source files; never commit copyrighted PDF samples.
- [ ] Batch 3 PDFs with duplicate names, null metadata, non-ASCII names, low storage, failed conversion, cancel, restart and existing folder name.
- [ ] Confirm all source page count and order preserved; check EPUB2/3 TOC and image placement, PDF→XTC/XTCH output in actual Folio Nooir X4.
- [ ] Cancel after page 1,000, restore queued work and verify no partially published EPUB or overwritten book.
- [ ] Check the actual EPUB2 512 KiB metadata compatibility and XTEINK 180 MiB upload policies. Do not claim to support unlimited output or to defeat storage/reader limits.

## Other limits requiring a separate scoped audit

| Path | Current guard | Assessment |
|---|---|---|
| Manga source chapter export | 1,000 pages/chapter; 25 MiB/image; 400 MiB/chapter temp | 1,000 looks conservative, but source API may materialize page lists. Needs bounded streaming and source tests before raising |
| Imported CBZ/image conversion | 512 MiB input; 10,000 resulting pages | High resource budget but arbitrary; safe import scan, temp storage and reader parser need validation |
| Windows local CBZ/folder converter | 5,000 source images, 25 MiB/source image, 25m decoded pixels | Prevents costly filename lists, image-bombs and UI memory spikes. Widen only after streaming/enumeration tests |
| Local Suwayomi CBZ copy | 400 MiB per copy | Safeguard for disk and incomplete HTTP copies, independent of chapter-count rules |
| Novel separate-book batch | 10 URLs/action, 120 KiB combined manifest | Limits queue/paste data size, not EPUB page count; use a resumable manifest for very large jobs |
| General file-transfer picker | 25 files/action | Interaction/WorkManager bound, not a format limit |
| XTC/XTCH codec | 65,535 page header + additional 32-bit length/offset constraints | Real binary format; preserve |
| EPUB2 targeted at Folio Nooir | 512 KiB OPF and related reader constraints | Real target-firmware compatibility, not an EPUB standard page limit |
| Transfer to XTEINK | 180 MiB one-file HTTP upload | Conservative ESP32/SD path reliability policy; do not remove without X4 device tests |

Do not change these unrelated format/resource guards in the PDF branch. Follow up with focused benchmark-based optimizations rather than a blanket removal of error checks.

## Mihon upstream

As of November 10, **official Mihon stable** remains v0.21.3, and Mihon Preview/Nightly r8234 is available. Do not mix unreleased upstream reader/extension migrations into this PDF change.

