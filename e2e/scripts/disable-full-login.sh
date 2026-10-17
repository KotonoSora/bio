#!/bin/sh
# Restore the dev placeholder IdP (idp.invalid) and the documented dev-user
# internal-identity baseline (blank keys) after full-login e2e runs, then
# recreate gateway + account-service + profile-service. Mirror of
# scripts/enable-full-login.sh. deploy/smoke.sh reads services directly and
# relies on the dev-user fallback.
set -e
cd "$(dirname "$0")/../.."

python3 - <<'PY'
import pathlib

env = pathlib.Path('.env')
lines = env.read_text().splitlines() if env.exists() else []
remove = {
    'GENERIC_OIDC_AUTHORIZE_URI',
    'GENERIC_OIDC_TOKEN_URI',
    'GENERIC_OIDC_JWK_SET_URI',
    'GENERIC_OIDC_USERINFO_URI',
}
blank = {'GENERIC_OIDC_CLIENT_ID', 'GENERIC_OIDC_CLIENT_SECRET', 'GENERIC_OIDC_ISSUER',
         'INTERNAL_JWT_PRIVATE_KEY_PEM', 'INTERNAL_JWT_PUBLIC_KEY_PEM'}
out = []
for line in lines:
    key = line.split('=', 1)[0]
    if key in remove:
        continue
    out.append(f'{key}=' if key in blank else line)
env.write_text('\n'.join(out).rstrip() + '\n')
print('.env restored: generic-oidc back to placeholder defaults')
PY

docker compose up -d gateway account-service profile-service >/dev/null
echo 'gateway + account-service + profile-service recreated (dev-user identity baseline):'
docker inspect "$(docker compose ps -q account-service)" --format '{{range .Config.Env}}{{println .}}{{end}}' | grep '^GENERIC_OIDC_AUTHORIZE_URI=' || true
docker compose exec -T account-service printenv INTERNAL_JWT_PUBLIC_KEY_PEM | wc -c | sed 's/^/  verify key bytes (1 = blank): /'
