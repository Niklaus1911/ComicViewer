# Store listing

The Google Play listing lives in [`fastlane/metadata/android`](../fastlane/metadata/android), in the
layout [fastlane supply](https://docs.fastlane.tools/actions/supply/) and F-Droid use. The
repository holds what is live on Play; change the files, then publish them with
`scripts/play_listing.py`.

```
fastlane/metadata/android/
  <language>/                 en-US, it-IT, ar, cs-CZ, da-DK, fi-FI, hi-IN
    title.txt                 at most 30 characters
    short_description.txt     at most 80 characters
    full_description.txt      at most 4000 characters
    changelogs/<code>.txt     release notes for that version code, at most 500 characters
  en-US/images/
    icon.png                  512×512, 32-bit PNG with alpha, under 1 MB
    featureGraphic.png        1024×500, 24-bit PNG without alpha
    phoneScreenshots/         eight portrait screenshots, 1080×2160, PNG without alpha
    tenInchScreenshots/       four landscape screenshots, 2560×1600, PNG without alpha
```

English (`en-US`) is the default listing language; other languages fall back to its images. The
tablet screenshots fill both the 7-inch and the 10-inch slots. English and Italian describe PDF
support; the other languages still carry the older text.

## Publishing

```sh
scripts/play_listing.py --key <key.json> --texts --screenshots --dry-run   # validate, change nothing
scripts/play_listing.py --key <key.json> --texts --language en-US --language it-IT
```

Listing changes go through Google's review before they show on the store.

## Screenshots

Phone screenshots, in upload order:

1. `01-library.png`: library home with Continue reading, filters, and New, progress and finished
   badges.
2. `02-details.png`: comic page with the cover header, the continue button and page previews.
3. `03-reader.png`: single-page reading with the floating controls.
4. `04-vertical-scroll.png`: vertical scrolling for webtoons and long strips.
5. `05-reading-settings.png`: reading settings over a live page preview.
6. `06-search.png`: search matching a writer from comic metadata.
7. `07-history.png`: reading history grouped by day.
8. `08-settings.png`: app settings.

Phone screenshots were captured from 1.0.21 on a 1080×2400 emulator with SystemUI demo mode, then
fitted into 1080×2160 canvases to stay within Play's 2:1 limit. Tablet screenshots were captured
from 1.0.31 on a 2560×1600 emulator (`Tab_S8_like`) with the same demo library. All show the free
version in English.

## Artwork

Covers and pages in the screenshots are original demo artwork in [`assets/demo-art`](../assets/demo-art);
the icon's full-size source is [`assets/icon/icon-source-1254.png`](../assets/icon/icon-source-1254.png).
Use only original or licensed artwork in anything published or committed.
