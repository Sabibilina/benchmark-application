#!/usr/bin/env bash
# verify-integration.sh — System verification deliverable (ARCHITECTURE.md §6)
#
# Validates that all 8 services start, connect, authenticate cross-service,
# and propagate events through the Kafka pipeline.
#
# Prerequisites: Docker, Docker Compose v2, curl, jq
# Usage: bash scripts/verify-integration.sh [--keep-up]
#   --keep-up  Leave the compose stack running after verification (default: tear down)
#
# Exit codes: 0 = all steps PASS, 1 = at least one step FAIL

set -euo pipefail

COMPOSE_FILE="$(dirname "$0")/../docker-compose.yml"
COMPOSE="docker compose -f $COMPOSE_FILE"
BASE_URL="http://localhost:80"
KEEP_UP=false
PASS=0
FAIL=0

for arg in "$@"; do
  [[ "$arg" == "--keep-up" ]] && KEEP_UP=true
done

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

pass() { echo -e "${GREEN}[PASS]${NC} $1"; PASS=$((PASS+1)); }
fail() { echo -e "${RED}[FAIL]${NC} $1"; FAIL=$((FAIL+1)); }
info() { echo -e "${YELLOW}[INFO]${NC} $1"; }

# ── Prerequisite checks ───────────────────────────────────────────────────────

info "Checking prerequisites..."
for cmd in docker curl jq; do
  if ! command -v "$cmd" &>/dev/null; then
    echo "Required command '$cmd' not found. Install it and re-run."
    exit 1
  fi
done

# Warn if host RAM is below 8 GB (Profile A/B minimum)
TOTAL_RAM_GB=$(awk '/MemTotal/ {printf "%.0f", $2/1024/1024}' /proc/meminfo 2>/dev/null || sysctl -n hw.memsize 2>/dev/null | awk '{printf "%.0f", $1/1024/1024/1024}' || echo "0")
if [[ "$TOTAL_RAM_GB" -lt 8 ]]; then
  info "WARNING: Host has ${TOTAL_RAM_GB} GB RAM. 8 GB minimum recommended for Profile A/B."
fi

# ── Step 1: Start compose stack ───────────────────────────────────────────────

info "Step 1: Starting compose stack (default profile — no load generator)..."
$COMPOSE up -d

# ── Step 2: Wait for all 8 services to be healthy ────────────────────────────

SERVICES=(auth-service catalog-service streaming-service playlist-service
          search-service analytics-service recommendation-service notification-service)
HEALTH_TIMEOUT=120
POLL_INTERVAL=5

info "Step 2: Waiting up to ${HEALTH_TIMEOUT}s for all 8 services to be healthy..."
deadline=$((SECONDS + HEALTH_TIMEOUT))
all_healthy=false

while [[ $SECONDS -lt $deadline ]]; do
  all_healthy=true
  for svc in "${SERVICES[@]}"; do
    status=$($COMPOSE ps --status running --quiet "$svc" 2>/dev/null | wc -l || echo 0)
    if [[ "$status" -eq 0 ]]; then
      all_healthy=false
      break
    fi
    # Check actuator health via nginx-lb (proxy path based on service name prefix)
    svc_prefix="${svc%%-service}"
    health_path="/${svc_prefix}/actuator/health"
    # Fallback: check direct container exec for services whose path differs
    container_health=$($COMPOSE exec -T "$svc" \
      wget -qO- http://localhost:8080/actuator/health 2>/dev/null | jq -r '.status' 2>/dev/null || echo "")
    if [[ "$container_health" != "UP" ]]; then
      all_healthy=false
      break
    fi
  done
  if $all_healthy; then
    break
  fi
  sleep $POLL_INTERVAL
done

if $all_healthy; then
  pass "All 8 services are healthy"
else
  fail "Not all services became healthy within ${HEALTH_TIMEOUT}s"
  info "Run: $COMPOSE ps  to inspect container states"
fi

# ── Step 3: JWT + cross-service auth flow ─────────────────────────────────────

info "Step 3: Verifying JWT issuance and cross-service validation..."
TEST_USER="verify-$(date +%s)"
TEST_EMAIL="${TEST_USER}@verify.test"
TEST_PASS="Verify123!"

REG_RESP=$(curl -sf -X POST "${BASE_URL}/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"${TEST_USER}\",\"email\":\"${TEST_EMAIL}\",\"password\":\"${TEST_PASS}\"}" \
  2>/dev/null || echo "{}")

TOKEN=$(echo "$REG_RESP" | jq -r '.token // empty' 2>/dev/null || echo "")
if [[ -z "$TOKEN" ]]; then
  LOGIN_RESP=$(curl -sf -X POST "${BASE_URL}/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"${TEST_USER}\",\"password\":\"${TEST_PASS}\"}" \
    2>/dev/null || echo "{}")
  TOKEN=$(echo "$LOGIN_RESP" | jq -r '.token // empty' 2>/dev/null || echo "")
fi

if [[ -n "$TOKEN" ]]; then
  pass "JWT issued by auth-service"
else
  fail "Could not obtain JWT from auth-service"
fi

# Cross-service auth: catalog-service must accept the JWT from auth-service
if [[ -n "$TOKEN" ]]; then
  CATALOG_STATUS=$(curl -so /dev/null -w "%{http_code}" \
    -H "Authorization: Bearer ${TOKEN}" \
    "${BASE_URL}/catalog/songs?page=0&size=5" 2>/dev/null || echo "0")
  if [[ "$CATALOG_STATUS" == "200" ]]; then
    pass "catalog-service accepts JWT issued by auth-service (cross-service JWT validation)"
  else
    fail "catalog-service returned HTTP ${CATALOG_STATUS} with valid JWT (expected 200)"
  fi

  # Protected endpoint must reject missing token
  UNAUTH_STATUS=$(curl -so /dev/null -w "%{http_code}" \
    "${BASE_URL}/catalog/songs?page=0&size=5" 2>/dev/null || echo "0")
  if [[ "$UNAUTH_STATUS" == "401" ]]; then
    pass "catalog-service returns 401 for unauthenticated request (M-25)"
  else
    fail "catalog-service returned HTTP ${UNAUTH_STATUS} without token (expected 401)"
  fi
fi

# ── Step 4: Kafka event pipeline (stream → analytics) ────────────────────────

info "Step 4: Verifying Kafka event pipeline (streaming-service → analytics-service)..."

if [[ -n "$TOKEN" ]]; then
  # Get first song id from catalog
  CATALOG_BODY=$(curl -sf -H "Authorization: Bearer ${TOKEN}" \
    "${BASE_URL}/catalog/songs?page=0&size=1" 2>/dev/null || echo "{}")
  SONG_ID=$(echo "$CATALOG_BODY" | jq -r '(.content[0].id // .songs[0].id // .[0].id // "1")' 2>/dev/null || echo "1")

  # Trigger stream to emit a play.started event
  STREAM_STATUS=$(curl -so /dev/null -w "%{http_code}" \
    -H "Authorization: Bearer ${TOKEN}" \
    "${BASE_URL}/stream/${SONG_ID}" 2>/dev/null || echo "0")

  if [[ "$STREAM_STATUS" == "200" ]]; then
    pass "streaming-service returned HLS manifest (GET /stream/${SONG_ID})"
  else
    fail "streaming-service returned HTTP ${STREAM_STATUS} (expected 200)"
  fi

  # Wait for Kafka event to propagate through analytics-service batch buffer (max 10 s)
  info "Waiting 10s for Kafka event to reach analytics-service..."
  sleep 10

  HISTORY_BODY=$(curl -sf -H "Authorization: Bearer ${TOKEN}" \
    "${BASE_URL}/analytics/me/history" 2>/dev/null || echo "[]")
  HISTORY_COUNT=$(echo "$HISTORY_BODY" | jq 'length' 2>/dev/null || echo "0")

  if [[ "$HISTORY_COUNT" -gt 0 ]]; then
    pass "analytics-service has ${HISTORY_COUNT} history entry/entries — Kafka pipeline is live"
  else
    # Analytics batch buffer may not have flushed yet (up to 5 s flush interval)
    info "History empty; waiting an additional 6s for batch flush..."
    sleep 6
    HISTORY_BODY2=$(curl -sf -H "Authorization: Bearer ${TOKEN}" \
      "${BASE_URL}/analytics/me/history" 2>/dev/null || echo "[]")
    HISTORY_COUNT2=$(echo "$HISTORY_BODY2" | jq 'length' 2>/dev/null || echo "0")
    if [[ "$HISTORY_COUNT2" -gt 0 ]]; then
      pass "analytics-service has ${HISTORY_COUNT2} history entry/entries after batch flush"
    else
      fail "analytics-service history is still empty after 16s — Kafka pipeline may not be flowing"
    fi
  fi
fi

# ── Step 5: k6 smoke run ──────────────────────────────────────────────────────

info "Step 5: Running k6 smoke scenario (5 VUs, 2 min)..."
if $COMPOSE --profile load-test run --rm \
    -e K6_SCENARIO=smoke \
    -e NGINX_LB_URL="http://nginx-lb:80" \
    load-generator; then
  pass "k6 smoke scenario completed with exit code 0"
else
  fail "k6 smoke scenario exited with non-zero code (check for HTTP errors above)"
fi

# ── Step 6: Prometheus scrape targets ─────────────────────────────────────────

info "Step 6: Checking Prometheus scrape target states..."
PROM_PORT="${PROMETHEUS_HOST_PORT:-9090}"
TARGETS=$(curl -sf "http://localhost:${PROM_PORT}/api/v1/targets" 2>/dev/null || echo "{}")
UP_COUNT=$(echo "$TARGETS" | jq '[.data.activeTargets[] | select(.health=="up")] | length' 2>/dev/null || echo "0")
DOWN_COUNT=$(echo "$TARGETS" | jq '[.data.activeTargets[] | select(.health!="up")] | length' 2>/dev/null || echo "0")

if [[ "$DOWN_COUNT" -eq 0 && "$UP_COUNT" -gt 0 ]]; then
  pass "All ${UP_COUNT} Prometheus scrape targets are UP"
else
  fail "${DOWN_COUNT} Prometheus scrape target(s) are DOWN (${UP_COUNT} UP). Check prometheus UI."
fi

# ── Step 7: Grafana dashboard availability ────────────────────────────────────

info "Step 7: Checking Grafana dashboard availability..."
GRAFANA_PORT="${GRAFANA_HOST_PORT:-3001}"
GRAFANA_HEALTH=$(curl -sf "http://localhost:${GRAFANA_PORT}/api/health" 2>/dev/null || echo "{}")
GRAFANA_STATUS=$(echo "$GRAFANA_HEALTH" | jq -r '.database // empty' 2>/dev/null || echo "")

if [[ "$GRAFANA_STATUS" == "ok" ]]; then
  pass "Grafana is healthy (database: ok)"
else
  fail "Grafana health check failed or returned unexpected status"
fi

# ── Summary ───────────────────────────────────────────────────────────────────

echo ""
echo "═══════════════════════════════════════════"
echo " Integration Verification Results"
echo "═══════════════════════════════════════════"
echo -e " ${GREEN}PASS: ${PASS}${NC}   ${RED}FAIL: ${FAIL}${NC}"
echo "═══════════════════════════════════════════"

if ! $KEEP_UP; then
  info "Tearing down compose stack (use --keep-up to leave it running)..."
  $COMPOSE down
fi

if [[ $FAIL -gt 0 ]]; then
  exit 1
fi
exit 0
