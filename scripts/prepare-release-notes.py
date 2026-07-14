#!/usr/bin/env python3
"""Assemble release notes from Markdown source, header template, and GitHub API."""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
from pathlib import Path

from dotenv_util import load_dotenv


def repo_root() -> Path:
    return Path(__file__).resolve().parent.parent


def download_url(version: str) -> str:
    return (
        "https://repo1.maven.org/maven2/fr/pilato/elasticsearch/crawler/"
        f"fscrawler-distribution/{version}/fscrawler-distribution-{version}.zip"
    )


def render_header(template_path: Path, version: str) -> str:
    template = template_path.read_text(encoding="utf-8")
    return template.format(VERSION=version, DOWNLOAD_URL=download_url(version))


def read_release_notes(notes_path: Path) -> str:
    if not notes_path.is_file():
        sys.exit(f"Release notes not found: {notes_path}")
    return notes_path.read_text(encoding="utf-8").strip()


def gh_generate_notes(repo: str, tag_name: str, previous_tag_name: str) -> str:
    cmd = [
        "gh",
        "api",
        f"repos/{repo}/releases/generate-notes",
        "-f",
        f"tag_name={tag_name}",
        "-f",
        f"previous_tag_name={previous_tag_name}",
    ]
    try:
        result = subprocess.run(cmd, check=True, capture_output=True, text=True)
    except subprocess.CalledProcessError as exc:
        stderr = exc.stderr.strip() if exc.stderr else str(exc)
        sys.exit(f"gh generate-notes failed: {stderr}")
    payload = json.loads(result.stdout)
    return payload.get("body", "").strip()


def assemble(
    version: str,
    since_tag: str,
    output: Path,
    github_repo: str,
    tag_prefix: str = "fscrawler",
) -> None:
    root = repo_root()
    header_path = root / "scripts" / "templates" / "release-header.md"
    notes_path = root / "docs" / "source" / "release" / f"{version}.md"
    tag_name = f"{tag_prefix}-{version}"

    parts = [
        render_header(header_path, version),
        "",
        read_release_notes(notes_path),
    ]

    gh_notes = gh_generate_notes(github_repo, tag_name, since_tag)
    if gh_notes:
        parts.extend(["", "## Merged pull requests", "", gh_notes])

    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(parts).strip() + "\n", encoding="utf-8")
    print(f"Wrote {output}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Prepare FSCrawler release notes.")
    parser.add_argument("--version", required=True, help="Release version (e.g. 3.0)")
    parser.add_argument(
        "--since-tag",
        required=True,
        help="Previous git tag (e.g. fscrawler-2.9)",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("target/release-notes.md"),
        help="Output Markdown file",
    )
    parser.add_argument(
        "--github-repo",
        default=os.environ.get("GITHUB_REPO", "dadoonet/fscrawler"),
        help="GitHub repository (owner/name)",
    )
    return parser.parse_args()


def main() -> None:
    load_dotenv(repo_root() / ".env")
    args = parse_args()
    assemble(
        version=args.version,
        since_tag=args.since_tag,
        output=args.output if args.output.is_absolute() else repo_root() / args.output,
        github_repo=args.github_repo,
    )


if __name__ == "__main__":
    main()
