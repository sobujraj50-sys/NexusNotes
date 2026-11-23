# Milestone 3 — Markdown editor

## A. Objective
Formatting, Markdown preview, find in note and distraction-free writing, with no lossy conversion.

## B. Technical design
- **Markdown is the single source of truth** (see `docs/EDITOR_FORMAT.md`). No WYSIWYG span model: Compose has no mature rich-text editor, and a hidden span model would risk losing formatting on export.
- `domain/markdown/MarkdownFormatter` — pure functions for toolbar actions, list continuation on Enter, checkbox toggle, find.
- `domain/markdown/MarkdownParser` — small block + inline parser for the supported subset; unknown syntax stays as text.
- `feature/editor/MarkdownPreview` — renders parsed blocks with Compose; links open only for https/http/mailto.
- Find highlights matches with a `VisualTransformation` (display only; stored text untouched).
- Focus mode hides system bars and all chrome; Back exits.
- No new libraries (no dependency risk).

## C. UI
Editor top bar: Back · Undo · Redo · Preview/Edit · Pin · Favorite · ⋮ (Find in note, Focus mode, Color, Duplicate, Archive, Trash, Info).
Bottom toolbar (edit mode): B I U S H1 H2 H3 • 1. ☐ ❝ <> { } Link Table.

## D/E. Database / dependencies
No schema change (still v1). No new dependencies. Version `0.3.0-m3`.

## J. Testing
### Run by Claude (2026-11-23)
- **81 JVM unit tests pass**, including 32 formatter tests and 20 parser tests (Bangla text, emoji, nested bold/italic, snake_case, tables, code blocks, list continuation, checkbox toggling, find).
- Whole-project syntax check: clean.
- Not run here: Android compile (GitHub Actions), 5 UI tests (need a device).

### Manual tests on your phone
| ID | Steps | Expected |
|---|---|---|
| M3-01 | Select a word → B | Becomes `**word**`; B again removes it |
| M3-02 | I, U, S, <> on a selection | `*x*`, `<u>x</u>`, `~~x~~`, `` `x` `` |
| M3-03 | No selection → B → type | Typing goes between the markers |
| M3-04 | Cursor on a line → H1 / H2 / H3 | `# `, `## `, `### ` prefix; same button again removes it |
| M3-05 | Select 3 lines → 1. | `1.` `2.` `3.` |
| M3-06 | Type `- milk` → Enter | Next line starts with `- `; Enter on empty `- ` ends the list |
| M3-07 | ☐ then Enter | New line starts with `- [ ] ` |
| M3-08 | Preview (eye icon) | Headings big, bold/italic/strike/underline styled, lists, quote bar, code box, table grid |
| M3-09 | Tap a checkbox in Preview | Ticks; back in Edit the text shows `- [x]`; autosaved |
| M3-10 | Link button → type URL → Preview → tap link | Opens browser |
| M3-11 | ⋮ → Find in note → type a word | Matches highlighted; `1/N`; arrows move between matches |
| M3-12 | ⋮ → Focus mode | Only text, status/navigation bars hidden; Back or ⤡ exits; text saved |
| M3-13 | Bangla text with formatting | Renders correctly; nothing lost |
| M3-14 | Undo after a toolbar action | Formatting step is undone |
| M3-15 | Long note (5,000+ words) | Typing stays smooth; preview opens within ~1 s |
| M3-16 | Close app, reopen note | Markdown text exactly as left |

## L. Known limitations
- Formatting is shown as Markdown symbols while editing; styling appears in Preview (no live WYSIWYG).
- Table column alignment is not applied in Preview. No images in notes yet (Milestone 6).
- Find works in Edit mode only and matches plain text (no regex/whole-word options).
- Nested lists render by indentation; block quotes do not contain lists.

## N. Next — Milestone 4
Folders (nested, colors, cycle-safe), tags, full-text search (SQLite FTS) with highlights and filters — first database migration (v1 → v2) with migration tests.
