# Getting started with MangaNooir

[← Back to README](../README.md) · [Wi-Fi transfer](WIFI_TRANSFER.md) · [Formats](CONVERSION.md)

MangaNooir is an **experimental** Android fork built on Mihon. Your everyday reading experience comes from Mihon; MangaNooir adds e-reader-oriented export tools.

## 1. Pick the right version

| Version | Where to get it | Intended use |
|---|---|---|
| **Main (1.0.1)** | [Main-branch Actions builds](https://github.com/toshio2011/mihon-nooir/actions/workflows/build-apk.yml) | Downloaded manga export, save file, XTEink Wi-Fi send |
| **Experimental (1.0.4)** | [Verified PR #10 APK build](https://github.com/toshio2011/mihon-nooir/actions/runs/39220489438) | Test Quick Export, file picker, batch queue, conversion presets, background jobs |

**Neither is a stable release.** GitHub Releases currently contains no published APKs. CI compiles and tests the integration, but actual on-phone and on-reader functionality still needs validation.

### Install a CI artifact

1. Open the successful GitHub Actions run in a browser while signed into GitHub.
2. Scroll to **Artifacts** and select `mihon-nooir-foss-apk`; download the ZIP.
3. Extract the ZIP in your Android file manager.
4. Install the APK for your CPU (`arm64-v8a` on ARM64 phones, or a supported universal variant if included). Do not choose an unsigned build.
5. When Android asks, temporarily allow your file manager to install apps. Open **MangaNooir**.

**Important:** the fork has its own package identity, so it can sit beside regular Mihon. If Android refuses an update over another MangaNooir build, the APKs may use different signing keys. **Back up your MangaNooir data before uninstalling**, and never uninstall your original Mihon just to try this fork.

## 2. Keep your Mihon setup

You can browse your existing library and use the familiar Mihon screens. Source extensions are **not bundled**. Configure your extensions in the same way you would in Mihon, and use only sources and content you are authorized to access.

MangaNooir doesn't need a separate manga server or Suwayomi account.

## 3. Export a chapter you already downloaded

1. Pick a manga and download a chapter using Mihon's normal Download button.
2. Open the manga details screen.
3. Tap the **Export** action beside Download. You may select chapters first; otherwise the downloaded-chapter selection follows the app's current export defaults.
4. Choose **X4** or **X3**, then choose a format: **Auto**, **XTC**, **XTCH**, **EPUB** or **CBZ**.
5. Tap **Save file**, choose an Android destination, and wait for completion.

The original Mihon chapter files are **not deleted**. A converter output is a separate file.

### Experimental Quick Export (1.0.4 only)

Select one or more chapters in the manga details screen, including chapters not permanently downloaded. In Export, enable **Quick Export**. Missing pages are retrieved through that installed source extension, processed chapter by chapter, and cleaned from the temporary cache afterward.

There is **no fixed five-chapter limit** in this preview, but long series still need enough temporary/final output storage. For direct XTEink HTTP sending, **180 MiB per file** is the current experimental guard. Source errors, authentication, network failures and format compatibility still need testing. Use a small chapter first.

### Experimental conversion settings (1.0.4 only)

Open **More conversion settings** in Export to choose Manga / Manhwa / Manhua presets, automatic/paged/webtoon layout, conservative white-margin trimming, split spreads, reading direction, contrast and dithering. These adjust image rendering; they do not promise every MangaPoint setting is available yet. See [formats and conversion](CONVERSION.md).

## 4. Send to Folio Nooir

The XTEink reader needs to be in **File Transfer** mode and reachable over the same local network or its own reader-created hotspot. Test the reader's IP in Chrome before trying MangaNooir.

[Follow the complete Wi-Fi guide →](WIFI_TRANSFER.md)

## 5. Send other files (experimental 1.0.4)

Choose **More → Send & Convert files**:

- **Choose multiple files** from your phone's Android file picker (up to 25 per batch in the preview).
- **Send originals** without altering them.
- **Convert manga & send** for recognized CBZ / manga images; non-manga formats remain unchanged.
- **Convert & save** can save multiple converted outputs to a selected folder.

The conversion/transfer batch uses Android WorkManager, so pressing Back or switching apps should not depend on the dialog remaining open. Reopen **More → Send & Convert** to view the last job, stage, transfer bytes or cancel. Android power restrictions, losing Wi-Fi or force-stopping the app can still interrupt it.

## 6. If something goes wrong

- **No Export action?** Check that you're using MangaNooir, not regular Mihon, and that the installed build matches the feature you want.
- **No More → Send & Convert?** That's only in the experimental 1.0.4 preview, not main 1.0.1.
- **APK won't update?** Back up and check signing certificates; do not wipe data without a backup.
- **Transfer never starts?** Use [the Wi-Fi troubleshooting steps](WIFI_TRANSFER.md).
- **100% sent but not finished?** Wait for the XTEink to acknowledge the file; a sent-byte count is not final confirmation.
- **Converted file won't open?** Check reader firmware support and [the format guide](CONVERSION.md). An upload does not guarantee the file can be opened.

For project progress and planned features, see [roadmap #8](https://github.com/toshio2011/mihon-nooir/issues/8).
