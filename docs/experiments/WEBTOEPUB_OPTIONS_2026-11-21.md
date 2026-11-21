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

### Not yet implemented

Actual cover preview/phone-file picker, editable stylesheets, content-selector tester, per-site parsers beyond current source handling, image compression/resizing, saved novel presets, persistent chapter checklists/retry/resume and WebToEpub-style library update/merge. A browser extension has access to the page/session context; our Android importer explicitly works with **public HTTPS only** and must not bypass logins, DRM, premium chapters or other access restrictions.

### Acceptance gates

- Kotlin Android compilation and full overlay smoke tests; synthetic EPUB2/3 cover-manifest and metadata checks in `tests/ReflowEpubTest.kt`.
- Android UI verification: settings visible, combined EPUB saved to phone and custom public HTTPS cover embedded; invalid custom cover clearly rejects book, while unavailable automatic cover falls back.
- Real Folio Nooir/XTEink reading: EPUB2 and EPUB3 cover, selected language, TOC, text and inline images; select a large novel for EPUB2 OPF size behavior.
- Real source checks using permitted, publicly readable pages; static-site and dynamic/locked-site errors must be distinct.
- Copyright/terms compliance and no user cookies or bypasses.

## Next useful improvements

1. Add a **live chapter-list and single/collage cover preview** before conversion, then let users select/deselect/reorder chapters.
2. Add safe **content CSS selector presets** with preview against fetched public HTML, plus unwanted-element removal.
3. Add image sizing/compression and a bounded stylesheet preset picker (avoid accepting arbitrary unsafe CSS before sanitization).
4. Save per-site nonsecret importer preferences, with explicit retry/resume for permitted sources.

Do not claim feature parity with WebToEpub's extensive site-specific parser ecosystem. Do not merge or publish without review and real testing.
