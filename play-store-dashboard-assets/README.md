# Google Play Store Assets

This folder contains upload-ready artwork for the Google Play Console store listing. The covers and comic pages are original demo artwork and do not use third-party characters or publications.

## Upload Files

- `icon/app-icon-512.png` — app icon, 512×512, 32-bit PNG with alpha, under 1 MB.
  `icon/icon-source-1254.png` is the full-size source artwork and `icon/legacy-play-store-icon-512.png` is the earlier icon without alpha; neither is uploaded.
- `feature-graphic/feature-graphic-1024x500.png` — feature graphic, 1024×500, 24-bit PNG without alpha.
- `phone-screenshots/` — eight portrait screenshots, 1080×2160, 24-bit PNG without alpha. Upload them in numbered order.
- `tablet-screenshots/` — four landscape screenshots, 2560×1600, 24-bit PNG without alpha, used for both the 7-inch and the 10-inch tablet slots: library, comic details, reader and history.

## Recommended Screenshot Order

1. `01-library.png` — library home with Continue reading, filters and New, progress and finished badges.
2. `02-details.png` — comic page with blurred cover header, continue button and page previews.
3. `03-reader.png` — single-page reading with the floating controls.
4. `04-vertical-scroll.png` — vertical scroll mode for webtoons and long strips.
5. `05-reading-settings.png` — compact reading settings over a live page preview.
6. `06-search.png` — search matching a writer from comic metadata.
7. `07-history.png` — reading history grouped by day with progress and finished entries.
8. `08-settings.png` — grouped app settings.

Captured from version 1.0.21 on a 1080×2400 emulator with a clean demo status bar, then proportionally fitted into 1080×2160 canvases so the whole interface stays visible within Google Play's maximum 2:1 ratio. The UI screenshots are in English and show the free version; create localized variants later only if the corresponding store listings need localized imagery. The previous set, which shows the interface before the 1.0.17–1.0.21 redesign, is kept in `phone-screenshots-1.0.15/` for reference and is not meant for upload.

Tablet screenshots were captured from version 1.0.31 on the `Tab_S8_like` emulator (2560×1600, landscape) with the same demo library and a clean demo status bar.

`scripts/play_listing.py` uploads listing texts and both screenshot sets through the Play API; run it with `--dry-run` first.

## Supporting Artwork

`demo-art/` contains the original covers and pages used inside the screenshots. These files are supporting source assets and are not required Play Console uploads.
