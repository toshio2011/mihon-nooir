# Browse XTEink files, Activity Queue and filename conflicts

> **Screenshot guide:** Queue and device-file screenshots are pending. See [Android screenshot coverage](ANDROID_VISUAL_GUIDE.md#screenshot-coverage-by-task).

> **Current Android release:** [MihonNooir v1.0.20](https://github.com/toshio2011/mihon-nooir/releases/tag/v1.0.20) (24 November 2026). This guide describes shipped features; see [release notes](releases/v1.0.20.md). Individual inputs, sources and reader firmware may behave differently. Windows is not publicly released.

[← All guides](README.md) · [Nooir Tools quick start](NOOIR_TOOLS_QUICK_START.md) · [Wi-Fi transfer](WIFI_TRANSFER.md)

> [!IMPORTANT]
> The built-in **Nooir Tools** and shared **Activity Queue** are included in v1.0.20. Mihon's download queue remains separate.

## The built-in reader file browser

1. On the XTEink, start **File Transfer → Join Network** or **Create Hotspot**.
2. Connect your Android phone so it can reach the reader. Confirm the browser opens the IP shown on XTEink.
3. In MihonNooir choose **More → Nooir Tools → Browse XTEink files**.
4. Enter the device **IP/hostname only**, not `http://`. Tap **Connect & browse**, enter folders, use **Up folder**, or **Refresh**.
5. The in-app view is **read-only** by design. To rename, move, create folders, download or delete files, choose **Open full file manager in browser**.

**Without MihonNooir:** Folio Nooir already has a full web file manager at `http://READER-IP/files` when File Transfer is enabled. You can use that directly from your phone browser. It does not need this experimental APK.

**Delete** removes actual SD-card contents. Double-check filenames and stop transfers before changing existing files. Nooir uses local HTTP without a password, so use a trusted network and close File Transfer afterward.

## Track conversions and transfers in Activity Queue

Open **MihonNooir → More → Nooir Tools → Activity Queue · All current jobs**.

- **Current** shows the running job and waiting jobs.
- **History** shows completed, failed or cancelled jobs.
- **All** shows both. Jobs are sequential: manga, phone files, PDF, webpages and web-novel batches share one queue.
- WorkManager records each job. A book that fails in the current app is marked **Failed** with an error reason **without causing later queued books to fail automatically**. A local multi-file batch can also continue after an individual-file error.
- **100% upload** reflects bytes sent. Treat an upload as complete only when **XTEink confirms** receiving the book.
- **Cancel entire Nooir queue** asks for confirmation and stops waiting jobs too. Individual WorkManager dependency cancellation is not offered because it can cascade.
- **Mihon's own chapter Download Queue is separate** and remains unchanged.

Background work may still be interrupted by power/battery restrictions, revoked permissions, network loss, Android force-stop or full SD storage. Don't mistake a successful queued conversion for a physically verified reader upload.

## Experimental per-book queue details and preflight (draft PR #22)

In the current app, **Activity Queue** shows a separate expandable card per job, with the manga title, format, device profile, chapter count, a preview of selected chapter names, progress, destination and error details. Use **Expand all** or **Collapse all**. Older jobs may lack the new chapter-preview fields.

Before writing converted book bytes, MihonNooir now checks the requested cover and selected chapters:

- **Missing cover:** first use a valid custom/cached image, then try the saved thumbnail URL. If that fails, automatically request refreshed manga details from the source (without refreshing chapters or changing the library entry), and retry the new thumbnail URL. If no usable cover is available, the job finishes in **Needs your decision** state. Choose **Continue without cover** to enqueue that one manga again without a separate cover, or **Cancel this book**. It never silently substitutes the first chapter as a cover.
- **Unavailable chapters:** verify downloaded chapter files or, in Quick Export, the chapter page list and first remote image. If some chapters are locked, missing or unavailable, choose **Skip unavailable** to explicitly omit them, or cancel that manga. If every chapter is unavailable, no book is written.
- **Transient source errors:** choose **Retry check**; temporary HTTP/network failures are not silently treated as locked or skipped.
- The shared queue continues with other books while a previous job awaits a decision. Once you confirm, the affected book is requeued at the end.
- Completed job details show skipped chapters and whether a missing cover was explicitly accepted.

These checks do **not** guarantee that every later page is accessible: an image may fail after the first-page probe. In that case the conversion fails without publishing a partial completed book. The Android Save As picker may already have created an empty destination document before the background worker starts; preflight prevents writing a partial *book*, not that picker placeholder.

## What if the filename already exists?

In the current release, **manga exports, phone-file transfers, PDF EPUBs, web articles and web-novel EPUBs** allow an output filename to be chosen before processing.

1. Set the destination folder (for example `/Books`) and output name.
2. The exporter checks the reader's existing filenames before expensive conversion. If a matching name exists, **only that book** receives an error rather than silently overwriting it.
3. Edit the name in the relevant tool and enqueue it again, or pick another folder.
4. The app checks again at upload time in case something else was added while the book was queued.

For phone-folder batch saves, the app can inspect names in the chosen Android document tree. Android's normal Save As picker remains in control of explicit target names. The preflight-and-recheck design **does not prove atomic race protection** when another user uploads a same-named file simultaneously: the firmware should also reject overwrites.

## Missing XTC / XTCH covers

Folio Nooir generates XTC/XTCH cover thumbnails from **the first page of the book** and caches them on its SD card.

1. Confirm that the manga's actual cover is visible in Mihon. If you request a separate cover, make sure the source artwork is available or explicitly allow a missing cover. Disable the cover option to begin with chapter pages.
2. Export to a **new filename**, open the book, and check whether its **first displayed page** is the cover.
3. If page one is correct but a reader's **library shelf** lacks a thumbnail, refresh its thumbnail cache (on Folio Nooir: **Retrieve All Book Details**). MihonNooir-generated XTCH covers have been reported working on Folio Nooir.
4. If the first book page is incorrect, please report the source, format (XTC or XTCH), phone build and Folio Nooir firmware version. Refreshing thumbnails won't fix a wrongly exported first page.

A MihonNooir XTCH shelf cover is now reported visible on Folio Nooir. Continue testing different firmware versions and actual reading/navigation; automated XTC/XTCH tests only check the byte format and first-page/chapter indexing.

[More XTC metadata detail](XTC_METADATA.md)

## Troubleshooting

| Issue | Check |
|---|---|
| Can't open reader's `/files` in Chrome | File Transfer enabled, correct on-screen IP, same LAN/hotspot, VPN/router isolation |
| Phone app can upload but folder list fails | Enter IP without `http://`; folder must exist; very large directories may exceed list budget |
| Queue History says **Failed** with no reason | Older preview jobs may not have stored error output; retry with the current release to get better diagnostics |
| A batch has one bad book | Check that item's error; subsequent items should continue in the current release |
| Existing name prevents export | Edit filename before requeuing; don't force an overwrite |
| Reader shows no cover on shelf | First check page one; then use **Retrieve All Book Details** if first page is correct |

For experiments not yet verified on hardware and remaining tasks, see [merged integration PR #21](https://github.com/toshio2011/mihon-nooir/pull/21) and the [project roadmap](https://github.com/toshio2011/mihon-nooir/issues/8).
