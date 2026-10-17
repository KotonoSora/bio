#!/bin/sh
# Smoke suite: health, edge serving (SPA + media allowlist + SSR proxy), beacon
# 202 through the edge, owner read, login redirect (G3).
# Single source used by CI, Podman parity, and local runs. Exits nonzero on failure.
# Usage: sh deploy/smoke.sh
# Env overrides: EDGE_URL or EDGE_HTTP_PORT (edge), GATEWAY_URL, PROFILE_URL,
# ANALYTICS_URL, PUBLICSITE_URL.
set -e
EDGE="${EDGE_URL:-http://localhost:${EDGE_HTTP_PORT:-80}}"
GATEWAY="${GATEWAY_URL:-http://localhost:8080}"
PROFILE="${PROFILE_URL:-http://localhost:8082}"
ANALYTICS="${ANALYTICS_URL:-http://localhost:8085}"
PUBLICSITE="${PUBLICSITE_URL:-http://localhost:8084}"

echo "== health (10 services + web + edge) =="
for p in 8080 8081 8082 8083 8084 8085 8086 8087 8088 8089 8090; do
  curl -sf "http://localhost:$p/health/liveness" >/dev/null || { echo "FAIL unhealthy: $p"; exit 1; }
done
curl -sf "$EDGE/health/liveness" >/dev/null || { echo "FAIL unhealthy: edge"; exit 1; }
echo "ok health"

echo "== edge serves the SPA bundle =="
curl -sf "$EDGE/app/" | grep -q 'id="root"' || { echo "FAIL edge SPA"; exit 1; }
echo "ok edge SPA"

echo "== edge proxies public SSR to the gateway =="
curl -sf "$EDGE/smokeuser" | grep -q smokeuser || { echo "FAIL edge SSR"; exit 1; }
echo "ok edge SSR"

echo "== edge media stays allowlist-only (no listing, gated paths 404) =="
for u in "$EDGE/media/" "$EDGE/media/profile/evidence/" "$EDGE/internal-media/profile/evidence/x.png"; do
  C="$(curl -s -o /dev/null -w '%{http_code}' "$u")"
  [ "$C" = "404" ] || { echo "FAIL $u -> $C"; exit 1; }
done
echo "ok edge media"

echo "== public SSR page =="
curl -sf "$PUBLICSITE/smokeuser" | grep -q smokeuser || { echo "FAIL public page"; exit 1; }
echo "ok public page"

echo "== beacon uniform 202 (edge -> gateway -> analytics) =="
CODE="$(curl -s -o /dev/null -w '%{http_code}' -X POST "$EDGE/api/t/smoke" \
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
