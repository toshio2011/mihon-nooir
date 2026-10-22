# Manga reading progress and Folio Nooir KOReader Sync

Status: **feasibility study only; no synchronization feature shipped**.

## What Folio Nooir currently implements

Inspected source in `toshio2011/folio-nooir`:
- `src/activities/reader/KOReaderSyncActivity.cpp/.h`: EPUB-specific sync activity, EPUB spine/page mapping, KOReader-style XPath and percentage.
- `lib/KOReaderSync/KOReaderDocumentId.cpp`: document identity is either MD5 of the filename or KOReader's sampled-binary MD5 (1 KiB chunks at fixed offsets).
- `lib/KOReaderSync/KOReaderSyncClient.h`: KOReader Sync API uses GET `/syncs/progress/:document`, PUT `/syncs/progress`, and standard `document`, `progress` (XPath), `percentage` fields.
- `src/activities/settings/KOReaderSettingsActivity.cpp`: configurable server, filename/binary matching and Ask/Smart behavior.

This is **not** currently an XTC/XTCH manga progress protocol. The existing Folio Nooir sync activity operates on EPUB files.

## Why direct Mihon ↔ Folio syncing is not plug-and-play

Mihon stores reading state as source manga/chapter IDs and chapter-relative page positions. Folio Nooir's KOReader Sync uses a **file-based** document hash and an EPUB content XPath plus percentage. The source manga ID, exported file ID, chapter order and exact page layout may change when chapters are combined or re-exported. An arbitrary chapter percentage cannot safely be mapped to the same EPUB XPath.

Even for manga exported as EPUB, fixed-layout image XHTML is different from reflowable prose. We must test that Folio Nooir's XPath mapper can resolve an image-page anchor; percentage alone is not an exact page position.

## Recommended staged experiment (no firmware modifications in this PR)

1. **EPUB only, opt-in.** Export one stable manga EPUB with a fixed chapter selection, consistent filename and metadata. Test Folio Nooir's actual KOReader Sync on the file before attempting a Mihon-side integration.
2. **Define stable mapping.** Record exported chapter IDs, chapter boundaries and per-page indices. Do not assume Mihon's chapter number equals the EPUB spine index.
3. **Match the same document.** Choose the filename hash mode or calculate the actual exported file hash. A manga URL/series title is not the KOReader document ID.
4. **Validate round-trip.** Read on Folio Nooir, sync, inspect the server's `progress` and `percentage` values, then verify they resolve to the expected chapter/page. Test re-export and a different chapter selection.
5. **Only then consider a separate opt-in Mihon bridge.** Require user-supplied KOReader Sync server and credentials, conflict resolution and explicit privacy settings. Never copy credentials from Folio Nooir.
6. **XTC/XTCH remain out of scope** until Folio Nooir has a compatible reader-side progress mapping and sync entry point. Do not modify `toshio2011/folio-nooir` from this project.

Avoid automatically uploading Mihon reading activity to any server. Existing conversion and queue behavior must remain independent of KOReader Sync.
