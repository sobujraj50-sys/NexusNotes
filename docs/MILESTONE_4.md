# Milestone 4 — Folders, tags and search

## A. Objective
Organize notes in nested folders and with tags, and find anything with fast offline full-text search — with the first database upgrade done safely on phones that already hold notes.

## B. Technical design
- **Schema v2** (migration 1→2, add-only): `notes.folderId`, `folders`, `tags`, `note_tags`, `notes_fts` (SQLite FTS4, external content = notes, kept in sync by triggers).
- **Upgrade safety**
  1. Before Room upgrades, `DatabaseSafety` copies the database to `nexus-backup-v1.db` (with WAL files).
  2. The database is opened at startup off the main thread; if anything fails the app shows an error screen instead of crashing.
  3. SQLite runs the migration in a transaction: a failure rolls back and leaves v1 untouched.
- **Folders**: nested up to 8 levels; `FolderTree` blocks moving a folder into itself or its own sub-folders (no loops possible); sibling names unique (case-insensitive). Deleting a folder deletes its sub-folders and moves their notes to Unfiled — notes are never deleted.
- **Tags**: many per note, names unique ignoring case, created on the fly from the editor. Deleting a tag only unlinks it.
- **Search**: user text → `SearchQuery.toFtsMatch` (special characters removed, every word must match, prefix match). Filters: folder / Unfiled, tag, include archived. Trash is never searched. Title and snippet highlights. Folder and tag names matching the query appear as one-tap filters.
- **Search history**: off by default; stored only in local settings; turning it off deletes it.

## C. UI
- Folders tab: Library (All, Unfiled with count, Tags) + folder tree with counts; ⋮ per folder: New sub-folder, Rename, Color, Move, Delete. FAB New folder.
- Folder screen: sub-folders + notes; + creates a note inside the folder; search inside folder.
- Tags screen: tags with counts; tap = notes with that tag; ⋮ Rename/Delete.
- Editor: folder chip (tap to move) and tag chips (tap × to remove, “+ Tag” to add with suggestions).
- Search: filter chips (folder, tag, archived), suggestions, recent searches (if enabled), highlighted results.
- Home: top-level folders appear as shortcuts.
- Settings: “Remember searches” switch.

## D. Database
See `docs/ARCHITECTURE.md` §5. Version 2.

## E. Dependencies
None added. Version `0.4.0-m4`.

## J. Testing
### Run by Claude (2026-11-23)
- **100 JVM unit tests pass** (new: FolderTree 9 incl. loop/depth rules, SearchQuery 8 incl. injection-safe queries and Bangla).
- **Migration SQL executed on real SQLite** (Python sqlite3 with FTS4): v1 data preserved; search for English and Bangla prefixes; index updates on insert/update/delete; Trash excluded; folder/tag filters; duplicate tag names rejected.
- Whole-project syntax check clean.
- **Not run here**: Android compile (GitHub), `MigrationTest` (Room's own schema validation of the upgrade) and DAO/UI tests — these need a device.

### Manual tests on your phone (do M4-01 first)
| ID | Steps | Expected |
|---|---|---|
| M4-01 | Install over Milestone 3 (do not uninstall) | App opens normally; **all existing notes still there** |
| M4-02 | Search a word from an old note | Found and highlighted |
| M4-03 | Folders → New folder “Work” → ⋮ New sub-folder “Projects” | Tree shows Work › Projects |
| M4-04 | Open Projects → + → write note | Note is inside Projects; Work shows counts |
| M4-05 | Note editor → folder chip → move to Unfiled | Disappears from folder, appears in Unfiled |
| M4-06 | ⋮ Move “Work” into “Projects” | Option greyed out (no loops) |
| M4-07 | Rename to a name that already exists next to it | Error message, nothing changes |
| M4-08 | Delete “Work” | Confirmation; notes kept in Unfiled |
| M4-09 | Editor → + Tag → “ideas”; on another note add “Ideas” | Same tag reused (case-insensitive) |
| M4-10 | Tags → ideas | Shows both notes |
| M4-11 | Search with folder/tag/archived filters | Results respect filters |
| M4-12 | Search Bangla word part, e.g. “নোট” | Finds “নোটের” |
| M4-13 | Search `"quote" * (` | No crash; searches the words only |
| M4-14 | Settings → Remember searches on → search → reopen Search | Recent searches listed; Clear works; turning off deletes |
| M4-15 | Move a note to Trash → search its text | Not found |

## L. Known limitations
- Search matches from the start of words (not the middle of a word).
- Search ranking is pinned → recently edited (no relevance score yet).
- Prompts become searchable in Milestone 5.
- The pre-upgrade backup file is not yet visible in the app; Backup & Restore (M8) will expose it.

## N. Next — Milestone 5
Prompt Library: prompts, categories, favorites, copy/duplicate, version history, `{{variable}}` templates with the Template Runner, JSON import/export, prompts in search.
