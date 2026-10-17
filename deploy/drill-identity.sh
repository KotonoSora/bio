#!/bin/sh
# E0 identity proof (05-delivery-phasing-and-scaffold.md): the gateway's identity
# wiring works end to end — a service verifies the gateway-signed JWT audience
# (RS256, 60 s TTL, JWKS published). Forged tokens are denied. Requires the
# stack up; uses a throwaway keypair (nothing committed).
set -e
cd "$(dirname "$0")/.."
need() { command -v "$1" >/dev/null 2>&1 || { echo "need $1"; exit 1; }; }
need openssl; need python3; need curl
python3 -c "import jwt" 2>/dev/null || pip install -q pyjwt[crypto]

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out /tmp/idt-priv.pem 2>/dev/null
openssl pkey -in /tmp/idt-priv.pem -pubout -out /tmp/idt-pub.pem 2>/dev/null
awk 'NF {sub(/\r/, ""); printf "%s\\n", $0}' /tmp/idt-priv.pem > /tmp/idt-priv.env
awk 'NF {sub(/\r/, ""); printf "%s\\n", $0}' /tmp/idt-pub.pem > /tmp/idt-pub.env

export INTERNAL_JWT_PRIVATE_KEY_PEM="$(cat /tmp/idt-priv.env)"
export INTERNAL_JWT_PUBLIC_KEY_PEM="$(cat /tmp/idt-pub.env)"
export INTERNAL_JWT_PREVIOUS_PUBLIC_KEY_PEM=""
docker compose up -d --build gateway profile-service >/dev/null
for i in $(seq 1 40); do curl -sf http://localhost:8082/health/liveness >/dev/null && break || sleep 5; done

echo "== JWKS published =="
curl -sf http://localhost:8080/.well-known/jwks.json | grep -q '"kty"' || { echo "FAIL jwks"; exit 1; }
echo "ok jwks"

echo "== audience verified by sample service =="
TOKEN="$(python3 - <<'EOF'
import time, jwt
priv = open('/tmp/idt-priv.pem').read()
now = int(time.time())
print(jwt.encode({"sub": "e0-user", "aud": "profile-service", "iat": now, "exp": now + 60}, priv, algorithm="RS256"))
EOF
)"
CODE="$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $TOKEN" http://localhost:8082/internal/v1/profiles/by-username/nonexistent)"
[ "$CODE" = "404" ] || { echo "FAIL audience verify: got $CODE"; exit 1; }
echo "ok audience verified (unknown user -> 404, not 401)"

echo "== wrong audience denied =="
WRONG="$(python3 - <<'EOF'
import time, jwt
priv = open('/tmp/idt-priv.pem').read()
now = int(time.time())
print(jwt.encode({"sub": "e0-user", "aud": "billing-service", "iat": now, "exp": now + 60}, priv, algorithm="RS256"))
EOF
)"
CODE="$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $WRONG" http://localhost:8082/internal/v1/profiles/by-username/nonexistent)"
[ "$CODE" = "401" ] || { echo "FAIL audience mismatch not denied: got $CODE"; exit 1; }
echo "ok wrong audience denied"

echo "== forged token denied =="
CODE="$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer forged.token.value" http://localhost:8082/internal/v1/profiles/by-username/nonexistent)"
[ "$CODE" = "401" ] || { echo "FAIL forged not denied: got $CODE"; exit 1; }
echo "PASS identity (issue -> verify -> deny)"
rm -f /tmp/idt-*
