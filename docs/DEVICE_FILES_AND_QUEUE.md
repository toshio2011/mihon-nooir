# Browse XTEink files, Activity Queue and filename conflicts

[← All guides](https://github.com/toshio2011/mihon-nooir/blob/main/docs/README.md) · [Nooir Tools quick start](https://github.com/toshio2011/mihon-nooir/blob/main/docs/NOOIR_TOOLS_QUICK_START.md) · [Wi-Fi transfer](https://github.com/toshio2011/mihon-nooir/blob/main/docs/WIFI_TRANSFER.md)

> [!IMPORTANT]
> The built-in **Nooir Tools** screen and shared **Activity Queue** are features of the **experimental v1.0.11 APK**, not the v1.0.1 app on `main`. The [build passed Android CI](https://github.com/toshio2011/mihon-nooir/actions/runs/39363272479), but on-reader verification is pending.

## The built-in reader file browser

1. On the XTEink, start **File Transfer → Join Network** or **Create Hotspot**.
2. Connect your Android phone so it can reach the reader. Confirm the browser opens the IP shown on XTEink.
3. In MangaNooir choose **More → Nooir Tools → Browse XTEink files**.
4. Enter the device **IP/hostname only**, not `http://`. Tap **Connect & browse**, enter folders, use **Up folder**, or **Refresh**.
5. The in-app view is **read-only** by design. To rename, move, create folders, download or delete files, choose **Open full file manager in browser**.

**Without MangaNooir:** Folio Nooir already has a full web file manager at `http://READER-IP/files` when File Transfer is enabled. You can use that directly from your phone browser. It does not need this experimental APK.

**Delete** removes actual SD-card contents. Double-check filenames and stop transfers before changing existing files. Nooir uses local HTTP without a password, so use a trusted network and close File Transfer afterward.

## Track conversions and transfers in Activity Queue

Open **MangaNooir → More → Nooir Tools → Activity Queue · All current jobs**.

- **Current** shows the running job and waiting jobs.
- **History** shows completed, failed or cancelled jobs.
- **All** shows both. Jobs are sequential: manga, phone files, PDF, webpages and web-novel batches share one queue.
- WorkManager records each job. A book that fails in the new preview is marked **Failed** with an error reason **without causing later queued books to fail automatically**. A local multi-file batch can also continue after an individual-file error.
- **100% upload** reflects bytes sent. Treat an upload as complete only when **XTEink confirms** receiving the book.
- **Cancel entire Nooir queue** asks for confirmation and stops waiting jobs too. Individual WorkManager dependency cancellation is not offered because it can cascade.
- **Mihon's own chapter Download Queue is separate** and remains unchanged.

Background work may still be interrupted by power/battery restrictions, revoked permissions, network loss, Android force-stop or full SD storage. Don't mistake a successful queued conversion for a physically verified reader upload.

## What if the filename already exists?

For experimental v1.0.11, **manga exports, phone-file transfers, PDF EPUBs, web articles and web-novel EPUBs** allow an output filename to be chosen before processing.

1. Set the destination folder (for example `/Books`) and output name.
2. The exporter checks the reader's existing filenames before expensive conversion. If a matching name exists, **only that book** receives an error rather than silently overwriting it.
3. Edit the name in the relevant tool and enqueue it again, or pick another folder.
4. The app checks again at upload time in case something else was added while the book was queued.

For phone-folder batch saves, the app can inspect names in the chosen Android document tree. Android's normal Save As picker remains in control of explicit target names. The preflight-and-recheck design **does not prove atomic race protection** when another user uploads a same-named file simultaneously: the firmware should also reject overwrites.

## Missing XTC / XTCH covers

Folio Nooir generates XTC/XTCH cover thumbnails from **the first page of the book** and caches them on its SD card.

1. Confirm that the manga's actual cover is visible in Mihon. A new preview export should report an error if it can't obtain/decode the cover, instead of silently putting the first chapter on the cover.
2. Export to a **new filename**, open the book, and check whether its **first displayed page** is the cover.
3. If the first book page is correct but the **library shelf** has no thumbnail, run **Retrieve All Book Details** on Folio Nooir to create missing cover thumbnails.
4. If the first book page is incorrect, please report the source, format (XTC or XTCH), phone build and Folio Nooir firmware version. Refreshing thumbnails won't fix a wrongly exported first page.

This needs a physical X4/X3 test; the current automated XTC/XTCH tests only check the byte format and first-page/chapter indexing.

[More XTC metadata detail](XTC_METADATA.md)

## Troubleshooting

| Issue | Check |
|---|---|
| Can't open reader's `/files` in Chrome | File Transfer enabled, correct on-screen IP, same LAN/hotspot, VPN/router isolation |
| Phone app can upload but folder list fails | Enter IP without `http://`; folder must exist; very large directories may exceed list budget |
| Queue History says **Failed** with no reason | Older preview jobs may not have stored error output; retry with v1.0.11 to get better diagnostics |
| A batch has one bad book | Check that item's error; subsequent items should continue in the current preview |
| Existing name prevents export | Edit filename before requeuing; don't force an overwrite |
| Reader shows no cover on shelf | First check page one; then use **Retrieve All Book Details** if first page is correct |

For experiments not yet verified on hardware and remaining tasks, see [draft PR #18](https://github.com/toshio2011/mihon-nooir/pull/18) and the [project roadmap](https://github.com/toshio2011/mihon-nooir/issues/8).
