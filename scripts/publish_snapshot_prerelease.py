#!/usr/bin/env python3
"""Publish (or refresh) the SNAPSHOT ZIP as a public GitHub pre-release.

Used by .github/workflows/maven.yml on every push to main. The pre-release tag is
fscrawler-{version} (for example fscrawler-3.1-SNAPSHOT). Stable releases created
by release.sh delete that pre-release once the final GitHub release is confirmed.
"""

from __future__ import annotations

import argparse
import os
import subprocess
import sys
from pathlib import Path


class SnapshotPrereleaseError(SystemExit):
    """SNAPSHOT pre-release cannot be published."""


def asset_name(version: str) -> str:
    return f"fscrawler-{version}.zip"


def release_tag(version: str) -> str:
    return f"fscrawler-{version}"


def snapshot_notes(target: str) -> str:
    return (
        "Development SNAPSHOT build. The ZIP is overwritten on every push to `main`.\n"
        f"Commit: {target}"
    )


def plan(
    *,
    version: str,
    zip_path: str,
    target: str,
    exists: bool,
) -> list[list[str]]:
    if not version.endswith("-SNAPSHOT"):
        return []

    tag = release_tag(version)
    asset = f"{zip_path}#{asset_name(version)}"
    notes = snapshot_notes(target)
    if exists:
        return [
            ["gh", "release", "upload", tag, asset, "--clobber"],
            [
                "gh",
                "release",
                "edit",
                tag,
                "--prerelease",
                "--latest=false",
                "--target",
                target,
                "--notes",
                notes,
            ],
        ]
    return [
        [
            "gh",
            "release",
            "create",
            tag,
            "--prerelease",
            "--latest=false",
            "--title",
            f"FSCrawler {version}",
            "--notes",
            notes,
            "--target",
            target,
            asset,
        ]
    ]


def release_exists(tag: str) -> bool:
    result = subprocess.run(
        ["gh", "release", "view", tag],
        check=False,
        capture_output=True,
        text=True,
    )
    return result.returncode == 0


def current_maven_version(root: Path) -> str:
    result = subprocess.run(
        ["mvn", "-q", "help:evaluate", "-Dexpression=project.version", "-DforceStdout"],
        cwd=root,
        check=True,
        capture_output=True,
        text=True,
    )
    return result.stdout.strip()


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Publish the SNAPSHOT ZIP as a GitHub pre-release.")
    parser.add_argument("--version", default=None, help="Maven version (default: mvn help:evaluate)")
    parser.add_argument("--zip", dest="zip_path", default=None, help="Path to the distribution ZIP")
    parser.add_argument(
        "--target",
        default=os.environ.get("GITHUB_SHA") or "",
        help="Commit SHA the pre-release points at (default: GITHUB_SHA)",
    )
    parser.add_argument("--dry-run", action="store_true", help="Print gh commands without running them")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> None:
    args = parse_args(argv)
    root = Path(__file__).resolve().parent.parent
    version = args.version or current_maven_version(root)
    if not version.endswith("-SNAPSHOT"):
        print(f"Version {version} is not a SNAPSHOT — skipping GitHub pre-release.")
        return

    zip_path = args.zip_path or str(
        root / "distribution" / "target" / f"fscrawler-distribution-{version}.zip"
    )
    if not args.dry_run and not Path(zip_path).is_file():
        raise SnapshotPrereleaseError(f"Distribution ZIP not found: {zip_path}")

    target = args.target
    if not target:
        target = subprocess.run(
            ["git", "rev-parse", "HEAD"],
            cwd=root,
            check=True,
            capture_output=True,
            text=True,
        ).stdout.strip()

    exists = False if args.dry_run else release_exists(release_tag(version))
    commands = plan(version=version, zip_path=zip_path, target=target, exists=exists)
    for cmd in commands:
        print("+ " + " ".join(cmd))
        if args.dry_run:
            continue
        try:
            subprocess.run(cmd, check=True)
        except subprocess.CalledProcessError as exc:
            raise SnapshotPrereleaseError(f"Command failed: {' '.join(cmd)}") from exc
    print(f"SNAPSHOT pre-release {release_tag(version)} is up to date.")


if __name__ == "__main__":
    main()
