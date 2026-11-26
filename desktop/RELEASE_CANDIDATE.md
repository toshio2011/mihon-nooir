# Desktop integration candidate — 26 November 2026

This isolated candidate combines backup and extension recovery through PR #143, verified live public web acceptance from PR #133, and the 19 actual desktop guide images from PR #139. Android production source, firmware, signing and release publication remain unchanged. This branch is not the previously verified November 25 candidate.

## Prior evidence (not exact combined-commit acceptance)

- Integrated original candidate: run 45572700921, all eight jobs passed.
- Live web acceptance: run 45573117372, public-domain EPUB and one public Wattpad chapter with cover and synopsis.
- Native real-server backup round trip: run 45650969283, all eight passed; chapter flags, page/history record counts, missing signed source ID, recovery copy and repeated import.
- Explicit desktop server reading-position save: run 45734689592, all eight passed; real pinned server backup includes updated position, known rewind refused.
- Approved extension recovery: run 45739874517, all eight passed; synthetic localhost installation contract only.
- Legacy repository index fallback: PR #143; acceptance not yet verified at creation.
- Documentation: 19 actual embedded desktop screenshots, prior candidate, verified paths. Android PR #140 independently contains 18 actual release-source captures. No final signed Android screenshot claim.

## Release gates

1. Exact combined commit must pass all native and live-web jobs and preserve the frozen Android source guard.
2. Verify fresh GUI captures, including Backup & Restore and progress save controls; remaining PDF, batch, transfer and installation screenshots.
3. Test actual third-party extension recovery on an isolated server and physical Android restore of a desktop-generated backup. Verify category membership/order, exact page values, history and duplicate handling. No automatic missing-repository registration; modern index recovery remains open.
4. Test physical readers and platforms, actual Linux package-manager upgrade, unsigned distribution and macOS signing/notarization. Windows ARM64 portable only.
5. Review final guides, artifact names/checksums, user-state retention and obtain release approval.

No artifacts published as stable desktop releases. Android v1.0.20 remains untouched.
