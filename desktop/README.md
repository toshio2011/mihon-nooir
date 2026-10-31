## Verified local CBZ-copy progress and disk safety (Windows preview)

The **Offline CBZ copy jobs** screen now shows the actual bytes downloaded **from the already-running local Suwayomi server into the Windows library** (MiB, and percent only if the HTTP response includes a valid Content-Length). This is **not** progress for remote manga source downloads. Before creating a local CBZ, the downloader checks the response size (400 MiB default maximum) and available disk space with a 32 MiB reserve, then monitors disk availability as it streams. A file is created exclusively without overwriting an existing destination; failed, oversized, low-space, interrupted and incomplete copies remove their own partial file. The existing offline saver still validates image pages and publishes an archive only after completion. Mock-server regression tests cover size, disk preflight, exclusive-file behavior, real byte callbacks and partial-file cleanup. Actual Suwayomi/Windows testing remains open.

## Synthetic cross-workflow acceptance (Windows preview)

A headless localhost-only acceptance test now exercises the joined path: completed Suwayomi request records → explicit offline-copy handoff → persistent CBZ job completion → reload grouped library after restart → Continue Reading → EPUB 2 export **from the local CBZ**. It rejects duplicate source submissions and asserts that reopening and converting offline cause no new manga-source or CBZ requests. This is synthetic contract coverage, **not** proof against a real released Suwayomi server or an actual Windows display.

## Direct conversion from your saved bookshelf (Windows preview)

In **Local library**, select any available local CBZ, ZIP or image folder and choose **Convert selected**. Pick EPUB 2, EPUB 3, XTC, XTCH or CBZ, then choose the target e-reader profile and the **same conversion settings** used elsewhere in Windows. Output is written in the background using the existing Android-shared Kotlin conversion engine; the app rejects existing filenames instead of silently replacing them. This feature needs **no Suwayomi process, remote source, or internet**. The Windows adapter intentionally keeps the unsupported diagnostic ZIP option off, and real hardware/pixel parity testing remains open.

## Downloaded-series cover previews (Windows preview)

**Downloaded series** now previews the first available saved chapter as a small cover image next to the series and chapter lists. Preview decoding runs on a worker thread, uses the same bounded and path-safe reader as the local bookshelf, and never contacts Suwayomi or downloads missing chapters. A missing or unreadable local CBZ displays a clear placeholder; selecting another series cancels/invalidates the older preview. A real Windows UI smoke test is still required.

## Offline bookshelf search and cover preview (Windows preview)

**Local library** now has a **Find manga** field filtering the user's saved local entries (case-insensitive), and an on-demand **cover preview** of the selected CBZ or image folder. A named `cover.jpg` / `cover.png` wins over the natural first page; otherwise the first readable page is used. The image is decoded off the Swing UI thread, scaled to a small bounded thumbnail, and invalidated if the user selects a different book. No background downloads, cloud artwork, automatic library scans, persistent thumbnail copies, or deletions. Missing files display an explanation rather than making network requests. JVM tests cover local filtering, cover precedence and bounded preview dimensions; actual Windows visual quality still needs on-device validation.

## Continue Reading for downloaded series (Windows preview)

From **Local library**, click **Continue last downloaded manga** to reopen the most recently read available CBZ chapter in one step. Alternatively, open **Downloaded series**. The new **Start / Continue selected series** button opens the last-read available chapter for that series, falling back to its first available downloaded chapter if it has not been read yet. **Continue last read** opens the most recently read available chapter across all downloaded series. The series list shows which chapter is currently resumable.

This uses the existing atomic local-library history and the reader's independent saved per-file page position; it does **not** introduce a second reading database, contact Suwayomi, download missing chapters, or change Android. Missing CBZs are skipped; the source catalog and original files remain unchanged. Synthetic JVM regression covers first-time reading, persisted history after restart, multiple series and missing-file fallback. The real Windows UI still needs click-through testing.

---
 
## Offline Windows reader: explicit bookmarks and fullscreen (preview)

The local CBZ/folder reader and saved offline series now support **up to 100 explicit bookmarks per comic** without replacing the separate last-read position. Use **Bookmark page** or Ctrl+B to add/remove a saved page; **Bookmarks...** lists that comic's saved pages, with actions to jump or remove. Bookmarks persist under `~/.mihonnooir/reader-bookmarks-v1.tsv` as **hashed local file paths**, not the user's filenames or comic pages. Moving or renaming a comic changes its bookmark identity until a migration/repair feature is added. Corrupted bookmark indexes are preserved for manual repair, never silently overwritten.

Use **Fullscreen (F11)** to enter/leave fullscreen reading; **Esc** leaves fullscreen without closing the comic. The reader's toolbar is split into navigation and display-mode rows for smaller Windows displays. Bookmark persistence has standalone Kotlin regression tests; fullscreen and webtoon behavior still require actual Windows display/keyboard testing before production release.

> **Windows full-product work (29 October 2026):** Development is active on [`feature/windows-full-v1.0.16`](https://github.com/toshio2011/mihon-nooir/tree/feature/windows-full-v1.0.16). It restores the GUI prototype onto the tested Android v1.0.16 conversion engine, exposes **Panel by Panel, Row by Row, Auto Fit, full-page overview and first-chapter-page preservation**, and adds **local image-folder/CBZ conversion and a basic offline comic reader** (natural page ordering, fit/zoom, arrow keys, and saved position) that do not need Suwayomi. The Windows app is **not yet fully released**: the managed backend, real source test, reader, queues, Wi-Fi transfer and installer are development milestones. See [the full Windows implementation plan](../docs/WINDOWS_FULL_IMPLEMENTATION_PLAN.md). Preserve Android source and safety gates.


## Offline copy job queue cleanup (Windows preview)

The offline CBZ-copy queue is bounded at **500 records**. In **Offline CBZ copy jobs**, the **Clear finished/canceled records** button reclaims only locally **COMPLETED** or **CANCELED** history for the currently selected localhost server **after confirmation**. It does not delete downloaded chapters, saved CBZ files, series metadata, source files or pending/retryable/in-flight operations. **FAILED**, **INTERRUPTED**, **RUNNING** and **PENDING** jobs are retained so that uncertain work cannot silently disappear. Clearing history is optional and never runs at app startup. Synthetic tests cover a full 500-job index, server isolation, safe retention, persistence and capacity recovery.

## Safe queue-space cleanup (Windows preview)

The remote request index currently caps at **500 records across the server's series**. In **Download requests**, select 1–20 entries in the **PENDING** (never submitted), **DOWNLOADED** or **CANCELED** state, then click **Forget safe queue records** and confirm. This reclaims queue capacity **without deleting files or telling Suwayomi to cancel or delete anything**. To prevent accidental duplicate remote downloads, **SUBMITTING, WAITING, FAILED and INTERRUPTED records cannot be forgotten** by this action; use status-only rechecks instead. Completed requests are safe to forget from the local queue after any desired offline CBZ handoff.

## Bounded Download All and optional offline job handoff (Windows preview)

With a manga selected and its chapters loaded, choose **Queue all missing (max 500)** to store pending requests for **every currently loaded, not-yet-downloaded chapter**. The request index is atomic: if the 500-job total capacity is exceeded, it refuses the whole selection rather than saving an incomplete subset. Previously queued chapters are reused, and a second click cannot create duplicates. No manga source is contacted or download started when queuing. This is an intentional bounded preview, **not** an unlimited server downloader; chapter data must have been loaded in the desktop GUI first.

Open **Download requests**, then explicitly approve **Submit pending**, which handles up to **20 requests per approval** to limit source pressure. For a series of more than 20 chapters, approve subsequent batches separately. Downloads still use actual Suwayomi `isDownloaded` checks, not made-up byte percentages. On this screen you may opt into **Also prepare offline CBZ jobs after verified downloads**; it is off by default. This creates persistent copy *requests* only for verified completed downloads. It does **not** start an offline CBZ copy; use **Offline CBZ copy jobs → Run pending** for a separate confirmation. This also applies to explicit **Recheck selected** status checks. Closing the window stops monitoring, not server-side downloads. Real released-server and physical Windows validation remain required.

## Completed Suwayomi downloads → offline CBZ copy jobs (Windows preview)

In **Download requests**, select **1–20 DOWNLOADED** chapter requests (Ctrl/Shift select), then click **Queue selected as offline CBZ**. Confirm to create persistent CBZ-copy jobs, preserving the manga/chapter grouping. This action **does not contact Suwayomi, start any source download or copy CBZ files**; repeated clicks reuse pending/completed jobs. Open **Offline CBZ copy jobs** and explicitly click **Run pending** to begin validating and saving CBZs. Chapters still pending, waiting, failed or interrupted in the remote queue cannot be queued via this shortcut.

**Stop before submission:** If monitoring is stopped *before* the chapter enqueue mutation is attempted, the request returns to PENDING and needs a new explicit Submit pending approval. If an enqueue was attempted, interrupted/failed requests still **only recheck status** and must not blindly resubmit. No remote cancellation or percentage progress is claimed. These boundaries are covered by headless Windows-only tests; real Windows/Suwayomi integration is still outstanding.

## Suwayomi remote chapter requests (experimental Windows-only)

For chapters **not yet downloaded in Suwayomi**, select up to 20 chapters and choose **Queue missing chapter downloads**. This creates local requests only; it does **not** contact a manga source. Open **Download requests**, then explicitly approve **Submit pending** to ask your own localhost Suwayomi server to download them. MihonNooir checks each chapter's `isDownloaded` status to distinguish *waiting* from *fully downloaded*, and avoids enqueuing a chapter that is already complete.

These requests are stored under `~/.mihonnooir/remote-chapter-queue-v1.tsv` with atomic writes and no credentials. After a crash, in-flight requests are marked **INTERRUPTED**; **Recheck selected** checks only the server's status and never silently submits the same chapter again. **Cancel pending request** only removes an unsent request. **Stop monitoring does not cancel Suwayomi's source download**, which may keep running independently. There is no invented percent-progress or remote cancellation support.

When Suwayomi has finished a chapter, use **Queue downloaded chapters** / **Offline jobs** to save it as a local CBZ for completely offline reading. This is a first implementation with mock GraphQL contract tests, **not yet real Suwayomi Windows-tested** or a Download All manager with server-wide progress and cancellation.

## Persistent offline chapter-copy queue (development preview)

The Windows desktop now provides **Queue downloaded chapters** and **Offline jobs** alongside the existing immediate save action. The queue accepts only chapters already marked downloaded by a user-configured *localhost* Suwayomi server. Adding jobs does not contact the server. Select **Run pending** to explicitly start one CBZ copy at a time; **Pause after current chapter** stops between items, **Retry selected** retries a failed/interrupted item, and **Cancel selected** discards a pending job record without deleting any downloaded comic.

Jobs are stored in `~/.mihonnooir/offline-save-queue-v1.tsv` using bounded, versioned atomic writes; there are no stored credentials. A process crash leaves in-flight jobs marked **INTERRUPTED** rather than silently retrying network operations. Reopening the queue shows saved status; the user must manually retry interrupted jobs. Re-imports reuse validated CBZ files without re-fetching them, and completed jobs are deduplicated. The queue intentionally does *not* initiate missing manga chapter downloads from third-party sources. It is an **offline copy queue**, not yet a full Suwayomi remote-download manager with remote progress reporting/cancellation. Real large-scale Windows user testing is pending.

## Current Windows full-app development preview: local reading and library

**Webtoon reader preview:** In the offline reader, select **Webtoon scroll** for a continuous top-to-bottom view. It decodes a rolling maximum of three images (up to 36 million source pixels total) rather than loading all chapter images, tracks the visible page for resuming, and supports jumping using the page spinner. The rendering and memory model have automated coverage; smooth boundary transitions and extremely tall pages still require real Windows testing before treating this mode as finished.

**Reader progress (this branch):** Local CBZ and image-folder reading now supports single page or optional **two-page spreads** with the first page kept alone, RTL/LTR visual placement, page jumping, zoom, fit-page/fit-width/original size and persistent reading position for each book. The downloaded-series reader exposes **Previous chapter / Next chapter** inside the reader, skipping missing local CBZs without automatically downloading anything. Use Ctrl+PageUp / Ctrl+PageDown between available chapters, and Left/Right or PageUp/PageDown between pages. Webtoon scrolling now has an experimental bounded implementation. Fullscreen, online chapter reading, real Windows large-file validation and polishing remain open; this is not yet the full stable reader.

On the development branch, the Windows app opens **offline by default**. **Local library** opens a persistent user-curated bookshelf. Add an image folder or CBZ archive, open a book for single-page reading with fit/zoom and RTL-aware arrow keys, and resume its last position after restarting the app. Removing a library entry does **not** delete the original comic. The index is stored in the user's home directory (`~/.mihonnooir/local-library-v1.txt`); comic pages stay where the user chose them.

**Read local comic** opens a folder/CBZ directly, and **Convert local folder / CBZ** exports to EPUB2, EPUB3, XTC, XTCH or CBZ using the same tested Kotlin conversion engine and panel settings as Android. These workflows do not need a running Suwayomi backend.

From **Local library → Downloaded series**, readers can now browse saved chapters grouped under their original Suwayomi manga title and chapter order, open the selected chapter offline, and remove a series-index entry without deleting the original CBZ. The chapter grouping index is stored separately in `~/.mihonnooir/offline-series-v1.tsv`. CBZs saved by older previews still appear in the normal local library; grouping metadata is created when a chapter is imported or re-imported using this branch.

When connected to a user-configured localhost Suwayomi server, select chapters that Suwayomi already downloaded and press **Save downloaded chapters offline**. Up to 20 chapters per action are copied as validated CBZ archives into `~/.mihonnooir/downloaded-chapters/` and added to the local library. This never installs sources, enqueues missing remote downloads or overwrites archives. Real released Suwayomi integration, per-chapter progress/cancellation and the managed-backend packaging still need verification and implementation.

### Managed Suwayomi process — opt-in development integration

The Windows development GUI now offers **Download & start Suwayomi** (optional), **Start local Suwayomi** (select an existing JAR), and **Stop managed server**. The download button asks for permission before fetching the **official pinned v2.4.2366 JAR (~183 MB)** from GitHub. It saves only a completed, SHA-256-verified JAR in `~/.mihonnooir/server-cache/`; interrupted, incorrect or oversized downloads are rejected without running. An already-verified cache is reused offline; an invalid cached file is never silently executed or overwritten. Both start paths still pass through `SuwayomiProcessManager`'s independent SHA-256 verification and owner-only process lifecycle.

The original file-selector start path starts only after explicit consent and a user-selected `Suwayomi-Server-v2.4.2366.jar`. For now, download that exact released JAR from the [official Suwayomi v2.4.2366 release](https://github.com/Suwayomi/Suwayomi-Server/releases/tag/v2.4.2366), verify its published checksum, then select it in the file dialog. **MihonNooir still does not bundle the server binary or install extensions; it downloads the official JAR only when the user explicitly approves.** This initial managed-process slice still needs validation with the real official JAR on Windows.

The launcher uses the Java runtime from the Windows app and passes Suwayomi system settings to bind `server.ip=127.0.0.1`, `server.port=4567`, and set a separate `server.rootDir` at `~/.mihonnooir/managed-suwayomi`. It disables external tray/browser auto-launch, captures bounded startup readiness via localhost GraphQL and writes local process output to `server-output.log`. It refuses to claim port 4567 if another process already uses it; **Stop managed server never kills an external server** and closes only the process MihonNooir itself started. The app remains usable offline without any backend.

**Still pending for a complete one-click application:** audit server redistribution/license, test a real released Suwayomi server and its startup/shutdown on Windows, create a user-approved extension/source setup flow, choose final bundling or verified-download distribution strategy, implement durable download jobs and a release installer. The opt-in download code has fixture-based checks; no successful real official-JAR startup has been claimed. Do not represent this developer integration as an automatic bundled backend yet.

**Status:** Windows portable preview is unsigned and under development; it is not yet the full stable Windows app or installer. The original Android release, signing keys, APK source and Folio Nooir firmware are unchanged.

# MihonNooir Windows — D1 shared conversion spike (developer-only)

> **Legacy command names:** The example developer JARs below retain `manganooir-cli.jar` and `manganooir-suwayomi.jar` as existing local command/output filenames. These are **not** the current product name; the app is **MihonNooir**. Keep the filenames unchanged if following the commands exactly.

This is the **first implementation increment**, not the Windows desktop app or a Windows installer. It proves that the same Kotlin/JVM conversion codecs and MihonNooir settings can run on desktop without Android APIs. It now also includes a **D0 developer-only Suwayomi localhost integration** for listing chapters and exporting selected downloaded chapters. It does **not** include a managed Suwayomi backend, source browsing UI, Mihon backup restore, a GUI, PDF or web conversion.

## Shared source — no duplicate codecs

The Windows CLI compiles these **unchanged Android overlay files directly**:

- `overlay/app/src/main/java/dev/nooir/bridge/`: XtcCodec, GrayscaleConverter, GrayscalePng, BookArchiveWriter, Epub2Writer, ExportFormats, NooirDeviceProfiles, PageSlicePlanner and NaturalPageOrder.
- `overlay/app/src/main/java/dev/nooir/mangabridge/MangaConversionSettingsCore.kt`: pure JVM MangaConversionOptions model, presets and legacy job-token migration.

Android's `MangaConversionOptions.kt` now only contains the Compose controls and SharedPreferences adapter; the conversion model is shared. Android's manga exporter and image source use the shared PNG writer and natural page ordering. The Android overlay build still copies the same files through `tools/apply_overlay.py`.

The CLI is a **JVM adapter** in `desktop/cli/NooirDesktopCli.kt`, using Java2D/ImageIO for image decoding and rendering. **It has not yet passed pixel-by-pixel parity validation against Android Bitmap**, so do not advertise bit-identical pages.

## Build and run (JDK 21+, kotlinc on PATH)

From the repository root:

```bash
mkdir -p build/desktop
kotlinc \
  overlay/app/src/main/java/dev/nooir/bridge/{XtcCodec.kt,ExportFormats.kt,NooirDeviceProfiles.kt,BookArchiveWriter.kt,Epub2Writer.kt,GrayscaleConverter.kt,GrayscalePng.kt,NaturalPageOrder.kt,PageSlicePlanner.kt} \
  overlay/app/src/main/java/dev/nooir/mangabridge/MangaConversionSettingsCore.kt \
  desktop/cli/NooirDesktopCli.kt \
  -include-runtime -d build/desktop/manganooir-cli.jar

java -Djava.awt.headless=true -cp build/desktop/manganooir-cli.jar dev.nooir.desktop.NooirDesktopCli \
  --input "/path/to/manga-image-folder" \
  --output "/path/to/output/book.xtch" \
  --format XTCH --device X4 --title "My Manga"
```

On Windows PowerShell, compile the same source files by listing paths explicitly (PowerShell does not expand Bash brace patterns), then:

```powershell
java -Djava.awt.headless=true -cp build/desktop/manganooir-cli.jar dev.nooir.desktop.NooirDesktopCli --input "C:\\Manga\\Chapter" --output "C:\\Exports\\Chapter.epub" --format EPUB2 --device X4
```

Formats: `AUTO`, `XTC`, `XTCH`, `EPUB` (EPUB 3), `EPUB2` and `CBZ`. Input: a folder of image files (chapter subfolders are grouped as chapters) or a CBZ/ZIP of image files. Optional `--options-token` accepts the **same token** emitted by MihonNooir Android's `MangaConversionOptions.token()` (including older job-token versions). A root-level `cover.png` is treated as a separate cover. Existing output files are rejected; files are written to a temporary sibling and moved only after a successful conversion.

Run automated checks:

```bash
python3 -m unittest discover -s tests -p "test_*.py" -v
bash tests/run.sh
```

## Proven by CI tests (once green)

- Same Kotlin export codec source is compiled for desktop and Android.
- EPUB 2, EPUB 3, XTC, XTCH, CBZ from synthetic manga images; page/chapter ordering and cover position.
- CBZ/ZIP input, no silent output overwrite, readable grayscale PNG, format metadata and shared legacy settings migration.
- Android overlay structure and full Android FOSS APK build remain regression gates.

## Limitations and next steps

- A Suwayomi localhost API bridge is available for existing downloaded chapters and explicitly requested backend downloads; **no embedded backend**, manga source browser, Mihon backup restore, Windows GUI or packaged EXE yet.
- ZIP and image files are bounded; desktop ImageIO codec support differs from Android, especially WebP. Password-protected archives, RAR, PDF and text EPUB are not inputs here.
- Image rendering is currently a Java2D approximation of Android's Bitmap canvas, not certified pixel parity. Cover resolution from online sources and Android preflight confirmations are not yet ported.
- Source files with huge dimensions may require more memory than intended; real-world batch, malformed image and crash-recovery testing remains.
- Next: validate against a **real released Suwayomi v2.4.2366 server** with a user-approved extension and lawful sample chapter, compare generated pages against Android fixtures, stabilize the image adapter and build the Windows GUI/managed backend only after the D0 gate.

Full roadmap: [Windows desktop feasibility and feature plan](../docs/WINDOWS_DESKTOP_FEASIBILITY_ROADMAP.md).

## D0: Suwayomi localhost → selected chapters → e-reader book

The `desktop/suwayomi/` adapter is pinned for contract research to [Suwayomi-Server v2.4.2366](https://github.com/Suwayomi/Suwayomi-Server/releases/tag/v2.4.2366). The implementation uses its documented GraphQL manga/chapter fields and `enqueueChapterDownloads` mutation, plus `GET /api/v1/chapter/{chapterId}/download?markAsRead=false` to obtain **downloaded chapter CBZ archives** without modifying read status. It extracts bounded image pages into temporary folders and invokes the **existing** shared MangaNooir conversion CLI. This integration has mock-server tests; **a real released Suwayomi instance has not yet been tested**.

### Setup

1. Install and run your own released Suwayomi server on `127.0.0.1:4567` (or another localhost port). Follow [Suwayomi's extension setup guide](https://github.com/Suwayomi/Suwayomi-Server/blob/master/docs/Getting-Extensions.md) and add **only** extension repositories you trust; MangaNooir bundles none.
2. In Suwayomi, add a manga and fetch its chapters. Note the **Suwayomi manga ID**, not an Android Mihon database ID.
3. Build the desktop CLI with the same shared sources as above, **plus** the three files under `desktop/suwayomi/`. For example, on Linux/macOS Bash:

```bash
kotlinc \
  overlay/app/src/main/java/dev/nooir/bridge/{XtcCodec.kt,ExportFormats.kt,NooirDeviceProfiles.kt,BookArchiveWriter.kt,Epub2Writer.kt,GrayscaleConverter.kt,GrayscalePng.kt,NaturalPageOrder.kt,PageSlicePlanner.kt} \
  overlay/app/src/main/java/dev/nooir/mangabridge/MangaConversionSettingsCore.kt \
  desktop/cli/NooirDesktopCli.kt desktop/suwayomi/*.kt \
  -include-runtime -d build/desktop/manganooir-suwayomi.jar
```

4. List chapter IDs and download state:

```bash
java -cp build/desktop/manganooir-suwayomi.jar dev.nooir.desktop.suwayomi.SuwayomiToNooir \
  --mode list --manga-id 123 --base-url http://127.0.0.1:4567
```

5. Export **explicitly selected downloaded chapters** (replace IDs, paths and output extension):

```bash
java -Djava.awt.headless=true -cp build/desktop/manganooir-suwayomi.jar dev.nooir.desktop.suwayomi.SuwayomiToNooir \
  --mode export --manga-id 123 --chapters 456,457 \
  --output /path/to/output/manga.epub --format EPUB2 --device X4
```

To request missing chapters from your already-configured Suwayomi source, add `--download-missing true --wait-seconds 180`. Without this flag, the command refuses to fetch missing chapters. It does not install extensions, search unapproved sources, or mark chapters read. Formats are `EPUB2`, `EPUB` (EPUB 3), `XTC`, `XTCH` and `CBZ`; device profiles and `--options-token` match the Android core. For a missing series cover, use an Android-generated options token with `allowMissingCover=true` or `includeCover=false`; there is **no silent fabricated cover**.

For a server requiring an Authorization header, set `NOOIR_SUWAYOMI_AUTHORIZATION` in the environment (e.g. `Bearer ...` or `Basic ...`) rather than passing credentials in a command argument. Never share credentials or run this early D0 client against a public endpoint; it deliberately permits **localhost only**.

**Limitations:** chapter list must already be populated in Suwayomi; server version/schema compatibility and auth modes beyond the supplied header need live verification; no automatic server launch, no real backend packaging, no online streaming of chapter pages, no download cancellation, no image-perfect Android/Java2D parity and no Windows EXE. ImageIO may lack WebP support; archives and image sizes are bounded, and temporary chapter data is cleaned after conversion.

Run the full mock contract and conversion tests with `bash tests/run.sh`.

## D0.2: Browse installed sources and find manga without knowing IDs

The developer-only `SuwayomiBrowseCli` uses Suwayomi-Server **v2.4.2366** `sources(first, offset)`, `mangas(condition: {inLibrary: true})`, `fetchSourceManga` (POPULAR/LATEST/SEARCH) and optional `fetchMangaAndChapters` GraphQL operations. Source IDs are **signed 64-bit values** (some are negative); manga IDs and chapter IDs are separate positive integers.

Build the same Kotlin JVM source set used by the D0 bridge, including `desktop/suwayomi/SuwayomiBrowseCli.kt` (or compile `desktop/suwayomi/*.kt`). Use an already-running localhost Suwayomi instance with your own configured extensions. MihonNooir does not bundle sources or copyrighted content.

```bash
# List installed sources; use --offset to page beyond the first 100.
java -cp build/desktop/manganooir-suwayomi.jar dev.nooir.desktop.suwayomi.SuwayomiBrowseCli \
  --mode sources --limit 100 --offset 0

# Browse your Suwayomi library, with pagination (not Android Mihon sync).
java -cp build/desktop/manganooir-suwayomi.jar dev.nooir.desktop.suwayomi.SuwayomiBrowseCli \
  --mode library --limit 100 --offset 0

# Browse popular manga from a selected source; signed source IDs are valid.
java -cp build/desktop/manganooir-suwayomi.jar dev.nooir.desktop.suwayomi.SuwayomiBrowseCli \
  --mode popular --source-id -900000000000000001 --page 1

# Search that source by title (search terms are escaped as GraphQL strings).
java -cp build/desktop/manganooir-suwayomi.jar dev.nooir.desktop.suwayomi.SuwayomiBrowseCli \
  --mode search --source-id -900000000000000001 --query "Example Manga" --page 1

# After choosing a manga ID, explicitly fetch its latest metadata and chapters.
java -cp build/desktop/manganooir-suwayomi.jar dev.nooir.desktop.suwayomi.SuwayomiBrowseCli \
  --mode chapters --manga-id 123 --refresh true
```

Replace example IDs with IDs returned by your own server. `--mode latest` is available only for sources reporting `supportsLatest=true`. Without `--refresh true`, chapter listing reads Suwayomi's existing cached chapters; newly discovered manga may have none until refreshed. Refreshing fetches source metadata and chapters and can make network requests; searching/browsing also contacts the selected source and may cache metadata on the Suwayomi server. Neither action downloads pages or changes reading progress. Library browsing lists titles already in **Suwayomi's own library**; Mihon backup restoration and automatic Android library synchronization remain unimplemented.

Once chapter IDs are displayed, the existing `SuwayomiToNooir --mode export --chapters ...` command converts the chosen chapters with the **same Android-compatible conversion writers**.

**Still pending:** test against a real released Suwayomi instance and user-approved source; a graphical source browser, cover grid, chapter selection UI, download manager, local server lifecycle and packaged Windows app. Automated fake-server tests verify request shapes, signed IDs, pagination, search escaping, explicit refresh, error handling and CLI output, but do not prove that all third-party extensions work.
