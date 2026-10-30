# Send to an XTEink reader over Wi-Fi

[← Back to README](../README.md) · [All guides](README.md) · [Choose chapters](CHAPTER_SELECTION.md) · [First-time setup](GETTING_STARTED.md) · [Folio Nooir web-server docs](https://github.com/toshio2011/folio-nooir/blob/main/docs/webserver.md)

MihonNooir can send converted manga to XTEink readers with a **compatible HTTP/WebSocket File Transfer API**. The documented integration uses Folio Nooir's server; other firmware may have different endpoints. The experimental v1.0.11 preview can also send existing files from Android phone storage. This works over a **local network**; no Bluetooth pairing, USB cable, online account or internet connection is required for the transfer itself.

> [!WARNING]
> Folio Nooir's web server has **no authentication** and serves local HTTP. Use a trusted private network or the reader's short-lived hotspot. Turn off File Transfer when finished.

> **Version note:** v1.0.1 supports basic manga Wi-Fi sending; v1.0.11's **Nooir Tools**, file browser, shared queue and pre-conversion duplicate-name checks are experimental. [Getting started](GETTING_STARTED.md).

## Option A — Use your home Wi-Fi

1. On XTEink, navigate to **Home → File Transfer → Join Network**.
2. Select and connect to your **2.4 GHz Wi-Fi** network. The XTEink uses 2.4 GHz; your phone can use a router's 5 GHz band *if both bands are on the same local network and devices can reach one another*.
3. **Keep the XTEink on its File Transfer screen.** The server stops when you leave.
4. The reader displays a direct address such as `http://192.168.1.102/` and may show `http://crosspoint.local/`. Your actual IP will differ.
5. On the phone, open **Chrome** and enter the exact URL shown on the XTEink. You should see the reader's web interface and File Manager.

**If this browser test fails, fix the network before trying MihonNooir.** Common causes are wrong Wi-Fi, guest/client isolation, VPN, phone switching networks or an incorrect hostname. Try the displayed **IP address** instead of `crosspoint.local`.

## Option B — Use the XTEink hotspot

No router nearby? You can connect your phone directly to the reader.

1. On XTEink: **Home → File Transfer → Create Hotspot**.
2. On the phone, join the Wi-Fi named `CrossPoint-Reader` (an open hotspot; no password).
3. If Android says **Connected without internet**, choose to stay connected.
4. Use the address shown on the reader. It is often `http://192.168.4.1/` in the browser, but always prefer the actual on-screen IP.
5. Turn off the hotspot after transferring. It is open and not encrypted.

## Send from MihonNooir

### From a manga details screen (main 1.0.1 and experimental preview)

1. Open the manga details page. **Long-press** a chapter, then **tap** the others you want. The Export dialog itself cannot select chapters. [Chapter selection tutorial →](CHAPTER_SELECTION.md)
2. Tap **Export** next to Download.
3. Choose the X4/X3 target and XTC, XTCH, EPUB, CBZ or Auto.
4. Choose **Send to XTEink**.
5. In the **hostname / IP** field, enter only the address, e.g. `192.168.1.102`, **not** `http://192.168.1.102/`.
6. Set the device folder to an **existing** path such as `/Books`. If you're unsure whether that folder exists, choose `/` to send to the SD-card root.
7. Start with one **small EPUB or CBZ** and wait for confirmation.

### From your phone files (experimental v1.0.11 only)

Open **More → Nooir Tools → Send & Convert files**, select up to **25 files**, then choose:

- **Send originals** — copy the selected files unchanged.
- **Convert manga & send** — convert recognized CBZ/image manga; pass other documents through unchanged.
- **Share selected** — invoke the Android share sheet for other installed apps/services.

The v1.0.11 preview uses one **sequential** WorkManager queue for manga, phone files, PDF, web and novel transfers. You may navigate Back or switch apps; check progress under **More → Nooir Tools → Activity Queue** or in the Android foreground notification. **Conversion** and **uploading** are different stages:

| Status | Meaning |
|---|---|
| Queued / preparing | Work is awaiting execution or opening source files |
| Converting chapter N/M | Pages are being processed; byte percentage may not be known |
| Uploading — N% | Phone has sent that portion of the output to the reader |
| Waiting for XTEink confirmation | All bytes were sent, but final receiver response is pending |
| Confirmed / complete | Reader returned a successful response |
| Failed / cancelled | Do not assume a complete file exists on the XTEink |

**100% bytes sent is not a completed transfer** until XTEink responds. Network loss or SD failures can cause a late failure.

## File size and reader safety

Folio Nooir's HTTP receiver uses a **small SD-card write buffer** rather than holding the whole book in ESP32 memory. Large files are not automatically a RAM crash, but may expose Wi-Fi timeouts, insufficient SD space or interruptions.

The experimental **1.0.4** sender conservatively refuses individual files larger than **180 MiB**. That's an application safety threshold, not a proven firmware limit. Automatic split-by-volume or split-by-chapter output is planned but not finished.

For initial testing, send a **5–20 MB** file, confirm it appears in the reader's File Manager, then open it in the reader. Avoid retrying immediately after a failed upload without checking for a partial file. A same-named destination file may be handled differently by firmware versions or transfer paths, so check before replacing important content.

## Troubleshooting

| Problem | Try this |
|---|---|
| Chrome cannot open XTEink URL | Confirm File Transfer mode, same LAN/hotspot, correct IP, no guest isolation/VPN |
| `crosspoint.local` won't resolve | Enter the XTEink's displayed IP address |
| MihonNooir says cannot connect | Open the same address in Chrome; use **IP only**, no `http://` or slash in the app field |
| Transfer progress doesn't move | Stay close to router, check XTEink power/SD space, inspect status notification; don't turn off File Transfer |
| XTEink says file failed or SD full | Free SD space; check for an incomplete file; try a much smaller file |
| Transfer completes but book won't open | File transfer ≠ file format support. Check the firmware's EPUB/CBZ/XTC/XTCH support |
| It stops after switching apps | Check Android background/battery restrictions and the last WorkManager job result; force-stop cancels/interferes with background tasks |
| `/Books` isn't found | Use `/`, or create the folder first through the XTEink web File Manager |

The reader's browser File Manager can also **upload a file manually** if you're troubleshooting MihonNooir.

Related implementation details: [Folio Nooir web server](https://github.com/toshio2011/folio-nooir/blob/main/docs/webserver.md) · [HTTP API](https://github.com/toshio2011/folio-nooir/blob/main/docs/webserver-endpoints.md).

## Multiple exports and existing filenames — experimental v1.0.11

For the latest preview, open **More → Nooir Tools → Activity Queue** to see running/waiting/finished jobs. A failed book should not poison the next queued book. Set a different **output filename before conversion** if one already exists in the destination; the app checks names before processing and once more before upload. Choosing **Cancel entire Nooir queue** cancels waiting work too. If an XTC/XTCH cover is missing only on the *library shelf*, run **Retrieve All Book Details** in Folio Nooir after verifying the first book page is correct. [Full guide](DEVICE_FILES_AND_QUEUE.md).
