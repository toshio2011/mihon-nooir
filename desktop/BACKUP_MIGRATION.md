# Android ↔ Desktop backup migration — development stage 1

Read-only Backup & restore → Inspect backup previews a gzip protobuf backup locally, including library count, chapter read/bookmark/page positions, history, sources and extension repository records. No private backup is checked in. The user may open the local Suwayomi WebUI for its existing import/export tools; native restore/export is not yet implemented.

Next acceptance: pinned Suwayomi restore/export using synthetic source and signed IDs in a disposable server root; Android import of desktop export with category order/ID mapping verified; missing source matching; explicit trusted repository review including signing identity before registration and compatible extension installation. Never fetch a repository solely because a backup names it. No downloaded pages, source credentials or Nooir preferences are assumed to migrate. Android production unchanged.

## Stage 2 — native transport (pending CI)

Create library backup and Restore library now use the pinned localhost GraphQL API. Import validates before mutation and requires a separate recovery backup. Explicit flags exclude server settings and client metadata. Restore waits for explicit server status; unknown/failure can mean partial changes. No automatic retries, repository installs, or claims of complete Android compatibility. Existing WebUI remains available. Real-server round-trip, category mapping, empty-library export and extension store migration still need acceptance.

## Stage 3 — portable extension metadata and source review (pending CI)

Explicit portable backup includes configured extension stores using Mihon's field 106 with index URL, name, badge, signing identity, contacts, legacy flag and extension list URL. This is not an APK backup. Native restore still uses library-only flags; review missing extensions compares exact signed source IDs with cached catalog and configured store signing identity. No automatic refresh, repository registration or extension installation yet. Missing catalog source IDs are unresolved, not inferred from names. A matched stored signing key is not independent proof of trust. Actual Android import and real backend catalog acceptance remain release gates.

## Stage 4 — real-server acceptance and explicit progress checkpoint

Native online reader offers an explicit Save progress to server action after a page has decoded. It keeps local resume independent, checks current server position and chapter identity, skips known conflicts and does not intentionally rewind, mark chapters read or change bookmarks. The API has no atomic compare-and-set; avoid concurrent writers. A configured server may forward progress to its sync service. The real pinned-server smoke now exercises portable export, validation, recovery and restore in a synthetic disposable root. This is same-server restore acceptance, not a fresh-device or Android round trip. All new behavior pending CI.
