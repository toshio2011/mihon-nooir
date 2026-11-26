# Screenshot coverage and release gate

Use version-matched real screenshots, synthetic/public-domain books, clean profile and redacted reader IP addresses. Never include personal backups, login tokens, account names or browsing history. Refresh affected images whenever the UI changes.

Desktop: 19 genuine 100% scale Windows QA screenshots, candidate 7ff39e90bc5f2372ee1ca004e27c24bbb68e2ff7, CI run 45572700921, artifact 15003418407. Remaining: PDF/EPUB import, batch, queue, Wi-Fi transfer, installation and native backup/extension recovery after acceptance.

Android v1.0.20: the separate [Android visual guide PR #140](https://github.com/toshio2011/mihon-nooir/pull/140) has 18 genuine release-source emulator captures, including local inputs, actual XTCH viewing, initial PDF form and web editor. These are not final signed-APK captures. Full onboarding, extension installation, manga selection/settings, completed PDF export, transfer and backup still require additional captures.

| Guide | Capture |
|---|---|
| GETTING_STARTED | First launch, extension setup, first export |
| NOOIR_TOOLS_QUICK_START | More → Nooir Tools, Universal Book Converter |
| CONVERSION | Manga settings, export format, rendering, actual preview |
| CHAPTER_SELECTION | Select, reorder, confirm |
| DEVICE_PROFILES | Device profiles |
| ORIENTATION_OVERLAP | Auto orientation and overlap |
| PANEL_AUTO_FIT | Auto Fit before/after |
| EXPORT_FORMATS_EPUB2 | Format/version selector |
| XTC_METADATA | Metadata and actual preview |
| PDF_TO_EPUB | PDF input and modes, output |
| WEB_TO_READER | Analyze, preview, metadata and export |
| WEB_NOVELS | Chapter selection, combined covers, projects and updates |
| WIFI_TRANSFER | Connection, confirmation, result |
| DEVICE_FILES_AND_QUEUE | File browser, queue, conflicts |
| FAQ | Link to illustrated troubleshooting |
| UPDATES | Version/update flow |

Historical QA and handover files are evidence, not end-user guides. Keep screenshot references next to the relevant steps and verify relative links before release.

New GUI acceptance (pending CI) captures the actual backup action menu, read-only inspection and explicit reading-progress save confirmation/acknowledgement. Review and embed the four verified captures after CI; no placeholder screenshots.

Four additional reviewed real Windows captures from PR #145, run 45744401559, artifact 15083202444: backup-menu, backup-inspection, online-progress-confirm, online-progress-saved. The actual GUI tests passed at three scales. Remaining backup captures: native export/restore completion and actual extension recovery. Total desktop guide images: 23.
