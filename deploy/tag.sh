#!/bin/sh
# Release tagging (04-containers-and-compose.md): :<semver>-<gitsha> tags,
# promotion by digest only (`latest` never appears), provenance recorded.
# Usage: sh deploy/tag.sh [semver]  (default 0.1.0)
set -e
cd "$(dirname "$0")/.."
SEMVER="${1:-0.1.0}"
SHA="$(git rev-parse --short HEAD)"
TAG="$SEMVER-$SHA"
SERVICES="gateway account-service profile-service link-service public-site-service analytics-service resume-service shop-service billing-service publish-service web nginx"
mkdir -p deploy/releases

echo "== build all images =="
docker compose build

echo "== plan-claims release archive (feature 03 T-08 / NFR-08 / G7) =="
node scripts/plan-claims-release.mjs --tag "$TAG"

OUT="deploy/releases/local-$TAG.env"
: > "$OUT"
{
  echo "# Local promotion record $TAG (built $(date -u +%FT%TZ), commit $(git rev-parse HEAD))"
  echo "# Wrapper: $(cat deploy/wrapper-checksum.txt)"
  echo "# Catalog: gradle/libs.versions.toml @ $(git hash-object gradle/libs.versions.toml)"
  echo "# Web lockfile: web/package-lock.json @ $(git hash-object web/package-lock.json 2>/dev/null || echo missing)"
  echo "# Plan-claims archive: deploy/releases/plan-claims/$TAG (registry export + rendered snapshot + diff vs previous tag)"
} >> "$OUT"
for svc in $SERVICES; do
  # Compose service name -> image repo (the `nginx` row builds resume/edge).
  IMG="resume/$svc"
  [ "$svc" = "nginx" ] && IMG="resume/edge"
  SRC="$(docker compose images "$svc" --format json 2>/dev/null | python3 -c 'import json,sys; print(json.load(sys.stdin)["ImageID"])' 2>/dev/null || true)"
  if [ -z "$SRC" ]; then
    SRC="$(docker images --format '{{.Repository}}:{{.Tag}} {{.ID}}' | grep "$IMG" | head -1 | awk '{print $2}')"
  fi
  docker tag "$SRC" "$IMG:$TAG"
  DIGEST="$(docker inspect --format '{{index .RepoDigests 0}}' "$IMG:$TAG" 2>/dev/null | sed 's/.*@//' || true)"
  echo "export ${svc}_DIGEST=${DIGEST:-unknown}" | tr -c 'A-Za-z0-9_=.\n' '_' >> "$OUT"
  echo "tagged $IMG:$TAG digest=${DIGEST:-unknown}"
done
echo "record: $OUT"
