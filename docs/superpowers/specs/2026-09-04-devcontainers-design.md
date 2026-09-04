# Design: Dev Containers for FSCrawler

**Date:** 2026-09-04  
**Status:** Approved for implementation planning  
**Scope:** Shared Dev Container config for local VS Code/Cursor and GitHub Codespaces

## Goals

- One-click / reopen-in-container environment for contributors
- Same config works for **local Dev Containers** and **GitHub Codespaces**
- JDK **25**, Maven, Git, and Docker-outside-of-Docker (DooD)
- Elasticsearch **9.x** available after create via Elastic **start-local** (ES-only by default)
- Document usage in `docs/source/dev/build.md` and add a Codespaces badge

## Non-goals

- Versioned Elasticsearch `docker-compose.yml` inside `.devcontainer/`
- Docker-in-Docker as the primary story
- Changing application Java code or default IT Testcontainers behavior
- Auto-starting Kibana (optional, documented only)

## Architecture

```
.devcontainer/
  devcontainer.json    # VS Code / Cursor / Codespaces entrypoint
  post-create.sh       # start-local + Maven dependency warm-up
docs/source/dev/build.md   # Dev Containers / Codespaces section + badge
```

- **App container:** Microsoft Dev Containers Java feature stack with **JDK 25** and Maven
- **Docker access:** `docker-outside-of-docker` feature (host Docker socket) so start-local can run
- **Elasticsearch:** started in `postCreateCommand` via [start-local](https://github.com/elastic/start-local) under `IGNORE_ME/` (already gitignored)
- **No** sibling compose service managed by Dev Containers for ES — start-local owns the generated compose / `.env`

## Components

### `devcontainer.json`

- Base: Ubuntu + Dev Container feature `ghcr.io/devcontainers/features/java` with `version: "25"` and Maven enabled (or equivalent Microsoft Java 25 image if available and equivalent)
- Features:
  - `java` (JDK 25 + Maven)
  - `docker-outside-of-docker`
  - Git (via image/feature as needed)
- `forwardPorts`: `9200` (Elasticsearch); document `5601` for optional Kibana
- VS Code extensions: Extension Pack for Java, Maven for Java
- `postCreateCommand`: `bash .devcontainer/post-create.sh`
- `remoteUser`: default `vscode`

### `post-create.sh`

1. Ensure `IGNORE_ME/` exists
2. If Elasticsearch already responds on `http://localhost:9200` → skip start-local
3. Otherwise run start-local with:
   - `ES_LOCAL_PASSWORD=changeme`
   - `ES_LOCAL_DIR=IGNORE_ME/elastic-start-local` (or equivalent under `IGNORE_ME/`)
   - `-v <elasticsearch.version from pom.xml>` (pin to current default, e.g. `9.5.2`)
   - `--esonly`
4. Warm Maven deps: `mvn -q dependency:go-offline -DskipTests` (or closest practical equivalent used by the project)
5. Print how to run ITs against the cluster using `tests.cluster.url` and `tests.cluster.apiKey` from `IGNORE_ME/elastic-start-local/.env` (`ES_LOCAL_API_KEY`)

### Kibana (optional)

- Not started by default
- Document: re-run start-local **without** `--esonly` (same version / password / dir) when Kibana is needed

### Version pinning

- JDK: **25**
- Elasticsearch: **9.x**, aligned with `<elasticsearch.version>` in root `pom.xml`
- Script should read the version from `pom.xml` when practical; otherwise keep a clearly marked constant updated with that property

## Developer workflow

1. Open repo in Dev Container or Codespaces
2. Wait for `post-create.sh` (start-local + Maven warm-up)
3. Build: `mvn clean package -DskipTests -Ddocker.skip` (or full build as needed)
4. Integration tests against start-local:

```bash
source IGNORE_ME/elastic-start-local/.env
mvn verify ... -Dtests.cluster.url=http://localhost:9200 -Dtests.cluster.apiKey="$ES_LOCAL_API_KEY"
```

(Exact Maven module flags follow existing project docs.)

## Documentation

Update `docs/source/dev/build.md`:

- Codespaces badge pointing at this repository
- Section explaining Dev Containers / Codespaces
- Prerequisites (Docker on the host for local Dev Containers)
- What `post-create` does
- How to run builds and ITs against start-local
- How to enable Kibana optionally

## Error handling / edge cases

- **ES already running:** skip start-local; continue with Maven warm-up
- **start-local / Docker failure:** exit non-zero with a clear message that Docker socket / DooD is required (fail-fast; do not hide a broken Docker setup behind a successful Maven warm-up)
- **Network required** on first create to download start-local and Maven artifacts
- **Codespaces create time:** Maven `go-offline` may be slow; acceptable per product decision
- Generated start-local files must stay under `IGNORE_ME/` and never be committed

## Testing / verification for this change

- Static review of `.devcontainer/*` and docs
- Where possible in the agent environment: shellcheck-style sanity, JSON validity, dry-run of version extraction from `pom.xml`
- Full Codespaces boot is not required to merge; manual smoke by maintainer is enough for v1

## Decisions log

| Topic | Choice |
|-------|--------|
| Target | Local Dev Containers + Codespaces |
| JDK | 25 |
| ES | 9.x via start-local `--esonly` |
| Docker in container | DooD (socket), for start-local |
| Kibana | Optional, documented |
| Maven warm-up | Yes (`dependency:go-offline`) |
| Docs | `build.md` + Codespaces badge |
| Image approach | Microsoft Dev Containers Java features (approach 1) |
