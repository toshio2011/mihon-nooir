# Getting started with MihonNooir

> **Release status (16 November 2026):** [MihonNooir v1.0.18](https://github.com/toshio2011/mihon-nooir/releases/tag/v1.0.18) is the latest official Android release. It uses the same persistent signing certificate as official v1.0.17. [Release notes and verification](releases/v1.0.18.md). Windows remains in development.

[← Homepage](../README.md) · [All guides](README.md) · [Nooir Tools quick start](NOOIR_TOOLS_QUICK_START.md) · [Wi-Fi guide](WIFI_TRANSFER.md)

MihonNooir is an **experimental Android app based on the full Mihon reader**. Browse, organize and read manga as usual; MihonNooir adds ways to prepare and send books to e-ink readers.

## Step 1 — Choose the correct APK

Download from the [official v1.0.18 release](https://github.com/toshio2011/mihon-nooir/releases/tag/v1.0.18), not an Actions/debug artifact or the separate QA app.

| Android device | APK |
|---|---|
| Most modern phones (ARM64) | [app-arm64-v8a-foss.apk](https://github.com/toshio2011/mihon-nooir/releases/download/v1.0.18/app-arm64-v8a-foss.apk) |
| Architecture uncertain | [app-universal-foss.apk](https://github.com/toshio2011/mihon-nooir/releases/download/v1.0.18/app-universal-foss.apk) |
| Other supported devices | [All APKs and checksums](https://github.com/toshio2011/mihon-nooir/releases/tag/v1.0.18) |

1. Download the APK and optionally verify its SHA-256 against [SHA256SUMS.txt](https://github.com/toshio2011/mihon-nooir/releases/download/v1.0.18/SHA256SUMS.txt).
2. Back up your existing MihonNooir library and important downloads before updating. From official v1.0.17, use the manual download above because the old updater may select an incompatible APK.
3. Open the APK from Android's file manager; no ZIP extraction is required. Allow installation from the chosen file manager if prompted.
4. If updating the official app, install **over** the existing installation. Do not uninstall or clear storage.
5. Open MihonNooir and confirm About shows **FOSS v1.0.18**; verify your library and reading progress. It can coexist with ordinary Mihon.

> [!WARNING]
> Older temporary-key builds, such as v1.0.12, may not update in place. If Android reports a signature or package conflict, stop and report it rather than deleting app data. No manga or source extensions are bundled.

[Current release notes](releases/v1.0.18.md) · [Build/update details](UPDATES.md)

## Step 2 — Locate the features

In v1.0.18 open **More → Nooir Tools**:

| Tool | What it does |
|---|---|
| **Activity Queue** | Running, waiting, finished and failed conversion/transfer jobs |
| **Send & Convert files** | Select local files, optionally convert supported manga, save/send |
| **Web link → EPUB** | One public article **or multiple public pages into one EPUB** |
| **Web novels · Batch EPUB** | Separate stories, discover **All chapters**, or combine selected chapter URLs |
| **PDF → EPUB · Text + illustrations** | One or more PDFs into EPUB 2/3 or XTC/XTCH; selectable text in EPUB where supported |
| **Browse XTEink files** | Read-only folder browser and link to Folio Nooir's full file manager |

**Where is manga export?** The **Export** icon is on the **manga details screen**, not under Nooir Tools. In the older **v1.0.1 APK**, the Nooir Tools hub **won't be there**. This is the fastest way to tell the builds apart.

[Open the full illustrated-by-steps Nooir Tools guide](NOOIR_TOOLS_QUICK_START.md)

## Step 3 — Export your first manga

1. Open a manga from your Library or a working installed source extension.
2. To select specific chapters, **long-press** the first chapter and **tap** the others. Check the highlighted selection count.
3. Press the **Export/Save** action in the manga toolbar.
4. Choose the **X4/X3** device preset and **XTC / XTCH / EPUB / CBZ** (or Auto).
5. In **More conversion settings**, choose Page, Row by Row or Panel by Panel; try Auto Fit, Full Page Before Splits or the four rendering styles. Use **Preview first page** after choosing a manga image or CBZ, then **Save file**. For XTC/XTCH use **Inspect an exported XTC / XTCH file** to verify the actual saved book.

**Normal Export:** Only *already downloaded* selected chapters are exported. If no chapters are selected, Normal Export exports **all downloaded chapters**.

**Quick Export (experimental):** Select one or more chapters first, then enable **Quick Export · fetch missing pages**. It temporarily retrieves missing page images through the installed online source; it does not add those images to your permanent Mihon downloads.

See [chapter selection examples](CHAPTER_SELECTION.md), [formats](CONVERSION.md) and [landscape/overlap settings](ORIENTATION_OVERLAP.md).

## Step 4 — Connect your XTEink over Wi-Fi

1. On Folio Nooir open **Home → File Transfer → Join Network** (home Wi-Fi) or **Create Hotspot**.
2. Connect your phone to the same reachable network. With the hotspot, Android may say **no internet**; stay connected for local transfer.
3. Open the reader's on-screen HTTP URL in your phone browser. You should see its file manager.
4. In the MihonNooir transfer dialog type **only the reader's IP/hostname** (e.g. `192.168.1.102`, *not* `http://192.168.1.102/`) and an existing folder such as `/Books` or `/`.
5. Start **Send to XTEink**; wait for **reader acknowledgement** after the upload reaches 100%.

The XTEink local server does **not** have authentication. Use trusted networks, turn off File Transfer when done, and don't start repeated uploads while one may still be processing.

[Complete Wi-Fi walkthrough](WIFI_TRANSFER.md)

## Step 5 — Try books from phone, web or PDF

**From phone:** **More → Nooir Tools → Send & Convert files**, select up to 25 local documents per batch. Choose an original transfer or convert supported CBZ/images.

**One webpage:** **More → Nooir Tools → Web link → EPUB**, paste one public HTTPS article and save it as reflowable EPUB.

**Several webpages in one book:** On the **Web link → EPUB** screen tap **Multiple web links → one EPUB**, paste direct public article/chapter URLs **one per line in reading order**, then name and export your combined EPUB.

**Full public web novel:** **Web novels · Batch EPUB**, paste a public story URL; use **Download all chapters** if the source exposes its chapter list in public HTML. Up to 10 *separate stories* per batch. JavaScript-only chapter lists, inaccessible chapters and paywalls may not work.

**PDF:** **PDF → EPUB · Text + illustrations**; choose one or more source PDFs. EPUB supports reflowable text + illustrations or original page images; XTC/XTCH render pages to the selected device dimensions. Save separate books to a folder for batch conversion. PDFs without actual text stay image-based; no OCR or AI. Large local PDFs have no arbitrary source-page cutoff, but storage, processing and actual file-format limits apply. For large manga or PDFs, save to phone and copy to SD card; Wi-Fi has a separate 180 MiB per-file limit.

[Web article guide](WEB_TO_READER.md) · [Web novels and multi-link guide](WEB_NOVELS.md) · [PDF converter guide](PDF_TO_EPUB.md)

## Step 6 — Read progress and handle errors

The current release uses a **shared sequential WorkManager queue** for manga, file, web and PDF conversion/sending. Open **Nooir Tools → Activity Queue**; choose **History** to see finished or failed tasks. An ordinary failed book should not block the next queued job.

**Destination filename already exists?** Enter a different name before starting; the preview checks names before expensive processing and again at upload. It should not silently overwrite a book.

**XTC/XTCH cover missing on the reader shelf?** First inspect page 1 of the actual book; then on Folio Nooir use **Retrieve All Book Details** if that first page contains the cover but its shelf thumbnail hasn't been generated.

**Cancel:** Activity Queue offers a confirmed **Cancel entire Nooir queue**. It stops waiting jobs as well; cancelling individual WorkManager chain dependencies can cascade, so the current preview doesn't offer that action.

[Reader files, Activity Queue and cover troubleshooting](DEVICE_FILES_AND_QUEUE.md) · [FAQ](FAQ.md)

## Still experimental

Please verify the output on your own phone and XTEink. Automated CI does not test your installed source extensions, protected/dynamic websites, scan-heavy PDFs, reader SD-card reliability or cover cache behavior. If something goes wrong, collect your **build SHA, input/source, file type, phone version, XTEink firmware and exact error** without sharing private credentials or copyrighted book pages.

[v1.0.18 release notes](releases/v1.0.18.md) · [Roadmap](https://github.com/toshio2011/mihon-nooir/issues/8)
