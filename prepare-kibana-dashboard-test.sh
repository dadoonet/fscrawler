#!/usr/bin/env bash
#
# Setup script for manual Kibana dashboard testing (issue #2477).
# Run from the project root. Ensures: Elasticsearch + Kibana check, Maven build,
# unzip distribution, job config with kibana.push_dashboard, and optionally starts FSCrawler.
#
# Prerequisites: Elasticsearch + Kibana (start-local), Java 17+.
#
# Example:
#   ./prepare-kibana-dashboard-test.sh
#   RUN_FSCRAWLER=1 ./prepare-kibana-dashboard-test.sh
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR"
DIST_TARGET="$PROJECT_ROOT/distribution/target"
DOCS_SOURCE="$PROJECT_ROOT/test-documents/target/classes/documents"
JOB_NAME="${FSCRAWLER_JOB:-kibana_dashboard_test}"
ES_URL="${ELASTICSEARCH_URL:-http://127.0.0.1:9200}"
KIBANA_URL="${KIBANA_URL:-http://127.0.0.1:5601}"
ES_PASSWORD="${ES_LOCAL_PASSWORD:-changeme}"
FSCRAWLER_HOME="${FSCRAWLER_HOME:-}"

echo "=== FSCrawler Kibana dashboard test – setup ==="
echo "  Project root: $PROJECT_ROOT"
echo "  Job name:     $JOB_NAME"
echo "  Docs path:    $DOCS_SOURCE"
echo "  Elasticsearch: $ES_URL"
echo "  Kibana:       $KIBANA_URL"
echo ""

# 0. Check Elasticsearch
echo "--- 0. Check Elasticsearch ---"
ES_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$ES_URL" 2>/dev/null || echo "000")
if [ "$ES_STATUS" != "200" ] && [ "$ES_STATUS" != "401" ]; then
	echo "Elasticsearch does not appear to be running at $ES_URL (got HTTP $ES_STATUS)."
	echo ""
	echo "Start Elasticsearch + Kibana with:"
	echo "  curl -fsSL https://elastic.co/start-local | ES_LOCAL_PASSWORD=\"changeme\" sh"
	echo ""
	echo "Then re-run this script."
	exit 1
fi
echo "Elasticsearch is running at $ES_URL"

# 1. Check Kibana
echo "--- 1. Check Kibana ---"
KIBANA_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$KIBANA_URL/api/status" 2>/dev/null || echo "000")
if [ "$KIBANA_STATUS" != "200" ] && [ "$KIBANA_STATUS" != "401" ]; then
	echo "Kibana does not appear to be running at $KIBANA_URL (got HTTP $KIBANA_STATUS)."
	echo ""
	echo "start-local includes Kibana on http://localhost:5601. Wait until it is ready, then re-run."
	exit 1
fi
KIBANA_VERSION=$(curl -s "$KIBANA_URL/api/status" 2>/dev/null | python3 -c "import json,sys; print(json.load(sys.stdin).get('version',{}).get('number',''))" 2>/dev/null || true)
if [ -n "$KIBANA_VERSION" ]; then
	echo "Kibana is running at $KIBANA_URL (version $KIBANA_VERSION)"
	MAJOR=$(echo "$KIBANA_VERSION" | cut -d. -f1)
	MINOR=$(echo "$KIBANA_VERSION" | cut -d. -f2)
	if [ "$MAJOR" -lt 9 ] || { [ "$MAJOR" -eq 9 ] && [ "$MINOR" -lt 5 ]; }; then
		echo "WARNING: Dashboards API requires Kibana 9.5+. FSCrawler will soft-disable dashboard provisioning."
	fi
else
	echo "Kibana is running at $KIBANA_URL"
fi

# 2. Maven build
echo "--- 2. Maven build ---"
if ! [ -d "$DOCS_SOURCE" ]; then
	echo "Building test-documents and distribution..."
	mvn clean compile -pl test-documents -q
	mvn package -DskipTests -pl distribution -am -q
else
	echo "Building distribution (test-documents already present)..."
	mvn package -DskipTests -pl distribution -am -q
fi

# 3. Unzip distribution
echo "--- 3. Unzip distribution ---"
ZIP=$(ls "$DIST_TARGET"/fscrawler*.zip 2>/dev/null | head -1)
if [ -z "$ZIP" ]; then
	echo "No zip found in $DIST_TARGET. Run: mvn package -DskipTests -pl distribution -am"
	exit 1
fi
echo "Using: $ZIP"
cd "$DIST_TARGET"
unzip -o -q "$(basename "$ZIP")"
EXTRACTED=$(find . -maxdepth 1 -type d -name 'fscrawler*' ! -name '.' | head -1)
if [ -z "$EXTRACTED" ]; then
	EXTRACTED="."
fi
FSCRAWLER_HOME="$DIST_TARGET/${EXTRACTED#./}"
cd "$PROJECT_ROOT"
echo "FSCrawler home: $FSCRAWLER_HOME"

# 4. Create job config
echo "--- 4. Create job: $JOB_NAME ---"
if ! [ -d "$DOCS_SOURCE" ]; then
	echo "ERROR: Documents dir not found: $DOCS_SOURCE"
	echo "Run: mvn compile -pl test-documents"
	exit 1
fi
DOCS_ABS=$(cd "$DOCS_SOURCE" && pwd)
CONFIG_DIR="$FSCRAWLER_HOME/config"
JOB_DIR="$CONFIG_DIR/$JOB_NAME"
mkdir -p "$JOB_DIR"

ELASTIC_API_KEY=""
API_KEY_RESPONSE=$(curl -s -u "elastic:$ES_PASSWORD" -X POST -H "Content-Type: application/json" \
	-d "{\"name\":\"fscrawler-$JOB_NAME\",\"expiration\":\"7d\"}" \
	"$ES_URL/_security/api_key" 2>/dev/null || true)
if command -v jq >/dev/null 2>&1; then
	ELASTIC_API_KEY=$(echo "$API_KEY_RESPONSE" | jq -r '.encoded // empty')
elif python3 -c 'import json' 2>/dev/null; then
	ELASTIC_API_KEY=$(python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('encoded',''))" <<<"$API_KEY_RESPONSE" 2>/dev/null)
fi
if [ -z "$ELASTIC_API_KEY" ]; then
	echo "WARNING: Could not create Elasticsearch API key (check ES_LOCAL_PASSWORD?). Using username/password."
	ELASTIC_AUTH="  username: \"elastic\"
  password: \"$ES_PASSWORD\""
else
	echo "Elasticsearch API key created for FSCrawler."
	ELASTIC_AUTH="  api_key: \"$ELASTIC_API_KEY\""
fi

cat >"$JOB_DIR/_settings.yaml" <<EOF
name: "$JOB_NAME"

fs:
  url: "$DOCS_ABS"
  update_rate: "15m"
  index_content: true
  index_folders: true

elasticsearch:
  urls: ["$ES_URL"]
$ELASTIC_AUTH

kibana:
  url: "$KIBANA_URL"
EOF
echo "Job config: $JOB_DIR/_settings.yaml"
echo "Config dir: $CONFIG_DIR"

DASHBOARD_ID="fscrawler-$JOB_NAME"
echo ""
echo "After the crawl, open:"
echo "  $KIBANA_URL/app/dashboards"
echo "  Dashboard id: $DASHBOARD_ID"
echo "  Title:        FSCrawler - $JOB_NAME"
echo ""

# 5. Launch FSCrawler (optional)
echo "--- 5. Launch FSCrawler (loop 1) ---"
echo "To start FSCrawler run:"
echo "  $FSCRAWLER_HOME/bin/fscrawler $JOB_NAME --loop 1 --config_dir $CONFIG_DIR"
echo ""
if [ "${RUN_FSCRAWLER:-0}" = "1" ]; then
	echo "Starting FSCrawler (RUN_FSCRAWLER=1)..."
	exec "$FSCRAWLER_HOME/bin/fscrawler" "$JOB_NAME" --loop 1 --config_dir "$CONFIG_DIR"
else
	echo "Setup done. Set RUN_FSCRAWLER=1 to start automatically, or run the command above."
fi
