# Deploying Travel Microservices to GCP (GKE) — Step-by-Step Guide

This guide takes you from a fresh GCP account to a fully running Travel Microservices platform on Google Kubernetes Engine, using **pure Kubernetes** (no Google-proprietary services beyond GKE itself, Artifact Registry, and the GCLB ingress). Every command is shown — copy-paste-ready.

By the end you'll have:

- A GKE Standard cluster running 2–3 worker nodes
- Four Docker images in Google Artifact Registry
- Hotel, Flight, Travel services running as Kubernetes Deployments
- Cancellation function running as a Knative Service (scale-to-zero serverless)
- A public Google Cloud HTTP(S) Load Balancer routing traffic to the services

---

## Table of Contents

1. [Architecture on GCP](#1-architecture-on-gcp)
2. [Prerequisites](#2-prerequisites)
3. [GCP Project Setup](#3-gcp-project-setup)
4. [Install Required CLI Tools](#4-install-required-cli-tools)
5. [Enable Required GCP APIs](#5-enable-required-gcp-apis)
6. [Create the GKE Cluster](#6-create-the-gke-cluster)
7. [Install Knative Serving](#7-install-knative-serving-for-the-serverless-function)
8. [Create Artifact Registry Repository](#8-create-artifact-registry-repository)
9. [Reserve a Static IP for the Ingress](#9-reserve-a-static-ip-for-the-ingress)
10. [Build and Push Docker Images](#10-build-and-push-docker-images)
11. [Deploy to Kubernetes](#11-deploy-to-kubernetes)
12. [Verify the Deployment](#12-verify-the-deployment)
13. [Test End-to-End](#13-test-the-booking-flow-end-to-end)
14. [Updating an Existing Deployment](#14-updating-an-existing-deployment)
15. [Cost Estimate](#15-cost-estimate)
16. [Tear Down](#16-tear-down-everything)
17. [Troubleshooting](#17-troubleshooting)

---

## 1. Architecture on GCP

```
                       Internet
                          │
                          ▼
            ┌────────────────────────────┐
            │   Google Cloud HTTP(S)     │  ← provisioned by GKE Ingress
            │   Load Balancer (GCLB)     │     controller from the
            │   travel-app-ip (static)   │     K8s Ingress resource
            └────────────┬───────────────┘
                         │
                         ▼
           ┌──────────────────────────────────┐
           │   GKE Cluster (travel-app-gke)   │
           │   ┌──────────────────────────┐   │
           │   │ Namespace: travel-app    │   │
           │   │                          │   │
           │   │  travel-service (x2)     │   │
           │   │       │                  │   │
           │   │       ├──► hotel-service │   │
           │   │       │     (x2)         │   │
           │   │       ├──► flight-service│   │
           │   │       │     (x2)         │   │
           │   │       │                  │   │
           │   │       └──► cancellation  │   │
           │   │            function      │   │
           │   │            (Knative,     │   │
           │   │             scale to 0)  │   │
           │   └──────────────────────────┘   │
           │                                  │
           │   GCE Worker Nodes (2x e2-medium)│
           └──────────────────────────────────┘
                          │
                          ▼
           ┌──────────────────────────────────┐
           │  Artifact Registry (region:      │
           │  us-central1, repo: travel)      │
           │  - travel-service:v1             │
           │  - hotel-service:v1              │
           │  - flight-service:v1             │
           │  - cancellation-function:v1      │
           └──────────────────────────────────┘
```

All GCP-specific pieces (GCLB ingress, pd-ssd storage class) live in `k8s/gcp/` overlays. Everything else (`k8s/common/`) is identical to AWS.

---

## 2. Prerequisites

- A **GCP account** with billing enabled (this stack costs ~$5-8/day if left running — see [Section 15](#15-cost-estimate))
- New GCP accounts get **$300 in free credits** for 90 days — plenty for this demo
- A machine to run CLI commands from (Mac, Linux, or Windows with WSL2)
- Docker installed and running
- The `travel-microservices.zip` from earlier, unzipped to a working directory

---

## 3. GCP Project Setup

### 3.1 Create a project

1. Go to https://console.cloud.google.com/
2. Click the project dropdown at the top → **New Project**
3. Project name: `travel-app` (or any name you like)
4. Note the **Project ID** — this is what GCP uses internally (it may differ slightly from the project name, e.g. `travel-app-450312`).
5. Click **Create**

### 3.2 Enable billing

1. **Billing** in the left nav → **Link a billing account** (or **Create billing account**)
2. Add a credit card. New accounts get $300 in free credits.

### 3.3 Set the active project

Once you've installed gcloud CLI (next section), you'll set the active project. For now, just remember the Project ID.

---

## 4. Install Required CLI Tools

### 4.1 gcloud CLI

The gcloud CLI is Google's official tool — it handles auth, gcloud, gsutil, and a bundled `kubectl`.

**macOS:**
```bash
brew install --cask google-cloud-sdk
```

**Linux:**
```bash
curl https://sdk.cloud.google.com | bash
exec -l $SHELL
```

**Windows:** download the installer from https://cloud.google.com/sdk/docs/install

Verify:
```bash
gcloud --version
# Google Cloud SDK XXX.X.X ...
```

### 4.2 kubectl

The gcloud CLI installer includes `kubectl`, but you may want it standalone:

```bash
# macOS
brew install kubectl

# Linux
gcloud components install kubectl
```

Verify:
```bash
kubectl version --client
```

### 4.3 GKE auth plugin (required since gcloud 1.26+)

```bash
gcloud components install gke-gcloud-auth-plugin
```

Or if installed via Homebrew:
```bash
brew install --cask google-cloud-sdk
```
and then ensure `gke-gcloud-auth-plugin` is on your PATH.

### 4.4 Authenticate gcloud

```bash
gcloud auth login
# Opens browser → log in with your GCP account → grant permission

gcloud auth application-default login
# Same flow — sets up Application Default Credentials used by SDKs
```

### 4.5 Set the active project

```bash
export GCP_PROJECT_ID=travel-app-XXXXXX   # your actual project ID
export GCP_REGION=us-central1

gcloud config set project $GCP_PROJECT_ID
gcloud config set compute/region $GCP_REGION
```

Verify:
```bash
gcloud config list
# [core]
# account = your@email.com
# project = travel-app-XXXXXX
# [compute]
# region  = us-central1
```

---

## 5. Enable Required GCP APIs

```bash
gcloud services enable \
    container.googleapis.com \
    artifactregistry.googleapis.com \
    compute.googleapis.com \
    cloudresourcemanager.googleapis.com
```

This takes ~1 minute. Confirm:

```bash
gcloud services list --enabled --filter="name:(container.googleapis.com OR artifactregistry.googleapis.com)"
```

---

## 6. Create the GKE Cluster

This takes **5-10 minutes** (faster than EKS).

### 6.1 Choose a cluster mode

GKE has two flavors:
- **Standard** — you manage nodes, you pay per node. Cheaper, more control. **We use this.**
- **Autopilot** — fully managed, pay per pod. More expensive but zero ops.

### 6.2 Create the cluster

```bash
gcloud container clusters create travel-app-gke \
    --region=$GCP_REGION \
    --num-nodes=1 \
    --machine-type=e2-medium \
    --release-channel=regular \
    --enable-autoscaling \
    --min-nodes=1 \
    --max-nodes=3 \
    --enable-autorepair \
    --enable-autoupgrade
```

> **What "regional cluster" means**: `--region` (instead of `--zone`) creates a cluster across 3 zones for HA. `--num-nodes=1` means 1 node *per zone*, so you'll see 3 nodes total. Cheaper option: use `--zone=us-central1-a --num-nodes=2` for a single-zone setup with 2 nodes.

When done:

```bash
kubectl get nodes
# NAME                                       STATUS   ROLES    AGE   VERSION
# gke-travel-app-gke-default-pool-...-xxxx   Ready    <none>   3m    v1.29.x
# gke-travel-app-gke-default-pool-...-xxxx   Ready    <none>   3m    v1.29.x
# gke-travel-app-gke-default-pool-...-xxxx   Ready    <none>   3m    v1.29.x
```

`gcloud container clusters create` automatically updates your `kubectl` context — no separate step needed.

### 6.3 (Optional) Manually refresh kubectl credentials

If you switch between clusters, refresh:

```bash
gcloud container clusters get-credentials travel-app-gke --region=$GCP_REGION
```

---

## 7. Install Knative Serving (for the serverless function)

GCP gives you a choice for serverless:

| Option | What it is | When to use |
|---|---|---|
| **Knative on GKE** | The cloud-agnostic K8s serverless layer | **This guide — for portability with AWS** |
| **Cloud Run** | Fully managed Knative (no K8s cluster needed) | If you want to skip GKE entirely for the function |
| **Cloud Functions Gen 2** | Google's FaaS service | If you want the most GCP-native option |

**This guide uses Knative on GKE** because the manifest at `k8s/functions/cancellation-function-knative.yaml` is *identical* between AWS and GCP — that's the cloud-agnostic promise.

### 7.1 Install Knative Serving

```bash
kubectl apply -f https://github.com/knative/serving/releases/download/knative-v1.14.0/serving-crds.yaml

kubectl apply -f https://github.com/knative/serving/releases/download/knative-v1.14.0/serving-core.yaml
```

### 7.2 Install Kourier as the Knative ingress

```bash
kubectl apply -f https://github.com/knative/net-kourier/releases/download/knative-v1.14.0/kourier.yaml

kubectl patch configmap/config-network \
    --namespace knative-serving \
    --type merge \
    --patch '{"data":{"ingress-class":"kourier.ingress.networking.knative.dev"}}'
```

### 7.3 Verify Knative is healthy

```bash
kubectl get pods -n knative-serving
# All pods should be Running

kubectl get pods -n kourier-system
# 3scale-kourier-gateway should be Running
```

---

## 8. Docker Hub Setup

This project uses **Docker Hub** for a cloud-agnostic registry setup.

1. Follow [docs/DOCKER_HUB_SETUP.md](DOCKER_HUB_SETUP.md) to create your Docker Hub account and Personal Access Token.
2. Add the `docker-user` and `docker-pass` credentials to Jenkins.
3. Your images will be pushed to `docker.io/<your-username>/<service>`.

---

## 9. Reserve a Static IP for the Ingress

The GCP-specific ingress manifest (`k8s/gcp/ingress-gke.yaml`) references a static IP named `travel-app-ip`. Reserve it now so the ingress can find it.

```bash
gcloud compute addresses create travel-app-ip --global
```

Verify:
```bash
gcloud compute addresses describe travel-app-ip --global
# Note the 'address:' field — this is the public IP your app will be on
```

> **Why reserve in advance?** GCP global IPs are an ephemeral-vs-reserved concept. Reserving means GCP guarantees the same IP across redeploys; the Ingress manifest references it by name.

---

## 10. Build and Push Docker Images

### 10.1 Build and Push

From the project root:

```bash
export CLOUD_PROVIDER=GCP
export DOCKER_USER=your-username
export DOCKER_PASS=your-password-or-token
export IMAGE_TAG=v1

./scripts/build-and-push.sh
```

### 10.2 Verify images are in Docker Hub
Visit `https://hub.docker.com/u/<your-username>` to see your newly pushed repositories. Ensure they are set to **Public**.

You should see all four service names, each with `v1` and `latest` tags.

---

## 11. Deploy to Kubernetes

The `deploy.sh` script handles variable substitution and applies everything in the right order.

```bash
export CLOUD_PROVIDER=GCP
export GCP_PROJECT_ID=$GCP_PROJECT_ID
export GCP_REGION=$GCP_REGION
export GCP_GKE_CLUSTER=travel-app-gke
export IMAGE_TAG=v1
export USE_KNATIVE=true

./scripts/deploy.sh
```

What this does, in order:
1. Reconfirms `kubectl` is pointing at your GKE cluster
2. Applies `k8s/common/*.yaml` — namespace, configmap, services for hotel/flight/travel, HPAs
3. Applies `k8s/gcp/*.yaml` — GCLB ingress, BackendConfig, pd-ssd storage class
4. Applies `k8s/functions/cancellation-function-knative.yaml` — the Knative Service
5. Waits for each Deployment to roll out successfully

You'll see output ending in:
```
✅ Deployment to GCP complete.
NAME                              READY   STATUS    RESTARTS   AGE
hotel-service-xxxxx-xxxxx         1/1     Running   0          1m
hotel-service-xxxxx-xxxxx         1/1     Running   0          1m
flight-service-xxxxx-xxxxx        1/1     Running   0          1m
flight-service-xxxxx-xxxxx        1/1     Running   0          1m
travel-service-xxxxx-xxxxx        1/1     Running   0          1m
travel-service-xxxxx-xxxxx        1/1     Running   0          1m
```

The `cancellation-function` won't show up in `get pods` yet — Knative scales it to zero until it receives traffic.

---

## 12. Verify the Deployment

### 12.1 Check pod status

```bash
kubectl -n travel-app get pods -w
# Ctrl+C when all are Running
```

### 12.2 Check ingress

The GCLB takes **3-5 minutes** to provision after the Ingress is applied (longer than AWS ALB). The static IP you reserved earlier will become the ADDRESS:

```bash
kubectl -n travel-app get ingress travel-app-ingress -w
# NAME                 ADDRESS         PORTS
# travel-app-ingress   34.xxx.xxx.xxx  80
```

### 12.3 Get the public IP

```bash
export LB_IP=$(kubectl -n travel-app get ingress travel-app-ingress \
    -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
echo "App URL: http://$LB_IP"
```

### 12.4 Wait for backends to become HEALTHY

GCLB health checks take **several minutes** to start passing after the LB is provisioned. While they're still being checked, you'll get 502 errors from the LB. Watch:

```bash
# In GCP Console: Network Services → Load Balancing → click your LB → Backends
# Wait for all backends to show "Healthy"

# Or check from CLI:
gcloud compute backend-services list
```

If after 10 minutes the backends are still unhealthy, see [Troubleshooting](#17-troubleshooting).

### 12.5 Health check from outside the cluster

```bash
curl http://$LB_IP/api/v1/trips/DOES-NOT-EXIST
# Expect: {"error":"Trip not found: DOES-NOT-EXIST"}
```

That 400 response is from your code — it means the GCLB → ingress → travel-service path works.

### 12.6 Verify the Knative function

```bash
kubectl get ksvc -n travel-app
# NAME                    URL                                                          READY
# cancellation-function   http://cancellation-function.travel-app.svc.cluster.local   True
```

The function is invoked internally by the travel-service, so it doesn't need external exposure.

---

## 13. Test the Booking Flow End-to-End

### 13.1 Scenario 1 — Happy path

```bash
START_DATE=$(date -d '+30 days' '+%Y-%m-%d' 2>/dev/null || date -v+30d '+%Y-%m-%d')
END_DATE=$(date -d '+35 days' '+%Y-%m-%d' 2>/dev/null || date -v+35d '+%Y-%m-%d')

RESP=$(curl -s -X POST http://$LB_IP/api/v1/trips \
    -H "Content-Type: application/json" \
    -d "{
        \"customerName\":\"Alice\",
        \"customerEmail\":\"alice@example.com\",
        \"originCity\":\"New York\",
        \"destinationCity\":\"Paris\",
        \"startDate\":\"$START_DATE\",
        \"endDate\":\"$END_DATE\",
        \"numberOfTravelers\":2,
        \"numberOfRooms\":1
    }")
echo $RESP | python3 -m json.tool

TRIP_REF=$(echo $RESP | grep -o '"tripReference":"[^"]*"' | cut -d'"' -f4)
echo "Trip booked: $TRIP_REF"
```

Expected: `status: "CONFIRMED"` with both `hotelBookingReference` and `flightBookingReference` populated.

### 13.2 Watch the logs in real-time

In a separate terminal:

```bash
kubectl -n travel-app logs -l app=travel-service -f --tail=20
```

### 13.3 Scenario 2 — Cancel the trip (triggers the serverless function)

```bash
curl -X DELETE http://$LB_IP/api/v1/trips/$TRIP_REF
```

Now watch the cancellation function get spun up:

```bash
kubectl get pods -n travel-app -l serving.knative.dev/service=cancellation-function -w
# A pod appears, runs for a few seconds, then disappears as Knative scales back to zero
```

Inspect the function's logs:

```bash
kubectl logs -n travel-app -l serving.knative.dev/service=cancellation-function --tail=20
```

You should see entries like `Calling DELETE http://hotel-service...`, `Hotel booking ... cancelled successfully`, etc.

### 13.4 Scenario 3 — Hotel unavailable

```bash
curl -X POST http://$LB_IP/api/v1/trips \
    -H "Content-Type: application/json" \
    -d "{
        \"customerName\":\"Bob\",
        \"customerEmail\":\"bob@example.com\",
        \"originCity\":\"New York\",
        \"destinationCity\":\"Atlantis\",
        \"startDate\":\"$START_DATE\",
        \"endDate\":\"$END_DATE\",
        \"numberOfTravelers\":1,
        \"numberOfRooms\":1
    }"
```

Expected: `status: "FAILED"` and the message confirming the flight was **not** attempted.

---

## 14. Updating an Existing Deployment

When you make code changes and want to redeploy:

```bash
export IMAGE_TAG=v2

# 1. Rebuild and push
./scripts/build-and-push.sh

# 2. Redeploy — `kubectl apply` performs a rolling update
./scripts/deploy.sh

# 3. Watch the rollout
kubectl -n travel-app rollout status deployment/travel-service
```

Rolling update means zero downtime — old pods stay up until new ones are ready.

---

## 15. Cost Estimate

Approximate hourly costs in us-central1 (as of early 2026 — check current pricing):

| Resource | Quantity | Hourly | Daily |
|---|---|---|---|
| GKE cluster management fee | 1 (regional) | $0.10 | $2.40 |
| GCE e2-medium nodes | 3 (regional) | $0.0335 × 3 ≈ $0.10 | $2.40 |
| Cloud Load Balancing (forwarding rule + traffic) | 1 | $0.025 + ingress | ~$0.60 |
| Persistent disk (boot) | ~100 GB | $0.04/GB-month → $0.13/day | $0.13 |
| Artifact Registry storage | <1 GB | negligible | <$0.01 |
| Network egress | varies | varies | $0.50 - $5 |
| **Total** | | | **~$6 - $11 / day** |

> **Free credits**: New GCP accounts get **$300 over 90 days**, so you can run this stack for nearly the entire 90 days without paying anything.

> **Tip**: Spin down nightly when not in use. See [Section 16](#16-tear-down-everything).

---

## 16. Tear Down Everything

When you're done testing, **delete everything** — idle clusters add up quickly even with free credits.

### 16.1 Delete the K8s application

```bash
cd travel-microservices/
./scripts/cleanup.sh
# Or manually: kubectl delete namespace travel-app
```

### 16.2 Delete Knative

```bash
kubectl delete -f https://github.com/knative/net-kourier/releases/download/knative-v1.14.0/kourier.yaml
kubectl delete -f https://github.com/knative/serving/releases/download/knative-v1.14.0/serving-core.yaml
kubectl delete -f https://github.com/knative/serving/releases/download/knative-v1.14.0/serving-crds.yaml
```

### 16.3 Delete the GKE cluster

```bash
gcloud container clusters delete travel-app-gke --region=$GCP_REGION --quiet
```

This takes ~5-10 minutes. It deletes the worker nodes, control plane, and associated firewall rules.

### 16.4 Delete the static IP

```bash
gcloud compute addresses delete travel-app-ip --global --quiet
```

(Idle static IPs are charged at ~$0.20/day, so don't skip this.)

### 16.5 Delete the Artifact Registry repository

```bash
gcloud artifacts repositories delete travel \
    --location=$GCP_REGION --quiet
```

### 16.6 Sanity check

```bash
# Should not list any travel-app-gke cluster
gcloud container clusters list

# Should not list any 'travel' repos
gcloud artifacts repositories list --location=$GCP_REGION

# Should not list travel-app-ip
gcloud compute addresses list
```

### 16.7 (Optional) Delete the entire project

If this was a throwaway project:

```bash
gcloud projects delete $GCP_PROJECT_ID
```

---

## 17. Troubleshooting

| Problem | Likely Cause | Fix |
|---|---|---|
| `gcloud container clusters create` fails with permission denied | Account lacks Kubernetes Engine Admin role | In GCP Console → IAM → grant your account `Kubernetes Engine Admin` |
| `kubectl get nodes` fails with "no Auth Provider found" | Missing `gke-gcloud-auth-plugin` | `gcloud components install gke-gcloud-auth-plugin` |
| Pods stuck in `ImagePullBackOff` | GKE nodes can't pull from Docker Hub | Confirm repository is set to **Public** on Docker Hub. |
| Pods stuck in `Pending` with "0/3 nodes are available: insufficient cpu" | Worker nodes too small | Either scale up: `gcloud container clusters resize travel-app-gke --num-nodes=2 --region=$GCP_REGION`, or use larger node type at creation |
| Ingress ADDRESS stays empty for >10 minutes | Static IP not reserved, or ingress annotation wrong | `gcloud compute addresses list` — verify `travel-app-ip` exists. Check the ingress annotation `kubernetes.io/ingress.global-static-ip-name: "travel-app-ip"` |
| Ingress has ADDRESS but curl returns 502 | GCLB backends still being health-checked | Wait 5-10 minutes after ingress creation. Check backend health in Console → Network Services → Load Balancing |
| Backends permanently UNHEALTHY in GCP Console | Health check path wrong, or actuator endpoint not exposed | The BackendConfig in `k8s/gcp/ingress-gke.yaml` checks `/actuator/health/liveness`. Confirm the service is responding: `kubectl exec -it deployment/travel-service -n travel-app -- wget -qO- http://localhost:8080/actuator/health/liveness` |
| Knative service stays "Unknown" / not ready | Serving CRDs missing or pods unhealthy | `kubectl get pods -n knative-serving` — all must be Running |
| Booking returns 503 from the LB | Travel service pod not ready, or hotel-service unreachable | `kubectl logs -n travel-app -l app=travel-service` — usually one of the Feign URLs is wrong |
| `kubectl get ksvc` says "no resources found" | Knative CRDs not installed | Re-run [Section 7](#7-install-knative-serving-for-the-serverless-function) |
| Want to see what happens if you delete a pod | Pod self-heals because the Deployment has `replicas: 2` | `kubectl delete pod <pod-name> -n travel-app` — a new one is created in seconds |
| Booking fails the second time with "no rooms available" | H2 in-memory DB persists for pod lifetime; multiple bookings on same dates exhaust inventory | Either book different cities/dates, or restart the pod: `kubectl rollout restart deployment/hotel-service -n travel-app` |
| `Forbidden: required compute.googleapis.com is not enabled` | API not enabled | Re-run `gcloud services enable compute.googleapis.com` |
| Free credit alert email arriving daily | Cluster left running | Delete via [Section 16](#16-tear-down-everything) when not actively testing |

### Useful debugging commands

```bash
# Pod-level inspection
kubectl describe pod <pod-name> -n travel-app

# Get logs from all pods of a service
kubectl logs -n travel-app -l app=travel-service --tail=100 --all-containers

# Shell into a pod
kubectl exec -it -n travel-app deployment/travel-service -- /bin/sh

# Inspect the GCLB backend health from CLI
gcloud compute backend-services get-health <backend-service-name> --global

# Watch events live (the K8s equivalent of dmesg)
kubectl get events -n travel-app --sort-by='.lastTimestamp' -w

# Open GKE dashboard in browser
gcloud container clusters describe travel-app-gke --region=$GCP_REGION
```

---

## What You Have Now

After this guide:
- ✅ A GKE cluster ready to run any Kubernetes workload
- ✅ A working CI-style image pipeline (the `build-and-push.sh` script is what Jenkins runs)
- ✅ Knative Serving installed — you can deploy other scale-to-zero services with `kubectl apply -f your-ksvc.yaml`
- ✅ Real-world load balancing through GCLB

The same Docker images and the same `k8s/common/` manifests will deploy unchanged to AWS EKS — only the overlay (`k8s/gcp/` → `k8s/aws/`) and the registry/cluster names differ. That's the cloud-agnostic story in practice.

## Quick Reference Card — GCP vs AWS

| Concept | AWS | GCP |
|---|---|---|
| Managed K8s | EKS | GKE |
| Container Registry | ECR | Artifact Registry |
| Load Balancer | ALB (via AWS Load Balancer Controller) | GCLB (native ingress controller) |
| Cluster create command | `eksctl create cluster` | `gcloud container clusters create` |
| Registry login | `aws ecr get-login-password \| docker login` | `gcloud auth configure-docker` |
| kubeconfig update | `aws eks update-kubeconfig` | `gcloud container clusters get-credentials` |
| Static IP | Auto-allocated by ALB | Must pre-reserve with `gcloud compute addresses create` |
| Storage | EBS (gp3) | Persistent Disk (pd-ssd) |
| Serverless option (native) | Lambda | Cloud Run / Cloud Functions |
| Serverless option (cloud-agnostic) | Knative on EKS | Knative on GKE |
| Free tier | 12 months (some services) | $300 / 90 days |
