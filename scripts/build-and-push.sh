#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# build-and-push.sh — Build and push all 4 service images to GitHub Container
# Registry (ghcr.io).
#
# Required env vars:
#   GHCR_OWNER     GitHub username or organization name (lowercase)
#   GHCR_PAT       GitHub Personal Access Token with `write:packages` scope
#   IMAGE_TAG      Tag for the images (e.g. v1, latest)
#
# Optional:
#   SERVICES       Space-separated list of services to build (defaults to all 4)
#
# Usage:
#   GHCR_OWNER=alice GHCR_PAT=ghp_xxx IMAGE_TAG=v1 ./scripts/build-and-push.sh
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

GHCR_OWNER="${GHCR_OWNER:?GHCR_OWNER must be set (your GitHub username or org)}"
GHCR_PAT="${GHCR_PAT:?GHCR_PAT must be set (Personal Access Token with write:packages)}"
IMAGE_TAG="${IMAGE_TAG:-latest}"
SERVICES="${SERVICES:-hotel-service flight-service travel-service cancellation-function}"
GHCR_REGISTRY="ghcr.io"

REGISTRY="${GHCR_REGISTRY}/${GHCR_OWNER}"

echo "→ Logging in to GitHub Container Registry as ${GHCR_OWNER}"
echo "${GHCR_PAT}" | docker login "${GHCR_REGISTRY}" \
    --username "${GHCR_OWNER}" --password-stdin

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
echo "   Visit: https://github.com/${GHCR_OWNER}?tab=packages"

docker logout "${GHCR_REGISTRY}" 2>/dev/null || true
