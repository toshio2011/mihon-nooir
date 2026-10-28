# Compare panel EPUB exports without manually paging through everything

This is a **developer/tester diagnostic**, not a new Android app screen. It works offline, uses only Python's standard library, reads EPUB files **without extracting manga images onto disk**, and commits no manga artwork.

## When to use

Compare a **baseline Panel by Panel EPUB** with a fresh EPUB exported from the **same source chapters, device profile, orientation, overlap, and Full Page Before Splits settings**. The split count can change, so page 126 in an earlier build may no longer be page 126 after a successful split.

The command locates the same underlying PNG/JPEG byte payloads using SHA-256 and the **EPUB spine's actual reading order** instead of relying on frame numbering:

    python3 tools/panel_epub_diff.py "Detective Conan panel - old.epub" "Detective Conan panel - new.epub"
    python3 tools/panel_epub_diff.py old.epub new.epub --json -o panel-comparison.json

The output reports:

- Old/new numbers of image frames and **exact identical image payloads**.
- Each changed sequence, with old/new **1-based inclusive frame ranges** and representative file names.
- When one old grouped frame becomes two new frames, it appears as a one-to-two **candidate replacement** surrounded by identical anchor frames. The tool does **not** claim it was a correct split.
- A no-anchor comparison signals that every output was re-rendered/recompressed, source chapters differ, or settings changed. Try matching settings first.

### Reading the real Conan baseline

The October 2026 baseline has a deliberate **full-page overview** preceding the partly grouped two-panel story row. Panel p00125.png matched Row p00095.png; the next Panel p00126.png matched Row p00096.png byte-for-byte even though it contains two illustrated columns.

The next same-settings export should keep the overview but ideally replace that grouped story row with two correctly ordered, unclipped RTL panels. **Do not rely on the old frame numbers** in the new EPUB; use unchanged anchors and compare nearby pictures. Other previously unchanged illustrated rows were R225 and R232.

### What the tool cannot prove

**Exact file identity isn't image equivalence.** A re-encoded/resized page may look identical while hashing differently. Conversely, a new split can omit a speech balloon or reverse panel order while passing archive and pixel-count tests. The comparison locates review targets; it cannot certify artwork completeness, reading order, or device legibility.

A meaningful physical acceptance test still requires checking changed panels, speech balloons, overview sequencing, XTEink X4 readability, and any unexpected standalone word fragments. The code makes no OCR or page-type assumptions about bonus or credit pages.

## Running regression tests

    python3 -m unittest discover -s tests -p "test_panel_epub_diff.py" -v

GitHub Actions also automatically discovers these tests via Python unittest discovery. They create synthetic EPUBs in a temporary directory—no manga pages are included in the repository.

## Privacy and limitations

All processing is local; it does not upload either EPUB. The report contains file names and frame ranges but **no image bytes**. Avoid publishing copyrighted EPUBs or derived pages into GitHub issues or commits. A corrupt or non-fixed-image EPUB fails explicitly instead of producing misleading comparisons.
