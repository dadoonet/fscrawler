#!/usr/bin/env python3
"""Tests for scripts/publish_snapshot_prerelease.py."""

from __future__ import annotations

import random
import sys
import unittest
from pathlib import Path

SCRIPTS_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(SCRIPTS_DIR))

import publish_snapshot_prerelease as publisher  # noqa: E402


class PublishSnapshotPrereleaseTest(unittest.TestCase):
    def setUp(self) -> None:
        rng = random.Random()
        self.version = f"{rng.randint(3, 9)}.{rng.randint(0, 20)}-SNAPSHOT"
        self.sha = "".join(rng.choice("abcdef0123456789") for _ in range(12))
        self.zip_path = f"/tmp/fscrawler-distribution-{self.version}.zip"

    def test_skips_non_snapshot_versions(self) -> None:
        released = self.version.removesuffix("-SNAPSHOT")
        self.assertEqual(
            publisher.plan(
                version=released,
                zip_path=self.zip_path,
                target=self.sha,
                exists=False,
            ),
            [],
        )

    def test_creates_prerelease_when_missing(self) -> None:
        commands = publisher.plan(
            version=self.version,
            zip_path=self.zip_path,
            target=self.sha,
            exists=False,
        )
        self.assertEqual(len(commands), 1)
        create = commands[0]
        self.assertEqual(create[0:3], ["gh", "release", "create"])
        self.assertIn(f"fscrawler-{self.version}", create)
        self.assertIn("--prerelease", create)
        self.assertIn("--latest=false", create)
        self.assertIn(self.sha, create)
        self.assertTrue(any(f"#{publisher.asset_name(self.version)}" in part for part in create))

    def test_clobbers_existing_prerelease_zip(self) -> None:
        commands = publisher.plan(
            version=self.version,
            zip_path=self.zip_path,
            target=self.sha,
            exists=True,
        )
        flat = " ".join(" ".join(cmd) for cmd in commands)
        self.assertIn("gh release upload", flat)
        self.assertIn("--clobber", flat)
        self.assertIn("gh release edit", flat)
        self.assertIn("--prerelease", flat)
        self.assertIn(self.sha, flat)
        self.assertNotIn("gh release create", flat)

    def test_asset_display_name_is_friendly_zip(self) -> None:
        self.assertEqual(publisher.asset_name(self.version), f"fscrawler-{self.version}.zip")


if __name__ == "__main__":
    unittest.main()
