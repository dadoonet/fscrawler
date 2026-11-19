#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
POM="${1:-$ROOT_DIR/pom.xml}"

if [[ ! -f "$POM" ]]; then
  echo "pom.xml not found: $POM" >&2
  exit 1
fi

# Prefer xmllint if available; fall back to sed for the first elasticsearch.version property.
version=""
if command -v xmllint >/dev/null 2>&1; then
  version="$(xmllint --xpath "string(/*[local-name()='project']/*[local-name()='properties']/*[local-name()='elasticsearch.version'])" "$POM" 2>/dev/null || true)"
fi

if [[ -z "$version" ]]; then
  version="$(sed -n 's/.*<elasticsearch\.version>\([^<][^<]*\)<\/elasticsearch\.version>.*/\1/p' "$POM" | head -n 1)"
fi

if [[ -z "$version" ]]; then
  echo "Could not read elasticsearch.version from $POM" >&2
  exit 1
fi

printf '%s\n' "$version"
