#!/bin/sh
# Dev-cluster bootstrap: one logical database per service (prod: separate instances).
# Role split (03-data-layer-conventions.md): migrator (DDL, migrations only) vs app
# (DML under RLS, no DDL, no superuser). App role gets CONNECT + USAGE + DML only;
# migrator owns schema DDL. RLS policies themselves ship in each service's V4 migration.
# Statement timeout 5 s enforced server-side per database. Idempotent re-runs.
set -e

setup_db() {
  DB="$1"; APP="$2"; APP_PW="$3"; MIG="$4"; MIG_PW="$5"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -c "SELECT 'x' WHERE EXISTS (SELECT FROM pg_database WHERE datname = '$DB')" | grep -q x \
    || psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -c "CREATE DATABASE $DB;"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$DB" <<EOSQL
SELECT 'SELECT version()' AS boot_assert;
SHOW server_version;
DO \$\$ BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '$APP') THEN CREATE ROLE $APP LOGIN PASSWORD '$APP_PW'; ELSE ALTER ROLE $APP WITH LOGIN PASSWORD '$APP_PW'; END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '$MIG') THEN CREATE ROLE $MIG LOGIN PASSWORD '$MIG_PW'; ELSE ALTER ROLE $MIG WITH LOGIN PASSWORD '$MIG_PW'; END IF;
END \$\$;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT CONNECT ON DATABASE $DB TO $APP, $MIG;
GRANT USAGE ON SCHEMA public TO $APP;
GRANT USAGE, CREATE ON SCHEMA public TO $MIG;
ALTER DEFAULT PRIVILEGES FOR ROLE $MIG IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO $APP;
ALTER DEFAULT PRIVILEGES FOR ROLE $MIG IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO $APP;
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS citext;
ALTER DATABASE $DB SET statement_timeout = '5s';
EOSQL
}

setup_db account_db   account_app   "${ACCOUNT_DB_PASSWORD:-account_app_dev_only}"       account_migrator   "${ACCOUNT_MIGRATOR_PASSWORD:-account_migrator_dev_only}"
setup_db profile_db   profile_app   "${PROFILE_DB_PASSWORD:-profile_app_dev_only}"       profile_migrator   "${PROFILE_MIGRATOR_PASSWORD:-profile_migrator_dev_only}"
setup_db links_db     link_app      "${LINK_DB_PASSWORD:-link_app_dev_only}"              link_migrator      "${LINK_MIGRATOR_PASSWORD:-link_migrator_dev_only}"
setup_db analytics_db analytics_app "${ANALYTICS_DB_PASSWORD:-analytics_app_dev_only}"    analytics_migrator "${ANALYTICS_MIGRATOR_PASSWORD:-analytics_migrator_dev_only}"
setup_db resume_db    resume_app    "${RESUME_DB_PASSWORD:-resume_app_dev_only}"           resume_migrator    "${RESUME_MIGRATOR_PASSWORD:-resume_migrator_dev_only}"
setup_db shop_db      shop_app      "${SHOP_DB_PASSWORD:-shop_app_dev_only}"               shop_migrator      "${SHOP_MIGRATOR_PASSWORD:-shop_migrator_dev_only}"
setup_db billing_db   billing_app   "${BILLING_DB_PASSWORD:-billing_app_dev_only}"        billing_migrator   "${BILLING_MIGRATOR_PASSWORD:-billing_migrator_dev_only}"
setup_db publish_db   publish_app   "${PUBLISH_DB_PASSWORD:-publish_app_dev_only}"         publish_migrator   "${PUBLISH_MIGRATOR_PASSWORD:-publish_migrator_dev_only}"
