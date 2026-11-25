# Milestone 10 — Hardening & release candidate (v1.0.0-rc1)

## Done
- **Performance**
  - R8 code shrinking and optimisation in release builds (the biggest win for Compose speed and APK size). The app's own class names are kept, so crash reports stay readable.
  - Vault auto-lock is served from memory (no main-thread disk read).
  - Debug builds run StrictMode to flag main-thread disk work.
  - New large-note performance tests: a ~0.6 MB note for Markdown parsing, word count, task toggling and vault encryption, plus 5 000-note backup JSON.
- **Security review**: see `SECURITY_REVIEW.md`. Covers permissions, exported components, PendingIntents, logging, vault leakage paths, backups, residual risks.
- **Diagnostics**: Backup & Restore › Check database health runs SQLite `integrity_check` and `foreign_key_check`, and checks attachment files. It changes nothing.
- **Release**
  - Signed release via GitHub Actions; the signing fingerprint is in `RELEASE.md`.
  - `CHANGELOG.md` and the full manual `TEST_PLAN.md`.
  - The version is **1.0.0-rc1** until the test plan passes on a real phone; then it becomes 1.0.0 with a one-line change.

## Tests
204 unit tests pass locally (JVM). Room migrations v1→v7 were checked on SQLite. On-device instrumented tests (`MigrationTest`, `NoteDaoTest`, `NavigationTest`) are in `androidTest` but are not run by the CI workflow.

## Honest status
- Since M7 the code has been compiled in CI step by step. Fixes were made from the CI logs, because this workspace has no Android SDK. The first green release build plus `TEST_PLAN.md` on a phone are what make it 1.0.0.
- R8 is new in this build. If the release build fails at the R8 step, the log names the rule to add. If the app crashes only in the release build, the crash report will show it.
