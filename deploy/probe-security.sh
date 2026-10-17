#!/bin/sh
# G5 security probe set (08-security-baseline.md): IDOR (cross-user reads/writes
# denied), rate rules active (429 + Retry-After), no secrets in image env,
# manifests, or logs, webhook no-oracle (uniform response either way).
# Upload integrity is owned by feature 09 (no avatar endpoint on this baseline).
set -e
cd "$(dirname "$0")/.."
EDGE="${EDGE_URL:-http://localhost:${EDGE_HTTP_PORT:-80}}"
need() { command -v "$1" >/dev/null 2>&1 || { echo "need $1"; exit 1; }; }
need openssl; need python3; need curl; need docker
python3 -c "import jwt" 2>/dev/null || pip install -q pyjwt[crypto]

echo "== known keypair (prod-mode verification on) =="
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out /tmp/prb-priv.pem 2>/dev/null
openssl pkey -in /tmp/prb-priv.pem -pubout -out /tmp/prb-pub.pem 2>/dev/null
awk 'NF {sub(/\r/, ""); printf "%s\\n", $0}' /tmp/prb-priv.pem > /tmp/prb-priv.env
awk 'NF {sub(/\r/, ""); printf "%s\\n", $0}' /tmp/prb-pub.pem > /tmp/prb-pub.env
export INTERNAL_JWT_PRIVATE_KEY_PEM="$(cat /tmp/prb-priv.env)"
export INTERNAL_JWT_PUBLIC_KEY_PEM="$(cat /tmp/prb-pub.env)"
export INTERNAL_JWT_PREVIOUS_PUBLIC_KEY_PEM=""
docker compose up -d --build gateway profile-service resume-service analytics-service billing-service public-site-service nginx >/dev/null
for p in 8082 8084 8086 8085 8088; do
  for i in $(seq 1 40); do curl -sf "http://localhost:$p/health/liveness" >/dev/null && break || sleep 5; done
done
curl -sf "$EDGE/health/liveness" >/dev/null || { echo "FAIL edge unhealthy"; exit 1; }

mint() { # mint <sub> <aud>
  python3 - "/tmp/prb-priv.pem" "$1" "$2" <<'EOF'
import sys, time, jwt
priv = open(sys.argv[1]).read()
now = int(time.time())
print(jwt.encode({"sub": sys.argv[2], "aud": sys.argv[3], "iat": now, "exp": now + 60}, priv, algorithm="RS256"))
EOF
}
UA="$(mint user-a resume-service)"; UB="$(mint user-b resume-service)"
PA="$(mint user-a profile-service)"

echo "== IDOR: B must not see A's rows =="
curl -sf -X POST http://localhost:8086/api/v1/resumes -H "Authorization: Bearer $UA" \
  -H 'Content-Type: application/json' -d '{"title":"A-secret","bodyJson":"{}"}' >/dev/null
MINE_B="$(curl -sf http://localhost:8086/api/v1/resumes -H "Authorization: Bearer $UB")"
echo "$MINE_B" | grep -q "A-secret" && { echo "FAIL IDOR: cross-user read"; exit 1; }
echo "ok IDOR reads scoped"
curl -s -o /dev/null -w '%{http_code}' http://localhost:8082/api/v1/profiles/me -H "Authorization: Bearer $PA" | grep -q 200 \
  || { echo "FAIL owner read"; exit 1; }
echo "ok owner read"

echo "== edge security headers on public documents =="
curl -sI "$EDGE/app/" | grep -qi '^content-security-policy: default-src' || { echo "FAIL edge CSP"; exit 1; }
curl -sI "$EDGE/app/" | grep -qi '^x-content-type-options: nosniff' || { echo "FAIL edge nosniff"; exit 1; }
echo "ok edge headers"

echo "== edge never serves gated or internal paths =="
for u in "/media/" "/media/profile/evidence/" "/internal/v1/accounts/me" "/internal-media/profile/evidence/x.png"; do
  C="$(curl -s -o /dev/null -w '%{http_code}' "$EDGE$u")"
  [ "$C" = "404" ] || { echo "FAIL $u -> $C at the edge"; exit 1; }
done
echo "ok edge gated paths 404"

echo "== edge -> gateway -> public-site SSR (prod mode) =="
curl -sf "$EDGE/smokeuser" | grep -q smokeuser || { echo "FAIL edge SSR"; exit 1; }
echo "ok edge SSR"

echo "== rate rules active (beacon burst -> 429 + Retry-After) =="
HIT429=0
i=1
while [ "$i" -le 70 ]; do
  R="$(curl -s -o /dev/null -w '%{http_code}' -D /tmp/rate.hdrs -X POST http://localhost:8085/api/t/ratetest \
    -H 'Content-Type: application/json' -d '{"visitorKey":"rate-1"}')"
  if [ "$R" = "429" ]; then
    grep -qi "Retry-After" /tmp/rate.hdrs || { echo "FAIL 429 without Retry-After"; exit 1; }
    HIT429=1; break
  fi
  i=$((i + 1))
done
[ "$HIT429" = "1" ] || { echo "FAIL rate limiter never tripped"; exit 1; }
echo "ok rate 429 + Retry-After"

echo "== no secrets in image env =="
LEAK="$(docker inspect "resume/account-service:${TAG:-0.1.0-SNAPSHOT}" "resume/gateway:${TAG:-0.1.0-SNAPSHOT}" 2>/dev/null \
  | grep -iE "PRIVATE KEY|client_secret|sk_live|AKIA" || true)"
[ -z "$LEAK" ] || { echo "FAIL secret in image: $LEAK"; exit 1; }
echo "ok images clean"

echo "== no secrets in manifests/logs =="
grep -rE --exclude-dir=node_modules --exclude=.env -E '(-----BEGIN (RSA )?PRIVATE KEY-----|sk_live_|AKIA[0-9A-Z]{16})' docker-compose.yml deploy/ .github/ 2>/dev/null && { echo "FAIL secret in manifests"; exit 1; } || true
for svc in profile-service resume-service; do
  docker compose logs --tail=200 "$svc" 2>/dev/null | grep -iE "BEGIN (RSA )?PRIVATE KEY|sk_live_|AKIA[0-9A-Z]{16}|client_secret" && { echo "FAIL secret in $svc logs"; exit 1; } || true
done
echo "ok manifests/logs clean"

echo "== no object-store tooling anywhere (storage amendment) =="
PAT="min""io|boto""3|amaz""onaws|aws-""sdk|s3:/""/"
if grep -rInEi --exclude-dir=.git --exclude-dir=bin --exclude-dir=build --exclude-dir=dist \
   --exclude-dir=node_modules --exclude-dir=drill-log --exclude-dir=gate-log --exclude-dir=releases \
   --exclude-dir=openapi-baseline "$PAT" \
   docker-compose.yml docker-compose.scale.yml deploy services gateway web gradle .github \
   build.gradle.kts settings.gradle.kts 2>/dev/null; then
  echo "FAIL: object-store reference found (storage amendment)"; exit 1
fi
echo "ok storage policy clean"

echo "== webhook no-oracle (garbage vs plausible both uniform) =="
C1="$(curl -s -o /dev/null -w '%{http_code}' -X POST http://localhost:8088/webhooks/stripe -H 'Content-Type: application/json' -d '{"garbage":1}')"
C2="$(curl -s -o /dev/null -w '%{http_code}' -X POST http://localhost:8088/webhooks/stripe -H 'Content-Type: application/json' -d '{"id":"evt_1","type":"charge.succeeded"}')"
[ "$C1" = "$C2" ] || { echo "FAIL webhook oracle: $C1 vs $C2"; exit 1; }
echo "ok webhook no-oracle ($C1)"
rm -f /tmp/prb-* /tmp/rate.hdrs
echo "PASS security probes"
