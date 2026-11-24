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

### Illustration optimization, reader styles and custom EPUB CSS (experimental)

The Web → EPUB editor now offers three chapter-illustration modes: **Original** (existing embedded bytes), **Balanced** (downsample oversized JPEG/PNG to 1600 px, JPEG quality 82), and **Small** (1000 px, quality 70). Cover quality is independent. Animated GIFs remain original; PNG transparency is retained. Per-source inputs and decoded pixel dimensions are still bounded. Original image mode preserves earlier output for regression testing. If optional resizing makes a JPEG/PNG larger than its original bytes or exceeds the 2 MB image budget, the existing original illustration is retained instead of dropping it. Android memory/device measurements remain outstanding.

Both combined and separate-story exports can select **Standard**, **Compact** or **Large** text styling, plus optional user-entered **offline EPUB CSS**. Standard with no custom CSS retains the shared writer's previous XHTML output; the other presets add limited body/paragraph spacing and font-size styles. Custom CSS allows up to 12 simple tag-only rules and 2000 characters, with text-friendly properties such as font size, line height, indentation, margins, alignment, fonts and hex colors. No remote stylesheets, URL assets, at-rules, arbitrary HTML, complex selectors or scripts. Settings reach the actual EPUB2/3 XHTML through the shared writer, with host tests for CSS inclusion and rejected unsafe input. Reader applications may override EPUB font size.

### Local saved novels and chapter checks (experimental)

In **Combine pasted webpages into ONE EPUB**, **Save project** stores the book/chapter editor, cover, extraction, image and styling settings in a bounded app-private JSON library on the phone (up to 24 projects / 1 MB). **Load** restores a project. **Delete** uses a second confirmation click and removes only the saved project, not any exported book. No login credentials, cookies, browser sessions or chapter content are saved.

For a project created from **Analyze chapters**, the library also remembers the original public table-of-contents URL and *all* known chapter links, including chapters the user excluded. **Check new chapters** re-reads that public TOC and lists only URLs that were not previously known. New discoveries have a paginated 10-at-a-time checklist with **Select all new / Select none**, individual checkboxes and an explicit **Add new chapters to export list** step. Only checked chapters are appended; unchecked new chapters are marked as seen when the update is accepted, preventing them from silently reappearing later. Existing selected titles and order are preserved. The user then saves the project and explicitly creates a **new EPUB**, subject to the existing destination collision protections.

This is not in-place EPUB merging, automatic scheduled checking, guaranteed detection on dynamically loaded sites, resumable chapter downloads or a background watcher. It avoids silently reintroducing excluded chapters or mutating an EPUB already installed on the reader. Real-source manual testing is still needed.

### Android phone-cover picker (new experimental slice)

Combined-book single-cover mode now has **Choose cover from phone**, using Android's document picker restricted to JPEG/PNG. The app requests persistable read permission, so a queued WorkManager export can reopen the selected image later. Cover previews decode that exact file; the export worker reads it as a bounded content URI and embeds the validated bytes as an EPUB cover instead of relying on a remote cover URL. A selected phone cover is exclusive with a remote URL and with collage mode. The saved-project library can remember the document URI; if access is revoked or the document disappears, preview/export fails clearly and the user can reselect it.

No arbitrary filesystem path is accepted. The selected source is streamed up to 1.5 MB, verified by JPEG/PNG signature, and decoded only for the 16-million-pixel dimension guard before being embedded. Both remote and phone covers share the same image validation routine. The cover is not sent to any third-party service; the user still chooses where to export the finished EPUB.

Real Android document-provider and physical Folio Nooir testing are required before calling this stable.

### Public-page extraction fidelity (WebToEpub engine comparison — 22 November)

Independently reviewed the current WebToEpub developer branch (1.0.14.92), including parser selection, heuristic content scanning, image collection, fetch errors/backoff, chapter processing and EPUB packaging. No GPLv3 code or parser files have been copied into MihonNooir.

The draft importer now ranks HTML candidates by paragraph/prose density **minus linked navigation text**, so large TOC blocks are less likely to become chapter bodies. It also attempts validated full-resolution/lazy chapter illustration alternatives (`data-original`, `data-orig-file`, `data-srcset`, `srcset`, `data-src`, ordinary `src`) in that priority, with a maximum of four HTTP attempts per illustration and the existing HTTPS/byte limits. A chapter heading matching the source heading is no longer inserted twice. Both candidate selection algorithms have pure Kotlin tests in `tests/run.sh`.

Public webpage retrieval now uses a conservative three-attempt maximum for transient HTTP statuses (408, 429, 500, 502, 503, 504), with 0.9 s and 1.8 s default backoff. Numeric `Retry-After` headers up to eight seconds are respected; longer waits stop and report the HTTP failure instead of retrying prematurely. HTTP 403/404, logins and paid chapters never trigger retries. Redirect validation, eight-megabyte HTML limit, and the existing public-HTTPS-only policy remain. A separate pure Kotlin policy test runs in the standard host CI. This is not resumable downloading.

This improves public HTML conversions but does **not** enable browser sessions, JavaScript-rendered chapters, protected APIs, image proxy workarounds, or WebToEpub site-specific parser coverage. Next acceptance steps are actual public-site fixtures, Android device rendering and performance checks. Further independent enhancements worth testing: configurable per-site public selectors, bounded transient retry/rate limit and multi-page public tables of contents.

### Numbered public table-of-contents pagination (22 November)

The chapter analyzer and the normal novel converter now share bounded **multi-page public chapter discovery** for ordinary HTTPS sites. Only pagination controls that point to the next numbered TOC page on the same site and in the same collection are eligible: `?page=2`, `?paged=2`, `?page_number=2`, `?pagenum=2`, or `/page/2/`. Reading-page “Next chapter” links, unrelated page paths, private/cross-site URLs, changed non-pagination query parameters, and off-site redirects are not followed. Every additional TOC request is paced.

The importer examines **at most 12 numbered TOC pages per story**, merging chapters in encounter order and deduplicating tracking-only variants. If the public TOC is still continuing at this limit, a continuation repeats existing chapters, redirects outside the collection, or has no readable chapter list, it reports a clear failure **rather than exporting a knowingly partial book**. Only a real multi-chapter TOC is eligible; pages that show fewer than two discovered chapters do not trigger this navigation.

This is intentionally **not** universal pagination support: JS-only chapter lists, unnumbered “load more”, nonstandard page names and site-specific parser rules still require manual chapter URLs or future independent adapters. Missing/unsupported pagination that is not explicitly recognizable can remain undetected, so users should review the discovered chapter checklist before exporting. No protected content or session context is fetched. Pure Kotlin tests cover URL resolution, looping, redirects, same-site guards and next-chapter rejection; end-to-end real-site acceptance is still outstanding.

### Not yet implemented

Full site-specific parser compatibility, advanced cover-tile editing, complete unrestricted WebToEpub CSS/pipeline parity, background/resumable imports, automatic novel library refresh, persistent EPUB-in-place chapter merging and comprehensive real-site/device verification. A browser extension has access to the page/session context; our Android importer explicitly works with **public HTTPS only** and must not bypass logins, DRM, premium chapters or other access restrictions.

### Acceptance gates

- Kotlin Android compilation and full overlay smoke tests; synthetic EPUB2/3 cover-manifest and metadata checks in `tests/ReflowEpubTest.kt`.
- Android UI verification: settings visible, combined EPUB saved to phone and custom public HTTPS cover embedded; invalid custom cover clearly rejects book, while unavailable automatic cover falls back.
- Real Folio Nooir/XTEink reading: EPUB2 and EPUB3 cover, selected language, TOC, text and inline images; select a large novel for EPUB2 OPF size behavior.
- Real source checks using permitted, publicly readable pages; static-site and dynamic/locked-site errors must be distinct.
- Copyright/terms compliance and no user cookies or bypasses.

## Next useful improvements

1. Complete Android compilation, real phone UI acceptance (including the new document picker), EPUBCheck and independent Folio Nooir/XTEink reading, with actual screenshots.
2. Improve metadata/cover editing beyond the initial phone cover picker and actual cover preview.
3. Add reliable resumable imports and book-update workflows (without accidentally overwriting existing EPUBs).
4. Expand public-site parser tests and safe per-site extractor profiles, respecting access terms.

Do not claim feature parity with WebToEpub's extensive site-specific parser ecosystem. Do not merge or publish without review and real testing.


### 23 November — preserve percent-encoded chapter fetch paths

- A public chapter path containing `%2F` (an encoded slash) must **not**
  become a literal `/` when producing chapter links. URI reconstruction
  from decoded `.path` could previously change which public chapter was
  fetched or cause a 404. Chapter discovery now preserves raw escaped URLs
  and drops only the fragment.
- Discovery now uses the existing comparison-only chapter identity to
  eliminate repeated same-page entries with trackers or reordered unique
  query parameters, while retaining the very first original fetch URL.
  `/chapter/a%2Fb` and `/chapter/a/b` remain different chapters.
- Added a network-free, real Android/Jsoup table-of-contents regression.
  The gated TOC instrumentation suite now expects **six** cases. This
  is URL correctness, not site-specific parser parity or authenticated
  chapter support. Keep PR #110 separate and draft.


### 23 Nov 2026: public chapter illustration signature validation

- Validate bytes of downloaded JPEG, PNG and GIF chapter illustrations against
  their declared MIME before adding them to the EPUB. Some CDNs return a login,
  cache or error HTML page incorrectly labeled as `image/jpeg`.
- Such responses now fail the current candidate and allow the existing
  bounded `srcset`/lazy-image fallback to try another publicly accessible
  alternative, rather than embedding broken illustration bytes.
- The signature check is a cheap, offline byte-prefix contract (not a complete
  media decoder); the existing 2 MiB budget, HTTPS validation, redirect policy,
  optional downsampling and EPUB packaging remain unchanged. Added a pure
  Kotlin unit test to `tests/run.sh`. No auth/session bypass or remote
  redirect following is introduced.


### 24 Nov 2026: incomplete single-article SAF cleanup

- The single-article Web → EPUB Save-to-phone picker uses Android
  `CreateDocument`, which creates a destination file **before** its
  background download/conversion begins. Previously a failed article download,
  malformed page, failed copy or in-flight cancellation could leave a
  zero-byte/partial EPUB on the phone.
- Added a scoped `SAVE_PICKER_CREATED` WorkManager field. The worker now
  best-effort deletes this newly created document on early invalid inputs,
  conversion/copy failure and cancellation. It **never** deletes a previously
  existing destination URI from other callers without that explicit marker.
- The successful save path is untouched. The multi-chapter exporter already
  deletes individually failed SAF documents; its guard remains intact.
- A host structural regression now runs in `tests/run.sh`. This does not
  guarantee cleanup if WorkManager is cancelled before the worker *starts*,
  or a document provider rejects `deleteDocument`.
- No public Android APK, converter encoder, Windows or Folio firmware changes.
