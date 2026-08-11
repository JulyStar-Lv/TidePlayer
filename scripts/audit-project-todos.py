#!/usr/bin/env python3
"""Report first-party TODO markers and blocking placeholders without failing CI."""

from __future__ import annotations

from collections import Counter
from pathlib import Path
import re


REPOSITORY_ROOT = Path(__file__).resolve().parents[1]
SOURCE_ROOTS = (
    "androidApp",
    "desktopApp",
    "iosApp",
    "shared",
    "core",
    "feature",
    "service",
    "source",
    "rust-libs/app-backend",
    "rust-libs/audio-dsp",
    "rust-libs/audio-metadata",
    "rust-libs/storage-backend",
)
EXCLUDED_PARTS = {
    "build",
    "target",
    "generated",
    "dist",
    ".gradle",
    ".idea",
}
TEXT_SUFFIXES = {".kt", ".kts", ".rs", ".swift", ".xml", ".yml", ".yaml", ".md"}
MARKERS = {
    "TODO": re.compile(r"\bTODO\b"),
    "FIXME": re.compile(r"\bFIXME\b"),
    "HACK": re.compile(r"\bHACK\b"),
    "runBlocking": re.compile(r"\brunBlocking\b"),
    "Noop": re.compile(r"\bNoop[A-Za-z0-9_]*\b"),
    "placeholder": re.compile(r"\bplaceholder\b", re.IGNORECASE),
}


def source_files() -> list[Path]:
    files: list[Path] = []
    for root_name in SOURCE_ROOTS:
        root = REPOSITORY_ROOT / root_name
        if not root.exists():
            continue
        for path in root.rglob("*"):
            relative_parts = path.relative_to(REPOSITORY_ROOT).parts
            if not path.is_file() or path.suffix not in TEXT_SUFFIXES:
                continue
            if any(part in EXCLUDED_PARTS for part in relative_parts):
                continue
            if relative_parts[:2] in {("rust-libs", "vendor")}:
                continue
            if relative_parts[:3] == ("rust-libs", "plugin-runtime", "vendor"):
                continue
            files.append(path)
    return sorted(files)


def main() -> int:
    counts: Counter[str] = Counter()
    findings: list[tuple[str, Path, int, str]] = []
    for path in source_files():
        for line_number, line in enumerate(path.read_text(errors="replace").splitlines(), start=1):
            for category, pattern in MARKERS.items():
                if pattern.search(line):
                    counts[category] += 1
                    findings.append((category, path, line_number, line.strip()))

    for category, path, line_number, line in findings:
        relative = path.relative_to(REPOSITORY_ROOT)
        print(f"{category:12} {relative}:{line_number}: {line}")

    print("\nSummary (advisory only)")
    for category in MARKERS:
        print(f"  {category:12} {counts[category]}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
