#!/usr/bin/env python3
"""Convert simple RST grid tables in {eval-rst} blocks to Markdown pipe tables."""

from __future__ import annotations

import re
import sys
from pathlib import Path


def slugify(text: str) -> str:
    text = text.strip().lower().replace("'", "")
    text = re.sub(r"[^\w\s-]", "", text)
    return re.sub(r"[\s_]+", "-", text).strip("-")


def convert_cell(cell: str) -> str:
    cell = cell.strip()
    cell = re.sub(r":ref:`([^`<]+)\s*<([^>]+)>`", r"{ref}`\1 <\2>`", cell)
    cell = re.sub(r":ref:`([^`]+)`", r"{ref}`\1`", cell)
    cell = re.sub(r"`([^<`]+)\s*<([^>]+)>`__", r"[\1](\2)", cell)
    cell = re.sub(r"`([^`]+)`_", lambda m: f"[{m.group(1)}](#{slugify(m.group(1))})", cell)
    cell = re.sub(r"``([^`]+)``", r"`\1`", cell)
    return cell


def parse_grid_table(block: str) -> list[list[str]] | None:
    rows: list[list[str]] = []
    current: list[str] | None = None

    for line in block.splitlines():
        stripped = line.strip()
        if not stripped.startswith("|"):
            continue
        cells = [c.strip() for c in stripped.strip("|").split("|")]
        if current is not None and cells and cells[0] == "":
            for i, value in enumerate(cells):
                if value and i < len(current):
                    current[i] = f"{current[i]} {value}".strip()
            continue
        if current is not None:
            rows.append(current)
        current = cells

    if current is not None:
        rows.append(current)

    if len(rows) < 2:
        return None
    return rows


def rows_to_markdown(rows: list[list[str]]) -> str:
    col_count = max(len(row) for row in rows)
    normalized = [row + [""] * (col_count - len(row)) for row in rows]
    header = "| " + " | ".join(convert_cell(c) for c in normalized[0]) + " |"
    sep = "| " + " | ".join("---" for _ in range(col_count)) + " |"
    body = [
        "| " + " | ".join(convert_cell(c) for c in row) + " |"
        for row in normalized[1:]
    ]
    return "\n".join([header, sep, *body])


def convert_block(block: str) -> str | None:
    if not re.search(r"^\+[-=+]", block, re.M):
        return None
    rows = parse_grid_table(block)
    if rows is None:
        return None
    return rows_to_markdown(rows)


def convert_file(path: Path) -> tuple[int, list[str]]:
    text = path.read_text(encoding="utf-8")
    skipped: list[str] = []
    converted = 0

    def replacer(match: re.Match[str]) -> str:
        nonlocal converted
        block = match.group(1).strip("\n")
        if block.lstrip().startswith(".. "):
            return match.group(0)
        md = convert_block(block)
        if md is None:
            skipped.append(block.splitlines()[0][:80])
            return match.group(0)
        converted += 1
        return md + "\n"

    new_text = re.sub(
        r"```\{eval-rst\}\n(.*?)```",
        replacer,
        text,
        flags=re.S,
    )
    if new_text != text:
        path.write_text(new_text, encoding="utf-8")
    return converted, skipped


def convert_list_table_block(block: str) -> str | None:
    if not block.lstrip().startswith(".. list-table::"):
        return None
    rows: list[list[str]] = []
    for line in block.splitlines():
        m = re.match(r"\s*\*\s*-\s*(.*)", line)
        if not m:
            continue
        first = m.group(1).strip()
        continuation = re.match(r"\s*-\s*(.*)", line)
        if m:
            # collect multi-line cells: next lines starting with spaces and -
            pass
    # simpler line-by-line parse
    current_row: list[str] = []
    rows = []
    for line in block.splitlines():
        if re.match(r"\s*\*\s*-\s*", line):
            if current_row:
                rows.append(current_row)
            parts = re.split(r"\s*-\s*", line.strip().lstrip("*").strip(), maxsplit=1)
            rest = line.split("-", 1)
            cells = re.findall(r"-\s*(.*)", line)
            # use regex for * - cell1 \n - cell2 pattern
        ...
    return None


def main() -> None:
    roots = [Path(p) for p in sys.argv[1:]] if len(sys.argv) > 1 else [Path("docs/source")]
    total = 0
    manual: dict[str, list[str]] = {}

    for root in roots:
        for path in sorted(root.rglob("*.md")):
            count, skipped = convert_file(path)
            if count:
                print(f"converted {count} table(s) in {path}")
                total += count
            if skipped:
                manual[str(path)] = skipped

    print(f"\nTotal converted: {total}")
    if manual:
        print("\nSkipped blocks (manual):")
        for path, blocks in manual.items():
            print(f"  {path}")


if __name__ == "__main__":
    main()
