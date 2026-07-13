#!/usr/bin/env bash
# Wait until an Elasticsearch cluster answers HTTP 200 on GET / with API key auth.
# Used in CI before cloud integration tests to wake up cold Elastic Cloud deployments.
set -euo pipefail

CLUSTER_URL="${TESTS_CLUSTER_URL:?TESTS_CLUSTER_URL is required}"
API_KEY="${TESTS_CLUSTER_APIKEY:?TESTS_CLUSTER_APIKEY is required}"
MAX_DURATION_SECONDS="${TESTS_CLUSTER_WARMUP_SECONDS:-300}"

CLUSTER_URL="${CLUSTER_URL%/}"

get_public_ip() {
  curl -fsSL --max-time 5 https://api.ipify.org 2>/dev/null \
    || curl -fsSL --max-time 5 https://ifconfig.me/ip 2>/dev/null \
    || echo "unknown (could not determine public IP)"
}

report_error() {
  local message="$1"
  local public_ip="$2"
  echo "ERROR: ${message}" >&2
  echo "Public outbound IP (as seen by external services): ${public_ip}" >&2
}

start_time=$(date +%s)
delay=1

echo "Warming up Elasticsearch cluster (max ${MAX_DURATION_SECONDS}s)..."

while true; do
  now=$(date +%s)
  elapsed=$((now - start_time))
  if [ "$elapsed" -ge "$MAX_DURATION_SECONDS" ]; then
    public_ip=$(get_public_ip)
    report_error "Cluster root endpoint did not return HTTP 200 within ${MAX_DURATION_SECONDS}s." "${public_ip}"
    exit 1
  fi

  http_code=$(curl -sS -o /dev/null -w "%{http_code}" \
    -H "Authorization: ApiKey ${API_KEY}" \
    "${CLUSTER_URL}/" 2>/dev/null || echo "000")

  if [ "$http_code" = "200" ]; then
    echo "Cluster is ready (HTTP 200) after ${elapsed}s."
    exit 0
  fi

  public_ip=$(get_public_ip)
  echo "Root endpoint returned HTTP ${http_code} from public IP ${public_ip}. Cluster might still be cold. Retrying in ${delay}s..."
  sleep "$delay"
  delay=$((delay * 2))
  if [ "$delay" -gt 10 ]; then
    delay=10
  fi
done
