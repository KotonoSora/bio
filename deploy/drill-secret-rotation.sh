#!/bin/sh
# Internal JWT dual-key rotation drill (04-containers-and-compose.md).
# Proves services roll without downtime: from a known keypair A, rotate the
# gateway to signing with B while services verify B with A in the previous-key
# window; tokens from BOTH keys must be accepted during the window (60 s TTL).
# PyJWT is installed ephemerally for minting; nothing is committed.
set -e
cd "$(dirname "$0")/.."
need() { command -v "$1" >/dev/null 2>&1 || { echo "need $1"; exit 1; }; }
need openssl; need python3; need curl
python3 -c "import jwt" 2>/dev/null || pip install -q pyjwt[crypto]

genpair() {
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "/tmp/rot-$1-priv.pem" 2>/dev/null
  openssl pkey -in "/tmp/rot-$1-priv.pem" -pubout -out "/tmp/rot-$1-pub.pem" 2>/dev/null
  # single-line env form
  awk 'NF {sub(/\r/, ""); printf "%s\\n", $0}' "/tmp/rot-$1-priv.pem" > "/tmp/rot-$1-priv.env"
  awk 'NF {sub(/\r/, ""); printf "%s\\n", $0}' "/tmp/rot-$1-pub.pem" > "/tmp/rot-$1-pub.env"
}
genpair a
genpair b

mint() { # mint <keyname> <aud> -> token
  python3 - "/tmp/rot-$1-priv.pem" "$2" <<'EOF'
import sys, time, jwt
priv = open(sys.argv[1]).read()
aud = sys.argv[2]
now = int(time.time())
print(jwt.encode({"sub": "drill-user", "aud": aud, "iat": now, "exp": now + 60}, priv, algorithm="RS256"))
EOF
}

echo "== phase 1: known pair A everywhere =="
export INTERNAL_JWT_PRIVATE_KEY_PEM="$(cat /tmp/rot-a-priv.env)"
export INTERNAL_JWT_PUBLIC_KEY_PEM="$(cat /tmp/rot-a-pub.env)"
export INTERNAL_JWT_PREVIOUS_PUBLIC_KEY_PEM=""
docker compose up -d --build gateway profile-service >/dev/null
for i in $(seq 1 40); do curl -sf http://localhost:8081/health/liveness >/dev/null && break || sleep 5; done
CODE_A="$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $(mint a profile-service)" http://localhost:8082/internal/v1/profiles/by-username/nonexistent)"
[ "$CODE_A" = "404" ] || { echo "FAIL phase1: want 404 (key accepted), got $CODE_A"; exit 1; }
echo "ok phase1: pair-A token accepted"

echo "== phase 2: gateway signs with B, services verify B with A in window =="
export INTERNAL_JWT_PRIVATE_KEY_PEM="$(cat /tmp/rot-b-priv.env)"
export INTERNAL_JWT_PUBLIC_KEY_PEM="$(cat /tmp/rot-b-pub.env)"
export INTERNAL_JWT_PREVIOUS_PUBLIC_KEY_PEM="$(cat /tmp/rot-a-pub.env)"
docker compose up -d gateway profile-service >/dev/null
sleep 8
CODE_B="$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $(mint b profile-service)" http://localhost:8082/internal/v1/profiles/by-username/nonexistent)"
CODE_OLD="$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $(mint a profile-service)" http://localhost:8082/internal/v1/profiles/by-username/nonexistent)"
[ "$CODE_B" = "404" ] || { echo "FAIL new key: got $CODE_B"; exit 1; }
[ "$CODE_OLD" = "404" ] || { echo "FAIL previous key window: got $CODE_OLD"; exit 1; }
CODE_BAD="$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer invalid.token.here" http://localhost:8082/internal/v1/profiles/by-username/nonexistent)"
[ "$CODE_BAD" = "401" ] || { echo "FAIL forged token not denied: got $CODE_BAD"; exit 1; }
echo "PASS rotation: new signs, previous verifies in window, forged denied"
rm -f /tmp/rot-a-* /tmp/rot-b-*
