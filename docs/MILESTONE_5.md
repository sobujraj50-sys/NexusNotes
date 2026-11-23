# Milestone 5 — AI Prompt Library

## A. Objective
A professional, offline prompt library: save, organize, search, version and reuse prompts with `{{variables}}`, and move them in/out as JSON.

## B. Technical design
- **Schema v3** (migration 2→3, add-only): `prompts`, `prompt_categories`, `prompt_variables`, `prompt_versions`, `prompts_fts` (FTS4). Same safety as M4: pre-upgrade copy (`nexus-backup-v2.db`), startup gate, transactional migration.
- **Template engine** (`domain/prompt/PromptTemplate`):
  - `{{name}}` or `{{ name }}`; names start with a letter or `_`, then letters/vowel signs/digits/`_` — Bangla names like `{{বিষয়}}` work.
  - `\{{` is a literal `{{`.
  - Single pass: a value containing `{{x}}` is inserted literally, never re-processed.
  - Empty values leave `{{name}}` in place and are listed as missing.
  - The stored template is never modified; the runner works on a copy.
- **Version history**: edits within 5 minutes update the latest version; later edits start a new one; 30 kept per prompt. Restoring creates a new version (nothing is lost).
- **Variable defaults** stored per prompt; prefilled in the runner.
- **Categories**: the 11 built-in ones plus your own (create, rename, delete → prompts move to General).
- **Import/export**: `nexus-prompts` JSON v1 through the system file picker (no storage permission). Import never overwrites: same id + same content → skipped; same id + different content → added as “(imported)” copy; invalid entries skipped and counted. Own strict JSON parser (no library).
- **Search**: Prompt Library search and the global Search both use the prompt FTS index (title + template).
- **Examples**: 6 optional starter prompts, added only when you tap the button.

## C. UI
- Library: search, chips (All, Favorites, each category with counts), prompt cards with Copy and Fill & run; ⋮ New category, Rename/Delete selected custom category, Export all, Import, Add examples.
- Editor: title, category picker (+ New category…), monospace template with highlighted `{{variables}}`, insert-variable chips (subject, style, camera, lighting, aspect_ratio, duration, negative_prompt, Custom…), default value per variable, undo/redo, favorite, ⋮ Copy / Share / Duplicate / Version history / Delete, “Fill variables & run”.
- Template Runner: one field per variable, live result (filled parts highlighted, missing ones underlined in red), Copy, Share, Reset to defaults.

## E. Dependencies
None added. Version `0.5.0-m5`.

## J. Testing
### Run by Claude (2026-11-23)
- **119 JVM unit tests pass** (new: PromptTemplate 10, MiniJson 4, PromptExchange 5). Caught and fixed: Bangla vowel signs were not allowed in variable names.
- v1→v2→v3 migration SQL run on real SQLite: all tables created; prompt FTS search (English, Bangla, text inside `{{ }}`), index updates on edit/delete, version trimming to 30, category move on delete; note search still works.
- Whole-project syntax check clean.
- Not run here: Android compile (GitHub), MigrationTest / DAO / UI tests (device).

### Manual tests on your phone
| ID | Steps | Expected |
|---|---|---|
| M5-01 | Install over M4 (do not uninstall) | All notes, folders, tags still there |
| M5-02 | Prompts tab (empty) → Add example prompts | 6 prompts in their categories |
| M5-03 | Open “Cinematic still” → Fill variables & run | Fields prefilled with defaults; result updates live |
| M5-04 | Clear one field | Shown as `{{name}}` underlined + “Not filled yet” |
| M5-05 | Copy / Share result | Clipboard / share sheet with the filled text |
| M5-06 | Back to editor | Template unchanged |
| M5-07 | New prompt → title + template with `{{subject}}` | Autosaves; variable listed; set a default |
| M5-08 | Insert chips / Custom… `bad name!` | Inserted / error shown |
| M5-09 | Edit, wait 5+ min, edit again → Version history → restore older | Content restored; history keeps both |
| M5-10 | Favorite, Duplicate, Delete (confirm) | Works; delete removes it |
| M5-11 | ⋮ New category “Midjourney” → move a prompt there → delete category | Prompt moved to General Prompts |
| M5-12 | Library search “golden” | Finds by template text |
| M5-13 | Global Search a prompt word | “Prompts” section appears; tap opens it |
| M5-14 | Export all → choose file; Import the same file | “0 imported, N already present” |
| M5-15 | Edit a prompt, import the old file again | Old one added as “(imported)” copy; yours untouched |
| M5-16 | Import a random non-JSON file | Clear error, nothing changed |
| M5-17 | Bangla template `ছবি: {{বিষয়}}` | Variable detected and fillable |

## L. Known limitations
- No AI calls here (optional AI integration is Milestone 9); prompts are copied/shared to the AI app you use.
- Runner values are not remembered after leaving (defaults are).
- Exported files are plain JSON — keep them private if prompts are sensitive.

## N. Next — Milestone 6
Attachments: images (Photo Picker), audio recording, files (system picker), previews, size limits, share-to-notes, safe app-private storage and missing-file recovery.
