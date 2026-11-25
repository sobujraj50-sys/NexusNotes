# Release guide — Nexus Notes

## Build
GitHub Actions (`.github/workflows/build.yml`) on every upload:
1. Unit tests (`testDebugUnitTest`). The build stops if any test fails.
2. `assembleRelease` with R8, signed with the project key from repository secrets `KEYSTORE_BASE64` and `KEYSTORE_PASSWORD`.
3. The APK is published under **Releases** as `v<version>-build<run>`.

`versionCode` = the Actions run number (always increasing), so updates install over the old app.

## Verify that an APK is really yours
The signing certificate's SHA-256 fingerprint is:
```
AB:31:26:72:05:36:D3:AC:87:35:3C:EF:A6:78:31:81:F0:7F:77:D2:09:73:D7:91:E5:49:30:B5:DD:14:10:9C
```
- On a computer: `apksigner verify --print-certs NexusNotes.apk` → "Signer #1 certificate SHA-256 digest" must match.
- On the phone: Android also refuses to install an update signed with a different key, so "App not installed" when updating means **stop**. Don't uninstall to force it, or your notes are lost.

## Keep the key safe
- `nexus.jks` + its password = the only way to ship updates that install over the current app.
- Keep an offline copy (USB drive / password manager). Never commit it to GitHub as a file.
- Losing it means future versions need a fresh install, and data must be moved with Backup & Restore.

## Turning a release candidate into 1.0.0
1. Build is green and the full **TEST_PLAN.md** passes on a real phone.
2. Change `versionName = "1.0.0-rc1"` to `"1.0.0"` in `app/build.gradle.kts`, then upload.
3. Before updating, make a backup (Backup & Restore › Create backup).
