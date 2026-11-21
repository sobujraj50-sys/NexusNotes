# Milestone 1 — Foundation

## A. Objective
A buildable Android project with the final design system, working navigation and all 18 primary screens as honest shells, so later milestones only add behavior.

## B. Technical design
- Single `:app` module, Kotlin + Jetpack Compose + Material 3.
- `NexusTheme` provides color scheme, typography, shapes and spacing.
- `NexusNotesRoot` hosts a root `Scaffold` with a bottom `NavigationBar`, shown only on the four top-level routes.
- `NexusNavHost` registers every destination with string routes; optional arguments (`noteId`, `promptId`) are nullable query parameters.
- Tab switches use `popUpTo(start) { saveState } + restoreState + launchSingleTop`.
- No ViewModels yet: no screen has state worth holding beyond Compose `rememberSaveable` (search text).

## C. UI specification
| # | Screen | Route | M1 behavior |
|---|---|---|---|
| 1 | Splash | system | Android 12+ system splash with themed background and the app icon. Older versions show the themed window background. |
| 2 | Home | `home` | Greeting, search launcher, quick actions, Prompt Library card, Pinned/Recent/Favorites empty states, collection chips, Quick note FAB |
| 3 | All Notes | `notes` | Empty state, search action, New note FAB |
| 4 | Note Editor | `editor?noteId=` | Layout preview, inputs disabled (no storage yet), formatting row disabled with M3 badge |
| 5 | Folders | `folders` | Library rows (All notes, Unfiled, Tags) + planned folders card |
| 6 | Folder Details | `folders/{folderId}` | Reached through "Unfiled"; empty state |
| 7 | Tags | `tags` | Empty state |
| 8 | Prompt Library | `prompts` | 11 categories listed, intro card, New prompt FAB |
| 9 | Prompt Editor | `prompts/editor?promptId=` | Read-only fields, opens Template Runner |
| 10 | Template Runner | `prompts/run?promptId=` | Explains the substitution behavior, example template |
| 11 | Search | `search` | Working text field with auto-focus; states nothing is indexed yet |
| 12 | Favorites | `favorites` | Empty state |
| 13 | Archive | `archive` | Empty state |
| 14 | Trash | `trash` | Empty state |
| 15 | Private Vault | `vault` | States plainly that nothing is encrypted yet; how it will work; recovery limits |
| 16 | Backup & Restore | `backup` | Planned actions, all badged M8 |
| 17 | Settings | `settings` | Working links to Tags, Archive, Trash, Vault, Backup, About; planned settings badged |
| 18 | About | `about` | Version from BuildConfig, privacy promises, build status |

## D. Database changes
None. Room arrives in Milestone 2.

## E. Dependency changes
New project. See `docs/ARCHITECTURE.md` §3 and `gradle/libs.versions.toml`.

## F. Files
See the directory tree in `docs/ARCHITECTURE.md` §4. Every file is complete; nothing is omitted.

## J. Testing checklist

### What was actually run (by Claude, 2026-11-21)
- **JVM unit tests — 10 tests, all passed** (`NexusDestinationTest`, `PromptCategoryTest`, `GreetingTest`). Compiled and run with Kotlin 2.0.21 + JUnit 4.13.2 outside Gradle, because the build machine has no Android SDK or internet.
- **Not run:** the Gradle build, the Android/Compose code compile, lint, and the instrumented UI tests. These need Android Studio and the Android SDK. Treat the APK build as unverified until you report back.

### Automated tests for you to run
```
./gradlew testDebugUnitTest          # 10 JVM tests
./gradlew connectedDebugAndroidTest  # 5 Compose UI tests, needs a device or emulator
```
UI tests in `NavigationTest`: app starts on Home; bottom nav reaches all 4 tabs; Quick note opens a read-only editor and back returns Home; Settings → About → back; Folders → Unfiled.

### Manual device acceptance tests
| ID | Steps | Expected |
|---|---|---|
| M1-01 | Install debug APK, launch | Splash, then Home with greeting matching the time of day |
| M1-02 | Tap each bottom tab | Correct title each time; selected tab highlighted |
| M1-03 | Folders tab → scroll → Home tab → Folders tab | Folders keeps its scroll position (state restored) |
| M1-04 | Home → Quick note → system Back | Returns to Home, bottom bar visible again |
| M1-05 | Visit every screen in the table above | All 18 reachable; nothing crashes |
| M1-06 | Try typing in the note editor | Fields are disabled; notice explains why |
| M1-07 | Search: type text, rotate the phone | Text survives rotation; clear (×) button empties it |
| M1-08 | Switch system dark/light mode | App follows; text readable in both |
| M1-09 | Settings → Accessibility → largest font size | Text scales; no clipped or overlapping text |
| M1-10 | TalkBack on, explore Home | Icon buttons announce "Search", "Navigate back"; badges announce "Planned for milestone N" |
| M1-11 | Gesture navigation and 3-button navigation | Content not hidden behind bars; edge-to-edge looks correct |
| M1-12 | App info → Permissions | "No permissions requested" |
| M1-13 | Home screen long-press icon / themed icons (Android 13+) | Adaptive and monochrome icon render |
| M1-14 | Rotate on several screens; split-screen | No crash; layout adapts |
| M1-15 | Background app 5 min, return | Same screen restored |

### Failure scenarios to check
- Back pressed rapidly several times on a secondary screen → returns step by step, never leaves a blank screen.
- Process death (Developer options → "Don't keep activities"): reopen → app restores navigation stack.

## K. Troubleshooting
See README.md → Troubleshooting.

## L. Known limitations
- No data is stored anywhere; editor and prompt editor are read-only previews.
- Strings are hard-coded in Kotlin; moved to `strings.xml` (and Bangla translation, if wanted) in M10.
- Theme and dynamic-color settings are not user-configurable until DataStore (M2).
- FolderDetail is only reachable via the virtual "Unfiled" folder until real folders exist (M4).
- Build and instrumented tests not executed by Claude (no Android SDK available).
- Release build is unsigned and unminified until M10.

## M. Completion criteria
1. `./gradlew assembleDebug` succeeds on your machine.
2. Unit tests and UI tests pass on your device/emulator.
3. Manual tests M1-01 … M1-15 pass, or failures are reported for fixing.
4. You approve the look and the navigation structure.

## N. Next milestone preview — Milestone 2
Room database (NoteEntity, schema v1 exported), Hilt, DataStore settings (theme, dynamic color), repositories and ViewModels, working note creation with debounced autosave, pin/favorite/archive/trash/restore/permanent delete, duplicate, color labels, sort options, recent notes, word count and undo/redo. Adds migration-test scaffolding and persistence tests (kill app, restart device).
