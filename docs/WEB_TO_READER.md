# Web link → E-reader (experimental 1.0.7)

**Status:** Experimental draft; not in MangaNooir's stable `main` APK. This first implementation converts **ordinary public HTTPS article pages** into **reflowable EPUB 3**, keeping genuine selectable text and supported illustrations together. No AI/OCR API is used; conversion runs locally on your Android phone.

## Convert a website link to EPUB

1. Open **MangaNooir → More → Web to E-reader**.
2. Paste a **public HTTPS** web article URL in **Article URL**.
3. Tap **Save article EPUB to phone**. Pick the file location in Android's save dialog.
4. Wait for the background notification/phase to say **Article EPUB saved successfully**.
5. Open the saved EPUB in a compatible reader; font size and line flow remain adjustable.

## Send the resulting article to XTEink

1. Put your XTEink in **Folio Nooir → File Transfer** and connect your phone to the same trusted local network (or use the XTEink hotspot).
2. In **Web to E-reader**, paste the URL and enter the XTEink **IP address without `http://`**, e.g. `192.168.4.1` (use the address your device displays).
3. Enter an existing destination folder (e.g. `/Books`), then tap **Convert article & send to XTEink**.
4. Keep Wi-Fi connected; the worker runs in the background. **100% upload** is not success until the reader acknowledges the file.
5. Check the reader library for the uploaded EPUB.

## What the first version preserves

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
- The saved filename currently defaults to **Article.epub**; rename it in Android's Save dialog if desired.
- Automated format and Android build checks do not prove EPUB rendering on actual Folio Nooir hardware.

## Not the same as Quick Export or PDF conversion

- **Quick Export** works with Mihon's manga chapter list via the installed online source extension.
- **Web to E-reader** converts an article **from a pasted link**.
- **PDF → mixed-content reflowable EPUB** is a separate planned converter that must extract PDF text and diagrams without turning all the novel's real text into bitmap pages.

See [all documentation](https://github.com/toshio2011/mihon-nooir/blob/main/docs/README.md) and the [roadmap](https://github.com/toshio2011/mihon-nooir/issues/8).
