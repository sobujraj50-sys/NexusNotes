# Security review — Nexus Notes 1.0.0-rc1

Reviewed: the whole code base after Milestones 1–9. Method: reading the code, grep-based audits (commands below), and the automated tests. This is a self-review, not an external audit.

## Permissions (AndroidManifest)
| Permission | Used for | Asked |
|---|---|---|
| RECORD_AUDIO | Audio attachments | Only when "Record audio" is tapped |
| USE_BIOMETRIC | Vault fingerprint/face unlock | Normal permission, no prompt |
| POST_NOTIFICATIONS | Reminders | Only when the first reminder is saved |
| SCHEDULE_EXACT_ALARM | On-time reminders | User can revoke; app degrades to inexact |
| RECEIVE_BOOT_COMPLETED | Re-arm reminders/backup job | — |
| **No INTERNET** | — | The app cannot open network connections at all |
| **No storage permissions** | Photo Picker / SAF only | — |

## Findings
| Area | Check | Result |
|---|---|---|
| Network | No INTERNET permission, no network libraries | ✅ |
| Android backup | `allowBackup=false` + data-extraction rules exclude everything | ✅ |
| Exported components | Only MainActivity (launcher + share target). Receivers, job service, FileProvider: `exported=false` | ✅ |
| PendingIntents | All 6 use FLAG_IMMUTABLE and explicit components | ✅ |
| Shared-in content | Only `content://` URIs; size limits; sanitized names; text capped at 200k chars | ✅ |
| FileProvider | Only `files/attachments/`; temporary read grant on explicit Open/Share | ✅ |
| Vault crypto | AES-256-GCM with per-record AAD; PBKDF2 210k + Keystore wrap; no PIN stored; attempt limit | ✅ (28 tests) |
| Vault leakage | Not in DB notes/FTS, Home, widget, search, exports, auto backups, notifications; FLAG_SECURE on vault screens; images decrypted in memory only | ✅ |
| Deleted plaintext | SQLite secure_delete + WAL checkpoint; old DB upgrade copies removed when a note moves to the vault | ✅ (flash storage can't be guaranteed wiped — documented) |
| Backups | SHA-256 manifest; allow-listed entry names (no path traversal); size limits; one-transaction restore; safety copy before Replace | ✅ (27 tests) |
| Backup vault key | Separate backup password, PBKDF2 600k, min 10 chars incl. non-digits | ✅ |
| Logging | No `Log.*`/`println` of content anywhere | ✅ |
| Crash reports | Stored locally only (`last_crash.txt`), shown to the user, never sent | ✅ |
| Notifications | Title only; lock-screen public version hides it | ✅ |
| Widget | Titles only; never vault | ✅ |
| AI tools | Off by default; user sees the full prompt; leaves only via user's share/copy | ✅ |
| Release build | R8 enabled; signed with the project key (fingerprint in RELEASE.md) | ✅ |
| Secrets in repo | Keystore and password only in GitHub Secrets; ZIPs and repo contain no key files | ✅ |

## Residual risks (accepted, documented to the user)
- A **6-digit PIN** can be brute-forced offline by an attacker who can use this phone's Keystore (rooted phone). Longer passphrases are offered and recommended.
- Plaintext of an **unlocked** vault is in RAM and on screen. The JVM may keep copies we can't wipe.
- **Normal (non-vault) notes are not encrypted at rest by the app**; they rely on Android's file-based encryption and the app sandbox. Backups of normal notes are not encrypted either; the user is told.
- Keyboards are third-party apps. Autocorrect is off in the vault, but a keyboard could still record input.
- Phone-maker battery savers may delay reminders.

## Audit commands used
```
grep -rn "Log\.\(d\|i\|v\|w\|e\)(\|println(" app/src/main/java      # → nothing
grep -n "exported\|allowBackup\|INTERNET" app/src/main/AndroidManifest.xml
grep -rn "PendingIntent.get" -A4 app/src/main/java | grep -c FLAG_IMMUTABLE   # 6 of 6
```
