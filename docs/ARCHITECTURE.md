# Nexus Notes — Architecture

Personal Pro Edition · living document · last updated Milestone 1 (2026-11-21)

## 1. Feasibility assessment

**Verdict: feasible at zero cost, as a single-module native app, built over ten milestones.**

| Area | Feasibility | Notes |
|---|---|---|
| Notes, folders, tags, trash | High | Standard Room + Compose work. |
| Offline full-text search | High | SQLite FTS4 through Room `@Fts4`. FTS5 is not exposed by Room annotations, so FTS4 is used. |
| Markdown editing + preview | High | Markdown as the stored format; preview rendered by an open-source parser (chosen and verified in M3). |
| WYSIWYG rich text | **Medium / risky** | Compose has no mature built-in rich-text editor. Plan: Markdown is the single source of truth; the toolbar inserts Markdown syntax; preview renders it. This avoids lossy rich-text↔Markdown conversion (spec 3.B). Underline and tables are Markdown extensions and will be documented. |
| Prompt library + templates | High | Pure Kotlin substitution engine, fully unit-testable. |
| Attachments, audio | High | Photo Picker, SAF, MediaRecorder; files in app-private storage. |
| Encrypted vault | Medium | Achievable with Keystore + AES-GCM + PBKDF2/Argon2 — needs careful design and tests (M7). Highest-risk milestone. |
| Backup/restore/export | High | ZIP + JSON manifest + checksums via SAF. PDF via `android.graphics.pdf.PdfDocument`. |
| Reminders, widget | High | AlarmManager/WorkManager + Glance or RemoteViews (decided in M9). |
| Optional AI | High (as an interface) | No provider bundled; user supplies their own key at runtime, opt-in only. |
| Cost | Zero | All tools and libraries are free/open source. Play Store publishing ($25) is not required — sideload the APK. |

Main risks: (1) rich-text expectations — mitigated by the Markdown-first model; (2) vault crypto correctness — mitigated by a written threat model and dedicated tests; (3) solo maintenance — mitigated by keeping one Gradle module and few dependencies.

## 2. Recommended architecture

- **Single `:app` module**, packages split by layer and feature. Multi-module adds build complexity with no benefit for one developer. Revisit only if build times hurt.
- **MVVM + unidirectional data flow.** Screen ← `StateFlow<UiState>` ← ViewModel ← Repository ← DAO/Files.
- **Clean-ish layering**, kept light:
  - `data/` — Room entities, DAOs, repository implementations, file storage.
  - `domain/` — plain Kotlin models and the few use cases with real logic (template substitution, folder-cycle check, backup validation). No use case classes that only forward calls.
  - `feature/` — Compose screens + ViewModels.
  - `core/` — design system, security, database setup, shared utilities.
- **Manual dependency injection** through `AppContainer` (decided in M2). Hilt was dropped: its Gradle plugin is the riskiest piece on AGP 9 and the dependency graph is small. Revisit if the graph grows.
- **Coroutines + Flow** everywhere; Room queries return `Flow`; writes are `suspend` on `Dispatchers.IO` (Room does this automatically for suspend DAOs).
- **Navigation Compose with string routes** (no serialization plugin needed). IDs in routes are UUID strings.

## 3. Technology stack (Milestone 1)

Checked against official release pages on 2026-11-21.

| Component | Version | Source / status |
|---|---|---|
| Android Gradle Plugin | 9.2.1 | developer.android.com AGP 9.2 release notes. Newer 9.4/9.5 exist; 9.2.1 chosen because its Gradle/JDK/KGP table is documented. Android Studio's AGP Upgrade Assistant can move it forward. |
| Gradle | 9.4.1 | Minimum and default for AGP 9.2 (same page). |
| JDK | 17 | Minimum for AGP 9.2. Android Studio's bundled JBR (21) also works. |
| Kotlin (+ Compose compiler plugin) | 2.3.20 | kotlinlang.org (released March 2026). 2.4.20 is the newest stable; 2.3.20 chosen as the conservative match for AGP 9.2's KGP dependency. |
| Compose BOM | 2026.08.00 | Android Developers Blog, Aug 2026. Requires compileSdk 37 and AGP ≥ 9.2.0. |
| compileSdk / targetSdk / minSdk | 37 / 36 / 26 | compileSdk 37 forced by Compose 1.12. targetSdk 36 meets current Play requirements; raise to 37 in M10 after testing Android 17 behavior changes. minSdk 26 (Android 8.0) gives java.time, adaptive icons and modern Keystore. |
| androidx.core:core-ktx | 1.19.1 | AndroidX versions page |
| androidx.activity:activity-compose | 1.13.0 | AndroidX versions page |
| androidx.lifecycle | 2.11.1 | AndroidX versions page |
| androidx.navigation:navigation-compose | 2.10.2 | AndroidX versions page |
| compose material-icons-core | 1.7.8 | Pinned (no longer advanced by the BOM). Icons missing from core are drawn in `NexusIcons`. |
| JUnit 4 / androidx.test.ext:junit / Espresso | 4.13.2 / 1.3.0 / 3.7.0 | Stable releases |

**Needs confirming on your machine:** the AndroidX numbers came from a summarized read of the versions page. If Gradle sync reports "Could not find …", change only that line in `gradle/libs.versions.toml` to the version shown on its release page.

**Added in Milestone 2:** Room 2.8.5 (+ room-ktx, compiler via KSP 2.3.12), DataStore Preferences 1.3.0, lifecycle-viewmodel-compose/savedstate 2.11.1.

**Added in later milestones (versions verified when added):** WorkManager (M8/M9), Biometric (M7), a Markdown renderer (M3).

## 4. Directory structure

```
NexusNotes/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/nexusnotes/
│       │   │   ├── NexusNotesApp.kt
│       │   │   ├── MainActivity.kt
│       │   │   ├── core/
│       │   │   │   ├── common/        Greeting.kt
│       │   │   │   ├── database/      (M2)
│       │   │   │   ├── design/
│       │   │   │   │   ├── components/  NexusScreen.kt, Components.kt
│       │   │   │   │   ├── icons/       NexusIcons.kt
│       │   │   │   │   └── theme/       Color, Theme, Type, Shape, Spacing
│       │   │   │   ├── security/      (M7)
│       │   │   │   └── storage/       (M6)
│       │   │   ├── data/              (M2: entity/, dao/, repository/)
│       │   │   ├── domain/model/      PromptCategory.kt
│       │   │   ├── feature/
│       │   │   │   ├── backup/  editor/  folders/  home/  notes/
│       │   │   │   ├── prompts/ search/  settings/ tags/  vault/
│       │   │   └── navigation/        NexusDestination, NexusNavHost, NexusNotesRoot
│       │   └── res/  (values, values-night, xml, drawable, mipmap-anydpi-v26)
│       ├── test/          JVM unit tests
│       └── androidTest/   Compose UI tests
├── docs/   ARCHITECTURE.md, ROADMAP.md, MILESTONE_1.md, PROGRESS.md
├── gradle/ libs.versions.toml, wrapper/
├── build.gradle.kts, settings.gradle.kts, gradle.properties
├── README.md
└── SECURITY.md
```

Deviation from the brief: `feature/tags/` added (Tags is its own screen).

## 5. Database architecture overview (implemented from M2)

Room database `nexus.db`, `exportSchema = true`, schemas committed under `app/schemas/` for migration tests. All IDs are UUID strings (`TEXT PRIMARY KEY`). Timestamps are epoch milliseconds (UTC).

| Entity | Key columns | Relations / constraints |
|---|---|---|
| NoteEntity (**built: v1 M2, folderId v2 M4**) | id, title, body (Markdown), bodyFormatVersion, folderId?, colorIndex?, isPinned, isFavorite, isArchived, deletedAt?, createdAt, updatedAt | FK folderId → Folder (SET NULL). Indexes: folderId, updatedAt, (isArchived, deletedAt). Trash = `deletedAt IS NOT NULL` (soft delete). |
| NoteFtsEntity (**built v2**) | title, body | `@Fts4(contentEntity = NoteEntity)`; Room creates sync triggers. Vault notes are never in NoteEntity, so never indexed. |
| FolderEntity (**built v2**) | id, name, parentId?, colorIndex?, createdAt | No SQL FK; FolderDao deletes subtrees and unfiles their notes in one transaction. FolderTree blocks loops; max depth 8; sibling names unique (case-insensitive, enforced in code). |
| TagEntity (**built v2**) | id, name | Unique index on name; case-insensitive uniqueness enforced in TagDao. |
| NoteTagCrossRef (**built v2**) | noteId, tagId | Composite PK; both FKs CASCADE; index on tagId. |
| AttachmentEntity | id, noteId, type, fileName (internal), displayName, mimeType, sizeBytes, sha256, createdAt | FK noteId CASCADE; files live in `filesDir/attachments/`, never as blobs. |
| PromptEntity (**built v3**) | id, title, template, categoryKey, isFavorite, createdAt, updatedAt | categoryKey = `PromptCategory.name` or custom category id. FTS table for prompts too. |
| PromptCategoryEntity (**built v3**) | id, name, createdAt | User-defined categories (built-ins come from the enum). |
| PromptVariableEntity (**built v3**) | promptId, name, defaultValue | FK CASCADE; unique (promptId, name). |
| PromptVersionEntity (**built v3**) | id, promptId, title, template, createdAt | Prompt history (5-min coalescing, 30 kept). Note history planned later. |
| ReminderEntity | id, noteId, triggerAt, repeatRule?, enabled | FK CASCADE. Notification text never shows vault or note body content. |
| VaultItemEntity | id, ciphertext, iv, keyVersion, createdAt, updatedAt | **Separate encrypted table**: title+body+metadata encrypted together as one AES-GCM blob. No plaintext columns. |
| BackupHistoryEntity | id, createdAt, destinationLabel, itemCount, sha256, schemaVersion, result | Informational only. |

Rules: no `fallbackToDestructiveMigration()` in release builds; every schema bump ships a `Migration` and a `MigrationTestHelper` test; multi-table writes in `@Transaction`.

## 6. Premium UI design system

Implemented in `core/design/`.

**Color (dark-first, Material 3 roles).** Dark: background `#0B1020`, surface containers `#111729 → #242D45`, primary violet `#8B5CF6`, secondary cyan `#22D3EE`, tertiary emerald `#34D399`, text `#F8FAFC` / `#94A3B8`. Light theme mirrors these with deeper accents (`#6D28D9`, `#0E7490`) for contrast on `#F6F7FB`. Dynamic color supported on Android 12+, off by default. Screens only use `MaterialTheme.colorScheme` roles, never raw hex.

**Typography.** Platform sans-serif (no bundled font files). Headlines SemiBold with slight negative tracking; body sizes are Material defaults for readability; respects the system font scale.

**Shape.** 6 / 10 / 16 / 22 / 28 dp corner scale. Cards use `large` (22 dp), list rows `medium` (16 dp), search bar fully rounded.

**Spacing.** 4 dp scale via `MaterialTheme.spacing` (xs 4, sm 8, md 12, lg 16, xl 24, xxl 32; screen margin 20; FAB clearance 96).

**Components.** `NexusScreen` (top bar + back + FAB frame), `EmptyState`, `CenteredEmptyState`, `InlineEmpty`, `PlannedFeatureCard`, `MilestoneBadge`, `NexusListRow`, `SectionHeader`, `SearchLauncher`.

**Honesty rule.** Every feature that does not exist yet carries a `MilestoneBadge`; its controls are disabled or not clickable. No control pretends to work.

**Motion.** 220 ms fade + small horizontal slide between screens.

**Accessibility.** All icon-only buttons have content descriptions; decorative icons use `null`; touch targets ≥ 48 dp (Material defaults); text scales with system settings; color is never the only signal (badges have text).
