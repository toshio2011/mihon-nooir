# Managing files already on your XTEink (experimental 1.0.9)

**You can already manage the XTEink's SD-card files using existing Folio Nooir firmware**, without installing any new reader firmware or waiting for MangaNooir 1.0.9.

## Available now: existing Folio Nooir web file manager

1. On your XTEink, open **File Transfer**.
2. Choose **Join Network** or **Create Hotspot**. Connect your Android phone to the same reachable Wi-Fi; use the actual IP address displayed on the reader.
3. Open your phone's browser and visit `http://READER-IP/files` (example: `http://192.168.4.1/files`, if that is the IP shown).
4. Browse folders, upload or download books, create folders, rename, move, or delete items using the existing firmware web interface.
5. Close File Transfer when you finish; the normal reader stays focused on reading.

**Be careful with Delete:** it removes SD-card content, not merely a MangaNooir library reference. Confirm the exact filename before deleting. Never rename/delete a file while uploading it. The firmware protects some hidden/system file names, but do not rely on that as a backup.

## Experimental: MangaNooir built-in XTEink Device Files

In the upcoming experimental **MangaNooir 1.0.9** build:

1. Open **MangaNooir → More → XTEink Device Files**.
2. Enter the same XTEink IP/hostname used for upload (without `http://`), then tap **Connect & browse**.
3. Tap a folder to open it, **Up folder** to return, or **Refresh** to request that folder again.
4. File names and approximate sizes are shown. For full create/download/rename/move/delete actions, select **Open full file manager in browser**. That opens the existing Folio Nooir website.
5. The built-in view is intentionally **read-only** for now. Editing actions remain in the firmware web manager, avoiding a second set of destructive code and duplicated file-management logic.

### Why this should stay light on X3/X4

- Uses only the **existing** `GET /api/files?path=...` endpoint and `/files` website—**no firmware patch required**.
- **No background polling, recursive scans, cover thumbnail generation, content downloads, or storage copies** from the reader. Each explicit folder Browse/Refresh fetches only that folder's file names and sizes.
- Pauses built-in folder refresh when it can detect an active manga or local-file transfer, reducing simultaneous SD reads during uploads.
- Handles HTTP response limits and networking in the **phone**. It does not launch a service in the normal e-reader UI.
- The existing `/api/files` server streams its response and yields while walking entries. Giant directories should be split into smaller folders to keep the ESP32 responsive.

## Multiple manga exports and their queue

**You can queue manga A, manga B, manga C while A is still uploading.** The Manga Details exporter already uses WorkManager's unique **sequential** queue, so the next manga waits until the previous one has finished.

**Why a book seemed to disappear:** versions through 1.0.8 showed **only the last queued manga job**. The first job can remain running even while the More screen shows the second queued job.

The experimental 1.0.9 More → Send & Convert screen fixes that display:

- Shows **running, enqueued, waiting, completed and failed manga exports** as separate named rows, sorted by status.
- Shows the current chapter/transfer progress for the running manga and **Waiting for previous manga to finish** for queued items.
- Allows cancelling an individual job. **Important:** cancelling a job in a WorkManager dependency chain may also affect later dependent jobs; check their status and requeue as needed.
- The old sender still has a separate **local file transfer** queue; do not start unrelated file uploads and multiple manga transfers simultaneously on a resource-constrained ESP32. Full device-wide IO coordination is a planned improvement.

## Troubleshooting

- **Can't open `/files`:** turn on **File Transfer** and check the IP shown by the device. Test the URL in your phone browser.
- **Works in browser but not the app:** check the host without `http://`; allow MangaNooir network access; use Wi-Fi rather than unrelated cellular/mobile networks.
- **Reader transfer is active:** the in-app folder list intentionally pauses new refreshes until that transfer ends. The previously loaded folder stays visible.
- **Old manga still isn't visible:** old builds only remember the most recently queued job. Do not immediately queue the same book twice; inspect the XTEink after the transfer finishes, and check Android's MangaNooir notification.
- **A folder has a large number of files:** switch to the reader's `/files` web manager or organize books into subfolders.
- **A copied XTC/XTCH title appears without its synopsis:** see the separate experimental [XTC metadata guide](https://github.com/toshio2011/mihon-nooir/blob/feature/xtc-cover-synopsis/docs/XTC_METADATA.md).

This guide covers in-progress code; Android CI and physical device checks must pass before treating experimental 1.0.9 as a ready-to-install APK.

## Experimental follow-up — isolated failures and safe filenames (draft PR #18)

- **A conversion/upload failure is not a queue-wide failure.** The task's output records the error while the sequential dependency continues. In the Nooir Activity Queue, the book is shown as failed even though WorkManager considers its dependency node complete. Check each book, not only the overall queue status.
- **Before expensive conversion**, MangaNooir requests the exact destination folder's existing filenames and rejects a collision. It also checks again immediately before HTTP upload. If it cannot safely read the target folder, it stops instead of guessing. The check is not an atomic reader-side reservation; external file manager races still require firmware's own no-overwrite rule.
- Set a **different filename** in manga export, local Send & Convert, web article, PDF or per-story web novel dialogs before queuing. For converted output, the selected output format's extension is added.
- In a batch of phone files, an individual file collision/error is reported without stopping later selected files.
- Tool dialogs no longer suggest that canceling an individual WorkManager dependency is safe. **Nooir Tools → Activity Queue** offers a confirmed **cancel entire queue** action. Cancellation still stops waiting jobs.
- Existing files are never intentionally overwritten by the new destination-check path; Android's Save As document picker remains in control of explicitly chosen phone filenames.

**Still experimental:** Android CI and real X4 tests must pass. Device-side atomic no-overwrite behavior, duplicate-case handling, SD-full failures and simultaneous browser uploads need testing.

## Troubleshooting XTC/XTCH covers (20 October preview)

Folio Nooir's XTC/XTCH reader builds its shelf cover **from page 0** of the exported file. An XTC header thumbnail-offset field or a separately uploaded JPEG is **not** required for the existing reader. However, the XTEink **shelf thumbnail** is also cached: on the reader, use **Retrieve All Book Details** to generate missing thumbnails for newly uploaded files.

For new MangaNooir previews, XTC/XTCH exports now require the manga's actual cover to decode and become first page. If Mihon's custom, cached or public source cover cannot be decoded, the job returns an actionable cover error instead of being marked complete with the first chapter page as an accidental cover.

When verifying:

1. Make sure the manga cover is visible in Mihon first. For a missing cover, revisit/refresh its manga details, then retry.
2. Export **one** XTC or XTCH book to a **new filename**. Existing names are not overwritten by the experimental collision guard.
3. Open the generated book on XTEink: **page 1 should be the series cover**, followed by selected chapters.
4. In Folio Nooir's Library, run **Retrieve All Book Details** if the shelf still shows no thumbnail. Existing thumbnail cache entries may need a refresh.
5. Verify both formats separately, with X3/X4 output settings, and record if the cover is missing in the book versus missing *only on the shelf*.

The existing reader calculates thumbnails from first-page data using native cache storage. This has not yet been physically confirmed with a new MangaNooir build; no firmware-side changes were merged.
