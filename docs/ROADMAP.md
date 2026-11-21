# Ten-milestone roadmap

Each milestone ends with a buildable APK, its tests, and a stop for your approval.

| # | Milestone | Main deliverables | Exit criteria |
|---|---|---|---|
| 1 | Foundation | Gradle project, design system, theme (dark/light), bottom nav + 18 screens as honest shells, unit + UI tests | Debug APK installs; all screens reachable; tests pass on your device |
| 2 | Database + note CRUD | Room DB, Hilt, DataStore; NoteEntity; create/edit/autosave (debounced), pin, favorite, archive, trash/restore, permanent delete with confirm, duplicate, color labels, sorting, recent, word count, undo/redo; theme setting | Notes survive app kill and device restart; schema exported |
| 3 | Editor | Markdown-first editor: toolbar inserting syntax (bold, italic, underline ext., strike, headings, lists, checklists, quotes, links, code, tables), live preview, find-in-note, focus mode; content format version 1 documented | Round-trip tests: save → reload is byte-identical; cursor/selection tests |
| 4 | Organization + search | Folders (nested, colors, cycle prevention), tags (many-to-many), filters, FTS4 search with highlights, suggestions, optional history | Folder-cycle and FTS-sync tests; search over 10k notes < 200 ms on device |
| 5 | Prompt Library | Prompts, categories, favorites, copy, duplicate, version history, `{{variable}}` engine, Template Runner, JSON import/export | Substitution engine fully unit-tested; source template never modified |
| 6 | Attachments | Photo Picker images, audio recording, SAF files, previews, size limits, share-to-notes, missing-file recovery | No broad storage permissions; orphan-file cleanup tested |
| 7 | Private vault | PIN + key derivation, Keystore wrapping, AES-GCM item encryption, biometric unlock, auto-lock, FLAG_SECURE, encrypted attachments | Wrong PIN, biometric failure, key loss and lifecycle tests pass; threat model updated |
| 8 | Backup + export | Versioned ZIP backup with manifest + SHA-256, validated restore with duplicate handling, encrypted vault backup, TXT/MD/JSON/PDF export | Corrupt-backup rejection test; restore never overwrites without confirmation |
| 9 | Productivity + AI interface | Reminders (exact/inexact, recurring), home-screen widget, templates, quick note; optional AI provider interface (off by default, explicit per-use consent) | Notification permission requested only when needed; AI disabled = zero network |
| 10 | Hardening + release | R8 + keep rules, baseline profile, performance measurements, accessibility pass, security review, string resources, signed release APK | Signed APK; measured startup and scroll targets met on your device |
