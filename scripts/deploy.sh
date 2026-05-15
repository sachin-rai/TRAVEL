#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# deploy.sh — Deploy travel-microservices to AWS EKS or GCP GKE.
#
# Images are PULLED from PUBLIC GitHub Container Registry (ghcr.io).
# Because the packages are public, NO PAT is needed here — K8s nodes can
# pull anonymously. CLOUD_PROVIDER only chooses WHERE to deploy.
#
# Required env vars:
#   CLOUD_PROVIDER     AWS or GCP — which K8s cluster to target
#   GHCR_OWNER         GitHub username or org that owns the public packages
#   IMAGE_TAG          Tag to deploy (e.g. v1, latest)
#
# Cloud-specific env vars:
#   AWS:  AWS_REGION, AWS_EKS_CLUSTER
#   GCP:  GCP_PROJECT_ID, GCP_REGION, GCP_GKE_CLUSTER
#
# Optional:
#   USE_KNATIVE        true|false — deploy cancellation function on Knative (default: true)
#
# Examples:
#   CLOUD_PROVIDER=AWS GHCR_OWNER=alice IMAGE_TAG=v1 \
#       AWS_REGION=us-east-1 AWS_EKS_CLUSTER=travel-app-eks ./scripts/deploy.sh
#
#   CLOUD_PROVIDER=GCP GHCR_OWNER=alice IMAGE_TAG=v1 \
#       GCP_PROJECT_ID=my-prj GCP_REGION=us-central1 GCP_GKE_CLUSTER=travel-app-gke \
#       ./scripts/deploy.sh
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

CLOUD_PROVIDER="${CLOUD_PROVIDER:?CLOUD_PROVIDER must be AWS or GCP}"
GHCR_OWNER="${GHCR_OWNER:?GHCR_OWNER must be set (GitHub username or org)}"
IMAGE_TAG="${IMAGE_TAG:-latest}"
USE_KNATIVE="${USE_KNATIVE:-true}"

# ── Images always come from public GHCR ─────────────────────────────────────
export IMAGE_REGISTRY="ghcr.io/${GHCR_OWNER}"
export IMAGE_TAG

# ── Cloud-specific: configure kubectl to point at the right cluster ─────────
case "$CLOUD_PROVIDER" in
    AWS)
        : "${AWS_REGION:?AWS_REGION required}"
        : "${AWS_EKS_CLUSTER:?AWS_EKS_CLUSTER required}"
        K8S_OVERLAY="k8s/aws"
        echo "→ Targeting AWS EKS cluster: ${AWS_EKS_CLUSTER} (region ${AWS_REGION})"
        aws eks update-kubeconfig --region "${AWS_REGION}" --name "${AWS_EKS_CLUSTER}"
        ;;
    GCP)
        : "${GCP_PROJECT_ID:?GCP_PROJECT_ID required}"
        : "${GCP_REGION:?GCP_REGION required}"
        : "${GCP_GKE_CLUSTER:?GCP_GKE_CLUSTER required}"
        K8S_OVERLAY="k8s/gcp"
        echo "→ Targeting GCP GKE cluster: ${GCP_GKE_CLUSTER} (region ${GCP_REGION})"
        gcloud container clusters get-credentials "${GCP_GKE_CLUSTER}" \
            --region "${GCP_REGION}" \
            --project "${GCP_PROJECT_ID}"
        ;;
    *)
        echo "ERROR: CLOUD_PROVIDER must be AWS or GCP (got: ${CLOUD_PROVIDER})" >&2
        exit 1
        ;;
esac

echo "→ Image registry: ${IMAGE_REGISTRY} (public — no pull auth needed)"
echo "→ Image tag:      ${IMAGE_TAG}"
echo "→ K8s overlay:    ${K8S_OVERLAY}"

# ── 1. Namespace ────────────────────────────────────────────────────────────
echo ""
echo "→ Ensuring namespace exists..."
kubectl create namespace travel-app --dry-run=client -o yaml | kubectl apply -f -

# ── 2. Common manifests (configmap, deployments, services, HPA) ─────────────
echo ""
echo "→ Applying common manifests..."
for f in k8s/common/*.yaml; do
    envsubst < "$f" | kubectl apply -f -
done

# ── 3. Cloud-specific overlay (ingress + storage class) ─────────────────────
echo ""
echo "→ Applying ${K8S_OVERLAY} overlay..."
for f in "${K8S_OVERLAY}"/*.yaml; do
    [[ "$f" == *README.md ]] && continue
    [[ "$f" == *.md ]] && continue
    envsubst < "$f" | kubectl apply -f -
done

# ── 4. Cancellation function ────────────────────────────────────────────────
echo ""
if [[ "${USE_KNATIVE}" == "true" ]]; then
    echo "→ Deploying cancellation-function as Knative Service..."
    envsubst < k8s/functions/cancellation-function-knative.yaml | kubectl apply -f -
else
    echo "→ Deploying cancellation-function as Deployment..."
    envsubst < k8s/functions/cancellation-function-deployment.yaml | kubectl apply -f -
fi

# ── 5. Wait for rollouts ────────────────────────────────────────────────────
echo ""
echo "→ Waiting for rollouts..."
kubectl -n travel-app rollout status deployment/hotel-service  --timeout=5m
kubectl -n travel-app rollout status deployment/flight-service --timeout=5m
kubectl -n travel-app rollout status deployment/travel-service --timeout=5m

# ── 6. Summary ──────────────────────────────────────────────────────────────
echo ""
echo "✅ Deployed ${IMAGE_TAG} from public GHCR (ghcr.io/${GHCR_OWNER}) to ${CLOUD_PROVIDER}"
echo ""
kubectl -n travel-app get pods
echo ""
kubectl -n travel-app get svc
echo ""
kubectl -n travel-app get ingress || true
