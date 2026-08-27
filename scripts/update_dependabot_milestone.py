#!/usr/bin/env python3
"""Point Dependabot PRs at the GitHub milestone matching the next SNAPSHOT.

Dependabot requires the numeric milestone id (the URL suffix), not the title.
For POM 3.1-SNAPSHOT the title is 3.1 (currently GitHub milestone 28).
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path
from typing import Any

MILESTONE_LINE = re.compile(r"^(\s*milestone:\s*)\d+\s*$", re.MULTILINE)


class DependabotMilestoneError(SystemExit):
    """dependabot.yml cannot be updated."""


def milestone_title_from_snapshot(version: str) -> str:
    return version.removesuffix("-SNAPSHOT")


def replace_milestones(yaml_text: str, number: int) -> str:
    if not MILESTONE_LINE.search(yaml_text):
        raise DependabotMilestoneError(
            "dependabot.yml has no 'milestone:' keys to update."
        )
    return MILESTONE_LINE.sub(rf"\g<1>{number}", yaml_text)


def select_milestone(milestones: list[dict[str, Any]], title: str) -> dict[str, Any] | None:
    matches = [item for item in milestones if str(item.get("title", "")) == title]
    if not matches:
        return None
    open_matches = [item for item in matches if item.get("state") == "open"]
    pool = open_matches or matches
    return max(pool, key=lambda item: int(item["number"]))


def list_milestones(repo: str) -> list[dict[str, Any]]:
    cmd = [
        "gh",
        "api",
        "--paginate",
        f"repos/{repo}/milestones?state=all&per_page=100",
        "--jq",
        "[.[] | {number,title,state}]",
    ]
    try:
        result = subprocess.run(cmd, check=True, capture_output=True, text=True)
    except subprocess.CalledProcessError as exc:
        stderr = exc.stderr.strip() if exc.stderr else str(exc)
        raise DependabotMilestoneError(f"Failed to list GitHub milestones: {stderr}") from exc
    payload = json.loads(result.stdout)
    if isinstance(payload, list) and payload and isinstance(payload[0], list):
        # gh --paginate can emit one array per page
        return [item for page in payload for item in page]
    if not isinstance(payload, list):
        raise DependabotMilestoneError(f"Unexpected milestones payload: {payload!r}")
    return payload


def create_milestone(repo: str, title: str) -> dict[str, Any]:
    cmd = [
        "gh",
        "api",
        f"repos/{repo}/milestones",
        "-f",
        f"title={title}",
        "-f",
        "state=open",
    ]
    try:
        result = subprocess.run(cmd, check=True, capture_output=True, text=True)
    except subprocess.CalledProcessError as exc:
        stderr = exc.stderr.strip() if exc.stderr else str(exc)
        raise DependabotMilestoneError(
            f"Failed to create GitHub milestone {title!r}: {stderr}"
        ) from exc
    payload = json.loads(result.stdout)
    return {"number": payload["number"], "title": payload.get("title", title), "state": payload.get("state", "open")}


def resolve_milestone_number(
    *,
    repo: str,
    title: str,
    create: bool,
    allow_missing: bool,
) -> int | None:
    existing = select_milestone(list_milestones(repo), title)
    if existing is not None:
        return int(existing["number"])
    if create:
        created = create_milestone(repo, title)
        return int(created["number"])
    if allow_missing:
        print(
            f"No GitHub milestone titled {title!r}; leaving dependabot.yml unchanged.",
            file=sys.stderr,
        )
        return None
    raise DependabotMilestoneError(
        f"No GitHub milestone titled {title!r}. Create it or pass --create."
    )


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Set dependabot.yml milestone to the GitHub milestone matching the next SNAPSHOT."
    )
    parser.add_argument("--file", type=Path, required=True, help="Path to .github/dependabot.yml")
    parser.add_argument(
        "--snapshot",
        required=True,
        help="Next Maven version (e.g. 3.1-SNAPSHOT); milestone title is the version without -SNAPSHOT",
    )
    parser.add_argument(
        "--github-repo",
        default="dadoonet/fscrawler",
        help="GitHub repository (owner/name) used to resolve the milestone number",
    )
    parser.add_argument(
        "--milestone-number",
        type=int,
        default=None,
        help="Skip GitHub lookup and write this numeric id (for tests)",
    )
    parser.add_argument(
        "--create",
        action="store_true",
        help="Create the GitHub milestone when the title does not exist",
    )
    parser.add_argument(
        "--allow-missing",
        action="store_true",
        help="Do nothing when the milestone title is missing (local rehearsal)",
    )
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> None:
    args = parse_args(argv)
    path: Path = args.file
    if not path.is_file():
        raise DependabotMilestoneError(f"dependabot.yml not found: {path}")

    title = milestone_title_from_snapshot(args.snapshot)
    number = args.milestone_number
    if number is None:
        number = resolve_milestone_number(
            repo=args.github_repo,
            title=title,
            create=args.create,
            allow_missing=args.allow_missing,
        )
        if number is None:
            return

    updated = replace_milestones(path.read_text(encoding="utf-8"), number)
    path.write_text(updated, encoding="utf-8")
    print(f"Updated {path} milestone -> {number} (title {title})")


if __name__ == "__main__":
    main()
