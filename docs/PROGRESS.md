# Development progress record

| Date | Milestone | Status | Notes |
|---|---|---|---|
| 2026-11-21 | M1 Foundation | Approved | Built by GitHub Actions (run #3), installed and checked on user's phone |
| 2026-11-23 | M2 Database + notes | Approved | GitHub build #5 green; all features verified on phone. Build #4 failed: ZIP contained .github (token cannot change workflows) — ZIPs no longer include .github |
| 2026-11-23 | M3 Markdown editor | Approved | All features verified on phone |
| 2026-11-23 | M4 Folders, tags, search | Approved | Migration v1→v2 ran on the phone with existing notes; all features verified |
| 2026-11-23 | M5 Prompt Library | Delivered, awaiting build + phone test | Schema v3; template engine, versions, JSON import/export; 119 JVM tests; migration SQL verified |

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
- M3: Markdown is the single stored format (Nexus Markdown v1); own parser/formatter instead of a library.
- M4: add-only migration; pre-upgrade DB copy; folders without SQL FK (integrity in DAO transactions); FTS4 external content; search history off by default.
- M5: own template engine (single-pass, never mutates source) and strict JSON codec; prompt history coalesced per 5 min, 30 kept.
- 2026-11-24: Fixed crash opening Prompt Library — `{{variable}}` regex had an unescaped `}}`; Android's ICU regex rejects it (JVM accepts). All braces now escaped. Lesson: JVM tests cannot catch ICU-only regex errors; keep braces/brackets escaped in every pattern.
- 2026-11-24: Signing fixed — KEYSTORE_BASE64 secret had never been added (every build used a throwaway debug key, forcing uninstalls). Local crash reporter added (0.5.1).


## Milestone 6 — Attachments (0.6.0-m6) — delivered, awaiting test
Photo/audio/file attachments, image viewer, audio recorder/player, Share to Nexus Notes, missing-file recovery, storage cleanup, DB v4. Unit tests: 125 pass locally; v1→v4 migration verified on SQLite.

## Milestone 7 — Private Vault (0.7.0-m7) — delivered, awaiting test
AES-256-GCM vault with PIN (PBKDF2 + Android Keystore) and biometric unlock, encrypted notes and attachments, move to/out of vault, auto-lock, attempt limits, FLAG_SECURE, DB v5. Unit tests: 154 pass locally; v1→v5 migration verified on SQLite.

## Milestone 8 — Backup, Restore & Export (0.8.0-m8) — delivered, awaiting test
Verified ZIP backups (SHA-256 manifest), optional encrypted vault with backup password, Merge/Replace restore in one transaction with safety copy and undo, TXT/Markdown/JSON/PDF export (all or one note), history, DB v6. Unit tests: 181 pass locally; v1→v6 migration verified on SQLite.
