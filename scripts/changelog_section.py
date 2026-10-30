#!/usr/bin/env python3
"""Print one version's section of CHANGELOG.md, without its heading.

  scripts/changelog_section.py 1.0.34

Used for the body of a GitHub release. Exits with an error when the version has no section.
"""

import re
import sys
from pathlib import Path

CHANGELOG = Path(__file__).resolve().parent.parent / "CHANGELOG.md"


def changelog_section(text: str, version: str) -> str | None:
    heading = re.compile(rf"^## \[{re.escape(version)}\][^\n]*\n", re.MULTILINE)
    match = heading.search(text)
    if match is None:
        return None
    rest = text[match.end():]
    end = re.search(r"^(## |\[[^\]]+\]: )", rest, re.MULTILINE)
    return (rest[: end.start()] if end else rest).strip()


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__, file=sys.stderr)
        return 2
    version = sys.argv[1].removeprefix("v")
    section = changelog_section(CHANGELOG.read_text(), version)
    if not section:
        print(f"No section for {version} in {CHANGELOG.name}", file=sys.stderr)
        return 1
    print(section)
    return 0


if __name__ == "__main__":
    sys.exit(main())
