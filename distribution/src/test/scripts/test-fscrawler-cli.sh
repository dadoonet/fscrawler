#!/bin/sh
# test-fscrawler-cli.sh — Manual smoke test for FSCrawler + EDOT Collector (OpenTelemetry)
#
# What this script does:
#   1. (optional) Build the distribution ZIP with Maven
#   2. Start the docker-compose EDOT stack (ES + Kibana + EDOT Collector)
#   3. Unzip the FSCrawler distribution into /tmp/fscrawler-edot-test
#   4. Create a job config (test-edot/_settings.yaml) pointing to test-documents/
#   5. Configure OTel env vars and launch FSCrawler
#
# Prerequisites:
#   - Java 17+, Maven, Docker, docker-compose (or docker compose v2), curl, unzip
#
# Usage:
#   ./test-fscrawler-cli.sh                        # full run: build + docker + crawl
#   ./test-fscrawler-cli.sh --skip-build           # reuse existing distribution ZIP
#   ./test-fscrawler-cli.sh --skip-docker          # assume docker stack is already running
#   ./test-fscrawler-cli.sh --no-otel              # run FSCrawler without OTel agent
#   ./test-fscrawler-cli.sh --log-level=trace      # set log level (default: info)
#   ./test-fscrawler-cli.sh --help

set -e

# ---------------------------------------------------------------------------
# Resolve paths
# ---------------------------------------------------------------------------
SCRIPT_DIR=$(cd "$(dirname "$0")"; pwd)
# Relative path from the *generated* script location to the project root.
# Injected by Maven resource filtering (see distribution/pom.xml).
PROJECT_ROOT=$(cd "$SCRIPT_DIR/${test.scripts.root.relative.path}"; pwd)

# Version is set by Maven resource filtering at build time (see distribution/pom.xml).
# To regenerate this file after a version bump: mvn generate-test-resources -pl distribution
FSCRAWLER_VERSION="${project.version}"

DIST_ZIP="$PROJECT_ROOT/distribution/target/fscrawler-distribution-${FSCRAWLER_VERSION}.zip"
INSTALL_DIR="/tmp/fscrawler-edot-test/fscrawler-distribution-${FSCRAWLER_VERSION}"
CONFIG_DIR="/tmp/fscrawler-edot-test/config"
JOB_NAME="test-edot"
DOCUMENTS_DIR="$PROJECT_ROOT/test-documents/src/main/resources/documents"
DOCKER_COMPOSE_DIR="$PROJECT_ROOT/contrib/docker-compose-example-edot"
ES_URL="http://localhost:9200"
COLLECTOR_URL="http://localhost:4318"

# ---------------------------------------------------------------------------
# Parse arguments
# ---------------------------------------------------------------------------
SKIP_BUILD=false
SKIP_DOCKER=false
NO_OTEL=false
LOG_LEVEL=info
for arg in "$@"; do
    case "$arg" in
        --skip-build)      SKIP_BUILD=true ;;
        --skip-docker)     SKIP_DOCKER=true ;;
        --no-otel)         NO_OTEL=true ;;
        --log-level=*)     LOG_LEVEL="${arg#--log-level=}" ;;
        --help|-h)
            sed -n '2,21p' "$0" | sed 's/^# \{0,1\}//'
            exit 0
            ;;
        *)
            echo "Unknown option: $arg  (use --help for usage)"
            exit 1
            ;;
    esac
done

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
info()    { printf '\033[0;34m[INFO]\033[0m  %s\n' "$*"; }
success() { printf '\033[0;32m[OK]\033[0m    %s\n' "$*"; }
warn()    { printf '\033[0;33m[WARN]\033[0m  %s\n' "$*"; }
die()     { printf '\033[0;31m[ERROR]\033[0m %s\n' "$*" >&2; exit 1; }

wait_for_url() {
    local url="$1" label="$2" retries=30
    info "Waiting for $label ($url) ..."
    while [ $retries -gt 0 ]; do
        if curl -sf "$url" > /dev/null 2>&1; then
            success "$label is ready"
            return 0
        fi
        retries=$((retries - 1))
        printf '.'
        sleep 5
    done
    printf '\n'
    die "$label did not become ready in time"
}

# Wait for a docker-compose service to reach the "healthy" state.
# Used for services whose health-check runs inside the container (e.g. EDOT Collector
# binds its health endpoint to localhost:13133, unreachable from the host).
wait_docker_healthy() {
    local service="$1" label="$2" retries=30
    info "Waiting for $label (docker health) ..."
    while [ $retries -gt 0 ]; do
        status=$($DOCKER_COMPOSE ps -q "$service" 2>/dev/null \
                 | xargs -I{} docker inspect --format='{{.State.Health.Status}}' {} 2>/dev/null)
        if [ "$status" = "healthy" ]; then
            success "$label is ready"
            return 0
        fi
        retries=$((retries - 1))
        printf '.'
        sleep 5
    done
    printf '\n'
    die "$label did not become ready in time"
}

# ---------------------------------------------------------------------------
# Step 1 — Build distribution ZIP
# ---------------------------------------------------------------------------
if $SKIP_BUILD; then
    info "Skipping build (--skip-build)"
    [ -f "$DIST_ZIP" ] || die "Distribution ZIP not found: $DIST_ZIP\n  Run without --skip-build to build it first."
else
    info "Building FSCrawler distribution (skipping tests and Docker image)..."
    cd "$PROJECT_ROOT"
    mvn clean package -DskipTests -Ddocker.skip -q
    [ -f "$DIST_ZIP" ] || die "Build succeeded but ZIP not found at: $DIST_ZIP"
    success "Distribution built: $DIST_ZIP"
fi

# ---------------------------------------------------------------------------
# Step 2 — Start docker-compose EDOT stack
# ---------------------------------------------------------------------------
if $SKIP_DOCKER; then
    info "Skipping docker-compose start (--skip-docker)"
else
    # Detect docker compose command (v2 plugin vs standalone v1)
    if docker compose version > /dev/null 2>&1; then
        DOCKER_COMPOSE="docker compose"
    elif command -v docker-compose > /dev/null 2>&1; then
        DOCKER_COMPOSE="docker-compose"
    else
        die "Neither 'docker compose' nor 'docker-compose' found. Please install Docker."
    fi

    info "Starting EDOT stack via docker-compose (ES + Kibana + EDOT Collector)..."
    # We run the stack without the fscrawler service (we run it locally instead)
    cd "$DOCKER_COMPOSE_DIR"
    $DOCKER_COMPOSE up -d elasticsearch kibana edot-collector
    success "docker-compose services started"

    # Wait for Elasticsearch and EDOT Collector to be ready
    wait_for_url "$ES_URL/_cluster/health?wait_for_status=yellow&timeout=5s" "Elasticsearch"
    wait_docker_healthy "edot-collector" "EDOT Collector"
    info "Kibana will be available at http://localhost:5601 (may take a minute to finish starting)"
fi

# ---------------------------------------------------------------------------
# Step 3 — Unzip distribution
# ---------------------------------------------------------------------------
info "Unzipping distribution into /tmp/fscrawler-edot-test ..."
mkdir -p /tmp/fscrawler-edot-test
# Remove previous install to avoid stale files
rm -rf "$INSTALL_DIR"
unzip -q "$DIST_ZIP" -d /tmp/fscrawler-edot-test
chmod +x "$INSTALL_DIR/bin/fscrawler"
success "Distribution ready at $INSTALL_DIR"

# Verify that the OTel agent was bundled
OTEL_AGENT=$(ls "$INSTALL_DIR"/external/elastic-otel-javaagent-*.jar 2>/dev/null | head -1)
if [ -n "$OTEL_AGENT" ]; then
    success "Elastic OTel agent found: $(basename "$OTEL_AGENT")"
else
    warn "Elastic OTel agent JAR not found in external/ — OTel tracing will be disabled"
    NO_OTEL=true
fi

# ---------------------------------------------------------------------------
# Step 4 — Create job config
# ---------------------------------------------------------------------------
JOB_CONFIG_DIR="$CONFIG_DIR/$JOB_NAME"
mkdir -p "$JOB_CONFIG_DIR"

info "Creating job config at $JOB_CONFIG_DIR/_settings.yaml ..."
cat > "$JOB_CONFIG_DIR/_settings.yaml" << YAML
# FSCrawler job — test-edot
# Indexes test documents from the FSCrawler source tree into Elasticsearch
name: "$JOB_NAME"
fs:
  url: "$DOCUMENTS_DIR"
  update_rate: "1m"
  excludes:
    - "*.DS_Store"
elasticsearch:
  urls:
  - "$ES_URL"
  semantic_search: false
YAML
success "Job config created"

# ---------------------------------------------------------------------------
# Step 5 — Configure OTel and launch FSCrawler
# ---------------------------------------------------------------------------
if $NO_OTEL; then
    warn "OTel tracing disabled (--no-otel or agent not found)"
    unset OTEL_EXPORTER_OTLP_ENDPOINT
    unset OTEL_SERVICE_NAME
    export OTEL_SDK_DISABLED=true
else
    info "Configuring OpenTelemetry exporter → $COLLECTOR_URL"
    export OTEL_EXPORTER_OTLP_ENDPOINT="$COLLECTOR_URL"
    export OTEL_SERVICE_NAME="fscrawler"
    export OTEL_RESOURCE_ATTRIBUTES="deployment.environment=local,service.version=$FSCRAWLER_VERSION"
    # Reduce export timeout so the agent doesn't block on startup if the collector is slow
    export OTEL_EXPORTER_OTLP_TIMEOUT=5000
    # Flush metrics every 5s so JVM metrics are exported before FSCrawler exits (--loop 1)
    # (default is 60s — too long for a short-lived process)
    export OTEL_METRIC_EXPORT_INTERVAL=5000
    # Enable Java 17+ JVM runtime metrics (jvm.cpu.recent_utilization, etc.)
    export OTEL_INSTRUMENTATION_RUNTIME_TELEMETRY_JAVA17_ENABLED=true
    # Enable inferred spans via async-profiler (Elastic OTel agent specific)
    export ELASTIC_OTEL_INFERRED_SPANS_ENABLED=true
fi

info ""
info "========================================================"
info " Launching FSCrawler — job: $JOB_NAME"
info "   Documents  : $DOCUMENTS_DIR"
info "   Config dir : $CONFIG_DIR"
info "   ES index   : http://localhost:9200/test-edot"
info "   Log level  : $LOG_LEVEL"
if ! $NO_OTEL; then
    info "   OTel traces: http://localhost:5601 → Observability → APM"
fi
info "========================================================"
info " Press Ctrl+C to stop FSCrawler"
info ""

export FS_JAVA_OPTS="-DLOG_LEVEL=$LOG_LEVEL"
exec "$INSTALL_DIR/bin/fscrawler" --config_dir "$CONFIG_DIR" "$JOB_NAME" --restart
