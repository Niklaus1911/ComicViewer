#!/usr/bin/env python3
"""Publish the release bundle to Google Play through the Play Developer API.

Needs a service account JSON key whose email has release permissions for the
app in Play Console (Users and permissions). Keep the key outside the repo.

  # Verify access and show the current tracks (changes nothing):
  scripts/play_publish.py --key ~/keys/play.json --check

  # Upload, validate and discard (changes nothing):
  scripts/play_publish.py --key ~/keys/play.json --track production --dry-run

  # Staged production rollout to 20% of users:
  scripts/play_publish.py --key ~/keys/play.json --track production --rollout 0.2

  # Put an already uploaded build on another track without re-uploading:
  scripts/play_publish.py --key ~/keys/play.json --track internal --version-code 18

Release notes come from fastlane/metadata/android/<language>/changelogs/<versionCode>.txt, one
file per language, en-US required. --notes LANGUAGE=FILE replaces them for a single run.
"""

import argparse
import base64
import json
import re
import sys
import time
from pathlib import Path

import requests
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding

PACKAGE = "com.comicviewer"
SCOPE = "https://www.googleapis.com/auth/androidpublisher"
API = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications"
UPLOAD_API = "https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications"
ROOT = Path(__file__).resolve().parent.parent
DEFAULT_BUNDLE = ROOT / "app/build/outputs/bundle/release/app-release.aab"
DEFAULT_MAPPING = ROOT / "app/build/outputs/mapping/release/mapping.txt"
DEFAULT_METADATA = ROOT / "fastlane/metadata/android"
BUILD_FILE = ROOT / "app/build.gradle.kts"
DEFAULT_LANGUAGE = "en-US"
REQUEST_TIMEOUT = 60
UPLOAD_TIMEOUT = 600
MAX_NOTES_LENGTH = 500


class PlayError(Exception):
    pass


def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def fetch_access_token(key_path: Path) -> str:
    info = json.loads(key_path.read_text())
    token_uri = info.get("token_uri", "https://oauth2.googleapis.com/token")
    now = int(time.time())
    header = {"alg": "RS256", "typ": "JWT", "kid": info["private_key_id"]}
    claims = {
        "iss": info["client_email"],
        "scope": SCOPE,
        "aud": token_uri,
        "iat": now,
        "exp": now + 3600,
    }
    signing_input = f"{b64url(json.dumps(header).encode())}.{b64url(json.dumps(claims).encode())}"
    private_key = serialization.load_pem_private_key(info["private_key"].encode(), password=None)
    signature = private_key.sign(signing_input.encode(), padding.PKCS1v15(), hashes.SHA256())
    response = requests.post(
        token_uri,
        data={
            "grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer",
            "assertion": f"{signing_input}.{b64url(signature)}",
        },
        timeout=REQUEST_TIMEOUT,
    )
    if not response.ok:
        raise PlayError(f"Token request failed ({response.status_code}): {response.text}")
    return response.json()["access_token"]


class PlayClient:
    def __init__(self, token: str, package: str):
        self.session = requests.Session()
        self.session.headers["Authorization"] = f"Bearer {token}"
        self.base = f"{API}/{package}"
        self.upload_base = f"{UPLOAD_API}/{package}"

    def call(self, method: str, url: str, **kwargs) -> dict:
        kwargs.setdefault("timeout", REQUEST_TIMEOUT)
        response = self.session.request(method, url, **kwargs)
        if not response.ok:
            raise PlayError(f"{method} {url} failed ({response.status_code}): {response.text}")
        return response.json() if response.content else {}

    def upload(self, url: str, path: Path) -> dict:
        with path.open("rb") as stream:
            return self.call(
                "POST",
                url,
                params={"uploadType": "media"},
                headers={"Content-Type": "application/octet-stream"},
                data=stream,
                timeout=UPLOAD_TIMEOUT,
            )

    def insert_edit(self) -> str:
        return self.call("POST", f"{self.base}/edits")["id"]

    def delete_edit(self, edit_id: str) -> None:
        self.call("DELETE", f"{self.base}/edits/{edit_id}")


def describe_release(release: dict) -> str:
    parts = [
        release.get("name", "(unnamed)"),
        f"codes={','.join(release.get('versionCodes', [])) or '-'}",
        f"status={release.get('status')}",
    ]
    if "userFraction" in release:
        parts.append(f"rollout={release['userFraction']:.0%}")
    return " ".join(parts)


def check_access(client: PlayClient) -> None:
    edit_id = client.insert_edit()
    try:
        details = client.call("GET", f"{client.base}/edits/{edit_id}/details")
        listings = client.call("GET", f"{client.base}/edits/{edit_id}/listings").get("listings", [])
        tracks = client.call("GET", f"{client.base}/edits/{edit_id}/tracks").get("tracks", [])
    finally:
        client.delete_edit(edit_id)

    print(f"Access OK. Default language: {details.get('defaultLanguage')}")
    print(f"Store listing languages: {', '.join(listing['language'] for listing in listings)}")
    for track in tracks:
        releases = track.get("releases", [])
        print(f"Track {track['track']}: {len(releases)} release(s)")
        for release in releases:
            print(f"  {describe_release(release)}")


def read_notes(language: str, path: Path) -> dict:
    text = path.read_text().strip()
    if not text:
        raise PlayError(f"Release notes for {language} are empty: {path}")
    if len(text) > MAX_NOTES_LENGTH:
        raise PlayError(f"Release notes for {language} exceed {MAX_NOTES_LENGTH} characters ({len(text)}): {path}")
    return {"language": language, "text": text}


def parse_notes(entries: list[str]) -> list[dict]:
    notes = []
    for entry in entries:
        language, separator, file_name = entry.partition("=")
        if not separator or not language or not file_name:
            raise PlayError(f"--notes expects LANGUAGE=FILE, got {entry!r}")
        notes.append(read_notes(language, Path(file_name).expanduser()))
    return notes


def metadata_notes(metadata: Path, version_code: int) -> list[dict]:
    """The release notes in <language>/changelogs/<versionCode>.txt of the fastlane metadata folder."""
    notes = [
        read_notes(path.parent.parent.name, path)
        for path in sorted(metadata.glob(f"*/changelogs/{version_code}.txt"))
    ]
    if not any(note["language"] == DEFAULT_LANGUAGE for note in notes):
        raise PlayError(f"No release notes at {metadata / DEFAULT_LANGUAGE / 'changelogs'}/{version_code}.txt")
    return notes


def built_version_code() -> int | None:
    """The appVersionCode the bundle is built with, read from app/build.gradle.kts."""
    match = re.search(r"^val appVersionCode = (\d+)$", BUILD_FILE.read_text(), re.MULTILINE)
    return int(match.group(1)) if match else None


def build_release(args: argparse.Namespace, version_code: int, notes: list[dict]) -> dict:
    release: dict = {"versionCodes": [str(version_code)]}
    if args.release_name:
        release["name"] = args.release_name
    if args.draft:
        release["status"] = "draft"
    elif args.rollout < 1:
        release["status"] = "inProgress"
        release["userFraction"] = args.rollout
    else:
        release["status"] = "completed"
    if notes:
        release["releaseNotes"] = notes
    return release


def publish(client: PlayClient, args: argparse.Namespace) -> None:
    if not 0 < args.rollout <= 1:
        raise PlayError("--rollout must be greater than 0 and at most 1")
    if args.version_code is None and not args.bundle.is_file():
        raise PlayError(f"Bundle not found: {args.bundle}. Run ./gradlew bundleRelease first.")
    # Notes are checked before anything is uploaded, against the version code the bundle should have.
    expected_version_code = args.version_code or built_version_code()
    if args.notes:
        notes = parse_notes(args.notes)
    elif expected_version_code is not None:
        notes = metadata_notes(args.metadata, expected_version_code)
    else:
        notes = None

    edit_id = client.insert_edit()
    committed = False
    try:
        if args.version_code is not None:
            version_code = args.version_code
            print(f"Using already uploaded version code {version_code}")
        else:
            print(f"Uploading {args.bundle} ...")
            version_code = client.upload(f"{client.upload_base}/edits/{edit_id}/bundles", args.bundle)["versionCode"]
            print(f"Uploaded bundle with version code {version_code}")

            if args.mapping and args.mapping.is_file():
                client.upload(
                    f"{client.upload_base}/edits/{edit_id}/apks/{version_code}/deobfuscationFiles/proguard",
                    args.mapping,
                )
                print(f"Uploaded R8 mapping {args.mapping}")
            elif args.mapping:
                print(f"No mapping file at {args.mapping}; skipping deobfuscation upload")

        if not args.notes and version_code != expected_version_code:
            notes = metadata_notes(args.metadata, version_code)

        release = build_release(args, version_code, notes)
        client.call(
            "PUT",
            f"{client.base}/edits/{edit_id}/tracks/{args.track}",
            json={"track": args.track, "releases": [release]},
        )
        print(f"Prepared {args.track} release: {describe_release(release)}")

        client.call("POST", f"{client.base}/edits/{edit_id}:validate")
        print("Edit validated by Google Play")

        if args.dry_run:
            print("Dry run: discarding the edit, nothing was published")
            return

        client.call("POST", f"{client.base}/edits/{edit_id}:commit")
        committed = True
        print(f"Committed {args.track} release {version_code}")
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
    parser.add_argument("--check", action="store_true", help="verify access and list tracks, then exit")
    parser.add_argument("--track", help="internal, alpha, beta, production or a custom track")
    parser.add_argument("--bundle", type=Path, default=DEFAULT_BUNDLE)
    parser.add_argument(
        "--version-code",
        type=int,
        help="assign an already uploaded version code to the track instead of uploading the bundle",
    )
    parser.add_argument("--mapping", type=Path, default=DEFAULT_MAPPING, help="R8 mapping.txt to attach")
    parser.add_argument("--no-mapping", dest="mapping", action="store_const", const=None)
    parser.add_argument("--rollout", type=float, default=1.0, help="user fraction, e.g. 0.2 for 20%%")
    parser.add_argument("--draft", action="store_true", help="create a draft release instead of rolling out")
    parser.add_argument("--release-name", help="defaults to the version name chosen by Play")
    parser.add_argument(
        "--notes",
        action="append",
        default=[],
        metavar="LANGUAGE=FILE",
        help="release notes for one run, instead of the fastlane changelogs",
    )
    parser.add_argument("--metadata", type=Path, default=DEFAULT_METADATA, help="fastlane metadata folder")
    parser.add_argument("--dry-run", action="store_true", help="upload and validate, then discard the edit")
    args = parser.parse_args()
    if not args.check and not args.track:
        parser.error("--track is required unless --check is given")
    return args


def main() -> int:
    args = parse_args()
    try:
        client = PlayClient(fetch_access_token(args.key.expanduser()), args.package)
        if args.check:
            check_access(client)
        else:
            publish(client, args)
    except PlayError as error:
        print(f"Error: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
