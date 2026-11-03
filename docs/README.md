# MangaNooir — user guides

[← Repository homepage](../README.md) · [Reader diagnostic samples](READER_INTEROP_DIAGNOSTICS.md) · [Start with Nooir Tools](NOOIR_TOOLS_QUICK_START.md) · [Frequently asked questions](FAQ.md)

> [!IMPORTANT]
> **Choose the guide for your installed version.** The latest verified public [MihonNooir v1.0.16 official release](https://github.com/toshio2011/mihon-nooir/releases/tag/v1.0.16) was published on **29 October 2026**. Android **v1.0.17** panel refinements remain **experimental**, in [draft PR #57](https://github.com/toshio2011/mihon-nooir/pull/57); they are not a released APK. CI QA APKs are debug-signed and must not be installed as updates over the official release-signed app. For current development/QA status see the [3 November handover](HANDOVER_2026-11-03_ANDROID_PANEL_V1.0.17.md). Historical v1.0.12 instructions apply to that older release only.

## If this is your first time

1. [**Install MangaNooir and export your first book**](GETTING_STARTED.md) — APK download, Android installation, selection.
2. [**Nooir Tools: six features explained**](NOOIR_TOOLS_QUICK_START.md) — short v1.0.11 user walkthrough.
3. [**Connect your phone and XTEink over Wi-Fi**](WIFI_TRANSFER.md) — trusted LAN/hotspot and recipient confirmation.
4. [**FAQ and troubleshooting**](FAQ.md) — solve common mistakes without digging through code.

## Guides by what you want to do

| I want to… | Read this |
|---|---|
| Pick certain manga chapters instead of the entire series | [Chapter selection and Quick Export](CHAPTER_SELECTION.md) |
| Convert manga into XTC, XTCH, EPUB or CBZ | [Formats and conversion](CONVERSION.md) |
| Read portrait/landscape and overlapping manga pages | [Orientation and overlap](ORIENTATION_OVERLAP.md) |
| Convert one public webpage/article to EPUB | [Web link → EPUB](WEB_TO_READER.md) |
| **Combine multiple webpages into one EPUB** | [Multi-link EPUB / web novels](WEB_NOVELS.md#new-combine-individual-webpages-into-one-epub-experimental-v1011) |
| Turn all discoverable public web-novel chapters into EPUB | [Web novels, batch import and All chapters](WEB_NOVELS.md) |
| Convert PDF novels with **selectable text + illustrations** | [PDF → EPUB guide](PDF_TO_EPUB.md) |
| Send phone files or monitor conversions | [Nooir Tools quick start](NOOIR_TOOLS_QUICK_START.md) |
| See queue history, rename an existing destination file, fix missing cover | [Queue / XTEink files / covers](DEVICE_FILES_AND_QUEUE.md) |
| Understand XTC/XTCH cover and synopsis fields | [XTC/XTCH metadata and Folio Nooir](XTC_METADATA.md) |

## For contributors and testers

| Document | Purpose |
|---|---|
| [Build/update policy](UPDATES.md) | Pinned Mihon, Android signatures, release constraints |
| [**Current 3 Nov Android v1.0.17 panel handover**](HANDOVER_2026-11-03_ANDROID_PANEL_V1.0.17.md) | Latest PR #57 branch, One Piece QA, debug-APK/CI pitfalls, next test steps and copy-paste continuation prompt |
| [Developer handover](PROJECT_HANDOVER.md) | Project architecture plus historical handover snapshots |
| [Illustrated-novel PDF QA checklist](PDF_ILLUSTRATED_NOVEL_QA.md) | Privacy-safe representative test requirements (not test source files) |
| [**Latest public v1.0.16 release**](https://github.com/toshio2011/mihon-nooir/releases/tag/v1.0.16) | Official signed Android release; do not replace with debug-signed PR #57 QA APK |
| [Automatic Panel-by-Panel implementation status](PANEL_IMPLEMENTATION_STATUS.md) | Historical panel implementation status; later PR #57 quality work remains experimental and requires real-book/X4 QA |
| [Panel EPUB comparison QA](PANEL_EXPORT_QA.md) | Offline frame-by-frame content matching for old versus new exports, without uploading manga artwork |
| [Roadmap #8](https://github.com/toshio2011/mihon-nooir/issues/8) | Upcoming improvements and incomplete items |

**Reminder:** A successful CI build doesn't establish that an individual public website exposes readable chapters, that PDF text reflows flawlessly, or that XTC/XTCH covers behave identically on every XTEink firmware. The app contains no manga, bundled manga extensions, AI OCR or access-control bypass.
