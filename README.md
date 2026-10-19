# Mihon × MangaPoint × Folio Nooir

**Experimental Android fork integration, not yet a production APK.**

Standalone Android manga reader based on the *full Mihon upstream*, with a MangaPoint-compatible e-reader export workflow. No Suwayomi server, private server or account required. Source discovery remains provided by Mihon's existing extensions, and sources are not bundled.

## First integration milestone — 19 October 2026

- Keep all Mihon features by applying a minimal overlay to Mihon's source, pinned to commit `deeb9b2584b6e246135cbd31da49a39e6b6e1e52` (17 Oct 2026).
- Add **Export to e-reader** to the existing manga detail screen, using downloaded chapters selected in Mihon or every downloaded chapter when none is selected.
- Offer **Auto, XTC, XTCH, EPUB, CBZ** formats. Auto currently resolves to XTCH for X4; this is a preset, not a proven perfect choice for every manga.
- **XTC / XTCH:** MangaPoint-compatible 1-bit/2-bit page serialization, per-chapter navigation, title, author, publisher, language and cover-page index.
- **EPUB 3 fixed-layout:** ordered XHTML/image pages, navigation document, embedded cover, OPF title/creator/description/subjects/language/publisher/rights/identifier metadata.
- **CBZ:** numbered PNGs, `ComicInfo.xml` with title/series/writer/artist/description/genre/language/cover/chapter bookmarks; machine-readable metadata JSON included for more fields.
- Include original manga metadata as available from Mihon. Missing metadata is not fabricated. Preserve untouched original downloads for later re-export.
- Currently X4 480×800 by default (X3 528×792 is implemented as a backend parameter but not exposed by the UI yet).
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

GitHub Actions `Build Mihon Nooir debug APK` clones the pinned Mihon revision, applies this overlay, and attempts `:app:assembleFoss`; the resulting APK will be uploaded as a workflow artifact **if the build succeeds**. We have not yet verified a full Android build or installed it on the user's phone.

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

The export dialog remembers format (Auto/XTC/XTCH/EPUB/CBZ), X4/X3 device preset, hostname/IP, and folder. **Save file** uses Android's file picker. **Send to XTEink** probes Nooir's `GET /api/status`, creates the export in temporary app storage, then uploads with multipart `POST /upload?path=/Books`. Keep your XTEink in Nooir's File Transfer mode and phone on the same Wi-Fi. Sending to the same name overwrites the previous file on the device.

As with the earlier build, sources and metadata are retained in Mihon; format conversion remains non-destructive. **Direct transfer still requires on-device hardware testing.** Page crop, contrast, dithering settings, upload progress, and download-time format selection remain future milestones.


## Send & Convert any phone file — experimental PR

A **More → Send & Convert files** entry will let the user choose any Android document (Storage Access Framework). Options:

- **Send original to Folio Nooir** over local Wi-Fi (the target device must be in File Transfer mode). This copies file bytes as-is and does not imply the e-reader can open arbitrary formats. HTTP without encryption is appropriate for a trusted LAN only.
- **Share original** via Android's native share sheet, including any installed cross-device transfer app.
- **Manga detected:** recognize JPG/PNG/WebP/BMP/GIF or CBZ/ZIP containing images. Offer Auto/XTC/XTCH/EPUB/CBZ conversion and Save file or Convert & send to Nooir. For CBZ, extract available ComicInfo.xml title, creator, genre, description, language, publisher, series metadata, using native output fields wherever possible.

Files are selected by the user, not gathered through bulk storage scanning. The original file is not modified. Conversion currently accepts input up to 512 MB. Nouir may store documents it does not know how to display.

**Still planned, not implemented here:** direct WebDAV/OPDS/Calibre/KOReader/other-device support, built-in web-article extraction to EPUB or PDF, richer metadata editing, live progress, multi-file batch sending and large-library queues. Quick Export of web-sourced manga remains a separate PR, with a future streaming/chapter-batch redesign to lift the temporary first-test cap.
