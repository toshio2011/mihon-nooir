# MangaNooir — project handover and continuation guide

> **Last audited:** 20 October 2026 (GitHub state observed after the 19 October development session).  
> **Authoritative repository:** https://github.com/toshio2011/mihon-nooir  
> **Master roadmap:** https://github.com/toshio2011/mihon-nooir/issues/8  
> **Reader firmware:** https://github.com/toshio2011/folio-nooir  
> **Status:** Active experimental development; **not a stable release**. This handover documents observations, not proof of hardware compatibility.

## New-chat handoff — start here

Paste this into a new ChatGPT conversation:

> Continue **MangaNooir** development from https://github.com/toshio2011/mihon-nooir. **First read `docs/PROJECT_HANDOVER.md` on `main` in full**, then inspect the live GitHub PRs, latest branch commits and CI logs before changing any code. The newest experimental work is `feature/nooir-tools-hub-activity-queue` (1.0.10, not yet PR/CI verified), based on `feature/web-novel-batch-export`; both extend successful 1.0.9 PR #15. Preserve all original Mihon features, the working `main` release and existing docs. Fix and validate the new 1.0.10 tools hub, activity queue and web novel importer; keep web URL → reflowable EPUB and PDF → mixed-content reflowable EPUB work moving. Do not merge/release/flash firmware without permission. Update the roadmap/docs, perform meaningful code batches, check actual Android CI and be clear about remaining physical X4 tests. Do not assume the previous chat's older status is current.

**This file is the handover snapshot; the repository/CI are the source of truth.** GitHub state can change immediately. Check the branch SHA and build results again in your new session.

## 1. Non-negotiable product decisions

**MangaNooir is the full Mihon Android manga reader PLUS an e-reader companion.** Do not remove or impair Mihon's sources/extensions, library, reading, downloads, tracking, history, categories, backup/restore, or navigation.

Main experiences:
1. Manga detail → select chapters → export or send XTC/XTCH/EPUB/CBZ. Support already-downloaded and, experimentally, online chapters.
2. Local files → SAF picker → inspect/convert or send unchanged → local storage or Folio Nooir XTEink.
3. Web link / web novels → real reflowable EPUB with selectable text, illustrations, metadata and TOC, save or send.
4. **PDF → EPUB with genuinely reflowable text and preserved embedded images/diagrams**, including pages with text above/below floor plans (novel such as *Strange Houses* is the reference use case). Scan-only pages/complex vector diagrams fall back to image pages **in the same EPUB**. No mandatory AI, OCR subscription, or third-party hosted conversion service. **Do not flatten every PDF page into images** when the user chose reflowable.
5. A discoverable Nooir Tools/Companion Hub, job activity/queue, and XTEink file browsing. Other reader devices later.

Source originals remain untouched; chapter/page data and story content are not fabricated. Reader format support is not guaranteed by successful Wi-Fi upload.

Reference projects: [MangaPoint](https://github.com/W1seGit/MangaPoint), [xtcjs](https://github.com/varo6/xtcjs), [WebToEpub](https://github.com/dteviot/WebToEpub). Learn from settings/algorithms/UX without copying entire implementations. **WebToEpub is GPLv3**; do not transplant code into the MIT MangaNooir source. Preserve Mihon/Tachiyomi/other actual licenses and credits.

## 2. Accurate branch/PR/build state

**Snapshot 20 October 2026**. The earlier handover claiming that PDF PR #14 was still failing is **outdated**. The PDF, device-files and shared-queue branches have since received green CI runs and additional commits.

| Work | Branch / PR | Most recent observed branch SHA | CI evidence | Status |
|---|---|---|---|---|
| Stable baseline & user guides | `main` (1.0.1) | `e9ce2d2fc2c2` before this docs commit | [main run 39235941812](https://github.com/toshio2011/mihon-nooir/actions/runs/39235941812) success | Preserve; no 1.0.10 code here |
| Prototype online chapters | [#6](https://github.com/toshio2011/mihon-nooir/pull/6) | `c33aa7cb8d10` | Older work | Draft; superseded by stacked integration |
| Prototype local files | [#7](https://github.com/toshio2011/mihon-nooir/pull/7) | `51330263299e` | Older work | Draft; superseded by stacked integration |
| Integrated Quick Export/files | [#9](https://github.com/toshio2011/mihon-nooir/pull/9) | `70887e842984` | Older work | Draft parent of #10 |
| Background queue, conversion options | [#10](https://github.com/toshio2011/mihon-nooir/pull/10), `feature/transfer-queue-conversion-settings` (1.0.4) | `1dacc9f24821` | [39220489438](https://github.com/toshio2011/mihon-nooir/actions/runs/39220489438) success | Draft |
| XTC/XTCH cover + synopsis | [#11](https://github.com/toshio2011/mihon-nooir/pull/11), `feature/xtc-cover-synopsis` (1.0.5) | `1b95110a401c` | [39228413383](https://github.com/toshio2011/mihon-nooir/actions/runs/39228413383) success | Draft |
| Orientation and overlap | [#12](https://github.com/toshio2011/mihon-nooir/pull/12), `feature/orientation-overlap-pagecuts` (1.0.6) | `2200ad46d46d` | [39259698764](https://github.com/toshio2011/mihon-nooir/actions/runs/39259698764) success | Draft |
| URL → article EPUB | [#13](https://github.com/toshio2011/mihon-nooir/pull/13), `feature/web-link-to-epub` (1.0.7) | `de8318c71d5f` | [39261057124](https://github.com/toshio2011/mihon-nooir/actions/runs/39261057124) success | Draft |
| PDF → mixed reflow EPUB | [#14](https://github.com/toshio2011/mihon-nooir/pull/14), `feature/pdf-mixed-reflow-epub` (1.0.8) | `7870c3732621` | [39331615047](https://github.com/toshio2011/mihon-nooir/actions/runs/39331615047) success; FOSS APK artifact verified | Draft; original failure fixed |
| Reader files + shared queue | [#15](https://github.com/toshio2011/mihon-nooir/pull/15), `feature/device-files-browser-and-queue` (1.0.9) | `4f456ecd2ea8` | [39332739610](https://github.com/toshio2011/mihon-nooir/actions/runs/39332739610) success; FOSS APK artifact verified | Draft; latest verified APK branch |
| Multi-story web novels | `feature/web-novel-batch-export` (1.0.10) | `9e7ce3079790` | **No corresponding PR/CI run observed** | Branch exists, experimental, NOT green-verified |
| Nooir Tools + Activity Queue | **`feature/nooir-tools-hub-activity-queue` (1.0.10)** | **`2ef9a150bbd5`** | **No corresponding PR/CI run observed** | **Newest active code, continuation target** |

**GitHub PR #15 is NOT the newest code branch.** Two later unPR'd 1.0.10 branches exist. This is the most important difference from older summaries. GitHub PRs #6, #7, #9–#15 were all observed **open and draft**, with no merges. The current `main` is still the stable 1.0.1 source and docs, not the latest 1.0.10 code.

The builds listed for #14 and #15 have a non-expired `mihon-nooir-foss-apk` artifact as observed during audit. **Android compile/CI success does not equal a successfully installed, tested APK.** APK signing can differ between CI runs; back up MangaNooir app data before replacing/removing a build.

### Experimental lineage: do not merge backwards blindly

`main` → PR #9 integration → PR #10 (1.0.4) → PR #11 (1.0.5) → PR #12 (1.0.6) → PR #13 (1.0.7) → PR #14 (1.0.8) → PR #15 (1.0.9) → `feature/web-novel-batch-export` (1.0.10) → `feature/nooir-tools-hub-activity-queue` (1.0.10).

The final two branches are successive experimental developments inferred from their commit histories and contained source files. **Confirm ancestry/merge-base before cherry-picking, rebasing or opening new PRs**. None should be merged to `main` automatically.

### Do not confuse main documentation with the old feature-branch README

The user-facing main README and `docs/GETTING_STARTED.md` / `docs/CHAPTER_SELECTION.md` / `docs/FAQ.md` / `docs/WIFI_TRANSFER.md` were updated in commit `e9ce2d2` **after the experimental branch ancestry had diverged**. Active feature branches still have an older README and do not contain all main docs. **Reconcile docs during integration**, keeping the newer main guides, proper credits, and all new experimental instructions. Never overwrite the improved main README with an old branch README.

## 3. What each experimental feature actually does

### Manga exports and XTEink Wi-Fi

- Manga Details: long-press a chapter to enter selection mode, tap others, then choose Export. **Normal Export** uses selected downloaded chapters or **all downloaded chapters** when none selected. **Quick Export** requires selected online chapters and fetches missing pages temporarily. One combined book (not one file per chapter); original Mihon downloads unchanged.
- XTC (1-bit), XTCH (2-bit / 4 gray), image-based fixed-layout EPUB3, CBZ + ComicInfo. X3 528×792 and X4 480×800 output presets.
- Reader File Transfer mode and trusted same LAN/reader hotspot. Sender uses Nooir HTTP APIs. **100% bytes sent != reader confirmed success**.
- WorkManager foreground processing; up to **25 local files** in a file-picker batch; experimental **180 MiB per-file** upload limit (app safeguard, not firmware hard maximum).
- XTC/XTCH synopsis is NOT a standard format field. PR #11 adds optional bounded `NOOIRMD1` JSON trailer and dedicated first-page cover; directly sent books also attempt metadata sync through existing Folio Nooir `POST /api/book`. Plain manually copied XTC synopsis would require the separate experimental firmware parser.
- PR #12 supplies page orientation (portrait/landscape), clockwise/counterclockwise rotation choice and **0–50% overlapping webtoon/sideways-page cuts**, using original XTC/XTCH panel dimensions. Do not misinterpret "landscape" as simply resizing a whole manga page smaller.

### Web URL → EPUB (#13)

- More → Web to E-reader. **Paste a public HTTPS article URL**; Jsoup extracts body/available metadata/images, uses shared `ReflowEpubWriter`, saves reflowable EPUB3 or uploads to XTEink.
- Background worker; local Android output. No AI or OCR. Initial limitation: JS-rendered sites, login/paywalls and unusual image/layout structures. **Browser Android Share URL ingestion is NOT confirmed finished**.
- Inspect security carefully: hostname/redirect/DNS changes, private-network SSRF protection, link/image request bounds, incomplete EPUB cleanup, HTML/XHTML semantics, real website compatibility.

### PDF → mixed EPUB (#14)

- More → PDF to EPUB. Modes **Reflowable text + illustrations** or **Keep original pages as images**.
- PDFBox-Android extracts existing PDF text and bitmap pictures; Android PdfRenderer handles scanned-only or complex/vector page fallback. Text is supposed to remain selectable, images interleaved near their estimated source position.
- No paid service, no OCR. Protected PDFs and semantically ambiguous layouts can fail. Experimental input limits ~100 MiB / 600 pages.
- **Critical acceptance requirement**: *Strange Houses*-style **real text interleaved with diagrams** inside one EPUB. The existence of code/build is NOT proof of correct diagram placement, paragraph order or renderer behavior. Test real PDF samples and open the EPUB in a reader before claiming quality.
- PDF → XTC/XTCH/CBZ by rasterized pages is still a separate planned output. Do not silently claim it already converts local PDFs to every format.

### XTEink device browser and shared queue (#15)

- More → XTEink Device Files, read-only, one SD folder at a time via firmware `GET /api/files?path=...`; no recursive background scans, no mass thumbnail fetches, no firmware changes.
- Full file-manager actions use existing Folio Nooir browser `http://READER-IP/files`. Warn users that deleting/moving is destructive.
- Original queue defect: queuing manga B while manga A ran made A **look** lost, because UI watched only B's UUID. PR #14/#15 show multiple running/waiting/completed entries and add a **shared sequential WorkManager chain** for manga, local files, web article and PDF operations. Avoid concurrent reader uploads or SD reads. App's `NooirDeviceWorkQueue.NAME` reuses `manganooir_manga_exports`; inspect all producers for consistent unique-work semantics.

### WEB NOVELS branch — 1.0.10, not PR/CI verified

**`feature/web-novel-batch-export`** (SHA `9e7ce3079790`) includes:
- `NooirWebNovel.kt` / `NooirNovelBatchWorker.kt` / `NooirNovelBatchDialog.kt`, user guide `docs/WEB_NOVELS.md`.
- Up to **10 public HTTPS story URLs per batch**, each becomes its **own EPUB** with TOC, chapter boundaries, supported images, and available story cover.
- User-defined first/last chapter range (currently max 80 chapters per individual book); ~1.3-second throttle between chapter requests; sequential queue and individual success/error reporting.
- Generic **public HTML TOC discovery**, not full site-specific API support. **Wattpad landing pages may be JavaScript-rendered; don't promise working Wattpad imports or bypass locked/premium chapters.** WebToEpub (GPLv3) and wpd.my are inspiration/credits only.
- **No observed CI for this branch**. Validate 5–10 chapter site, chapter ranges, non-JS/JS landing pages, broken image fallback, valid EPUB cover, rate limits, and partial batch failures.

### NOOIR TOOLS HUB branch — NEWEST 1.0.10, not PR/CI verified

**`feature/nooir-tools-hub-activity-queue`** (SHA `2ef9a150bbd5`) adds:
- `NooirToolsHubDialog.kt`: replaces **five scattered entries under Mihon More** with **one "Nooir Tools" parent**, grouping Activity Queue, local Send & Convert, Web link → EPUB, Web novels batch, PDF → EPUB, XTEink Device Files. **Manga detail Export stays at manga details**; native Mihon Download Queue is preserved.
- `NooirActivityQueueDialog.kt`: centralized WorkManager activity queue showing running/waiting/finished for manga, files, articles, PDFs and novels. It intentionally warns that canceling one dependency-chain work item can affect later jobs; whole-queue cancellation requires confirmation.
- `tools/apply_overlay.py` now injects only a single More entry pointing at the Hub.
- **No observed PR or green CI for this newest branch.** Inspect its UI/dialog navigation, type signatures, dependency injection, runtime open/close/back behavior, activity labels/progress, queue cancellation semantics, and whether any prior tools became unreachable. This should be the first major coding/validation task in a new chat.

## 4. Folio Nooir cross-repository facts

- Latest **published** Folio Nooir release observed: [v1.6.5](https://github.com/toshio2011/folio-nooir/releases/tag/1.6.5) (14 Oct 2026).
- Firmware repo **default branch is `codex/folio-nooir`** (not `main`). Its `main` is much older (last observed commit from Aug 2026, around v1.5.10); **do not mistake `main` for the latest released firmware**.
- Draft [firmware PR #1](https://github.com/toshio2011/folio-nooir/pull/1), `feature/xtc-rich-metadata-footer`, is based on **that older `main`** and exposes the optional XTC/XTCH synopsis trailer. It was **not hardware verified**, and needs a forward-port/rebase against the actual current firmware branch before merge or flashing.
- Released reader may already have a working `/api/book` metadata update API; confirm against the installed firmware and actual response, not solely HTTP 200.
- Wi-Fi File Transfer page/server runs on the ESP32 reader; preserve low memory, responsive UI, file ownership and SD integrity. Avoid recursive heavy directory scans; do not overwrite an existing reader book without explicit safe collision handling.
- X4 is the confirmed target for physical tests. X3/X4 Pro assumptions must not be presented as proven hardware support. Firmware building/flashing requires explicit user approval, backups and a safe rollback path.

## 5. New chat: immediate priority work, in order

1. **Open this file on GitHub, then re-check ALL current PRs/branches/runs**. Start with `feature/nooir-tools-hub-activity-queue` SHA `2ef9a150bbd5` and `feature/web-novel-batch-export` SHA `9e7ce3079790`. Do not repeat an outdated PDF CI failure as current.
2. Review ancestry, latest source changes and all WorkManager jobs for consistent queue semantics; check `tools/apply_overlay.py` still integrates with the pinned Mihon upstream. Verify overlay smoke tests and ephemeral signing/updater behavior.
3. **Get real Android CI for both unPR'd 1.0.10 branches.** Open draft PR(s) against the correct experimental base if appropriate and keep them unmerged; diagnose any compile/test errors. Only claim a 1.0.10 APK exists if the matching latest run is **green with an artifact**.
4. Verify **Nooir Tools** UI is clear on a phone, every tool opens, no overlay navigation regressions, activity queue displays all jobs without making jobs disappear, and safe cancel-all behavior.
5. Verify **web novels** with permitted, publicly readable HTML chapters. Proper titles/covers/TOC, per-story output and failure reporting. Do not promise Wattpad-specific support where generic extractor cannot read public TOCs.
6. Exercise **Web link → EPUB** and **PDF mixed EPUB** with real input samples, including embedded illustrations, vector floor plans, scanned pages, relative image paths, XHTML validity, EPUB font reflow and reader compatibility.
7. Exercise **orientation/overlap** on output and verify manga order, 0%/8%/15% repeats, cover unsplit, chapter navigation, XTC/XTCH page dimensions and X4 readability.
8. Physical RedMagic/Android and X4 File Transfer tests: title/author/synopsis, covers, EPUB open, medium/large upload, duplicate filename, interrupted transfer, multiple queued books and SD file browser. **Never claim device-tested without the user actually testing.**
9. Document known issues; stabilize/improve a **usable first-run UX**. Integrate the newer user-facing README/FAQ/guides from `main` without accidentally overwriting newer source changes.
10. Keep progressing on next agreed enhancements: PDF image-output (XTC/XTCH/CBZ), browser Share → app, smarter mixed PDF layout reconstruction/preview, article and novel extractor improvements, further MangaPoint/xtcjs reading settings, download-time format selection, per-chapter/volume output splitting, better device profiles and notifications.

## 6. Technical validation / pitfalls to prioritize

- **Stable APK vs preview:** `main` v1.0.1 remains working baseline. Latest **green** PR build observed is 1.0.9 (#15), **not the newest source branch**. Latest source is unverified 1.0.10. Do not publish APKs or merge without explicit consent.
- **CI verification:** successful workflow should include overlay structure tests, pure JVM XTC/XTCH/EPUB/CBZ tests, page-slicing and reflow EPUB tests, plus actual Android FOSS assembly and artifact. CI does not test physical X4, e-ink orientation or EPUB rendering.
- **Novel code safety:** cap book/chapter/image counts, cancellation and temp file cleanup; status must distinguish site blocked/JS-only/partial output; no DRM/paywall circumvention or shared credentials.
- **Web fetch security:** public HTTPS validation, redirect limits, DNS/private-IP rebinding issues, content-size limits and output sanitization; explicit no local-host or intranet fetch.
- **PDF fidelity:** avoid recycling PDFBox-cached bitmap objects incorrectly; bound memory. Real text that looks extractable can be mangled by PDF fonts/layout. Vector-only diagrams may require page image fallback. Avoid silent text/image duplication.
- **EPUB standards:** verify mimetype first/uncompressed, valid OPF/nav/XHTML, stable relative image paths, Unicode characters, cover-image declaration, chapter order. Use EPUBCheck when practical.
- **Queue:** a WorkManager dependency chain may cascade cancellations; don't offer a misleading "Cancel this one only" UI. Confirm only one active device upload; delayed/broken jobs should not permanently block normal reading.
- **Upload:** 180 MiB limit is experimental app protection; slow reader, flaky Wi-Fi and SD-full can fail. 100% bytes sent is not server confirmation. Full web manager has destructive operations—built-in browse remains read-only until tested.
- **Reader firmware:** never assume latest code is in the old `main` of Folio Nooir; PR #1 base is stale. Flash/rebase only with explicit approval and rollback safety.
- **Signing:** CI APK upgrade may fail due to unrelated keys; backup user data before uninstalling. Keep separate MangaNooir Android package from official Mihon.
- **Documentation drift:** experimental branch README is older than main's user guides. Reconcile safely.

## 7. Documentation and project reference index

**Stable main guides** (newer than experimental branch documentation):
- [Main README](https://github.com/toshio2011/mihon-nooir/blob/main/README.md)
- [Docs index](https://github.com/toshio2011/mihon-nooir/blob/main/docs/README.md)
- [Getting started](https://github.com/toshio2011/mihon-nooir/blob/main/docs/GETTING_STARTED.md)
- [Chapter selection and Quick Export](https://github.com/toshio2011/mihon-nooir/blob/main/docs/CHAPTER_SELECTION.md)
- [Wi-Fi transfer](https://github.com/toshio2011/mihon-nooir/blob/main/docs/WIFI_TRANSFER.md)
- [File formats / conversion](https://github.com/toshio2011/mihon-nooir/blob/main/docs/CONVERSION.md)
- [FAQ](https://github.com/toshio2011/mihon-nooir/blob/main/docs/FAQ.md)
- [Versioning, build and signing](https://github.com/toshio2011/mihon-nooir/blob/main/docs/UPDATES.md)

**Experimental feature guides**:
- [XTC cover/synopsis](https://github.com/toshio2011/mihon-nooir/blob/feature/xtc-cover-synopsis/docs/XTC_METADATA.md)
- [Landscape and overlap](https://github.com/toshio2011/mihon-nooir/blob/feature/orientation-overlap-pagecuts/docs/ORIENTATION_OVERLAP.md)
- [Web article to EPUB](https://github.com/toshio2011/mihon-nooir/blob/feature/web-link-to-epub/docs/WEB_TO_READER.md)
- [PDF to real-text EPUB](https://github.com/toshio2011/mihon-nooir/blob/feature/pdf-mixed-reflow-epub/docs/PDF_TO_EPUB.md)
- [XTEink device browser + queue](https://github.com/toshio2011/mihon-nooir/blob/feature/device-files-browser-and-queue/docs/DEVICE_FILES_AND_QUEUE.md)
- [Multi-story web novels](https://github.com/toshio2011/mihon-nooir/blob/feature/web-novel-batch-export/docs/WEB_NOVELS.md)

**Roadmap issues**: [#1 download-time export](https://github.com/toshio2011/mihon-nooir/issues/1), [#2 MangaPoint options](https://github.com/toshio2011/mihon-nooir/issues/2), [#3 metadata](https://github.com/toshio2011/mihon-nooir/issues/3), [#4 upload](https://github.com/toshio2011/mihon-nooir/issues/4), [#5 Mihon retention/test matrix](https://github.com/toshio2011/mihon-nooir/issues/5), [#8 master roadmap](https://github.com/toshio2011/mihon-nooir/issues/8).

**Keep updating this file at meaningful milestones**; new chats should never have to reconstruct branch lineage and CI status from a long conversation.
