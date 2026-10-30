# Repository Guidelines

## Project Structure & Module Organization

This is a single-module Android app in `app/` (namespace and Play application ID `com.comicviewer`). Kotlin sources live under `app/src/main/java/com/comicviewer/`:

- Root package: domain, storage, and archive code such as `ComicLoader.kt`, `LibraryArchiveScanner.kt`, `StorageAccess.kt`, `DocumentAccess.kt`, `AppPreferences.kt`, and `AppBackup.kt`.
- Archives are read in place, never extracted: `ZipArchiveReader.kt` does random-access ZIP/ZIP64 reads, `ComicArchiveAccess.kt` opens files and picked documents (copying only when a provider cannot seek), and reader pages are `comic-archive:` URIs that `ComicArchivePageFetcher` streams through Coil (registered in `ComicViewerApplication`).
- PDFs use the same library entries as archives. `ComicFileAccess.kt` opens any comic file once, decides ZIP or PDF from its first bytes, and keeps the reader's open files in `ComicFileSessions`; `PdfComicAccess.kt` wraps the platform `PdfRenderer`, and PDF pages are `comic-pdf:` URIs that `ComicPdfPageFetcher` renders at the requested size. Both readers request pages at the screen size times a zoom-dependent detail step (see `ReaderPageScale.kt`); the vertical reader zooms by laying its strip out wider, so the list still scrolls over whole pages. Device-wide auto discovery only adds PDFs that look like comics (at least 8 pages and about 150 KB or more per page, see `pdfLooksLikeComic`), since most PDFs on a device are ordinary documents; folder scans of a chosen import folder include every PDF.
- `billing/`: the Pro in-app purchase flow (`ProBillingRepository`, `ProPurchaseAcknowledgeWorker`).
- `ui/`: Compose UI by feature: `app` (root scaffold, the Library, History and Settings tabs, app state), `library` (library home with search and the Add sheet, comic details), `reader` (the `ReaderScreen` shell, `PagedReader` and `VerticalReader` sharing `ReaderChrome`, `ReaderPageGrid`, and the paging and zoom math in `ReaderPaging` and `ReaderPageScale`), `browser` (import file browsers), `history`, `settings`, `welcome`, plus shared `common` components and `theme`. `MainActivity.kt` is the entry point; `ComicViewerApplication` registers the archive and PDF page fetchers with Coil.

Unit tests are in `app/src/test/`, one class per area (`ComicArchiveTest`, `LibraryScanTest`, `ReaderGestureTest`, ...) with shared fixtures in `TestFixtures.kt`; instrumented tests in `app/src/androidTest/`, and resources in `app/src/main/res/`.

Outside the app:

- `fastlane/metadata/android/`: the Google Play listing as it is live (title, short and full description per language), the store images under `en-US/images/`, and Play release notes per version code in `<language>/changelogs/<versionCode>.txt`. See `docs/store-listing.md`.
- `scripts/`: `play_publish.py` publishes builds and reads the release notes from the fastlane changelogs, `play_listing.py` uploads the listing texts and screenshots, and `changelog_section.py` prints a version's `CHANGELOG.md` section for GitHub releases.
- `assets/`: original demo artwork used in the screenshots, and the icon source.
- `docs/`: `RELEASING.md` (the release process) and `store-listing.md`.
- Root documents: `README.md`, `CHANGELOG.md` (Keep a Changelog), `CONTRIBUTING.md`, `SECURITY.md`, `LICENSE` (proprietary, all rights reserved), plus `.editorconfig` and `.gitattributes`.

## Toolchain

Gradle 9.8 wrapper, AGP 9.5 with built-in Kotlin (only the `kotlin.compose` plugin is applied; do not add `org.jetbrains.kotlin.android`), compileSdk 37, targetSdk 36, minSdk 24. Declare dependency and plugin versions in `gradle/libs.versions.toml`, not inline. Version code and name are the `appVersionCode` / `appVersionName` values at the top of `app/build.gradle.kts`.

## Build, Test, and Development Commands

- `./gradlew assembleDebug` builds the debug APK and validates Kotlin/Android compilation.
- `./gradlew testDebugUnitTest` runs local JUnit tests.
- `./gradlew connectedDebugAndroidTest` runs tests on a connected emulator or device.
- `./gradlew bundleRelease` generates the signed release AAB in `app/build/outputs/bundle/release/` and the R8 mapping in `app/build/outputs/mapping/release/`; signing requires a valid local `keystore.properties` (see `keystore.properties.example`).
- `./gradlew clean` removes generated outputs when Gradle state is stale.

Use Android Studio for emulator launch and manual UI checks. GitHub Actions (`android-ci.yml`) runs only the unit tests on pushes to `main` and on pull requests, to keep them quick; started by hand from the Actions tab it also runs lint, the debug build, an unsigned minified release build and the instrumented-test compile. Run lint and the release build locally before committing and releasing. Release signing only applies when `keystore.properties` exists. `device-tests.yml` runs the device tests on an API 35 emulator only when started by hand from the Actions tab. Pushing a `v*` tag runs `github-release.yml`, which creates a GitHub release with the version's changelog section as notes and no APK (debug builds can unlock Pro, so they are never published); it does not publish to Google Play. Tag each version that goes to production as `vX.Y.Z`.

## Coding Style & Naming Conventions

Use Kotlin with 4-space indentation and idiomatic Compose patterns. Prefer immutable `val`, small focused functions, and feature-local UI files. Name composables with PascalCase nouns (`LibraryScreen`, `ReaderTopBar`) and helpers with clear verbs or domain phrases (`loadLocalArchiveComic`). Put user-facing text in `res/values/strings.xml` and keep `values-it/` translations aligned. Avoid unrelated refactors.

## UI & Store Asset Fidelity

Check GUI changes in emulator captures against the current screens and the store screenshots. Preserve navigation, storage, reader, billing, and import behavior unless a feature change is explicitly requested. Keep Play Store screenshots, icon, and feature graphic within the dimensions documented in `docs/store-listing.md`; use only original or licensed demo artwork, such as `assets/demo-art/`, and never commit third-party comic art: the repository is public.

## Testing Guidelines

Unit tests use JUnit 4. Add tests for pure helpers, persistence encoding/decoding, sorting, cache keys, and boundary behavior. Use descriptive `functionName_expectedBehavior` names such as `upsertLibraryComic_dedupesAndKeepsRecentlyAddedFirst`. Run `./gradlew testDebugUnitTest assembleDebug` before handoff, plus device tests when Compose interaction or storage access changes. Release builds are minified with R8 and resource shrinking, so also run `./gradlew bundleRelease` after adding libraries, reflection, or serialization, and add keep rules to `app/proguard-rules.pro` when needed.

## Releasing to Google Play

Follow `docs/RELEASING.md`. In short:

1. Every user-facing change gets a line in the `Unreleased` section of `CHANGELOG.md`, written for users.
2. For an internal test build, raise `appVersionCode` (higher than any code on any track; `scripts/play_publish.py --key <key.json> --check` lists them) and `appVersionName` (Semantic Versioning: minor for features, patch for fixes), write `fastlane/metadata/android/{en-US,it-IT}/changelogs/<versionCode>.txt` (at most 500 characters), run `./gradlew testDebugUnitTest lintDebug bundleRelease`, then `scripts/play_publish.py --key <key.json> --track internal --dry-run` and the same without `--dry-run`. The script reads the notes for the version code itself. Commit as `Release <version> to internal testing`.
3. For production, promote the tested build with `--track production --version-code <code>` (dry run first; `--rollout 0.2` for a staged rollout), turn `Unreleased` into `[<version>] - <date>` with its comparison link, commit as `Release <version>`, and tag that commit `v<version>` (annotated: `git tag -a v<version> -m "Comic Viewer <version>"`). Pushing the tag creates the GitHub release from the changelog section.

Publishing reaches real users: run the script without `--dry-run` only when the user has explicitly asked for that release, track, and rollout.

## Commit & Pull Request Guidelines

The default branch is `main`. Use short imperative commit subjects without a final period, matching history such as `Add PDF comic support` and `Fix lint warnings`, then a blank line and a body that explains what changed and why, wrapped at about 72 characters. Don't add `Co-Authored-By`, session links or other AI attribution lines to commit messages or pull request descriptions. Pull requests follow `.github/pull_request_template.md`: a concise summary, validation commands, screenshots or recordings for UI changes, and notes about storage, permissions, billing, or cache behavior. Do not push or create tags unless asked.

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
