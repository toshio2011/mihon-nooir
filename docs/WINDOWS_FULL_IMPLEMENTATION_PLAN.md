# MihonNooir Windows — full desktop product delivery plan

**Started:** 29 October 2026  
**Repository:** [toshio2011/mihon-nooir](https://github.com/toshio2011/mihon-nooir)  
**Implementation branch:** `feature/windows-full-v1.0.16`  
**Android source baseline:** `30681a3f80a2c4e4a31376603e6def60269c8ed2` — frozen, tested v1.0.16 source; production Android release stays unmodified.

## Product contract

Build **one Windows desktop MihonNooir app**, not a separate converter, while protecting the already-released Android app. The final Windows release must let a user:

1. Launch the application with a bundled runtime and managed localhost manga backend, without separately invoking Java or Suwayomi commands.
2. Discover *user-chosen* trusted sources/extensions, search/browse manga with covers, inspect details, add to library, choose specific/all/ranged chapters and download with progress, retry, pause/cancel, conflict checks and source limits.
3. Read online and downloaded chapters (single/double page, webtoon, RTL/LTR, zoom/fit, keyboard, bookmarks, saved progress) and reopen content offline after restart.
4. Convert selected/downloaded manga and local CBZ, ZIP, image directories, suitable EPUB and PDFs into the **same five Android formats**: EPUB 2.0.1, EPUB 3, XTC 1-bit, XTCH 2-bit, CBZ.
5. Honor full Android converter settings, including Manga/Manhwa/Manhua presets, orientation, double-page splits, contrast, dithering, color EPUB/CBZ, RTL, overlaps, cover/metadata, custom device resolutions, **Panel by Panel**, **Row by Row**, **Auto Fit**, intentional **Full Page Before Splits** and **Keep Chapter First Page Whole**. Diagnostics ZIP requires a separate opt-in Windows implementation: do not expose a nonfunctional control.
6. Preview output and execute persistent batch jobs with progress, cancellation, retry and safe naming/overwrites; send compatible output to Folio Nooir/XTEINK on trusted LAN and verify acknowledgements.
7. Provide a real Windows installer and portable version, saved settings/library/downloads, safe updater, logs/backup/import and appropriate security/redistribution notices.

No DRM bypass, bundled copyrighted manga, unapproved sources, hidden extension installation, OCR requirement or paid AI dependency. PDF text/diagram conversion retains best-effort limitations already documented on Android. Do not imply perfect Android/desktop pixel parity without fixture-based proof.

## Completed foundation, not a final Windows release

- Shared JVM conversion core and codecs built from **the same Kotlin source files used by Android**, with synthetic format and metadata tests.
- Source discovery and chapter selection/download bridge through the existing Suwayomi client; **mock-server validated**, real released backend remains unverified.
- Earlier GUI proof of concept and unsigned Windows `jpackage` portable app-image from [PR #28](https://github.com/toshio2011/mihon-nooir/pull/28) / [CI #39895051344](https://github.com/toshio2011/mihon-nooir/actions/runs/39895051344), **not** a final installer.
- This branch: restore the desktop GUI on v1.0.16, expose shared panel/row/Auto Fit and overview options, enable direct **local folder/CBZ conversion without Suwayomi**, honor chapter first-page/overview semantics in the desktop converter and add actual output regression tests. These changes require green CI before being marked validated.

## Milestones, in delivery order

### W1 — Safe v1.0.16 integration and Windows preview

- [x] Create isolated branch from verified Android source; leave Android release branch, signing credentials, installed package and firmware unchanged.
- [x] Recover desktop GUI/source browser/selected chapter export code from draft PR #28 without overwriting newer Android source or tests.
- [x] Restore Java 21/Windows `jpackage` unsigned app-image workflow with v1.0.16 panel-engine dependencies.
- [x] Expose full desktop panel/row settings and enable local folder/CBZ conversion.
- [x] Add first offline local CBZ/image-folder reader with natural filename order, keyboard paging, RTL arrow mapping, fit/zoom and persisted per-file progress; test archive path safety. This is **not** the online/offline Suwayomi chapter reader yet.
- [x] Add user-curated **persistent local library** with versioned, atomic file index, missing-source visibility, reopen/recent ordering and safe removal of entries without deleting files; launch offline without any Suwayomi connection.
- [x] Add explicit **save already-downloaded Suwayomi chapters as offline CBZs** into local library, cap 20 chapters per action, validate archives, atomic publish and never silently enqueue missing source chapters. Mock-server contract tests cover persistence/reuse; real Suwayomi remains unverified.
- [x] Preserve Suwayomi manga **series/title/chapter metadata** in a separate versioned, atomic Windows-only offline index, with chapter order, missing-file visibility, safe record removal, and a grouped reader screen reachable from Local library. Chapter CBZs and the user's existing local library remain independent. New mock tests are required to pass before declaring this verified.
- [ ] Add persistent download/reader queue, progress per chapter, cancellation, resume and offline series/chapters view beyond the current CBZ-per-chapter library.
- [x] Preserve first-page and full-page-overview semantics; add Kotlin tests for actual XTC/XTCH/EPUB2 outputs.
- [ ] Pass full Android FOSS CI, host converter suites, Windows JVM/headless UI tests and packaged Windows executable smoke. No source/media uploaded to a public repo.
- [ ] Compare Windows outputs against Android for a matched legal fixture; byte-level where deterministic, page geometry/reading order otherwise.

**Gate:** A Windows portable artifact launches, can convert local manga offline to all five formats and leaves Android regression green. Until then it is a development preview.

### W2 — Managed Suwayomi backend

- [x] Implement an **explicitly user-approved** process manager for pinned `Suwayomi-Server-v2.4.2366.jar`: loopback-only server flags, dedicated root directory, bounded GraphQL readiness, safe output log, startup refusal on occupied ports and ownership-limited stop/shutdown. Expose Start/Stop controls in Windows GUI; no silent jar download or installation.
- [x] Add JVM process-launch security contract tests; require bundled Windows app-image to include `java.exe`.
- [ ] Select and audit the officially released server artifact and its SHA-256 + license, validate executable startup/API/storage/auth/extensions on real Windows, and package it as an app-managed dependency; **not bundled yet**.
- [ ] Perform real end-to-end integration with a legally accessible test title: add trusted source → search → download chapters → produce EPUB2 and XTC.
- [ ] Implement isolated localhost-only process manager (start/health/version/stop/restart), private data directory, opt-in extension repository setup and clear offline/errors.
- [ ] Package server/runtime alongside the app; guarantee clean shutdown and never expose unrestricted service on the LAN.

**Gate:** No manual server installation required for basic browsing/download on a clean Windows machine.

### W3 — Full manga library and reader

- [ ] Native cover grid, series detail, chapter view, downloaded marker, filters/categories/search/history.
- [ ] Read online/offline: page/webtoon/double-page, RTL/LTR, zoom, keyboard, fullscreen, progress/bookmark persistence.
- [ ] Independent persistent download queue with retry/cancel, disk/memory/source limits and no duplicates; reopen downloads offline after app restart.
- [ ] Explore Mihon backup import as an explicit, validated import flow. Never silently conflate Suwayomi and Android databases.

**Gate:** browse → download → restart offline → read → resume at last page.

### W4 — Complete exporter and e-reader transfers

- [ ] Cover/metadata/chapter-range/grouping UI and full settings parity tests, including panel/row and Auto Fit visual comparison.
- [ ] PDF mixed text/illustration and web-to-EPUB adapters with lawful inputs and no false OCR claims.
- [ ] Persistent conversion/transfer queue, progress, cancellation, resume/retry, collision-safe filenames, output preview and huge CBZ memory budgets.
- [ ] Trusted LAN XTEINK pairing, discovery, upload, receiver ACK, safe rename/skip and on-device X4 validation.

**Gate:** select online or local content → produce all five supported formats → open/send on target reader.

### W5 — Production Windows release

- [ ] Redesign list-heavy Swing prototype into accessible desktop experience (book covers/sidebar/preview/activity), preserving tested backend/models.
- [ ] Windows installer and portable distribution with bundled JVM/backend and explicit update channel.
- [ ] Windows installation/update/uninstall, SmartScreen/code-signing strategy, checksum verification, scaling/accessibility, secure settings/backups.
- [ ] Real-machine e2e tests for first run, browse, offline reader, conversion, sending, recovery, low disk/memory, large files and error handling.
- [ ] Publish official Windows release only after safety, functional and licensing acceptance criteria pass. Android release is independent.

## Architecture boundaries

- `overlay/app/src/main/java/dev/nooir/bridge/`: source-of-truth platform-neutral converters/geometry. Do **not** fork detector thresholds or codecs for Windows.
- `desktop/cli/`: desktop image/CBZ decoding and file writer; reuse the shared engine.
- `desktop/suwayomi/`: typed localhost service adapter; no remotely accessible backend by default.
- `desktop/ui/`: Windows-specific UI and orchestration, no hidden network actions or long operations on the UI thread.
- New Windows-only process manager, reader, queues and storage modules should remain outside Android overlays.
- Never put signing keys, copyrighted pages, private diagnostic ZIPs, passwords or personal manga content into CI artifacts/issues/releases.
- Match settings migration/serialization to Android's `MangaConversionOptions`; tests must reject accidental mode loss.
- Do not call a prototype or a green compile a finished desktop app; actual Windows GUI clicks, server, and physical transfer remain explicit gates.

## Known risks and decisions

- The older desktop GUI PR is far behind `main`; do **not** merge its older conversion settings or codec source over v1.0.16.
- Java2D and Android Bitmap rendering differ; verify framing, grayscale, colors, panel order and margins on representative legal fixtures.
- Suwayomi extensions can be unavailable/untrusted; handle failures clearly, require user consent for installation.
- Windows Defender/SmartScreen may warn for unsigned portable builds. Publish stable signed install packages only after review; never instruct disabling protections.
- Android's first-panel behavior was introduced after the old Windows preview; desktop overview and title-page cases require regression tests.
- For help/history, see [existing desktop roadmap](WINDOWS_DESKTOP_FEASIBILITY_ROADMAP.md), [desktop README](../desktop/README.md) and [project roadmap #8](https://github.com/toshio2011/mihon-nooir/issues/8).
