# Dev Containers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a shared Dev Container (local VS Code/Cursor + GitHub Codespaces) with JDK 25, Maven, Docker-outside-of-Docker, Elasticsearch via start-local (ES-only), and docs with a Codespaces badge.

**Architecture:** Microsoft `devcontainers/java:25` image plus Maven (via Java feature with `version: "none"`) and `docker-outside-of-docker`. `postCreateCommand` runs `.devcontainer/post-create.sh`, which starts Elastic start-local under `IGNORE_ME/` and warms Maven dependencies.

**Tech Stack:** Dev Containers, JDK 25, Maven, Docker-outside-of-Docker, Elastic start-local, MyST Markdown docs

**Spec:** `docs/superpowers/specs/2026-09-04-devcontainers-design.md`

## Global Constraints

- JDK **25** (`mcr.microsoft.com/devcontainers/java:25`)
- Elasticsearch **9.x** from root `pom.xml` property `<elasticsearch.version>` (currently `9.5.2`)
- start-local with `--esonly`, `ES_LOCAL_PASSWORD=changeme`, files under `IGNORE_ME/`
- Docker-outside-of-Docker required; fail-fast if start-local cannot run
- No versioned ES compose in `.devcontainer/`
- Commit messages: `type(scope): emoji description` with detail bullets
- Branch: `cursor/add-devcontainers-ab5d` (already exists)

## File map

| File | Responsibility |
|------|----------------|
| `.devcontainer/devcontainer.json` | Container image, features, ports, extensions, postCreate |
| `.devcontainer/post-create.sh` | Idempotent ES start-local + Maven warm-up + usage hints |
| `.devcontainer/extract-es-version.sh` | Read `<elasticsearch.version>` from root `pom.xml` |
| `docs/source/dev/build.md` | Codespaces badge + Dev Containers section |

---

### Task 1: Extract Elasticsearch version from `pom.xml`

**Files:**
- Create: `.devcontainer/extract-es-version.sh`
- Test: run the script against the real root `pom.xml` (no new Java test module)

**Interfaces:**
- Consumes: root `pom.xml` path (default: repo root `pom.xml`)
- Produces: stdout = version string only (e.g. `9.5.2`); exit `0` on success, non-zero on failure

- [ ] **Step 1: Write the script skeleton that fails until parsing works**

Create `.devcontainer/extract-es-version.sh`:

```bash
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
```

- [ ] **Step 2: Make executable and run against the real pom**

```bash
chmod +x .devcontainer/extract-es-version.sh
./.devcontainer/extract-es-version.sh
```

Expected: prints `9.5.2` (or whatever `<elasticsearch.version>` is in root `pom.xml`).

- [ ] **Step 3: Negative check — missing file fails**

```bash
./.devcontainer/extract-es-version.sh /tmp/does-not-exist-pom.xml ; echo exit:$?
```

Expected: non-zero exit and error on stderr.

- [ ] **Step 4: Commit**

```bash
git add .devcontainer/extract-es-version.sh
git commit -m "$(cat <<'EOF'
feat(devcontainer): ✨ add elasticsearch.version extractor for start-local

- Read root pom.xml property for Dev Container post-create
- Support xmllint with sed fallback
EOF
)"
```

---

### Task 2: `post-create.sh` (start-local + Maven warm-up)

**Files:**
- Create: `.devcontainer/post-create.sh`
- Consumes: `.devcontainer/extract-es-version.sh`

**Interfaces:**
- Consumes: `extract-es-version.sh` → version string; Docker CLI; network for start-local curl
- Produces: `IGNORE_ME/elastic-start-local/` with running ES (or skip if already up); warmed local Maven repo; exit non-zero if start-local fails when ES is down

- [ ] **Step 1: Create `.devcontainer/post-create.sh`**

```bash
#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

ES_DIR="$ROOT_DIR/IGNORE_ME/elastic-start-local"
ES_URL="${ES_LOCAL_URL:-http://localhost:9200}"
ES_PASSWORD="${ES_LOCAL_PASSWORD:-changeme}"

echo "==> FSCrawler Dev Container post-create"

mkdir -p "$ROOT_DIR/IGNORE_ME"

es_up() {
  curl -fsS -u "elastic:${ES_PASSWORD}" "$ES_URL" >/dev/null 2>&1 \
    || curl -fsS "$ES_URL" >/dev/null 2>&1
}

if es_up; then
  echo "==> Elasticsearch already reachable at $ES_URL — skipping start-local"
else
  if ! command -v docker >/dev/null 2>&1; then
    echo "ERROR: docker CLI not found. Docker-outside-of-Docker is required for start-local." >&2
    exit 1
  fi
  if ! docker info >/dev/null 2>&1; then
    echo "ERROR: cannot talk to Docker daemon (socket / permissions)." >&2
    exit 1
  fi

  ES_VERSION="$("$ROOT_DIR/.devcontainer/extract-es-version.sh")"
  echo "==> Starting Elasticsearch ${ES_VERSION} via start-local (ES-only) under IGNORE_ME/"

  # If a previous install exists, prefer start.sh; otherwise bootstrap.
  if [[ -x "$ES_DIR/start.sh" ]]; then
    (cd "$ES_DIR" && ./start.sh)
  else
    curl -fsSL https://elastic.co/start-local \
      | ES_LOCAL_PASSWORD="$ES_PASSWORD" ES_LOCAL_DIR="$ES_DIR" \
        sh -s -- -v "$ES_VERSION" --esonly
  fi

  echo "==> Waiting for Elasticsearch at $ES_URL"
  for i in $(seq 1 60); do
    if es_up; then
      echo "==> Elasticsearch is up"
      break
    fi
    if [[ "$i" -eq 60 ]]; then
      echo "ERROR: Elasticsearch did not become ready in time" >&2
      exit 1
    fi
    sleep 2
  done
fi

echo "==> Warming Maven dependencies (dependency:go-offline)"
mvn -q dependency:go-offline -DskipTests

echo
echo "==> Ready."
echo "    Build:  mvn clean package -DskipTests -Ddocker.skip"
echo "    ITs vs start-local:"
if [[ -f "$ES_DIR/.env" ]]; then
  # shellcheck disable=SC1090
  set -a
  # shellcheck disable=SC1091
  source "$ES_DIR/.env"
  set +a
  echo "      source IGNORE_ME/elastic-start-local/.env"
  echo "      mvn verify -pl fr.pilato.elasticsearch.crawler:fscrawler-it \\"
  echo "        -Dtests.cluster.url=http://localhost:9200 \\"
  echo "        -Dtests.cluster.apiKey=\"\$ES_LOCAL_API_KEY\""
else
  echo "      (start-local .env not found yet — re-run post-create or start-local)"
fi
echo "    Optional Kibana: re-run start-local without --esonly (same ES_LOCAL_DIR / password / version)."
```

- [ ] **Step 2: Make executable and syntax-check**

```bash
chmod +x .devcontainer/post-create.sh
bash -n .devcontainer/post-create.sh
bash -n .devcontainer/extract-es-version.sh
```

Expected: no output (syntax OK).

- [ ] **Step 3: Commit**

```bash
git add .devcontainer/post-create.sh
git commit -m "$(cat <<'EOF'
feat(devcontainer): ✨ add post-create start-local and Maven warm-up

- Start ES-only via Elastic start-local under IGNORE_ME/
- Fail fast when Docker is unavailable; skip if ES already up
- Run mvn dependency:go-offline and print IT hints
EOF
)"
```

---

### Task 3: `devcontainer.json`

**Files:**
- Create: `.devcontainer/devcontainer.json`

**Interfaces:**
- Consumes: `.devcontainer/post-create.sh`
- Produces: Dev Container / Codespaces definition (JDK 25, Maven, DooD, port 9200)

- [ ] **Step 1: Create `.devcontainer/devcontainer.json`**

```json
{
  "name": "FSCrawler",
  "image": "mcr.microsoft.com/devcontainers/java:25",
  "features": {
    "ghcr.io/devcontainers/features/java:1": {
      "version": "none",
      "installMaven": true,
      "installGradle": false
    },
    "ghcr.io/devcontainers/features/docker-outside-of-docker:1": {
      "moby": true
    }
  },
  "forwardPorts": [9200, 5601],
  "portsAttributes": {
    "9200": {
      "label": "Elasticsearch",
      "onAutoForward": "notify"
    },
    "5601": {
      "label": "Kibana (optional)",
      "onAutoForward": "silent"
    }
  },
  "postCreateCommand": "bash .devcontainer/post-create.sh",
  "customizations": {
    "vscode": {
      "extensions": [
        "vscjava.vscode-java-pack",
        "vscjava.vscode-maven"
      ],
      "settings": {
        "java.configuration.updateBuildConfiguration": "automatic",
        "java.compile.nullAnalysis.mode": "automatic"
      }
    }
  },
  "remoteUser": "vscode"
}
```

- [ ] **Step 2: Validate JSON**

```bash
python3 -c 'import json; json.load(open(".devcontainer/devcontainer.json")); print("ok")'
```

Expected: `ok`

- [ ] **Step 3: Commit**

```bash
git add .devcontainer/devcontainer.json
git commit -m "$(cat <<'EOF'
feat(devcontainer): ✨ add Dev Container config for JDK 25 and Codespaces

- Use microsoft/devcontainers java:25 with Maven and DooD
- Forward ES 9200 and optional Kibana 5601
- Wire postCreateCommand to start-local + Maven warm-up
EOF
)"
```

---

### Task 4: Documentation (`build.md` + Codespaces badge)

**Files:**
- Modify: `docs/source/dev/build.md`

**Interfaces:**
- Documents Tasks 1–3 for contributors

- [ ] **Step 1: Add Codespaces badge near the top of `docs/source/dev/build.md`**

After the JetBrains thanks block (around lines 4–7), add:

```markdown
[![Open in GitHub Codespaces](https://github.com/codespaces/badge.svg)](https://codespaces.new/dadoonet/fscrawler)
```

- [ ] **Step 2: Add a Dev Containers / Codespaces section**

Insert a new section after `## Clone the project` (or immediately before `## Build the artifact`) with:

```markdown
## Dev Containers and GitHub Codespaces

You can develop FSCrawler in [VS Code](https://code.visualstudio.com/) /
[Cursor](https://cursor.com/) Dev Containers or in
[GitHub Codespaces](https://github.com/features/codespaces) using the shared
configuration under `.devcontainer/`.

### Prerequisites (local Dev Containers)

* Docker Desktop (or another Docker engine) on the host
* VS Code / Cursor with the Dev Containers extension

Open the repository and use **Reopen in Container**. For Codespaces, use the
badge above or open a codespace from the GitHub UI.

### What the container provides

* JDK 25 and Maven
* Docker-outside-of-Docker (required for Elastic [start-local](https://github.com/elastic/start-local))
* After create, `post-create.sh`:
  * Starts Elasticsearch **9.x** (version from `<elasticsearch.version>` in the root `pom.xml`) with start-local **ES-only** under `IGNORE_ME/elastic-start-local/`
  * Uses password `changeme` for the `elastic` user
  * Warms the local Maven repository (`dependency:go-offline`)

Elasticsearch is then available at `http://localhost:9200`.

### Build and test inside the container

```shell
mvn clean package -DskipTests -Ddocker.skip
```

To run integration tests against the start-local cluster instead of Testcontainers:

```shell
source IGNORE_ME/elastic-start-local/.env
mvn verify -pl fr.pilato.elasticsearch.crawler:fscrawler-it \
  -Dtests.cluster.url=http://localhost:9200 \
  -Dtests.cluster.apiKey="$ES_LOCAL_API_KEY"
```

### Optional Kibana

By default only Elasticsearch is started (`--esonly`). To also run Kibana, re-run
start-local **without** `--esonly`, using the same directory, password, and version,
for example:

```shell
ES_VERSION="$(./.devcontainer/extract-es-version.sh)"
curl -fsSL https://elastic.co/start-local \
  | ES_LOCAL_PASSWORD=changeme ES_LOCAL_DIR="$PWD/IGNORE_ME/elastic-start-local" \
    sh -s -- -v "$ES_VERSION"
```

Kibana listens on `http://localhost:5601` when enabled.
```

Match the surrounding MyST style (fenced shell blocks, `*` lists). Adjust wording only if needed for Sphinx/MyST consistency; do not invent settings keys.

- [ ] **Step 3: Spot-check the section renders as valid MyST** (no unclosed fences)

```bash
# Count fences in the new section mentally / via editor; ensure even number of ``` lines in the file region
python3 - <<'PY'
from pathlib import Path
text = Path("docs/source/dev/build.md").read_text()
assert "codespaces.new/dadoonet/fscrawler" in text
assert "Dev Containers and GitHub Codespaces" in text
assert "start-local" in text
print("doc checks ok")
PY
```

Expected: `doc checks ok`

- [ ] **Step 4: Commit**

```bash
git add docs/source/dev/build.md
git commit -m "$(cat <<'EOF'
docs(devcontainer): 📝 document Dev Containers and Codespaces

- Add Codespaces badge to build guide
- Describe post-create, start-local ES-only, IT flags, optional Kibana
EOF
)"
```

---

### Task 5: Final verification and PR update

**Files:**
- Review only (no new code unless fixes)

- [ ] **Step 1: Verify all artifacts exist and are executable**

```bash
test -f .devcontainer/devcontainer.json
test -x .devcontainer/post-create.sh
test -x .devcontainer/extract-es-version.sh
./.devcontainer/extract-es-version.sh | grep -E '^[0-9]+\.[0-9]+\.[0-9]+'
python3 -c 'import json; json.load(open(".devcontainer/devcontainer.json"))'
bash -n .devcontainer/post-create.sh
```

Expected: all succeed; version like `9.5.2`.

- [ ] **Step 2: Push branch and update PR description**

```bash
git push -u origin cursor/add-devcontainers-ab5d
```

Update PR #2539 body to mark implementation complete and list the verification commands above.

- [ ] **Step 3: Spec coverage self-check**

Confirm each spec requirement maps to a task:

| Spec item | Task |
|-----------|------|
| Local + Codespaces shared config | 3 |
| JDK 25 + Maven + DooD | 3 |
| start-local ES-only under IGNORE_ME | 2 |
| Version from pom | 1 |
| Maven go-offline | 2 |
| Fail-fast on Docker | 2 |
| Skip if ES up | 2 |
| Docs + badge | 4 |
| Optional Kibana documented | 4 |
| No versioned ES compose in `.devcontainer/` | (none added) |

---

## Self-review (plan author)

1. **Spec coverage:** All approved decisions covered in Tasks 1–5.
2. **Placeholders:** None; scripts and JSON are complete.
3. **Consistency:** `ES_DIR=IGNORE_ME/elastic-start-local`, password `changeme`, image `java:25`, IT module `fr.pilato.elasticsearch.crawler:fscrawler-it` match existing docs.
