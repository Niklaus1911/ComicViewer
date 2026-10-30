# Changelog

All notable changes to Comic Viewer are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Each section lists what changed since the previous version that reached Google Play
production. Builds that only went to the internal test track are folded into the next
production version.

## [Unreleased]

## [1.0.36] - 2026-10-30

### Added

- Auto discover finds PDF comics: PDFs with at least 8 picture-heavy pages are added, while
  ordinary documents such as manuals and invoices are left out.
- A comic's details page shows where its file is, such as `Internal storage/Comics`, and a long
  press selects any detail so it can be copied.

## [1.0.34] - 2026-10-25

### Changed

- Vertical scrolling loads zoomed pages in up to three times more detail, keeps the zoom where the
  fingers are, and scrolls to every edge of a zoomed page.
- Updated Compose, Coil, Kotlin and the build tools.

### Removed

- Double-tap zoom in vertical scrolling, so a single tap shows or hides the controls at once.

### Fixed

- The page number and saved progress follow a pinch zoom in vertical scrolling.
- Pinching after rotating the screen no longer makes the strip jump.
- On a nearly full device, a comic opened from a cloud storage app can use space Android frees
  from cached files.

## [1.0.31] - 2026-10-25

### Added

- PDF comics, free for everyone: open and read PDFs like CBZ archives.
- TalkBack announces each page by its position, such as "Page 10 of 24".

### Changed

- Zoomed pages reload in sharper detail, and pinch zoom is smoother.
- Wide pages start at their top edge, and rotating the screen keeps the comic open.
- Library scans and search are faster.

### Fixed

- A full library never drops comics, and history survives a comic that fails to open.
- Pages no longer fail to display after fast page turns, and no error flashes while a page loads.

## [1.0.21] - 2026-10-25

### Added

- A new home: the library with Library, History and Settings tabs, search, sorting, progress
  filters and grid, compact and list layouts.
- A details page for every comic with page previews and metadata from `ComicInfo.xml`.
- Vertical scrolling for webtoons, a page grid to jump anywhere, and compact reading settings.
- Comic Viewer Pro, a one-time purchase: two-page spreads, right-to-left reading, fit modes,
  reader backgrounds, favorites, tags and extra themes.
- Automatic discovery of comic archives on the device.
- A welcome screen, light and dark themes, Material You colors and accent colors.

### Changed

- Archives are read in place instead of being extracted, so comics open faster and take no extra
  storage.
- Storage access goes through Android's document picker.
- Layouts for tablets in portrait and landscape.

### Fixed

- Pro purchases are always acknowledged, even if the app closes during the purchase.
- The page cache and the reader's saved state stay within fixed limits.

## [1.0.4] - 2026-06-22

### Added

- Add several comics to the library at once.
- Smoother page swipes in the reader.

## [1.0.2] - 2026-06-21

### Added

- English translation.

## [1.0.1] - 2026-06-21

### Added

- First release: read CBZ and ZIP comics and folders of images, with a file browser, a library
  grid, recent comics and a page scrubber.

[Unreleased]: https://github.com/Niklaus1911/ComicViewer/compare/v1.0.36...HEAD
[1.0.36]: https://github.com/Niklaus1911/ComicViewer/compare/v1.0.34...v1.0.36
[1.0.34]: https://github.com/Niklaus1911/ComicViewer/compare/v1.0.31...v1.0.34
[1.0.31]: https://github.com/Niklaus1911/ComicViewer/compare/v1.0.21...v1.0.31
[1.0.21]: https://github.com/Niklaus1911/ComicViewer/compare/v1.0.4...v1.0.21
[1.0.4]: https://github.com/Niklaus1911/ComicViewer/compare/v1.0.2...v1.0.4
[1.0.2]: https://github.com/Niklaus1911/ComicViewer/compare/v1.0.1...v1.0.2
[1.0.1]: https://github.com/Niklaus1911/ComicViewer/releases/tag/v1.0.1
