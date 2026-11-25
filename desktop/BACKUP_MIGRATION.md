# Android ↔ Desktop backup migration — development stage 1

Read-only Backup & restore → Inspect backup previews a gzip protobuf backup locally, including library count, chapter read/bookmark/page positions, history, sources and extension repository records. No private backup is checked in. The user may open the local Suwayomi WebUI for its existing import/export tools; native restore/export is not yet implemented.

Next acceptance: pinned Suwayomi restore/export using synthetic source and signed IDs in a disposable server root; Android import of desktop export with category order/ID mapping verified; missing source matching; explicit trusted repository review including signing identity before registration and compatible extension installation. Never fetch a repository solely because a backup names it. No downloaded pages, source credentials or Nooir preferences are assumed to migrate. Android production unchanged.

## Stage 2 — native transport (pending CI)

Create library backup and Restore library now use the pinned localhost GraphQL API. Import validates before mutation and requires a separate recovery backup. Explicit flags exclude server settings and client metadata. Restore waits for explicit server status; unknown/failure can mean partial changes. No automatic retries, repository installs, or claims of complete Android compatibility. Existing WebUI remains available. Real-server round-trip, category mapping, empty-library export and extension store migration still need acceptance.
