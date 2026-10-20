<div align="center">

# MangaNooir

**Your Mihon manga library, plus tools to prepare books for an e-reader.**

Read manga on Android · Choose chapters · Convert books · Send to XTEink over Wi-Fi

**[Four export formats in v1.0.12](docs/EXPORT_FORMATS_EPUB2.md)** · **[Start here](https://github.com/toshio2011/mihon-nooir/blob/main/docs/NOOIR_TOOLS_QUICK_START.md)** · [Installation](https://github.com/toshio2011/mihon-nooir/blob/main/docs/GETTING_STARTED.md) · [All guides](https://github.com/toshio2011/mihon-nooir/blob/main/docs/README.md) · [FAQ](https://github.com/toshio2011/mihon-nooir/blob/main/docs/FAQ.md) · [Project roadmap](https://github.com/toshio2011/mihon-nooir/issues/8)

![Android](https://img.shields.io/badge/platform-Android-blue)
![Status](https://img.shields.io/badge/status-experimental-orange)
![Base](https://img.shields.io/badge/based_on-Mihon-informational)

</div>

> [!IMPORTANT]
> **MangaNooir is experimental and unofficial.** The latest public APK is [v1.0.11 Experimental Preview](https://github.com/toshio2011/mihon-nooir/releases/tag/v1.0.11). The **unreleased v1.0.12 changes on this branch** add EPUB 2 Classic and expanded reader profiles under [draft PR #21](https://github.com/toshio2011/mihon-nooir/pull/21). The `main` application source remains v1.0.1; this feature stack is not merged. No v1.0.12 APK has been published.

## Experimental v1.0.12 export improvements (not released)

- **EPUB 2 Classic**: genuine EPUB 2.0.1, OPF 2.0, NCX chapters, traditional cover-image metadata, author/synopsis when available.
- **EPUB 3 Modern**: existing modern fixed-image EPUB export remains a separate choice.
- **XTC Fast**: portable XTC 1-bit row-major bitmap pages and chapter records.
- **XTCH High Quality**: portable 2-bit, four-level grayscale with correct first-page cover and chapter indexing.
- **Reader presets**: X3, X4, X4 Pro, select Kindle, Kobo, Boox and PocketBook models, or a safe custom resolution. XTEink-only Wi-Fi delivery is disabled for other readers.
- **Cover choice**: include a real series cover, or start directly with the first selected chapter page. Portable XTC/XTCH exports no longer contain an undocumented JSON trailer.

[**Detailed compatibility, image encoding, synopsis, cover and reconversion guide →**](docs/EXPORT_FORMATS_EPUB2.md)

## What can MangaNooir do?

MangaNooir is built as a minimal Android overlay on the **full [Mihon](https://github.com/mihonapp/mihon)** app. Its aim is to keep Mihon's normal **reader, extensions, library, downloads, tracking, history, categories and backups**, while adding optional e-reader workflows.

| What you want to do | Where to start in the **v1.0.11 preview** | Guide |
|---|---|---|
| Export selected manga chapters | **Manga details → select chapters → Export** | [Choose chapters](https://github.com/toshio2011/mihon-nooir/blob/main/docs/CHAPTER_SELECTION.md) |
| Convert a manga to XTC / XTCH / EPUB / CBZ | **Manga details → Export → format / settings** | [Formats](https://github.com/toshio2011/mihon-nooir/blob/main/docs/CONVERSION.md) |
| Send CBZ/images or existing books from your phone | **More → Nooir Tools → Send & Convert files** | [Nooir Tools walkthrough](https://github.com/toshio2011/mihon-nooir/blob/main/docs/NOOIR_TOOLS_QUICK_START.md) |
| Turn **one webpage** into an EPUB | **More → Nooir Tools → Web link → EPUB** | [Web link guide](https://github.com/toshio2011/mihon-nooir/blob/main/docs/WEB_TO_READER.md) |
| Turn **multiple webpage links into one EPUB** | **Web link → EPUB → Multiple web links → one EPUB** | [Multi-link / novels](https://github.com/toshio2011/mihon-nooir/blob/main/docs/WEB_NOVELS.md) |
| Download public web novel chapters | **More → Nooir Tools → Web novels · Batch EPUB** | [Web novels](https://github.com/toshio2011/mihon-nooir/blob/main/docs/WEB_NOVELS.md) |
| Turn PDF text + illustrations into EPUB | **More → Nooir Tools → PDF → EPUB** | [PDF conversion](https://github.com/toshio2011/mihon-nooir/blob/main/docs/PDF_TO_EPUB.md) |
| Watch transfers and diagnose a failed book | **More → Nooir Tools → Activity Queue** | [Queue and conflicts](https://github.com/toshio2011/mihon-nooir/blob/main/docs/DEVICE_FILES_AND_QUEUE.md) |
| Browse your XTEink's SD-card files | **More → Nooir Tools → Browse XTEink files** | [Reader files](https://github.com/toshio2011/mihon-nooir/blob/main/docs/DEVICE_FILES_AND_QUEUE.md) |

**Version reminder:** The new **Nooir Tools** hub, web/PDF converters, shared queue, and advanced controls are **not in the v1.0.1 main-branch app**. Download the experimental v1.0.11 build to try those screens. Mihon's native chapter Download Queue is separate and unchanged.

## Install the experimental Android APK

The publicly available [v1.0.11 experimental prerelease](https://github.com/toshio2011/mihon-nooir/releases/tag/v1.0.11) is available for installing; it does **not** include the v1.0.12 changes. For v1.0.11:

1. Open the [v1.0.11 release](https://github.com/toshio2011/mihon-nooir/releases/tag/v1.0.11).
2. Under **Assets**, download an `.apk` directly; no ZIP extraction is required.
3. Choose a suitable **signed** APK for your Android architecture (normally `arm64-v8a` on recent phones), then install it using Android's file manager.
4. Open **MangaNooir**. It has a separate app identity and can coexist with ordinary Mihon.
5. If installing over an older MangaNooir test APK fails, **back up your data first**; different CI signing keys can block an in-place update. Do not uninstall ordinary Mihon just to test this fork.

[**Full installation / first export tutorial →**](https://github.com/toshio2011/mihon-nooir/blob/main/docs/GETTING_STARTED.md) · [Build and update policy](https://github.com/toshio2011/mihon-nooir/blob/main/docs/UPDATES.md)

## Get a book onto your XTEink in five steps

1. **Select the manga chapters before opening Export.** Long-press the first chapter on a manga details page and tap the others. No selection + Normal Export means **all downloaded chapters**; Quick Export works on selected online chapters in the experimental preview.
2. **Choose X3/X4 and output format.** XTC is black/white, XTCH has four grayscale levels, EPUB and CBZ contain page images. For manga pages, advanced preview settings include layout, trimming, reading direction, portrait/landscape and overlapping cuts.
3. **Prepare the reader.** On Folio Nooir open **Home → File Transfer → Join Network** or **Create Hotspot**. Your phone must be able to reach the address shown on the reader.
4. **Test the connection in Chrome.** Open the reader's displayed HTTP address. In MangaNooir, enter **only** its IP/hostname (not `http://`) and an existing folder such as `/Books`.
5. Choose **Send to XTEink** and wait for **receiver confirmation**. 100% bytes uploaded does not by itself confirm the reader accepted the file. Original Mihon downloads remain untouched.

**No router nearby?** Use the XTEink's temporary hotspot and the exact IP it displays. Its local web server has **no login or encryption**: use trusted networks and close File Transfer when finished.

[**Wi-Fi walkthrough →**](https://github.com/toshio2011/mihon-nooir/blob/main/docs/WIFI_TRANSFER.md) · [Chapter selection examples](https://github.com/toshio2011/mihon-nooir/blob/main/docs/CHAPTER_SELECTION.md)

## New in the experimental v1.0.11 tools

- **Web links and novels:** Convert one public article, combine **multiple public webpages into one reflowable EPUB** in the order pasted, or create **separate EPUBs** from up to 10 public story links. For discoverable chapter indexes, choose **All chapters** rather than typing a fixed end number. Manual combination has no fixed chapter-count ceiling but uses a 128 KiB private URL-list budget. JavaScript-only, login-only and inaccessible chapters may fail; no paywall/DRM bypass is offered.
- **PDF → EPUB:** Attempts to preserve genuine **selectable text alongside figures/illustrations** in the same ebook. Scan-only and difficult diagram pages stay as page images, without OCR or AI. Fancy typography, tables and image positioning still need human review.
- **Safe file names and queue:** Choose names **before converting or transferring**. Existing destination names are checked and rejected instead of intentionally overwritten. One book's ordinary failure should **not stop later jobs** in the sequential Nooir Activity Queue. Cancelling the *entire queue* still cancels waiting jobs too.
- **XTC/XTCH covers:** The preview requires a readable manga cover as the first book page; Folio Nooir separately caches shelf thumbnails. If page one has the cover but the shelf doesn't, use **Retrieve All Book Details** on the reader.
- **Reader file browser:** A lightweight read-only folder view; full rename/move/delete features stay in Folio Nooir's existing browser-based file manager.

These statements describe the current **compiled preview code**, not proven physical compatibility. The XTEink upload path currently has a conservative **180 MiB per-file** application-level safety limit. MangaPoint parity, automatic splitting, full JavaScript website extraction, firmware-independent metadata and more live previews remain unfinished.

## All user guides

| Guide | You'll learn |
|---|---|
| **[Nooir Tools quick start](https://github.com/toshio2011/mihon-nooir/blob/main/docs/NOOIR_TOOLS_QUICK_START.md)** | All six tools and common workflows in one walkthrough |
| [Getting started](https://github.com/toshio2011/mihon-nooir/blob/main/docs/GETTING_STARTED.md) | Install the right build, first export, file picker |
| [Choosing chapters](https://github.com/toshio2011/mihon-nooir/blob/main/docs/CHAPTER_SELECTION.md) | Normal vs Quick Export, specific ranges, all downloaded chapters |
| [Wi-Fi transfer](https://github.com/toshio2011/mihon-nooir/blob/main/docs/WIFI_TRANSFER.md) | Join Network/hotspot, addresses, receiver confirmation |
| [Format and settings](https://github.com/toshio2011/mihon-nooir/blob/main/docs/CONVERSION.md) · [Landscape/overlap](https://github.com/toshio2011/mihon-nooir/blob/main/docs/ORIENTATION_OVERLAP.md) | XTC/XTCH/EPUB/CBZ, direction, trimming and page cuts |
| [Web link → EPUB](https://github.com/toshio2011/mihon-nooir/blob/main/docs/WEB_TO_READER.md) · [Multiple web links / novels](https://github.com/toshio2011/mihon-nooir/blob/main/docs/WEB_NOVELS.md) | One article, many pages in one EPUB, or many independent stories |
| [PDF → EPUB](https://github.com/toshio2011/mihon-nooir/blob/main/docs/PDF_TO_EPUB.md) | Reflowable text plus figures, scanned-page fallback, limitations |
| [Activity Queue and XTEink files](https://github.com/toshio2011/mihon-nooir/blob/main/docs/DEVICE_FILES_AND_QUEUE.md) | Failures, duplicate names, cover cache and device browser |
| [XTC cover/synopsis](https://github.com/toshio2011/mihon-nooir/blob/main/docs/XTC_METADATA.md) · [FAQ](https://github.com/toshio2011/mihon-nooir/blob/main/docs/FAQ.md) | Metadata compatibility and frequent questions |
| [Build/update policy](https://github.com/toshio2011/mihon-nooir/blob/main/docs/UPDATES.md) · [Developer handover](https://github.com/toshio2011/mihon-nooir/blob/main/docs/PROJECT_HANDOVER.md) | Signing, pinned Mihon, current development branches |

[**Browse the full guide index →**](https://github.com/toshio2011/mihon-nooir/blob/main/docs/README.md)

## Source, roadmap and feedback

This repository contains an **integration overlay**, not an independent rewrite of Mihon. Its [GitHub Actions workflow](.github/workflows/build-apk.yml) checks out the pinned Mihon revision from `config/mihon-upstream.sha`, applies `tools/apply_overlay.py`, runs smoke tests, and assembles a FOSS Android APK. Automated CI is not the same as on-device testing.

- [**Open experimental PR #18**](https://github.com/toshio2011/mihon-nooir/pull/18) — latest feature stack under testing (not merged).
- [**Roadmap / feature requests**](https://github.com/toshio2011/mihon-nooir/issues/8) — improvements and known work.
- [**Project handover**](https://github.com/toshio2011/mihon-nooir/blob/main/docs/PROJECT_HANDOVER.md) — contributor context and technical status.

For bug reports, include the **MangaNooir build/commit, Android version, input/source type, device and Folio Nooir firmware, destination folder, and exact error**. Please don't post copyrighted manga pages, credentials or private documents.

## Credits and licenses

- **[Mihon](https://github.com/mihonapp/mihon)** — Android manga reader and extension ecosystem. Mihon's original code remains **Apache-2.0**; thanks to the Mihon/Tachiyomi contributors and [Javier Tomás](https://github.com/inorichi).
- **[MangaPoint](https://github.com/W1seGit/MangaPoint)** — inspiration for e-reader conversion workflows and settings. Credits and its **MIT** license are retained in [license notices](LICENSES/MANGAPOINT-MIT.txt).
- **[Folio Nooir](https://github.com/toshio2011/folio-nooir)** — experimental XTEink firmware companion, built on work from **[CrossPoint Reader](https://github.com/crosspoint-reader/crosspoint-reader)**. Thanks to the firmware community.
- Community testers and contributors who help catch format, cover, transfer and reader issues.

The MangaNooir integration overlay is distributed under [MIT](LICENSE); modified Mihon code retains its upstream license and notices. MangaNooir is **independent and unofficial**, not affiliated with Mihon, MangaPoint, XTEink or manga providers. It **does not bundle manga, source extensions or a hosted catalog**; only access and transfer content you are entitled to use.
