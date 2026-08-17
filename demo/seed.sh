#!/usr/bin/env bash
#
# CaseHub Demo Data Seeder
#
# Seeds a running CaseHub instance with sample cases, work items, and queues
# via the REST API. Case definitions are loaded from the classpath at startup
# (place YAML files in src/main/resources/casehub/).
#
# Portable across all casehub apps — devtown, clinical, scaffold, or any
# deployment with the engine + work REST surface.
#
# Usage:
#   ./demo/seed.sh                    # defaults to http://localhost:8080
#   ./demo/seed.sh http://localhost:8081
#   ./demo/seed.sh https://staging.casehub.io --token <jwt>
#
# Requires: curl, jq

set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
TOKEN="${TOKEN:-}"
AUTH_HEADER=""
if [ -n "$TOKEN" ]; then
  AUTH_HEADER="Authorization: Bearer $TOKEN"
fi

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

log()  { echo -e "${GREEN}[seed]${NC} $*"; }
warn() { echo -e "${YELLOW}[seed]${NC} $*"; }
fail() { echo -e "${RED}[seed]${NC} $*" >&2; exit 1; }

api() {
  local method="$1" path="$2"
  shift 2
  local url="$BASE_URL$path"
  local -a headers=(-H "Content-Type: application/json")
  [ -n "$AUTH_HEADER" ] && headers+=(-H "$AUTH_HEADER")
  curl -s -X "$method" "${headers[@]}" "$url" "$@"
}

api_status() {
  local method="$1" path="$2"
  shift 2
  local url="$BASE_URL$path"
  local -a headers=(-H "Content-Type: application/json")
  [ -n "$AUTH_HEADER" ] && headers+=(-H "$AUTH_HEADER")
  curl -s -o /dev/null -w '%{http_code}' -X "$method" "${headers[@]}" "$url" "$@"
}

# ── Health check ─────────────────────────────────────────────────
log "Checking $BASE_URL..."
STATUS=$(api_status GET /q/health)
if [ "$STATUS" != "200" ]; then
  fail "Server not reachable at $BASE_URL (got HTTP $STATUS)"
fi
log "Server is healthy"

# ── Show available modules ───────────────────────────────────────
MODULES=$(api GET /api/modules 2>/dev/null)
if [ -n "$MODULES" ]; then
  log "Modules: $(echo "$MODULES" | jq -r '.modules | join(", ")' 2>/dev/null || echo '?')"
fi

# ── List available definitions ───────────────────────────────────
log "Checking case definitions (loaded from classpath)..."
DEFS_RESP=$(api GET /api/v1/case-definitions 2>/dev/null)
DEFS_COUNT=$(echo "$DEFS_RESP" | jq '.totalElements' 2>/dev/null || echo 0)
if [ "$DEFS_COUNT" = "0" ]; then
  warn "No definitions found. Place YAML files in src/main/resources/casehub/ and restart."
else
  log "  $DEFS_COUNT definition(s) available:"
  echo "$DEFS_RESP" | jq -r '.items[] | "    - \(.namespace)/\(.name) v\(.version)"' 2>/dev/null || true
fi

# ── Create sample cases ──────────────────────────────────────────
log "Creating sample cases..."

create_case() {
  local namespace="$1" name="$2" version="$3" context="$4" label="${5:-}"
  local body
  body=$(jq -n --arg ns "$namespace" --arg nm "$name" --arg v "$version" --argjson ctx "$context" \
    '{definition: {namespace: $ns, name: $nm, version: $v}, context: $ctx}')
  RESULT=$(api POST /api/v1/cases -d "$body")
  if echo "$RESULT" | jq -e '.id // .caseId' > /dev/null 2>&1; then
    CASE_ID=$(echo "$RESULT" | jq -r '.id // .caseId')
    log "  ✓ Case #$CASE_ID: $namespace/$name ${label:+($label)}"
  else
    warn "  ⚠ $namespace/$name: $(echo "$RESULT" | jq -r '.detail // .message // "unknown error"' 2>/dev/null)"
  fi
}

create_case demo "Customer Onboarding" "1.0.0" \
  '{"customerId":"CUST-101","customerName":"Alice Johnson","documentType":"passport"}' "Alice"
create_case demo "Customer Onboarding" "1.0.0" \
  '{"customerId":"CUST-102","customerName":"Bob Smith","documentType":"drivers_license"}' "Bob"
create_case demo "Customer Onboarding" "1.0.0" \
  '{"customerId":"CUST-103","customerName":"Carol Williams","documentType":"passport"}' "Carol"

create_case demo "Incident Response" "1.0.0" \
  '{"incidentId":"INC-2001","description":"Database connection pool exhausted","source":"monitoring"}' "DB pool"
create_case demo "Incident Response" "1.0.0" \
  '{"incidentId":"INC-2002","description":"Payment gateway timeout","source":"customer_report"}' "Payment"
create_case demo "Incident Response" "1.0.0" \
  '{"incidentId":"INC-2003","description":"SSL certificate expiry warning","source":"monitoring"}' "SSL cert"

# ── Create sample work items ─────────────────────────────────────
log "Creating sample work items..."

create_workitem() {
  local title="$1" type="${2:-TASK}" priority="${3:-MEDIUM}"
  local body
  body=$(jq -n --arg t "$title" --arg ty "$type" --arg p "$priority" \
    '{title: $t, type: $ty, priority: $p}')
  RESULT=$(api POST /workitems -d "$body")
  if echo "$RESULT" | jq -e '.id // .caseId' > /dev/null 2>&1; then
    WI_ID=$(echo "$RESULT" | jq -r '.id // .caseId')
    log "  ✓ $WI_ID: $title [$priority]"
  else
    warn "  ⚠ $title: $(echo "$RESULT" | jq -r '.detail // .message // .error // "unknown"' 2>/dev/null)"
  fi
}

create_workitem "Review KYC documents for CUST-101" TASK HIGH
create_workitem "Approve account for CUST-102" APPROVAL MEDIUM
create_workitem "Investigate INC-2001 root cause" TASK URGENT
create_workitem "Deploy hotfix for payment gateway" TASK HIGH
create_workitem "Renew SSL certificates" TASK LOW
create_workitem "Update runbook for DB pool exhaustion" TASK LOW
create_workitem "Schedule security audit" TASK MEDIUM
create_workitem "Review incident post-mortem" TASK MEDIUM

# ── Create label rules (for queue membership) ───────────────────
log "Creating label rules..."

create_label_rule() {
  local name="$1" label="$2" condition="$3"
  local body
  body=$(jq -n --arg n "$name" --arg l "$label" --arg c "$condition" \
    '{name: $n, scope: "ORG", conditionLanguage: "jexl", conditionExpression: $c, actions: [{type: "Add", label: $l}]}')
  RESULT=$(api POST /label-rules -d "$body")
  if echo "$RESULT" | jq -e '.id' > /dev/null 2>&1; then
    log "  ✓ Rule: $name → $label"
  else
    warn "  ⚠ $name: $(echo "$RESULT" | jq -r '.detail // .message // .error // "unknown"' 2>/dev/null)"
  fi
}

create_label_rule "High priority" "priority/high" "priority == 'HIGH' || priority == 'URGENT'"
create_label_rule "Medium priority" "priority/medium" "priority == 'MEDIUM'"
create_label_rule "Low priority" "priority/low" "priority == 'LOW'"

# ── Create queue views ───────────────────────────────────────────
log "Creating queue views..."

create_queue() {
  local name="$1" pattern="$2"
  local body
  body=$(jq -n --arg n "$name" --arg p "$pattern" \
    '{name: $n, labelPattern: $p}')
  RESULT=$(api POST /queues -d "$body")
  if echo "$RESULT" | jq -e '.id' > /dev/null 2>&1; then
    QUEUE_ID=$(echo "$RESULT" | jq -r '.id')
    log "  ✓ Queue: $name ($QUEUE_ID)"
  else
    warn "  ⚠ $name: $(echo "$RESULT" | jq -r '.detail // .message // .error // "unknown"' 2>/dev/null)"
  fi
}

create_queue "Urgent & High Priority" "priority/high"
create_queue "Medium Priority" "priority/medium"
create_queue "Low Priority" "priority/low"

# ── Summary ──────────────────────────────────────────────────────
log ""
log "Seed complete. Open $BASE_URL to view the console."
CASES_COUNT=$(api GET /api/v1/cases 2>/dev/null | jq '.totalElements' 2>/dev/null || echo '?')
WI_COUNT=$(api GET /workitems 2>/dev/null | jq 'length' 2>/dev/null || echo '?')
Q_COUNT=$(api GET /queues 2>/dev/null | jq 'length' 2>/dev/null || echo '?')
log "  Definitions: $DEFS_COUNT | Cases: $CASES_COUNT | Work items: $WI_COUNT | Queues: $Q_COUNT"
