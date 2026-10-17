#!/bin/sh
# Restore-to-scratch drill (03-data-layer-conventions.md, RPO proof shape).
# Dumps every service database (read-only on live) and replays onto a SEPARATE
# scratch instance. No manual SQL ever touches live databases: schema changes
# ride Flyway forward-only (05-delivery-phasing-and-scaffold.md). The quarterly
# production drill replays from WAL/PITR backups; this proves the replay path.
# Logged per release via deploy/run-drill.sh.
set -e
cd "$(dirname "$0")/.."
PG_IMG="postgres:18.6@sha256:74935e72241653ca55e0414067e6d8763aceb8a810eb51b452253ec3dcfc4336"
DBS="account_db profile_db links_db analytics_db resume_db shop_db billing_db publish_db"
DUMP_DIR="${DUMP_DIR:-/tmp/restore-drill}"
NET="$(docker network ls --format '{{.Name}}' | grep '_platform$' | head -1)"
mkdir -p "$DUMP_DIR"

echo "== dump (read-only) =="
for db in $DBS; do
  docker compose exec -T postgres pg_dump -U platform -d "$db" -Fc -f "/tmp/${db}.dump"
  docker compose cp "postgres:/tmp/${db}.dump" "$DUMP_DIR/${db}.dump"
done

echo "== scratch instance =="
docker rm -f pg-scratch >/dev/null 2>&1 || true
docker run -d --name pg-scratch --network "$NET" -e POSTGRES_USER=platform -e POSTGRES_PASSWORD=pw "$PG_IMG" >/dev/null
for i in $(seq 1 30); do docker exec pg-scratch pg_isready -U platform >/dev/null 2>&1 && break || sleep 2; done

echo "== replay + assert =="
for db in $DBS; do
  docker exec -i pg-scratch psql -U platform -d platform -q -c "CREATE DATABASE $db;" >/dev/null
  docker cp "$DUMP_DIR/${db}.dump" "pg-scratch:/tmp/${db}.dump"
  docker exec pg-scratch pg_restore -U platform -d "$db" "/tmp/${db}.dump" >/dev/null
  VER="$(docker exec pg-scratch psql -U platform -d "$db" -tAc "SHOW server_version;" | tr -d '\r')"
  case "$VER" in 18.6*) ;; *) echo "FAIL $db version=$VER"; exit 1;; esac
  docker exec pg-scratch psql -U platform -d "$db" -tAc "SELECT count(*) FROM audit_log;" >/dev/null
  echo "ok $db ($VER)"
done
docker rm -f pg-scratch >/dev/null
echo "PASS restore-to-scratch ($DUMP_DIR)"
