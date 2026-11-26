# MihonNooir — user guides

**Developer handover:** [Historical development checkpoints](PROJECT_HANDOVER.md). Historical snapshots describe their own dated branches, not the latest public app.

[← Repository homepage](../README.md) · [Reader diagnostic samples](READER_INTEROP_DIAGNOSTICS.md) · [Start with Nooir Tools](NOOIR_TOOLS_QUICK_START.md) · [Frequently asked questions](FAQ.md)

> [!IMPORTANT]
> **The latest official Android release is [v1.0.20](https://github.com/toshio2011/mihon-nooir/releases/tag/v1.0.20)** (24 November 2026). See [release notes](releases/v1.0.20.md) for Universal Book Converter, EPUB → XTC/XTCH, Web → EPUB and signed upgrade verification. Existing manga and PDF tools are retained. Windows remains in development.


**Visual walkthroughs:** [Android visual user guide](ANDROID_VISUAL_GUIDE.md) — actual release-source emulator screenshots and task coverage. [Desktop preview guide](https://github.com/toshio2011/mihon-nooir/blob/desktop/final-acceptance-20261126/desktop/DESKTOP_GUIDE.md) — separate development preview.

**Manga export and conversion gallery:** [Export manga and compare conversion modes](MANGA_EXPORT_GALLERY.md) — recommended settings, actual saved-file inspection examples, and explicit pending manga comparison captures.

[View the real Android screenshots for this workflow](ANDROID_VISUAL_GUIDE.md#first-launch-and-local-book-conversion) (v1.0.20 release-source emulator).

## If this is your first time

1. [**Install MihonNooir and export your first book**](GETTING_STARTED.md) — APK download, Android installation, selection.
2. [**Nooir Tools: quick start**](NOOIR_TOOLS_QUICK_START.md) — walkthrough of the Universal Book Converter, queue and reader tools.
3. [**Connect your phone and XTEink over Wi-Fi**](WIFI_TRANSFER.md) — trusted LAN/hotspot and recipient confirmation.
4. [**FAQ and troubleshooting**](FAQ.md) — solve common mistakes without digging through code.

## Guides by what you want to do

| I want to… | Read this |
|---|---|
| Pick certain manga chapters instead of the entire series | [Chapter selection and Quick Export](CHAPTER_SELECTION.md) |
| Convert manga into XTC, XTCH, EPUB or CBZ | [Manga export and gallery](MANGA_EXPORT_GALLERY.md) · [Formats and conversion](CONVERSION.md) |
| Read individual Japanese manga panels | [Recommended manga settings and Auto Fit](PANEL_AUTO_FIT.md) |
| Read portrait/landscape and overlapping manga pages | [Orientation and overlap](ORIENTATION_OVERLAP.md) |
| Convert one public webpage/article to EPUB | [Web link → EPUB](WEB_TO_READER.md) |
| **Combine multiple webpages into one EPUB** | [Multi-link EPUB / web novels](WEB_NOVELS.md#combine-individual-webpages-into-one-epub) |
| Turn all discoverable public web-novel chapters into EPUB | [Web novels, batch import and All chapters](WEB_NOVELS.md) |
| Convert PDFs with **selectable text + illustrations**, or original pages to XTC/XTCH | [PDF guide](PDF_TO_EPUB.md) · [v1.0.20 notes](releases/v1.0.20.md) |
| Compare e-ink rendering styles and inspect saved XTC/XTCH files | [Conversion and both previews](CONVERSION.md#two-different-previews) |
| Send phone files or monitor conversions | [Nooir Tools quick start](NOOIR_TOOLS_QUICK_START.md) |
| See queue history, rename an existing destination file, fix missing cover | [Queue / XTEink files / covers](DEVICE_FILES_AND_QUEUE.md) |
| Understand XTC/XTCH cover and synopsis fields | [XTC/XTCH metadata and Folio Nooir](XTC_METADATA.md) |

## For contributors and testers

| Document | Purpose |
|---|---|
| [Build/update policy](UPDATES.md) | Pinned Mihon, Android signatures, release constraints |
| [Developer handover](PROJECT_HANDOVER.md) | Project architecture and the next development steps |
| [Illustrated-novel PDF QA checklist](PDF_ILLUSTRATED_NOVEL_QA.md) | Privacy-safe representative test requirements (not test source files) |
| [v1.0.20 release](https://github.com/toshio2011/mihon-nooir/releases/tag/v1.0.20) · [Release notes and test evidence](releases/v1.0.20.md) | Current official Android APKs, changes and upgrade verification |
| [Automatic Panel-by-Panel implementation status](PANEL_IMPLEMENTATION_STATUS.md) | Historical panel implementation notes; see current release notes for shipped behavior |
| [Panel EPUB comparison QA](PANEL_EXPORT_QA.md) | Offline frame-by-frame content matching for old versus new exports, without uploading manga artwork |
| [Roadmap #8](https://github.com/toshio2011/mihon-nooir/issues/8) | Upcoming improvements and incomplete items |

**Reminder:** A successful CI build doesn't establish that an individual public website exposes readable chapters, that PDF text reflows flawlessly, or that XTC/XTCH covers behave identically on every XTEink firmware. The app contains no manga, bundled manga extensions, AI OCR or access-control bypass.
