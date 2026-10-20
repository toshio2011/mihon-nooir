<div align="center">

# MangaNooir

**Your Mihon manga library, plus tools to prepare books for an e-reader.**

Read manga on Android · Choose chapters · Convert books · Send to XTEink over Wi-Fi

**[Start here](docs/NOOIR_TOOLS_QUICK_START.md)** · [Installation](docs/GETTING_STARTED.md) · [All guides](docs/README.md) · [FAQ](docs/FAQ.md) · [Project roadmap](https://github.com/toshio2011/mihon-nooir/issues/8)

![Android](https://img.shields.io/badge/platform-Android-blue)
![Status](https://img.shields.io/badge/status-experimental-orange)
![Base](https://img.shields.io/badge/based_on-Mihon-informational)

</div>

> [!IMPORTANT]
> **MangaNooir is experimental and unofficial.** The `main` branch currently contains the **v1.0.1 baseline**, while the newer **v1.0.11 feature set is in [draft PR #18](https://github.com/toshio2011/mihon-nooir/pull/18)**. The [v1.0.11 Android build](https://github.com/toshio2011/mihon-nooir/actions/runs/39363272479) passed automated tests and produced an APK, but real-world source compatibility, PDF layout fidelity, phone operations and physical X3/X4 transfer/reading are **not yet verified**. There is no published stable GitHub Release. **Back up before replacing any test APK.**

## What can MangaNooir do?

MangaNooir is built as a minimal Android overlay on the **full [Mihon](https://github.com/mihonapp/mihon)** app. Its aim is to keep Mihon's normal **reader, extensions, library, downloads, tracking, history, categories and backups**, while adding optional e-reader workflows.

| What you want to do | Where to start in the **v1.0.11 preview** | Guide |
|---|---|---|
| Export selected manga chapters | **Manga details → select chapters → Export** | [Choose chapters](docs/CHAPTER_SELECTION.md) |
| Convert a manga to XTC / XTCH / EPUB / CBZ | **Manga details → Export → format / settings** | [Formats](docs/CONVERSION.md) |
| Send CBZ/images or existing books from your phone | **More → Nooir Tools → Send & Convert files** | [Nooir Tools walkthrough](docs/NOOIR_TOOLS_QUICK_START.md) |
| Turn **one webpage** into an EPUB | **More → Nooir Tools → Web link → EPUB** | [Web link guide](docs/WEB_TO_READER.md) |
| Turn **multiple webpage links into one EPUB** | **Web link → EPUB → Multiple web links → one EPUB** | [Multi-link / novels](docs/WEB_NOVELS.md) |
| Download public web novel chapters | **More → Nooir Tools → Web novels · Batch EPUB** | [Web novels](docs/WEB_NOVELS.md) |
| Turn PDF text + illustrations into EPUB | **More → Nooir Tools → PDF → EPUB** | [PDF conversion](docs/PDF_TO_EPUB.md) |
| Watch transfers and diagnose a failed book | **More → Nooir Tools → Activity Queue** | [Queue and conflicts](docs/DEVICE_FILES_AND_QUEUE.md) |
| Browse your XTEink's SD-card files | **More → Nooir Tools → Browse XTEink files** | [Reader files](docs/DEVICE_FILES_AND_QUEUE.md) |

**Version reminder:** The new **Nooir Tools** hub, web/PDF converters, shared queue, and advanced controls are **not in the v1.0.1 main-branch app**. Download the experimental v1.0.11 build to try those screens. Mihon's native chapter Download Queue is separate and unchanged.

## Install the experimental Android APK

There are **no stable Releases to install yet**. For testing:

1. Open the [latest verified v1.0.11 Android build](https://github.com/toshio2011/mihon-nooir/actions/runs/39363272479) (you may need to sign into GitHub).
2. Scroll to **Artifacts**, download **`mihon-nooir-foss-apk`**, and extract the ZIP.
3. Choose a suitable **signed** APK for your Android architecture (normally `arm64-v8a` on recent phones), then install it using Android's file manager.
4. Open **MangaNooir**. It has a separate app identity and can coexist with ordinary Mihon.
5. If installing over an older MangaNooir test APK fails, **back up your data first**; different CI signing keys can block an in-place update. Do not uninstall ordinary Mihon just to test this fork.

[**Full installation / first export tutorial →**](docs/GETTING_STARTED.md) · [Build and update policy](docs/UPDATES.md)

## Get a book onto your XTEink in five steps

1. **Select the manga chapters before opening Export.** Long-press the first chapter on a manga details page and tap the others. No selection + Normal Export means **all downloaded chapters**; Quick Export works on selected online chapters in the experimental preview.
2. **Choose X3/X4 and output format.** XTC is black/white, XTCH has four grayscale levels, EPUB and CBZ contain page images. For manga pages, advanced preview settings include layout, trimming, reading direction, portrait/landscape and overlapping cuts.
3. **Prepare the reader.** On Folio Nooir open **Home → File Transfer → Join Network** or **Create Hotspot**. Your phone must be able to reach the address shown on the reader.
4. **Test the connection in Chrome.** Open the reader's displayed HTTP address. In MangaNooir, enter **only** its IP/hostname (not `http://`) and an existing folder such as `/Books`.
5. Choose **Send to XTEink** and wait for **receiver confirmation**. 100% bytes uploaded does not by itself confirm the reader accepted the file. Original Mihon downloads remain untouched.

**No router nearby?** Use the XTEink's temporary hotspot and the exact IP it displays. Its local web server has **no login or encryption**: use trusted networks and close File Transfer when finished.

[**Wi-Fi walkthrough →**](docs/WIFI_TRANSFER.md) · [Chapter selection examples](docs/CHAPTER_SELECTION.md)

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
| **[Nooir Tools quick start](docs/NOOIR_TOOLS_QUICK_START.md)** | All six tools and common workflows in one walkthrough |
| [Getting started](docs/GETTING_STARTED.md) | Install the right build, first export, file picker |
| [Choosing chapters](docs/CHAPTER_SELECTION.md) | Normal vs Quick Export, specific ranges, all downloaded chapters |
| [Wi-Fi transfer](docs/WIFI_TRANSFER.md) | Join Network/hotspot, addresses, receiver confirmation |
| [Format and settings](docs/CONVERSION.md) · [Landscape/overlap](docs/ORIENTATION_OVERLAP.md) | XTC/XTCH/EPUB/CBZ, direction, trimming and page cuts |
| [Web link → EPUB](docs/WEB_TO_READER.md) · [Multiple web links / novels](docs/WEB_NOVELS.md) | One article, many pages in one EPUB, or many independent stories |
| [PDF → EPUB](docs/PDF_TO_EPUB.md) | Reflowable text plus figures, scanned-page fallback, limitations |
| [Activity Queue and XTEink files](docs/DEVICE_FILES_AND_QUEUE.md) | Failures, duplicate names, cover cache and device browser |
| [XTC cover/synopsis](docs/XTC_METADATA.md) · [FAQ](docs/FAQ.md) | Metadata compatibility and frequent questions |
| [Build/update policy](docs/UPDATES.md) · [Developer handover](docs/PROJECT_HANDOVER.md) | Signing, pinned Mihon, current development branches |

[**Browse the full guide index →**](docs/README.md)

## Source, roadmap and feedback

This repository contains an **integration overlay**, not an independent rewrite of Mihon. Its [GitHub Actions workflow](.github/workflows/build-apk.yml) checks out the pinned Mihon revision from `config/mihon-upstream.sha`, applies `tools/apply_overlay.py`, runs smoke tests, and assembles a FOSS Android APK. Automated CI is not the same as on-device testing.

- [**Open experimental PR #18**](https://github.com/toshio2011/mihon-nooir/pull/18) — latest feature stack under testing (not merged).
- [**Roadmap / feature requests**](https://github.com/toshio2011/mihon-nooir/issues/8) — improvements and known work.
- [**Project handover**](docs/PROJECT_HANDOVER.md) — contributor context and technical status.

For bug reports, include the **MangaNooir build/commit, Android version, input/source type, device and Folio Nooir firmware, destination folder, and exact error**. Please don't post copyrighted manga pages, credentials or private documents.

## Credits and licenses

- **[Mihon](https://github.com/mihonapp/mihon)** — Android manga reader and extension ecosystem. Mihon's original code remains **Apache-2.0**; thanks to the Mihon/Tachiyomi contributors and [Javier Tomás](https://github.com/inorichi).
- **[MangaPoint](https://github.com/W1seGit/MangaPoint)** — inspiration for e-reader conversion workflows and settings. Credits and its **MIT** license are retained in [license notices](LICENSES/MANGAPOINT-MIT.txt).
- **[Folio Nooir](https://github.com/toshio2011/folio-nooir)** — experimental XTEink firmware companion, built on work from **[CrossPoint Reader](https://github.com/crosspoint-reader/crosspoint-reader)**. Thanks to the firmware community.
- Community testers and contributors who help catch format, cover, transfer and reader issues.

The MangaNooir integration overlay is distributed under [MIT](LICENSE); modified Mihon code retains its upstream license and notices. MangaNooir is **independent and unofficial**, not affiliated with Mihon, MangaPoint, XTEink or manga providers. It **does not bundle manga, source extensions or a hosted catalog**; only access and transfer content you are entitled to use.
