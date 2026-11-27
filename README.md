<div align="center">

# MihonNooir

**Your Mihon manga library, plus tools to prepare books for an e-reader.**

Read manga on Android · Choose chapters · Convert books · Send to XTEink over Wi-Fi

**[Download Android v1.0.20](https://github.com/toshio2011/mihon-nooir/releases/tag/v1.0.20)** · [Installation & first export](docs/GETTING_STARTED.md) · [Nooir Tools](docs/NOOIR_TOOLS_QUICK_START.md) · [All guides](docs/README.md) · [FAQ](docs/FAQ.md)

[ARM64 APK](https://github.com/toshio2011/mihon-nooir/releases/download/v1.0.20/app-arm64-v8a-foss.apk) · [Universal APK](https://github.com/toshio2011/mihon-nooir/releases/download/v1.0.20/app-universal-foss.apk) · [SHA-256 checksums](https://github.com/toshio2011/mihon-nooir/releases/download/v1.0.20/SHA256SUMS.txt)

[![Android release](https://img.shields.io/github/v/release/toshio2011/mihon-nooir?label=Android)](https://github.com/toshio2011/mihon-nooir/releases/latest)
![Platform](https://img.shields.io/badge/platform-Android-blue)
![Windows](https://img.shields.io/badge/Windows-in_development-orange)
![Base](https://img.shields.io/badge/based_on-Mihon-informational)

</div>

> [!IMPORTANT]
> **MihonNooir is independent and unofficial.** The latest official Android release is **[v1.0.20](https://github.com/toshio2011/mihon-nooir/releases/tag/v1.0.20)**, published 24 November 2026, based on Mihon v0.21.3. It uses the persistent signing certificate shared with official v1.0.19, v1.0.18 and v1.0.17. Back up your library and important downloaded chapters before updating. **From v1.0.17, download the correct APK manually** because the old updater could choose the wrong CPU architecture. Install over the existing app; do not uninstall or clear storage. If Android reports a signing/package conflict, stop and report it. Windows is still in development.

## Windows Compose preview (experimental)

[**Windows preview download and visual walkthrough**](docs/WINDOWS_COMPOSE_PREVIEW_GUIDE.md) · [Portable ZIP (CI artifact)](https://github.com/toshio2011/mihon-nooir/actions/runs/45952282235/artifacts/15177533204). Native Android-inspired interface; separate from the stable Android release and existing Swing preview. Physical Windows acceptance is pending.

## What's new in Android v1.0.20

- **Universal Book Converter:** choose input type and compatible output; convert local EPUB to XTC/XTCH with typography controls, or convert supported CBZ/ZIP/images.
- **Web → EPUB:** analyze and select chapters, preview text, edit book metadata, choose EPUB2/3, customize covers and reading styles. Public Wattpad story cover and synopsis are supported where available.
- **Finished-book preview:** inspect actual XTC/XTCH files from the converter.
- **Verified signed upgrade:** all five APKs match the official v1.0.19 certificate; Android 16 in-place upgrade and exact-artifact publication passed.

[Full v1.0.20 release notes](docs/releases/v1.0.20.md)

## Retained from Android v1.0.19

- **Smarter manga segmentation:** detect supported slanted/irregular layouts, independent spanning panels and stacked panels.
- **Second-look detection:** consolidate suspicious thin fragments and recover strongly framed internal gutters, while protecting genuine narrow panels.
- **Enlarge oversized splits:** enabled by default in Panel by Panel and Row by Row. Adjustable minimum screen fill (25–80%, default 50%); prefer verified natural boundaries, then use protective overlap only where it improves readability.
- **Artwork preservation:** avoid fragmenting supported continuous illustrations and activity sheets.
- **Existing features retained:** actual XTC/XTCH inspection, four rendering styles, Context Peek, PDF/EPUB conversion, queue and architecture-aware updater.

[Full v1.0.19 release notes and verification →](docs/releases/v1.0.19.md) · [Previous v1.0.18 release notes](docs/releases/v1.0.18.md)

## Supported export formats

- **EPUB 2 Classic**: genuine EPUB 2.0.1, OPF 2.0, NCX chapters, traditional cover-image metadata, author/synopsis when available.
- **EPUB 3 Modern**: existing modern fixed-image EPUB export remains a separate choice.
- **XTC Fast**: portable XTC 1-bit row-major bitmap pages and chapter records.
- **XTCH High Quality**: portable 2-bit, four-level grayscale with correct first-page cover and chapter indexing.
- **Reader presets**: X3, X4, X4 Pro, select Kindle, Kobo, Boox and PocketBook models, or a safe custom resolution. XTEink-only Wi-Fi delivery is disabled for other readers.
- **Cover choice**: include a real series cover, or start directly with the first selected chapter page. Portable XTC/XTCH exports omit the private JSON trailer by default; optional extended metadata is experimental.

[**Detailed compatibility, image encoding, synopsis, cover and reconversion guide →**](docs/EXPORT_FORMATS_EPUB2.md)

## What can MihonNooir do?

MihonNooir is built as a minimal Android overlay on the **full [Mihon](https://github.com/mihonapp/mihon)** app. Its aim is to keep Mihon's normal **reader, extensions, library, downloads, tracking, history, categories and backups**, while adding optional e-reader workflows.

| What you want to do | Where to start in **v1.0.20** | Guide |
|---|---|---|
| Export selected manga chapters | **Manga details → select chapters → Export** | [Choose chapters](https://github.com/toshio2011/mihon-nooir/blob/main/docs/CHAPTER_SELECTION.md) |
| Convert a manga to XTC / XTCH / EPUB / CBZ | **Manga details → Export → format / settings** | [Formats](https://github.com/toshio2011/mihon-nooir/blob/main/docs/CONVERSION.md) |
| Send CBZ/images or existing books from your phone | **More → Nooir Tools → Universal Book Converter → Local files** | [Nooir Tools walkthrough](https://github.com/toshio2011/mihon-nooir/blob/main/docs/NOOIR_TOOLS_QUICK_START.md) |
| Turn **one webpage** into an EPUB | **More → Nooir Tools → Universal Book Converter → Web link** | [Web link guide](https://github.com/toshio2011/mihon-nooir/blob/main/docs/WEB_TO_READER.md) |
| Turn **multiple webpage links into one EPUB** | **Web link → EPUB → Multiple web links → one EPUB** | [Multi-link / novels](https://github.com/toshio2011/mihon-nooir/blob/main/docs/WEB_NOVELS.md) |
| Download public web novel chapters | **More → Nooir Tools → Universal Book Converter → Web novels** | [Web novels](https://github.com/toshio2011/mihon-nooir/blob/main/docs/WEB_NOVELS.md) |
| Convert one or more PDFs into EPUB / XTC / XTCH | **More → Nooir Tools → Universal Book Converter → PDF files** | [PDF conversion](docs/PDF_TO_EPUB.md) |
| Preview manga settings before export | **Manga details → Export → Choose manga image or CBZ for preview → Preview first page** | [Conversion](docs/CONVERSION.md) |
| Inspect actual saved XTC/XTCH pages | **Manga details → Export → Inspect an exported XTC / XTCH file** | [v1.0.19 release notes](docs/releases/v1.0.19.md) |
| Watch transfers and diagnose a failed book | **More → Nooir Tools → Activity Queue** | [Queue and conflicts](https://github.com/toshio2011/mihon-nooir/blob/main/docs/DEVICE_FILES_AND_QUEUE.md) |
| Browse your XTEink's SD-card files | **More → Nooir Tools → Browse XTEink files** | [Reader files](https://github.com/toshio2011/mihon-nooir/blob/main/docs/DEVICE_FILES_AND_QUEUE.md) |

The **Nooir Tools** hub, web/PDF converters, shared queue and advanced controls are included in v1.0.20. Mihon's native chapter Download Queue is separate.

## Download and install Android v1.0.20

| Device | Download |
|---|---|
| Most modern Android phones (ARM64) | [app-arm64-v8a-foss.apk](https://github.com/toshio2011/mihon-nooir/releases/download/v1.0.20/app-arm64-v8a-foss.apk) |
| Unsure of architecture | [app-universal-foss.apk](https://github.com/toshio2011/mihon-nooir/releases/download/v1.0.20/app-universal-foss.apk) |
| Other supported architectures | [All official release assets](https://github.com/toshio2011/mihon-nooir/releases/tag/v1.0.20) |
| Verify your download | [SHA256SUMS.txt](https://github.com/toshio2011/mihon-nooir/releases/download/v1.0.20/SHA256SUMS.txt) |

1. Download the official APK directly; no ZIP extraction is necessary.
2. For an existing MihonNooir installation, back up your library and important downloads first. If upgrading from v1.0.17, use the manual download above.
3. Open the APK in Android's file manager and follow the installer. MihonNooir can coexist with ordinary Mihon.
4. Install over the existing official MihonNooir app. Do not uninstall or clear storage if Android refuses the update; older test-signed builds (including v1.0.12) may use a different key.
5. Confirm About displays **FOSS v1.0.20** and check your library, reading progress and downloads.

[Full installation / first export tutorial →](docs/GETTING_STARTED.md) · [Build/update policy](docs/UPDATES.md)

## Get a book onto your XTEink in five steps

1. **Select the manga chapters before opening Export.** Long-press the first chapter on a manga details page and tap the others. No selection + Normal Export means **all downloaded chapters**; Quick Export can fetch missing pages for selected online chapters through installed sources.
2. **Choose X3/X4 and output format.** XTC is black/white, XTCH has four grayscale levels, EPUB and CBZ contain page images. For Japanese manga, tap **More conversion settings → Apply recommended manga panel settings** (Panel by Panel + Auto Fit). Check **Enlarge oversized splits** (default on, 50% minimum screen fill). Use Page or Row by Row for different reading styles; preview the settings before export.
3. **Prepare the reader.** On Folio Nooir open **Home → File Transfer → Join Network** or **Create Hotspot**. Your phone must be able to reach the address shown on the reader.
4. **Test the connection in Chrome.** Open the reader's displayed HTTP address. In MihonNooir, enter **only** its IP/hostname (not `http://`) and an existing folder such as `/Books`.
5. Choose **Save file** for local storage or **Send to XTEink** over Wi-Fi. For large manga/PDF books, saving locally and copying to the SD card is often more practical. With Wi-Fi, wait for receiver confirmation; 100% uploaded is not confirmation of acceptance. Original Mihon downloads remain untouched.

**No router nearby?** Use the XTEink's temporary hotspot and the exact IP it displays. Its local web server has **no login or encryption**: use trusted networks and close File Transfer when finished.

[Recommended manga settings and Auto Fit](docs/PANEL_AUTO_FIT.md) · [**Wi-Fi walkthrough →**](https://github.com/toshio2011/mihon-nooir/blob/main/docs/WIFI_TRANSFER.md) · [Chapter selection examples](https://github.com/toshio2011/mihon-nooir/blob/main/docs/CHAPTER_SELECTION.md)

## More tools in the Android app

- **Web links and novels:** Convert one public article, combine **multiple public webpages into one reflowable EPUB** in the order pasted, or create **separate EPUBs** from up to 10 public story links. For discoverable chapter indexes, choose **All chapters** rather than typing a fixed end number. Manual combination has no fixed chapter-count ceiling but uses a 128 KiB private URL-list budget. JavaScript-only, login-only and inaccessible chapters may fail; no paywall/DRM bypass is offered.
- **PDF conversion:** Save EPUB 2/3 with selectable text and illustrations where supported, preserve original pages as images, or render to XTC/XTCH. Select multiple PDFs for separate queued books. Scan-only pages remain image-based; no OCR or AI. Complex typography, tables and diagrams still need review.
- **Safe file names and queue:** Choose names **before converting or transferring**. Existing destination names are checked and rejected instead of intentionally overwritten. One book's ordinary failure should **not stop later jobs** in the sequential Nooir Activity Queue. Cancelling the *entire queue* still cancels waiting jobs too.
- **XTC/XTCH covers:** Include a real series cover or start with the first selected chapter page. Folio Nooir separately caches shelf thumbnails. If a book's first page contains its cover but the shelf doesn't, use **Retrieve All Book Details** on the reader.
- **Reader file browser:** A lightweight read-only folder view; full rename/move/delete features stay in Folio Nooir's existing browser-based file manager.

These features are available in the current Android release. Individual source, input and firmware compatibility can vary. The XTEink Wi-Fi upload path has a conservative **180 MiB per-file** safety limit; it does not impose that limit on local PDF conversion. JavaScript-only website extraction and universal firmware metadata compatibility remain incomplete.

## Windows desktop — in development

Windows is being developed as a separate desktop app with a local manga library/reader, optional Suwayomi integration, conversion and saved-book inspection, a job queue and compatible XTEink transfer. The portable and installer builds are unsigned development previews; there is **no official public Windows release yet**. Android includes the reviewed oversized-split and panel-detection improvements; further Windows-only development remains separate.

[Windows development documentation](desktop/README.md) · [Open development pull requests](https://github.com/toshio2011/mihon-nooir/pulls)

## All user guides

| Guide | You'll learn |
|---|---|
| **[Nooir Tools quick start](https://github.com/toshio2011/mihon-nooir/blob/main/docs/NOOIR_TOOLS_QUICK_START.md)** | All six tools and common workflows in one walkthrough |
| [Getting started](https://github.com/toshio2011/mihon-nooir/blob/main/docs/GETTING_STARTED.md) | Install the right build, first export, file picker |
| [Choosing chapters](https://github.com/toshio2011/mihon-nooir/blob/main/docs/CHAPTER_SELECTION.md) | Normal vs Quick Export, specific ranges, all downloaded chapters |
| [Wi-Fi transfer](https://github.com/toshio2011/mihon-nooir/blob/main/docs/WIFI_TRANSFER.md) | Join Network/hotspot, addresses, receiver confirmation |
| [Recommended manga panel settings](docs/PANEL_AUTO_FIT.md) | Auto Fit, genuine narrow panels, oversized enlargement and what to do if a page is grouped |
| [Format and settings](https://github.com/toshio2011/mihon-nooir/blob/main/docs/CONVERSION.md) · [Landscape/overlap](https://github.com/toshio2011/mihon-nooir/blob/main/docs/ORIENTATION_OVERLAP.md) | XTC/XTCH/EPUB/CBZ, direction, trimming and page cuts |
| [Web link → EPUB](https://github.com/toshio2011/mihon-nooir/blob/main/docs/WEB_TO_READER.md) · [Multiple web links / novels](https://github.com/toshio2011/mihon-nooir/blob/main/docs/WEB_NOVELS.md) | One article, many pages in one EPUB, or many independent stories |
| [PDF → EPUB](https://github.com/toshio2011/mihon-nooir/blob/main/docs/PDF_TO_EPUB.md) | Reflowable text plus figures, scanned-page fallback, limitations |
| [Activity Queue and XTEink files](https://github.com/toshio2011/mihon-nooir/blob/main/docs/DEVICE_FILES_AND_QUEUE.md) | Failures, duplicate names, cover cache and device browser |
| [XTC cover/synopsis](https://github.com/toshio2011/mihon-nooir/blob/main/docs/XTC_METADATA.md) · [FAQ](https://github.com/toshio2011/mihon-nooir/blob/main/docs/FAQ.md) | Metadata compatibility and frequent questions |
| [Build/update policy](https://github.com/toshio2011/mihon-nooir/blob/main/docs/UPDATES.md) · [Developer handover](https://github.com/toshio2011/mihon-nooir/blob/main/docs/PROJECT_HANDOVER.md) | Signing, pinned Mihon, current development branches |

[**Browse the full guide index →**](https://github.com/toshio2011/mihon-nooir/blob/main/docs/README.md)

## Source, roadmap and feedback

This repository contains an **integration overlay**, not an independent rewrite of Mihon. Its [GitHub Actions workflow](.github/workflows/build-apk.yml) checks out the pinned Mihon revision from `config/mihon-upstream.sha`, applies `tools/apply_overlay.py`, runs smoke tests, and assembles a FOSS Android APK. Automated CI is not the same as on-device testing.

- **[Official Android v1.0.20 release](https://github.com/toshio2011/mihon-nooir/releases/tag/v1.0.20)** — latest published APKs, [verified upgrade and test evidence](docs/releases/v1.0.20.md). Real-device results depend on firmware and input.
- [**Roadmap / feature requests**](https://github.com/toshio2011/mihon-nooir/issues/8) — improvements and known work.
- [**Project handover**](https://github.com/toshio2011/mihon-nooir/blob/main/docs/PROJECT_HANDOVER.md) — contributor context and technical status.

For bug reports, include the **MihonNooir build/commit, Android version, input/source type, device and Folio Nooir firmware, destination folder, and exact error**. Please don't post copyrighted manga pages, credentials or private documents.

## Credits and licenses

- **[Mihon](https://github.com/mihonapp/mihon)** — Android manga reader and extension ecosystem. Mihon's original code remains **Apache-2.0**; thanks to the Mihon/Tachiyomi contributors and [Javier Tomás](https://github.com/inorichi).
- **[MangaPoint](https://github.com/W1seGit/MangaPoint)** — inspiration for e-reader conversion workflows and settings. Credits and its **MIT** license are retained in [license notices](LICENSES/MANGAPOINT-MIT.txt).
- **[Folio Nooir](https://github.com/toshio2011/folio-nooir)** — experimental XTEink firmware companion, built on work from **[CrossPoint Reader](https://github.com/crosspoint-reader/crosspoint-reader)**. Thanks to the firmware community.
- Community testers and contributors who help catch format, cover, transfer and reader issues.

The MihonNooir integration overlay is distributed under [MIT](LICENSE); modified Mihon code retains its upstream license and notices. MihonNooir is **independent and unofficial**, not affiliated with Mihon, MangaPoint, XTEink or manga providers. It **does not bundle manga, source extensions or a hosted catalog**; only access and transfer content you are entitled to use.
