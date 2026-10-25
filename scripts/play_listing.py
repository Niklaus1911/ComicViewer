#!/usr/bin/env python3
"""Update the Google Play store listing: texts per language and screenshots.

Uses the same service account key as play_publish.py. Keep the key outside the repo.

  # Upload and validate, then discard the edit (changes nothing):
  scripts/play_listing.py --key ~/keys/play.json \\
      --listing en-US=listing/en-US.json --listing it-IT=listing/it-IT.json \\
      --phone-screenshots play-store-dashboard-assets/phone-screenshots \\
      --tablet-screenshots play-store-dashboard-assets/tablet-screenshots --dry-run

A listing file is JSON with any of "title", "shortDescription" and "fullDescription"; fields left
out keep their current text. A screenshot folder replaces every screenshot of that kind, in file
name order, in the --images-language listing; other languages without their own screenshots fall
back to it. Tablet screenshots fill both the 7-inch and the 10-inch slots.
"""

import argparse
import json
import sys
from pathlib import Path

from play_publish import PACKAGE, UPLOAD_TIMEOUT, PlayClient, PlayError, fetch_access_token

TEXT_LIMITS = {"title": 30, "shortDescription": 80, "fullDescription": 4000}
MAX_SCREENSHOTS = 8


def parse_listings(entries: list[str]) -> dict[str, dict]:
    listings = {}
    for entry in entries:
        language, separator, file_name = entry.partition("=")
        if not separator or not language or not file_name:
            raise PlayError(f"--listing expects LANGUAGE=FILE, got {entry!r}")
        data = json.loads(Path(file_name).expanduser().read_text())
        unknown = set(data) - set(TEXT_LIMITS)
        if unknown:
            raise PlayError(f"{file_name}: unknown fields {sorted(unknown)}")
        for field, limit in TEXT_LIMITS.items():
            text = data.get(field)
            if text is None:
                continue
            if not text.strip():
                raise PlayError(f"{language} {field} is empty")
            if len(text) > limit:
                raise PlayError(f"{language} {field} exceeds {limit} characters ({len(text)})")
        listings[language] = data
    return listings


def screenshot_files(folder: Path) -> list[Path]:
    files = sorted(folder.expanduser().glob("*.png"))
    if not files:
        raise PlayError(f"No PNG screenshots in {folder}")
    if len(files) > MAX_SCREENSHOTS:
        raise PlayError(f"{folder} has {len(files)} screenshots; Play takes at most {MAX_SCREENSHOTS}")
    return files


def replace_images(client: PlayClient, edit_id: str, language: str, image_type: str, files: list[Path]) -> None:
    client.call("DELETE", f"{client.base}/edits/{edit_id}/listings/{language}/{image_type}")
    for path in files:
        with path.open("rb") as stream:
            client.call(
                "POST",
                f"{client.upload_base}/edits/{edit_id}/listings/{language}/{image_type}",
                params={"uploadType": "media"},
                headers={"Content-Type": "image/png"},
                data=stream,
                timeout=UPLOAD_TIMEOUT,
            )
    print(f"Replaced {language} {image_type} with {len(files)} image(s)")


def update(client: PlayClient, args: argparse.Namespace) -> None:
    listings = parse_listings(args.listing)
    phone = screenshot_files(args.phone_screenshots) if args.phone_screenshots else []
    tablet = screenshot_files(args.tablet_screenshots) if args.tablet_screenshots else []
    if not listings and not phone and not tablet:
        raise PlayError("Nothing to update: pass --listing, --phone-screenshots or --tablet-screenshots")

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
        if phone:
            replace_images(client, edit_id, args.images_language, "phoneScreenshots", phone)
        if tablet:
            for image_type in ("sevenInchScreenshots", "tenInchScreenshots"):
                replace_images(client, edit_id, args.images_language, image_type, tablet)

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
    parser.add_argument("--listing", action="append", default=[], metavar="LANGUAGE=FILE")
    parser.add_argument("--phone-screenshots", type=Path, help="folder of phone screenshot PNGs")
    parser.add_argument("--tablet-screenshots", type=Path, help="folder of tablet screenshot PNGs")
    parser.add_argument("--images-language", default="en-US", help="listing that receives the screenshots")
    parser.add_argument("--dry-run", action="store_true", help="upload and validate, then discard the edit")
    return parser.parse_args()


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
