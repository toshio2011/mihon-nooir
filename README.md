# Mihon × MangaPoint × Folio Nooir

**Experimental Android fork; APK builds in GitHub Actions, but not yet a production release.**

Standalone Android manga reader based on the *full Mihon upstream*, with a MangaPoint-compatible e-reader export workflow. No Suwayomi server, private server or account required. Source discovery remains provided by Mihon's existing extensions, and sources are not bundled.

## First integration milestone — 19 October 2026

- Keep all Mihon features by applying a minimal overlay to Mihon's source, pinned to commit `deeb9b2584b6e246135cbd31da49a39e6b6e1e52` (17 Oct 2026).
- Add **Export to e-reader** to the existing manga detail screen, using downloaded chapters selected in Mihon or every downloaded chapter when none is selected.
- Offer **Auto, XTC, XTCH, EPUB, CBZ** formats. Auto currently resolves to XTCH for X4; this is a preset, not a proven perfect choice for every manga.
- **XTC / XTCH:** MangaPoint-compatible 1-bit/2-bit page serialization, per-chapter navigation, title, author, publisher, language and cover-page index.
- **EPUB 3 fixed-layout:** ordered XHTML/image pages, navigation document, embedded cover, OPF title/creator/description/subjects/language/publisher/rights/identifier metadata.
- **CBZ:** numbered PNGs, `ComicInfo.xml` with title/series/writer/artist/description/genre/language/cover/chapter bookmarks; machine-readable metadata JSON included for more fields.
- Include original manga metadata as available from Mihon. Missing metadata is not fabricated. Preserve untouched original downloads for later re-export.
- X4 480×800 and X3 528×792 presets are selectable in the export dialog.
- Test XTC/XTCH writer and EPUB/CBZ container + metadata + chapter navigation as pure JVM tests.

## Metadata limitations

All **four output formats** have *some* metadata embedded. The formats do not support the same fields:

| Format | Native metadata readable by supporting readers | Additional metadata |
|---|---|---|
| XTC / XTCH | Title, author, publisher, language, created date, cover page, chapter index | These formats have no standard synopsis/genre fields; firmware may show only title/author |
| EPUB | Title, author, publisher, language, description, subjects/genres, rights, identifier, cover, navigation | `META-INF/nooir-metadata.json` carries more source fields |
| CBZ | `ComicInfo.xml`: series, title, writer, artist, summary, genre, language, source URL, cover and chapter bookmarks | `nooir-metadata.json` carries more source fields; support varies by reader |

The extra JSON is not a substitute for native metadata and may be ignored by e-readers. For **XTC/XTCH complete rich metadata** we would need to add an optional sidecar file or a coordinated Nooir metadata-import feature; adding undocumented binary fields risks corrupting files.

## Target user experience

**Browse / download through Mihon → choose device + output format → convert downloaded manga → Save or send to Nooir over Wi-Fi.** A full *download-time format picker*, persistent conversion presets, scheduling conversion when the download finishes, progress/cancellation, direct transfer UI and broad device presets are still to build. The current picker works **when exporting already-downloaded chapters**, not automatically on download. Do not assume they are finished.

Why retain originals? Conversion is destructive. A saved original allows choosing another output later without downloading again, and preserves all existing Mihon readers, downloads, library, history, categories, updates, backups and tracking.

## Build

GitHub Actions `Build Mihon Nooir debug APK` clones the pinned Mihon revision, applies this overlay, and attempts `:app:assembleFoss`; the resulting APK will be uploaded as a workflow artifact **if the build succeeds**. The combined 1.0.3 preview passed an Android CI build, but on-device Quick Export and Nooir file-transfer tests are not yet verified.

```
python3 -m unittest discover -s tests -p 'test_*.py' -v
bash tests/run.sh                 # requires kotlinc and Java installed
python3 tools/apply_overlay.py /path/to/pinned-mihon-checkout
```

## Next milestones

1. Green full Android CI build, on-device import/export test, compare output against MangaPoint and Nooir parsers.
2. Single manga screen format/device preset picker; per-manga remembered settings; export progress and cancellation.
3. Download-time **keep originals + convert after download** queue using Mihon's download-state events and Android WorkManager; choose format at initiation; never block normal downloads.
4. Direct Nooir HTTP/Wi-Fi transfer with connectivity checks, explicit confirmation, retries and destination folder choice.
5. MangaPoint-grade crop, zoom, dithering, rotation, contrast, page split/strip presets and faithful XTC/XTCH/EPUB/CBZ output tests.
6. Metadata editor and optional rich-metadata sidecars; cover selection and remote/book metadata integration without overwriting Mihon data.

## Source and license

Mihon is Apache-2.0; MangaPoint is MIT. Retain all upstream attribution and altered-file notices. This is an unofficial, independent project. You must have appropriate rights to download and transfer manga; no content or providers are bundled.

- Mihon: https://github.com/mihonapp/mihon
- MangaPoint: https://github.com/W1seGit/MangaPoint
- Nooir: https://github.com/toshio2011/folio-nooir

## Keeping the app and Mihon upstream updated

Our app retains Mihon's built-in update screen, progress/download and Android installer flow. The overlay redirects update checks to this fork's GitHub releases, never to the official Mihon APK. Version names are independent and tracked by `config/version.txt`.

The `watch-mihon.yml` workflow checks upstream daily and updates one issue when commits change. Upstream code is reviewed and tested before updating the pinned SHA: no automatic merge that might erase Nooir-specific fixes.

Official releases require a **stable signing key across builds** so Android recognizes the APK as an update; development APKs from CI may not be upgradable. See [update policy](docs/UPDATES.md).


## MangaNooir v1.0.1 UI / Wi-Fi transfer

The launcher and localized app name are now **MangaNooir**, distinct from Mihon. The fork retains Mihon's features while showing an **Export to XTEink** action next to the manga toolbar Download button (also available when chapters are selected).

The export dialog remembers format (Auto/XTC/XTCH/EPUB/CBZ), X4/X3 device preset, hostname/IP, and folder. **Save file** uses Android's file picker. **Send to XTEink** probes Nooir's `GET /api/status`, creates the export in temporary app storage, then uploads with multipart `POST /upload?path=/Books`. Keep your XTEink in Nooir's File Transfer mode and phone on the same Wi-Fi. The current Nooir HTTP uploader rejects an existing filename instead of replacing it; rename the file or choose a different folder.

As with the earlier build, sources and metadata are retained in Mihon; format conversion remains non-destructive. **Direct transfer still requires on-device hardware testing.** Page crop, contrast, dithering settings, upload progress, and download-time format selection remain future milestones.


## Quick Export (experimental, source fetch without saving to Mihon)

Enable **Quick Export** in the MangaNooir export dialog after selecting at least one chapter (there is no fixed chapter-count limit). It uses any chapters already downloaded in Mihon, and fetches missing pages through the installed manga source's own page-list and image APIs. These missing pages are placed only in a temporary private cache and cleaned up on completion or failure. A successful result is saved as XTC/XTCH/EPUB/CBZ or transferred to XTEink.

Safety limits per chapter: up to 1,000 remote pages, 25 MB per page, 400 MB temporary cache. No fixed chapter count; processing is sequential. Very long exports should eventually use a foreground WorkManager job. No permanent Mihon download is created. Network access and an installed functioning online source extension are required. Some sources can block fetching or require authentication. Full chapter-by-chapter progress, pause/resume, and remote source reliability still need device testing.


### Streaming Quick Export extension

Quick Export no longer has a five-chapter ceiling. The writer fetches remote images one chapter at a time, decodes and streams their pages into the selected output format, and removes that chapter's temporary files before advancing. This supports selected volumes or entire series while bounding intermediate remote-page storage to the current chapter. Very large *final output files* may still require substantial storage; proper progress, cancellation buttons, background notifications and retry/resume are on the roadmap.

## Send & Convert any phone file — experimental PR

A **More → Send & Convert files** entry will let the user choose any Android document (Storage Access Framework). Options:

- **Send original to Folio Nooir** over local Wi-Fi (the target device must be in File Transfer mode). This copies file bytes as-is and does not imply the e-reader can open arbitrary formats. HTTP without encryption is appropriate for a trusted LAN only.
- **Share original** via Android's native share sheet, including any installed cross-device transfer app.
- **Manga detected:** recognize JPG/PNG/WebP/BMP/GIF or CBZ/ZIP containing images. Offer Auto/XTC/XTCH/EPUB/CBZ conversion and Save file or Convert & send to Nooir. For CBZ, extract available ComicInfo.xml title, creator, genre, description, language, publisher, series metadata, using native output fields wherever possible.

Files are selected by the user, not gathered through bulk storage scanning. The original file is not modified. Conversion currently accepts input up to 512 MB. Nouir may store documents it does not know how to display.

**Still planned, not implemented here:** direct WebDAV/OPDS/Calibre/KOReader/other-device support, built-in web-article extraction to EPUB or PDF, richer metadata editing, live progress, multi-file batch sending and large-library queues. Quick Export of web-sourced manga was prototyped in PR #6 and now streams one chapter at a time in the combined preview. Progress, retry/resume and foreground background jobs remain future work.

## Combined 1.0.3 preview
Includes both PR #6 (Quick Export) and PR #7 (any-file Send & Convert); APK build was green at commit d203e54. CI now also runs the JVM-format test suite. Keep main stable until the latest CI commit passes and on-device Nooir transfer and extension Quick Export are tested.


## Device verification checklist for 1.0.3

1. Download the **integration branch** APK artifact only after its latest green GitHub Actions run. Do not overwrite your normal Mihon installation; experimental MangaNooir CI packages may be signed with different keys, so back up the fork's data before uninstalling an earlier build.
2. Open MangaNooir and confirm **More → Send & Convert files** and the export action beside Download. Check the original library and reader continue working.
3. For a manga with a working online extension, select one chapter **not downloaded** in Mihon. Export → **Quick Export** → XTCH → **Save file**. Verify successful output and that the chapter is still not marked downloaded.
4. Try multiple chapters across a volume. Ensure readable chapter navigation and check that temporary files are removed on success and failure.
5. In **More → Send & Convert files**, select a small CBZ containing ComicInfo.xml, convert it to EPUB or XTCH and validate the output metadata; also choose a PDF/EPUB and check **Send original** without converting it.
6. On Folio Nooir, enter **File Transfer** mode and connect the phone and reader to the same trusted Wi-Fi. Enter the device IP shown by Nooir (if `crosspoint.local` does not resolve), destination `/Books`, and test sending a **small** CBZ/EPUB first.
7. Check the file appears in Nooir's library. A file uploading successfully does not guarantee the firmware knows how to open arbitrary formats.
8. Record source-extension failures, memory/storage warnings, incorrect image ordering, missing metadata, or transfer errors for follow-up fixes.

This checklist needs a user/device run: Android GitHub Actions cannot establish a real Wi-Fi connection to the physical X4.


## Send files from Android to Folio Nooir (XTEink X3/X4)

This workflow uses the **local network** and the Nooir HTTP file-transfer server, not Bluetooth. The reader's server has **no password**: use only a trusted home Wi-Fi or the temporary XTEink hotspot, and leave File Transfer mode when finished.

1. On XTEink choose **Home → File Transfer → Join Network**, join your home 2.4-GHz Wi-Fi. Your phone must be on the **same home network** (it may use the router's 5-GHz band if local devices can see each other).
2. Keep the reader's File Transfer screen visible. It displays a URL, for example `http://192.168.1.102/`, and often `http://crosspoint.local/`. Your IP will be different.
3. On your phone open **Chrome**, browse to the XTEink's displayed URL, and confirm you see the file manager. If Chrome can't reach it, fix the Wi-Fi first; try the displayed IP instead of `crosspoint.local`, disable VPN, and check router client isolation.
4. In **MangaNooir → More → Send & Convert files**, choose one or several local documents. Enter **only the IP/hostname** from the reader (e.g., `192.168.1.102`, not `http://...`) and an existing destination folder (use `/` to send to the root, or `/Books` if it exists).
5. **Send originals** preserves the selected files unchanged; **Convert & send manga** converts supported image/CBZ inputs before transfer. Unsupported formats can be sent unchanged, but the reader may not be able to open them.
6. A transfer notification and progress details should appear in builds with the background queue. **100% sent** means Android finished sending bytes; wait for **XTEink confirmed** before treating it as completed.
7. Look for the uploaded files on the reader (file manager/library). The current Folio Nooir HTTP handler rejects a filename that already exists. If you retry, rename the file or choose a different destination. Avoid repeatedly retrying an uncertain upload.

No router? Choose **File Transfer → Create Hotspot** on your XTEink, connect the phone to `CrossPoint-Reader`, and use the address printed on the reader (often `192.168.4.1`). Android may report “Connected without internet”; stay connected. The hotspot is **open**, so switch it off when finished.

Troubleshooting: if the UI appears stuck, check Android's persistent transfer notification for progress; wait for the receiver's acknowledgement; verify reader battery, SD-card space, Wi-Fi range, and destination folder. If the reader cannot be reached from Chrome, the app cannot reach it either. Transfer support: [Nooir web server](https://github.com/toshio2011/folio-nooir/blob/main/docs/webserver.md) and [HTTP upload API](https://github.com/toshio2011/folio-nooir/blob/main/docs/webserver-endpoints.md).

## MangaPoint conversion settings audit

Our earlier versions exposed **format and device only**: despite using a grayscale/dithering converter, they did **not** provide MangaPoint's comprehensive per-book settings. MangaPoint's source has Manga/Manhwa/Manhua presets, page layout (paged/webtoon/automatic), reading direction, fixed and automatic margin trimming, sensitivity/padding, spread fit/split/rotate, contrast, auto-levels, dithering algorithms, webtoon segmentation/overlap, portrait/landscape, fit/width/100–300% zoom (tile or crop), optional directional guide arrows and a live before/after preview. Its export workflow also has grouping/splitting options. Those capabilities are **not all implemented** by MangaNooir yet.

The first phone implementation should expose only controls that actually affect rendering; the rest remain tasks in [roadmap #8](https://github.com/toshio2011/mihon-nooir/issues/8). Progress for transferring the original is not equivalent to conversion progress; if a conversion has unknown total page count, display a stage/count rather than an invented percentage.


### Background behaviour in MangaNooir 1.0.4 preview

- **More → Send & Convert**: selected files form a sequential WorkManager queue. You can press **Back**, switch to another app, or lock the phone. Android's foreground notification gives item count, upload bytes/percent, confirmation, failure and cancellation. Open More → Send & Convert again to inspect or cancel the last transfer.
- **Export beside manga Download**: XTC, XTCH, EPUB and CBZ conversion / Quick Export and Send are also queued through WorkManager, with saved chapter selections and a foreground notification. The most recent manga export status is shown in More → Send & Convert.
- For either path, **100% means bytes sent to the XTEink but not yet acknowledged**. Only “Confirmed by XTEink” indicates successful upload. Conversion stages do not have a fake percent.
- **Convert & save**: choose a folder for a batch of files; converted manga outputs are saved individually and non-manga documents are copied unchanged. This also runs in the background.
- A system force-stop, severe power or connectivity loss, permissions removed by a provider, or unavailable SD card can stop/fail jobs. WorkManager can restart eligible work after app process recreation but cannot guarantee uninterrupted network transfer; canceled or failed partial outputs should be checked before retrying.
- **Output settings** shared between manga and file import: Manga/Manhwa/Manhua presets; Auto/Pages/Webtoon; conservative auto white-margin trimming; double-page spread splitting; left-/right-to-left order; contrast (50 neutral); Floyd–Steinberg vs nearest-level grayscale.
- MangaPoint's manual/fixed margin adjustments, trim sensitivity, custom presets, zoom/tiling, preview, orientation/landscape, guide arrows and sophisticated long-strip planning are **still TODO**, not implemented controls.

**Development status:** feature PR #10 is an experimental change layered on the combined 1.0.3 preview. A green Android build and X4 on-device verification are required before treating it as ready for regular use.


### Large transfers: current experimental safeguard

MangaNooir 1.0.4 currently stops any single XTEink HTTP upload above **180 MiB** (180 × 1,048,576 bytes). This is a deliberately conservative app-level limit, **not** a firmware-imposed maximum or proof that smaller files cannot fail. Folio Nooir streams incoming data through a small SD write buffer and yields to Wi-Fi; it does not keep the entire manga in ESP32 RAM. Risks remain if Wi-Fi drops, SD is full/slow, the server is exited, or power runs low. The device can reject a file with an existing filename, and an interrupted transfer may leave incomplete SD data in some failure paths. Prefer smaller chapter/volume files and check the receiver after each transfer. Automatic file splitting, SD capacity checks and receiver-side cleanup hardening are tracked in [roadmap #8](https://github.com/toshio2011/mihon-nooir/issues/8).

**Background behavior:** Both **More → Send & Convert** and **manga details → Export → Save/Send** now enqueue experimental foreground WorkManager jobs, so navigating Back or switching apps is intended to leave the job running. Android may still stop jobs under severe battery restrictions, process death or system pressure; the app must show a success/error state rather than promise a completed transfer. More → Send & Convert shows the most recently queued manga job and ordinary-file job; Android's permission for notifications may need enabling to see the progress outside the app.
