# Nooir Tools — quick start (experimental v1.0.11)

[← MangaNooir home](../README.md) · [All guides](README.md) · [Getting started](GETTING_STARTED.md)

> [!IMPORTANT]
> These steps describe the **v1.0.11 experimental Android preview**, not the **v1.0.1 code on `main`**. The [v1.0.11 APK build](https://github.com/toshio2011/mihon-nooir/actions/runs/39363272479) passed automated checks, **not physical phone/XTEink testing**. There is no stable MangaNooir release yet.

**Already using normal Mihon?** MangaNooir is a separate test app. Your existing Mihon reader, library, extensions, downloads, backup and tracking behavior are meant to remain available. No manga sources or books are bundled.

## Where are the new tools?

Open **MangaNooir → More → Nooir Tools**. Inside the hub:

| Choose | Use it for |
|---|---|
| **Activity Queue · All current jobs** | Watch running, waiting, completed or failed conversions/transfers |
| **Send & Convert files** | Send files already on your phone; convert local CBZ/images when supported |
| **Web link → EPUB** | Convert a public webpage **or multiple webpage links into one EPUB** |
| **Web novels · Batch EPUB** | One EPUB per public novel; discover chapters or combine selected chapter URLs |
| **PDF → EPUB · Text + illustrations** | Keep text selectable and images in the same EPUB where feasible |
| **Browse XTEink files** | Browse reader folders without editing; open its full file manager in your browser |

**Manga chapter export is not inside this hub:** open a manga details page, select chapters, and use its existing Export button.

## 1. Manga → XTC / XTCH / EPUB / CBZ

1. Open a manga's details page. **Long-press** the first wanted chapter, then **tap** other chapters. Review the highlighted chapters.
2. Tap **Export** in the manga toolbar. Choose your X3/X4 preset and output format.
3. Turn on **Quick Export** only when you want selected online chapters that are not already downloaded in Mihon. Normal Export uses already-downloaded chapters.
4. If needed, expand **More conversion settings** for portrait/landscape, overlapping cuts, reading direction, spread handling, trimming, contrast and dithering.
5. Choose **Save file** or **Send to XTEink**. For device sends, set an output filename; the preview checks whether that name already exists **before converting**, then again before uploading.

No selection + Normal Export means **all downloaded chapters** of that manga. Quick Export requires **at least one selected chapter**. Original Mihon downloads are not replaced.

**Missing XTC/XTCH cover?** The experimental exporter requires a decodable manga cover at the beginning of the book; if the source cover is unavailable it should show an error rather than quietly claim success. Check whether the cover is present in Mihon, then retry to a **new filename**. If page 1 has the cover but the reader **shelf** doesn't, use **Retrieve All Book Details** on Folio Nooir to create missing thumbnails.

[Chapter selection](CHAPTER_SELECTION.md) · [Conversion formats](CONVERSION.md) · [Orientation/overlap](ORIENTATION_OVERLAP.md) · [Cover and metadata](XTC_METADATA.md)

## 2. Local files → phone or XTEink

Open **More → Nooir Tools → Send & Convert files**, select documents with Android's picker, and choose whether to send originals unchanged or convert supported image/CBZ manga. You can select up to **25 files per batch**. Edit each output name before processing if it clashes with a file already on the reader or destination folder.

A PDF, EPUB or other file sent **unchanged** is not automatically converted or guaranteed readable by every firmware. Use the dedicated PDF converter for mixed-content PDF → EPUB.

## 3. One web link → EPUB

Open **More → Nooir Tools → Web link → EPUB**. Paste one **public HTTPS webpage** containing the readable article itself, pick an output name, then **Save article EPUB to phone** or **Convert article & send to XTEink**.

Text and supported inline artwork become a reflowable EPUB where extraction succeeds. A site's story overview, login-only view, script-only page, or paywalled chapter may not contain readable text in its public HTML.

## 4. Multiple web links → **one** EPUB

From the **same Web link → EPUB screen**, tap **Multiple web links → one EPUB**. You can also open **Web novels · Batch EPUB** and check **Combine pasted webpages into ONE EPUB**.

1. Paste public webpage or direct chapter **URLs one per line**. Line order = chapter order in the final EPUB.
2. Optionally enter the combined **book title**, **author**, **output filename** and **chapter titles** (chapter titles can be one per line).
3. Choose **Save combined EPUB to phone** or **Combine & send one EPUB to XTEink**.
4. The result is **one reflowable EPUB** with a navigable chapter list. If any requested page fails, that book stops instead of quietly dropping a chapter.

There's no fixed pasted-chapter count for this mode; URL-list storage is bounded to a private **128 KiB manifest**. For a public novel with its own discoverable chapter index, **Web novels → Download all chapters** is usually easier than pasting every chapter URL.

**Web novels · Batch EPUB** also offers a separate mode: paste up to **10 story links**, each generating its **own EPUB**. The **All chapters** setting uses every chapter discoverable in the public HTML; you can instead enter First/Last chapter. “All” cannot reveal JavaScript-only, locked or undiscoverable chapters.

[Web link guide](WEB_TO_READER.md) · [Web novel and multi-link guide](WEB_NOVELS.md)

## 5. PDF → EPUB with text and illustrations

Open **More → Nooir Tools → PDF → EPUB · Text + illustrations**, select a PDF, and choose a conversion mode:

- **Reflowable text + illustrations:** extract real selectable PDF text, position accessible pictures near the relevant text when feasible, and keep scanned/complex pages as images in the **same EPUB**.
- **Keep original pages as images:** preserve visual pages when text reflow is not suitable; the text won't be selectable as native EPUB text.

You can edit the resulting **book title, author and EPUB filename**. Save locally or send to XTEink. No AI or OCR is included: an image-only scan remains an image. Review tables, fancy typography, floor plans and unusual layouts after converting.

[Full PDF guide](PDF_TO_EPUB.md)

## 6. Set up Wi-Fi transfer

1. On Folio Nooir, enter **Home → File Transfer → Join Network** or **Create Hotspot**.
2. Connect the phone so it can reach the reader. Open the **reader's displayed address** in your phone browser first and verify the file manager responds.
3. In MangaNooir, type **only the IP/hostname** (no `http://`) and an **existing** folder such as `/Books` or `/`.
4. Wait for **XTEink confirmation**, not merely 100% of bytes sent. The experimental upload guard is **180 MiB per file**.

Folio Nooir's local HTTP transfer server is not password protected. Use only a trusted LAN or short-lived device hotspot and close File Transfer when finished.

[Full Wi-Fi guide](WIFI_TRANSFER.md) · [Reader files and queue](DEVICE_FILES_AND_QUEUE.md)

## 7. What if one job fails or a name already exists?

Open **More → Nooir Tools → Activity Queue → History** to read per-job results. In the new preview, a failed book **does not automatically fail the later queued books**, and a local multi-file batch continues with the next file. An existing destination filename is rejected instead of intentionally overwritten. **Rename it before requeuing** or use another destination folder.

The confirmed **Cancel entire Nooir queue** action stops queued jobs too; cancelling one WorkManager dependency may cascade, so individual job cancellation is not offered. Android system limits, lost Wi-Fi or low SD-card space can still interrupt a transfer. **Mihon's normal chapter Download Queue remains separate.**

**Reader file browser:** **Browse XTEink files** gives a lightweight read-only view. To rename, move, upload, download or delete reader files, use the existing Folio Nooir web file manager at `http://READER-IP/files`.

[Queue, duplicate files, cover troubleshooting](DEVICE_FILES_AND_QUEUE.md) · [FAQ](FAQ.md)

## Still being verified

A successful [Android CI build](https://github.com/toshio2011/mihon-nooir/actions/runs/39363272479) confirms the preview compiled and its automated format/overlay tests passed. It **does not prove** real Wattpad/Rengkang extraction, accurate output from every PDF, correct e-ink thumbnails, or transfer safety on a physical X3/X4. Please report your build, input format/source, device firmware, destination and exact error message (without sharing copyrighted pages or credentials).

[Draft PR #18 / development status](https://github.com/toshio2011/mihon-nooir/pull/18) · [Roadmap](https://github.com/toshio2011/mihon-nooir/issues/8)
