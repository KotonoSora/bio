#!/bin/sh
# Drill runner: executes a deploy drill script and archives the output with the
# release note (traceability: drill results archived per tag). Usage:
#   sh deploy/run-drill.sh drill-cache-flush.sh [args...]
set -e
cd "$(dirname "$0")"
mkdir -p drill-log
SCRIPT="$1"; shift
STAMP="$(date -u +%FT%TZ | tr ':' '-')"
LOG="drill-log/${SCRIPT%.sh}-$STAMP.log"
echo "== $SCRIPT @ $STAMP ==" | tee "$LOG"
sh "$SCRIPT" "$@" 2>&1 | tee -a "$LOG"
echo "logged: deploy/$LOG"
