# Android-inspired desktop UI experiment

Isolated feasibility work based on the accepted Swing candidate c2385938f9877869a1ff6afb465dbc73c3e7fd52. No change to Android, stable packages, existing Swing UI, conversion engine, or release workflows.

This first vertical slice uses Material 3, a responsive library cover grid, chapter details, and the installed source list. It compiles the existing SuwayomiClient and MiniJson directly, not a copied backend or mock library. It expects an already-running local server on port 4567. No credentials, automatic server startup, repository changes, downloads, reading-position writes, backup restore, or conversions are initiated by this slice. Cover decoding is bounded and off the UI thread; library pagination is explicit. Missing covers use title placeholders. It does not yet provide full Mihon UI parity.

Build using Java 21 and Gradle 8.12.1: gradle -p desktop/compose-preview compileKotlin. Run: gradle -p desktop/compose-preview run. These are unverified until isolated CI succeeds. Keep Swing as the acceptance baseline and do not distribute the experiment as a replacement.

Next gates: actual Android-style design review, real screenshots, native reading and export adapter, categories/updates/history, secure Windows certificate support, guided multi-extension recovery, performance on large libraries, equivalent full regression/packaging/physical acceptance. No changes to existing Android code, no duplicated conversion engine, no automatic TLS bypass. Existing Windows field test confirms library restore, manual extensions and XTC export with certificate workaround; seamless UI and recovery are release gates.

## Reader vertical slice

Adds native in-window chapter reading via the existing pinned-server chapter manifest/page APIs. Previous/Next, retry, existing server start position, and bounded single-page decode run through real APIs. No automatic reading-position writes or background downloads. Java ImageIO-supported image formats only in this experiment; retain full existing Swing reader until parity, including WebP, keyboard, reading progress, bookmarks, layouts and next-chapter behavior. New compilation and GUI acceptance required. The original three-OS compile passed for the preceding shell, run 45780842899; this is a new revision.

## Source browsing vertical slice

Adds actual installed-source pagination and native Popular/Latest/Search pages, covers, manga details and explicit chapter refresh. Latest is offered only when supported by the source. Browse requests use the existing pinned Suwayomi client; no new extension install or automatic download. Existing library remains read-only in this revision. Fresh compile and runtime verification required.

## Documentation release gate

Do not replace the current Swing guide until a verified Compose package is available. Capture actual desktop UI and actual decoded output, not concept images. Update onboarding (managed backend and certificate handling), Library/category navigation, Browse/Extensions, partial backup recovery, reader/resume, chapter selection/export, format/settings previews, Universal Converter (EPUB/PDF/web/batches), queue, transfer, settings, installation/update and troubleshooting. Link task-based guides from the project README and distinguish supported/unsupported operations and platform limitations. Keep Android guide aligned with its own verified release; do not claim new Compose functions in Android. Screenshot artifacts must identify exact commit and test run; synthetic fixtures are labeled. Preserve original files and user data.

## First real-window evidence

Compose workflow run 45783494996 passed 4/4 (three OS compilation jobs and actual Linux Xvfb window capture). Artifact 15099562276 contains the 1040x720 library screenshot, original synthetic cover images rendered by the running app, application and loopback fixture logs. The fixture confirmed library and cover requests. No private library or live extensions used. This confirms the library screen renders, not production library migration or GUI parity. Follow-up expands the fixture to verify first-cover navigation and actual manga detail/chapter query. Reader page captures still pending; verify screenshots visually before updating the final user guide.

## Manga details evidence

Run 45784959867 passed all four Compose jobs. Artifact 15100181909 includes the reviewed 1040x720 library and manga details screenshots and fixture logs confirming the first cover click, chapter metadata and thumbnail response. Follow-up adds a real chapter-open action using the existing fetchChapterPages and bounded page image path with original synthetic manga art. No persistent mutations allowed by the fixture. Reader capture and page-turn coordinates must be verified before claiming full reader runtime acceptance.

## Reader first-page evidence

Run 45787530533 passed 4/4; artifact 15101126595 contains the reviewed real 1040×720 reader screenshot, confirming the first chapter opened and its first original synthetic comic page rendered. Fixture logs contain READER_OK 101 and PAGE_OK 0. No rejected persistent mutations. Next/Previous and Back click-through tests added using reviewed window coordinates. These new tests are not yet verified. Full reader layout, progress persistence, WebP and Android-style interaction parity remain release gates.

## Native chapter export experiment

The preceding Compose run 45789609636 passed all four jobs, including first-page, next-page and previous-page fixture requests and screenshots. This revision adds explicit chapter selection and a first native export dialog using the existing SuwayomiToNooir and NooirDesktopCli source plus the exact Android shared bridge and options model. No duplicated conversion algorithm. Export requires a new output filename; missing chapter downloads are opt-in. Device/format, panel mode, orientation, cover and overview are available. This is not the full Android settings screen, persistent queue, actual decoded export preview, bundled WebP support or production package. New compile and end-to-end export tests are mandatory. Existing stable Android and Swing code remain unchanged.
