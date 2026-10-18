#!/bin/sh
# Point the Dev SSO (generic-oidc) provider at the local mock IdP
# (e2e/mock-idp), ensure the dev internal-JWT keypair, and recreate the
# gateway + account-service so the opt-in full-login e2e suite can run.
# Idempotent. Restore with: sh scripts/disable-full-login.sh
set -e
cd "$(dirname "$0")/../.."

python3 - <<'PY'
import pathlib

env = pathlib.Path('.env')
lines = env.read_text().splitlines() if env.exists() else []
values = {
    'GENERIC_OIDC_ENABLED': 'true',
    'GENERIC_OIDC_LABEL': 'Dev SSO',
    'GENERIC_OIDC_CLIENT_ID': 'dev-smoke-client',
    'GENERIC_OIDC_AUTHORIZE_URI': 'http://localhost:9099/authorize',
    'GENERIC_OIDC_TOKEN_URI': 'http://host.docker.internal:9099/token',
    'GENERIC_OIDC_JWK_SET_URI': 'http://host.docker.internal:9099/jwks',
    'GENERIC_OIDC_ISSUER': 'http://host.docker.internal:9099',
}
seen = set()
for i, line in enumerate(lines):
    key = line.split('=', 1)[0]
    if key in values:
        lines[i] = f'{key}={values[key]}'
        seen.add(key)
for key, value in values.items():
    if key not in seen:
        lines.append(f'{key}={value}')
env.write_text('\n'.join(lines) + '\n')
print('.env updated: generic-oidc -> mock IdP (localhost:9099)')
PY

# Internal identity keys (feature 01 D-6). Without a keypair the gateway mints
# no identity JWT and account-service falls back to its dev-user branch, so no
# real session can authorize /api/v1/**. Values stay single-line in .env;
# compose unescapes \n in double-quoted values before the containers see them.
if grep -q '^INTERNAL_JWT_PRIVATE_KEY_PEM=.' .env && grep -q '^INTERNAL_JWT_PUBLIC_KEY_PEM=.' .env; then
  echo 'internal JWT keypair: present (.env kept)'
else
  command -v openssl >/dev/null 2>&1 || { echo 'need openssl to generate the dev internal JWT keypair'; exit 1; }
  tmp="$(mktemp -d)"
  trap 'rm -rf "$tmp"' EXIT
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$tmp/priv.pem" 2>/dev/null
  openssl pkey -in "$tmp/priv.pem" -pubout -out "$tmp/pub.pem" 2>/dev/null
  priv="$(awk 'NF {sub(/\r/, ""); printf "%s\\n", $0}' "$tmp/priv.pem")"
  pub="$(awk 'NF {sub(/\r/, ""); printf "%s\\n", $0}' "$tmp/pub.pem")"
  python3 - "$priv" "$pub" <<'PY'
import pathlib, sys

priv, pub = sys.argv[1], sys.argv[2]
env = pathlib.Path('.env')
lines = env.read_text().splitlines() if env.exists() else []
def setv(key: str, value: str) -> None:
    for i, line in enumerate(lines):
        if line.split('=', 1)[0] == key:
            lines[i] = f'{key}="{value}"'
            return
    lines.append(f'{key}="{value}"')
setv('INTERNAL_JWT_PRIVATE_KEY_PEM', priv)
setv('INTERNAL_JWT_PUBLIC_KEY_PEM', pub)
env.write_text('\n'.join(lines) + '\n')
print('.env updated: dev internal-JWT keypair generated')
PY
fi

# Mesh credential (feature 02 S-5): with the internal keypair configured the
# verifiers leave their dev branch and require service JWTs on /internal/v1/*,
# so outbound mesh calls must be signed with the same dev key (MESH_* mirrors
# the internal private key; prod swaps in the mesh key material).
python3 - <<'PY'
import pathlib
env = pathlib.Path('.env')
lines = env.read_text().splitlines()
priv = next((l.split('=', 1)[1] for l in lines if l.startswith('INTERNAL_JWT_PRIVATE_KEY_PEM=')), '')
value = f'MESH_JWT_PRIVATE_KEY_PEM={priv}' if priv and priv != '""' else 'MESH_JWT_PRIVATE_KEY_PEM='
for i, line in enumerate(lines):
    if line.startswith('MESH_JWT_PRIVATE_KEY_PEM='):
        lines[i] = value
        break
else:
    lines.append(value)
env.write_text('\n'.join(lines) + '\n')
print('.env updated: mesh signing key -> dev internal keypair')
PY

docker compose up -d gateway account-service profile-service >/dev/null
# The edge caches upstream IPs at startup: recreate it last so nginx
# re-resolves the (possibly new) gateway IP, or /api/* answers with stale
# upstream 404s while the gateway is fine.
docker compose up -d --force-recreate nginx >/dev/null
echo 'gateway + account-service + profile-service + edge recreated:'
docker compose exec -T gateway printenv INTERNAL_JWT_PRIVATE_KEY_PEM | head -1
docker compose exec -T account-service printenv INTERNAL_JWT_PUBLIC_KEY_PEM | head -1
docker inspect "$(docker compose ps -q account-service)" --format '{{range .Config.Env}}{{println .}}{{end}}' | grep '^GENERIC_OIDC_AUTHORIZE_URI=' || true
echo 'run: npm run test:full   (from e2e/)'
