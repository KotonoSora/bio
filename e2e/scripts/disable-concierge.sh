#!/bin/sh
# Feature 05 (concierge) opt-out: clear the zero-window dev SLA tier so the
# compose defaults (normal windows) apply again, and recreate account-service.
# Idempotent. The full-login wiring stays until `npm run idp:off`.
# Operates on the repo-root `.env` (same file `enable-concierge.sh` writes).
set -e
cd "$(dirname "$0")/../.."

python3 - <<'PY'
import pathlib

env = pathlib.Path('.env')
lines = env.read_text().splitlines() if env.exists() else []
values = {
    'CONCIERGE_SLA_STANDARD_RESPONSE_MINUTES': '',
    'CONCIERGE_SLA_STANDARD_COMPLETION_MINUTES': '',
}
for i, line in enumerate(lines):
    key = line.split('=', 1)[0]
    if key in values:
        lines[i] = f'{key}={values[key]}'
env.write_text('\n'.join(lines) + '\n')
print('.env updated: concierge dev SLA windows cleared (compose defaults apply)')
PY

docker compose up -d account-service >/dev/null
echo 'account-service recreated: concierge dev SLA tier restored to defaults'
