#!/bin/sh
# Feature 05 (concierge) opt-in wiring: full-login (mock IdP + dev JWT keypair)
# plus the zero-window SLA dev tier the AC-04 miss drill needs, then recreate
# account-service so the config lands. Idempotent.
#
# Writes the repo-root `.env` - the file every compose invocation interpolates.
# (A nested `e2e/.env` would be visible only to compose runs started from that
# directory, silently diverging from the stack every other command sees.)
# Restore with: npm run concierge:off
set -e
cd "$(dirname "$0")/../.."

sh e2e/scripts/enable-full-login.sh >/dev/null

python3 - <<'PY'
import pathlib

env = pathlib.Path('.env')
lines = env.read_text().splitlines() if env.exists() else []
values = {
    # Config-provisioned first agency owner (compose default mirrors this).
    'CONCIERGE_OWNER_USER_IDS': 'concierge-owner-dev',
    # Dev/e2e placeholder windows (Q-01: launch values come from the staffing
    # cost model before any sale; the mechanism is fixed, the numbers are not).
    'CONCIERGE_SLA_STANDARD_RESPONSE_MINUTES': '0',
    'CONCIERGE_SLA_STANDARD_COMPLETION_MINUTES': '0',
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
print('.env updated: concierge owner + zero-window dev SLA tier')
PY

docker compose up -d account-service >/dev/null
echo 'account-service recreated with the concierge dev wiring:'
docker compose exec -T account-service printenv CONCIERGE_OWNER_USER_IDS CONCIERGE_SLA_STANDARD_RESPONSE_MINUTES
echo 'run: npm run test:concierge   (from e2e/)'
