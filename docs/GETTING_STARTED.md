# Getting started with MangaNooir

> **Release status (22 October 2026):** [MangaNooir v1.0.12](https://github.com/toshio2011/mihon-nooir/releases/tag/v1.0.12) is the **official public GitHub release**. The exact APKs from [successful CI run #39734517368](https://github.com/toshio2011/mihon-nooir/actions/runs/39734517368) were published without rebuilding. Real Android/XTEink device validation remains incomplete; these are not persistent-key-signed builds.

[← Homepage](../README.md) · [All guides](README.md) · [Nooir Tools quick start](NOOIR_TOOLS_QUICK_START.md) · [Wi-Fi guide](WIFI_TRANSFER.md)

MangaNooir is an **experimental Android app based on the full Mihon reader**. Browse, organize and read manga as usual; MangaNooir adds ways to prepare and send books to e-ink readers.

## Step 1 — Choose the correct APK

| Build | Features you can expect | Status |
|---|---|---|
| **Official v1.0.12 release** | Full Mihon overlay, Nooir Tools, EPUB 2/3, XTC/XTCH, multiprovider profiles, latest cover refresh and conversion workflows | [**Download official APKs**](https://github.com/toshio2011/mihon-nooir/releases/tag/v1.0.12); physical checks pending |
| **Older v1.0.11 prerelease** (historical) | Adds **Nooir Tools** hub: phone files, queue, PDF, web links, multiple links into one EPUB, public web novels, advanced manga settings and device browser | [**Download prerelease APKs**](https://github.com/toshio2011/mihon-nooir/releases/tag/v1.0.11); broader reader validation pending |

**v1.0.12 is officially published**, but real-device validation and stable signing remain pending. CI success does not prove every website, PDF or e-reader behaves correctly.

### Install the official v1.0.12 APK

1. Open the [v1.0.12 GitHub release](https://github.com/toshio2011/mihon-nooir/releases/tag/v1.0.12) and expand **Assets**.
2. Download [`app-arm64-v8a-foss.apk`](https://github.com/toshio2011/mihon-nooir/releases/download/v1.0.12/app-arm64-v8a-foss.apk) for most phones or `app-universal-foss.apk`.
3. Install the APK directly from Android's file manager. **You do not need to unzip a GitHub Actions artifact.**
4. Let Android grant temporary **Install unknown apps** permission to your chosen file manager when prompted.
5. Launch **MangaNooir**. It's separate from the original Mihon package and intended to coexist with it.

> [!WARNING]
> Before changing older **MangaNooir** test APKs, **back up your MangaNooir data**. CI-generated APKs may have different signing keys and fail to update one another. Don't uninstall ordinary Mihon just to try this app. No manga sources or source extensions are bundled.

[Build/update details](UPDATES.md)

## Step 2 — Locate the features

In v1.0.12 open **More → Nooir Tools**:

| Tool | What it does |
|---|---|
| **Activity Queue** | Running, waiting, finished and failed conversion/transfer jobs |
| **Send & Convert files** | Select local files, optionally convert supported manga, save/send |
| **Web link → EPUB** | One public article **or multiple public pages into one EPUB** |
| **Web novels · Batch EPUB** | Separate stories, discover **All chapters**, or combine selected chapter URLs |
| **PDF → EPUB · Text + illustrations** | Selectable native PDF text mixed with preserved images/scans |
| **Browse XTEink files** | Read-only folder browser and link to Folio Nooir's full file manager |

**Where is manga export?** The **Export** icon is on the **manga details screen**, not under Nooir Tools. In the older **v1.0.1 APK**, the Nooir Tools hub **won't be there**. This is the fastest way to tell the builds apart.

[Open the full illustrated-by-steps Nooir Tools guide](NOOIR_TOOLS_QUICK_START.md)

## Step 3 — Export your first manga

1. Open a manga from your Library or a working installed source extension.
2. To select specific chapters, **long-press** the first chapter and **tap** the others. Check the highlighted selection count.
3. Press the **Export/Save** action in the manga toolbar.
4. Choose the **X4/X3** device preset and **XTC / XTCH / EPUB / CBZ** (or Auto).
5. For the simplest test, choose **Save file** first and check the exported book.

**Normal Export:** Only *already downloaded* selected chapters are exported. If no chapters are selected, Normal Export exports **all downloaded chapters**.

**Quick Export (experimental):** Select one or more chapters first, then enable **Quick Export · fetch missing pages**. It temporarily retrieves missing page images through the installed online source; it does not add those images to your permanent Mihon downloads.

See [chapter selection examples](CHAPTER_SELECTION.md), [formats](CONVERSION.md) and [landscape/overlap settings](ORIENTATION_OVERLAP.md).

## Step 4 — Connect your XTEink over Wi-Fi

1. On Folio Nooir open **Home → File Transfer → Join Network** (home Wi-Fi) or **Create Hotspot**.
2. Connect your phone to the same reachable network. With the hotspot, Android may say **no internet**; stay connected for local transfer.
3. Open the reader's on-screen HTTP URL in your phone browser. You should see its file manager.
4. In the MangaNooir transfer dialog type **only the reader's IP/hostname** (e.g. `192.168.1.102`, *not* `http://192.168.1.102/`) and an existing folder such as `/Books` or `/`.
5. Start **Send to XTEink**; wait for **reader acknowledgement** after the upload reaches 100%.

The XTEink local server does **not** have authentication. Use trusted networks, turn off File Transfer when done, and don't start repeated uploads while one may still be processing.

[Complete Wi-Fi walkthrough](WIFI_TRANSFER.md)

## Step 5 — Try books from phone, web or PDF

**From phone:** **More → Nooir Tools → Send & Convert files**, select up to 25 local documents per batch. Choose an original transfer or convert supported CBZ/images.

**One webpage:** **More → Nooir Tools → Web link → EPUB**, paste one public HTTPS article and save it as reflowable EPUB.

**Several webpages in one book:** On the **Web link → EPUB** screen tap **Multiple web links → one EPUB**, paste direct public article/chapter URLs **one per line in reading order**, then name and export your combined EPUB.

**Full public web novel:** **Web novels · Batch EPUB**, paste a public story URL; use **Download all chapters** if the source exposes its chapter list in public HTML. Up to 10 *separate stories* per batch. JavaScript-only chapter lists, inaccessible chapters and paywalls may not work.

**Mixed PDF:** **PDF → EPUB · Text + illustrations**; choose reflowable text + illustrations or original page images. PDFs without actual text stay image-based; no OCR or AI conversion is included.

[Web article guide](WEB_TO_READER.md) · [Web novels and multi-link guide](WEB_NOVELS.md) · [PDF converter guide](PDF_TO_EPUB.md)

## Step 6 — Read progress and handle errors

The v1.0.12 release uses a **shared sequential WorkManager queue** for manga, file, web and PDF conversion/sending. Open **Nooir Tools → Activity Queue**; choose **History** to see finished or failed tasks. An ordinary failed book should not block the next queued job.

**Destination filename already exists?** Enter a different name before starting; the preview checks names before expensive processing and again at upload. It should not silently overwrite a book.

**XTC/XTCH cover missing on the reader shelf?** First inspect page 1 of the actual book; then on Folio Nooir use **Retrieve All Book Details** if that first page contains the cover but its shelf thumbnail hasn't been generated.

**Cancel:** Activity Queue offers a confirmed **Cancel entire Nooir queue**. It stops waiting jobs as well; cancelling individual WorkManager chain dependencies can cascade, so the current preview doesn't offer that action.

[Reader files, Activity Queue and cover troubleshooting](DEVICE_FILES_AND_QUEUE.md) · [FAQ](FAQ.md)

## Still experimental

Please verify the output on your own phone and XTEink. Automated CI does not test your installed source extensions, protected/dynamic websites, scan-heavy PDFs, reader SD-card reliability or cover cache behavior. If something goes wrong, collect your **build SHA, input/source, file type, phone version, XTEink firmware and exact error** without sharing private credentials or copyrighted book pages.

[Merged v1.0.12 PR #22](https://github.com/toshio2011/mihon-nooir/pull/22) · [Roadmap](https://github.com/toshio2011/mihon-nooir/issues/8)
