# Milestone 9 — Reminders, Templates, Widget, Quick notes, Auto backup, Optional AI (v0.9.0-m9)

## What was built
- **Reminders**
  - Open a note › ⋮ › Reminder…. Quick picks (in 1 hour, tonight 8 PM, tomorrow 9 AM) or any date and time.
  - Repeat: once, daily, weekly, monthly. Monthly keeps "last day of month" (Jan 31 → Feb 28 → Mar 31). Repeats keep wall-clock time across daylight-saving changes.
  - The notification shows the note title only, and hides it on the lock screen. It has Snooze 10 min and Done; tapping it opens the note.
  - Reminders survive restart, app update, clock and time-zone changes. A one-time reminder missed while the phone was off shows soon after boot.
  - A note in Trash doesn't ring. Deleting a note forever removes its reminder.
  - Settings › Reminders lists all reminders and warns if notifications or exact alarms are off.
- **Templates**
  - Built-in: Checklist, Daily journal, Meeting notes, Shopping list, Project plan, Idea, AI prompt draft.
  - Make your own: ⋮ › Save as template.
  - Placeholders {{date}} {{time}} {{weekday}} {{datetime}} are filled in when the note is created.
  - Open them from Home › Template or Settings › Templates.
- **Quick notes**
  - Long-press the app icon for Quick note, New checklist, Search notes.
  - The widget's "+" also opens a new note.
- **Home-screen widget**
  - Shows up to 4 pinned or recent note titles, plus a "+" button.
  - Updates when notes change. It shows titles only, never note text or vault notes.
- **Automatic backup**
  - Choose a folder once. Backups then run daily or weekly via JobScheduler, when the battery isn't low.
  - Each one is verified, and only the newest 3/5/10 automatic copies are kept. Manual backups in the same folder are never deleted.
  - Vault notes are not included (they need the backup password).
- **Optional AI tools** (Settings › AI tools, off by default)
  - Actions: Summarize, Rewrite, Grammar, Translate, Titles, Organize, Improve prompt.
  - The app builds the prompt and **shows the exact text**. You choose to Copy it or Send it to an AI app.
  - **Nexus Notes has no INTERNET permission and embeds no API key**: nothing leaves the phone unless you send it. A direct-provider interface (`AiProvider`) exists in code, but none is shipped or pretended.
- Backups now also include templates and reminders. Older backups still restore.
- DB v7: `reminders` (FK to notes, cascade), `note_templates`.

## Permissions (all asked only when needed)
| Permission | Why | When |
|---|---|---|
| POST_NOTIFICATIONS (Android 13+) | Show reminders | When you save your first reminder |
| SCHEDULE_EXACT_ALARM | Ring on time | You can turn it off; reminders then may be a few minutes late (the app says so) |
| RECEIVE_BOOT_COMPLETED | Re-arm reminders/backup after restart | — |
| No INTERNET | — | The app still cannot connect anywhere |

## Automated tests (run locally: 199 unit tests pass)
- `ReminderScheduleTest` (8): future/past once, daily, weekly, end-of-month monthly, DST (New York), far past, labels.
- `NoteTemplatesTest` (3) and `AiPromptsTest` (5): placeholders, built-ins, prompt building/cutting/translation, auto-backup names and retention.
- Backup tests extended: templates/reminders round trip, old backups without them, reminders follow restored/copied notes.
- `MigrationTest` (device/CI): v1→v7, reminder cascade. v1→v7 was also checked on SQLite here.

## Manual tests
1. In a note: ⋮ › Reminder… › In 1 hour → change the time to 2 minutes from now (Time) → Save → allow notifications → the notification arrives; Snooze → it comes back in 10 min; tap → the note opens.
2. Daily reminder → restart the phone → still listed in Settings › Reminders with the next time.
3. Home › Template › Daily journal → note with today's date. Make your own via ⋮ › Save as template.
4. Long-press the app icon → Quick note / New checklist / Search.
5. Add the "Nexus Notes" widget → shows pinned/recent titles; tap a title → it opens; "+" → new note; edit a title → the widget updates.
6. Backup › Back up automatically → choose a folder → Back up now → a file `NexusNotes-auto-….zip` appears; with Keep 3, older auto files are removed after more runs.
7. Settings › AI tools on → note ⋮ › AI tools… → Summarize → Send to AI app → choose your AI app.
8. Update from 0.8.0 without uninstalling → all data present.

## Known limitations
- The widget shows titles only (no checklist ticking on the widget).
- Reminder times use the phone's time zone. Some phone makers' battery savers can delay or block alarms; allowing "Alarms & reminders" and excluding the app from battery optimisation helps.
- Automatic backups run when Android allows (JobScheduler), not at an exact time.
- AI tools hand text to another app. What that app does with it is under that app's privacy policy.
