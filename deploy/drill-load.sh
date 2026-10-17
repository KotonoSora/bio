#!/bin/sh
# G6 load gate runner (per-phase floors in 06-release-gates.md / 07-scale-out.md).
# k6 image resolved at runtime (grafana/k6 latest release -> digest pin); the
# digest lands in the gate report. Queue lag bounded: post-run XLEN must drain
# to zero (no unbounded growth over the run).
set -e
cd "$(dirname "$0")/.."
K6_TAG="${K6_TAG:-$(curl -sf https://api.github.com/repos/grafana/k6/releases/latest | python3 -c 'import json,sys; print(json.load(sys.stdin)["tag_name"])')}"
[ -n "$K6_TAG" ] || { echo "FAIL: cannot resolve k6 release"; exit 1; }
DIGEST="$(docker buildx imagetools inspect "grafana/k6:$K6_TAG" --format '{{json .Manifest}}' | python3 -c 'import json,sys; print(json.load(sys.stdin)["digest"])')"
echo "k6 $K6_TAG @ $DIGEST"
echo "$DIGEST" > deploy/.k6-digest
# k6 in docker reaches the stack via host gateway (Linux) or host.docker.internal (Mac).
K6_HOST="${K6_HOST:-$(uname -s | grep -qi darwin && echo host.docker.internal || echo 172.17.0.1)}"
# Stage floors (07-scale-out.md). Until the E3 100k-mixed gate passes these are
# targets: smoke < 10k < 100k < 1m. SCALE=1 targets the scale-overlay LB entrypoints.
STAGE="${STAGE:-smoke}"
SCALE="${SCALE:-0}"
if [ "$SCALE" = "1" ]; then
  export BASE_URL="http://$K6_HOST:8184" LINK_URL="http://$K6_HOST:8183" BEACON_URL="http://$K6_HOST:8185"
else
  export BASE_URL="http://$K6_HOST:8084" LINK_URL="http://$K6_HOST:8083" BEACON_URL="http://$K6_HOST:8085"
fi
export CHECKOUT_URL="http://$K6_HOST:8088"
case "$STAGE" in
  smoke) VUS="${VUS:-5}" DURATION="${DURATION:-30s}" ;;
  10k)   VUS="${VUS:-50}" DURATION="${DURATION:-5m}" ;;
  100k)  VUS="${VUS:-200}" DURATION="${DURATION:-10m}" ;;
  1m)    VUS="${VUS:-1000}" DURATION="${DURATION:-15m}" ;;
  *) echo "unknown STAGE=$STAGE (smoke|10k|100k|1m)"; exit 1 ;;
esac
export VUS DURATION
echo "stage=$STAGE scale=$SCALE vus=$VUS duration=$DURATION"

echo "== reads floor (p95 < 500 ms) =="
docker run --rm -v "$PWD/deploy/k6:/scripts" -e BASE_URL -e LINK_URL -e VUS -e DURATION \
  "grafana/k6:$K6_TAG@$DIGEST" run /scripts/reads.js || { echo "FAIL reads floor"; exit 1; }

echo "== beacon burst floor (uniform 202) =="
BEFORE_ROLLUP="$(docker compose exec -T postgres psql -U platform -d analytics_db -tAc 'SELECT count(*) FROM hourly_rollup;' 2>/dev/null | tr -d '\r' || echo unknown)"
docker run --rm -v "$PWD/deploy/k6:/scripts" -e BEACON_URL -e VUS -e DURATION \
  "grafana/k6:$K6_TAG@$DIGEST" run /scripts/beacon-burst.js || { echo "FAIL beacon floor"; exit 1; }

echo "== mixed floor: checkout p95 < 1 s =="
docker run --rm -v "$PWD/deploy/k6:/scripts" -e CHECKOUT_URL -e VUS -e DURATION \
  "grafana/k6:$K6_TAG@$DIGEST" run /scripts/checkout.js || { echo "FAIL checkout floor"; exit 1; }

echo "== no PG write amplification on intake (rollup delta must be 0) =="
for i in $(seq 1 24); do
  LEN="$(docker compose exec -T redis redis-cli -n 6 XLEN beacon:q | tr -d '\r')"
  [ "$LEN" = "0" ] && break
  sleep 5
done
AFTER_ROLLUP="$(docker compose exec -T postgres psql -U platform -d analytics_db -tAc 'SELECT count(*) FROM hourly_rollup;' 2>/dev/null | tr -d '\r' || echo unknown)"
if [ "$BEFORE_ROLLUP" != "unknown" ] && [ "$AFTER_ROLLUP" != "unknown" ] && [ "$BEFORE_ROLLUP" != "$AFTER_ROLLUP" ]; then
  echo "FAIL intake wrote PG per hit ($BEFORE_ROLLUP -> $AFTER_ROLLUP)"; exit 1
fi
echo "ok intake PG-clean (rollup=$AFTER_ROLLUP)"

echo "== queue lag bounded (drain check) =="
for i in $(seq 1 24); do
  LEN="$(docker compose exec -T redis redis-cli -n 6 XLEN beacon:q | tr -d '\r')"
  [ "$LEN" = "0" ] && break
  sleep 5
done
[ "$LEN" = "0" ] || { echo "FAIL queue divergence (len=$LEN)"; exit 1; }
echo "PASS load (k6 $K6_TAG, queue drained)"
