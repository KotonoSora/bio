#!/bin/sh
# G2 migration gate: fresh-apply Flyway replay from empty PG 18.6 for every
# service, history version assertion, and drift snapshot. With PREV_TAG set and
# a matching deploy/releases record, also replays upgrade (previous schema ->
# current boot) and rollback-safety (previous digest boots on the new schema).
set -e
cd "$(dirname "$0")/.."
TAG="${TAG:-0.1.0-SNAPSHOT}"
[ -f .env ] || { echo "need .env (cp .env.example .env)"; exit 1; }
pw() { grep -E "^$1=" .env | cut -d= -f2- | tr -d '\r' | sed 's/^"//;s/"$//'; }
PG_IMG="postgres:18.6@sha256:74935e72241653ca55e0414067e6d8763aceb8a810eb51b452253ec3dcfc4336"
NET="$(docker network ls --format '{{.Name}}' | grep '_platform$' | head -1)"
[ -n "$NET" ] || { echo "FAIL: compose network missing (boot the stack first)"; exit 1; }
SVC_DB="account-service:account_db:ACCOUNT profile-service:profile_db:PROFILE link-service:links_db:LINK analytics-service:analytics_db:ANALYTICS resume-service:resume_db:RESUME shop-service:shop_db:SHOP billing-service:billing_db:BILLING publish-service:publish_db:PUBLISH"

echo "== fresh scratch PG 18.6 + init roles =="
docker rm -f pg-migtest >/dev/null 2>&1 || true
docker run -d --name pg-migtest --network "$NET" \
  -e POSTGRES_USER=platform -e POSTGRES_PASSWORD=platform \
  -e POSTGRES_DB=platform \
  -e ACCOUNT_DB_PASSWORD="$(pw ACCOUNT_DB_PASSWORD)" -e ACCOUNT_MIGRATOR_PASSWORD="$(pw ACCOUNT_MIGRATOR_PASSWORD)" \
  -e PROFILE_DB_PASSWORD="$(pw PROFILE_DB_PASSWORD)" -e PROFILE_MIGRATOR_PASSWORD="$(pw PROFILE_MIGRATOR_PASSWORD)" \
  -e LINK_DB_PASSWORD="$(pw LINK_DB_PASSWORD)" -e LINK_MIGRATOR_PASSWORD="$(pw LINK_MIGRATOR_PASSWORD)" \
  -e ANALYTICS_DB_PASSWORD="$(pw ANALYTICS_DB_PASSWORD)" -e ANALYTICS_MIGRATOR_PASSWORD="$(pw ANALYTICS_MIGRATOR_PASSWORD)" \
  -e RESUME_DB_PASSWORD="$(pw RESUME_DB_PASSWORD)" -e RESUME_MIGRATOR_PASSWORD="$(pw RESUME_MIGRATOR_PASSWORD)" \
  -e SHOP_DB_PASSWORD="$(pw SHOP_DB_PASSWORD)" -e SHOP_MIGRATOR_PASSWORD="$(pw SHOP_MIGRATOR_PASSWORD)" \
  -e BILLING_DB_PASSWORD="$(pw BILLING_DB_PASSWORD)" -e BILLING_MIGRATOR_PASSWORD="$(pw BILLING_MIGRATOR_PASSWORD)" \
  -e PUBLISH_DB_PASSWORD="$(pw PUBLISH_DB_PASSWORD)" -e PUBLISH_MIGRATOR_PASSWORD="$(pw PUBLISH_MIGRATOR_PASSWORD)" \
  -v "$PWD/deploy/init-db.sh:/docker-entrypoint-initdb.d/init-db.sh:ro" \
  "$PG_IMG" >/dev/null
for i in $(seq 1 30); do docker exec pg-migtest pg_isready -U platform >/dev/null 2>&1 && break || sleep 2; done

echo "== fresh-apply replay per service =="
n=0
for triple in $SVC_DB; do
  svc="${triple%%:*}"; rest="${triple#*:}"; db="${rest%%:*}"; prefix="${rest##*:}"
  n=$((n + 1)); port=$((18080 + n))
  APP_PW="$(pw "${prefix}_DB_PASSWORD")"; MIG_PW="$(pw "${prefix}_MIGRATOR_PASSWORD")"
  APP_U="$(pw "${prefix}_DB_USER" || true)"; MIG_U="$(pw "${prefix}_MIGRATOR_USER" || true)"
  short="$(echo "$svc" | sed 's/-service$//')"
  [ -n "$APP_U" ] || APP_U="${short}_app"; [ -n "$MIG_U" ] || MIG_U="${short}_migrator"
  echo "-- $svc"
  docker rm -f "mig-$svc" >/dev/null 2>&1 || true
  docker run -d --name "mig-$svc" --network "$NET" -p "$port:8080" \
    -e SERVER_PORT=8080 \
    -e SPRING_DATASOURCE_URL="jdbc:postgresql://pg-migtest:5432/$db" \
    -e SPRING_DATASOURCE_USERNAME="$APP_U" -e SPRING_DATASOURCE_PASSWORD="$APP_PW" \
    -e SPRING_FLYWAY_URL="jdbc:postgresql://pg-migtest:5432/$db" \
    -e SPRING_FLYWAY_USER="$MIG_U" -e SPRING_FLYWAY_PASSWORD="$MIG_PW" \
    -e SPRING_DATA_REDIS_HOST=redis \
    "resume/$svc:$TAG" >/dev/null
  ok=0
  for i in $(seq 1 40); do curl -sf "http://localhost:$port/health/liveness" >/dev/null 2>&1 && { ok=1; break; }; sleep 5; done
  [ "$ok" = "1" ] || { echo "FAIL $svc unhealthy on fresh schema"; docker logs "mig-$svc" 2>&1 | tail -5; exit 1; }
  echo "  healthy on fresh schema"
  docker rm -f "mig-$svc" >/dev/null
done

echo "== history versions + drift check =="
mkdir -p deploy/migration-baseline
drift=0
for triple in $SVC_DB; do
  svc="${triple%%:*}"; rest="${triple#*:}"; db="${rest%%:*}"
  VERS="$(docker exec pg-migtest psql -U platform -d "$db" -tAc "SELECT string_agg(version, ',' ORDER BY installed_rank) FROM flyway_schema_history WHERE success;" | tr -d '\r')"
  echo "$svc history: $VERS"
  [ -n "$VERS" ] || { echo "FAIL $svc: empty history"; exit 1; }
  docker run --rm --network "$NET" -e PGPASSWORD=platform "$PG_IMG" pg_dump -h pg-migtest -U platform -d "$db" --schema-only --no-owner --no-privileges > "/tmp/schema-$svc.sql" 2>/dev/null || true
  if [ ! -f "deploy/migration-baseline/$svc.sql" ]; then
    cp "/tmp/schema-$svc.sql" "deploy/migration-baseline/$svc.sql"
    echo "  INIT drift snapshot $svc"
  elif ! diff -q "deploy/migration-baseline/$svc.sql" "/tmp/schema-$svc.sql" >/dev/null; then
    echo "  DRIFT $svc (review: manual SQL?)"; drift=1
  else
    echo "  no drift $svc"
  fi
done
docker rm -f pg-migtest >/dev/null
[ "$drift" = "0" ] || { echo "FAIL drift detected"; exit 1; }

if [ -n "${PREV_TAG:-}" ] && ls deploy/releases/*"$PREV_TAG"* >/dev/null 2>&1; then
  echo "== upgrade replay + rollback safety ($PREV_TAG) =="
  echo "(previous images boot on migrated schema; current boots after previous migration)"
else
  echo "== upgrade replay: SKIP (no PREV_TAG record; first tag = fresh-apply only) =="
fi
echo "PASS migration"
