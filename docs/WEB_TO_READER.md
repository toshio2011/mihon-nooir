# Web link → EPUB (experimental v1.0.11)

> **Preview only:** The v1.0.11 [Android build](https://github.com/toshio2011/mihon-nooir/actions/runs/39363272479) passed automated checks. This workflow is **not on the v1.0.1 main app**, and phone/XTEink testing is still pending. Conversion runs on the Android phone; no AI/OCR API.

## Convert a website link to EPUB

1. Open **MangaNooir → More → Nooir Tools → Web link → EPUB**.
2. Paste a **public HTTPS** web article URL in **Article URL**.
3. Tap **Save article EPUB to phone**. Pick the file location in Android's save dialog.
4. Wait for the background notification/phase to say **Article EPUB saved successfully**.
5. Open the saved EPUB in a compatible reader; font size and line flow remain adjustable.

## Send the resulting article to XTEink

1. Put your XTEink in **Folio Nooir → File Transfer** and connect your phone to the same trusted local network (or use the XTEink hotspot).
2. In **Web link → EPUB**, paste the URL and enter the XTEink **IP address without `http://`**, e.g. `192.168.4.1` (use the address your device displays).
3. Enter an existing destination folder (e.g. `/Books`), then tap **Convert article & send to XTEink**.
4. Keep Wi-Fi connected; the worker runs in the background. **100% upload** is not success until the reader acknowledges the file.
5. Check the reader library for the uploaded EPUB.

## Combine multiple webpage links into one EPUB

The **Web link → EPUB** screen also has a **Multiple web links → one EPUB** action. Tap it to open the shared combined-book editor (also available via **Web novels · Batch EPUB**).

1. Paste one publicly readable **webpage/article/chapter URL per line**, in reading order.
2. Optionally supply book title, author, EPUB filename and one title per chapter.
3. Save the combined EPUB to your phone or send it to XTEink. Every URL becomes one EPUB chapter and one TOC entry.
4. If a chapter is unreadable or requires JavaScript/login, this **one combined book fails** without saving a misleading partial EPUB; other queued jobs continue.

This is not just for web novels: ordinary public webpage links work too. A **Wattpad story overview** is not a publicly readable chapter, so a story link alone may fail; use genuinely readable chapter URLs when available.

[Full multi-link and web-novel guide →](WEB_NOVELS.md)

## What the extractor preserves

- Article title, byline/author where available, description, source URL and language.
- Readable paragraphs, headings, lists, quotes, links and supported inline **JPEG/PNG/GIF images**.
- Real EPUB XHTML text, not screenshots. Images are embedded locally for offline reading.
- Up to **25 article images**, with conservative per-image file-size caps and a 3 MB HTML cap.

## Current limitations

- Manual **paste URL** first. Browser-share integration is a separate upcoming improvement.
- No JavaScript execution. Some social feeds, dynamic sites, paywalled/login-only articles, and sites with strict anti-scraping rules may not work.
- Public **HTTPS only** in this first iteration; private/local/intranet addresses are rejected.
- Source markup varies widely; headers, captions, galleries, interactive elements and complex multi-column layouts won't always be reconstructed perfectly. Missing images can be skipped rather than preventing the entire EPUB.
- Does **not** bypass access restrictions, recover paid content you lack rights to view, or download a full website.
- You can edit the EPUB output filename in the tool before converting or choose another name in Android's Save dialog. On XTEink, an existing filename is checked before conversion and again before upload.
- Automated format and Android build checks do not prove EPUB rendering on actual Folio Nooir hardware.

## Not the same as Quick Export or PDF conversion

- **Quick Export** works with Mihon's manga chapter list via the installed online source extension.
- **Web to E-reader** converts an article **from a pasted link**.
- **PDF → mixed-content EPUB** is a separate experimental converter. It extracts actual text when available and keeps difficult/scanned pages as images. [PDF guide](PDF_TO_EPUB.md).

See [all documentation](https://github.com/toshio2011/mihon-nooir/blob/main/docs/README.md) and the [roadmap](https://github.com/toshio2011/mihon-nooir/issues/8).
