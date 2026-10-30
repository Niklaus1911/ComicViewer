# Contributing

## Setup

You need JDK 17 or later and the Android SDK; Android Studio provides both. Open the project
folder in Android Studio, or build from the command line:

```sh
./gradlew assembleDebug               # debug APK in app/build/outputs/apk/debug/
./gradlew testDebugUnitTest           # unit tests
./gradlew lintDebug                   # Android lint
./gradlew connectedDebugAndroidTest   # device tests on a running emulator or phone
```

A signed release build (`./gradlew bundleRelease`) needs a local `keystore.properties`; copy
[`keystore.properties.example`](keystore.properties.example) and fill it in. Without it, release
builds are unsigned. Never commit the keystore or its properties file.

## Making a change

- Branch from `main` and keep each change focused; unrelated refactors go in their own change.
- Follow the [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html) and
  [`.editorconfig`](.editorconfig): 4-space indentation, imports in alphabetical order.
- Put user-facing text in `app/src/main/res/values/strings.xml`, with the Italian translation in
  `values-it/strings.xml`.
- Add unit tests for logic, and device tests for interaction or storage behavior.
- Before you push, run `./gradlew testDebugUnitTest lintDebug assembleDebug`; lint must report no
  issues. After adding libraries or reflection, also run `./gradlew bundleRelease` and check the
  minified build.
- Record user-facing changes in the `Unreleased` section of [`CHANGELOG.md`](CHANGELOG.md).

## Commit messages

Write a short subject in the imperative mood, under about 72 characters and without a final
period: `Add PDF comic support`, `Fix lint warnings`. Leave a blank line, then explain what changed
and why, wrapped at about 72 characters. Don't add `Co-Authored-By` or other attribution lines.

## Pull requests

Describe what changed and how you tested it, add screenshots or recordings for interface changes,
and point out anything that touches storage access, permissions, billing or caching.

## CI

Every push to `main` and every pull request runs the unit tests on GitHub Actions. Lint, the
release build and the device tests run when the workflows are started by hand from the Actions
tab, so run them locally before pushing.

## Releases

See [docs/RELEASING.md](docs/RELEASING.md).
