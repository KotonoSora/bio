#!/bin/sh
# Smoke suite: health, edge serving (SPA + MFE assets + media allowlist + SSR
# proxy), beacon 202 through the edge, owner read, login redirect (G3).
# Single source used by CI, Podman parity, and local runs. Exits nonzero on failure.
# Usage: sh deploy/smoke.sh
# Env overrides: EDGE_URL or EDGE_HTTP_PORT (edge), PROFILE_URL,
# ANALYTICS_URL, PUBLICSITE_URL.
set -e
EDGE="${EDGE_URL:-http://localhost:${EDGE_HTTP_PORT:-80}}"
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

echo "== edge serves the MFE manifest + account bundle =="
MFE_MANIFEST="$(curl -sf "$EDGE/app/mfe.manifest.json")" || { echo "FAIL edge mfe manifest"; exit 1; }
MFE_ENTRY="$(printf '%s' "$MFE_MANIFEST" | python3 -c 'import json,sys; print(json.load(sys.stdin)["account"]["entry"])')" || { echo "FAIL edge mfe manifest entry"; exit 1; }
curl -sf "$EDGE/app/$MFE_ENTRY" | grep -q 'rb-account-app' || { echo "FAIL edge mfe entry"; exit 1; }
MFE_CSS="$(printf '%s' "$MFE_MANIFEST" | python3 -c 'import json,sys; print(json.load(sys.stdin)["account"]["css"])')" || { echo "FAIL edge mfe manifest css"; exit 1; }
curl -sf "$EDGE/app/$MFE_CSS" | grep -q -- '--background' || { echo "FAIL edge mfe css"; exit 1; }
echo "ok edge mfe"

echo "== pricing surface (feature 03: edge 302 + frozen public plans read) =="
CODE="$(curl -s -o /dev/null -w '%{http_code}' "$EDGE/pricing")"
[ "$CODE" = "302" ] || { echo "FAIL /pricing=$CODE"; exit 1; }
LOC="$(curl -s -o /dev/null -w '%{redirect_url}' "$EDGE/pricing")"
case "$LOC" in
  */app/pricing) ;;
  *) echo "FAIL /pricing redirect -> $LOC"; exit 1 ;;
esac
PLANS_HEADERS="$(mktemp)"
if ! curl -sf -D "$PLANS_HEADERS" -o /dev/null "$EDGE/api/v1/plans"; then
  echo "FAIL public plans read"; rm -f "$PLANS_HEADERS"; exit 1
fi
grep -qi '^cache-control: public, max-age=300' "$PLANS_HEADERS" || { echo "FAIL plans cache headers"; rm -f "$PLANS_HEADERS"; exit 1; }
rm -f "$PLANS_HEADERS"
echo "ok pricing surface"

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
PROVIDERS="$(curl -sf "$EDGE/api/auth/providers")" || { echo "FAIL providers list"; exit 1; }
FIRST_ID="$(printf '%s' "$PROVIDERS" | awk -F'"id":"' 'NR==1 {split($2, a, "\""); print a[1]; exit}')"
if [ -z "$FIRST_ID" ]; then
  echo "skip login redirect (no provider configured in this environment)"
else
  CODE="$(curl -s -o /dev/null -w '%{http_code}' -X POST "$EDGE/api/auth/sign-in/social" \
    -d "provider=$FIRST_ID&callbackURL=/app")"
  [ "$CODE" = "302" ] || { echo "FAIL login redirect=$CODE"; exit 1; }
  echo "ok login redirect ($FIRST_ID -> IdP)"
fi
echo "PASS smoke"
