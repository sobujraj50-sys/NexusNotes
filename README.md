# Nexus Notes — Personal Pro Edition

Offline-first, private notes, knowledge and AI-prompt library for Android. Kotlin · Jetpack Compose · Material 3.

**Current state: Milestone 2 of 10** — notes are saved on the phone (autosave, pin, favorites, archive, trash, colors, sorting, undo/redo). See `docs/ROADMAP.md`.

## Build with GitHub (no Android Studio needed)
1. Upload the project ZIP with **Add file › Upload files** in the repository root.
2. GitHub Actions unpacks it, runs the unit tests, builds a signed APK and publishes it under **Releases**.
3. Requires the repository secrets `KEYSTORE_BASE64` and `KEYSTORE_PASSWORD` so every APK is signed with the same key and installs as an update.

## Requirements
- Android Studio, latest stable release (it must support AGP 9.2; if it does not, update Studio first)
- Android SDK Platform **37** (SDK Manager → SDK Platforms) and Build-Tools 36.0.0
- JDK 17 or newer (Android Studio's bundled JDK is fine)
- An Android 8.0+ phone (USB debugging on) or an emulator

## Open and build in Android Studio
1. Unzip the project somewhere with a short path, e.g. `C:\dev\NexusNotes` or `~/dev/NexusNotes`.
2. Android Studio → **File › Open** → select the `NexusNotes` folder (the one containing `settings.gradle.kts`).
3. Trust the project. Wait for **Gradle sync** to finish (first sync downloads Gradle 9.4.1 and all libraries; needs internet once).
4. If prompted to install SDK 37, accept.
5. Select the `app` run configuration and your device → **Run ▶**.

## Command line
```bash
./gradlew assembleDebug              # APK → app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug               # install on connected device
./gradlew testDebugUnitTest          # JVM unit tests
./gradlew connectedDebugAndroidTest  # UI tests on device/emulator
```
On Windows use `gradlew.bat`.

Install a built APK manually: `adb install -r app/build/outputs/apk/debug/app-debug.apk`. The debug build's ID is `com.nexusnotes.debug`, so it can sit alongside a future release build.

## Troubleshooting
| Symptom | Fix |
|---|---|
| `Could not find androidx.xxx:yyy:N` | That version number is wrong or not published. Open its page on developer.android.com/jetpack/androidx/versions and put the latest stable number in `gradle/libs.versions.toml`. Change nothing else. |
| `The project is using an incompatible version (AGP 9.2.1)` / "newer AGP than supported" | Update Android Studio. |
| `Failed to find target with hash string 'android-37'` | SDK Manager → install Android API 37. |
| `Dependency requires compileSdk 37` | Already set; make sure you did not lower `compileSdk`. |
| Kotlin / Compose compiler version mismatch | `kotlin` in libs.versions.toml drives the Compose compiler plugin; keep a single Kotlin version. |
| `Unsupported class file major version` | Gradle JDK too new/old: Settings › Build Tools › Gradle › Gradle JDK → choose the bundled JBR or JDK 17+. |
| Sync hangs on "Downloading gradle-9.4.1" | Network/proxy issue; retry, or download the zip manually into `~/.gradle/wrapper/dists`. |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Uninstall the old app with the same ID, then reinstall. |
| UI tests fail with "No compose hierarchies found" | Unlock the device screen and turn off animations in Developer options. |

**When reporting a build error, paste the first error block from the Build window (the lines starting with `e:` or `FAILURE:`), not the whole log.**

## Documentation
- `docs/ARCHITECTURE.md` — feasibility, architecture, stack, database plan, design system
- `docs/ROADMAP.md` — the ten milestones
- `docs/MILESTONE_1.md` — what M1 contains, acceptance tests, limitations
- `docs/PROGRESS.md` — progress log and decisions
- `SECURITY.md` — threat model and privacy rules

## License
Personal use. All code and the icon are original; the few Material icon paths in `NexusIcons.kt` are from Google's Material Icons (Apache 2.0).
