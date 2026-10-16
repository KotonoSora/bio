#!/bin/sh
# G4 contract gate: OpenAPI additive-only check for /api/v1 + /internal/v1.
# Compares live springdoc specs against deploy/openapi-baseline/<service>.json.
# Breaking change (removed path, method, or response code) fails the gate.
# Additive additions pass. Missing baseline = first run initializes it (E0 empty baseline).
set -e
BASELINE_DIR="deploy/openapi-baseline"
mkdir -p "$BASELINE_DIR"
SPECS="gateway:8080 account-service:8081 profile-service:8082 link-service:8083 public-site-service:8084 analytics-service:8085 resume-service:8086 shop-service:8087 billing-service:8088 publish-service:8089"
fail=0
for pair in $SPECS; do
  name="${pair%%:*}"; port="${pair##*:}"
  if ! curl -sf "http://localhost:${port}/v3/api-docs" -o "/tmp/spec-${name}.json"; then
    echo "FAIL ${name}: spec unreachable"; fail=1; continue
  fi
  if [ ! -f "${BASELINE_DIR}/${name}.json" ]; then
    cp "/tmp/spec-${name}.json" "${BASELINE_DIR}/${name}.json"
    echo "INIT baseline ${name}"
    continue
  fi
  if python3 - "deploy/openapi-baseline/${name}.json" "/tmp/spec-${name}.json" <<'EOF'; then
import json, sys
base = json.load(open(sys.argv[1])).get("paths", {})
cur = json.load(open(sys.argv[2])).get("paths", {})
removed = []
for path, ops in base.items():
    if path not in cur:
        removed.append(f"removed path {path}")
        continue
    for method, op in ops.items():
        if method not in cur[path]:
            removed.append(f"removed {method.upper()} {path}")
            continue
        for code in (op.get("responses") or {}):
            if code not in (cur[path][method].get("responses") or {}):
                removed.append(f"removed response {code} on {method.upper()} {path}")
if removed:
    print("BREAKING:\n  " + "\n  ".join(removed))
    sys.exit(1)
print("OK additive-only")
EOF
    echo "OK ${name}: additive-only"
  else
    echo "FAIL ${name}: breaking change"; fail=1
  fi
done
exit $fail
