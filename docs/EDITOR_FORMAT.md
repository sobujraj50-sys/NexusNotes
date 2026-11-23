# Note content format — Nexus Markdown v1

`NoteEntity.bodyFormatVersion = 1`. The body is stored **exactly as typed** (plain UTF-8 text). The app never converts it to another format, so saving and reopening is always byte-identical and nothing is lost when exporting to Markdown.

## Supported syntax
| Element | Syntax | Notes |
|---|---|---|
| Bold | `**text**` or `__text__` | |
| Italic | `*text*` or `_text_` | `_` inside words (snake_case) is not italic |
| Bold + italic | `***text***` | |
| Strikethrough | `~~text~~` | GitHub-style |
| Underline | `<u>text</u>` | HTML tag — Markdown has no underline; GitHub and most editors render it |
| Inline code | `` `code` `` | |
| Headings | `# H1`, `## H2`, `### H3` (up to 6) | A space after `#` is required (`#hashtag` stays text) |
| Bulleted list | `- item` (also `*`, `+`) | Indent 2 spaces per level |
| Numbered list | `1. item` or `1) item` | |
| Checklist | `- [ ] todo`, `- [x] done` | Tap the box in Preview to toggle |
| Quote | `> text` | |
| Code block | ```` ``` ```` fences, optional language | Content shown verbatim |
| Link | `[label](https://…)` | Only `https`, `http`, `mailto` open; others shown as text |
| Table | GitHub pipe tables with a `---` separator row | Alignment colons accepted, not yet applied |
| Rule | `---`, `***`, `___` | |
| Escape | `\*` etc. | |

Anything else is shown as plain text — never hidden or deleted.

## Editor behavior
- Toolbar buttons only insert/remove the syntax above around the selection or on the selected lines; pressing again removes it.
- Enter inside a list continues it; Enter on an empty item ends the list.
- Preview renders the same text; it does not modify the note except when you tick a checkbox.

## Versioning
A future incompatible change (e.g. new storage of rich spans) would use `bodyFormatVersion = 2` plus a migration that converts v1 bodies. v1 bodies must always remain readable.
