# Milestone 6 — Attachments (v0.6.0-m6)

## What was built
- **Attach** chip in the note editor → Photo (system Photo Picker), Record audio, File (system file picker).
- Files are **copied** into app-private storage (`files/attachments/<id>.<ext>`), checked with SHA-256, written to a temp file and then renamed, so a crash never leaves a half file.
- Attachment strip under the note: image thumbnails (EXIF rotation, down-sampled), audio play/stop with duration, file tiles with name + size.
- Full-screen image viewer with pinch-zoom and share.
- Tile menu: Open with…, Share, Remove, metadata (name · size).
- **Share → "Save to Nexus Notes"** from any app (text, links, images, audio, PDF; several images at once) creates a new note and opens it.
- Missing-file recovery: if a file vanished (cleaner app), the tile shows **Missing** and offers Remove; the app never crashes on it.
- Cleanup: deleting a note forever / emptying trash removes its files; orphan files are swept on startup.
- Duplicate note copies its attachments.
- Settings → Attachment storage used.
- DB v4 (`attachments` table, add-only migration, backup before upgrade).

## Limits
- 50 MB per file, 50 attachments per note, 30 min per recording.

## Permissions / privacy
- No storage permission at all (Photo Picker and the file picker give one-time access).
- `RECORD_AUDIO` is asked only when you tap Record audio for the first time.
- Files leave the app only when you tap Share / Open with (via FileProvider, temporary read grant).

## Manual tests
1. Note → Attach → Photo → pick a photo → thumbnail appears; tap → full screen, pinch zoom.
2. Attach → Record audio → allow microphone → speak → Stop & save → tap tile plays; tap again stops.
3. Attach → File → pick a PDF → tile shows name + size → menu → Open with…
4. Tile menu → Remove → confirm → gone.
5. In Gallery/Chrome → Share → Save to Nexus Notes → new note opens with the content.
6. Delete note → Trash → Delete forever → Settings storage number goes down.
7. Update over 0.5.2 without uninstalling → old notes and prompts still there.

## Known limitations
- No in-app PDF viewer (opens in your PDF app). No video attachments. Attachments are not yet encrypted (Vault, M7) and not yet in backups (M8).
