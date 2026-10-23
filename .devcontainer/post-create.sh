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
