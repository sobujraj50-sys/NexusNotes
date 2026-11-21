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

## Data retention and deletion (target behavior)
- Trash keeps notes until restored or permanently deleted (optional auto-empty after N days, user-set).
- Permanent delete removes the row, its FTS entry, versions and attachment files.
- Uninstalling the app deletes all app data; user-exported backups remain where the user saved them.

## Reporting
Personal project: record issues in docs/PROGRESS.md.
