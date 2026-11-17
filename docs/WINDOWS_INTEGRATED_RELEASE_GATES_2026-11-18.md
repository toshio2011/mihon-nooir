# Windows integrated completion and release gates — 18 November 2026

Stacked Windows-only draft after #91. The durable queue accepts PDF and image-only EPUB alongside manga folders and CBZ/ZIP, using the same persisted settings/output reservations. PDF batches always preserve original pages; selectable PDF text companion remains available through direct document conversion. EPUBs with unsupported prose or protection fail explicitly and do not stop later queued work. Queue pause stops after the current job, not partway through conversion. Added a mixed document batch test against actual codecs. No Android, Folio Nooir or public release changes.

## Before publication
- [ ] Fresh exact-head integrated Windows build, native packaged EXE, display QA, installer build/install/launch/uninstall, and pinned Suwayomi handshake all pass.
- [ ] Windows update metadata tests pass, Windows tags isolated from Android; release assets checksum verified. No Android APK publication.
- [ ] Real PC installation, existing library, file chooser/keyboard and screen scaling; test actual installer upgrade when a prior MSI exists. 
- [ ] Real authorized source/extension/download journey and reader transfer to physical XTEINK; inspect file and open it on device.
- [ ] Authenticode production signing and redistribution/license review; no fake trusting an Android signer or self-signed Windows certificate.
- [ ] Back up desktop data before updates: fully exit app and managed Suwayomi server; preserve the entire configured MIHONNOOIR_DATA_DIR (default %USERPROFILE%/.mihonnooir) separately from installed app. Existing book paths outside that folder and Java Preferences reader positions need separate preservation. Full automated backup/restore and cross-machine metadata path migration not yet implemented.
- [ ] Explicit approval for public Windows publication after review. An unsigned developer artifact is not a completed production release.

Existing limitations remain: image EPUB only, PDF text companion is not layout-aware reflow/OCR, manual browser-mediated updater, no automatic conversion-to-device or mDNS, guarded LAN sender cannot make overwrite-racing firmware atomic. Neither physical PC/device acceptance nor production signing can be inferred from mock/hosted CI.
