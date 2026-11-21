# Development progress record

| Date | Milestone | Status | Notes |
|---|---|---|---|
| 2026-11-21 | M1 Foundation | Delivered, awaiting your build + test report | 10 JVM unit tests passed in Claude's environment; Gradle build and UI tests not yet run (no Android SDK there) |

## Decisions log
- Markdown is the canonical note format (avoids lossy rich-text conversion).
- Single Gradle module; Hilt added in M2, not M1.
- String navigation routes (no kotlinx-serialization dependency).
- Android auto-backup disabled until M8 defines the backup policy.
- AGP 9.2.1 / Gradle 9.4.1 / Kotlin 2.3.20 / Compose BOM 2026.08.00 / compileSdk 37 / targetSdk 36 / minSdk 26.
