# Release test plan — Nexus Notes 1.0

Tick each item on a real phone (ideally Android 15 and one older device, Android 8–11).
Before starting: **Backup & Restore › Create backup** (so nothing can be lost).

## A. Install & update
- [ ] A1 Install the release APK over the previous version (no uninstall). All notes, prompts and attachments are still there; the vault is still locked with the same PIN.
- [ ] A2 Settings › About shows version 1.0.0-rc1.
- [ ] A3 Close the app from Recents, reopen → same data. Restart the phone → same data.

## B. Notes & editor
- [ ] B1 New note → type → back → it's saved (no save button needed). Empty new notes are not saved.
- [ ] B2 Markdown toolbar (bold, heading, list, checklist), preview, tap a checkbox in preview.
- [ ] B3 Undo/redo, Find in note, Focus mode.
- [ ] B4 Pin, favourite, color, archive, duplicate, Trash → restore → delete forever.
- [ ] B5 A very long note (paste ~50 pages) stays smooth when typing and scrolling.

## C. Organise & search
- [ ] C1 Folders with sub-folders, move a note, delete a folder (notes kept).
- [ ] C2 Tags: add, rename, filter.
- [ ] C3 Search English and Bangla ("নোট" finds "নোটের"), filters by tag/folder.

## D. Prompt Library
- [ ] D1 New prompt with {{variables}}, run it in Template Runner, copy result.
- [ ] D2 Version history restore; import/export JSON.

## E. Attachments
- [ ] E1 Photo, audio recording, PDF; open, share, remove.
- [ ] E2 Share from Gallery/Chrome → "Save to Nexus Notes" makes a note.

## F. Vault
- [ ] F1 Set up, lock/unlock (PIN), fingerprint on/off.
- [ ] F2 Wrong PIN 5× → wait; right PIN after wait works.
- [ ] F3 Move a note with a photo into the vault → gone from Home/Search/Trash/widget; inside vault with photo. Move out again.
- [ ] F4 Screenshot blocked; Recents preview blank; auto-lock "Immediately" locks after Home.

## G. Backup, restore, export
- [ ] G1 Create backup (with vault + password) → "saved and verified". Check a backup file → OK.
- [ ] G2 Restore › Merge after deleting a note → it returns, nothing else changes.
- [ ] G3 Restore › Replace → Undo last restore brings the old state back.
- [ ] G4 Wrong backup password → normal notes restored, vault part reports the error.
- [ ] G5 A modified backup file is rejected; nothing changes.
- [ ] G6 Export Markdown/TXT/JSON/PDF; Bangla correct in the PDF. Single-note export from ⋮.
- [ ] G7 Automatic backup: choose folder, Back up now, file appears; retention works.
- [ ] G8 Check database health → all OK.

## H. Reminders, templates, widget, shortcuts, AI
- [ ] H1 Reminder in 2 minutes → notification → Snooze → again in 10 min → tap opens note.
- [ ] H2 Daily reminder survives a phone restart (Settings › Reminders shows next time).
- [ ] H3 Template › Daily journal; Save as template; delete own template.
- [ ] H4 Widget shows pinned/recent titles, opens notes, "+" works, updates after edits.
- [ ] H5 App-icon shortcuts: Quick note, New checklist, Search.
- [ ] H6 AI tools off by default; turn on → AI tools… shows prompt → Send to AI app.

## I. Look & accessibility
- [ ] I1 Dark/light/system theme; dynamic color (Android 12+).
- [ ] I2 Large font (Android settings › font size max): screens still usable, nothing cut off badly.
- [ ] I3 TalkBack: main buttons are announced (back, more options, attach, lock, etc.).

## J. Failure behaviour
- [ ] J1 Airplane mode: everything works (the app never needs the internet).
- [ ] J2 Delete an attachment file with a file manager is not possible (private storage); a "Missing" tile appears only if storage is cleared by the system — app doesn't crash.
- [ ] J3 If a crash ever happens, the next start shows the crash report with Copy.

Result: release as 1.0.0 only when every item passes (or a failing item is fixed).
