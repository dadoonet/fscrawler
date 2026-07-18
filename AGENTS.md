# AGENTS.md

## Cursor Cloud specific instructions

FSCrawler is a Java 17+ Maven multi-module project (a file system crawler that parses
documents with Apache Tika and indexes them into Elasticsearch). The VM snapshot already
has **JDK 21** and **Maven** installed, and the update script pre-fetches Maven dependencies
(`mvn dependency:go-offline`). Standard build/test/run commands are already documented — see
`.claude/rules/build-commands.md`, `.claude/rules/testing.md`, and `docs/source/dev/build.md`.
Quick reference: build `mvn clean package -DskipTests -Ddocker.skip`, lint
`mvn spotless:check` (use `-DratchetFrom=NONE` for the whole tree, `mvn spotless:apply` to fix),
unit tests `mvn test -DskipIntegTests`.

### Docker is required for integration tests and local Elasticsearch (start it each session)

Docker is installed in the snapshot, but the daemon does **not** start automatically. Before
running integration tests (they use TestContainers) or starting a local Elasticsearch, start it:

```bash
sudo dockerd    # run in the background (e.g. a tmux session); leave it running
sudo chmod 666 /var/run/docker.sock   # so the non-root `ubuntu` user can reach the socket
```

Notes:
- The daemon is configured for `fuse-overlayfs` with the containerd-snapshotter feature disabled
  (`/etc/docker/daemon.json`) and uses iptables-legacy — this is required for Docker-in-Docker
  in this VM. Do not switch the storage driver back to `overlay2`.
- Integration tests (`*IT.java`, run with `mvn verify ... -Dit.test=...`) auto-start an
  Elasticsearch container via TestContainers when no external cluster is provided.

### Running the app / integration tests against a local Elasticsearch

Use the `start-elasticsearch` skill (`.claude/skills/start-elasticsearch/SKILL.md`), which runs
Elastic's `start-local` (needs Docker). Run it inside the git-ignored `IGNORE_ME/` directory so
nothing is committed. It brings up Elasticsearch on `http://localhost:9200` (user `elastic`,
password `changeme`) plus Kibana on `http://localhost:5601`, and writes an API key to
`IGNORE_ME/elastic-start-local/.env` (`ES_LOCAL_API_KEY`).

Run integration tests against that cluster instead of TestContainers with:
`-Dtests.cluster.url=http://localhost:9200 -Dtests.cluster.apiKey=<ES_LOCAL_API_KEY>`.

### FSCrawler job settings gotchas (non-obvious)

When writing a job `_settings.yaml` to point FSCrawler at a local `start-local` cluster:
- Use the `elasticsearch.urls` **list** key (not `nodes:`). An invalid/unknown key is silently
  ignored and FSCrawler falls back to the default `https://127.0.0.1:9200`, which fails against
  the HTTP-only `start-local` endpoint with `SSLException: Unsupported or unrecognized SSL message`.
- Use `http://` (not `https://`) for `start-local`.
- Do **not** set `elasticsearch.index` to the same value as the job `name`. FSCrawler creates an
  alias named after the job, so a custom index with that name fails with
  `alias name [...] self-conflicts with index name`. Leave `index` unset to use the defaults
  (`<name>_docs` and `<name>_folder`).

Example minimal run (crawl once and exit):

```bash
# distribution ZIP is produced at distribution/target/fscrawler-distribution-3.0-SNAPSHOT.zip
bin/fscrawler --config_dir <config_dir> <job-name> --loop 1
```
