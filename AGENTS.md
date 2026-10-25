# Repository Guidelines

## Project Structure & Module Organization

This is a single-module Android app in `app/` (namespace and Play application ID `com.comicviewer`). Kotlin sources live under `app/src/main/java/com/comicviewer/`:

- Root package: domain, storage, and archive code such as `ComicLoader.kt`, `LibraryArchiveScanner.kt`, `StorageAccess.kt`, `DocumentAccess.kt`, `AppPreferences.kt`, and `AppBackup.kt`.
- Archives are read in place, never extracted: `ZipArchiveReader.kt` does random-access ZIP/ZIP64 reads, `ComicArchiveAccess.kt` opens files and picked documents (copying only when a provider cannot seek), and reader pages are `comic-archive:` URIs that `ComicArchivePageFetcher` streams through Coil (registered in `ComicViewerApplication`).
- PDFs use the same library entries as archives. `ComicFileAccess.kt` opens any comic file once, decides ZIP or PDF from its first bytes, and keeps the reader's open files in `ComicFileSessions`; `PdfComicAccess.kt` wraps the platform `PdfRenderer`, and PDF pages are `comic-pdf:` URIs that `ComicPdfPageFetcher` renders at the requested size. The paged reader requests pages at the viewport size times a zoom-dependent detail step (see `ReaderPageScale.kt`). Device-wide auto discovery skips PDFs, since they cannot be told apart from ordinary documents; folder scans of a chosen import folder include them.
- `billing/`: the Pro in-app purchase flow (`ProBillingRepository`, `ProPurchaseAcknowledgeWorker`).
- `ui/`: Compose UI by feature: `app` (root scaffold, the Library, History and Settings tabs, app state), `library` (library home with search and the Add sheet, comic details), `reader` (the `ReaderScreen` shell, `PagedReader` and `VerticalReader` sharing `ReaderChrome`, `ReaderPageGrid`, and the paging and zoom math in `ReaderPaging` and `ReaderPageScale`), `browser` (import file browsers), `history`, `settings`, `welcome`, plus shared `common` components and `theme`. `MainActivity.kt` is the entry point; `ComicViewerApplication` registers the archive and PDF page fetchers with Coil.

Unit tests are in `app/src/test/`, one class per area (`ComicArchiveTest`, `LibraryScanTest`, `ReaderGestureTest`, ...) with shared fixtures in `TestFixtures.kt`; instrumented tests in `app/src/androidTest/`, and resources in `app/src/main/res/`. `scripts/play_publish.py` publishes releases to Google Play, and `scripts/play_listing.py` updates the store listing texts and screenshots. `new-gui-images/` contains early visual references; `play-store-dashboard-assets/` contains upload-ready store artwork, the current screenshots and the icon sources.

## Toolchain

Gradle 9.6 wrapper, AGP 9.3 with built-in Kotlin (only the `kotlin.compose` plugin is applied; do not add `org.jetbrains.kotlin.android`), compileSdk 36.1, targetSdk 36, minSdk 24. Declare dependency and plugin versions in `gradle/libs.versions.toml`, not inline. Version code and name are the `appVersionCode` / `appVersionName` values at the top of `app/build.gradle.kts`.

## Build, Test, and Development Commands

- `./gradlew assembleDebug` builds the debug APK and validates Kotlin/Android compilation.
- `./gradlew testDebugUnitTest` runs local JUnit tests.
- `./gradlew connectedDebugAndroidTest` runs tests on a connected emulator or device.
- `./gradlew bundleRelease` generates the signed release AAB in `app/build/outputs/bundle/release/` and the R8 mapping in `app/build/outputs/mapping/release/`; signing requires a valid local `keystore.properties` (see `keystore.properties.example`).
- `./gradlew clean` removes generated outputs when Gradle state is stale.

Use Android Studio for emulator launch and manual UI checks. GitHub Actions (`android-ci.yml`) runs unit tests, lint, the debug build, an unsigned minified release build and the instrumented-test compile on pushes to `master` and on pull requests; release signing only applies when `keystore.properties` exists. Pushing a `v*` tag runs `github-release.yml`, which attaches a debug APK to a GitHub release; it does not publish to Google Play. Tag each version that goes to production as `vX.Y.Z`.

## Coding Style & Naming Conventions

Use Kotlin with 4-space indentation and idiomatic Compose patterns. Prefer immutable `val`, small focused functions, and feature-local UI files. Name composables with PascalCase nouns (`LibraryScreen`, `ReaderTopBar`) and helpers with clear verbs or domain phrases (`loadLocalArchiveComic`). Put user-facing text in `res/values/strings.xml` and keep `values-it/` translations aligned. Avoid unrelated refactors.

## UI & Store Asset Fidelity

Before and during GUI work, inspect the relevant files in `new-gui-images/` and compare emulator captures against them. Preserve navigation, storage, reader, billing, and import behavior unless a feature change is explicitly requested. Do not edit the reference images. Keep Play Store screenshots, icon, and feature graphic within the dimensions documented in `play-store-dashboard-assets/README.md`; use only original or licensed demo artwork.

## Testing Guidelines

Unit tests use JUnit 4. Add tests for pure helpers, persistence encoding/decoding, sorting, cache keys, and boundary behavior. Use descriptive `functionName_expectedBehavior` names such as `upsertLibraryComic_dedupesAndKeepsRecentlyAddedFirst`. Run `./gradlew testDebugUnitTest assembleDebug` before handoff, plus device tests when Compose interaction or storage access changes. Release builds are minified with R8 and resource shrinking, so also run `./gradlew bundleRelease` after adding libraries, reflection, or serialization, and add keep rules to `app/proguard-rules.pro` when needed.

## Releasing to Google Play

1. Bump `appVersionCode` and `appVersionName` in `app/build.gradle.kts`. The version code must be higher than every code already uploaded to any track (internal, alpha, beta, production); Play never accepts a reused code. `scripts/play_publish.py --key <key.json> --check` lists what is on each track without changing anything.
2. Run `./gradlew testDebugUnitTest bundleRelease`.
3. Validate first: `scripts/play_publish.py --key <key.json> --track production --notes en-US=<file> --notes it-IT=<file> --dry-run`. It uploads the AAB and R8 mapping, has Play validate the edit, then discards it.
4. Rerun without `--dry-run` to publish. Use `--rollout 0.2` for a staged rollout or `--draft` for a draft release. To put an already uploaded build on another track (for example `internal`), pass `--version-code <code>` instead of uploading it again. Release notes are limited to 500 characters per language; `en-US` is the default listing language and the fallback for other listing languages.
5. Commit the version bump.

Publishing reaches real users: run the script without `--dry-run` only when the user has explicitly asked for that release, track, and rollout.

## Commit & Pull Request Guidelines

Use short imperative commit subjects, matching history such as `Overhaul comic viewer interface` and `Fix GUI overhaul regressions`. Don't add `Co-Authored-By`, session links or other AI attribution lines to commit messages or pull request descriptions. Pull requests should include a concise summary, validation commands, screenshots or recordings for UI changes, and notes about storage, permissions, billing, or cache behavior. Do not push or create tags unless asked.

## Reporting to the User

Keep replies short and easy to scan:

- Open with one sentence giving the outcome: what was done and whether it worked.
- Follow with at most five bullets, one fact each, using bold labels such as **Tests:**, **Play:**, **GitHub:**.
- Put anything the user must do on its own line labeled **Needs you:**.
- End with at most one suggested next step or question.
- Use plain words before identifiers. Include a path, commit hash or run ID only when the user needs it to act, and put it in backticks.
- Use a small table to compare several items, such as tracks, versions or files.
- Don't restate the request or narrate each step. Give details only when asked or when something failed.

## Security & Configuration Tips

Preserve SAF access controls, archive bounds (entry count, per-page size, inflated output capped at the declared entry size), PDF bounds (page count, rendered bitmap size), image header validation, hidden-path filtering, free-space checks for fallback copies, and cache cleanup behavior. Never commit `keystore.properties`, signing keys, generated build outputs, IDE state, or device-specific paths. The Google Play service account JSON key stays outside the repository (`.gitignore` does not exclude `.json` files); pass its path to the publish script, and never copy it into the project or print its contents.
