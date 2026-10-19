# Web novels and batch EPUBs — experimental 1.0.10

MangaNooir's first **web novel importer** is an experimental extension of [Web to E-reader](WEB_TO_READER.md). The goal is to read longer publicly available web stories as **real, reflowable EPUB books**, keeping individual chapters, a table of contents, an available cover, and inline illustrations.

**This is not a stable feature**. It needs a green build, real website tests, and reader testing. The working main release is unchanged.

## How to download several novels

1. In MangaNooir, go to **More → Web novels & batch EPUB**.
2. Paste **one public HTTPS story URL per line**, up to **10 stories**. Each story becomes its **own EPUB**.
3. Optionally select **First / Last chapter** (default 1–80, max 80 chapters per EPUB). Split long novels into separate ranges.
4. Tap **Download EPUBs to phone folder** and choose an Android storage folder, or connect the XTEink to Folio Nooir File Transfer and tap **Convert novels & send to XTEink**.
5. The phone creates each EPUB **sequentially**. While uploading, MangaNooir shares one queue with manga, file, PDF, and article conversions—no simultaneous XTEink transfers.
6. Review the job's output and any failed links before assuming all books were exported. An invalid/locked chapter is an error, not a silently missing chapter.

## What works and what doesn't

The current implementation finds chapter links **already visible in a site's public HTML**, using common table-of-contents and chapter link markup. It then fetches each publicly accessible chapter, embeds supported images, and writes each chapter into an EPUB navigation section. Requests are spaced out; no high-concurrency crawling.

**Wattpad and similar services:** this is **not yet a full Wattpad-specific importer**. The Wattpad story landing page may load its TOC dynamically in JavaScript, so generic HTML chapter discovery may fail. If the app cannot detect real chapter content, it should report an error rather than exporting a useless book description. Directly readable public chapters may work. We will need source-specific, officially permitted integrations and testing for better reliability.

**No paywall, premium chapter, access-control, or DRM bypass.** Some sites sell paid chapters; MangaNooir only imports content made accessible to it without circumventing protections. If you've legitimately purchased a book, use an authorized file export or supplied EPUB and then send that EPUB through **More → Send & Convert files**.

## Inspiration and code credits

- [WebToEpub](https://github.com/dteviot/WebToEpub): inspiration for novel chapter listings, selecting ranges, visible download status, retries, and TOC/book metadata. Its source is **GPLv3**; we **do not copy its source code** into MangaNooir's MIT app.
- [WP Downloader / wpd.my](https://wpd.my/): inspiration for Wattpad/Inkitt/Royal Road story URLs, reading lists, EPUB/cover handling and flexible output naming. Their public website describes batch reading-list ZIP exports. We **do not use their hosted service or borrow unpublished/proprietary code** in MangaNooir.
- [MangaNooir roadmap](https://github.com/toshio2011/mihon-nooir/issues/8): future source connectors, automatic story-list discovery, saved chapter checklists, bulk import management, reading-list import, browser Share, retry/resume, and richer cover/synopsis handling.

## Current safety limits

- Up to 10 distinct public HTTPS story links per batch.
- Up to 80 discovered chapters per output EPUB; chapter range may be 1–300.
- 1.3-second pause between chapter requests; no subscription bypass or credential harvesting.
- Existing Android extraction caps for HTML and image sizes.
- No recursive device scans, no additional XTEink firmware APIs, and one-at-a-time Wi-Fi transfers.
- Failures are shown individually; completed books remain saved. Local file conversion and XTEink upload may be interrupted by Wi-Fi or Android battery restrictions.

## Validation before merge

- [ ] Build Android FOSS APK and pass overlay/smoke tests.
- [ ] Test public story website with 5–10 chapter links and a real cover.
- [ ] Test multiple independent novels to phone storage, with one failure between successful books.
- [ ] Test chapter range 2–5 and an over-limit selection, ensuring no silent truncation.
- [ ] Test sites where HTML is dynamic/locked; show clear non-success rather than fabricated content.
- [ ] Confirm cover, real text, inline illustrations and TOC in Folio Nooir.
- [ ] Confirm device receives a *single* upload at a time when manga and novel jobs are queued.
