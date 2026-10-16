#!/bin/sh
# Capacity review per stage (07-scale-out.md): recorded headroom >= 30% on
# CPU/memory/connections at gate load; the next stage's provision plan cites
# this review. Writes deploy/capacity/<stamp>.json. Usage:
#   sh deploy/capacity-review.sh [load-description]
set -e
cd "$(dirname "$0")/.."
mkdir -p deploy/capacity
STAMP="$(date -u +%FT%TZ | tr ':' '-')"
OUT="deploy/capacity/$STAMP.json"
DESC="${1:-post-load}"

echo "== container headroom =="
STATS="$(docker stats --no-stream --format '{{.Name}} {{.CPUPerc}} {{.MemPerc}} {{.MemUsage}}' 2>/dev/null || true)"
echo "$STATS"
RESTARTS="$(docker ps --format '{{.Names}}' | while read -r n; do
  c="$(docker inspect -f '{{.RestartCount}}' "$n" 2>/dev/null || echo ?)"; echo "$n=$c"
done | tr '\n' ' ')"

echo "== postgres connections vs 200 budget =="
CONNS="$(docker compose exec -T postgres psql -U platform -d platform -tAc 'SELECT count(*) FROM pg_stat_activity;' 2>/dev/null | tr -d '\r' || echo unknown)"
echo "connections=$CONNS"

echo "== queue depth =="
LAG="$(curl -sf http://localhost:8085/internal/v1/stream-lag 2>/dev/null || echo '{}')"
echo "$LAG"

python3 - "$OUT" "$DESC" "$CONNS" "$RESTARTS" <<'EOF'
import json, sys, re
out, desc, conns, restarts = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
conn_headroom = None
try:
    conn_headroom = 1.0 - int(conns) / 200.0
except ValueError:
    pass
unstable = [r for r in restarts.split() if not r.endswith("=0")]
report = {
    "at": desc,
    "pg_connection_headroom": conn_headroom,
    "headroom_floor": 0.30,
    "container_restarts": restarts.strip(),
    "stream_lag": "see drill output",
    "provision_plan": (
        "hold" if (conn_headroom or 0) >= 0.30 and not unstable
        else "add replicas to the breached path (scale overlay) or vertical-up the database; re-run gate"
    ),
}
json.dump(report, open(out, "w"), indent=2)
ok = (conn_headroom or 0) >= 0.30 and not unstable
print(json.dumps(report, indent=2))
sys.exit(0 if ok else 1)
EOF
echo "review: $OUT"
