#!/usr/bin/env python3
"""Tests for scripts/update_readme_versions.py (HTML-marker README table)."""

from __future__ import annotations

import random
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPTS_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(SCRIPTS_DIR))

import update_readme_versions as updater  # noqa: E402

START = "<!-- release-versions:start -->"
END = "<!-- release-versions:end -->"


def _readme(table_rows: list[str], preamble: str = "Current versions are:", epilogue: str = "## Quick start") -> str:
    body = "\n".join(table_rows)
    return (
        f"# File System Crawler\n\n## Latest versions\n\n{preamble}\n\n"
        f"{START}\n{body}\n{END}\n\n{epilogue}\n"
    )


def _header_rows() -> list[str]:
    return [
        "| Elasticsearch | FSCrawler    | Released   | Docs |",
        "|---------------|--------------|------------|------|",
    ]


class UpdateReadmeVersionsTest(unittest.TestCase):
    def setUp(self) -> None:
        rng = random.Random()
        self.released = f"{rng.randint(3, 9)}.{rng.randint(0, 20)}"
        self.next = f"{rng.randint(3, 9)}.{rng.randint(0, 20)}-SNAPSHOT"
        while self.next.startswith(f"{self.released}-"):
            self.next = f"{rng.randint(3, 9)}.{rng.randint(0, 20)}-SNAPSHOT"
        self.date = f"202{rng.randint(4, 9)}-{rng.randint(1, 12):02d}-{rng.randint(1, 28):02d}"
        self.es_current = f"{rng.randint(7, 9)}.x, {rng.randint(8, 10)}.x"
        self.historical_version = f"2.{rng.randint(1, 9)}"
        self.historical_es = f"{rng.randint(6, 7)}.x"
        self.historical_date = f"2022-0{rng.randint(1, 9)}-{rng.randint(10, 28)}"

    def _snapshot_table(self) -> str:
        return _readme(
            _header_rows()
            + [
                f"| {self.historical_es} | {self.historical_version} | {self.historical_date} | "
                f"[{self.historical_version}](https://fscrawler.readthedocs.io/en/fscrawler-{self.historical_version}/) |",
                f"| {self.es_current} | {self.released}-SNAPSHOT |  | "
                f"[{self.released}-SNAPSHOT](https://fscrawler.readthedocs.io/en/latest/) |",
            ]
        )

    def test_promotes_snapshot_row_and_appends_next_snapshot(self) -> None:
        updated = updater.update_readme(
            self._snapshot_table(),
            released=self.released,
            next_version=self.next,
            release_date=self.date,
        )

        self.assertIn(START, updated)
        self.assertIn(END, updated)
        self.assertIn(self.historical_version, updated)
        self.assertIn(self.historical_date, updated)

        block = updated.split(START, 1)[1].split(END, 1)[0]
        released_lines = [
            line for line in block.splitlines() if self.date in line and self.released in updater._cells(line)
        ]
        self.assertEqual(len(released_lines), 1, updated)
        released_cells = updater._cells(released_lines[0])
        self.assertEqual(released_cells[0], self.es_current)
        self.assertEqual(released_cells[1], self.released)
        self.assertEqual(released_cells[2], self.date)
        self.assertEqual(
            released_cells[3],
            f"[{self.released}](https://fscrawler.readthedocs.io/en/fscrawler-{self.released}/)",
        )

        self.assertIn(self.next, updated)
        self.assertIn(f"[{self.next}](https://fscrawler.readthedocs.io/en/latest/)", updated)
        self.assertNotIn(f"{self.released}-SNAPSHOT", updated)
        self.assertIn("## Quick start", updated)
        self.assertIn("# File System Crawler", updated)

    def test_new_snapshot_keeps_elasticsearch_range_from_previous_snapshot(self) -> None:
        updated = updater.update_readme(
            self._snapshot_table(),
            released=self.released,
            next_version=self.next,
            release_date=self.date,
        )
        block = updated.split(START, 1)[1].split(END, 1)[0]
        snapshot_lines = [line for line in block.splitlines() if self.next in line]
        self.assertEqual(len(snapshot_lines), 1)
        self.assertIn(self.es_current, snapshot_lines[0])

    def test_es_versions_override_applies_to_new_snapshot_row_only(self) -> None:
        override = f"{random.randint(9, 12)}.x"
        updated = updater.update_readme(
            self._snapshot_table(),
            released=self.released,
            next_version=self.next,
            release_date=self.date,
            es_versions=override,
        )
        block = updated.split(START, 1)[1].split(END, 1)[0]
        released_lines = [
            line
            for line in block.splitlines()
            if f"| {self.released} |" in line.replace("  ", " ") or f"| {self.released} " in line
        ]
        snapshot_lines = [line for line in block.splitlines() if self.next in line]
        self.assertTrue(any(self.es_current in line for line in released_lines))
        self.assertEqual(len(snapshot_lines), 1)
        self.assertIn(override, snapshot_lines[0])
        self.assertNotIn(self.es_current, snapshot_lines[0])

    def test_missing_markers_raise(self) -> None:
        with self.assertRaises(updater.ReadmeVersionsError) as ctx:
            updater.update_readme(
                "# No markers here\n",
                released=self.released,
                next_version=self.next,
                release_date=self.date,
            )
        self.assertIn("release-versions:start", str(ctx.exception))

    def test_missing_snapshot_row_raise(self) -> None:
        readme = _readme(
            _header_rows()
            + [
                f"| {self.historical_es} | {self.historical_version} | {self.historical_date} | "
                f"[{self.historical_version}](https://fscrawler.readthedocs.io/en/fscrawler-{self.historical_version}/) |",
            ]
        )
        with self.assertRaises(updater.ReadmeVersionsError) as ctx:
            updater.update_readme(
                readme,
                released=self.released,
                next_version=self.next,
                release_date=self.date,
            )
        self.assertIn("SNAPSHOT", str(ctx.exception))

    def test_cli_rewrites_readme_file(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "README.md"
            path.write_text(self._snapshot_table(), encoding="utf-8")
            updater.main(
                [
                    "--readme",
                    str(path),
                    "--released",
                    self.released,
                    "--next",
                    self.next,
                    "--date",
                    self.date,
                ]
            )
            text = path.read_text(encoding="utf-8")
            self.assertIn(self.released, text)
            self.assertIn(self.next, text)
            self.assertIn(self.date, text)
            self.assertNotIn(f"{self.released}-SNAPSHOT", text)


if __name__ == "__main__":
    unittest.main()
