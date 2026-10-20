# MangaNooir FAQ

[← All guides](README.md) · [Nooir Tools quick start](NOOIR_TOOLS_QUICK_START.md) · [Getting started](GETTING_STARTED.md)

> **Version:** Main-branch code is **v1.0.1**. The newer [experimental v1.0.11 APK](https://github.com/toshio2011/mihon-nooir/actions/runs/39363272479) passed CI, but **physical phone/XTEink testing is still pending**. Features under **Nooir Tools** require the experimental build.

### Where is the chapter picker?

In the **manga details chapter list**. Long-press a chapter, tap any others, and press the toolbar **Export** icon. The Export dialog is for format/device/destination, not for selecting chapters. [Guide](CHAPTER_SELECTION.md)

### Can I export online chapters without downloading them permanently?

In the preview, yes: select chapters first and turn on **Quick Export · fetch missing pages** in manga Export. Normal Export only takes already-downloaded selected chapters; Normal Export without any selection takes **every downloaded chapter**. Quick Export needs a working online source extension. [Guide](CHAPTER_SELECTION.md)

### I can't find “Nooir Tools” under More.

You're likely running the older **v1.0.1 main APK**. The newer Tools hub exists in **experimental v1.0.11**, built from [PR #18](https://github.com/toshio2011/mihon-nooir/pull/18). There is no stable release of it yet. [Installation](GETTING_STARTED.md)

### Can I send CBZ, PDF, EPUB, FB2 or other files from my phone?

**More → Nooir Tools → Send & Convert files** uses Android's picker. You can send files **unchanged**; supported CBZ/image manga inputs may also be converted. A file being transferable doesn't guarantee the reader can open it. Up to **25 local files** per batch. [Quick start](NOOIR_TOOLS_QUICK_START.md)

### Can I convert multiple web links into **one EPUB**?

**Yes, in the experimental v1.0.11 code.** Open **More → Nooir Tools → Web link → EPUB → Multiple web links → one EPUB**. Paste public readable article/chapter URLs **one per line**. The lines become EPUB chapters in that order; optionally enter one book title, author, filename and chapter titles. You can reach the same combined editor from **Web novels · Batch EPUB**. [Full multi-link guide](WEB_NOVELS.md)

### What about one EPUB **per novel** and “Download all chapters”?

In **Web novels · Batch EPUB**, leave combined mode off, paste up to **10 public story URLs**, and select **All chapters** (default) or a manual First/Last range. One story produces one EPUB. There is no fixed 80-chapter maximum: “All” means **every chapter actually discoverable in public HTML**, not hidden pages. [Web novels guide](WEB_NOVELS.md)

### Why do a Wattpad story URL or a web-novel page fail?

Some links open a **story overview** or use JavaScript/authenticated chapter lists that the HTML extractor cannot read. A genuine direct public chapter URL may work; locked chapters, paywalls and scripts are not bypassed. A page containing just title/synopsis is not a complete EPUB chapter. The queue should report which story/page failed. [Web guide](WEB_NOVELS.md)

### Can I turn PDF novels into editable text EPUBs?

The preview's **PDF → EPUB · Text + illustrations** tool extracts **real selectable text** when a PDF has a text layer, retaining accessible pictures and preserving difficult/scanned pages as images in the **same EPUB**. It doesn't perform OCR on image-only pages. Check decorative typography, tables and diagrams after converting. [PDF guide](PDF_TO_EPUB.md)

### What if a PDF has both text and illustrations?

That's the main target. The converter attempts to interleave text and figures at similar vertical positions; complex pages may need whole-page image fallback. EPUB output may need visual corrections, and X4 testing is pending. [PDF guide](PDF_TO_EPUB.md)

### Does one failed conversion stop all queued books?

The **v1.0.11 experimental** WorkManager queue records per-book failure and allows later queued books to continue; a bad file inside a local batch also shouldn't block subsequent files. Open **More → Nooir Tools → Activity Queue → History** to inspect failures. **Cancel entire queue** is intentionally different; it stops waiting jobs too. [Queue guide](DEVICE_FILES_AND_QUEUE.md)

### Does MangaNooir overwrite a book with the same name?

The current preview checks output filenames **before converting** and again before upload, and rejects existing destination names instead of intentionally overwriting them. Choose another name or folder. The preflight check alone isn't an atomic guarantee against simultaneous uploads; the reader must also protect the file. [Queue / names guide](DEVICE_FILES_AND_QUEUE.md)

### Why does XTC/XTCH show the wrong cover or no cover?

Folio Nooir builds the shelf thumbnail from **the first page of the XTC/XTCH file**. The v1.0.11 preview requires a readable cover first; an unavailable source cover should cause a clear export error. If first book page is correct but the shelf image is missing, run **Retrieve All Book Details** on Folio Nooir. Synopsis metadata from manually copied XTC files may depend on a separate experimental firmware change. [Covers and metadata](XTC_METADATA.md)

### What output format should I choose?

**XTC** for high-contrast monochrome pages, **XTCH** for four-level grayscale, **EPUB** for a comic/image ebook or text-based exports where applicable, and **CBZ** for an ordered comic archive. **Auto** selects a device-oriented format (X4 typically XTCH), not a guarantee that every comic looks best. [Format guide](CONVERSION.md)

### Do I need Bluetooth, USB or an online server for e-reader transfers?

No. Use Folio Nooir **File Transfer** on the same reachable local Wi-Fi, or its temporary hotspot. The phone can test the on-screen reader IP in Chrome first. Type just the **IP/hostname**, not `http://`, into MangaNooir. [Wi-Fi guide](WIFI_TRANSFER.md)

### Can I leave MangaNooir during conversion?

The experimental tools use background WorkManager jobs, and you can navigate away. Android battery rules, forced stop, Wi-Fi loss and storage failures can interrupt them. Wait for **XTEink receiver confirmation** rather than assuming 100% upload means success. [Queue](DEVICE_FILES_AND_QUEUE.md)

### Where can I get the app? Can it replace regular Mihon?

There are **no published stable GitHub Releases** yet. Download the [successful v1.0.11 GitHub Actions APK artifact](https://github.com/toshio2011/mihon-nooir/actions/runs/39363272479). MangaNooir has a separate package identity and can coexist with normal Mihon. Back up MangaNooir before changing debug builds, because signing keys may differ. [Install guide](GETTING_STARTED.md)

### Is MangaNooir official, and does it include free manga?

No. This is an independent project based on Mihon, inspired by MangaPoint and built alongside Folio Nooir. It **does not bundle manga, content-source extensions or a catalog**. Use sources and documents you are entitled to access.

### My problem isn't listed. What should I report?

Share your **build/commit, Android version, input format/source, XTEink model/Folio Nooir firmware, destination folder and exact error message**, without posting private documents, credentials or copyrighted manga pages. [Roadmap and issues](https://github.com/toshio2011/mihon-nooir/issues/8)
