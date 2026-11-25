# Milestone 7 — Private Vault (v0.7.0-m7)

## What was built
- **Vault setup**: PIN (6+ digits) or passphrase, entered twice, with an explicit "no recovery" acknowledgement.
- **Unlock** with PIN, or **fingerprint/face** (Android 11+, Class 3 "strong" sensors only).
- **Encrypted vault notes**: title + body (Markdown preview), encrypted autosave.
- **Encrypted attachments**: photos and files. Images are decrypted into memory only, with pinch-zoom. "Save a copy…" exports a plaintext copy only to a place you choose, after a warning.
- **Move to Vault** from any note: the note and its attachments are encrypted, then the original is deleted for good (row, search index, files, old DB copies). Folder and tags are not kept.
- **Move out of vault**: turns a vault note back into a normal note.
- **Auto-lock** after you leave the app: immediately, 1, 5 or 15 min (default 1 min). The vault also locks on the lock button, when the app is killed, and on restart. Rotation doesn't lock it. File pickers get a 2-minute grace period.
- **Wrong-PIN limit**: 5 free tries, then waits of 30 s, 1 min, 2 min … up to 1 h.
- **Change PIN**, turn fingerprint unlock on/off, delete the vault (type DELETE).
- **Screenshots blocked** and the Recents preview blanked on vault screens (FLAG_SECURE).
- DB v5: `vault_items`, `vault_attachments` (add-only migration).

## Cryptographic design
```
vaultKey   = 32 random bytes (SecureRandom)
pinKey     = PBKDF2-HMAC-SHA256(PIN, salt 16 B, 210 000 iterations)
stored     = KeystoreAES-GCM( AES-GCM(pinKey, vaultKey, AAD "nexus-vault-key-v1") )   → files/vault/vault.meta
biometric  = KeystoreAES-GCM[user-auth, biometric-strong, invalidated on new enrolment]( vaultKey )
note       = AES-256-GCM(vaultKey, title+body, AAD "note:<id>")                    → vault_items.payload
att. meta  = AES-256-GCM(vaultKey, name+type+size, AAD "att:<id>")                 → vault_attachments.payload
file       = chunked AES-256-GCM, 64 KB chunks, nonce = prefix|counter|last-flag, AAD "file:<id>" → files/vault/files/<id>.nxv
```
- A wrong PIN is detected by GCM tag failure. **No PIN or PIN hash is stored.**
- The attempt is counted *before* checking, so killing the app mid-check gives no free tries.
- Only plaintext timestamps (created/updated) and the number of vault notes are visible without the key.

## Threat model
| Threat | Protected? |
|---|---|
| Someone copies the app's files/DB (backup tool, root, lost phone forensics) | Yes. Data is AES-GCM; the outer key is non-exportable in Android Keystore |
| Files moved to another phone | Yes. The Keystore key doesn't exist there |
| Someone with the unlocked phone, vault locked | Yes. Needs the PIN; guesses are rate-limited |
| Screenshots / Recents thumbnail / screen recording | Blocked on vault screens |
| Search, Home, Trash, notifications, Android backup | Vault content never goes there |
| Tampering (changed/swapped/truncated ciphertext) | Detected and refused (tests) |
| A rooted attacker who can drive this phone's Keystore and reset the attempt counter | **Partly.** They can try PINs offline at PBKDF2 speed. A 6-digit PIN would fall; a long passphrase resists. This is why passphrases are offered |
| Malware or a person using the phone while the vault is **unlocked** | **No.** Nobody can protect plaintext that is on screen |
| Keyboard apps learning typed words | Reduced (autocorrect off). The keyboard is still a third-party app |
| Plaintext in RAM | Wiped from our buffers on lock; the JVM/Compose may keep transient copies (platform limit) |

## Recovery limits (shown to the user)
- **Forgotten PIN**: no reset, no back door. The only option is to delete the vault.
- **Keys lost** (factory reset, restored on another phone, rare OS keystore loss): the app shows "The vault can't be opened" and offers deletion. Normal notes are not affected.
- **Fingerprints changed**: fingerprint unlock turns itself off; the PIN still works.
- **Backups**: Android backup is disabled for the whole app. Encrypted vault backup comes in Milestone 8 (it will stay encrypted and never be exported as plain text).

## Automated tests (run locally: 154 unit tests pass)
- `VaultCryptoTest` (14): round trips, no plaintext in ciphertext, wrong key, swapped record (AAD), every flipped bit, truncation, chunk reorder, chunk-boundary sizes, size limit, PBKDF2 determinism, codec.
- `VaultKeyManagerTest` (14): create/unlock, PIN not stored, wrong-PIN countdown → lockout → recovery, growing lockout, count-before-check, device key lost, files on another device, change PIN (right/wrong old PIN), weak PINs, destroy, no double create, damaged settings file → "keys lost", not a new vault.
- `MigrationTest` (on-device, CI-capable): v1→v5 chain, vault tables, cascade delete.
- Keystore and biometric code can't run on the JVM. Covered by the manual tests below.

## Manual tests
1. Vault → set up with PIN `482915` → the vault opens.
2. New vault note → type → back → lock (🔒) → the note is not visible → unlock → it is there.
3. Wrong PIN 5× → wait message with a countdown → the right PIN works after the wait.
4. Menu → Turn on fingerprint unlock → lock → reopen the vault → the fingerprint prompt appears → unlocks.
5. Add a new fingerprint in Android settings → open the vault → message "fingerprint unlock was turned off" → the PIN works.
6. Open a normal note with a photo → ⋮ → Move to Vault → (PIN) → Move → the note disappears from Home/Search/Trash → it is in the vault with its photo.
7. Vault note → ⋮ → Move out of vault → it opens as a normal note, searchable again.
8. Try a screenshot in the vault → blocked. Recents → the app preview is blank.
9. Auto-lock "Immediately" → press Home → return → locked. Add a photo from the vault (picker) → no lock while picking.
10. Force-stop the app → reopen → the vault is locked.
11. Update from 0.6.0 without uninstalling → all notes intact → the vault starts "not set up".
12. Menu → Delete vault… → type DELETE → the vault is empty and asks for setup again.

## Known limitations
- No vault backup yet (M8). Uninstalling the app or clearing its data destroys the vault.
- Vault audio recording isn't offered (recordings would need a plaintext temp file). Audio files can be added as files.
- Vault notes have no folders, tags, pin or colors. Search inside the vault filters in memory.
- Fingerprint unlock is not offered on Android 8–10.
- Flash storage can keep deleted blocks; deleted plaintext files can't be guaranteed wiped at hardware level (SQLite secure_delete and WAL checkpoint are used for the database).
