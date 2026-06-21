# Repository Guidelines

## Project Structure & Module Organization

This is a single-module Android project. The app module lives in `app/`, with Kotlin source under `app/src/main/java/com/comicviewer/`. Core comic loading and browsing logic is split across files such as `ComicLoader.kt`, `FileBrowser.kt`, `RecentComics.kt`, and `LibraryComics.kt`; most Compose UI currently lives in `MainActivity.kt`. Unit tests are in `app/src/test/java/com/comicviewer/`, and instrumented tests are in `app/src/androidTest/java/com/comicviewer/`. Android resources, themes, strings, icons, and XML backup rules are in `app/src/main/res/`.

## Build, Test, and Development Commands

- `./gradlew assembleDebug` builds a debug APK and validates Kotlin/Android compilation.
- `./gradlew testDebugUnitTest` runs local JVM unit tests.
- `./gradlew connectedDebugAndroidTest` runs instrumented tests on a connected emulator/device.
- `./gradlew clean` removes generated build outputs when Gradle state appears stale.

Use Android Studio for emulator launch, manual UI checks, and APK installation during development.

## Coding Style & Naming Conventions

Use Kotlin with 4-space indentation and idiomatic Compose patterns. Keep functions small where practical, prefer immutable `val`, and avoid unrelated refactors. Name composables with PascalCase nouns, for example `LibraryScreen` or `ReaderTopBar`. Name helpers with clear verbs or domain phrases, for example `loadLocalArchiveComic` or `compareComicPageNames`. Keep user-facing Italian strings consistent with existing UI text unless a feature intentionally changes language style.

## Testing Guidelines

Unit tests use JUnit 4 in `ExampleUnitTest.kt`. Add tests for pure helpers, persistence encoding/decoding, sorting, cache keys, and boundary behavior. Prefer descriptive test names using the existing pattern: `functionName_expectedBehavior`, such as `upsertLibraryComic_dedupesAndKeepsRecentlyAddedFirst`. Run `./gradlew testDebugUnitTest assembleDebug` before handing off substantive changes.

## Commit & Pull Request Guidelines

Recent commits use short imperative summaries, such as `Add reader page scrubber` and `Harden comic archive handling`. Keep commit subjects concise and focused on the user-visible or architectural change. Pull requests should include a brief summary, validation commands run, screenshots or screen recordings for UI changes, and notes about storage, permissions, or cache behavior when relevant.

## Security & Configuration Tips

The app reads local comic archives and folders, so preserve bounded archive extraction and cache cleanup behavior. Do not weaken file validation, hidden-path filtering, or copy-size checks without adding tests. Avoid committing generated build outputs, local IDE state, or device-specific paths.
