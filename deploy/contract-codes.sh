#!/bin/sh
# G4 contract gate, part 1: stable ProblemDetail `code` strings are never renamed
# or removed (additive-only). Additions pass; removals fail the gate.
set -e
cd "$(dirname "$0")/.."
BASE="deploy/contract-codes-baseline.txt"
CUR="$({ grep -rhoE '"code", "[a-z_]+"|"code":"[a-z_]+"' services/ gateway/ --include=*.kt | grep -oE '"[a-z_]+"$' | tr -d '"'; grep -rhoE 'deny\(response, [0-9]+, "[a-z_]+"' services/ gateway/ --include=*.kt | grep -oE '"[a-z_]+"$' | tr -d '"'; } | sort -u)"
if [ -z "$CUR" ]; then echo "FAIL: no stable codes found"; exit 1; fi
if [ ! -f "$BASE" ]; then
  printf '%s\n' "$CUR" > "$BASE"
  echo "INIT code baseline ($(printf '%s\n' "$CUR" | wc -l | tr -d ' ') codes)"
  exit 0
fi
printf '%s\n' "$CUR" > /tmp/codes-cur.txt
REMOVED="$(comm -23 "$BASE" /tmp/codes-cur.txt)"
ADDED="$(comm -13 "$BASE" /tmp/codes-cur.txt)"
[ -n "$ADDED" ] && echo "added codes: $(echo "$ADDED" | tr '\n' ' ')"
if [ -n "$REMOVED" ]; then
  echo "FAIL breaking: removed/renamed codes: $(echo "$REMOVED" | tr '\n' ' ')"
  exit 1
fi
echo "OK codes additive-only"
