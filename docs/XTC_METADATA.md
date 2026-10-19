# XTC/XTCH cover and synopsis (experimental)

This work is tracked in [MangaNooir PR #11](https://github.com/toshio2011/mihon-nooir/pull/11) and the companion [Folio Nooir firmware PR](https://github.com/toshio2011/folio-nooir/pull/1). Neither change has been merged into a stable release.

## Cover

For manga exported from Mihon, we look for the **custom cover** first, then a **cached library cover**, and then the source extension's artwork URL. If the artwork is readable, it becomes a separate first page of the XTC/XTCH output; the manga chapters follow with chapter navigation adjusted accordingly. Existing Folio Nooir firmware builds its XTC shelf cover from page 0, so this works without a new parser. If the artwork cannot be obtained, the first manga chapter page remains the fallback.

The same first-page cover improvement applies to EPUB and CBZ exports from Mihon.

## Synopsis and other metadata

XTC/XTCH's normal 256-byte metadata area does not have a native synopsis field. MangaNooir therefore stores an **optional extension after the final page**:

```text
[Unmodified XTC header, metadata, chapters, page table, images]
[UTF-8 JSON payload] [4-byte little-endian JSON byte length] [NOOIRMD1]
```

The JSON is bounded to 8192 bytes and carries available title, author, artist, synopsis, genres, publisher, language and identifier. Nothing is fabricated. No legacy image offset or page table is changed. Some third-party readers may reject trailing bytes; that remains to be tested.

### Folio Nooir support

- **Direct Wi-Fi upload:** after sending XTC/XTCH, the Android app can call Folio Nooir's **existing** `POST /api/book` endpoint to set title, author and synopsis as a stored bookshelf metadata override. That works independently of the new footer parser on firmware versions that support the API. If metadata sync fails, the transfer is still treated as successful but the UI reports a warning.
- **SD card / USB / manual upload:** the [companion firmware PR](https://github.com/toshio2011/folio-nooir/pull/1) reads the optional footer and provides the synopsis to Library and Recent. Until that firmware is installed, an older reader ignores the synopsis payload.
- **Cover:** Folio Nooir continues to use the first XTC page as its cover source.

## Testing

1. Export a manga with a known cached custom cover to XTC and XTCH.
2. Check page 0 contains the cover, subsequent pages are in their original reading order, and chapter navigation starts at the right page.
3. Transfer to an X4 running Folio Nooir and verify title, author, cover thumbnail and synopsis after refreshing book metadata if necessary.
4. Copy a file manually to an SD card and verify synopsis **only on the companion firmware branch**.
5. Confirm that ordinary legacy XTC files without the trailer still open.
6. Check a title with no cover or synopsis; export should not fail or fabricate these fields.

These changes have not yet been physically tested on the maintainer's X4 or independently verified on other firmware.
