#!/usr/bin/env python3
"""Tests for scripts/publish_snapshot_prerelease.py."""

from __future__ import annotations

import random
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPTS_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(SCRIPTS_DIR))

import publish_snapshot_prerelease as publisher  # noqa: E402


def uploaded_zip_basenames(commands: list[list[str]]) -> list[str]:
    """GitHub asset names are file basenames; ``#`` is only a display label."""
    names: list[str] = []
    for cmd in commands:
        for part in cmd:
            path = part.split("#", 1)[0]
            if path.endswith(".zip"):
                names.append(Path(path).name)
    return names


class PublishSnapshotPrereleaseTest(unittest.TestCase):
    def setUp(self) -> None:
        rng = random.Random()
        self.version = f"{rng.randint(3, 9)}.{rng.randint(0, 20)}-SNAPSHOT"
        self.sha = "".join(rng.choice("abcdef0123456789") for _ in range(12))
        self.zip_path = f"/tmp/fscrawler-distribution-{self.version}.zip"
        self.friendly_name = f"fscrawler-{self.version}.zip"
        self.legacy_name = f"fscrawler-distribution-{self.version}.zip"

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
        self.assertEqual(uploaded_zip_basenames(commands), [self.friendly_name])

    def test_github_download_url_uses_file_basename_not_hash_label(self) -> None:
        commands = publisher.plan(
            version=self.version,
            zip_path=self.zip_path,
            target=self.sha,
            exists=False,
        )
        names = uploaded_zip_basenames(commands)
        self.assertEqual(names, [self.friendly_name])
        self.assertNotIn(self.legacy_name, names)
        flat = " ".join(" ".join(cmd) for cmd in commands)
        self.assertNotIn(f"#{self.friendly_name}", flat)

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
        self.assertEqual(uploaded_zip_basenames(commands), [self.friendly_name])

    def test_clobber_deletes_legacy_maven_artifact_asset(self) -> None:
        commands = publisher.plan(
            version=self.version,
            zip_path=self.zip_path,
            target=self.sha,
            exists=True,
            existing_assets=[self.legacy_name, self.friendly_name],
        )
        delete = next(cmd for cmd in commands if cmd[:3] == ["gh", "release", "delete-asset"])
        self.assertIn(self.legacy_name, delete)
        self.assertIn("--yes", delete)

    def test_clobber_skips_delete_when_legacy_asset_absent(self) -> None:
        commands = publisher.plan(
            version=self.version,
            zip_path=self.zip_path,
            target=self.sha,
            exists=True,
            existing_assets=[self.friendly_name],
        )
        self.assertFalse(any(cmd[:3] == ["gh", "release", "delete-asset"] for cmd in commands))

    def test_asset_name_is_friendly_zip(self) -> None:
        self.assertEqual(publisher.asset_name(self.version), self.friendly_name)
        self.assertEqual(publisher.maven_zip_name(self.version), self.legacy_name)

    def test_stage_github_zip_copies_to_friendly_filename(self) -> None:
        payload = bytes(random.randrange(256) for _ in range(random.randint(16, 64)))
        with tempfile.TemporaryDirectory() as tmp:
            source = Path(tmp) / self.legacy_name
            source.write_bytes(payload)
            staged = publisher.stage_github_zip(source, self.version)
            self.assertEqual(staged.name, self.friendly_name)
            self.assertEqual(staged.read_bytes(), payload)
            self.assertTrue(source.is_file())

    def test_stage_github_zip_is_noop_when_already_friendly(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            source = Path(tmp) / self.friendly_name
            source.write_bytes(b"zip")
            staged = publisher.stage_github_zip(source, self.version)
            self.assertEqual(staged.resolve(), source.resolve())


if __name__ == "__main__":
    unittest.main()
