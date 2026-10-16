#!/bin/sh
# Data-root bootstrap (04-containers-and-compose.md, 09-local-storage-and-nginx-spec.md):
# pre-create the host bind-mount tree before first boot. Idempotent; safe to
# re-run. Docker/Podman create missing dirs implicitly, but explicit 0700 dirs
# keep upload paths host-level protected (09: at-rest protection is host-level)
# and namespaces follow the registered table exactly.
set -e
cd "$(dirname "$0")/.."
DATA_ROOT="${DATA_ROOT:-./data}"

DIRS="
$DATA_ROOT/postgres
$DATA_ROOT/redis
$DATA_ROOT/logs/nginx
$DATA_ROOT/edge/certs
$DATA_ROOT/uploads/account/purge-certificates
$DATA_ROOT/uploads/profile/avatars
$DATA_ROOT/uploads/profile/evidence
$DATA_ROOT/uploads/profile/exports/leads
$DATA_ROOT/uploads/resume/archives
$DATA_ROOT/uploads/resume/gallery
$DATA_ROOT/uploads/shop/covers
$DATA_ROOT/uploads/shop/artwork
$DATA_ROOT/uploads/shop/digital
$DATA_ROOT/uploads/shop/courses
$DATA_ROOT/uploads/links/qr
$DATA_ROOT/uploads/links/hit-raw
$DATA_ROOT/uploads/analytics/raw
$DATA_ROOT/uploads/publish/media
$DATA_ROOT/uploads/publish/exports
"
for d in $DIRS; do mkdir -p "$d"; done
chmod 0700 "$DATA_ROOT" "$DATA_ROOT/edge" "$DATA_ROOT/logs" 2>/dev/null || true
chmod -R 0700 "$DATA_ROOT/uploads" 2>/dev/null || true
# Service-uid ownership where the runner has the rights (09: 0700 service dirs).
# Docker Desktop and rootless Podman map ownership themselves; failures are fine.
chown -R 10001:10001 "$DATA_ROOT/uploads" 2>/dev/null || true
echo "data root ready: $DATA_ROOT"
