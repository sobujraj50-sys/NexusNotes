# Milestone 2 — Database and note CRUD

## A. Objective
Notes are really saved on the phone: write, autosave, edit, pin, favorite, color, archive, trash, restore, delete forever, duplicate, sort, undo/redo, plus working theme settings.

## B. Technical design
- **Room** database `nexus.db`, version 1, one table `notes`. Schema exported to `app/schemas/` (the GitHub workflow commits it automatically).
- **DataStore** (`settings.preferences_pb`) for theme mode, dynamic color and note sort order.
- **Manual DI** via `AppContainer` (owned by `NexusNotesApp`). Hilt was planned but skipped: its Gradle plugin is the most likely part to break on AGP 9, and the app is small.
- **MVVM**: `HomeViewModel`, `NoteListViewModel` (All / Favorites / Archive / Trash), `NoteEditorViewModel`, `SettingsViewModel`; screens observe `StateFlow`.
- **Autosave**: each text change restarts a 600 ms timer. Pending text is also written when the app goes to the background (`ON_STOP`), when Back is pressed and when the ViewModel is cleared. Writes run in an application-wide coroutine scope and are `NonCancellable`, so leaving the screen never loses the last edit.
- **Empty notes are never stored**: a new note gets its row only when the title or body has text.
- **Undo/redo**: `EditHistory` groups typing bursts (1 s) into one step, keeps 100 steps, in memory only.
- **Trash** = soft delete (`deletedAt`). Notes in Trash are read-only. Permanent delete works only for notes already in Trash and always asks first.
- Flags (pin, favorite, color, archive) do not change "last edited"; only text changes do.

## D. Database (schema v1)
`notes(id TEXT PK, title, body, bodyFormatVersion=1, colorIndex?, isPinned, isFavorite, isArchived, deletedAt?, createdAt, updatedAt)` with indexes on `updatedAt`, `(deletedAt, isArchived)`, `isPinned`. No migrations yet (first version). No destructive fallback.

## E. Dependency changes
Added: Room 2.8.5 (runtime, ktx, compiler via KSP), KSP 2.3.12, DataStore Preferences 1.3.0, lifecycle-viewmodel-compose / savedstate 2.11.1. Version name `0.2.0-m2`.

## J. Testing checklist

### Run by Claude (2026-11-23)
- **29 JVM unit tests passed**: EditHistory (11), NoteText word/char counts incl. Bangla and emoji (8), plus the 10 from M1. Compiled with Kotlin 2.0.21 outside Gradle.
- Syntax check of every Kotlin file: no syntax errors.
- **Not run here**: Android compile (runs in GitHub Actions), `NoteDaoTest` (8 database tests) and `NavigationTest` (4 UI tests) — these need a phone/emulator.

### Manual tests on your phone
| ID | Steps | Expected |
|---|---|---|
| M2-01 | Quick note → type a title and text → Back | Note appears on Home under Recent notes |
| M2-02 | Open it, edit, watch the status line | "Editing…" then "Saved" within about 1 second |
| M2-03 | Type text, press Home button immediately, swipe app away from Recents, reopen | Text is there |
| M2-04 | Restart the phone, open the app | All notes still there |
| M2-05 | Quick note → Back without typing | No empty note is created |
| M2-06 | Type, pause, type more → Undo twice → Redo | Text steps back and forward correctly |
| M2-07 | Pin a note | It moves to the Pinned section and to the top of All Notes |
| M2-08 | Star a note | Appears in Favorites (Home and Settings › … › Favorites chip) |
| M2-09 | ⋮ → Color label → pick a color | Colored stripe on the card, dot in the editor |
| M2-10 | ⋮ → Duplicate | A "(copy)" note opens; original unchanged |
| M2-11 | ⋮ → Archive | Disappears from Home/All Notes, appears in Archive; Unarchive brings it back |
| M2-12 | ⋮ → Move to Trash | Disappears; appears in Trash; opening it is read-only |
| M2-13 | In Trash: open → Restore | Back in All Notes |
| M2-14 | In Trash: open → ⋮ → Delete forever → confirm | Gone for good; Cancel keeps it |
| M2-15 | Trash → Empty → confirm | Trash empty |
| M2-16 | All Notes → sort icon → each option | Order changes; pinned stay on top; choice remembered after restart |
| M2-17 | Settings → Theme → Light / Dark / Follow system | App switches immediately; remembered after restart; status bar icons readable |
| M2-18 | Settings → Dynamic color (Android 12+) | Colors follow wallpaper |
| M2-19 | Write a long Bangla note | Word count correct; typing stays smooth |
| M2-20 | ⋮ → Note info | Created / last edited times, counts |
| M2-21 | Install over the Milestone 1 app | Installs as an update (same signing key) |

## L. Known limitations
- Search, folders and tags arrive in Milestone 4. "Unfiled" currently lists all notes.
- Formatting buttons (bold, lists…) arrive in Milestone 3; the body is plain text for now (it is stored as Markdown-ready text).
- Undo history is lost when the editor is closed.
- Trash is never emptied automatically.
- No database migration exists yet because this is schema version 1; migration tests start with the first schema change (Milestone 4).

## M. Completion criteria
GitHub build green; manual tests M2-01 … M2-21 pass on your phone; you approve.

## N. Next — Milestone 3
Markdown editor: formatting toolbar (bold, italic, underline, strikethrough, headings, lists, checklists, quotes, links, code, tables), live preview, find in note, distraction-free mode.
