# MangaNooir Windows Desktop — Full Reader Feasibility & Implementation Roadmap

**Research date:** 22 October 2026  
**Status:** D1 shared conversion JVM CLI merged (PR #23); D0 localhost Suwayomi download-to-conversion **mock contract integration in progress**. No Windows GUI, installer, bundled backend or real-server end-to-end validation yet.  
**Product priority:** Android v1.0.12 stabilization and repeatable signing/updates remain the release gate before full desktop implementation. Planning and isolated proof-of-concept work may be prepared without modifying Folio Nooir.

## 1. Product scope

**Full MangaNooir for Windows**, not only a converter. The desktop user can:

1. Discover/configure *user-chosen* manga source/extension repositories (none bundled), browse popular/latest titles, search and filter.
2. View manga details, chapters, artwork and metadata; add manga to a categorized local library.
3. Select single, ranges, all unread or all chapters; download chapters for offline reading, see per-chapter progress, pause/resume/retry, respect source/network limitations.
4. Read online or downloaded chapters in single-page, double-page, vertical/webtoon, RTL/LTR, fit-width/height and keyboard/touchpad modes; save reading progress and bookmarks.
5. Export selected chapters, volumes or full downloaded manga to XTC, XTCH, EPUB 2, EPUB 3 or CBZ, retaining original chapter files.
6. Import local CBZ/ZIP/image folders, PDFs and supported EPUBs; convert to reader-appropriate formats with preview and queue.
7. Save output locally or transfer to XTEink via trusted LAN; remember device presets, folder and conflict policy.
8. Manage updates, diagnostics, backups and privacy without requiring Android, a browser tab or manual command-line setup.

**Not promised for v0.1:** exact Android Mihon UI parity; Android extension APK execution directly on JVM; automatic two-way Android library sync; guaranteed compatibility with every extension or protected website; OCR; Windows EXE before CI proves it.

## 1A. Non-negotiable Android ↔ Windows conversion parity

**Product requirement (22 October 2026): Windows must expose the same MangaNooir conversion workflow and conversion settings as Android, not a cut-down converter.** Build one platform-neutral conversion core, with Android and Windows input/output adapters. Keep Android regression tests green and compare generated output with identical fixtures.

| Android MangaNooir capability | Required Windows equivalent |
|---|---|
| EPUB 2.0.1 Classic / EPUB 3 | Same packaging, metadata, cover, navigation and compatibility rules |
| XTC 1-bit Fast / XTCH 2-bit HQ | Same native codec, grayscale, plane layout, orientation and reader constraints |
| CBZ | Same page order, naming, image quality and metadata where supported |
| Chapter selection | Single, range, selected, unread, all; consistent ordering, volume grouping and missing-chapter warnings |
| Quick online / Normal downloaded export | Equivalent workflows, with explicit network availability and source restrictions; reuse cached pages instead of needless redownloads |
| Conversion presets | Same XTEink X3/X4/X4 Pro and other profiles, custom resolution, defaults and overrides |
| Page processing | Same portrait/landscape, spread splitting, overlapping, webtoon slicing, trimming, cropping, margins, resizing, dithering, grayscale and quality controls that are actually implemented on Android |
| Covers and metadata | Same cover fallback order, title/author/chapters, source cover refresh when supported, and source integrity rules |
| PDF → EPUB and PDF → XTC/XTCH | Same best-effort text/illustration and image-mode behavior, with platform PDF renderer differences documented |
| Public web URL → EPUB | Same supported article/web-novel extraction and safety limits; do not imply web-to-XTC/XTCH exists unless implemented |
| Background export queue | Same preflight, destination collision checks, cancellation, per-job progress/errors, persistence and safe retry semantics |
| Output destinations | Save to local folder and send to compatible XTEink receiver with conflict/ACK checks |

**Parity is a requirement, not a current implementation claim.** Some Android features are experimental or incomplete; audit their real behavior and tests before marking a Windows row done. Platform-specific controls (Android SAF picker versus Windows folder picker, WorkManager versus desktop job service) may differ visually, but user-facing behavior and conversion output should match.

**Desktop additions**: drag-and-drop files/folders, batch input selection, preview and compare, native Save As/choose-folder, large-screen chapter table, keyboard shortcuts, and optionally reusable preset import/export. These add to parity; they must not replace or simplify existing MangaNooir workflows.

**Parity gates and tests:**
- [ ] Inventory actual Android conversion settings and default values directly from source/UI; make a single cross-platform settings schema with migration/versioning.
- [ ] Build shared Kotlin/JVM conversion APIs; Android delegates to them without behavior regressions.
- [ ] For each format and representative chapter/PDF/web fixture, compare Android and Windows output (byte-equal where deterministic; otherwise page count, ordering, pixels/planes, metadata, validation and device readability).
- [ ] Test normal downloaded and quick online workflows, offline handling, missing pages, retries and cancellation.
- [ ] Test all device profiles, custom resolutions, long-webtoon slicing, RTL/LTR, cover recovery and large manga.
- [ ] Document intentional platform differences; never silently omit an Android setting.
- [ ] Require both Android and Windows CI conversion suites to pass before shipping a desktop release.

## 2. Source-verified feasibility findings

### Existing MangaNooir code

Current repo [`toshio2011/mihon-nooir`](https://github.com/toshio2011/mihon-nooir) overlays Mihon Android source at build time. It is **not currently a desktop Gradle application**. The following files in `overlay/app/src/main/java/dev/nooir/bridge/` use Kotlin/JVM types without Android imports and are promising for a shared module:

| File | Desktop use | Notes |
|---|---|---|
| [XtcCodec.kt](../overlay/app/src/main/java/dev/nooir/bridge/XtcCodec.kt) | Direct | JVM XTC/XTCH writer, quantized page and metadata types |
| [Epub2Writer.kt](../overlay/app/src/main/java/dev/nooir/bridge/Epub2Writer.kt) | Direct | EPUB 2.0.1 packaging and cover/NCX |
| [BookArchiveWriter.kt](../overlay/app/src/main/java/dev/nooir/bridge/BookArchiveWriter.kt) | Direct | EPUB 3 image books and CBZ |
| [GrayscaleConverter.kt](../overlay/app/src/main/java/dev/nooir/bridge/GrayscaleConverter.kt) | Direct | 1-bit/2-bit dithering |
| [PageSlicePlanner.kt](../overlay/app/src/main/java/dev/nooir/bridge/PageSlicePlanner.kt) | Direct | spread, webtoon, overlap and orientation geometry |
| [NooirDeviceProfiles.kt](../overlay/app/src/main/java/dev/nooir/bridge/NooirDeviceProfiles.kt) | Direct | X3/X4/X4 Pro and other e-reader profiles |
| [ReflowEpubWriter.kt](../overlay/app/src/main/java/dev/nooir/bridge/ReflowEpubWriter.kt) | Direct | mixed-content EPUB 2/3 ZIP writer |
| [PdfReflowLines.kt](../overlay/app/src/main/java/dev/nooir/bridge/PdfReflowLines.kt) | Direct | PDF text line grouping logic |

These should be extracted into `nooir-core` **with existing fixtures and Android regression tests first**; they are code-reuse candidates, not proof that desktop binaries are already built.

**Adapter/refactor required:**
- `mangabridge/AndroidPageSource.kt` uses Android Bitmap/ContentResolver/SAF; implement JVM file/ZIP and BufferedImage/ImageIO (or tested imaging library) equivalents.
- `mangabridge/MihonNooirExporter.kt` couples source fetch, downloaded chapter lookup, bitmap processing and output to Mihon Android; extract a neutral `ChapterPageProvider` and `ExportJob` interface, preserve metadata and ordering.
- `mangabridge/ImportedMangaConverter.kt` mixes portable archive logic with Android Context/Uri/Bitmap; split input/renderer from conversion core.
- `bridge/NooirUpload.kt` contains reusable HTTP protocol concepts but imports Android Context/Uri, `org.json` and a manga-bridge guard; create `nooir-transfer-jvm` with pure Java/Kotlin HTTP and filesystem paths, bounded streaming and receiver ACK.
- `mangabridge/NooirPdfToEpub.kt` uses PdfRenderer, Android PDFBox and Android graphics; use Apache PDFBox desktop and a JVM renderer while preserving best-effort text/illustration fallback.
- `NooirMangaExportWorker.kt`, queue dialogs and Android UI are WorkManager/Compose-Android-specific; implement desktop job service and native UI separately.
- Web article parsing uses Jsoup and mostly JVM APIs, but `org.json`, network security and callers need compatibility/security tests.

### Manga browsing and chapter downloading: don't port Android extension loader

[Mihon's `ExtensionLoader.kt`](https://github.com/mihonapp/mihon/blob/main/app/src/main/java/eu/kanade/tachiyomi/extension/util/ExtensionLoader.kt) imports Android `Context`, `PackageManager` and a Dalvik class loader. APK extension execution cannot be assumed to work in an ordinary Windows JVM.

**Proven alternative:** [Suwayomi-Server](https://github.com/Suwayomi/Suwayomi-Server) explicitly supports executing Mihon/Tachiyomi extensions on desktop, source browsing/search, chapter downloads, offline library, automatic updates, backups and tracking. [Suwayomi-WebUI](https://github.com/Suwayomi/Suwayomi-WebUI) demonstrates desktop reader and download queue UX. Its backend exposes GraphQL source, manga, chapter and download operations; the code includes [`SourceQuery.kt`](https://github.com/Suwayomi/Suwayomi-Server/blob/master/server/src/main/kotlin/suwayomi/tachidesk/graphql/queries/SourceQuery.kt) and [`DownloadMutation.kt`](https://github.com/Suwayomi/Suwayomi-Server/blob/master/server/src/main/kotlin/suwayomi/tachidesk/graphql/mutations/DownloadMutation.kt), including `enqueueChapterDownloads` and `dequeueChapterDownloads`. This is a **feasible integration path, not yet validated against a specific released server build**.

Suwayomi README describes Windows packages and a Java 21+ server runtime; its [extension setup guide](https://github.com/Suwayomi/Suwayomi-Server/blob/master/docs/Getting-Extensions.md) says **no default extensions**: users add their own extension repository. Do not bundle unauthorized extensions or manga.

**Licensing:** Suwayomi code is MPL-2.0 (with separately credited Apache-2.0 components); preserve licenses, notices and source availability for covered modified files. Prefer using Suwayomi as a separately versioned bundled executable/process through its documented API rather than copying/relicensing internals. Review redistribution and bundled runtime licenses before release.

## 3. Architecture decision

**Preferred design: one desktop app, managed local Suwayomi backend, shared Nooir conversion core.**

```text
MangaNooir Windows GUI (Kotlin Compose Desktop candidate)
  |-- Library / Sources / Search / Manga Details / Chapter Selection
  |-- Reader (paged / double / webtoon / keyboard / progress)
  |-- Downloads (source jobs) / Export Activity (conversion jobs)
  |-- Local Files / Web to Reader / Devices / Settings
  |
  +-- MangaBackend interface -> Suwayomi GraphQL/HTTP client
  |     +-- managed local Suwayomi process (extension runtime,
  |         source browsing, chapters, downloads, library database)
  |
  +-- ConversionService -> nooir-core (pure Kotlin/JVM)
  |     +-- desktop image/PDF/archive input adapters
  |     +-- EPUB 2/3, XTC/XTCH, CBZ, metadata and profile writers
  |
  +-- DeviceTransfer -> nooir-transfer-jvm
  |     +-- Folio Nooir HTTP upload, preflight, ACK, errors
  |
  +-- DesktopJobStore -> persistent conversion/transfer queue
  +-- Local application preferences, diagnostics and backups
```

- **One user-facing application:** launch/stop the backend automatically; surface setup, errors and health in-app. Keep optional advanced **connect to existing Suwayomi server** mode.
- **Avoid owning the Suwayomi DB directly:** use its documented API for sources, manga, chapters, downloads and reading progress. Keep Nooir's conversion/transfer job store separate.
- **Stable IDs:** use `backend-instance + sourceId + mangaId + chapterId` for internal jobs; never assume Mihon Android and Suwayomi DB row IDs match.
- **Download → export bridge:** resolve selected chapter image streams/files through a tested Suwayomi API/endpoint or an explicitly configured downloaded-files path; normalize to `ChapterPageProvider`, preserve source headers/metadata, stream one image/page at a time. Verify API contracts and output ordering in the prototype.
- **Security:** local backend should bind only loopback by default; configure authentication/token where supported, avoid exposing to LAN; isolate server storage, restrict extensions to user-approved repositories, guard redirects and filenames, and never transmit private auth tokens to the XTEink receiver.
- **Offline:** downloaded manga and local conversions must remain usable without network; online browsing requires network and working source extensions.
- **Updates:** pin Suwayomi version and verify checksums/signatures; test compatibility on updates; desktop and backend versions may differ.

### UI choice to prototype, not assume

**Preferred candidate:** Kotlin + Compose Desktop, sharing JVM types and integrating Suwayomi through a typed GraphQL client; use `jpackage` and a bundled Java runtime for a Windows installer. Native-feeling desktop UX, but implementing reader/library screens is significant work.

**Fallback to compare in a spike:** Tauri/Electron + a WebUI-derived or original TypeScript UI with a JVM conversion sidecar. Suwayomi-WebUI already covers many reader features, but direct reuse requires a deliberate license, design, build-size and integration assessment. **Do not claim the Compose choice is final until both startup/packaging and chapter-reader spikes work.**

## 4. Desktop UX and screens

Suggested left navigation: **Home · Library · Browse · Downloads · Reader (contextual) · Convert & Export · Devices · History · Settings**.

- **Home:** continue reading, recently updated chapters, downloads, recent exports, saved XTEink device.
- **Browse:** user-added extension stores, installed sources, popular/latest, search, source filters, manga cards and source status. Handle missing/untrusted/broken extensions visibly.
- **Manga details:** cover, description, metadata, chapter table with multi-select, downloaded/read state, download and export buttons. Allow all/selected/unread/range, chapter ordering and volume grouping.
- **Reader:** offline-first, keyboard arrows/PageUp/PageDown, mouse wheel, single/double page, webtoon, RTL/LTR, zoom, fullscreen, bookmarks and progress. Remember per-title options.
- **Downloads:** dedicated chapter download queue (backend-owned), retry/cancel/priority and download location/space checks.
- **Convert & Export:** input from downloaded chapters or local files; output format/profile/settings/preview, safe naming, save folder or Wi-Fi send. Separate conversion queue from chapter download queue while showing unified activity summary.
- **Devices:** XTEink IP/host, trusted LAN, device health/handshake, destination browser, file conflict prompts, transfer result.
- **Settings:** storage, extension stores/trust, server health, update channel, network restrictions, backups, logs, language/theme.

## 5. Implementation milestones (not started)

### D0 — Feasibility proof (isolated developer CLI, not a desktop release)
- [ ] Pin and install a released Suwayomi Windows/server build in a disposable environment.
- [ ] Confirm localhost-only backend lifecycle, auth, first-run onboarding, extension store, browse/search, manga details, chapters and a real authorized download.
- [ ] Verify GraphQL schema and downloaded-page retrieval **against a real released Suwayomi server**; mock contract tests for manga, chapters, download mutation and chapter CBZ endpoint are being added; capture success, rate limit, offline and extension failure cases.
- [x] Spike a Kotlin/JVM converter CLI from existing `bridge/` sources and validate format/metadata fixtures (PR #23); exact Android image pixel/hash parity remains open.
- [ ] Compare Compose Desktop vs web frontend + sidecar using real UI, packaging and license criteria.
- **Gate:** one end-to-end *source → chapter download → XTC/EPUB2 export → readable file* demonstration on Windows.

### D1 — Shared conversion core (after Android stabilization)
- [ ] Create `nooir-core`, `nooir-desktop-io`, `nooir-transfer-jvm` modules; keep Android overlay API compatibility and a shared settings schema with the complete Android conversion workflow.
- [x] Compile the shared Kotlin codecs on JVM, validate synthetic EPUB2/3, XTC, XTCH and CBZ and keep Android APK + EPUBCheck CI green (PR #23). [ ] Android-vs-Java2D image pixel parity and memory budgets remain.
- [ ] Port image/PDF adapters; no `android.*` imports in shared modules.
- **Gate:** Android build stays green; desktop CLI produces valid EPUB2/3, XTC/XTCH, CBZ from fixtures.

### D2 — Desktop shell and backend integration
- [ ] Package desktop shell and managed backend with a reproducible Windows build, first-run setup, local data dir and clean shutdown.
- [ ] Implement backend health/version checks, source/extension setup, browse/search, manga details, library and chapter selection.
- **Gate:** user launches one app and browses a user-added source without terminal/server setup.

### D3 — Downloads and full reader
- [ ] Chapter download queue and persistence, download all/selected/unread, progress, retry, source throttling and disk safety.
- [ ] Offline chapter reader, page modes, progress/bookmarks and shortcuts.
- [ ] Reopen downloaded manga offline after app restart.
- **Gate:** end-to-end manga discovery, download and offline reading works on Windows.

### D4 — Export, local documents and e-reader sending
- [ ] Export downloaded selected chapters to all five formats, with metadata/cover, split volumes, settings and output preview; meet the Android/Windows conversion parity matrix in §1A.
- [ ] Local file import and PDF mixed-content conversion; reuse web-article conversion where tested.
- [ ] Background conversion/transfer queue, XTEink local upload with ACK, conflict guard and receiver test.
- **Gate:** one manga downloaded from source and sent as XTC/XTCH/EPUB to an X4; other formats pass fixtures.

### D5 — Public Windows release readiness
- [ ] Windows x64 installer and portable build, bundled runtime, upgrade/uninstall without data loss.
- [ ] CI: Kotlin/JVM unit tests, fixture/EPUBCheck, backend API contract tests, Windows build/install/launch smoke tests.
- [ ] Security: backend localhost isolation, extension trust, dependency/redistribution license audit, signed binaries if available.
- [ ] Accessibility, DPI/scaling, keyboard navigation, error messages, storage cleanup and crash recovery.
- [ ] Public release notes, guide and clear supported/unsupported source disclaimer.
- **Gate:** independent users can install, browse, download, read, export and send without manual setup.

### D6 — Later improvements
- [ ] Android/Windows reading-state sync only after explicit data-model compatibility design; Suwayomi's Mihon integration may help, but does **not** automatically synchronize Nooir export jobs, saved files or every library state.
- [ ] Multi-device/remote server mode, additional reader transfer protocols, full metadata editing, cross-platform UI consistency.
- [ ] macOS/Linux packaging only after Windows core stabilizes.

## 6. Acceptance matrix

| Flow | Required proof |
|---|---|
| User adds a source | Source is visible and trustworthy, failures explainable |
| Search and browse | Manga details and chapter list load with correct metadata |
| Download selected chapters | Progress, retries, pause/cancel and complete on-disk pages |
| Offline reading | Fully downloaded chapter opens after restart without network |
| Reader progress | Resume page/chapter, reading order and bookmark persist |
| Export to XTC/XTCH | First cover, chapters, page count, 1/2-bit and device geometry correct |
| Export to EPUB 2/3 and CBZ | EPUBCheck, cover, TOC, readable pages, native metadata |
| Local PDF conversion | Selectable text and inline diagrams retained when extractable; scans fallback |
| XTEink upload | Conflict handling, no silent overwrite, receiver ACK, physical X4 opens output |
| Installer | Fresh install, update, uninstall/reinstall preserve data; backend stops cleanly |
| Security | Loopback-only by default; no bundled unauthorized sources or content |
| Android regression | No broken MangaNooir Android export/build due to shared-core extraction |

## 7. Risks and decisions to validate

1. **Backend distribution/version compatibility:** Suwayomi and its Android-compatibility shim are nontrivial; pin a release and test its extension runtime rather than fork it casually.
2. **Extension trust and availability:** repositories and sources are external, user-managed, may fail, require login or block automated access. No promises of universal compatibility, no DRM/paywall bypass.
3. **API and page access:** chapter image retrieval/download path and library operations need schema verification against a pinned Suwayomi version before binding to our converter.
4. **Two queues and storage:** backend owns manga downloads; MangaNooir owns export/transfer jobs. Don't corrupt backend storage or double-download pages.
5. **App footprint:** bundled backend + Java runtime + UI may be sizable; measure startup, idle memory, disk and packaging before locking GUI technology.
6. **License/attribution:** Suwayomi MPL-2.0, included Apache-2.0 components, Mihon and third-party dependencies require review for binary redistribution and modifications.
7. **Source terms/copyright:** user-supplied extensions only; no bundled copyrighted content or unapproved provider catalog.
8. **Cross-device synchronization:** don't assume a desktop Suwayomi library is the same database as MangaNooir Android; use explicit export/import or future sync protocol.
9. **Release sequencing:** official Android v1.0.12 exists but hardware validation/stable signing remain incomplete. Desktop implementation should not delay that work.

## 8. Reference sources inspected

- [MangaNooir bridge source](../overlay/app/src/main/java/dev/nooir/bridge/) and [Android adapters](../overlay/app/src/main/java/dev/nooir/mangabridge/)
- [MangaNooir main roadmap](https://github.com/toshio2011/mihon-nooir/issues/8)
- [Mihon Android extension loader](https://github.com/mihonapp/mihon/blob/main/app/src/main/java/eu/kanade/tachiyomi/extension/util/ExtensionLoader.kt)
- [Mihon Android chapter downloader](https://github.com/mihonapp/mihon/blob/main/app/src/main/java/eu/kanade/tachiyomi/data/download/Downloader.kt)
- [Suwayomi-Server README](https://github.com/Suwayomi/Suwayomi-Server)
- [Suwayomi extension setup](https://github.com/Suwayomi/Suwayomi-Server/blob/master/docs/Getting-Extensions.md)
- [Suwayomi GraphQL source query](https://github.com/Suwayomi/Suwayomi-Server/blob/master/server/src/main/kotlin/suwayomi/tachidesk/graphql/queries/SourceQuery.kt)
- [Suwayomi GraphQL download mutation](https://github.com/Suwayomi/Suwayomi-Server/blob/master/server/src/main/kotlin/suwayomi/tachidesk/graphql/mutations/DownloadMutation.kt)
- [Suwayomi-WebUI features](https://github.com/Suwayomi/Suwayomi-WebUI)
- [Suwayomi MPL-2.0 license](https://github.com/Suwayomi/Suwayomi-Server/blob/master/LICENSE)

**Bottom line:** Full Windows MangaNooir is achievable without porting Android itself: integrate a pinned desktop-compatible manga backend, reuse Nooir's JVM conversion core, and build a real desktop interface. The critical unknown is the **verified end-to-end backend download → export bridge**; do that spike before committing to the full UI.

## 22 October 2026 — D0 Suwayomi localhost bridge implementation slice

- The D1 conversion CLI was merged in [PR #23](https://github.com/toshio2011/mihon-nooir/pull/23); the Android APK and shared Kotlin tests passed in [run #39755714615](https://github.com/toshio2011/mihon-nooir/actions/runs/39755714615).
- **D0 follow-up implementation:** `desktop/suwayomi/SuwayomiClient.kt` uses the **Suwayomi-Server v2.4.2366** GraphQL manga/chapter fields, `enqueueChapterDownloads`, and the `/api/v1/chapter/{chapterId}/download` CBZ REST endpoint. `SuwayomiToNooir.kt` accepts explicit chapter IDs, verifies download status, optionally enqueues missing chapters on request, extracts image pages safely, and invokes the shared EPUB2/3/XTC/XTCH/CBZ CLI. Mock HTTP tests exercise downloaded and newly enqueued chapters.
- **Do not mark D0 complete until tested with an actual released Suwayomi server** and a user-approved, lawfully accessed source chapter. No bundled extensions, manga, remote server connections, or automated backend process management are present.
- Android parity, GUI, reader, PDF/web conversion, Windows packaging, update signing and the full source browsing workflow remain independent open tasks.

## 23 October 2026 — D0.2 source discovery and browsing developer slice

- Implemented **source discovery** from Suwayomi GraphQL `sources(first, offset)`, preserving signed 64-bit IDs, languages, display names, latest support and content warnings.
- Implemented **paginated POPULAR/LATEST/SEARCH** from `fetchSourceManga`, with GraphQL-safe escaped search terms, manga title/author/thumbnail metadata and server-library status.
- Added **explicit** `fetchMangaAndChapters` refresh for newly discovered manga; chapter lists can then supply selected chapter IDs to the already-merged `SuwayomiToNooir` conversion pipeline.
- Added a CLI, mock HTTP contract tests, safe pagination/argument limits and user documentation.
- **Still unverified:** real released Suwayomi server compatibility and extensions, GUI browsing/cover display, source installation/management, Windows packaging, full reader and automated downloads in the GUI.
- The user-facing Windows app is **not yet available**. See `desktop/README.md`.

## 23 October 2026 — Consolidated D0.2 source browser and library

- [PR #25](https://github.com/toshio2011/mihon-nooir/pull/25) includes installed source discovery, **signed LongString source IDs** (negative IDs allowed), source popular/latest/search with GraphQL-safe search terms, explicit chapter refresh, and a paginated **Suwayomi library** listing.
- The separate [PR #26](https://github.com/toshio2011/mihon-nooir/pull/26) overlaps; consolidate rather than merging conflicting source browser implementations.
- [ ] Verify consolidated mock contract tests and Android FOSS APK CI.
- [ ] Validate a **real** Suwayomi v2.4.2366 installation and user-configured source.
- [ ] Implement graphical Windows browsing/reading, backend lifecycle and packaged installer.

This does **not** import Mihon backups or synchronize Android library entries, and no third-party extensions or manga are bundled.
