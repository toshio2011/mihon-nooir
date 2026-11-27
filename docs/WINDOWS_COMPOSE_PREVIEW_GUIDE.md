# MihonNooir — Windows Compose preview guide (0.1.0)

> **Experimental, not an official release.** Android v1.0.20 is unchanged. Keep the existing Swing preview and your original backup. The native Compose preview passed its CI regression and packaging gates, but physical Windows and XTEink acceptance remain open.

## 1. Download and launch

- [Windows portable ZIP (~305 MB)](https://github.com/toshio2011/mihon-nooir/actions/runs/45952282235/artifacts/15177533204) (GitHub sign-in required; CI artifacts expire).
- [Build and test evidence](https://github.com/toshio2011/mihon-nooir/actions/runs/45952282235)
- [Actual screenshots and decoded sample books](https://github.com/toshio2011/mihon-nooir/actions/runs/45952282235/artifacts/15176679885)

Extract the downloaded archive and the inner ZIP if present. Open the MihonNooir-Compose-Preview folder and run MihonNooir-Compose-Preview.exe. Keep the entire folder together: the UI runtime and backend-java folder are required. No separate Java installation is intended. This is an unsigned experimental portable build, not an installer or in-place upgrade.

For an isolated first test, open PowerShell in the extracted application folder and run:

```powershell
$env:MIHONNOOIR_DATA_DIR = 'D:\MihonNooirComposeTest'
.\MihonNooir-Compose-Preview.exe
```

Choose a new directory on an available drive. Close any existing local Suwayomi server first if you want a fully separate test: the app connects to an already running compatible service on port 4567, regardless of the data-directory override. Do not stop unrelated processes.

On first launch, choose **Set up MihonNooir** and explicitly approve the pinned official reading service download. Subsequent starts can reuse the verified cached server. No need to open the separate browser interface for the implemented workflows. If setup fails, keep your data and report the error. Local tools currently require the app to reach its main interface.

## 2. Restore your library and extensions

1. Open **More → Backup & restore → Choose backup** and select your Android Mihon/Nooir backup (.tachibk).
2. Review counts of library titles, categories, chapters, progress, history and missing sources.
3. Choose **Restore library**, then save the separate recovery backup when prompted. Restore merges records into the connected server; existing records may change.
4. Open **Browse → Extensions → Repositories** to add only trusted repository index URLs. Review signing identity and refresh the catalogue explicitly.
5. Return to backup recovery, choose **Review extensions**, select compatible packages and approve installation. Exact reviewed matches only; failures are reported separately. Missing or changed packages may need manual installation. Library restore is not dependent on every extension succeeding.

Android app preferences, downloaded chapter files, and Nooir-specific settings are not automatically restored as part of the library backup. Keep the original backup. Never disable certificate checks to install extensions. If PKIX errors occur, report the exact message; the prior Windows certificate workaround is not yet automated in this preview.

## 3. Library, Browse and reading

Use the left navigation: Library, Updates, History, Browse and More.

- **Library:** All or a restored category; title filter applies to the current page. Unread badges use server data. Click a cover to open chapter details.
- **Browse:** choose an installed source, then Popular, Latest (when supported) or Search. Use Extensions to manage available/installed packages and repositories.
- **Details:** Continue reading opens the first unread chapter when known, at its saved page. Choose Read for a specific chapter. Chapter details show download/read status.
- **Reader:** Previous and Next turn pages. Save position is explicit and only enabled for a successfully displayed later page, without a reported conflict. It does not automatically mark a chapter read. Concurrent clients can still race.
- **Updates:** recently fetched local chapter records, not a remote refresh of all sources.
- **History:** saved chapter reading timestamps, not a complete per-session event log.

Actual captures in the evidence ZIP: **compose-library.png**, **compose-manga-details.png**, **compose-reader-page2.png**, **compose-updates.png**, **compose-history.png**. The original sample artwork and book names are synthetic; these are real application screenshots, not mockups.

## 4. Export manga for an e-reader

1. Open a manga in Library or Browse. Choose **Select chapters** and tick the chapters you want. Choose **Export**.
2. Select your device and format. For a typical X4 manga export, start with **Apply manga settings**, XTC, Panel by Panel, Auto Fit. Review the actual option values rather than assuming defaults. For four-level grayscale use XTCH when appropriate.
3. Choose a new output file in an existing folder. Export does not replace an existing file.
4. Start Export. A missing chapter may need an explicit download approval.
5. Choose **Preview converted book** for XTC/XTCH; browse actual decoded pages, including the content page after a cover. Use **Send to XTEink** if appropriate.

Evidence: **compose-chapter-selection.png**, **compose-export-ready.png**, **compose-export-complete.png**, **compose-converted-manga-page.png**, and decoded sample XTC/XTCH PNGs.

## 5. Universal Converter: local manga, PDF and EPUB

Open **More → Universal Converter**. Choose the local input and compatible conversion mode, reader profile and output format. The interface offers manga/image archive and folder inputs, PDF original-page conversion, PDF reflow/companion EPUB, and image/text EPUB modes. Available output formats depend on the input and device. Text EPUB offers typography controls; manga offers panel/orientation and rendering controls. Apply manga settings for manga, not ordinary prose. Browse to a new output file and Convert. Preview actual XTC/XTCH when available. For large books, save the full file locally.

Evidence: **compose-more.png**, **compose-universal-converter.png** and local conversion output files in the ZIP. Synthetic local manga, PDF, image EPUB and prose EPUB conversion/decoding passed the headless smoke test.

## 6. Web to EPUB

Open **More → Web to EPUB**. Paste one public story overview URL and Analyze story, or paste individual chapter links and choose Use pasted links. Review selected chapters, edit titles/order and preview content. In Book details, check title, author, synopsis, language and cover. Choose a detected/custom cover URL, local JPEG/PNG, or combined covers. In Settings, review EPUB2/3, reading style, illustration quality and optional extraction/CSS rules. Choose a new .epub file and Export EPUB. If canceling, wait for the active bounded operation to finish.

Only public accessible chapters are supported; no login/cookies/paywall bypass. The underlying shared engine is reused, but the new editor needs live-site acceptance, including Wattpad cover/synopsis. Saved web projects, chapter update checks and cover preview are not yet integrated in Compose; keep the existing editor for those features. No verified native web-editor screenshot yet.

## 7. Activity Queue

Open **More → Activity Queue → Add books**. Choose input type, supported files, device, format and an existing output folder. The first native batch interface uses default manga/typography presets. Choose Run / recover to start, Pause after current to stop between books, Retry for failed/interrupted records, or Cancel for eligible pending/failed records. Completed XTC/XTCH records can open the real preview. Clear finished removes records only, not output files. Persistent local queue uses the existing storage and no-overwrite policy. Remote chapter/download queues still require the existing preview. Native queue GUI acceptance and screenshot remain pending.

## 8. Send to XTEink

Open **More → Send to XTEink**, or Send to XTEink in the actual converted-book preview. Enable File Transfer on a compatible Folio Nooir reader on a trusted local network. Enter the private IPv4 displayed by the device, an existing folder such as /Books, and a safe filename with the original extension. Review and confirm. Transfer uses unencrypted local HTTP, checks for an existing filename, verifies the exact acknowledgement and re-lists file size. Avoid concurrent uploads to the same name. If not confirmed, inspect the reader before retrying; a partial file may exist. Over 180 MiB: save locally and copy via SD card or supported USB transfer, or split into smaller volumes. Real reader acceptance and native transfer screenshot still pending.

## 9. Test checklist and reporting

- [ ] Launch on Windows and check first-time reading service setup.
- [ ] Restore a copy of an Android backup; compare library/category counts.
- [ ] Recover compatible extensions, note unsupported packages, browse and read.
- [ ] Try Continue Reading and Save position; reopen and compare.
- [ ] Export manga to XTC and preview content; compare with Android.
- [ ] Convert local EPUB/PDF and test a small queue batch.
- [ ] Analyze a public web novel; verify cover, synopsis and EPUB contents.
- [ ] Send one small book to a compatible reader; open it on the device.
- [ ] Check window scaling, navigation responsiveness and close protection.
- [ ] Report build commit, Windows version, workflow, reproducible steps, expected/actual behavior, and a privacy-safe screenshot. Never include credentials, private backup data or copyrighted chapter archives in public reports.

## Screenshot provenance and verification

The screenshot bundle is artifact 15176679885, produced by successful Compose run 45952282235 from the PR merge checkout recorded in provenance.txt (15c584b5b92c52e733b0aa35f5bb5bca816881cb). The PR head used for this build is 9bfdf525bb13e138f74c5546fc3f503fb3c767df. Captures are from Linux Xvfb at 1040×720, with separate modal captures on a 1440×900 virtual desktop, using a loopback synthetic library. Do not treat them as physical Windows screenshots. The same run passed compilation on Windows/Linux/macOS, actual reader and export click-through, decoded output tests, local converters, synthetic backup recovery, extension install, position persistence and reader transfer contract tests, plus Windows package creation. Native backup/extension/queue/web/transfer click-through, full UI parity, real source/reader testing, and performance acceptance are outstanding. No official Windows release has been published.

## Upcoming preview correction (not in the original 0.1.0 ZIP)

Field feedback: the first Compose build exposed too few conversion settings. The next revision shares expanded manga/image settings across export, Universal Converter and batch jobs, adds text margins/line height, chapter filter/sort and download requests. Updates/History now accept the pinned server's legitimate unknown page count (-1); actual server regression added. Full Android parity, live settings preview, diagnostic export, custom resolutions, persistent preferences and download queue monitoring remain to be verified or implemented. Do not treat this revision as a tested downloadable package until CI passes.

### Follow-up validation and settings persistence

Expanded settings revision d833958 compiled on all three OS targets, but CI exposed a docs whitelist issue (README.md and docs/README.md only) and a stale GUI chapter-selection coordinate. The next revision allows those exact documentation files while retaining protected Android source checks, adjusts clicks to the reviewed failure screenshot (checkbox y404, toolbar y344), adds independent combinable reading/download/bookmark filters, and explicit load/save default manga preferences using the existing shared desktop store. Save requires approval; existing queued jobs and Android are unchanged. Native persistent typography, auto-load behavior, full GUI setting acceptance, and the real-server history/updates test remain pending. No new preview ZIP yet.

### Live sample preview (next candidate)

The expanded settings panel now includes a local image sample preview in manga export, image/PDF conversion and image batches. It uses the exact shared JVM page renderer and debounces changes, with at most 12 frames / 24 MiB of encoded sample data. Treat as chapter first page applies the real first-page preservation rule. A sample image is explicitly selected; automatic extraction from CBZ/EPUB/PDF/source chapters and full original-image comparison are not yet implemented. The original file is read only. New synthetic renderer smoke added; CI and GUI verification pending. No Android changes or released binary.

### Chapter actions and download queue (next candidate)

The expanded-settings/live-sample build at 196cdc8 passed all five Compose jobs (45962864405); [portable Windows candidate](https://github.com/toshio2011/mihon-nooir/actions/runs/45962864405/artifacts/15181368618). Its real-server verification was still running. The next revision adds selected chapter mark read/unread, bookmark/remove bookmark, and More → Download queue using the pinned server's native status API. Mark unread retains page position; it does not reset progress. Explicit start/stop affects the connected server and all its clients; remove is restricted to a currently queued/error entry and never deletes downloaded files. Queue state refreshes every five seconds; no invented completion percentage or automatic retry. Actual-server flag and empty queue tests added; active download control, new GUI captures and runtime validation are pending. No Android changes.

### Conversion workflow and parity correction (next candidate)

Field report: Conan XTC had 186 valid 480×800 frames; the original source pages, Android export and exact settings are needed for a real frame-by-frame comparison. Desktop recommended manga settings now copy the same fields as Android instead of resetting unrelated options. First-chapter-page preservation and Float sizing now follow Android's AndroidPageSource. The next candidate introduces app-owned sequential single-export activity with Queue & close, permitting Library/Browse/reader navigation while conversion runs. Close of the entire app is blocked while work is pending. Existing immediate export remains available; durable local batches remain separate and session single-export records are not crash-persistent. Full Android/JVM output parity, PDF/EPUB/Web settings and active-job UI acceptance remain open. Actual pinned server updates, chapter flags and queue query passed on ad1288e; repeat restore failed in the server's Windows multipart temp handling before history validation. No Android source changes.

### Application-owned conversion activity and additional renderer parity

The app-owned worker now also runs durable local batches outside the queue dialog; pause-after-current and status survive closing/reopening that dialog. Queued manga/local/web single exports wait behind an active batch rather than running a second concurrent conversion. Immediate export is disabled while app-owned conversions are active. Web to EPUB gains Queue & close. The existing batch TSV, runner lock and output files are preserved. Named cover/frontcover/folder pages now use Android's intact-page geometry in the JVM renderer; source chapter CBZ extraction retains those markers in indexed names. Original single-export job records still session-only; persistent web/manga job specifications, exact Android device-vs-JVM pixel comparisons, and live GUI workflow acceptance remain pending. A new app-owned job sequencing smoke test is gated in Compose CI. No Android source changes.

### Universal source menu and local-image support (next candidate)

More → Universal Converter opens a source hub (Local files, PDF files, Web link, Web novels, finished XTC/XTCH inspector, batch conversion). The two web entries intentionally reuse the same combined editor. Local input type is chosen before browsing; picker restricts to supported extensions and validates the selection. Individual JPEG/PNG/WebP/BMP/GIF inputs now use the same bounded CLI decoder, including durable local batches; output is a new file. Existing XTC/XTCH files can be inspected independently without conversion. Original PDF only exposes supported rendering, direction, color and EPUB grouping controls; PDF text/companion modes describe their actual behavior rather than showing ignored manga controls. Native hub click-through and single-image decoded regression added; CI pending. Automatic settings loading, custom device dimensions, metadata overrides, web projects, cover preview, batch names and combined transfer still open. Android source unchanged.

### Android manga settings parity correction (next candidate)

Desktop now displays Floyd–Steinberg, Atkinson, Bayer 8×8 and Threshold like Android, resolving legacy stored tokens by their actual dither behavior. Fresh dialogs use the Android default options with explicit Floyd–Steinberg; Manga/Manhwa/Manhua presets use the shared Android preset values. Recommended manga settings copy exactly the Android fields, retaining unrelated settings. Auto Fit is available for row and panel modes, rotation/overlap visibility follows Android, manual changes mark Custom, and contrast/minimum-fill slider behavior matches Android. Batch non-manga defaults no longer force Threshold. Existing saved tokens and queued jobs are not rewritten. Renderer regression compares legacy and explicit Floyd PNG bytes; Android source remains untouched. CI and physical Windows acceptance pending. Diagnostic ZIP and automatic sample extraction are still missing.

### Preferences, custom devices and EPUB typography (next candidate)

Manga export, local conversion and batch setup load the shared saved manga settings before editable controls appear, with a warning and Android defaults if the file is invalid. Existing preferences are not rewritten automatically; per-job permissions and diagnostics never persist. Local image/EPUB comic input uses the loaded manga defaults, PDF and text use separate defaults. Custom reader dimensions use the shared Android device profile validator (64–4096 pixels, at most 3 million pixels). Text EPUB now offers exact Android Compact (20/14/110), Balanced (24/26/118), Large (32/20/135) font/margin/line-height presets and Sans default. New smoke assertions cover settings round-trip/corrupt preservation, custom dimensions in rendered PNGs, and presets. This revision needs CI and GUI verification; no Android source changes. Persistent device/format and typography settings, web project/cover workflows and integrated convert-and-send remain open.

### Web editor parity (next candidate)

Projects tab saves/loads/deletes local chapter inventories and book settings with confirmation. Check new chapters compares the original complete inventory (including previously excluded chapters) and offers reviewed additions; unselected new chapters remain discoverable next time. Save again after updates. Book details now accepts 2–4 explicit combined-cover URLs, saved with the project in a backward-compatible field, and previews actual local, remote or combined artwork through the shared EPUB cover engine. No automatic export, network checking, or book deletion. Manual link input clears stale metadata from prior story. Added offline project persistence/legacy/validation/update regression. Live web requests and UI click-through are not yet verified; separate-story batches, PDF metadata overrides and convert-and-send remain pending. Android unchanged.

### Reader keyboard navigation (next candidate)

Online reader and finished XTC/XTCH inspector now handle Left/Right, Page Up/Down, Space (next), Shift+Space (previous), Home (first), End (last), and Escape (back). In the online reader Ctrl+S opens the existing explicit save-position confirmation only when a later page is successfully decoded and no conflict is reported. Shortcuts are scoped to reader focus and blocked during save/transfer dialogs. Left and right always mean previous and next in source reading order, regardless of manga artwork direction. GUI regression now verifies real keyboard page requests, End/Home and Escape plus converted-preview Right. Pending CI and Windows keyboard acceptance.

### PDF metadata, batch filenames and custom profiles (next candidate)

PDF single export offers optional title and author; EPUB falls back to embedded PDF metadata, then filename for title. Both original-page and reflow/companion paths consume overrides. Local batch jobs allow per-file output names without an extension; blank retains safe default. Duplicate, unsafe and existing targets reject the entire enqueue transaction. Custom reader IDs use shared validated profile rather than the old alphanumeric-only guard. Queue index format and existing job records unchanged. Tests exercise decoded custom resolution batches, duplicate/traversal refusal and EPUB metadata. CI pending; batch PDF metadata overrides not implemented. Android source unchanged.

### Version label and keyboard API correction

Windows development builds display the shared Android baseline (`config/version.txt`, currently 1.0.20) followed by `-preview` in the window title. The portable ZIP contains PREVIEW.txt with that baseline, exact commit, and separate native package version (0.1.0). The internal Windows version and existing update checks are deliberately unchanged until an official release is agreed; Android version, Android sources, and release workflows are untouched. Preview filenames and artifact name remain compatible with existing instructions. The previous keyboard build failed compilation on all three Compose targets because `Key.End` does not exist. This revision uses Compose's `Key.MoveHome`/`Key.MoveEnd` for physical Home/End. CI including actual keyboard events remains required before offering this revision as a new Windows package.

### Preview selected comic source (next candidate)

For local manga/image inputs and the first local manga batch book, Live conversion preview now offers Preview selected book and previous/next source page. It uses the CLI's actual natural source ordering, includes/excludes explicit covers according to the current setting, and automatically identifies chapter opening pages and named cover art. The source is read only; no extraction files or output books are created for preview. Image bytes are capped at 25 MiB and existing frame limits still apply. Source bytes are retained while settings change, so sliders rerender rather than re-open the archive. The shared page-option selection is reused by the export path. Regression adds natural ordering, cover exclusion, chapter opening and pixel comparisons with actual exported XTC frames. Image EPUB, PDF and reading-service chapter auto-preview remain pending; manual sample works in manga/image EPUB. New code pending CI.

### Background Convert & Send (next candidate)

Manga export, local converter (compatible XTEink profile), and Web to EPUB now offer Convert & send after choosing a new local output path. Enter and explicitly confirm private reader IPv4, existing folder and filename; sequential app-owned jobs convert locally before invoking the same verified upload contract. No transfer starts if conversion fails. Upload failure is a distinct TRANSFER_UNCONFIRMED state, and the local book remains intact. Activity Queue allows explicit manual send of finished books, including EPUB/CBZ, and a failed transfer may be retried only after inspecting the reader. No automatic deletion or retry. The existing 180 MiB limit, duplicate guard and ACK/size verification remain enforced by the transfer engine. Session task tests verify transfer ordering and preservation on transfer failure. Physical reader and new GUI flow pending. Durable local batch auto-transfer remains unimplemented. Android unchanged.
