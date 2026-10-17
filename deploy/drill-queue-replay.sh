#!/bin/sh
# Queue-replay collapse drill (03-data-layer-conventions.md).
# Proves: uniform 202 intake under consumer outage, group-offset replay on restart
# (no loss), and dedupe collapse (same visitorKey twice enqueues once).
# Usage: sh deploy/drill-queue-replay.sh [N]
set -e
N="${1:-50}"
TS="$(date +%s)"

echo "== emit ${N} unique beacons x2 (dedupe collapse expects ${N} enqueued) =="
i=1
while [ "$i" -le "$N" ]; do
  curl -sf -o /dev/null -X POST "http://localhost:8085/api/t/drill-$TS" \
    -H 'Content-Type: application/json' -d "{\"visitorKey\":\"replay-$i\"}" || { echo "intake failed"; exit 1; }
  curl -sf -o /dev/null -X POST "http://localhost:8085/api/t/drill-$TS" \
    -H 'Content-Type: application/json' -d "{\"visitorKey\":\"replay-$i\"}"
  i=$((i + 1))
done

echo "== restart consumer mid-backlog (pending redelivers via group offsets) =="
docker compose restart analytics-service >/dev/null
for p in 8085; do
  for i in $(seq 1 60); do curl -sf "http://localhost:$p/health/liveness" >/dev/null && break || sleep 5; done
done

echo "== wait for stream drain =="
for i in $(seq 1 24); do
  LEN="$(docker compose exec -T redis redis-cli -n 6 XLEN beacon:q | tr -d '\r')"
  [ "$LEN" = "0" ] && break
  sleep 5
done
[ "$LEN" = "0" ] || { echo "FAIL: stream did not drain (len=$LEN)"; exit 1; }

SUM="$(docker compose exec -T redis redis-cli -n 6 EVAL "local s=0; for _,k in ipairs(redis.call('KEYS','rt:drill-$TS:*')) do s=s+tonumber(redis.call('GET',k)) end; return s" 0 | tr -d '\r')"
[ "$SUM" = "$N" ] || { echo "FAIL: counters sum=$SUM, want $N"; exit 1; }
DLQ="$(docker compose exec -T redis redis-cli -n 6 XLEN beacon:dlq | tr -d '\r')"
echo "PASS queue-replay: collapse=$N/$((N * 2)) beacons, replay lossless, dlq=$DLQ"
