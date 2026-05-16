#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# build-and-push.sh — Build and push all 4 service images to Docker Hub.
#
# Required env vars:
#   DOCKER_USER    Docker Hub username or organization name (lowercase)
#   DOCKER_PASS    Docker Hub Password or Personal Access Token (PAT)
#   IMAGE_TAG      Tag for the images (e.g. v1, latest)
#
# Optional:
#   SERVICES       Space-separated list of services to build (defaults to all 4)
#
# Usage:
#   DOCKER_USER=alice DOCKER_PASS=dckr_pat_xxx IMAGE_TAG=v1 ./scripts/build-and-push.sh
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

DOCKER_USER="${DOCKER_USER:?DOCKER_USER must be set (your Docker Hub username or org)}"
DOCKER_PASS="${DOCKER_PASS:?DOCKER_PASS must be set (Password or Token)}"
IMAGE_TAG="${IMAGE_TAG:-latest}"
SERVICES="${SERVICES:-hotel-service flight-service travel-service cancellation-function}"
DOCKER_REGISTRY="docker.io"

REGISTRY="${DOCKER_REGISTRY}/${DOCKER_USER}"

echo "→ Logging in to Docker Hub as ${DOCKER_USER}"
echo "${DOCKER_PASS}" | docker login "${DOCKER_REGISTRY}" \
    --username "${DOCKER_USER}" --password-stdin

echo "→ Building and pushing tag=${IMAGE_TAG} to ${REGISTRY}"

for svc in ${SERVICES}; do
    echo ""
    echo "═════ ${svc} ═════"

    if [[ ! -d "./${svc}" ]]; then
        echo "  ✗ ./${svc} not found — skipping"
        continue
    fi

    docker build -t "${REGISTRY}/${svc}:${IMAGE_TAG}" "./${svc}"
    docker tag  "${REGISTRY}/${svc}:${IMAGE_TAG}" "${REGISTRY}/${svc}:latest"

    docker push "${REGISTRY}/${svc}:${IMAGE_TAG}"
    docker push "${REGISTRY}/${svc}:latest"

    echo "  ✓ ${REGISTRY}/${svc}:${IMAGE_TAG} pushed"
done

echo ""
echo "✅ All images pushed to ${REGISTRY}"
echo "   Visit: https://hub.docker.com/u/${DOCKER_USER}"

docker logout "${DOCKER_REGISTRY}" 2>/dev/null || true
