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

## Export QA follow-up

Run 45791132971 compiled the new converter integration on all three operating systems. Its real-window capture failed after the chapter selection toolbar moved the Read action to y=384 (artifact 15102619004 shows the changed screen). Existing Windows and Android workflows passed for that commit. This follow-up updates the capture coordinates, adds selection and dialog captures for visual inspection, and invokes the exact production export entry point in a separate headless test using the fixture's synthetic downloaded CBZ. It checks actual XTC and XTCH page decoding, dimensions, grayscale levels and existing-file refusal. This is not a GUI-driven conversion acceptance test; the dialog still needs actual user click-through verification. New CI results pending.

## Verified export evidence and screenshot quality gate

Run 45828862311 passed all four jobs. Artifact 15120784710 contains actual XTC (144522 bytes) and XTCH (288522 bytes), each three decoded 480x800 frames, with successful existing-output refusal. The source fixture has three pages; this does NOT establish panel segmentation or Android UI parity. Manual review found the screenshots named chapter-selection/export-dialog showed zero selected chapters and no modal, because the click missed the checkbox. They are not approved for the user guide. This revision corrects the reviewed coordinates (145,384) and requires an opt-in metadata-free diagnostic from the actual Compose dialog confirming exactly one selected chapter before capturing it. New verification pending.

Guide publishing checklist: verify actual screenshot contents rather than filenames; capture each verified workflow with synthetic data, record commit/run/artifact provenance, distinguish actual GUI actions from headless converter tests, and replace screenshots after layout changes. Preserve Android and current Swing guides until their replacements are independently verified.

## Export modal verification and preview follow-up

Run 45829565886 passed all four jobs. Artifact 15122675845 contains visually reviewed chapter-selection and export-modal captures, plus three decoded 480×800 frames each in XTC (144522 bytes) and XTCH (288522 bytes). The GUI captured a real one-chapter selection, but conversion is still independently driven through the headless production entry point. This revision widens the export settings dialog, exposes the shared rendering algorithms, and adds an actual finished-file XTC/XTCH page viewer using XtcBookPreview and GrayscalePng. The viewer opens only after successful export and does not modify the book. Full GUI export/preview acceptance, Android settings parity, and revised guide screenshots remain required. No stable Android or Swing changes.

## Full GUI export and preview acceptance follow-up

Run 45849325844 passed all four Compose jobs, including the wide modal capture and independent XTC/XTCH exports. Artifact 15132467314 contains reviewed wider dialog and actual decoded 480x800 pages. Android run 45849325945 passed. Existing desktop run 45849326244 failed during Maven dependency plugin resolution (HTTP 429); downstream jobs were skipped, so it is not a green full regression. This follow-up keeps the editable absolute output path, file picker, status and preview controls outside the scrolling settings. GUI acceptance enters a new output path, clicks Export, waits for the real output and completion, opens the finished-file preview and requires a decoded frame diagnostic before capturing it. Independent QA decodes the GUI-produced XTC as well as both previous formats. No automatic overwrite or persistent mutation is allowed. New footer geometry and GUI clicks must be verified; screenshots alone are not evidence. No Android or production Swing changes.

## GUI export acceptance correction

Run 45855997765 compiled the fixed-footer revision on all three platforms. Capture artifact 15136367119 confirms the chapter selection and modal, including the real fixed output footer. It did not reach output readiness or export: the earlier test clicked y=566, outside the text field (reviewed field bounds x=303..725, y=499..557 on the 1440x900 fixture display). Corrected the click to (480,528), Export to (785,658), Preview to (402,658), and added a failure screenshot and log tail. No production conversion or Android changes. Re-run required before claiming GUI-driven export/preview acceptance.

## GUI export verified; native backup slice

Run 45858612378 passed the actual Linux GUI export/preview test, macOS and Linux compilation; Windows failed during Gradle Maven dependency resolution with HTTP 403, not a Kotlin compiler failure. Artifact 15138225155 contains GUI-produced XTC, independent XTC/XTCH outputs, and successful export/finished-file preview screenshots. New revision exposes More → Backup & restore using the original bounded inspector, localhost backup transport, and approved batch extension recovery. Users select a backup, review library records and missing sources, create portable backups, explicitly confirm a recovery snapshot before restoring, and optionally select exact compatible packages from trusted already configured repositories. No automatic installation or deletion. Repository setup, legacy index selection, full extension management, and managed backend integration remain pending; this is an unverified new GUI slice. Retain existing guides until screenshots and runtime results are verified.

## Backup recovery acceptance follow-up

Initial native backup revision 7b1e06f compiled on Linux and macOS (run 45861215175; remaining jobs pending at review). Added separate Compose JVM QA with an original synthetic backup containing three source IDs: two exact compatible packages, one unresolved source. Mock restore requires the recovery snapshot on disk before mutation; one extension installation is deliberately rejected and the next succeeds. Assertions cover exact install order, unresolved sources, byte-for-byte preservation of input and recovery backup, and overwrite refusal. No real package or repo is contacted. UI now shows per-package outcomes, not only totals. Fresh CI required. GUI restore and extension screenshots still pending; this test verifies shared backend behavior, not production restore persistence or GUI click-through.

## Native extension catalogue

Run 45861661179 passed all Compose jobs, including the independent synthetic backup partial-recovery acceptance. Android 45861661487 and existing desktop 45861661177 passed. This revision adds Browse → Extensions, searchable installed and available lists, configured repository identity review, and explicit installation of a current exact version from a configured HTTPS repository. The transport rejects incomplete identity, stale catalogues and unconfirmed results; there is no automatic retry, removal, repository addition or refresh. New code requires compilation and a synthetic installation test. Existing backup recovery and Android paths unchanged. No real extension installed by development.

## Managed backend startup

Run 45929695330 passed the preceding native extension catalogue revision (including reader, GUI export and synthetic backup regression). This change reuses DesktopDataHome, SuwayomiProcessManager and OfficialSuwayomiCache. Startup tries the existing local library API if port 4567 is occupied, otherwise starts a checksum-verified cached pinned JAR. First-time download requires explicit approval; cached lookup never downloads. Application close stops only an owned process. Existing data root and MIHONNOOIR_DATA_DIR override are preserved; no migration or automatic extensions. New compile, first-start, existing-service, and shutdown tests are required. A corrupt cache is not executed; repair remains manual. Existing backend authentication, TLS trust and repository setup limitations remain. No release or Android source changes.

## Startup fixture and extension installation acceptance

Run 45930232835 compiled managed startup on Windows, macOS and Linux. The GUI fixture failed because its library endpoint returned all six records to the new first:1 readiness request; the production client correctly rejected the oversized result. This revision makes the fixture honour first/offset, and requires independent readiness and actual library requests before capturing the UI. The separate desktop run 45930232871 passed JVM, Windows packaging, real Suwayomi and four other native jobs; Linux x64 failed while updating the runner's Microsoft APT repository (HTTP 403). Added an isolated real-transport catalogue installation smoke test: no mutation on missing version, changed key or changed version; exactly one approved mutation; confirmed installed source; repeated approval refused. No live extension executed. New runtime results and actual setup/extension screenshots remain pending. Repository addition is not implemented until its pinned API contract can be verified.

## Native repository addition and explicit refresh

Run 45932326899 passed the managed-existing-service readiness GUI test, real export/preview, synthetic recovery and exact-version installation transport test. The Android and previous desktop workflows were still running when reviewed. This revision implements repository addition and explicit catalogue refresh in Browse → Extensions → Repositories, based on upstream v2.4.2366 ExtensionStoreMutation.addExtensionStore and ExtensionMutation.fetchExtensions. The add flow requires an explicitly approved public-style HTTPS index URL, checks exact duplicates and reviews the returned canonical signing identity. Upstream may follow redirects, indexV2 or canonicalize legacy indices and upsert rows; this is not an SSRF sandbox or an atomic no-overwrite guarantee. If previously configured metadata changes the client reports uncertainty instead of rolling back or retrying. Refresh contacts configured repositories only after confirmation. No automatic installation; Windows TLS remediation and real repository compatibility acceptance remain pending. No real repository contacted by development. New compilation, synthetic repository test and guide screenshots required.

## Local Universal Converter first slice

Run 45933466027 passed Compose compilation and the earlier regression suite after native repository setup. This revision exposes More → Universal Converter using the existing desktop adapters for CBZ/ZIP/image folders, original-page PDF, PDF text reflow/companion EPUB, prose EPUB to XTC/XTCH, and image EPUB. Input mode and device constrain allowed formats; output must be a new absolute path outside any source folder. The existing actual XTC/XTCH decoder is reused after conversion. PDFBox 3.0.8 and TwelveMonkeys WebP 3.15.3 match existing desktop pinned version lines; Compose Gradle dependency resolution still needs verification. No Android modifications. This is a single-file local slice; web import, persistent queue, full settings parity, server-independent entry, conversion lifecycle and guide captures remain pending. New code has not been compiled or exercised yet.
