# MihonNooir — full cross-track handover (10 November 2026)

**Last verified:** 10 November 2026 (MYT, late afternoon). **This document lives on the *draft PDF branch*, not merged `main`.** On resuming development, read GitHub's current PR heads, CI conclusions, and new comments first: all snapshot statuses below can become stale.

## 1. Mandatory project boundaries and user decisions

- Repository: **https://github.com/toshio2011/mihon-nooir**.
- Public Android: **v1.0.17**, published 5 November 2026 — **DO NOT change or replace its APK**, signing certificate, app data or public GitHub release until explicit user approval. Never tell the user to uninstall the working Android app. Android v1.0.18 candidate remains separate.
- Main at handover: `81959782a5c0d867d602b4ac625a7aa299cfc47e` (public Android v1.0.17 baseline). All active PRs **draft**, do not merge/publish without explicit user approval.
- **Android v1.0.18 updater**: [draft PR #80](https://github.com/toshio2011/mihon-nooir/pull/80), branch `fix/android-updater-abi-v1.0.18`. Maintain in-place upgrade with unchanged signing cert/package and preserved app UID/data.
- **Windows complete desktop**: [draft PR #81](https://github.com/toshio2011/mihon-nooir/pull/81), branch `integration/windows-full-current-main`. **Do not merge older Windows draft PR #53 directly**: its detector is stale and would overwrite the current Android detector.
- **Huge PDFs, batch selection, oversized transfer advice**: [draft PR #82](https://github.com/toshio2011/mihon-nooir/pull/82), branch `feature/pdf-large-multi-input-v1.0.17`. Current focus.
- Keep `folio-nooir` firmware XTEINK project separate. The user has directed XTEINK development/checkouts to **D: only; do not use the old C: project directory again**. Its 1.6.7 / Web / X4 Pro & Classic branches are handled in separate project handovers, not modified by this MihonNooir PR.
- Stable Android, PR #80, PR #81, PR #82 must remain independently testable and must not trample each other's branches. WorkManager's established persistent FIFO queue name is `manganooir_manga_exports` — never rename it casually.
- For large files: **never impose XTEINK's 180 MiB Wi-Fi limit on the separate "Save to phone" path**. Save complete converted EPUB/XTC/XTCH on phone as long as real disk/format constraints allow. Over-limit **Send to XTEINK** must show an understandable warning, not imply the conversion was corrupted or forbid phone save.
- User prefers conservative, verifiable release decisions and complete handover notes. Do not claim successful QA before the workflow confirms it.

## 2. Current official Android and upstream Mihon

- [Official MihonNooir v1.0.17](https://github.com/toshio2011/mihon-nooir/releases/tag/v1.0.17) is the actual installed/released baseline.
- As last checked **10 Nov 2026**, official upstream Mihon stable was **v0.21.3** (25 Oct 2026); upstream main had a 9 Nov dependency-injection refactor; Mihon preview/nightly **r8234** released 10 Nov. Do not mix upstream experimental changes with these PRs. Upstream follow-up tracked in [issue #20](https://github.com/toshio2011/mihon-nooir/issues/20).

## 3. Draft PR #82 — large-PDF and multi-PDF work (active)

[Open draft PR #82](https://github.com/toshio2011/mihon-nooir/pull/82) | [PDF-specific implementation/QA plan](https://github.com/toshio2011/mihon-nooir/blob/feature/pdf-large-multi-input-v1.0.17/docs/PDF_LARGE_BATCH_2026-11-10.md)

**Exact latest branch head written before this note:** `87423c1eeb8c0597a4c435b1216c4d0931e673dc`. After creating this handover document the head advances; use GitHub API for the actual HEAD.

### Features now present in source, pending complete Android QA

- Removed experimental **600-page PDF** rejection in PDF→EPUB and PDF→XTC/XTCH. Kept genuine XTC/XTCH **65,535-page field limit** and byte/offset constraints.
- Removed experimental **100 MiB source PDF** and **300 MiB converted EPUB** hard cutoffs. On phone, stream original PDF from Android SAF into cache in 32 KiB chunks, check real available disk periodically (96 MiB free reserve), and require 64 MiB free while writing EPUB/XTC. Actual finite storage, image memory and target format may still fail.
- Reflowable EPUB uses **PDFBox-Android mixed 16 MiB RAM + disk-backed scratch** (`MemoryUsageSetting.setupMixed(16L * 1024 * 1024).setTempDir(context.cacheDir)`). PDF page-image fallback uses one `PdfRenderer` page at a time; bitmaps are bounded to approximate 1,600×2,200; embedded image dimensions are preflighted (25 million pixels), and pages with >30 pictures or processing errors fall back to full-page image instead of silently discarding illustrations.
- `NooirPdfToEpubDialog.kt` uses `ActivityResultContracts.OpenMultipleDocuments` for up to **50 selections per picker action**; there is **no page-count limit per selected PDF**. Re-select and queue additional batches to process more. One source PDF becomes one output EPUB2/EPUB3/XTC/XTCH. Batch filenames are collision-safe within a selection via `PdfBatchPlan.kt`.
- Batch conversion uses serial `WorkManager.beginUniqueWork(NAME, APPEND_OR_REPLACE, first).then(next)...`, not concurrent PDFBox loads or competing ESP32 uploads. Android SAF folder picker for separate local output files, checks collision before work and again before save, verifies resulting SAF name and deletes incomplete newly created output on failure. Single-document CreateDocument path is preserved.
- Dialog now persists/tracks WorkManager UUIDs of all jobs in a batch, treats `BLOCKED`, `ENQUEUED`, `RUNNING` as busy, and displays saved/failed/waiting-or-running counts. Last PDF alone no longer determines the whole batch status.
- **Transfer vs save:** `XteinkTransferPolicy.kt` centralizes **180 MiB Wi-Fi upload policy** and helpful instruction to save full file on phone, split volumes, or use a smaller format. `NooirUpload` rejects known oversized uploads **before contacting reader**; `NooirPdfExportWorker.kt` and `NooirMangaExportWorker.kt` label it **"XTEINK transfer too large"**, not "PDF/manga conversion failed". Local phone-save paths deliberately do **not** invoke the 180 MiB policy.
- No modifications to released v1.0.17 APK, app signing or stable release.

### Tests added (not all fully green)

- `tests/PdfBatchPlanTest.kt`: duplicate names/case-insensitive collision, unicode naming fallback, 50 selections, and rejection of picker selection count >50. Tests the *batch count*, not book-length restriction.
- `tests/XteinkTransferPolicyTest.kt`: just-over/at/under 180 MiB advisory, and local save wording.
- `tests/PdfLargeReflowTest.kt`: generates legal **1,201-section EPUB2 and EPUB3** with synthetic image assets and checks TOC, contents and no truncation; host suite runs from `tests/run.sh`.
- `overlay/app/src/androidTest/java/dev/nooir/mangabridge/PdfLongExportInstrumentedTest.kt`: generates a tiny **1,001-page original PDF using Android PdfDocument**, converts it to EPUB3 using the real Android `NooirPdfToEpub` image-preservation path, then checks 1,001 XHTML and image files and last-page navigation.
- `.github/scripts/pdf-long-smoke.sh` checks instrumentation pass/fail and captures logcat. `.github/workflows/build-apk.yml` adds branch-scoped `android-pdf-long-smoke` after same-run Android FOSS APK instrumentation build.
- Real 2,000+/5,000-page text, vector, mixed and scanned PDF benchmarks, real device cancellation/storage tests and X4 file-opening tests **NOT DONE**.
- The PDFBox text stripper `getText(book)` currently traverses the PDF pages to reach one requested page; re-creating it for every source page may make reflow mode O(N²) on long text PDFs. This is an **investigation item**, not a measured result. Avoid declaring long mixed-mode PDFs fast without a dedicated benchmark and safe single-pass collector design.

### CI failure and correction — do not forget

1. [Run 42780777074](https://github.com/toshio2011/mihon-nooir/actions/runs/42780777074) **FAILED** on `Assemble fork (FOSS APK)`, after host-format suite and EPUBCheck **PASSED**. Error `NooirMangaExportWorker.kt:282:36 Unresolved reference 'output'`. The transfer-too-large notification catch block referenced `val output` declared inside `try`.
2. Fixed in commit **`87423c1eeb8c0597a4c435b1216c4d0931e673dc`**: use `inputData.getString("output_uri").isNullOrEmpty()` in catch. This is the same persisted WorkManager input and remains available in catch.
3. [Subsequent run 42784778995](https://github.com/toshio2011/mihon-nooir/actions/runs/42784778995) was **in progress** when this handover was drafted. **Re-check its exact final conclusion**, then diagnose any compile, runtime or instrumentation failure using the GitHub job logs. Do not claim Android compile/1,001-page test passed until actually green.
4. Multiple earlier PR #82 CI runs show `cancelled` because the workflow deliberately cancels superseded push runs. These are **not** green, nor evidence of app crashes. Avoid repeated commits during a lengthy emulator run unless a confirmed blocker requires one.

### Remaining high-priority PDF acceptance

1. Achieve full green exact-head host → FOSS APK compile → Android panel regression → Android 1,001-page original PDF instrumentation.
2. Confirm PDF `ORIGINAL_PAGES` and `MIXED` text+illustration modes, PDF → XTC/XTCH, all >600 pages, complete page counts and no silent lost figures.
3. Real phone multi-PDF batch: names with collisions/unicode, SAF permissions, selected destination, cancellation, long-lived WorkManager continuation, resume, failed item followed by successful items. Include over-180 MiB **save to phone** and over-180 MiB **send to XTEINK** results.
4. Benchmark/optimize PDFBox repeated page-tree traversal for multi-thousand-page text PDFs **without** regressing reflow or memory floor.
5. Use only owned/synthetic PDFs in CI. Never upload copyrighted manga or private PDF contents into source control.
6. Preserve Android v1.0.17 upgrade compatibility and normal panel/reader/export behavior. **Do not merge or publish this draft** without explicit approval and QA.

## 4. Windows desktop — draft PR #81

[PR #81 — current-main safe integration](https://github.com/toshio2011/mihon-nooir/pull/81) | branch `integration/windows-full-current-main` | recorded head `008f45f70fc2e03452ec861561649bfeb8ff016b` | [detailed readiness report](https://github.com/toshio2011/mihon-nooir/blob/integration/windows-full-current-main/docs/WINDOWS_RELEASE_READINESS_2026-11-10.md).

- **Green [Windows build CI 42769247374](https://github.com/toshio2011/mihon-nooir/actions/runs/42769247374)**: headless JVM UI/model tests, packaged unsigned Windows app with Java runtime and five-format XTC/XTCH/EPUB2/EPUB3/CBZ synthetic exports, real official pinned Suwayomi GraphQL API on Windows loopback and owned process shutdown.
- **Green [Android regression CI 42769247473](https://github.com/toshio2011/mihon-nooir/actions/runs/42769247473)** including Android panel instrumentation. Windows PR #81 uses current 2,638-line Android detector, not stale 1,042-line variant from old PR #53, and does not change production Android overlay or `tests/run.sh`.
- Added `tests/WindowsOfflineJourneyTest.kt`: synthetic, explicitly approved chapter enqueue, complete/download, separately approved offline CBZ copy, library/reader page decode, restart, export offline EPUB2 and XTC after stopping loopback Suwayomi.
- After one saved CBZ, GUI offers **Read now / Open library / Close**; multi-chapter saves offer library instead of guessing a chapter.
- Optional data folder environment variable `MIHONNOOIR_DATA_DIR` supports **D:\\MihonNooirData**, default still `~/.mihonnooir`. No migration, deletion or overwrite implied.
- **Still NOT a complete public Windows release:** real user-facing source/extension download → offline → reading → conversion journey on a Windows machine, GUI keyboard/DPI/zoom/stress acceptance, current Android panel visual parity, direct XTEINK transfer and ACK, PDF/EPUB/web import, production installer/updater/signing, UX and data migration/backup. No physical Windows QA yet.
- **Never merge PR #53 wholesale**: old branch's panel detector would regress Android. PR #81 stays draft until explicit approval and full requirements.

## 5. Android v1.0.18 updater — draft PR #80

[PR #80](https://github.com/toshio2011/mihon-nooir/pull/80) | branch `fix/android-updater-abi-v1.0.18` | recorded head `0c59b4173664642ed45313684a90e329016039b3`.

- Fixes in-app GitHub release asset ABI selection so a REDMAGIC 9 Pro ARM64 should choose correct signed `app-arm64-v8a-foss.apk`. Do not imply the draft has been published or tested on that actual handset.
- **Green [signed Android emulator in-place-upgrade run 42764500428](https://github.com/toshio2011/mihon-nooir/actions/runs/42764500428)** confirms v1.0.17 → v1.0.18 package install on Android 16 with persistent release certificate and package user ID continuity. **Green [Android build/panel regression run 42764505932](https://github.com/toshio2011/mihon-nooir/actions/runs/42764505932)**.
- Added quick mock-ADB script regression test, with a previous shell bug resolved (pipefail/SIGPIPE, then missing `name` binding). No user phone data wiped; no need to uninstall Android v1.0.17.
- Still draft, not published. Follow signing-safe release procedures and explicit user permission before changing official stable.

## 6. Relevant separate Folio Nooir XTEINK project (boundary only)

Folio Nooir 1.6.7 (original X4) + related Web and X4 Pro/Classic tracks are separate repos/branches. Its current operational constraint: **do not use C: drive for its workspaces/builds or retry cleaning any occupied C: directory. D: is the designated working/archive drive**. Keep X3 fallback safe; stability/reader-memory first; no cross-project firmware changes from this handover. Consult Folio's own D:-drive handover and running Codex sessions for details; their current CI and firmware states have *not* been re-verified as part of this MihonNooir handover.

## 7. Recommended next agent steps (in strict order)

1. Fetch PR #82 head and [run 42784778995](https://github.com/toshio2011/mihon-nooir/actions/runs/42784778995) status, job steps/logs. The 87423c1 compile correction is already pushed; fix **only** evidence-based failures. Be careful GitHub `pull_request` builds use merge refs.
2. After full Android APK and 1,001-page emulator suite green, thoroughly inspect why PDFBox mixed-mode reflow may traverse all pages N times; implement a bounded, measurable optimization in PDF-only branch with tests (not an untested sweeping rewrite).
3. Investigate batch output semantics, no overwrite, protected XTEINK transfer limits, image preservation and cancellation while keeping WorkManager FIFO consistent.
4. Update this note's exact SHA/CI outcomes or add a dated handover continuation, and comment on PR #82 with evidence.
5. Keep separate PR #80 and #81 draft; verify their latest checks only when working those tracks; never merge/publish without user approval.

## 8. Paste-ready new-chat continuation prompt

> Kiro, continue MihonNooir from our cross-project handover:
> https://github.com/toshio2011/mihon-nooir/blob/feature/pdf-large-multi-input-v1.0.17/docs/HANDOVER_2026-11-10_ALL_TRACKS.md
>
> Read the full document, inspect **live GitHub state** for PR #82 (large PDF/multi-PDF), PR #81 (Windows), PR #80 (Android 1.0.18 updater), and latest CI. The official v1.0.17 Android APK is stable; do not modify it or merge/release without my approval. Focus first on exact-head PR #82 Android build and the 1,001-page PDF instrumentation test; fix real failures, protect all page content, prevent duplicate batch exports, preserve unlimited phone saving subject to actual storage/format constraints, warn only for oversized XTEINK Wi-Fi transfers. Keep independent branches and update handover notes after work. Folio Nooir XTEINK firmware work is separate and must stay on D:, never use C: project paths.
