# Android-inspired desktop UI experiment

Isolated feasibility work based on the accepted Swing candidate c2385938f9877869a1ff6afb465dbc73c3e7fd52. No change to Android, stable packages, existing Swing UI, conversion engine, or release workflows.

This first vertical slice uses Material 3, a responsive library cover grid, chapter details, and the installed source list. It compiles the existing SuwayomiClient and MiniJson directly, not a copied backend or mock library. It expects an already-running local server on port 4567. No credentials, automatic server startup, repository changes, downloads, reading-position writes, backup restore, or conversions are initiated by this slice. Cover decoding is bounded and off the UI thread; library pagination is explicit. Missing covers use title placeholders. It does not yet provide full Mihon UI parity.

Build using Java 21 and Gradle 8.12.1: gradle -p desktop/compose-preview compileKotlin. Run: gradle -p desktop/compose-preview run. These are unverified until isolated CI succeeds. Keep Swing as the acceptance baseline and do not distribute the experiment as a replacement.

Next gates: actual Android-style design review, real screenshots, native reading and export adapter, categories/updates/history, secure Windows certificate support, guided multi-extension recovery, performance on large libraries, equivalent full regression/packaging/physical acceptance. No changes to existing Android code, no duplicated conversion engine, no automatic TLS bypass. Existing Windows field test confirms library restore, manual extensions and XTC export with certificate workaround; seamless UI and recovery are release gates.
