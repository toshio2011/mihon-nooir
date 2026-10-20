# Web novels and batch EPUBs — experimental 1.0.10

MangaNooir's first **web novel importer** is an experimental extension of [Web to E-reader](WEB_TO_READER.md). The goal is to read longer publicly available web stories as **real, reflowable EPUB books**, keeping individual chapters, a table of contents, an available cover, and inline illustrations.

**This is not a stable feature**. It needs a green build, real website tests, and reader testing. The working main release is unchanged.

## How to download several novels

1. In MangaNooir, go to **More → Nooir Tools → Web novels · Batch EPUB**.
2. Paste **one public HTTPS story URL per line**, up to **10 stories**. Each story becomes its **own EPUB**.
3. Leave **Download all chapters** checked (default) to export every chapter discoverable in public HTML. Or uncheck it to supply **First / Last chapter** when you only want a range. There is no artificial 80-chapter cap. Large novels can take a long time and may exceed the 180 MiB XTEink upload limit; use Save to phone when needed.
4. Before starting, optionally type an output name for each story; the importer checks for collisions in the selected destination folder *before* fetching its chapters. If the name exists, that story is skipped with an error, and subsequent stories continue. Tap **Download EPUBs to phone folder** and choose an Android storage folder, or connect the XTEink to Folio Nooir File Transfer and tap **Convert novels & send to XTEink**.
5. The phone creates each EPUB **sequentially**. While uploading, MangaNooir shares one queue with manga, file, PDF, and article conversions—no simultaneous XTEink transfers.
6. Review the job's output and any failed links before assuming all books were exported. An invalid/locked chapter is an error, not a silently missing chapter.

## New: combine individual webpages into ONE EPUB (experimental v1.0.11)

Inspired by [Genebook's multiple-webpage converter](https://genebook.de/en/converter/multiple-web-to-ebook/). Our implementation is independent and **runs offline on Android after fetching the public webpages directly**; it does not upload your book to Genebook or any external conversion provider.

1. Open **MangaNooir → More → Nooir Tools → Web novels / batch EPUB**.
2. Check **Combine pasted webpages into ONE EPUB**.
3. Paste each publicly readable **chapter URL on its own line**, in the order you want them in the book. You can reorder chapters by reordering the lines before processing.
4. Optionally set a combined **book title**, **author**, **EPUB filename**, and individual **chapter titles**. Otherwise webpage titles become TOC entries and the first page supplies book metadata.
5. Choose **Save combined EPUB to phone** or **Combine & send one EPUB to XTEink**. The app checks for a duplicate filename in the chosen destination **before downloading the rest of the chapters**, and once more before upload/save.
6. All pages become **one real reflowable EPUB** with a navigable table of contents and embedded public images. If a chapter cannot be read, conversion fails with an explanation; the app **does not silently save a partial book**. Other queued jobs continue.

The combined mode does **not automatically expand each submitted URL into an entire book**: a URL is treated as one chapter/page. For an automatically discoverable story with hundreds of chapters, leave combined mode off and use **Download all chapters** instead. Importing a Wattpad story landing page does not bypass its dynamically loaded or login-only chapters.

Combined mode has **no fixed number of pasted chapters**; the public URL list is stored privately in an app-local manifest (maximum 128 KiB of list metadata, individual URLs up to 600 characters). It avoids WorkManager's 10 KiB job-input restriction. Very large EPUBs still depend on network availability, Android background-work limits, storage, and the XTEink safe upload limit of 180 MiB. Separate-story mode keeps its 10-story-per-batch limit.

**Privacy:** URL manifests stay in the app's private storage and are removed when the associated worker finishes or fails. No EPUB content is committed to GitHub and no public conversion website receives your imported pages.

## Combined mode acceptance checks

- [ ] Three public article/chapter URLs produce exactly one EPUB; TOC matches input order.
- [ ] Optional chapter titles override the source headings correctly.
- [ ] Output title/author and selected filename are applied.
- [ ] Broken/locked second chapter aborts the EPUB without saving an incomplete result.
- [ ] Destination duplicate fails before remaining chapter requests; rename works.
- [ ] 20+ public URLs work from a private manifest (without WorkManager input-data overflow).
- [ ] A combined-book failure does not poison the shared export queue.
- [ ] Android FOSS APK CI and real phone/X4 tests pass.

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
- No artificial chapter-count maximum: **All chapters** uses the complete list discoverable from public HTML. Missing/locked/dynamic content cannot be magically fetched, and every chapter must still be readable. Large EPUBs are constrained by the device/storage/runtime and XTEink upload safety limits.
- 1.3-second pause between chapter requests; no subscription bypass or credential harvesting.
- Existing Android extraction caps for HTML and image sizes.
- No recursive device scans, no additional XTEink firmware APIs, and one-at-a-time Wi-Fi transfers.
- Failures are shown individually; completed books remain saved. Local file conversion and XTEink upload may be interrupted by Wi-Fi or Android battery restrictions.

## Validation before merge

- [ ] Build Android FOSS APK and pass overlay/smoke tests.
- [ ] Test public story website with 5–10 chapter links and a real cover.
- [ ] Test multiple independent novels to phone storage, with one failure between successful books and a name conflict before chapter download.
- [ ] Test chapter range 2–5, All chapters on a 100+ chapter publicly readable novel and interrupted conversions; ensure no silent truncation.
- [ ] Test sites where HTML is dynamic/locked; show clear non-success rather than fabricated content.
- [ ] Confirm cover, real text, inline illustrations and TOC in Folio Nooir.
- [ ] Confirm device receives a *single* upload at a time when manga and novel jobs are queued.

## Troubleshooting two real public links (20 October 2026)

**Wattpad story link** (`https://www.wattpad.com/story/350193740`, including optional sharing query parameters): a story URL is a **landing page**, not necessarily an HTML page containing its chapters. The importer now recognizes numeric Wattpad chapter URLs *when the public returned HTML visibly lists them*. If the site only provides its chapter list through JavaScript/login, MangaNooir reports that limitation and does not generate a misleading empty EPUB. This feature is **not a Wattpad login, scraping-bypass or premium unlock client**.

**Rengkang article/page** (`https://syazwandaniial.com/rengkang`): a page can have a readable title while the selected HTML contains no actual novel paragraphs. The web article tool now checks additional WordPress/Elementor and classic chapter containers, then explicitly says when a page appears to be a landing/catalog page. It cannot turn a preview, product listing or script-only page into full novel prose. For single-page conversion, open the page that actually **displays readable text** and paste that public chapter URL.

**Error display:** Nooir Tools → Activity Queue distinguishes a failed task from a failed queue. Older WorkManager records may say only `Failed` if the previous build did not save an error message. The newer preview records the per-story failure reason without aborting later queued books.

These are **code/diagnostic changes**, not a claim that both URLs are accessible or working. Direct live HTML was not retrievable from the development environment; phone-side testing is required.
