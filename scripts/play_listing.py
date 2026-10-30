#!/usr/bin/env python3
"""Update the Google Play store listing from fastlane/metadata/android.

Uses the same service account key as play_publish.py. Keep the key outside the repo.

  # Upload texts and screenshots, have Play validate them, then discard the edit (changes nothing):
  scripts/play_listing.py --key ~/keys/play.json --texts --screenshots --dry-run

  # Publish only the English and Italian texts:
  scripts/play_listing.py --key ~/keys/play.json --texts --language en-US --language it-IT

Each language folder holds title.txt, short_description.txt and full_description.txt; a missing
file keeps that field's current text. Screenshots come from the en-US folder only and replace
every screenshot of that kind in order of file name; other languages fall back to them.
images/phoneScreenshots fills the phone slots and images/tenInchScreenshots both tablet slots.
"""

import argparse
import sys
from pathlib import Path

from play_publish import PACKAGE, ROOT, UPLOAD_TIMEOUT, PlayClient, PlayError, fetch_access_token

DEFAULT_METADATA = ROOT / "fastlane/metadata/android"
SCREENSHOT_LANGUAGE = "en-US"
TEXT_FILES = {
    "title": ("title.txt", 30),
    "shortDescription": ("short_description.txt", 80),
    "fullDescription": ("full_description.txt", 4000),
}
SCREENSHOT_SLOTS = {
    "phoneScreenshots": ["phoneScreenshots"],
    "tenInchScreenshots": ["sevenInchScreenshots", "tenInchScreenshots"],
}
MAX_SCREENSHOTS = 8


def listing_texts(metadata: Path, languages: list[str]) -> dict[str, dict]:
    folders = [metadata / language for language in languages] if languages else sorted(
        folder for folder in metadata.iterdir() if folder.is_dir()
    )
    listings = {}
    for folder in folders:
        if not folder.is_dir():
            raise PlayError(f"No metadata folder for {folder.name}")
        fields = {}
        for field, (file_name, limit) in TEXT_FILES.items():
            path = folder / file_name
            if not path.is_file():
                continue
            text = path.read_text().strip()
            if not text:
                raise PlayError(f"{path} is empty")
            if len(text) > limit:
                raise PlayError(f"{path} exceeds {limit} characters ({len(text)})")
            fields[field] = text
        if fields:
            listings[folder.name] = fields
    return listings


def screenshot_files(folder: Path) -> list[Path]:
    files = sorted(folder.glob("*.png"))
    if not files:
        raise PlayError(f"No PNG screenshots in {folder}")
    if len(files) > MAX_SCREENSHOTS:
        raise PlayError(f"{folder} has {len(files)} screenshots; Play takes at most {MAX_SCREENSHOTS}")
    return files


def replace_images(client: PlayClient, edit_id: str, image_type: str, files: list[Path]) -> None:
    base = f"edits/{edit_id}/listings/{SCREENSHOT_LANGUAGE}/{image_type}"
    client.call("DELETE", f"{client.base}/{base}")
    for path in files:
        with path.open("rb") as stream:
            client.call(
                "POST",
                f"{client.upload_base}/{base}",
                params={"uploadType": "media"},
                headers={"Content-Type": "image/png"},
                data=stream,
                timeout=UPLOAD_TIMEOUT,
            )
    print(f"Replaced {SCREENSHOT_LANGUAGE} {image_type} with {len(files)} image(s)")


def update(client: PlayClient, args: argparse.Namespace) -> None:
    listings = listing_texts(args.metadata, args.language) if args.texts else {}
    screenshots = {}
    if args.screenshots:
        images = args.metadata / SCREENSHOT_LANGUAGE / "images"
        screenshots = {folder: screenshot_files(images / folder) for folder in SCREENSHOT_SLOTS}

    edit_id = client.insert_edit()
    committed = False
    try:
        for language, fields in listings.items():
            client.call(
                "PATCH",
                f"{client.base}/edits/{edit_id}/listings/{language}",
                json={"language": language, **fields},
            )
            print(f"Updated {language} listing: {', '.join(fields)}")
        for folder, files in screenshots.items():
            for image_type in SCREENSHOT_SLOTS[folder]:
                replace_images(client, edit_id, image_type, files)

        client.call("POST", f"{client.base}/edits/{edit_id}:validate")
        print("Edit validated by Google Play")

        if args.dry_run:
            print("Dry run: discarding the edit, nothing was published")
            return

        client.call("POST", f"{client.base}/edits/{edit_id}:commit")
        committed = True
        print("Committed the store listing update")
    finally:
        if not committed:
            try:
                client.delete_edit(edit_id)
            except PlayError as error:
                print(f"Warning: could not delete edit {edit_id}: {error}", file=sys.stderr)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--key", type=Path, required=True, help="service account JSON key")
    parser.add_argument("--package", default=PACKAGE)
    parser.add_argument("--metadata", type=Path, default=DEFAULT_METADATA, help="fastlane metadata folder")
    parser.add_argument("--texts", action="store_true", help="upload titles and descriptions")
    parser.add_argument("--language", action="append", default=[], help="limit --texts to these languages")
    parser.add_argument("--screenshots", action="store_true", help="replace the phone and tablet screenshots")
    parser.add_argument("--dry-run", action="store_true", help="upload and validate, then discard the edit")
    args = parser.parse_args()
    if not args.texts and not args.screenshots:
        parser.error("pass --texts, --screenshots or both")
    return args


def main() -> int:
    args = parse_args()
    try:
        client = PlayClient(fetch_access_token(args.key.expanduser()), args.package)
        update(client, args)
    except PlayError as error:
        print(f"Error: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
