# E-reader device profiles — experimental v1.0.12

MangaNooir now separates **export output profiles** from **network-transfer capabilities**. Presets choose the image canvas size and `Auto` format; they **do not** make all formats compatible with all reader firmware.

## Choose a reader

In a manga's **Export** dialog, or in **More → Nooir Tools → Send & Convert files**, open the **Target e-reader** chooser.

| Reader preset | Canvas (portrait) | Auto format | Direct XTEink Wi-Fi |
|---|---:|---|---|
| XTEink X3 | 528×792 | XTCH (4 grays) | Only on compatible firmware |
| XTEink X4 | 480×800 | XTCH | Only on compatible firmware |
| XTEink X4 Pro | 480×800 | XTCH | Only on compatible firmware; stock firmware not assumed |
| Kindle Paperwhite 12 | 1264×1680 | EPUB | No — send EPUB via Send to Kindle |
| Kindle Paperwhite 11 | 1236×1648 | EPUB | No |
| Kindle 11 | 1072×1448 | EPUB | No |
| Kobo Clara BW | 1072×1448 | CBZ | No |
| Kobo Libra 2 / Colour | 1264×1680 | CBZ | No |
| Boox Palma | 824×1648 | CBZ | No |
| Boox Page | 1264×1680 | CBZ | No |
| PocketBook Verse Pro | 1072×1448 | CBZ | No |
| Custom | Your entered dimensions | EPUB | No |

For custom profiles, enter width and height in pixels. Each edge must be **64–4096 px**, with **3,000,000 pixels maximum** total. Unknown device IDs are rejected rather than silently using the X4 size.

### Delivery is different from conversion

- **XTEink:** XTC/XTCH output is intended for firmware that supports the format. The built-in Wi-Fi transfer uses the Folio Nooir/CrossPoint-style local protocol and **requires a compatible server**, not just XTEink hardware.
- **Kindle:** Save an EPUB file to your phone and send via **Send to Kindle**. Do not assume copying an EPUB over USB works or that every fixed-layout EPUB converts perfectly.
- **Kobo, Boox, PocketBook:** Save EPUB or CBZ and transfer using the reader's supported file-management method. No direct XTEink Wi-Fi upload is offered for these presets.
- **Custom:** EPUB/CBZ are the conservative defaults; XTC/XTCH remain available only when manually selected, **without any promise** that your device opens them.

The user can still send **original unchanged files** using Android's Share sheet. This does not convert a file or verify the receiving reader can open it.

## Compatibility notes

The X3 and X4 legacy IDs and resolutions are preserved so existing queued jobs still select the same canvas and XTCH output. X4 Pro uses X4 dimensions; it is **not** a guarantee that its stock firmware supports XTC/XTCH or a local Wi-Fi server.

XTCH custom dimensions require a **height divisible by 8 pixels** (for example 800 or 792, not 801). This is a safety check: Folio/CrossPoint bitmap-reading conventions would otherwise disagree with the XTCH column-major plane sizes. Choose XTC, EPUB or CBZ for an irregular custom height. Built-in X3/X4 sizes are unaffected.

The larger non-XTEink EPUB/CBZ pages consume more Android memory during conversion. Try a short volume first. Device dimensions and basic format guidance were crosschecked with [MangaPoint's public device profiles](https://github.com/W1seGit/MangaPoint/blob/main/crates/device-profiles/src/lib.rs), but this UI is independently implemented and real-device validation is still required.

**Status:** experimental branch only, not part of the published v1.0.11 APK.
