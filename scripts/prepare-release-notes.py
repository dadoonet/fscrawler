#!/usr/bin/env python3
"""Assemble release notes from Markdown source, header template, and GitHub API."""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
from pathlib import Path

from dotenv_util import load_dotenv

LABEL_ANCHOR_RE = re.compile(r"^\(([-\w]+)\)=$", re.MULTILINE)
REF_TITLED_RE = re.compile(r"\{ref\}`([^`<]+)\s*<([^>]+)>`")
REF_SIMPLE_RE = re.compile(r"\{ref\}`([^`<]+)`")


def repo_root() -> Path:
    return Path(__file__).resolve().parent.parent


def readthedocs_url(version: str, html_path: str, anchor: str) -> str:
    return (
        f"https://fscrawler.readthedocs.io/en/fscrawler-{version}/"
        f"{html_path}#{anchor}"
    )


def build_ref_index(docs_source: Path) -> dict[str, str]:
    """Map MyST label to its HTML page path under docs/source."""
    index: dict[str, str] = {}
    for md_file in sorted(docs_source.rglob("*.md")):
        html_path = md_file.relative_to(docs_source).with_suffix(".html").as_posix()
        content = md_file.read_text(encoding="utf-8")
        for match in LABEL_ANCHOR_RE.finditer(content):
            index[match.group(1)] = html_path
    return index


def ref_link_text(label: str, title: str | None = None) -> str:
    if title:
        return title.strip()
    return label.replace("-", " ")


def resolve_myst_refs(
    markdown: str,
    version: str,
    ref_index: dict[str, str],
) -> str:
    """Convert MyST {ref}`...` directives to ReadTheDocs links."""

    def titled_replace(match: re.Match[str]) -> str:
        title, label = match.group(1), match.group(2).strip()
        html_path = ref_index.get(label)
        if html_path is None:
            return match.group(0)
        url = readthedocs_url(version, html_path, label)
        return f"[{ref_link_text(label, title)}]({url})"

    def simple_replace(match: re.Match[str]) -> str:
        label = match.group(1).strip()
        html_path = ref_index.get(label)
        if html_path is None:
            return match.group(0)
        url = readthedocs_url(version, html_path, label)
        return f"[{ref_link_text(label)}]({url})"

    markdown = REF_TITLED_RE.sub(titled_replace, markdown)
    return REF_SIMPLE_RE.sub(simple_replace, markdown)


def download_url(version: str) -> str:
    return (
        "https://repo1.maven.org/maven2/fr/pilato/elasticsearch/crawler/"
        f"fscrawler-distribution/{version}/fscrawler-distribution-{version}.zip"
    )


def render_header(template_path: Path, version: str) -> str:
    template = template_path.read_text(encoding="utf-8")
    return template.format(VERSION=version, DOWNLOAD_URL=download_url(version))


def read_release_notes(notes_path: Path, version: str, ref_index: dict[str, str]) -> str:
    if not notes_path.is_file():
        sys.exit(f"Release notes not found: {notes_path}")
    content = notes_path.read_text(encoding="utf-8").strip()
    return resolve_myst_refs(content, version, ref_index)


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
    ref_index = build_ref_index(root / "docs" / "source")
    tag_name = f"{tag_prefix}-{version}"

    parts = [
        render_header(header_path, version),
        "",
        read_release_notes(notes_path, version, ref_index),
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
