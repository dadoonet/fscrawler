#!/usr/bin/env python3
"""Update the README "Latest versions" table between HTML comment markers.

The table stays human-readable Markdown. release.sh rewrites the SNAPSHOT row into
the just-released version and appends a new SNAPSHOT row for the next iteration.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

START_MARKER = "<!-- release-versions:start -->"
END_MARKER = "<!-- release-versions:end -->"
DOCS_RELEASED = "https://fscrawler.readthedocs.io/en/fscrawler-{version}/"
DOCS_SNAPSHOT = "https://fscrawler.readthedocs.io/en/latest/"


class ReadmeVersionsError(SystemExit):
    """README table cannot be updated (missing markers or SNAPSHOT row)."""


def _cells(line: str) -> list[str]:
    stripped = line.strip()
    if not stripped.startswith("|"):
        return []
    parts = [part.strip() for part in stripped.strip("|").split("|")]
    return parts


def _is_separator(line: str) -> bool:
    cells = _cells(line)
    return bool(cells) and all(set(cell) <= set("-:") and bool(cell) for cell in cells)


def _is_header(cells: list[str]) -> bool:
    return bool(cells) and cells[0].lower() == "elasticsearch"


def _format_row(cells: list[str], widths: list[int]) -> str:
    padded = [cell.ljust(widths[i]) for i, cell in enumerate(cells)]
    return "| " + " | ".join(padded) + " |"


def _format_separator(widths: list[int]) -> str:
    return "| " + " | ".join("-" * width for width in widths) + " |"


def _docs_cell(version: str) -> str:
    if version.endswith("-SNAPSHOT"):
        return f"[{version}]({DOCS_SNAPSHOT})"
    return f"[{version}]({DOCS_RELEASED.format(version=version)})"


def update_readme(
    markdown: str,
    *,
    released: str,
    next_version: str,
    release_date: str,
    es_versions: str | None = None,
) -> str:
    if START_MARKER not in markdown or END_MARKER not in markdown:
        raise ReadmeVersionsError(
            f"README is missing {START_MARKER} / {END_MARKER} markers around the versions table."
        )

    before, rest = markdown.split(START_MARKER, 1)
    block, after = rest.split(END_MARKER, 1)

    header: list[str] | None = None
    rows: list[list[str]] = []
    snapshot_index: int | None = None

    for line in block.splitlines():
        if not line.strip():
            continue
        cells = _cells(line)
        if not cells:
            continue
        if _is_separator(line):
            continue
        if _is_header(cells):
            header = cells
            continue
        if len(cells) < 4:
            raise ReadmeVersionsError(f"Unexpected versions table row: {line}")
        rows.append(cells)
        if "-SNAPSHOT" in cells[1]:
            snapshot_index = len(rows) - 1

    if header is None:
        raise ReadmeVersionsError("Versions table is missing the Elasticsearch / FSCrawler header row.")
    if snapshot_index is None:
        raise ReadmeVersionsError("Versions table has no SNAPSHOT row to promote.")

    snapshot_row = rows[snapshot_index]
    released_es = snapshot_row[0]
    snapshot_es = es_versions if es_versions else released_es

    rows[snapshot_index] = [
        released_es,
        released,
        release_date,
        _docs_cell(released),
    ]
    rows.append(
        [
            snapshot_es,
            next_version,
            "",
            _docs_cell(next_version),
        ]
    )

    widths = [len(cell) for cell in header]
    for row in rows:
        for i, cell in enumerate(row):
            widths[i] = max(widths[i], len(cell))

    lines = [
        _format_row(header, widths),
        _format_separator(widths),
        *[_format_row(row, widths) for row in rows],
    ]
    table = "\n".join(lines)
    return f"{before}{START_MARKER}\n{table}\n{END_MARKER}{after}"


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Promote the README SNAPSHOT version row and append the next SNAPSHOT."
    )
    parser.add_argument("--readme", type=Path, required=True, help="Path to README.md")
    parser.add_argument("--released", required=True, help="Version being released (e.g. 3.1)")
    parser.add_argument("--next", dest="next_version", required=True, help="Next SNAPSHOT (e.g. 3.2-SNAPSHOT)")
    parser.add_argument("--date", dest="release_date", required=True, help="Release date (YYYY-MM-DD)")
    parser.add_argument(
        "--es-versions",
        default=None,
        help="Elasticsearch range for the new SNAPSHOT row (default: keep the previous SNAPSHOT range)",
    )
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> None:
    args = parse_args(argv)
    readme = args.readme
    if not readme.is_file():
        raise ReadmeVersionsError(f"README not found: {readme}")
    updated = update_readme(
        readme.read_text(encoding="utf-8"),
        released=args.released,
        next_version=args.next_version,
        release_date=args.release_date,
        es_versions=args.es_versions,
    )
    readme.write_text(updated, encoding="utf-8")
    print(f"Updated {readme} ({args.released} released, next {args.next_version})")


if __name__ == "__main__":
    main()
