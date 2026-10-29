> **Windows full-product work (29 October 2026):** Development is active on [`feature/windows-full-v1.0.16`](https://github.com/toshio2011/mihon-nooir/tree/feature/windows-full-v1.0.16). It restores the GUI prototype onto the tested Android v1.0.16 conversion engine, exposes **Panel by Panel, Row by Row, Auto Fit, full-page overview and first-chapter-page preservation**, and adds **local image-folder/CBZ conversion and a basic offline comic reader** (natural page ordering, fit/zoom, arrow keys, and saved position) that do not need Suwayomi. The Windows app is **not yet fully released**: the managed backend, real source test, reader, queues, Wi-Fi transfer and installer are development milestones. See [the full Windows implementation plan](../docs/WINDOWS_FULL_IMPLEMENTATION_PLAN.md). Preserve Android source and safety gates.


## Current Windows full-app development preview: local reading and library

**Reader progress (this branch):** Local CBZ and image-folder reading now supports single page or optional **two-page spreads** with the first page kept alone, RTL/LTR visual placement, page jumping, zoom, fit-page/fit-width/original size and persistent reading position for each book. The downloaded-series reader exposes **Previous chapter / Next chapter** inside the reader, skipping missing local CBZs without automatically downloading anything. Use Ctrl+PageUp / Ctrl+PageDown between available chapters, and Left/Right or PageUp/PageDown between pages. Continuous webtoon/fullscreen and online chapter reading are still open milestones; this is not yet the full stable reader.

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

# MangaNooir Windows — D1 shared conversion spike (developer-only)

This is the **first implementation increment**, not the Windows desktop app or a Windows installer. It proves that the same Kotlin/JVM conversion codecs and MangaNooir settings can run on desktop without Android APIs. It now also includes a **D0 developer-only Suwayomi localhost integration** for listing chapters and exporting selected downloaded chapters. It does **not** include a managed Suwayomi backend, source browsing UI, Mihon backup restore, a GUI, PDF or web conversion.

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

Formats: `AUTO`, `XTC`, `XTCH`, `EPUB` (EPUB 3), `EPUB2` and `CBZ`. Input: a folder of image files (chapter subfolders are grouped as chapters) or a CBZ/ZIP of image files. Optional `--options-token` accepts the **same token** emitted by MangaNooir Android's `MangaConversionOptions.token()` (including older job-token versions). A root-level `cover.png` is treated as a separate cover. Existing output files are rejected; files are written to a temporary sibling and moved only after a successful conversion.

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

Build the same Kotlin JVM source set used by the D0 bridge, including `desktop/suwayomi/SuwayomiBrowseCli.kt` (or compile `desktop/suwayomi/*.kt`). Use an already-running localhost Suwayomi instance with your own configured extensions. MangaNooir does not bundle sources or copyrighted content.

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
