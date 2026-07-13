#!/usr/bin/env bash
# Wait until an Elasticsearch cluster answers HTTP 200 on GET / with API key auth.
# Used in CI before cloud integration tests to wake up cold Elastic Cloud deployments.
set -euo pipefail

CLUSTER_URL="${TESTS_CLUSTER_URL:?TESTS_CLUSTER_URL is required}"
API_KEY="${TESTS_CLUSTER_APIKEY:?TESTS_CLUSTER_APIKEY is required}"
MAX_DURATION_SECONDS="${TESTS_CLUSTER_WARMUP_SECONDS:-300}"

CLUSTER_URL="${CLUSTER_URL%/}"

start_time=$(date +%s)
delay=1

echo "Warming up Elasticsearch cluster (max ${MAX_DURATION_SECONDS}s)..."

while true; do
  now=$(date +%s)
  elapsed=$((now - start_time))
  if [ "$elapsed" -ge "$MAX_DURATION_SECONDS" ]; then
    echo "ERROR: Cluster root endpoint did not return HTTP 200 within ${MAX_DURATION_SECONDS}s."
    exit 1
  fi

  http_code=$(curl -sS -o /dev/null -w "%{http_code}" \
    -H "Authorization: ApiKey ${API_KEY}" \
    "${CLUSTER_URL}/" 2>/dev/null || echo "000")

  if [ "$http_code" = "200" ]; then
    echo "Cluster is ready (HTTP 200) after ${elapsed}s."
    exit 0
  fi

  echo "Root endpoint returned HTTP ${http_code}. Cluster might still be cold. Retrying in ${delay}s..."
  sleep "$delay"
  delay=$((delay * 2))
  if [ "$delay" -gt 10 ]; then
    delay=10
  fi
done
