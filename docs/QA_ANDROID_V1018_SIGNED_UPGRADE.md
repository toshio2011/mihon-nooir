# MihonNooir v1.0.18 — physical Android upgrade acceptance

**Test status: NOT performed on a physical phone.** This guide belongs to draft [PR #80](https://github.com/toshio2011/mihon-nooir/pull/80), not a published v1.0.18 release. **Do not uninstall the existing official v1.0.17 app.**

## Verified prerequisite — real Android 16 emulator

- [Signed build + Android 16 emulator run 42764500428](https://github.com/toshio2011/mihon-nooir/actions/runs/42764500428): **both jobs green** on draft commit `0c59b4173664642ed45313684a90e329016039b3`.
- The signing job built all five v1.0.18 FOSS APK variants with the persistent release key, confirmed matching signing certificate against [official public v1.0.17](https://github.com/toshio2011/mihon-nooir/releases/tag/v1.0.17), verified `dev.nooir.mihontrial.foss`, version code `1000018`, version name `1.0.18`, and generated `SHA256SUMS.txt`.
- The Android 16 x86_64 emulator installed **official signed v1.0.17** (`adb install -r`), then installed **signed v1.0.18 on top** (another `adb install -r`), without uninstall or data clearing. Logs confirmed package version transitioned from `1.0.17`/`1000017` to `1.0.18`/`1000018` and package UID **10216 remained unchanged**. This validates Android's signing/package update compatibility on the emulator, **not** real saved-library migration.
- A time-limited, verified signed candidate is available as Actions artifact `mihonnooir-v1.0.18-signed-upgrade-candidate` from that successful run, artifact ID `13738649881`; GitHub reported expiry **2026-11-17 06:02 UTC**. It is a **test candidate**, not a public release or a guaranteed future download. Do not substitute the **Android Debug** APKs from ordinary PR build workflows.

## Owner-approved phone test — do not perform automatically

1. **Before any installation**, make a regular MihonNooir backup, record installed app version (`1.0.17`), back up any irreplaceable downloaded chapters separately, and confirm there is enough free storage. Keep the app installed and the library unchanged.
2. Obtain only the exact signed artifact from [Actions run 42764500428](https://github.com/toshio2011/mihon-nooir/actions/runs/42764500428), if still available, and check its `SHA256SUMS.txt` before extracting/using APKs. Do **not** use APKs from the public v1.0.17 release as though they were v1.0.18 or use any APK from the `Build Mihon Nooir debug APK` workflow.
3. On a verified **ARM64 Android** phone, choose **`app-arm64-v8a-foss.apk`**, not x86, x86_64 or an unverified file; the signed workflow verified package identity and signing certificate continuity. No public v1.0.18 GitHub release exists at this stage.
4. Explicitly approve Android's normal installer prompt for the selected file **over the current app**. If the device says **"App not installed as package conflicts with an existing package"**, STOP: do **not** uninstall v1.0.17, clear its storage, change signing keys or try a random APK. Report the exact APK filename and Android error instead.
5. After a successful install, verify **version `1.0.18`**, existing library, reading progress, downloads and extension listings. Check that opening a known previously downloaded chapter still works. Do not claim success based only on the application launching.
6. Test the **in-app updater's browser handoff** against a known official release entry; confirm it selects the appropriate ARM64 or universal APK and opens a normal HTTPS browser download. **Do not install an unexpected or unverified APK.** Because the old v1.0.17 updater is already buggy, this test concerns the newly installed v1.0.18 behavior, not the first-update experience.
7. Record any crash, wrong-ABI pick, missing chapters, storage/permission issue or misleading button wording. The user must explicitly approve any later public merge/release; no hidden installs or automatic release.

## Acceptance record (fill only after actual phone test)

```text
Phone / Android version:
Official installed before test: package / version / backup completed:
Candidate: Actions run / commit SHA / artifact name / APK filename:
SHA256SUMS check:
Install without uninstall: PASS / FAIL / NOT RUN
Android package conflict error: none / exact text
Version after upgrade:
Library and reading position preserved: PASS / FAIL / NOT RUN
Downloaded chapter still opens: PASS / FAIL / NOT RUN
Updater browser link selected correct architecture: PASS / FAIL / NOT RUN
Android installer UI and permissions: PASS / FAIL / NOT RUN
Observations / reproducible steps:
```

**Current outcome:** emulator PASS, **physical phone acceptance NOT RUN**. Preserve `main`, Windows draft #81, PDF draft #82, public v1.0.17 and all release signing material.
