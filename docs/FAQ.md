# MihonNooir FAQ

> **Screenshot guide:** For real Android screen examples, see the [illustrated Android guide](ANDROID_VISUAL_GUIDE.md), including actual finished XTCH pages.

> **Current Android release:** [MihonNooir v1.0.20](https://github.com/toshio2011/mihon-nooir/releases/tag/v1.0.20) (24 November 2026). This guide describes shipped features; see [release notes](releases/v1.0.20.md). Individual inputs, sources and reader firmware may behave differently. Windows is not publicly released.

[← All guides](README.md) · [Nooir Tools quick start](NOOIR_TOOLS_QUICK_START.md) · [Getting started](GETTING_STARTED.md)

> **Version:** Use the official v1.0.20 APK; [installation and update instructions](GETTING_STARTED.md). Draft Android and Windows builds may differ.

### Where is the chapter picker?

In the **manga details chapter list**. Long-press a chapter, tap any others, and press the toolbar **Export** icon. The Export dialog is for format/device/destination, not for selecting chapters. [Guide](CHAPTER_SELECTION.md)

### Can I export online chapters without downloading them permanently?

Yes: select chapters first and turn on **Quick Export · fetch missing pages** in manga Export. Normal Export only takes already-downloaded selected chapters; Normal Export without any selection takes **every downloaded chapter**. Quick Export needs a working online source extension. [Guide](CHAPTER_SELECTION.md)

### I can't find “Nooir Tools” under More.

Check that About shows MihonNooir FOSS v1.0.20, not ordinary Mihon or a very old build. Open **More → Nooir Tools**. [Installation](GETTING_STARTED.md).

### Can I send CBZ, PDF, EPUB, FB2 or other files from my phone?

**More → Nooir Tools → Universal Book Converter → Local files** uses Android's picker. You can send files **unchanged**; supported CBZ/image manga and EPUB → XTC/XTCH inputs may also be converted. A file being transferable doesn't guarantee the reader can open it. Up to **25 local files** per batch. [Quick start](NOOIR_TOOLS_QUICK_START.md)

### Can I convert multiple web links into **one EPUB**?

**Yes.** Open **More → Nooir Tools → Universal Book Converter → Web link → Multiple web links → one EPUB**. Paste public readable article/chapter URLs **one per line**. The lines become EPUB chapters in that order; optionally enter one book title, author, filename and chapter titles. You can reach the same combined editor from **Web novels · Batch EPUB**. [Full multi-link guide](WEB_NOVELS.md)

### What about one EPUB **per novel** and “Download all chapters”?

In **Web novels · Batch EPUB**, leave combined mode off, paste up to **10 public story URLs**, and select **All chapters** (default) or a manual First/Last range. One story produces one EPUB. There is no fixed 80-chapter maximum: “All” means **every chapter actually discoverable in public HTML**, not hidden pages. [Web novels guide](WEB_NOVELS.md)

### Why do a Wattpad story URL or a web-novel page fail?

Some links open a **story overview** or use JavaScript/authenticated chapter lists that the HTML extractor cannot read. A genuine direct public chapter URL may work; locked chapters, paywalls and scripts are not bypassed. A page containing just title/synopsis is not a complete EPUB chapter. The queue should report which story/page failed. [Web guide](WEB_NOVELS.md)

### Can I turn PDF novels into editable text EPUBs?

The **PDF → EPUB · Text + illustrations** tool extracts **real selectable text** when a PDF has a text layer, retaining accessible pictures and preserving difficult/scanned pages as images in the **same EPUB**. It doesn't perform OCR on image-only pages. Check decorative typography, tables and diagrams after converting. [PDF guide](PDF_TO_EPUB.md)

### What if a PDF has both text and illustrations?

That's the main target. The converter attempts to interleave text and figures at similar vertical positions; complex pages may need whole-page image fallback. EPUB output may need visual corrections, and X4 testing is pending. [PDF guide](PDF_TO_EPUB.md)

### Does one failed conversion stop all queued books?

The WorkManager queue records per-book failure and allows later queued books to continue; a bad file inside a local batch also shouldn't block subsequent files. Open **More → Nooir Tools → Activity Queue → History** to inspect failures. **Cancel entire queue** is intentionally different; it stops waiting jobs too. [Queue guide](DEVICE_FILES_AND_QUEUE.md)

### Does MihonNooir overwrite a book with the same name?

The current app checks output filenames **before converting** and again before upload, and rejects existing destination names instead of intentionally overwriting them. Choose another name or folder. The preflight check alone isn't an atomic guarantee against simultaneous uploads; the reader must also protect the file. [Queue / names guide](DEVICE_FILES_AND_QUEUE.md)

### Why does XTC/XTCH show the wrong cover or no cover?

Compatible firmware builds a shelf thumbnail from the **first page of an XTC/XTCH book**. MihonNooir can insert the series cover as the first page when requested, and its XTCH cover has been reported working on Folio Nooir. If a shelf thumbnail is missing, check the actual first page and refresh the reader's cache (Folio Nooir: **Retrieve All Book Details**). Reader synopsis support varies by firmware. [Covers and metadata](XTC_METADATA.md)

### What output format should I choose?

**XTC** for high-contrast monochrome pages, **XTCH** for four-level grayscale, **EPUB** for a comic/image ebook or text-based exports where applicable, and **CBZ** for an ordered comic archive. **Auto** selects a device-oriented format (X4 typically XTCH), not a guarantee that every comic looks best. [Format guide](CONVERSION.md)

### Do I need Bluetooth, USB or an online server for e-reader transfers?

No. Use Folio Nooir **File Transfer** on the same reachable local Wi-Fi, or its temporary hotspot. The phone can test the on-screen reader IP in Chrome first. Type just the **IP/hostname**, not `http://`, into MihonNooir. [Wi-Fi guide](WIFI_TRANSFER.md)

### Can I leave MihonNooir during conversion?

The tools use background WorkManager jobs, and you can navigate away. Android battery rules, forced stop, Wi-Fi loss and storage failures can interrupt them. Wait for **XTEink receiver confirmation** rather than assuming 100% upload means success. [Queue](DEVICE_FILES_AND_QUEUE.md)

### Where can I get the app? Can it replace regular Mihon?

Download the [official Android v1.0.20 APKs](https://github.com/toshio2011/mihon-nooir/releases/tag/v1.0.20) under Assets. MihonNooir has its own package and can coexist with Mihon. Back up before updating; from v1.0.17 manually download the correct APK and install over the existing app. Do not uninstall or clear data if a signature conflict occurs. [Install guide](GETTING_STARTED.md).

### Can I convert more than one PDF or a PDF with thousands of pages?

Yes. v1.0.19 accepts multiple PDFs, saves separate books sequentially and no longer enforces the old fixed source-page cutoff. Real storage, memory/processing and output-format constraints still apply. For large books use local saving and copy to SD card; Wi-Fi is limited to 180 MiB per file. [PDF guide](PDF_TO_EPUB.md).

### Which reading segmentation should I use?

**Page** for the normal page workflow; **Row by Row** to read horizontal tiers without separating columns; **Panel by Panel** to attempt individual panel detection. **Auto Fit** chooses portrait or landscape to maximize reading size, without stretching the artwork. Protected and uncertain pages can stay whole. For Japanese manga, choose **Apply recommended manga panel settings**. v1.0.19 includes the configurable oversized-split enhancement in both row and panel modes. [Recommended settings](PANEL_AUTO_FIT.md).

### Why did a panel stay whole, split into a thin strip, or combine with another panel?

Panel detection is heuristic. v1.0.19 examines suspicious fragments and strongly framed internal gutters, but unusual artwork can still defeat it. A real narrow panel is not necessarily a bug. Try Row by Row or Page for that layout, or use Full Page Before Splits to retain the original context. Changing normal overlap alone does not repair detection. Use the optional private panel diagnostics ZIP for troubleshooting.

### What's the difference between ordinary overlap and oversized enlargement?

Ordinary overlapping page cuts control repeated areas in ordinary slices. **Enlarge oversized splits** (default on) checks eligible poorly fitting views using the separate **Minimum screen fill** setting (25–80%, default 50%). It tries verified natural boundaries first and can use a separate 12% protective overlap if both views become more readable. The recommended manga shortcut sets ordinary overlap to 0%; it does not reset enlargement, threshold or Context Peek. To avoid optional zoom splits, turn enlargement off; existing detection and normal slicing may still produce multiple frames. [Orientation guide](ORIENTATION_OVERLAP.md).

### What's the difference between the previews?

**Preview first page** shows the intended conversion before saving. **Inspect an exported XTC / XTCH file** decodes the actual book you selected, after export. You can compare the four rendering styles using the live preview. [Conversion guide](CONVERSION.md).

### Is Windows available?

Not as an official public Windows release. Windows preview development remains separate from Android v1.0.20.

### Is MihonNooir official, and does it include free manga?

No. This is an independent project based on Mihon, inspired by MangaPoint and built alongside Folio Nooir. It **does not bundle manga, content-source extensions or a catalog**. Use sources and documents you are entitled to access.

### My problem isn't listed. What should I report?

Share your **build/commit, Android version, input format/source, XTEink model and installed firmware, destination folder and exact error message**, without posting private documents, credentials or copyrighted manga pages. [Roadmap and issues](https://github.com/toshio2011/mihon-nooir/issues/8)

### How do I convert an EPUB or get the cover and synopsis for a web novel?

Open **More → Nooir Tools → Universal Book Converter → Local files**. Choose the input type before browsing, then select compatible output and settings. EPUB → XTC/XTCH supports font family, font size, margin, line height and reading direction. CBZ/ZIP/images retain supported manga settings. Save to phone or send to compatible XTEink. Inspect actual output using **Universal Book Converter → Preview finished XTC / XTCH book**.

Open **More → Nooir Tools → Universal Book Converter → Web novels**, paste a public story URL and tap **Analyze chapters before export**. Review detected title, author, cover and synopsis, select/reorder chapters and preview text. Choose **Use selected chapters in one EPUB** to edit book metadata, choose automatic/custom or combined covers, select EPUB2/3, adjust text styling and illustration sizes, save a local project or check for new chapters. Wattpad metadata can populate the cover and a Synopsis page plus EPUB description metadata. Public chapter listings do not guarantee readable text; no login or paywall bypass.
