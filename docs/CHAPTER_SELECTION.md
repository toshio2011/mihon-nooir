# Choosing chapters for manga export

[← Documentation](README.md) · [Main README](../README.md) · [Getting started](GETTING_STARTED.md) · [Wi-Fi transfer](WIFI_TRANSFER.md)

**The most important step:** choose chapters **on the manga details screen before opening Export**. The Export dialog chooses the device, format and destination; it does **not** contain a separate chapter picker. In this context, “import chapters” usually means **export chapters out of MangaNooir** for your e-reader.

> **Version note:** This chapter-selection flow exists in the baseline app, but **Quick Export, Nooir Tools and its Activity Queue** require experimental v1.0.11; [download/installation guide](GETTING_STARTED.md).\n\n## Select exactly the chapters you want

1. Open **MangaNooir → Library** (or browse a source) and open the manga's **details page**, where its chapter list appears.
2. **Long-press** the first chapter you want. This enters Mihon's chapter-selection mode; the chapter becomes selected.
3. **Tap** additional chapters to include them. Tap a selected chapter again to deselect it.
4. For consecutive chapters, you can **long-press a later chapter** and Mihon's range-selection behavior may select the chapters in between. Check the highlighted chapters and selected count before exporting.
5. To choose everything in the current filtered chapter list, use **Select All** in the top toolbar. **Invert Selection** swaps selected and unselected chapters; the Back/cancel selection action clears the selection.
6. When the correct chapters are highlighted, tap the **Save/Export icon** in the top toolbar (the selected-chapters mode shows “Export selected chapters”; the normal toolbar shows “Export to XTEink”).
7. In **Export to e-reader**, choose the **X4 / X3 preset**, **Auto / XTC / XTCH / EPUB / CBZ**, and optionally **More conversion settings** in the experimental preview. Then choose **Save file** or **Send to XTEink**.

MangaNooir exports the chosen chapters into **one output book** with chapter navigation, rather than producing one file per selected chapter. The exporter orders chapters by chapter number; this may differ from the order currently shown on screen if you've changed Mihon's sorting.

## Important: Normal Export vs Quick Export

| Your choice | What MangaNooir exports |
|---|---|
| **Normal Export**, some chapters selected | **Only selected chapters that are already downloaded** in Mihon. Selected chapters that aren't downloaded are skipped. |
| **Normal Export**, **nothing selected** | **All downloaded chapters** of that manga. This can be a lot more than you intended. |
| **Quick Export**, some chapters selected (experimental v1.0.11) | **The selected chapters**, whether already downloaded or online. Missing pages are fetched temporarily through that manga source. |
| **Quick Export**, **nothing selected** | Cannot start: **select at least one chapter** in the chapter list first. |

**Quick Export isn't a separate “import chapters” screen.** Choose the chapter selection first, then turn on **Quick Export · fetch missing pages** in the Export dialog. That checkbox only works for an **online source**; it is disabled for a local/non-online source.

For non-downloaded online chapters, MangaNooir uses the installed source extension and its page/image requests. It processes one chapter at a time and cleans temporary fetched page files afterward, without marking the chapter permanently downloaded in Mihon. There is no fixed five-chapter limit, but a long series still needs time and output storage. Sending a single output to XTEink in the current experimental sender is capped at **180 MiB** for reliability; split your selection into smaller batches until automatic volume splitting is implemented.

## Examples

**Export only chapters 12–15 already on your phone:** Open the manga → long-press chapter 12 → select chapters 13, 14, 15 → Export → leave **Quick Export off** → choose XTCH → **Save file**. The selected chapters must already be downloaded.

**Export chapters 12–15 without downloading them permanently:** Open the manga → select chapters 12–15 → Export → turn **Quick Export on** → XTCH → **Save file** or **Send to XTEink**. Online fetching requires a working source extension and network access.

**Export every permanently downloaded chapter:** Open the manga → **clear all selections** → Export → **Quick Export off** → choose output format. Be careful: *every downloaded chapter* of that manga is included.

**Send selected chapters directly to your X4:** First select the chapters, then Export → format/device → **Send to XTEink**. Put Folio Nooir in **File Transfer** mode first and use its local IP address. See the [Wi-Fi walkthrough](WIFI_TRANSFER.md).

## Where to check progress

In experimental v1.0.11, the chapter export and Wi-Fi send use the shared background Nooir Activity Queue. You can return to Mihon or switch apps; open **More → Nooir Tools → Activity Queue** to see current and completed jobs, progress, and per-book failures. One ordinary failed book shouldn't block later jobs. **Cancel entire queue** cancels waiting jobs as well; individual dependency cancellation is not offered. The Android notification can show activity if allowed. **100% sent** refers only to bytes uploaded; wait for the reader to confirm success.

The **More → Nooir Tools → Send & Convert files** picker is *different* from chapter selection: it chooses existing documents, CBZ archives or images **from Android storage**, not online chapters from a manga details page.

## If you can't find the chapter selection

- **Tapping a chapter opens the reader instead of selecting:** First **long-press** a chapter to enter selection mode, then tap other chapters.
- **No Export icon:** Check that you're in **MangaNooir**, not the original Mihon, and look in the toolbar actions/overflow menu if your screen is narrow.
- **Quick Export is disabled:** The manga isn't from a compatible online source.
- **Quick Export's Save/Send is disabled:** Select at least one chapter **before opening Export**, then reopen the dialog.
- **Normal Export ignores some selected chapters:** They must be permanently downloaded, or use Quick Export in the experimental preview.
- **No downloaded chapters and no selection:** Normal Export cannot produce a book; download chapters first or explicitly select chapters for Quick Export.
- **I want one file per chapter instead of one combined book:** The current manga details export creates a single combined file. Automatic split-by-chapter/volume is tracked on the [roadmap](https://github.com/toshio2011/mihon-nooir/issues/8).

The chapter-selection behavior above is based on the **pinned Mihon UI and MangaNooir exporter code**. Exact icon placement can vary slightly by device size and build. [Report problems or request refinements](https://github.com/toshio2011/mihon-nooir/issues).
