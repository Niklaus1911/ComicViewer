# Releasing

Comic Viewer ships through Google Play. Every build goes to the internal test track first; a build
that has been tested is promoted to production, tagged, and gets a GitHub release.

## Versions

- `appVersionName` follows [Semantic Versioning](https://semver.org): raise the minor version for
  new features and the patch version for fixes only.
- `appVersionCode` goes up by one with every upload and must be higher than any code already on
  any track; Play never accepts a code twice.

Both are at the top of [`app/build.gradle.kts`](../app/build.gradle.kts).
`scripts/play_publish.py --key <key.json> --check` shows what is on each track.

## Internal test build

1. Add the user-facing changes to the `Unreleased` section of [`CHANGELOG.md`](../CHANGELOG.md).
2. Raise `appVersionCode` and `appVersionName`.
3. Write the Play release notes, at most 500 characters each, in
   `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` and
   `fastlane/metadata/android/it-IT/changelogs/<versionCode>.txt`.
4. Build and check:

   ```sh
   ./gradlew testDebugUnitTest lintDebug bundleRelease
   ```

5. Publish. The script reads the notes for the new version code on its own:

   ```sh
   scripts/play_publish.py --key <key.json> --track internal --dry-run   # validate, change nothing
   scripts/play_publish.py --key <key.json> --track internal
   ```

6. Commit the version bump and the notes as `Release <version> to internal testing`.

## Production release

1. Promote the tested build without uploading it again:

   ```sh
   scripts/play_publish.py --key <key.json> --track production --version-code <code> --dry-run
   scripts/play_publish.py --key <key.json> --track production --version-code <code>
   ```

   Add `--rollout 0.2` for a staged rollout. Production release notes can be rewritten for users
   in that version code's changelog files first.
2. In `CHANGELOG.md`, rename `Unreleased` to `[<version>] - <date>`, start a new empty
   `Unreleased` section, and update the comparison links at the bottom.
3. Commit as `Release <version>`, then tag that commit and push both:

   ```sh
   git tag -a v<version> -m "Comic Viewer <version>"
   git push origin main v<version>
   ```

   The tag runs the GitHub Release workflow, which creates the release named "Comic Viewer
   <version>" with the changelog section as notes and a debug APK attached.
4. If the store listing changed, publish it as described in [store-listing.md](store-listing.md).

The Google Play service account key never goes into the repository; pass its path to the scripts.
