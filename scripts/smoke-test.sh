#!/usr/bin/env bash
set -euo pipefail

API_KEY="${INDEXER_API_KEY:-change-me}"
BASE_URL="${BASE_URL:-http://localhost:8080}"
MGMT_URL="${MGMT_URL:-http://localhost:8081}"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
NC='\033[0m'

pass() { echo -e "${GREEN}✓ $1${NC}"; }
fail() { echo -e "${RED}✗ $1${NC}"; FAILURES=$((FAILURES + 1)); }
warn() { echo -e "${YELLOW}⚠ $1${NC}"; }

FAILURES=0

echo "============================================"
echo " StableBridge Indexer — Smoke Test"
echo "============================================"
echo ""

# --- 1. Health Check ---
echo "--- Health Check ---"
HEALTH=$(curl -sf "$MGMT_URL/actuator/health" 2>/dev/null || echo '{"status":"DOWN"}')
STATUS=$(echo "$HEALTH" | jq -r '.status' 2>/dev/null || echo "UNKNOWN")
if [ "$STATUS" = "UP" ]; then
    pass "Actuator health: $STATUS"
else
    fail "Actuator health: $STATUS"
fi

# --- 2. Chain Status ---
echo ""
echo "--- Chain Status ---"
CHAINS=$(curl -sf -H "X-API-Key: $API_KEY" "$BASE_URL/api/v1/status" 2>/dev/null || echo "[]")
CHAIN_COUNT=$(echo "$CHAINS" | jq 'length' 2>/dev/null || echo "0")
if [ "$CHAIN_COUNT" -gt 0 ]; then
    pass "$CHAIN_COUNT chain(s) reporting"
    echo "$CHAINS" | jq -r '.[] | "     \(.chainName): \(.status) (block \(.latestIndexedBlock // "n/a"))"' 2>/dev/null
else
    fail "No chains reporting (got $CHAIN_COUNT)"
fi

# --- 3. API Authentication ---
echo ""
echo "--- API Authentication ---"
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/v1/status" 2>/dev/null || echo "000")
if [ "$HTTP_CODE" = "401" ]; then
    pass "Unauthenticated request correctly rejected (401)"
else
    fail "Expected 401 without API key, got $HTTP_CODE"
fi

# --- 4. Bloom Filter ---
echo ""
echo "--- Bloom Filter ---"
BLOOM=$(curl -sf -H "X-API-Key: $API_KEY" "$BASE_URL/api/v1/status/bloom" 2>/dev/null || echo "{}")
if echo "$BLOOM" | jq -e '.' >/dev/null 2>&1; then
    pass "Bloom filter status endpoint responding"
else
    fail "Bloom filter status endpoint failed"
fi

# --- 5. Prometheus Metrics ---
echo ""
echo "--- Prometheus Metrics ---"
METRICS=$(curl -sf "$MGMT_URL/actuator/prometheus" 2>/dev/null || echo "")
METRIC_COUNT=$(echo "$METRICS" | grep -c "indexer_" 2>/dev/null || echo "0")
if [ "$METRIC_COUNT" -gt 0 ]; then
    pass "$METRIC_COUNT indexer metrics found"
else
    fail "No indexer metrics found at /actuator/prometheus"
fi

# --- 6. Redis Connectivity ---
echo ""
echo "--- Redis ---"
REDIS_PING=$(docker exec indexer-redis redis-cli PING 2>/dev/null || echo "FAIL")
if [ "$REDIS_PING" = "PONG" ]; then
    pass "Redis is reachable"
    PROGRESS=$(docker exec indexer-redis redis-cli HGETALL indexer:progress 2>/dev/null || echo "")
    if [ -n "$PROGRESS" ]; then
        pass "Block progress tracked in Redis"
    else
        warn "No block progress in Redis yet (indexer may still be starting)"
    fi
else
    fail "Redis unreachable"
fi

# --- 7. Kafka Connectivity ---
echo ""
echo "--- Kafka ---"
TOPICS=$(docker exec indexer-redpanda rpk topic list --brokers localhost:9092 2>/dev/null || echo "FAIL")
if echo "$TOPICS" | grep -q "transfer.events" 2>/dev/null; then
    pass "Transfer event topics exist"
else
    warn "No transfer.events topics found (may be created on first publish)"
fi

# --- Summary ---
echo ""
echo "============================================"
if [ "$FAILURES" -eq 0 ]; then
    echo -e "${GREEN}All checks passed${NC}"
else
    echo -e "${RED}$FAILURES check(s) failed${NC}"
fi
echo "============================================"

exit "$FAILURES"
