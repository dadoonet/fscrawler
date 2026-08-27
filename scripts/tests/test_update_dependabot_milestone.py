#!/usr/bin/env python3
"""Tests for scripts/update_dependabot_milestone.py."""

from __future__ import annotations

import random
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPTS_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(SCRIPTS_DIR))

import update_dependabot_milestone as updater  # noqa: E402

SAMPLE = """\
version: 2
updates:
- package-ecosystem: maven
  directory: "/"
  milestone: {old}
- package-ecosystem: "docker"
  milestone: {old}
- package-ecosystem: "github-actions"
  milestone: {old}
"""


class UpdateDependabotMilestoneTest(unittest.TestCase):
    def setUp(self) -> None:
        rng = random.Random()
        self.major = rng.randint(3, 9)
        self.minor = rng.randint(0, 20)
        self.snapshot = f"{self.major}.{self.minor}-SNAPSHOT"
        self.title = f"{self.major}.{self.minor}"
        self.old_number = rng.randint(1, 40)
        self.new_number = rng.randint(41, 90)

    def test_strips_snapshot_suffix_for_milestone_title(self) -> None:
        self.assertEqual(updater.milestone_title_from_snapshot(self.snapshot), self.title)
        self.assertEqual(updater.milestone_title_from_snapshot(self.title), self.title)

    def _milestone_values(self, yaml_text: str) -> list[str]:
        return [
            line.strip()
            for line in yaml_text.splitlines()
            if line.strip().startswith("milestone:")
        ]

    def test_replaces_every_milestone_number(self) -> None:
        yaml_text = SAMPLE.format(old=self.old_number)
        updated = updater.replace_milestones(yaml_text, self.new_number)
        expected = [f"milestone: {self.new_number}"] * 3
        self.assertEqual(self._milestone_values(updated), expected)
        self.assertIn('package-ecosystem: maven', updated)
        self.assertIn('package-ecosystem: "docker"', updated)

    def test_preserves_surrounding_yaml(self) -> None:
        yaml_text = SAMPLE.format(old=self.old_number)
        updated = updater.replace_milestones(yaml_text, self.new_number)
        self.assertTrue(updated.startswith("version: 2\n"))
        self.assertIn('directory: "/"', updated)

    def test_missing_milestone_keys_raise(self) -> None:
        with self.assertRaises(updater.DependabotMilestoneError) as ctx:
            updater.replace_milestones("version: 2\nupdates: []\n", self.new_number)
        self.assertIn("milestone:", str(ctx.exception))

    def test_selects_open_milestone_by_title(self) -> None:
        milestones = [
            {"number": self.old_number, "title": "2.9", "state": "open"},
            {"number": self.new_number, "title": self.title, "state": "open"},
            {"number": self.new_number + 1, "title": "4.0", "state": "open"},
        ]
        chosen = updater.select_milestone(milestones, self.title)
        self.assertEqual(chosen["number"], self.new_number)

    def test_prefers_open_milestone_when_title_is_duplicated(self) -> None:
        milestones = [
            {"number": self.old_number, "title": self.title, "state": "closed"},
            {"number": self.new_number, "title": self.title, "state": "open"},
        ]
        chosen = updater.select_milestone(milestones, self.title)
        self.assertEqual(chosen["number"], self.new_number)
        self.assertEqual(chosen["state"], "open")

    def test_missing_title_returns_none(self) -> None:
        self.assertIsNone(
            updater.select_milestone(
                [{"number": 1, "title": "2.9", "state": "open"}],
                self.title,
            )
        )

    def test_cli_rewrites_file_with_explicit_number(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "dependabot.yml"
            path.write_text(SAMPLE.format(old=self.old_number), encoding="utf-8")
            updater.main(
                [
                    "--file",
                    str(path),
                    "--snapshot",
                    self.snapshot,
                    "--milestone-number",
                    str(self.new_number),
                ]
            )
            text = path.read_text(encoding="utf-8")
            self.assertEqual(self._milestone_values(text), [f"milestone: {self.new_number}"] * 3)


if __name__ == "__main__":
    unittest.main()
