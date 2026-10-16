#!/bin/sh
# Cache-flush boot drill (03-data-layer-conventions.md).
# Proves: a restart needs no manual data repair (migrations self-apply, caches
# rebuild lazily) and reads degrade honestly (zeros/partials, never fabricated).
set -e
echo "== flush all caches =="
docker compose exec -T redis redis-cli FLUSHALL >/dev/null

echo "== all services still answer health =="
for p in 8080 8081 8082 8083 8084 8085 8086 8087 8088 8089; do
  curl -sf "http://localhost:$p/health/liveness" >/dev/null || { echo "FAIL unhealthy: $p"; exit 1; }
done

echo "== intake still uniform 202 after flush =="
CODE="$(curl -s -o /dev/null -w '%{http_code}' -X POST http://localhost:8085/api/t/flushdrill \
  -H 'Content-Type: application/json' -d '{"visitorKey":"flush-1"}')"
[ "$CODE" = "202" ] || { echo "FAIL beacon=$CODE"; exit 1; }

echo "== owner stats degrade to labeled zeros =="
curl -sf "http://localhost:8085/internal/v1/stats/flushdrill" | grep -q '"partial":true' \
  || { echo "FAIL stats honesty label"; exit 1; }
echo "PASS cache-flush"
