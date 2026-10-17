-- Mirrors deploy/init-db.sh for account_db: least-privilege role split plus the
-- pgcrypto extension V1__account_core.sql assumes. Runs as the container
-- superuser before any test code, inside the default database.
CREATE ROLE account_app LOGIN PASSWORD 'account_app_it';
CREATE ROLE account_migrator LOGIN PASSWORD 'account_migrator_it';
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO account_app;
GRANT USAGE, CREATE ON SCHEMA public TO account_migrator;
ALTER DEFAULT PRIVILEGES FOR ROLE account_migrator IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO account_app;
ALTER DEFAULT PRIVILEGES FOR ROLE account_migrator IN SCHEMA public
  GRANT USAGE, SELECT ON SEQUENCES TO account_app;
CREATE EXTENSION IF NOT EXISTS pgcrypto;
