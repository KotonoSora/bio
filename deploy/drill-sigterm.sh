#!/bin/sh
# G3 SIGTERM drain drill: traffic in flight, SIGTERM via `stop -t`, then assert
# the container exits 0 BEFORE the kill timeout (drained, not killed) and
# restarts healthy. Queue consumers close via Spring graceful shutdown.
set -e
cd "$(dirname "$0")/.."
SVC="${1:-account-service}"
PORT="${2:-8081}"
NAME="$(docker compose ps -q "$SVC")"
[ -n "$NAME" ] || { echo "FAIL $SVC not running"; exit 1; }

echo "== background traffic =="
( for i in $(seq 1 40); do curl -sf "http://localhost:$PORT/health/liveness" >/dev/null 2>&1; sleep 0.5; done ) &
TRAFFIC=$!

echo "== SIGTERM, 25 s grace =="
START="$(date +%s)"
docker compose stop -t 25 "$SVC" >/dev/null
END="$(date +%s)"
kill "$TRAFFIC" 2>/dev/null || true
ELAPSED=$((END - START))
echo "stopped in ${ELAPSED}s"
[ "$ELAPSED" -lt 25 ] || { echo "FAIL: hit SIGKILL timeout (no drain)"; exit 1; }
CODE="$(docker inspect -f '{{.State.ExitCode}}' "$NAME")"
[ "$CODE" = "0" ] || { echo "FAIL exit=$CODE"; exit 1; }
docker compose logs --tail=30 "$SVC" 2>/dev/null | grep -qiE "shutdown|closing|closed" \
  && echo "ok shutdown marker" || echo "WARN no shutdown marker in logs"

echo "== restart healthy =="
docker compose up -d "$SVC" >/dev/null
for i in $(seq 1 40); do curl -sf "http://localhost:$PORT/health/liveness" >/dev/null && break || sleep 5; done
curl -sf "http://localhost:$PORT/health/liveness" >/dev/null || { echo "FAIL restart"; exit 1; }
echo "PASS sigterm (exit 0 in ${ELAPSED}s, restart green)"
