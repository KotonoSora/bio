#!/bin/sh
# Smoke suite: public page, beacon 202, owner read, login redirect (G3).
# Single source used by CI, Podman parity, and local runs. Exits nonzero on failure.
# Usage: sh deploy/smoke.sh [base-health-port-prefix] (default: localhost ports as composed)
set -e
GATEWAY="${GATEWAY_URL:-http://localhost:8080}"
PROFILE="${PROFILE_URL:-http://localhost:8082}"
ANALYTICS="${ANALYTICS_URL:-http://localhost:8085}"
PUBLICSITE="${PUBLICSITE_URL:-http://localhost:8084}"

echo "== health (10 services + web) =="
for p in 8080 8081 8082 8083 8084 8085 8086 8087 8088 8089 8090; do
  curl -sf "http://localhost:$p/health/liveness" >/dev/null || { echo "FAIL unhealthy: $p"; exit 1; }
done
echo "ok health"

echo "== public SSR page =="
curl -sf "$PUBLICSITE/smokeuser" | grep -q smokeuser || { echo "FAIL public page"; exit 1; }
echo "ok public page"

echo "== beacon uniform 202 =="
CODE="$(curl -s -o /dev/null -w '%{http_code}' -X POST "$ANALYTICS/api/t/smoke" \
  -H 'Content-Type: application/json' -d '{"visitorKey":"smoke-1"}')"
[ "$CODE" = "202" ] || { echo "FAIL beacon=$CODE"; exit 1; }
echo "ok beacon 202"

echo "== owner read =="
curl -sf "$PROFILE/api/v1/profiles/me" >/dev/null || { echo "FAIL owner read"; exit 1; }
echo "ok owner read"

echo "== login redirect (SSO entry, no password flow exists) =="
curl -sf "$GATEWAY/api/v1/auth/login/google" 2>/dev/null | grep -q google \
  || curl -s -o /dev/null -w '%{http_code}' "http://localhost:8081/api/v1/auth/login/google" | grep -q 200 \
  || { echo "FAIL login redirect"; exit 1; }
echo "ok login redirect"
echo "PASS smoke"
