# MihonNooir — user guides

**Developer handover:** [Historical development checkpoints](PROJECT_HANDOVER.md). Historical snapshots describe their own dated branches, not the latest public app.

[← Repository homepage](../README.md) · [Reader diagnostic samples](READER_INTEROP_DIAGNOSTICS.md) · [Start with Nooir Tools](NOOIR_TOOLS_QUICK_START.md) · [Frequently asked questions](FAQ.md)

> [!IMPORTANT]
> **The latest official Android release is [v1.0.20](https://github.com/toshio2011/mihon-nooir/releases/tag/v1.0.20)** (24 November 2026). See [release notes](releases/v1.0.20.md) for Universal Book Converter, EPUB → XTC/XTCH, Web → EPUB and signed upgrade verification. Existing manga and PDF tools are retained. Windows remains in development.


**Visual walkthroughs:** [Android visual user guide](ANDROID_VISUAL_GUIDE.md) — actual release-source emulator screenshots and task coverage. [Desktop preview guide](https://github.com/toshio2011/mihon-nooir/blob/desktop/final-acceptance-20261126/desktop/DESKTOP_GUIDE.md) — separate development preview.

**Manga export and conversion gallery:** [Export manga and compare conversion modes](MANGA_EXPORT_GALLERY.md) — recommended settings, actual saved-file inspection examples, and explicit pending manga comparison captures.

[View the real Android screenshots for this workflow](ANDROID_VISUAL_GUIDE.md#first-launch-and-local-book-conversion) (v1.0.20 release-source emulator).

**Universal Book Converter:** [Visual walkthrough and actual Android EPUB output](UNIVERSAL_CONVERTER_VISUAL_GUIDE.md) — local EPUB, PDF, web novels, combined chapters and saved-file inspection.

**Library, extensions, backup and transfer:** [Visual migration and library guide](LIBRARY_MIGRATION_VISUAL_GUIDE.md) — real desktop captures, Android instructions and explicit verification limits.

## Visual walkthroughs by task

Choose your platform and task. **Captured** means relevant real screens exist; **partial** means the complete journey or output is not pictured. Desktop remains a development preview, and its images are from Windows QA rather than every supported operating system.

| Task | Android | Desktop preview |
|---|---|---|
| Install and set up | [First launch](ANDROID_VISUAL_GUIDE.md#a-finish-first-run-setup) · partial | [Packages and setup](https://github.com/toshio2011/mihon-nooir/blob/desktop/final-acceptance-20261126/desktop/DESKTOP_GUIDE.md#choose-the-package-for-your-computer) · partial |
| Extensions and manga reading | [Getting started](GETTING_STARTED.md) · text; captures pending | [Extension setup](https://github.com/toshio2011/mihon-nooir/blob/desktop/final-acceptance-20261126/desktop/DESKTOP_GUIDE.md#first-launch-and-extension-setup) · [Reader](https://github.com/toshio2011/mihon-nooir/blob/desktop/final-acceptance-20261126/desktop/DESKTOP_GUIDE.md#read-manga) · captured |
| Select and export manga | [Chapter selection](CHAPTER_SELECTION.md) · captures pending | [Chapter browsing](https://github.com/toshio2011/mihon-nooir/blob/desktop/final-acceptance-20261126/desktop/DESKTOP_GUIDE.md#read-manga) · captured; end-to-end export capture pending |
| Compare manga conversion settings | [Manga comparison gallery](MANGA_EXPORT_GALLERY.md) · desktop output evidence; Android comparison pending | [Actual segmentation and formats](MANGA_EXPORT_GALLERY.md) · verified desktop results, image publication partial |
| Convert local EPUB, CBZ or images | [Local files](ANDROID_VISUAL_GUIDE.md#c-local-epub-cbzzip-or-images) · partial | [Universal Converter](https://github.com/toshio2011/mihon-nooir/blob/desktop/final-acceptance-20261126/desktop/DESKTOP_GUIDE.md#universal-book-converter) · partial |
| Inspect finished XTC/XTCH | [Actual saved-file viewer](ANDROID_VISUAL_GUIDE.md#d-inspect-a-finished-xtcxtch) · captured | [Saved-file viewer](https://github.com/toshio2011/mihon-nooir/blob/desktop/final-acceptance-20261126/desktop/DESKTOP_GUIDE.md#universal-book-converter) · captured |
| Convert PDF | [PDF input](ANDROID_VISUAL_GUIDE.md#e-convert-a-pdf) · partial | [Universal Converter](https://github.com/toshio2011/mihon-nooir/blob/desktop/final-acceptance-20261126/desktop/DESKTOP_GUIDE.md#universal-book-converter) · text; PDF captures pending |
| Web novel to EPUB | [Web editor](ANDROID_VISUAL_GUIDE.md#2-create-one-epub-per-public-story) · partial | [Chapter editor](https://github.com/toshio2011/mihon-nooir/blob/desktop/final-acceptance-20261126/desktop/DESKTOP_GUIDE.md#public-web-pages-and-novels) · partial |
| Combine chapters, covers and synopsis | [Combined book](ANDROID_VISUAL_GUIDE.md#3-combine-public-chapters-into-one-book) · [Book details](ANDROID_VISUAL_GUIDE.md#4-book-details-and-covers) · partial | [Book details and export](https://github.com/toshio2011/mihon-nooir/blob/desktop/final-acceptance-20261126/desktop/DESKTOP_GUIDE.md#public-web-pages-and-novels) · partial |
| Save projects and check new chapters | [Saved projects](ANDROID_VISUAL_GUIDE.md#5-chapter-titles-and-saved-projects) · partial | [Web projects](https://github.com/toshio2011/mihon-nooir/blob/desktop/final-acceptance-20261126/desktop/DESKTOP_GUIDE.md#public-web-pages-and-novels) · text; completion captures pending |
| Transfer and queue | [Wi-Fi transfer](WIFI_TRANSFER.md) · [Queue](DEVICE_FILES_AND_QUEUE.md) · captures pending | [Data and transfer](https://github.com/toshio2011/mihon-nooir/blob/desktop/final-acceptance-20261126/desktop/DESKTOP_GUIDE.md#your-data-and-updates) · captures pending |
| Backup and migrate | [Android setup and backup precautions](GETTING_STARTED.md) · captures pending | [Backup and migration](https://github.com/toshio2011/mihon-nooir/blob/desktop/final-acceptance-20261126/desktop/DESKTOP_GUIDE.md#move-your-library-between-android-and-desktop-development-candidate) · partial; physical Android round-trip not yet verified |

For screenshot coverage and provenance, see [Android capture status](ANDROID_VISUAL_GUIDE.md#screenshot-coverage-by-task) and [desktop capture status](https://github.com/toshio2011/mihon-nooir/blob/desktop/final-acceptance-20261126/desktop/SCREENSHOT_COVERAGE.md).

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
