# Security & privacy

Status: **Milestone 1 — no user data is stored and no encryption exists yet.** This file states the threat model the later milestones are built against. It will be revised in M7 and reviewed in M10.

## What the app protects
- Ordinary notes, prompts and attachments: private to the app sandbox, never uploaded.
- Vault items: confidentiality and integrity even if someone copies the app's data files.

## Threat model
| Threat | In scope? | Planned defense |
|---|---|---|
| Another app reading Nexus data | Yes | Android app sandbox; app-private storage; no exported components except the launcher |
| Someone with the unlocked phone opening the app | Vault only | Vault PIN/biometric, auto-lock timeout, FLAG_SECURE on vault screens, no vault previews or notifications |
| Copy of app data via backup / device transfer | Yes | Android backup disabled (M1); M8 allows only an explicit, user-triggered backup; vault stays encrypted inside it |
| Offline attack on a stolen data file | Vault | AES-256-GCM per item; key derived from PIN with a slow KDF and random salt, wrapped by an Android Keystore key; attempt limits |
| Plaintext leaks (logs, search index, temp files, recents screenshot) | Yes | No content logging; vault excluded from FTS; decrypted attachments only in memory or deleted temp files; FLAG_SECURE |
| Silent upload of notes | Yes | No network code in core features; AI features opt-in with per-use consent; no analytics or ad SDKs |
| Rooted / compromised device, malware with root | No | Out of scope — documented limitation |
| Forgotten PIN | Accepted risk | No recovery or back door, by design. Shown to the user before vault creation |

## Rules (from the project brief)
No ads, no trackers, no mandatory account or internet, no plaintext vault secrets, no sensitive logs, no silent uploads, no unnecessary permissions, no hard-coded keys, no destructive migrations, no private content in notifications, no unencrypted vault in backups, no insecure temp copies, no false security claims, no fake features.

## Search (Milestone 4)
- The full-text index lives inside the app database; it only ever contains normal notes. Vault notes (M7) are stored elsewhere and are never indexed.
- Search history is **off by default**; when on, the last 10 queries are kept in local settings only; turning it off deletes them.
- Before a database upgrade, a copy `nexus-backup-v<old>.db` is kept in app-private storage (not readable by other apps, excluded from Android backup).

## Data retention and deletion (target behavior)
- Trash keeps notes until restored or permanently deleted (optional auto-empty after N days, user-set).
- Permanent delete removes the row, its FTS entry, versions and attachment files.
- Uninstalling the app deletes all app data; user-exported backups remain where the user saved them.

## Reporting
Personal project: record issues in docs/PROGRESS.md.


## Attachments (M6)
- Stored only in app-private storage; no broad storage permission requested.
- Photo Picker / Storage Access Framework give one-time access to the chosen item only.
- Microphone permission is requested only when recording starts.
- Files are shared out only on explicit user action, through FileProvider with a temporary read grant.
- Shared-in content is treated as untrusted: only content:// URIs, size limits, sanitized names.

## Private Vault (M7)
See docs/MILESTONE_7.md for the full design and threat model. Summary: AES-256-GCM with per-record AAD; vault key wrapped by PBKDF2(PIN, 210k) and a non-exportable Android Keystore key; optional biometric Keystore key (strong, invalidated on enrolment change); no PIN or PIN hash stored; attempt counter with growing lockout; vault data outside notes/FTS; FLAG_SECURE; secure_delete + WAL checkpoint after moving notes in; old DB upgrade copies deleted after a move. No recovery without the PIN — by design.
