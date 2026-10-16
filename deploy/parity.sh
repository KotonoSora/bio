#!/bin/sh
# Podman parity gate (04-containers-and-compose.md): boot the SAME digests under
# Podman rootless and Docker, compare health + smoke output. A parity failure
# blocks promotion. Without podman/podman-compose present this reports SKIP
# (CI runs it). Rootless caveats: the edge ports 80/443 need privileges, so both
# runtimes run with EDGE_HTTP_PORT=8880/EDGE_HTTPS_PORT=8443 (identical smoke
# conditions), and bind-mount state is wiped between runtimes because rootless
# Podman writes subuid-owned files the host user cannot reuse.
set -e
cd "$(dirname "$0")/.."
if ! command -v podman >/dev/null 2>&1 || ! command -v podman-compose >/dev/null 2>&1; then
  echo "SKIP parity: podman and/or podman-compose not installed (CI gate covers this)"
  sh deploy/smoke.sh > /tmp/smoke-docker.out 2>&1 && echo "docker smoke: PASS (see /tmp/smoke-docker.out)"
  exit 0
fi
export EDGE_HTTP_PORT="${EDGE_HTTP_PORT:-8880}"
export EDGE_HTTPS_PORT="${EDGE_HTTPS_PORT:-8443}"
# Runner memory bound: cap parallel docker image builds (podman-compose builds serially).
export COMPOSE_PARALLEL_LIMIT="${COMPOSE_PARALLEL_LIMIT:-3}"
DATA_ROOT="${DATA_ROOT:-./data}"
podman unshare rm -rf "$DATA_ROOT" 2>/dev/null || true

echo "== podman rootless =="
podman-compose up -d --build >/dev/null
PODMAN_RC=0
sh deploy/smoke.sh > /tmp/smoke-podman.out 2>&1 || PODMAN_RC=$?
podman-compose down -v >/dev/null
podman unshare rm -rf "$DATA_ROOT" 2>/dev/null || true

echo "== docker baseline =="
docker compose up -d --build >/dev/null
DOCKER_RC=0
sh deploy/smoke.sh > /tmp/smoke-docker.out 2>&1 || DOCKER_RC=$?
docker compose down -v >/dev/null

echo "docker=$DOCKER_RC podman=$PODMAN_RC"
diff /tmp/smoke-podman.out /tmp/smoke-docker.out && echo "PASS parity: identical smoke output" || { echo "FAIL parity: output diverged"; exit 1; }
[ "$DOCKER_RC" = "0" ] && [ "$PODMAN_RC" = "0" ] || { echo "FAIL parity: nonzero smoke"; exit 1; }
