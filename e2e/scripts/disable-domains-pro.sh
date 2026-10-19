#!/bin/sh
# Restore the production DoH default and recreate profile-service + edge after
# custom-domains serving e2e runs. The SSO/identity restore stays with
# disable-full-login.sh (npm run idp:off).
set -e
cd "$(dirname "$0")/../.."

python3 - <<'PY'
import pathlib

env = pathlib.Path('.env')
lines = env.read_text().splitlines() if env.exists() else []
out = [line for line in lines if line.split('=', 1)[0] != 'DOMAINS_DOH_RESOLVER']
env.write_text('\n'.join(out).rstrip() + '\n')
print('.env restored: DOMAINS_DOH_RESOLVER back to the dns.google default')
PY

docker compose up -d profile-service >/dev/null
# The edge caches upstream IPs at startup: recreate it last.
docker compose up -d --force-recreate nginx >/dev/null
echo 'profile-service + edge recreated (production DoH default)'
