#!/bin/sh
# Enable the opt-in custom-domains serving e2e suite (feature 04): the dev
# full-login wiring plus a local mock DoH resolver so TXT verification never
# touches the public internet. The plan half of the gate is seeded per run by
# the suite itself (Redis `rb:plan:{userId}` - the same cache the ITs use).
# Restore with: sh scripts/disable-domains-pro.sh
set -e
cd "$(dirname "$0")/../.."

python3 - <<'PY'
import pathlib

env = pathlib.Path('.env')
lines = env.read_text().splitlines() if env.exists() else []
values = {
    # profile-service reaches the mock resolver on the host loopback
    # (Docker Desktop host.docker.internal, same as the mock IdP wiring).
    'DOMAINS_DOH_RESOLVER': 'http://host.docker.internal:9097/resolve',
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
print('.env updated: DOMAINS_DOH_RESOLVER -> mock DoH (host.docker.internal:9097)')
PY

# Full-login wiring (mock IdP + dev internal-JWT keypair) and one recreate
# pass that now also carries the DoH override into profile-service; the edge
# is recreated last so nginx re-resolves the fresh upstream IPs.
sh e2e/scripts/enable-full-login.sh
echo 'run: npm run test:domains-pro   (from e2e/)'
