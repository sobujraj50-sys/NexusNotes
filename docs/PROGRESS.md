# Development progress record

| Date | Milestone | Status | Notes |
|---|---|---|---|
| 2026-11-21 | M1 Foundation | Approved | Built by GitHub Actions (run #3), installed and checked on user's phone |
| 2026-11-23 | M2 Database + notes | Delivered, awaiting build + phone test | 29 JVM tests pass; DAO + UI tests need a device |

## Decisions log
- Markdown is the canonical note format (avoids lossy rich-text conversion).
- Single Gradle module; Hilt added in M2, not M1.
- String navigation routes (no kotlinx-serialization dependency).
- Android auto-backup disabled until M8 defines the backup policy.
- AGP 9.2.1 / Gradle 9.4.1 / Kotlin 2.3.20 / Compose BOM 2026.08.00 / compileSdk 37 / targetSdk 36 / minSdk 26.
- M2: manual dependency container instead of Hilt (AGP 9 plugin risk; small app).
- M2: Room 2.8.5 + KSP 2.3.12, DataStore 1.3.0. Schema JSON committed by the CI workflow.
- M2: empty new notes are never stored; Trash is read-only; flags don't change "last edited".
- Build & release: ZIP upload to GitHub → Actions builds a signed APK into Releases.
