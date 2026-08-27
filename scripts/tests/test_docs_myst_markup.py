#!/usr/bin/env python3
"""Guards for MyST markup that ReadTheDocs silently mis-renders.

MyST does not expand ``{{ name }}`` inside fenced or inline code, and a
backtick-fenced ``{ifconfig}`` is closed by the first nested `` ``` `` of the
same length — which leaks or drops the rest of the block.
"""

from __future__ import annotations

import random
import re
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
DOCS_SOURCE = REPO_ROOT / "docs" / "source"

# Gestalt / Jinja triple-brace placeholders (e.g. {{{_tmp_fingerprint}}}) are
# not MyST substitutions. Require a non-brace before {{ and a letter after.
MUSTACHE = re.compile(r"(?<!\{)\{\{\s*([A-Za-z][A-Za-z0-9_]*)\s*\}\}")
FENCE_OPEN = re.compile(r"^(?P<indent> *)(?P<ticks>`{3,}|~{3,})(?P<info>.*)$")
BACKTICK_IFCONFIG = re.compile(r"^ *`{3,}\{ifconfig\}")


def _markdown_pages() -> list[Path]:
    pages = sorted(DOCS_SOURCE.rglob("*.md"))
    if not pages:
        raise FileNotFoundError(f"no Markdown pages under {DOCS_SOURCE}")
    return pages


def _is_code_fence(info: str) -> bool:
    """True for ```sh / {code-block}; false for {note} / {ifconfig} / {warning}."""
    stripped = info.strip()
    if stripped.startswith("{code-block}") or stripped.startswith("{code}"):
        return True
    return not stripped.startswith("{")


def _fenced_blocks(text: str) -> list[tuple[int, str, str]]:
    """Return (start_line, info_string, body) for each fence, including nested ones.

    A shorter backtick fence inside a longer one does not close the outer fence
    (CommonMark), so `` ```sh `` inside `` ````{note} `` is its own block.
    """
    lines = text.splitlines()
    blocks: list[tuple[int, str, str]] = []
    stack: list[dict] = []
    for i, line in enumerate(lines):
        match = FENCE_OPEN.match(line)
        if match:
            ticks = match.group("ticks")
            marker = ticks[0]
            info = match.group("info").strip()
            if stack:
                top = stack[-1]
                if marker == top["marker"] and len(ticks) >= top["len"]:
                    blocks.append((top["start"], top["info"], "\n".join(top["body"])))
                    stack.pop()
                    continue
            stack.append(
                {
                    "marker": marker,
                    "len": len(ticks),
                    "start": i + 1,
                    "info": info,
                    "body": [],
                }
            )
            continue
        if stack:
            stack[-1]["body"].append(line)
    for leftover in stack:
        blocks.append((leftover["start"], leftover["info"], "\n".join(leftover["body"])))
    return blocks


def _inline_code_spans(text: str) -> list[str]:
    """Inline `code` after fenced blocks have been blanked, so fences are ignored."""
    lines = text.splitlines(keepends=True)
    blanked: list[str] = []
    i = 0
    while i < len(lines):
        match = FENCE_OPEN.match(lines[i].rstrip("\n"))
        if match:
            ticks = match.group("ticks")
            marker = ticks[0]
            blanked.append("\n")
            i += 1
            while i < len(lines):
                closer = FENCE_OPEN.match(lines[i].rstrip("\n"))
                if (
                    closer
                    and closer.group("ticks")[0] == marker
                    and len(closer.group("ticks")) >= len(ticks)
                ):
                    blanked.append("\n")
                    i += 1
                    break
                blanked.append("\n")
                i += 1
            continue
        blanked.append(lines[i])
        i += 1
    return re.findall(r"`([^`\n]+)`", "".join(blanked))


class DocsMystMarkupTest(unittest.TestCase):
    def setUp(self) -> None:
        rng = random.Random()
        self.pages = _markdown_pages()
        self.assertGreater(len(self.pages), rng.randint(3, 8))

    def test_myst_substitutions_are_not_inside_fenced_or_inline_code(self) -> None:
        failures: list[str] = []
        for page in self.pages:
            rel = page.relative_to(REPO_ROOT)
            text = page.read_text(encoding="utf-8")
            for start, info, body in _fenced_blocks(text):
                if not _is_code_fence(info):
                    continue
                found = MUSTACHE.findall(body)
                if found:
                    names = ", ".join(sorted(set(found)))
                    failures.append(
                        f"{rel}:{start} fenced block ({info!r}) contains {{{{ {names} }}}}"
                    )
            for span in _inline_code_spans(text):
                found = MUSTACHE.findall(span)
                if found:
                    names = ", ".join(sorted(set(found)))
                    failures.append(
                        f"{rel} inline code `{span}` contains {{{{ {names} }}}}"
                    )
        self.assertEqual(
            failures,
            [],
            "MyST {{ substitutions }} are not expanded inside code. Use a "
            "{code-block} with :substitutions: and |name| (see installation.md "
            "docker-compose example), or a substitution whose value already "
            "includes the backticks.",
        )

    def test_ifconfig_uses_colon_fences(self) -> None:
        failures: list[str] = []
        for page in self.pages:
            rel = page.relative_to(REPO_ROOT)
            for lineno, line in enumerate(page.read_text(encoding="utf-8").splitlines(), 1):
                if BACKTICK_IFCONFIG.match(line):
                    failures.append(
                        f"{rel}:{lineno} {{ifconfig}} opened with backticks. "
                        "Use colon fences (::::{ifconfig}) so nested ``` "
                        "admonitions and code blocks do not close the directive."
                    )
        self.assertEqual(failures, [])


if __name__ == "__main__":
    unittest.main()
