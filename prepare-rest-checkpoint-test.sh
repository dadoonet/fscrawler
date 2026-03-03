#!/usr/bin/env bash
#
# Setup script for manual REST checkpoint testing (pause, resume, status, checkpoint).
# Run from the project root. Ensures: Elasticsearch check, optional API key creation,
# Maven build, unzip distribution, job config, and optionally starts FSCrawler with REST.
#
# Prerequisites: Elasticsearch (start with script if needed), Java 17+.
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR"
DIST_TARGET="$PROJECT_ROOT/distribution/target"
DOCS_SOURCE="$PROJECT_ROOT/test-documents/target/classes/documents"
JOB_NAME="${FSCRAWLER_JOB:-rest_checkpoint_test}"
ES_URL="${ELASTICSEARCH_URL:-http://127.0.0.1:9200}"
REST_URL="${REST_URL:-http://127.0.0.1:8080}"
ES_PASSWORD="${ES_LOCAL_PASSWORD:-changeme}"
FSCRAWLER_HOME="${FSCRAWLER_HOME:-}"

echo "=== FSCrawler REST checkpoint test – setup ==="
echo "  Project root: $PROJECT_ROOT"
echo "  Job name:     $JOB_NAME"
echo "  Docs path:    $DOCS_SOURCE"
echo "  Elasticsearch: $ES_URL"
echo "  REST URL:     $REST_URL"
echo ""

# 0. Check Elasticsearch is running; if not, print start command and exit
echo "--- 0. Check Elasticsearch ---"
ES_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$ES_URL" 2>/dev/null || echo "000")
if [ "$ES_STATUS" != "200" ] && [ "$ES_STATUS" != "401" ]; then
  echo "Elasticsearch does not appear to be running at $ES_URL (got HTTP $ES_STATUS)."
  echo ""
  echo "Start a local Elasticsearch with:"
  echo "  curl -fsSL https://elastic.co/start-local | ES_LOCAL_PASSWORD=\"changeme\" sh"
  echo ""
  echo "Then re-run this script. Set ES_LOCAL_PASSWORD if you used another password."
  exit 1
fi
echo "Elasticsearch is running at $ES_URL"

# 1. Maven build (distribution + test-documents for classes/documents)
echo "--- 1. Maven build ---"
if ! [ -d "$DOCS_SOURCE" ]; then
  echo "Building test-documents and distribution..."
  mvn clean compile -pl test-documents -q
  mvn package -DskipTests -pl distribution -am -q
else
  echo "Building distribution (test-documents already present)..."
  mvn package -DskipTests -pl distribution -am -q
fi

# 2. Unzip distribution
echo "--- 2. Unzip distribution ---"
ZIP=$(ls "$DIST_TARGET"/fscrawler*.zip 2>/dev/null | head -1)
if [ -z "$ZIP" ]; then
  echo "No zip found in $DIST_TARGET. Run: mvn package -DskipTests -pl distribution -am"
  exit 1
fi
echo "Using: $ZIP"
cd "$DIST_TARGET"
unzip -o -q "$(basename "$ZIP")"
# Top-level dir created by unzip (e.g. fscrawler-distribution-2.10-SNAPSHOT)
EXTRACTED=$(find . -maxdepth 1 -type d -name 'fscrawler*' ! -name '.' | head -1)
if [ -z "$EXTRACTED" ]; then
  EXTRACTED="."
fi
FSCRAWLER_HOME="$DIST_TARGET/${EXTRACTED#./}"
cd "$PROJECT_ROOT"
echo "FSCrawler home: $FSCRAWLER_HOME"

# 3. Create job config
echo "--- 3. Create job: $JOB_NAME ---"
if ! [ -d "$DOCS_SOURCE" ]; then
  echo "ERROR: Documents dir not found: $DOCS_SOURCE"
  echo "Run: mvn compile -pl test-documents"
  exit 1
fi
DOCS_ABS=$(cd "$DOCS_SOURCE" && pwd)
CONFIG_DIR="$FSCRAWLER_HOME/config"
JOB_DIR="$CONFIG_DIR/$JOB_NAME"
mkdir -p "$JOB_DIR"

# Create Elasticsearch API key for FSCrawler (authenticate as elastic user)
ELASTIC_API_KEY=""
API_KEY_RESPONSE=$(curl -s -u "elastic:$ES_PASSWORD" -X POST -H "Content-Type: application/json" \
  -d "{\"name\":\"fscrawler-$JOB_NAME\",\"expiration\":\"7d\"}" \
  "$ES_URL/_security/api_key" 2>/dev/null || true)
if command -v jq >/dev/null 2>&1; then
  ELASTIC_API_KEY=$(echo "$API_KEY_RESPONSE" | jq -r '.encoded // empty')
elif python3 -c 'import json' 2>/dev/null; then
  ELASTIC_API_KEY=$(python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('encoded',''))" <<< "$API_KEY_RESPONSE" 2>/dev/null)
fi
if [ -z "$ELASTIC_API_KEY" ]; then
  echo "WARNING: Could not create Elasticsearch API key (check ES_LOCAL_PASSWORD?). Using no auth in job config."
  ELASTIC_AUTH=""
else
  echo "Elasticsearch API key created for FSCrawler."
  ELASTIC_AUTH="  api_key: \"$ELASTIC_API_KEY\""
fi

cat > "$JOB_DIR/_settings.yaml" << EOF
name: "$JOB_NAME"

fs:
  url: "$DOCS_ABS"
  update_rate: "1m"
  index_content: true
  index_folders: true

elasticsearch:
  urls: ["$ES_URL"]
  index: "${JOB_NAME}_docs"
  index_folder: "${JOB_NAME}_folder"
$ELASTIC_AUTH

rest:
  url: "$REST_URL"
EOF
echo "Job config: $JOB_DIR/_settings.yaml"
echo "Config dir: $CONFIG_DIR"

# 4. Launch FSCrawler (optional)
echo "--- 4. Launch FSCrawler (REST, loop 0) ---"
echo "To start FSCrawler run:"
echo "  $FSCRAWLER_HOME/bin/fscrawler $JOB_NAME --rest --loop 0 --config_dir $CONFIG_DIR"
echo ""
if [ "${RUN_FSCRAWLER:-0}" = "1" ]; then
  echo "Starting FSCrawler (RUN_FSCRAWLER=1)..."
  exec "$FSCRAWLER_HOME/bin/fscrawler" "$JOB_NAME" --rest --loop 0 --config_dir "$CONFIG_DIR"
else
  echo "Setup done. Set RUN_FSCRAWLER=1 to start automatically, or run the command above."
  echo "Test steps: IGNORE_ME/manual-test-rest-checkpoint.md"
fi
