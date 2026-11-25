# Milestone 8 — Backup, Restore & Export (v0.8.0-m8)

## What was built
- **Create backup** → one `.zip` saved where you choose (Storage Access Framework: phone storage, SD card, Drive/OneDrive app…). Includes notes (also Archive and Trash), folders, tags, prompts with categories/variables/history, and attachments.
- **Vault in backups (optional)**: vault notes and files are copied **still encrypted**. The vault key goes in wrapped with a separate **backup password** (PBKDF2-HMAC-SHA256, 600 000 rounds, 10+ characters, must include non-digits). The phone's Keystore key never leaves the phone, so this is what makes moving the vault to a new phone possible.
- **Verify after writing**: each backup is read back and fully checked before "success" is shown.
- **Check a backup file**: verifies a file without restoring.
- **Restore** with a preview (date, app version, counts) and two modes:
  - **Merge (default)** never overwrites. Identical items are skipped. Changed items come back as "(restored)" copies. Tags match by name. Missing folders mean Unfiled.
  - **Replace** (type REPLACE) deletes current notes/folders/tags/prompts/attachments and loads the backup. A **safety copy** is saved first, so **Undo last restore** can bring the old data back. The vault is never deleted by a restore.
  - **Vault restore**: needs a vault on this phone (unlocked) plus the backup password. Notes are re-encrypted with this phone's vault key; duplicates are skipped.
- **Export** (readable, not restorable): Markdown ZIP (YAML front matter + attachments folder), TXT ZIP, JSON, PDF (A4, Bangla supported). It applies to all notes, or to one note from the editor's ⋮ › Export…. Vault and Trash are never exported.
- **History** of backups, restores, checks and exports. No note content is kept there.
- DB v6: `backup_history` (add-only migration).

## Backup file format (v1)
```
data.json            all records; vault rows stay AES-GCM ciphertext (Base64)
attachments/<file>   attachment bytes as stored
vault/<id>.nxv       vault files, still encrypted (chunked AES-GCM)
manifest.json        format, formatVersion, appVersion, dbVersion, createdAt, counts,
                     and size + SHA-256 of every other entry (written last)
```
Restore checks, before anything changes:
- The ZIP is readable, and its entry names are on an allow-list (no paths or `..`, no extra files).
- Size limits hold (zip-bomb protection).
- Every listed file is present, with the same size and SHA-256, and nothing unlisted.
- The format version is known (newer → "update the app").
- JSON fields are strictly typed.
- No duplicate ids, every attachment has its note and its file, counts match the manifest.

## How a restore stays all-or-nothing
1. The whole file is verified into a private staging folder (cache).
2. A Replace restore first writes a safety copy of the current data. If that fails, nothing is changed.
3. Attachment files are copied under new names only (never overwriting).
4. All database rows are written in **one transaction** (a Replace's deletes are in that same transaction). If anything fails, the copied files are removed and the database is untouched.
5. Vault notes are restored last and separately. A wrong backup password only skips the vault part and says so.

## Automated tests (run locally: 181 unit tests pass)
- `BackupArchiveTest` (15): full round trip (Bangla, quotes, emoji); empty backup; changed attachment / changed data → integrity failure; missing file; missing manifest; truncated file; not a ZIP; a foreign ZIP; path traversal (`../`) rejected with nothing written; newer format; size limit; orphan attachment; unsafe file name in data; name rules.
- `RestorePlannerTest` (6): merge into empty, duplicate skip vs "(restored)" copy, no overwrite, tag-by-name, dropped dangling links, file-name clash (this test found a real bug, now fixed), conflicting prompt with children, replace, folder cycles.
- `ExportAndKeyTest` (6): Markdown front matter, TXT, JSON, safe unique file names (Bangla kept), vault backup key wrap/unwrap/wrong password/tampered, password rules.
- `MigrationTest` (device/CI): v1→v6 incl. backup_history. v1→v6 was also checked on SQLite here, including the search index after a Replace.

## Manual tests
1. Backup & Restore → Create backup → no vault → save to Downloads → "Backup saved and verified".
2. Check a backup file → choose it → "Backup is OK" with counts.
3. Edit a note, delete another → Restore → Merge → the deleted note comes back, the edited one appears as "(restored)" copy, nothing lost.
4. Restore → Replace → type REPLACE → data equals the backup. Then **Undo last restore** → the previous data is back.
5. Vault: create a backup with vault + backup password. Restore on a phone with a vault (unlock) + password → vault notes added. Try a wrong password → normal notes restored, vault part reports "Wrong backup password".
6. Open a backup .zip with a file manager, change any file, try Restore → "damaged … integrity check", nothing changed.
7. Export Markdown / TXT / JSON / PDF → open them in other apps; the PDF shows Bangla correctly.
8. In a note: ⋮ › Export… › PDF.
9. Update from 0.7.0 without uninstalling → all data present.

## Known limitations
- No automatic scheduled backups (needs background work; planned with reminders in M9). Backups are manual.
- PDF prints Markdown as plain text and doesn't embed images.
- A backup made with "include vault" needs the vault unlocked at that moment. A forgotten backup password means the vault part of that backup can't be restored.
- Restoring vault notes needs a vault on the new phone first (set one up with any PIN, unlock, then restore).
- Large backups are built and verified on the phone; very large attachment collections take a while.
