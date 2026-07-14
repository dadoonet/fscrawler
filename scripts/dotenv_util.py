"""Minimal .env loader with quote stripping and ${VAR} expansion."""

from __future__ import annotations

import os
import re
from pathlib import Path

_ENV_REF = re.compile(r"\$\{([^}]+)\}")


def _strip_quotes(value: str) -> str:
    if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
        return value[1:-1]
    return value


def _expand(value: str, values: dict[str, str]) -> str:
    previous = None
    current = value
    while previous != current:
        previous = current
        current = _ENV_REF.sub(
            lambda match: values.get(match.group(1), os.environ.get(match.group(1), "")),
            current,
        )
    return current


def load_dotenv(env_path: Path, *, override: bool = False) -> None:
    if not env_path.is_file():
        return

    raw: dict[str, str] = {}
    for line in env_path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, _, value = line.partition("=")
        key = key.strip()
        value = _strip_quotes(value.strip())
        if key:
            raw[key] = value

    expanded = {key: _expand(value, raw) for key, value in raw.items()}
    for key, value in expanded.items():
        if override or key not in os.environ:
            os.environ[key] = value
