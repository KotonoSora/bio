#!/bin/sh
# G4 contract gate, part 2: event schemas are versioned <domain>.<fact>.v<major>
# and stable (no renames/removals). Validates every event literal in events
# packages plus domain constants, then diffs against the baseline.
set -e
cd "$(dirname "$0")/.."
BASE="deploy/contract-events-baseline.txt"
LITS="$(grep -rhoE '"[a-z][a-z0-9_]*\.[a-z][a-z0-9_.]*\.v[0-9]+"' services/ --include=*.kt | tr -d '"' | sort -u)"
if [ -z "$LITS" ]; then echo "FAIL: no versioned events found"; exit 1; fi
BAD="$(printf '%s\n' "$LITS" | grep -vE '^[a-z]+\.[a-z_]+\.v[0-9]+$' || true)"
if [ -n "$BAD" ]; then echo "FAIL unversioned event names: $BAD"; exit 1; fi
if [ ! -f "$BASE" ]; then
  printf '%s\n' "$LITS" > "$BASE"
  echo "INIT event baseline ($(printf '%s\n' "$LITS" | wc -l | tr -d ' ') events)"
  exit 0
fi
printf '%s\n' "$LITS" > /tmp/events-cur.txt
REMOVED="$(comm -23 "$BASE" /tmp/events-cur.txt)"
ADDED="$(comm -13 "$BASE" /tmp/events-cur.txt)"
[ -n "$ADDED" ] && echo "added events: $(echo "$ADDED" | tr '\n' ' ')"
if [ -n "$REMOVED" ]; then
  echo "FAIL breaking: removed/renamed events: $(echo "$REMOVED" | tr '\n' ' ')"
  exit 1
fi
echo "OK events versioned + additive-only"
