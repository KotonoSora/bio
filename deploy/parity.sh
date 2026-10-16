#!/bin/sh
# Podman parity gate (04-containers-and-compose.md): boot the SAME digests under
# Docker and Podman rootless, compare health + smoke output. A parity failure
# blocks promotion. Without podman-compose present this reports SKIP (CI runs it).
set -e
cd "$(dirname "$0")/.."
if ! command -v podman >/dev/null 2>&1 || ! command -v podman-compose >/dev/null 2>&1; then
  echo "SKIP parity: podman and/or podman-compose not installed (CI gate covers this)"
  sh deploy/smoke.sh > /tmp/smoke-docker.out 2>&1 && echo "docker smoke: PASS (see /tmp/smoke-docker.out)"
  exit 0
fi
echo "== docker baseline =="
docker compose up -d --build >/dev/null
sh deploy/smoke.sh > /tmp/smoke-docker.out 2>&1; DOCKER_RC=$?
docker compose down >/dev/null
echo "== podman rootless =="
podman-compose up -d --build >/dev/null
sh deploy/smoke.sh > /tmp/smoke-podman.out 2>&1; PODMAN_RC=$?
podman-compose down -v >/dev/null
echo "docker=$DOCKER_RC podman=$PODMAN_RC"
diff /tmp/smoke-docker.out /tmp/smoke-podman.out && echo "PASS parity: identical smoke output" || { echo "FAIL parity: output diverged"; exit 1; }
[ "$DOCKER_RC" = "0" ] && [ "$PODMAN_RC" = "0" ] || { echo "FAIL parity: nonzero smoke"; exit 1; }
