# MihonNooir Windows — release readiness and remaining scope

**Reviewed:** 10 November 2026  
**Active safe integration:** [draft PR #81](https://github.com/toshio2011/mihon-nooir/pull/81) (`integration/windows-full-current-main`)  
**Previous development branch:** [draft PR #53](https://github.com/toshio2011/mihon-nooir/pull/53) — **do not merge directly**, older Android panel detector  
**Public Android version remains:** v1.0.17. Android v1.0.18 updater maintenance in separate draft PR #80.

## Bottom line

**NOT a finished Windows release.** The basic reader, converter, local library, queues and managed Suwayomi foundation are implemented, with a passing bundled-JVM build and real Suwayomi GraphQL handshake. Those successes do not prove full Mihon-style daily-use workflows, visual parity, an installer, direct XTEINK transfers, or dependable end-to-end manga downloads on a real Windows desktop.

### Verified by automation

- [x] Package an unsigned Windows app image with a bundled Java 21 runtime and produce a portable ZIP plus SHA256.
- [x] Compile the Windows GUI and shared Kotlin conversion engine **from current Android v1.0.17 main**, retaining the latest Android panel detector.
- [x] Run Kotlin/JVM headless reader/library, conversion, bookmark, queue and local Suwayomi mock tests.
- [x] Use the **packaged Windows Java runtime** to convert an original generated PNG to EPUB 2.
- [x] Download the official pinned Suwayomi v2.4.2366 JAR, verify its SHA256, start a loopback managed process on Windows, query real GraphQL source/library endpoints, and shut down **only that owned process**.
- [x] Verify PR #81 differs from current main in **no Android overlay, tools, config or main regression-suite files**.
- [ ] Confirm **new** packaged all-five-format (XTC, XTCH, EPUB2, EPUB3, CBZ) CI smoke and protected-Android-path guard on latest PR #81 head. These changes have been committed but remain pending until that head passes.

[Prior fully green preview CI](https://github.com/toshio2011/mihon-nooir/actions/runs/42738084417). Note the parallel Android panel-diagnostic emulator CI for that integration failed despite its host build passing and device instrumentation reporting two tests with no test failures; investigate harness/output semantics separately, never waive Android regressions.

### Functional implementation versus acceptance

| Component | Implemented today | What is still required |
|---|---|---|
| Offline comic reader | Local CBZ/folder, single/spread, RTL, scroll/webtoon preview, fit/zoom, bookmarks, fullscreen, resume | Real Windows GUI, huge CBZ, scrolling/jumps and memory stress |
| Local bookshelf | Persistent entries, title filter/cover, grouped downloaded chapters, Continue Reading | Polished cover-grid, richer browse/filter, backup/import and migration UX |
| Converter | Shared XTC, XTCH, EPUB2, EPUB3, CBZ engine with panel/row/Auto Fit settings | Current Android *visual/pixel/geometry parity*, chapter and RTL screenshots, full-format app-image smoke |
| Suwayomi backend | Pinned hash-verified JAR, start/stop, localhost API, safe opt-in download | Trustworthy extension/source selection, real authorized manga download/restart/end-to-end |
| Download queues | Bounded persistent remote requests and offline CBZ copy jobs, explicit batch approval, retry/recheck and cleanup | Actual remote progress if supported, proper server-side cancel only if available, bulk restart error recovery |
| Import | Local image folder/CBZ/ZIP | PDF, suitable EPUB and web imports |
| XTEINK / Folio transfers | Conversion exports available locally | Trusted LAN discovery/pairing, Wi-Fi transfer and receiver ACK; physical X4 testing |
| Packaging | Unsigned portable ZIP with bundled Java | Reviewed MSI/installer, app updater, signing/licensing, SmartScreen policy and install/uninstall tests |
| Complete Mihon-style desktop | First usable Swing screens and Suwayomi-backed source browsing | Full Android-like feature parity, richer reader/library UI, physical keyboard and scaling/accessibility QA |

**Important:** Windows uses Suwayomi as an optional locally managed manga backend. It is not already a native Windows port of every Mihon Android feature or database. Never imply it silently syncs Android's Mihon library/extensions.

## Ordered completion gates

1. **Release baseline safety:** pass Windows current-main CI, packaged export for all five formats, and all current Android tests or diagnose/fix the diagnostic CI harness without weakening tests.
2. **Real Windows interactions:** first run with no backend, local CBZ/folder import, bookmarks, RTL, two-page/webtoon, massive pages and persisted resume; DPI 100/125/150%, keyboard and file picker.
3. **Real Suwayomi journey:** explicit trusted source → search → chapter download → locally saved CBZ → restart offline → resume reading → export to target device profile. No unapproved source/extension install or invented progress.
4. **Converter parity:** compare exact legal test pages and metadata with current Android v1.0.17, including dark/gray gutters, speech/bubble overlap, Auto Fit, first-page/overview, all five target formats and X4/X3 compatible outputs.
5. **Complete import and transfer scope:** optional PDF/EPUB/web adapters, persistent conversion queue, e-ink device transfer acknowledgments and recovery.
6. **Production delivery:** signed/reviewed portable/installer path, updates, crash recovery, backup/import, source-license/privacy review, real-machine QA and explicit release approval.

**Do not publish a full Windows v1.0 release while steps 1–6 are incomplete.** A CI-green ZIP remains an **unsigned development preview**, not a production app. Do not suggest disabling Windows safety protections to run it.

## Current code safety

- Keep Android's modern `overlay/app/src/main/java/dev/nooir/bridge/PanelDetectionCore.kt`, Android overlay, signing/build workflow and `tests/run.sh` from `main`.
- Place extra Windows tests in `tests/run_windows_full.sh`, with the Android regression suite also executed in Windows build CI.
- No changes to `folio-nooir` firmware or Android v1.0.17 release artifacts.
- All private manga, credentials, downloaded source content, user backup files and diagnostic archives remain out of GitHub commits and public CI outputs.
