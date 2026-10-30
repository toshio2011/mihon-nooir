# Web link → EPUB (experimental v1.0.11)

> **Current release (29 October 2026):** [MihonNooir v1.0.16](https://github.com/toshio2011/mihon-nooir/releases/tag/v1.0.16) is the official Android release with persistent-key-signed APKs. References to v1.0.11/v1.0.12 below are historical; the Windows app is still in development.

> **Historical preview note:** These workflows began in earlier v1.0.11/v1.0.12 builds. Use official v1.0.16 for the current Android release; physical compatibility still varies.

## Convert a website link to EPUB

1. Open **MihonNooir → More → Nooir Tools → Web link → EPUB**.
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

## A webpage shows text in the browser, but EPUB extraction fails

A site such as `https://syazwandaniial.com/rengkang` may look readable in a normal browser while a direct public-HTML request to the converter contains very little text. This can happen when the content lives inside JavaScript-rendered components, a logged-in session, a separate embedded frame, or a site-builder layout the generic parser doesn't recognize. **Seeing text in your browser doesn't by itself establish that the same text is available in public HTML.**

The latest preview build passed Android CI, but Rengkang still needs an actual phone-side test. [Merged PR #21](https://github.com/toshio2011/mihon-nooir/pull/21) includes the earlier work to expand the public HTML extractor with ordinary mobile-browser request headers, additional site-builder article containers and openly supplied structured `articleBody` text. It also reports approximately how much readable HTML text arrived, which helps separate "missing content" from a conversion defect. These changes need a fresh phone-side test; the live Rengkang page could not be independently inspected from the development environment.

No sign-in session, site protection or paid access is bypassed. If a provider offers an authorized EPUB, save it and use **Send & Convert files** instead. If the latest preview still cannot read an ordinary publicly visible article, send a screenshot showing its page layout and the **copied error message** (not its private content or cookies), so a specific extractor can be tested.

