# MangaNooir — cross-chat development handover

> **Audited 20 October 2026 (Malaysia time).** This is the maintained starting point for the **next ChatGPT/Codex session**. It records work found in the actual GitHub repository, **including changes newer than the previous chat's last message**.
>
> **Important:** CI-green means the Android build/tests passed, **not** that the feature was tested on a real phone, transferred to an XTEink, or released to stable users. **Do not merge draft PRs, publish a release, flash firmware, or replace the working app without the user's explicit approval.**

## 1. What we're building and nonnegotiable decisions

**MangaNooir** is a full **Mihon-based Android manga reader** plus an independent **e-reader Companion Hub**. Preserve existing Mihon reading, library, extensions, browsing, downloads, tracking, categories, backups and updates. Companion workflows are additive, not a replacement for Mihon's familiar UI.

The companion targets **Folio Nooir** running on XTEink (primarily a physical **old X4**, with X3 compatibility to verify), and can save converted books to Android storage. Source-code fork: [MangaNooir](https://github.com/toshio2011/mihon-nooir). Companion firmware: [Folio Nooir](https://github.com/toshio2011/folio-nooir).

Confirmed product goals:
- Export **selected Mihon chapters** as XTC (1-bit), XTCH (2-bit/4 gray), fixed-layout EPUB or CBZ; include cover, title, author, synopsis where formats/firmware permit. **Quick Export** temporarily fetches selected non-downloaded chapters through Mihon source extensions; keep original downloads untouched.
- Import existing phone files through Android's file picker; select multiple and convert **supported CBZ/images**, or send other files *unchanged*. Mere transfer does not ensure the reader understands the document.
- Offer X3/X4 presets; crop, contrast, dithering, reading order, page/spread modes, **portrait/landscape**, overlap, and eventually more MangaPoint-grade settings and previews. Inspect [MangaPoint](https://github.com/W1seGit/MangaPoint) and [xtcjs](https://github.com/varo6/xtcjs) for *selective* ideas, not blanket copies.
- **Web to E-reader:** paste a public HTTPS article URL, extract actual readable text and inline pictures, generate **reflowable EPUB**, save or send to XTEink; browser Share input is still a future enhancement.
- **PDF → EPUB:** aim for **genuinely selectable, resizable novel text interleaved with diagrams/illustrations**, as with a mystery novel with floor plans (e.g. *Strange Houses*), **not simply a book of screenshots**. PDFs with an existing text layer can yield real text; **image-only scans and complex/vector pages fall back to images within the same EPUB**. No required AI, paid OCR, cloud conversion or fabricated text. Exact paragraph and image placement in complex PDFs is not guaranteed.
- A lightweight **Device Files** browser plus the reader's existing web file manager, and **sequential heavy jobs** so XTEink's ESP32 SD/Wi-Fi aren't hit by simultaneous uploads. Keep the XTEink side light.

This is a **GitHub integration/overlay** that patches a pinned Mihon checkout, not an entire fork of Mihon's sources committed wholesale. Android package identity is distinct from upstream Mihon, and code references to experimental changes belong on their actual branches.

## 2. Repository status — critical branch map

All PRs in the table were **open drafts, unmerged**, on this audit. **Stacked base branches matter**: the newest branch includes the earlier experimental work. Re-read actual GitHub heads before editing because this is a snapshot.

| PR / app version | Head branch → PR base | Implemented in code (not a stable release) |
|---|---|---|
| [#9 · 1.0.3](https://github.com/toshio2011/mihon-nooir/pull/9) | `integration/quick-export-send-convert` → `main` | Combined foundation for Quick Export + Send/Convert |
| [#10 · 1.0.4](https://github.com/toshio2011/mihon-nooir/pull/10) | `feature/transfer-queue-conversion-settings` → `integration/quick-export-send-convert` | WorkManager jobs, file picker/batches, upload byte progress and conversion presets |
| [#11 · 1.0.5](https://github.com/toshio2011/mihon-nooir/pull/11) | `feature/xtc-cover-synopsis` → `feature/transfer-queue-conversion-settings` | Proper first-page covers and optional XTC/XTCH synopsis metadata + Wi-Fi metadata sync |
| [#12 · 1.0.6](https://github.com/toshio2011/mihon-nooir/pull/12) | `feature/orientation-overlap-pagecuts` → `feature/xtc-cover-synopsis` | Portrait/landscape, clockwise toggle, 0–50% overlapping webtoon/sideways page cuts, pure planner tests |
| [#13 · 1.0.7](https://github.com/toshio2011/mihon-nooir/pull/13) | `feature/web-link-to-epub` → `feature/orientation-overlap-pagecuts` | **More → Web to E-reader** public HTTPS link → reflowable EPUB (text + embedded supported images), background Save/Send |
| [#14 · 1.0.8](https://github.com/toshio2011/mihon-nooir/pull/14) | `feature/pdf-mixed-reflow-epub` → `feature/web-link-to-epub` | **More → PDF to EPUB**, text-plus-illustrations or original-image-page mode, PDFBox-Android + Android PdfRenderer |
| **[#15 · 1.0.9](https://github.com/toshio2011/mihon-nooir/pull/15)** | **`feature/device-files-browser-and-queue` → `feature/pdf-mixed-reflow-epub`** | **Latest umbrella preview**: lightweight read-only XTEink SD-folder listing + browser file-manager link, visible multiple manga jobs, unified sequential WorkManager queue across local/manga/web/PDF jobs |

Also open draft [#6 Quick Export](https://github.com/toshio2011/mihon-nooir/pull/6) and [#7 Send/Convert](https://github.com/toshio2011/mihon-nooir/pull/7), historical components of #9. Do not independently merge these duplicates.

**Latest app experimental source at audit:** `feature/device-files-browser-and-queue` HEAD **`4f456ecd2ea8973f10634438c3c34c86112103ef`**, `config/version.txt = 1.0.9`. Its actual ancestry contains earlier experimental features. This is the **branch to inspect first for continuing all features**, not `main`.

**Stable/working main remains 1.0.1**, code not merged from these PRs. Before this documentation handover, `main` HEAD was `e9ce2d2fc2c26cd75af4106bcaa3ca26ca7e7278`. **No published GitHub Releases** were returned at audit; test APKs are **GitHub Actions artifacts**, not GitHub Releases. A docs-only commit in `main` does not change its app version or merge preview work.

### CI and artifacts verified on 20 October 2026

- **1.0.9 tip `4f456ecd…`**: [Actions run **39332739610**](https://github.com/toshio2011/mihon-nooir/actions/runs/39332739610) **completed success**, job `build` green. Artifact **`mihon-nooir-foss-apk`** (artifact ID `12225557159`, ZIP approximately **169,706,616 bytes**) present and not expired at audit. Other preceding PR #15 tip runs also succeeded. This is an *experimental/test* APK, not confirmed on X4 hardware.
- **1.0.8 PR #14 head `7870c3732621…`**: [Actions **39331615047**](https://github.com/toshio2011/mihon-nooir/actions/runs/39331615047) **success** (also **39331488601** success). Earlier failures on this branch were followed by fixes; do **not** treat the earlier failing run as the current result.
- **1.0.7 PR #13**: [Actions **39261057124**](https://github.com/toshio2011/mihon-nooir/actions/runs/39261057124) **success**.
- **1.0.6 PR #12**: [Actions **39259698764**](https://github.com/toshio2011/mihon-nooir/actions/runs/39259698764) **success**. An earlier page-slicing test failed but was fixed in later commits.
- **1.0.5 PR #11**: [Actions **39228413383**](https://github.com/toshio2011/mihon-nooir/actions/runs/39228413383) **success**.
- Verify current **PR head SHA + workflow head SHA** together before claiming a newly changed branch's latest CI is green. APK artifact names stay the same, and GitHub may require login to download them.

## 3. Feature detail and implementation locations

**Manga chapter export:** In manga details, **long-press a chapter**, tap others, then tap **Export**. Normal Export exports selected **downloaded** chapters, or **all downloaded chapters** when none selected; Quick Export needs at least one selected online chapter and uses the installed extension. Chapters are combined into a book, not one output file per chapter. See [chapter-selection walkthrough](CHAPTER_SELECTION.md).

**Import/conversion core:** `overlay/app/src/main/java/dev/nooir/mangabridge/` and `overlay/app/src/main/java/dev/nooir/bridge/` hold the Android adapter, rendering, XTC codec, EPUB/CBZ writer, upload, WorkManager and UI. `tools/apply_overlay.py` inserts source/toolbar/More-tab/worker wiring into the pinned Mihon checkout. `config/mihon-upstream.sha` pins upstream commit **`deeb9b2584b6e246135cbd31da49a39e6b6e1e52`**. `config/version.txt` controls custom version/versionCode.

**XTC/XTCH cover + synopsis (#11):** XTC/XTCH have no standard synopsis field. MangaNooir creates a proper cover as first page where available, and adds a bounded optional **`[UTF-8 metadata JSON][u32LE JSON length][NOOIRMD1]`** trailer after legacy image data. No legacy page header/table offsets moved. It also tries Folio Nooir's existing `POST /api/book` metadata override after Wi-Fi transfer and checks response semantics. **Manual SD/USB-copy synopsis decoding** needs separate **Folio Nooir draft [PR #1](https://github.com/toshio2011/folio-nooir/pull/1)**; that firmware PR is open/unmerged and has NOT been verified flashed to X4. Existing current firmware may show the metadata after successful API sync without flashing the parser PR. Test old file compatibility, caches, cover fallback, chapter nav, and firmware memory. See [XTC metadata](https://github.com/toshio2011/mihon-nooir/blob/feature/device-files-browser-and-queue/docs/XTC_METADATA.md).

**Page orientation / overlap (#12):** `MangaConversionOptions.kt`, `AndroidPageSource.kt`, `bridge/PageSlicePlanner.kt`, `tests/PageSlicePlannerTest.kt`. Portrait default, landscape may subdivide tall portrait pages into top/bottom overlapping sections to avoid tiny sideways text; 0–50% overlap (default around 8%). Split spreads in reading order. Native XTC/XTCH page framebuffer stays X4 **480×800** / X3 **528×792**; rotated artwork is fitted *within* it. EPUB/CBZ may use landscape page images. Keep cover unsplit/upright. Saved legacy 7-field tokens still parse alongside new 10-field options. [Orientation guide](https://github.com/toshio2011/mihon-nooir/blob/feature/device-files-browser-and-queue/docs/ORIENTATION_OVERLAP.md).

**Web → EPUB (#13):** `NooirWebArticle.kt`, `NooirWebToReaderDialog.kt`, `NooirWebExportWorker.kt`, shared `bridge/ReflowEpubWriter.kt`. Existing Mihon Jsoup dependency, public HTTPS article, conservative content/image limits, no JS execution or paywall bypass; includes actual EPUB XHTML text, select JPEG/PNG/GIF figures, EPUB3 metadata/nav, save via Android picker or XTEink Wi-Fi. **Manual URL paste is implemented; browser Share-to-MangaNooir is NOT yet implemented.** Site-specific article extraction, captions/complex HTML, missing pictures and EPUB validity on Folio require tests. [Web guide](https://github.com/toshio2011/mihon-nooir/blob/feature/device-files-browser-and-queue/docs/WEB_TO_READER.md).

**PDF → EPUB (#14):** `NooirPdfToEpub.kt`, `NooirPdfToEpubDialog.kt`, `NooirPdfExportWorker.kt`. Offline PDFBox-Android **2.0.27.0** (Apache-2.0) added to patched Gradle dependencies plus Android `PdfRenderer` fallback. Two modes: **reflow text + embedded images** or **preserve original page images**. Retain real PDF text and embedded diagrams where possible; scans, vector-intensive/unsafe pages become images. **Do not claim perfect semantic extraction or OCR**. Limits currently 100 MiB input/600 pages. No automatic chapter reconstruction or preview yet. Must test mixed PDFs with graphics, Unicode, two columns, nonstandard figures and EPUB reader. **PDF → XTC/XTCH/CBZ is NOT yet the implemented PDF output path**; don't conflate “send original PDF” with converted reader formats. [PDF guide](https://github.com/toshio2011/mihon-nooir/blob/feature/device-files-browser-and-queue/docs/PDF_TO_EPUB.md).

**XTEink Device Files (#15):** `NooirDeviceFileClient.kt`, `NooirDeviceFilesDialog.kt` and `docs/DEVICE_FILES_AND_QUEUE.md`. Uses only existing Folio Nooir `GET /api/files?path=...` to browse a **single folder on demand**, capped, no recursive scanning, thumbnails, downloads or background polling. **Read-only in-app**. For folder creation/download/delete/rename/move open Folio Nooir's existing **`http://READER-IP/files`** in phone browser while File Transfer is active. No companion firmware PR required for that existing endpoint. Review host/path validation and SD load. Never delete/rename without user's action. File Transfer's local HTTP interface is unauthenticated; **use trusted LAN/hotspot only**.

**Common job queue (#15 late fixes):** `NooirDeviceWorkQueue.NAME = "manganooir_manga_exports"` intentionally retains WorkManager's existing unique-chain name. On the **latest 1.0.9 branch**, manga export, local-file import/send, web conversion and PDF conversion all call `enqueueUniqueWork(NAME, ExistingWorkPolicy.APPEND_OR_REPLACE,...)`, serializing heavy operations/reader uploads. This improves the older separate-file vs manga race. **More → Send & Convert** now enumerates multiple manga jobs with names/stages (rather than showing only last manga). A canceled dependency may affect subsequent jobs; verify cancellation/recovery and queue after failure. **Documentation warning:** the older [Device Files and Queue guide](https://github.com/toshio2011/mihon-nooir/blob/feature/device-files-browser-and-queue/docs/DEVICE_FILES_AND_QUEUE.md) still says local-file transfer is a separate queue and device-wide coordination is future work; **that paragraph became stale after PR #15's final shared-queue commits**. Follow the code/current PR and fix that guide in a later docs pass. The UI is not yet a complete unified history dashboard for every job type.

## 4. Current gaps and test priorities

**Critical before stable merge/release:**
1. Install/test the actual 1.0.9 arm64 APK on Android (user's previously used physical test phone: RedMagic 9 Pro); validate overlay launch, Mihon reader, extensions, downloads, reader state, back navigation, Android notification/foreground WorkManager and existing-data migration.
2. **X4 physical File Transfer tests** using actual displayed IP: send small XTC/XTCH/EPUB; make sure page/cover/synopsis and Folio shelf/recent entries work. Try repeated transfers, duplicate filename, loss of Wi-Fi, SD full/slow, user cancellations and APPEND_OR_REPLACE chains. Distinguish **100% bytes uploaded** from **receiver confirmed**.
3. **1.0.6 orientation/overlap**: portrait regression; sideways pages held in both rotation directions; 0/8/15% overlaps; long webtoons; double spreads RTL/LTR; correct XTC framebuffer sizes; chapter nav and cover.
4. **1.0.7 webpage**: common static HTTPS article with inline pictures, article containing captions, redirects, missing images, sites requiring JavaScript; inspect saved EPUB text as selectable and run EPUB validation.
5. **1.0.8 PDF mixed**: *a properly licensed sample of an illustrated PDF with selectable text and images*, a scanned-only PDF, vector floor plan and mixed page types; ensure no content lost and text is actually reflowable; test memory and fallback. Never claim this is as good as original EPUB without checking.
6. **1.0.9 Device Files**: load only explicit folder, existing firmware path/query shape, actual file/folder sizes, huge-folder limit, read-only behavior, opens official file manager URL, no disruptive polling during transfer.
7. Validate installed APK signatures/build update path. Separate CI builds may use different signing keys; **back up MangaNooir data before uninstalling** any old test APK. Do not touch stable main without explicit approval.
8. Test optional XTC footer on external readers and companion Folio firmware separately, with backup and rollback available. **Never assume firmware PR is already installed**, and **never flash without approval**.

**Still missing (not silently “done”):** complete MangaPoint settings parity (fixed margins, trim sensitivity/padding, sophisticated gutter/quiet cuts, zoom/tiling, more dithering kernels, auto-levels, interactive preview, arrows and per-book overrides), share-in web URLs from Android browser, better article extraction, PDF-to-image outputs XTC/XTCH/CBZ, semantic PDF chapter reconstruction/editing, OCR (not requested/should not require paid AI), output book splitting for huge conversions, durable historical queue UI, signing/release workflow and comprehensive hardware validation.

**Transfer limits:** experimental Nooir uploader has a conservative **180 MiB per-output-file** limit, not a measured ESP32 maximum. No auto chapter/volume splitting yet. A reader can reject existing destination names; interrupted uploads can leave partial SD files. Verify on hardware, do not guess limits.

## 5. Build and development workflow

The Android app is assembled from the repo overlay plus pinned Mihon checkout; do not build this tiny repository as if it contained the full Android project directly.

```bash
# Repository checkout, in the preview branch you want to test
git switch feature/device-files-browser-and-queue

# Local source-only checks (needs kotlinc / Python)
python3 -m unittest discover -s tests -p 'test_*.py' -v
bash tests/run.sh

# CI does checkout + apply_overlay.py against pinned Mihon:
# config/mihon-upstream.sha = deeb9b2584b6e246135cbd31da49a39e6b6e1e52
# Then build inside pinned checkout:
./gradlew :app:assembleFoss -Pdist=foss -Pinclude-telemetry=false
```

Useful URLs:
- [All MangaNooir workflows](https://github.com/toshio2011/mihon-nooir/actions/workflows/build-apk.yml) · [latest verified 1.0.9 green run](https://github.com/toshio2011/mihon-nooir/actions/runs/39332739610)
- [Roadmap issue #8](https://github.com/toshio2011/mihon-nooir/issues/8) (historical checklist; some earlier unchecked items have since been implemented on **draft** PR branches)
- [Installation guide](GETTING_STARTED.md) · [Chapter selection](CHAPTER_SELECTION.md) · [Wi-Fi instructions](WIFI_TRANSFER.md) · [Format notes](CONVERSION.md)
- [Folio Nooir companion firmware](https://github.com/toshio2011/folio-nooir) · [Folio optional XTC metadata PR #1](https://github.com/toshio2011/folio-nooir/pull/1)

## 6. Suggested next action for the new session

**First do a fresh GitHub status pass**, because newer PRs/commits may have appeared since this handover. In particular, inspect **PR #15 branch HEAD, Actions, artifacts and changed files**, then its stacked predecessors #14 → #9, and check Folio Nooir PR #1. Read the source and docs instead of assuming older handover statuses still apply.

**Priority recommendation:** keep draft branches safe and begin structured **1.0.9 phone + X4 test**, fix real bugs and outdated documentation, and only then consider additional features or release/merge. If the user explicitly wants continuing coding without hardware access, prioritize low-risk testable improvements and one focused batch per CI run, while clearly separating “implemented”, “CI green” and “hardware confirmed”.

**Instruction to assistant taking over:** The user likes getting concrete progress in good-sized batches, expects you to actually use the GitHub connector for repo work, prefers a working end-to-end result over decorative mockups, and wants all agreed features/notes retained. Keep periodic progress updates, compare prior work before duplicating it, and **never fabricate a successful device test**. Ask before making destructive changes, merging, publishing or flashing.

---

This handover is a **snapshot of source and CI as inspected on 20 October 2026**. Check actual GitHub source/heads for changes before acting. A new chat can start by opening this file and saying: **“Continue MangaNooir from docs/PROJECT_HANDOVER.md; check the latest GitHub PRs/CI first, then continue testing and fixing, without merging or flashing anything.”**
