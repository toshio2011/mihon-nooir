# WebToEpub-inspired Web → EPUB options — experimental Android draft

> **Not in the public v1.0.19 release.** This is independent MihonNooir implementation on a review branch. No public APK/release change; no Windows reader or Folio Nooir firmware change.

## Source comparison (inspected 21 November 2026)

WebToEpub: https://github.com/dteviot/WebToEpub (current `ExperimentalTabMode` source), `plugin/popup.html`, `plugin/js/UserPreferences.js`, `plugin/js/CoverImageUI.js`, and project wiki [Advanced Options](https://github.com/dteviot/WebToEpub/wiki/Advanced-Options). It offers editable cover URL/preview, metadata (author/language/description), configurable stylesheet, image selection/compression, chapter selection/cleanup, parser overrides, library/update, error handling, EPUB2/3 and user preferences. Its source is GPLv3; this draft does **not** copy or vendor GPL code into MihonNooir's MIT sources.

### First independent implementation

**Android → More → Nooir Tools → Web novels / Batch EPUB**, combined-webpage mode:

- Title, author and output filename remain editable, with *new* description and BCP-47-ish language fields.
- **Cover image URL**: optional public HTTPS JPEG/PNG, up to 1.5 MiB and 16 MP; a selected invalid cover fails clearly rather than emitting a misleading book. Empty URL attempts `og:image`, `twitter:image` or `image_src`, best effort.
- Cover image is embedded in the EPUB manifest, and a real Cover section appears before the chapters in both EPUB2/3. Uses existing EPUB writer cover properties / EPUB2 metadata and guide entries; not a screenshot overlay.
- **EPUB 2 compatibility** checkbox (unchecked/default: EPUB 3) for both combined and separate-story exports. EPUB2's existing 512 KiB OPF guard still applies for extremely large novels.
- **Include inline illustrations** checkbox for both modes (on by default). Turning off strips chapter images, but a cover is still allowed for novel and combined books.
- One-book failure is atomic: an invalid explicit cover, locked/unreadable chapter or invalid metadata must not save a partial EPUB; the shared WorkManager destination checks and sequential XTEink upload remain unchanged.
- Individual separate-story mode retains best-effort automatic cover, original source title/metadata and chapter range, with the two new global format/inline image controls.

### Combined cover from multiple books (collage)

In **Combine pasted webpages into ONE EPUB**, check **Make one combined cover from multiple book covers**. Two ways:

- **Automatic:** leave the individual cover list empty. The first four submitted public source pages are inspected for distinct `og:image`, Twitter image or `image_src` cover metadata; 2–4 successfully loaded distinct covers become a single 800×1120 portrait JPEG front cover. The first source pages are reused as chapters, not fetched twice. This makes most sense when pasted URLs represent separate books/works, not consecutive chapters from one book.
- **Manual:** paste 2–4 direct public HTTPS JPEG/PNG *cover image URLs*, one per line. These can be covers of the separate works being combined, even if the chapter pages have no cover metadata. Missing, private, oversized, badly encoded or unsupported selected covers fail the combined job clearly instead of silently using a wrong cover.

The collage uses fit-contain tiles to avoid cropping cover artwork, with a dark neutral background. Its compressed output is bounded to 1.5 MiB. It becomes **one real EPUB cover manifest image and Cover spine section**, not extra chapters, thumbnails or a preview-only graphic. EPUB2 and EPUB3 share the output, and the ordinary single automatic/custom cover workflow remains the default. A collage with fewer than two distinct accessible covers is an explicit validation error, with a suggestion to use manual sources or switch to a single cover.

**Scope:** this currently combines public webpage/chapter links into one EPUB. Importing several existing local `.epub` files and merging their full books/metadata is a *different* feature and is not implemented by this option. The renderer and pure-Kotlin tile geometry are independent of WebToEpub GPLv3 sources. Android device rendering, actual page-cover retrieval and physical Folio Nooir reader acceptance still need verification.

### Chapter analysis and selection before export (new experimental slice)

In **Web novels / Batch EPUB**, paste **one public story/table-of-contents URL** while separate-story mode is active, then tap **Analyze chapters before export**. This fetches only public HTML or the same limited public Wattpad story metadata already used by the converter; no browser login or premium chapter access. Analysis is separate from exporting.

The analyzer displays the detected story title, author, cover URL (if provided by the site), total chapter count, and a paginated 10-at-a-time checklist with **select all**, **select none**, **reverse**, **move up/down** and **Preview text** per chapter. Text preview performs an explicit public chapter request and uses exactly the same readable-content selection routine as the EPUB exporter, but skips downloading illustrations. It reports missing/locked/dynamic prose rather than inventing an extract.

**Use selected chapters in one EPUB** copies only included chapter links in the chosen order to the existing combined-book editor, with matching editable chapter titles and suggested source title/author/language/description and source cover URL. The user still confirms the export destination, final cover, EPUB version and illustrations. It does not start a background export automatically. This does **not** add a full rendered EPUB page preview or a browser-session reader.

Safety: analysis is a user-initiated foreground action, with IO off the Compose main thread; stale results are discarded if the link is changed. The chapter plan validates HTTPS URLs, ordering, duplicates and the 120 KiB link-manifest budget. A fully selected 1000+ chapter story may exceed that budget: select fewer chapters or split volumes rather than silently truncating. Public chapter-list metadata never guarantees every chapter can be read; conversion still aborts atomically on unreadable pages.

CI: added pure Kotlin `tests/NooirWebChapterPlanTest.kt` to `tests/run.sh` covering selected order, exclude/reverse/move, duplicate/empty/oversize manifests, and metadata sanitization. Android Compose compile and device UI acceptance remain mandatory. No official APK change and no Windows code changes.

### Public chapter extraction test (new experimental slice)

In combined-webpage EPUB mode, optional **Read chapter content from CSS selector** and **Remove unwanted elements** settings let readers recover prose from a public site's specific HTML wrapper and remove advertisements or social widgets. Empty fields keep the established automatic selection. Examples: `article .chapter-body` and `.advertisement, .social-share`.

**Test extraction on first chapter** downloads the first selected public HTML page without illustrations and previews its first 800 characters. The actual EPUB export passes these same selector rules through the existing private WorkManager job to the **same** readable-content routine. A custom selector that finds no readable text fails rather than silently falling back to a navigation menu or saving a partial EPUB.

Rules are intentionally bounded: 160 characters per field, at most four comma-separated simple selectors and five descendant components per selector. Tags, IDs and class selectors are allowed; pseudo-selectors, attribute selectors and arbitrarily complex CSS are rejected. No browser scripts, cookies or premium content are involved. Pure Kotlin contract tests run in `tests/run.sh`. Android integration and real-site tests are still required.

These are *content selection* controls, not EPUB style-sheet editing or custom per-site parsers.

### Actual EPUB cover preview (new experimental slice)

The combined-book editor now provides **Preview selected book cover** or **Preview actual combined cover** after the source cover URL(s) are set. This is an explicit, cancellable-on-dialog-dismiss UI request to fetch public HTTPS artwork, decode a downsampled on-screen preview and display it within the editor before export. It does not save or upload the book. Automatic source covers are best-effort; a missing cover displays a clear preview error.

The combined preview reuses the *same* source-image validation, distinct-artwork deduplication, tile geometry and JPEG collage composer used by EPUB export; it is not a misleading mockup. The preview is downsampled for display, while export keeps the full 800×1120 composited image. Manual covers must still be public JPEG/PNG images and collage mode requires 2–4 distinct covers. No new third-party image service or browser session is involved.

Android compilation plus real phone preview and final EPUB rendering are still acceptance gates. There is not yet an on-device photo picker or cover-tile editor.

### Not yet implemented

Phone-file cover picker, editable EPUB stylesheets, per-site parsers beyond current source handling, image compression/resizing, saved novel presets, persistent chapter checklists/retry/resume and WebToEpub-style library update/merge. A browser extension has access to the page/session context; our Android importer explicitly works with **public HTTPS only** and must not bypass logins, DRM, premium chapters or other access restrictions.

### Acceptance gates

- Kotlin Android compilation and full overlay smoke tests; synthetic EPUB2/3 cover-manifest and metadata checks in `tests/ReflowEpubTest.kt`.
- Android UI verification: settings visible, combined EPUB saved to phone and custom public HTTPS cover embedded; invalid custom cover clearly rejects book, while unavailable automatic cover falls back.
- Real Folio Nooir/XTEink reading: EPUB2 and EPUB3 cover, selected language, TOC, text and inline images; select a large novel for EPUB2 OPF size behavior.
- Real source checks using permitted, publicly readable pages; static-site and dynamic/locked-site errors must be distinct.
- Copyright/terms compliance and no user cookies or bypasses.

## Next useful improvements

1. Add an optional on-phone cover picker and richer metadata panel; the single/collage cover **preview** is now implemented experimentally.
2. Add saveable **content selector presets per public site**, building on the manual preview and unwanted-element removal now wired into the experimental combined-book flow.
3. Add image sizing/compression and a bounded stylesheet preset picker (avoid accepting arbitrary unsafe CSS before sanitization).
4. Save per-site nonsecret importer preferences, with explicit retry/resume for permitted sources.

Do not claim feature parity with WebToEpub's extensive site-specific parser ecosystem. Do not merge or publish without review and real testing.
