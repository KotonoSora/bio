-- Mirrors deploy/init-db.sh for profile_db inside the Testcontainers PG:
-- least-privilege role split (profile_app DML / profile_migrator DDL) plus the
-- extensions V1-V6 assume. Runs as the container superuser before test code.
CREATE ROLE profile_app LOGIN PASSWORD 'profile_app_it';
CREATE ROLE profile_migrator LOGIN PASSWORD 'profile_migrator_it';
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO profile_app;
GRANT USAGE, CREATE ON SCHEMA public TO profile_migrator;
ALTER DEFAULT PRIVILEGES FOR ROLE profile_migrator IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO profile_app;
ALTER DEFAULT PRIVILEGES FOR ROLE profile_migrator IN SCHEMA public
  GRANT USAGE, SELECT ON SEQUENCES TO profile_app;
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS citext;
