#!/usr/bin/env python3
"""Convert Sphinx RST sources to MyST Markdown (best-effort)."""

from __future__ import annotations

import re
import sys
from pathlib import Path


SUBSTITUTIONS = [
    ("|year|", "{{ year }}"),
    ("|release|", "{{ release }}"),
    ("|version|", "{{ version }}"),
    ("|ES|_", "{{ ES }}"),
    ("|Tika|", "{{ Tika }}"),
    ("|Tika_format|_", "{{ Tika_format }}"),
    ("|Tika_version|_", "{{ Tika_version }}"),
    ("|Tika_configuring|_", "{{ Tika_configuring }}"),
    ("|ES_version7|_", "{{ ES_version7 }}"),
    ("|ES_version8|_", "{{ ES_version8 }}"),
    ("|ES_version9|_", "{{ ES_version9 }}"),
    ("|ES_stack_version|", "{{ ES_stack_version }}"),
    ("|FSCrawler_version|", "{{ FSCrawler_version }}"),
    ("|JPEG2000_version|_", "{{ JPEG2000_version }}"),
    ("|Download_URL|_", "{{ Download_URL }}"),
    ("|Maven_Central|_", "{{ Maven_Central }}"),
    ("|Sonatype|_", "{{ Sonatype }}"),
    ("|java_version|", "{{ java_version }}"),
]

UNDERLINE_LEVEL = {"=": 1, "-": 2, "~": 3, "^": 4, '"': 5, "'": 6}


def slugify(text: str) -> str:
    text = text.strip().lower().replace("'", "")
    text = re.sub(r"[^\w\s-]", "", text)
    return re.sub(r"[\s_]+", "-", text).strip("-")

DIRECTIVE_RE = re.compile(
    r"^\.\. (note|warning|hint|important|caution|tip|deprecated|versionadded|versionchanged)"
    r"(?:::|\s+::)\s*(.*)$"
)


def underline_level(line: str) -> int | None:
    if not line or line[0] not in UNDERLINE_LEVEL:
        return None
    char = line[0]
    if set(line) == {char} and len(line) >= 3:
        return UNDERLINE_LEVEL[char]
    return None


def convert_inline(text: str) -> str:
    for old, new in SUBSTITUTIONS:
        text = text.replace(old, new)
    text = re.sub(r":ref:`([^`<]+)\s*<([^>]+)>`", r"{ref}`\1 <\2>`", text)
    text = re.sub(r":ref:`([^`]+)`", r"{ref}`\1`", text)
    text = re.sub(r"`([^<`]+)\s*<([^>]+)>`__", r"[\1](\2)", text, flags=re.S)
    text = re.sub(r"`([^<`]+)\s*<([^>]+)>`_", r"[\1](\2)", text, flags=re.S)
    text = re.sub(
        r"`([^`]+)`_",
        lambda m: f"[{m.group(1)}](#{slugify(m.group(1))})",
        text,
    )
    text = re.sub(r"``([^`]+)``", r"`\1`", text)
    return text


def md_heading(level: int, title: str) -> str:
    level = max(1, min(level, 6))
    return f"{'#' * level} {title}"


class Converter:
    def __init__(self, lines: list[str]):
        self.lines = lines
        self.out: list[str] = []
        self.i = 0
        self.pending_label: str | None = None
        self.first_heading = True
        self.base_level: int | None = None

    def heading_md_level(self, rst_level: int) -> int:
        if self.base_level is None:
            self.base_level = rst_level
        return max(1, 1 + rst_level - self.base_level)

    def emit(self, *parts: str) -> None:
        self.out.extend(parts)

    def flush_admonition(self, admonition: dict | None) -> None:
        if not admonition:
            return
        arg = admonition.get("arg", "")
        header = f"{{{admonition['kind']}}}" + (f" {arg}" if arg else "")
        self.emit(f"```{header}")
        self.emit(*admonition["lines"])
        self.emit("```", "")

    def flush_codeblock(self, block: dict | None) -> None:
        if not block:
            return
        lang = block.get("lang", "")
        self.emit(f"```{lang}")
        self.emit(*block["lines"])
        self.emit("```", "")

    def read_indented_block(self, indent: int = 3) -> list[str]:
        block: list[str] = []
        while self.i < len(self.lines):
            line = self.lines[self.i]
            if line.startswith(" " * indent) or (line == "" and self.i + 1 < len(self.lines) and self.lines[self.i + 1].startswith(" " * indent)):
                block.append(line[indent:] if line.startswith(" " * indent) else "")
                self.i += 1
            elif line.strip() == "" and block:
                block.append("")
                self.i += 1
                if self.i < len(self.lines) and self.lines[self.i].startswith(" " * indent):
                    continue
                break
            else:
                break
        while block and block[-1] == "":
            block.pop()
        return block

    def read_eval_rst_block(self, first_line: str) -> list[str]:
        block = [first_line]
        self.i += 1
        while self.i < len(self.lines):
            line = self.lines[self.i]
            if line.strip() and not line.startswith(" ") and not line.startswith("\t"):
                if line.startswith(".. ") or line.startswith("+") or line.startswith("|"):
                    block.append(line)
                    self.i += 1
                    continue
                break
            block.append(line)
            self.i += 1
        while block and block[-1] == "":
            block.pop()
        return block

    def convert(self) -> str:
        while self.i < len(self.lines):
            line = self.lines[self.i]
            stripped = line.strip()

            label_match = re.match(r"^\.\. _([^:]+):\s*$", stripped)
            if label_match:
                self.pending_label = label_match.group(1)
                self.i += 1
                continue

            if stripped.startswith(".. contents::"):
                block = self.read_eval_rst_block(line)
                self.emit("```{contents}", *block, "```", "")
                continue

            if stripped.startswith(".. ifconfig::") or stripped.startswith(".. toctree::"):
                block = self.read_eval_rst_block(line)
                self.emit("```{eval-rst}", *block, "```", "")
                continue

            if stripped.startswith(".. list-table::") or stripped.startswith(".. csv-table::"):
                block = self.read_eval_rst_block(line)
                self.emit("```{eval-rst}", *block, "```", "")
                continue

            image_match = re.match(r"^\.\. image::\s+(\S+)", stripped)
            if image_match:
                path = image_match.group(1)
                opts: dict[str, str] = {}
                self.i += 1
                while self.i < len(self.lines) and self.lines[self.i].startswith("   :"):
                    opt_line = self.lines[self.i].strip()[1:]
                    if ":" in opt_line:
                        key, val = opt_line.split(":", 1)
                        opts[key.strip()] = val.strip()
                    self.i += 1
                alt = opts.get("alt", "")
                if "width" in opts:
                    self.emit(f'<img src="{path}" alt="{alt}" width="{opts["width"]}" />', "")
                elif "scale" in opts:
                    self.emit(f"![{alt}]({path})", "")
                else:
                    self.emit(f"![{alt}]({path})", "")
                continue

            directive_match = DIRECTIVE_RE.match(stripped)
            if directive_match:
                admonition = {
                    "kind": directive_match.group(1),
                    "arg": directive_match.group(2).strip(),
                    "lines": [],
                }
                self.i += 1
                admonition["lines"] = [convert_inline(x) for x in self.read_indented_block()]
                self.flush_admonition(admonition)
                continue

            code_match = re.match(r"^\.\. code(?:-block)?::\s*(\S*)", stripped)
            if code_match:
                lang = code_match.group(1) or ""
                self.i += 1
                if self.i < len(self.lines) and self.lines[self.i].strip() == "":
                    self.i += 1
                block_lines = self.read_indented_block()
                self.flush_codeblock({"lang": lang, "lines": block_lines})
                continue

            if stripped.startswith("+") and ("+" in stripped[1:] or "|" in stripped):
                table_lines = [line]
                self.i += 1
                while self.i < len(self.lines) and (
                    self.lines[self.i].strip().startswith("+") or self.lines[self.i].strip().startswith("|")
                ):
                    table_lines.append(self.lines[self.i])
                    self.i += 1
                self.emit("```{eval-rst}", *table_lines, "```", "")
                continue

            if self.i + 1 < len(self.lines):
                level = underline_level(self.lines[self.i + 1])
                if level and stripped:
                    md_level = self.heading_md_level(level)
                    if self.pending_label:
                        self.emit(f"({self.pending_label})=")
                        self.pending_label = None
                    self.emit(md_heading(md_level, convert_inline(stripped)), "")
                    self.i += 2
                    continue

            if stripped.endswith("::") and not stripped.startswith(".."):
                prefix = stripped[:-2].rstrip()
                if prefix:
                    self.emit(convert_inline(prefix))
                self.i += 1
                if self.i < len(self.lines) and self.lines[self.i].strip() == "":
                    self.i += 1
                block_lines = self.read_indented_block(4)
                self.flush_codeblock({"lang": "", "lines": block_lines})
                continue

            if stripped == "":
                self.emit("")
                self.i += 1
                continue

            if stripped.startswith("- ") or stripped.startswith("* ") or re.match(r"^\d+\.\s", stripped):
                self.emit(convert_inline(stripped))
                self.i += 1
                continue

            self.emit(convert_inline(line.rstrip()))
            self.i += 1

        result = "\n".join(self.out)
        result = re.sub(r"\n{3,}", "\n\n", result)
        return result.strip() + "\n"


def convert_rst(text: str) -> str:
    return Converter(text.splitlines()).convert()


def main() -> None:
    for path in sys.argv[1:]:
        src = Path(path)
        dst = src.with_suffix(".md")
        dst.write_text(convert_rst(src.read_text(encoding="utf-8")), encoding="utf-8")
        print(f"converted {src} -> {dst}")


if __name__ == "__main__":
    main()
