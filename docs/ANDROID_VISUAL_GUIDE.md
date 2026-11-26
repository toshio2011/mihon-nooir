# MihonNooir Android v1.0.20 — visual walkthrough

This is the Android guide, separate from the desktop development preview. Install the [official signed v1.0.20 release](https://github.com/toshio2011/mihon-nooir/releases/tag/v1.0.20). The screenshots below are actual raw Android emulator captures from the release-source integration run [45352687891](https://github.com/toshio2011/mihon-nooir/actions/runs/45352687891), not screenshots of the signed APK or a personal device. The exact [screenshot archive](https://github.com/toshio2011/mihon-nooir/actions/runs/45352687891/artifacts/14901798319) contains eight images and capture notes; Actions login and retention apply.

## 1. Find Nooir Tools

Open MihonNooir. Tap **More** (bottom-right) → **Nooir Tools** → **Universal Book Converter** → **Web novels**. The captured emulator followed these actual navigation labels. The library is deliberately empty in the test profile.

Screenshot: `00-launch-state.png` (bottom navigation).

## 2. Create one EPUB per public story

Paste public HTTPS story URLs, one per line. With **Combine pasted webpages into ONE EPUB** unchecked, each story produces its own EPUB. The visible editor supports up to ten separate stories per batch. For a story overview, use **Analyze chapters before export** where available and review chapter titles, cover and synopsis before choosing selected chapters. The captures show the initial editor, not a completed chapter analysis.

Screenshot: `01-web-novel-editor.png`.

## 3. Combine public chapters into one book

Check **Combine pasted webpages into ONE EPUB**. Paste direct readable chapter/page URLs in the intended reading order, one per line. The editor shows selected chapter count and a 128 KiB URL-list budget; there is no fixed chapter count in this mode. Each URL becomes one chapter. Reorder the URL lines before exporting.

Screenshot: `02-combined-book.png`.

## 4. Book details and covers

Scroll down to set combined book title, author, language (for example en or ms), description and **Cover options · auto-detect source cover**. Choose a supported cover when needed. Public Wattpad metadata can populate the title, cover, author and synopsis; only readable public chapters are supported. Specify a new combined EPUB filename without .epub.

Screenshots: `03-combined-settings-1.png`, `03-combined-settings-2.png`.

## 5. Chapter titles and saved projects

Optionally enter chapter titles in the same line order as the URLs. Enter a project name and save a completed setup to reuse later. Use **Check new chapters** for a saved project as appropriate. Projects are on this phone, not cloud-synced. The screenshot shows the buttons disabled before a valid setup is supplied.

Screenshot: `03-combined-settings-3.png`, `03-combined-settings-4.png`.

## 6. EPUB compatibility and reading style

Leave **EPUB 2 compatibility** unchecked for EPUB3, or enable it for EPUB2. Choose whether to include inline illustrations, select Standard, Compact or Large EPUB text style, and use the optional safe custom EPUB text CSS only if needed. Scroll for illustration size and the save/send action. Choose a new output filename, inspect the resulting book, and use the [Wi-Fi transfer guide](WIFI_TRANSFER.md) if your reader supports it.

Screenshot: `03-combined-settings-5.png`.

## More Android walkthroughs

The existing [Getting started](GETTING_STARTED.md), [Nooir Tools](NOOIR_TOOLS_QUICK_START.md), [Manga conversion](CONVERSION.md), [PDF](PDF_TO_EPUB.md), [Web novels](WEB_NOVELS.md), [Wi-Fi transfer](WIFI_TRANSFER.md) and [FAQ](FAQ.md) guides remain available. Captures for onboarding, extension repository setup, manga export, actual XTC/XTCH viewer, PDF, transfers, backup/restore and other menus are still needed from the exact released Android version. No personal backups, manga library or credentials should appear in documentation. Android/Desktop backup migration is still in development; this guide does not promise it in v1.0.20.
