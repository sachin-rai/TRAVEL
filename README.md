# Travel Booking Microservices Platform

A cloud-agnostic Spring Boot 3 / Java 17 microservices project that demonstrates an orchestrated booking workflow across **three independent services**, a **serverless cancellation function**, and a CI/CD pipeline that deploys to either **AWS EKS** or **GCP GKE** based on a single build-time parameter.

---

## Table of Contents

1. [What This Project Does](#1-what-this-project-does)
2. [Architecture Overview](#2-architecture-overview)
3. [Repository Layout](#3-repository-layout)
4. [Booking Flow & Scenarios](#4-booking-flow--scenarios)
5. [Prerequisites](#5-prerequisites)
6. [Local Development](#6-local-development)
7. [Build & Containerize](#7-build--containerize)
8. [Deploy to AWS (EKS, Pure Kubernetes)](#8-deploy-to-aws-eks-pure-kubernetes)
9. [Deploy to GCP (GKE, Pure Kubernetes)](#9-deploy-to-gcp-gke-pure-kubernetes)
10. [Jenkins CI/CD Pipeline](#10-jenkins-cicd-pipeline)
11. [API Reference & Swagger](#11-api-reference--swagger)
12. [How the Serverless Function Works](#12-how-the-serverless-function-works)
13. [Troubleshooting](#13-troubleshooting)

---

## 1. What This Project Does

| Microservice | Role | Port |
|---|---|---|
| **travel-service** | Orchestrator. Receives customer trip requests, calls hotel + flight services, manages trip lifecycle, issues itineraries, publishes cancellation events. | 8080 |
| **hotel-service** | Books and cancels rooms for a date range based on availability. | 8081 |
| **flight-service** | Books and cancels round-trip flights between two cities. | 8082 |
| **cancellation-function** | Cloud-agnostic serverless function (Spring Cloud Function). Triggered automatically when a customer cancels — calls hotel & flight cancellation APIs. | 8083 |

All four services use **H2 embedded databases** (in-memory) for demo purposes. Each service is independently deployable, has its own data store, and exposes a REST API.

---

## 2. Architecture Overview

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for full diagrams. Summary:

```
Customer → Ingress → travel-service ──► hotel-service
                          │      └─────► flight-service
                          │
                          ▼ (on cancellation)
                  cancellation-function ──► hotel-service.DELETE
                                       └──► flight-service.DELETE
```

**Cloud-agnostic by design:**

- Docker images are built once, pushed to **Docker Hub**.
- Kubernetes manifests under `k8s/common/` are identical for both clouds.
- Cloud-specific bits (ingress controller, storage class) live in `k8s/aws/` and `k8s/gcp/` and are applied as overlays.
- The serverless cancellation function runs on **Knative** (the cloud-agnostic K8s serverless layer) — the same manifest works on EKS and GKE. It can also run on AWS Lambda or GCP Cloud Run using the same JAR.

---

## 3. Repository Layout

```
travel-microservices/
├── pom.xml                                 ← parent Maven project
├── docker-compose.yml                      ← local dev stack
├── Jenkinsfile                             ← cloud-agnostic CI/CD pipeline
├── README.md                               ← this file
│
├── travel-service/                         ← orchestrator microservice
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/java/com/travel/...
│
├── hotel-service/                          ← hotel booking microservice
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/java/com/hotel/...
│
├── flight-service/                         ← flight booking microservice
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/java/com/flight/...
│
├── cancellation-function/                  ← Spring Cloud Function (serverless)
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/java/com/cancellation/...
│
├── k8s/
│   ├── common/                             ← cloud-agnostic manifests
│   │   ├── 00-namespace.yaml
│   │   ├── 01-configmap.yaml
│   │   ├── 10-hotel-service.yaml
│   │   ├── 11-flight-service.yaml
│   │   ├── 12-travel-service.yaml
│   │   └── 20-hpa.yaml
│   ├── functions/
│   │   ├── cancellation-function-knative.yaml      ← preferred (scale-to-zero)
│   │   └── cancellation-function-deployment.yaml   ← fallback
│   ├── aws/                                ← AWS overlay (ALB, EBS)
│   │   ├── ingress-alb.yaml
│   │   └── storage-class.yaml
│   └── gcp/                                ← GCP overlay (GCLB, PD)
│       ├── ingress-gke.yaml
│       └── storage-class.yaml
│
├── scripts/
│   ├── build-and-push.sh                   ← build + push images to Docker Hub
│   ├── deploy.sh                           ← apply manifests
│   ├── smoke-test.sh                       ← end-to-end test
│   └── cleanup.sh                          ← tear down
│
└── docs/
    └── ARCHITECTURE.md                     ← all flow diagrams
```

---

## 4. Booking Flow & Scenarios

### Scenario 1 — Happy Path

1. Customer POSTs a trip request to `/api/v1/trips`.
2. travel-service saves trip with status `INITIATED`.
3. travel-service calls `hotel-service.bookHotel()` — succeeds, returns booking reference.
4. travel-service updates trip status to `HOTEL_BOOKED`.
5. travel-service calls `flight-service.bookFlight()` — succeeds.
6. Trip status → `CONFIRMED`. Both booking refs stored on the trip.

### Scenario 2 — Hotel Unavailable

1. Customer POSTs trip request.
2. `hotel-service.bookHotel()` returns 400 (no rooms / unknown city).
3. travel-service marks trip `FAILED`. **Flight is NOT booked.**

### Scenario 3 — Hotel OK but Flight Unavailable

1. Hotel booking succeeds.
2. Flight booking fails.
3. travel-service **compensates** by calling `hotel-service.cancelBooking()`.
4. Trip status → `FAILED`.

### Cancellation (before start date)

1. Customer calls `DELETE /api/v1/trips/{tripRef}`.
2. travel-service checks `today < startDate`. If not → reject.
3. travel-service marks trip `CANCELLED`.
4. travel-service publishes a `CancellationEvent` (HTTP or stream).
5. The **cancellation-function** (Spring Cloud Function) is auto-triggered.
6. The function calls `hotel-service.DELETE` and `flight-service.DELETE` to roll back both bookings.

### Itinerary Issuance

A cron-scheduled job inside travel-service (`6:00 AM` daily by default) finds all CONFIRMED trips with `startDate == today`, marks them `COMPLETED`, and the itinerary is returned. After this, the trip can no longer be cancelled.

---

## 5. Prerequisites

| Tool | Version | Used For |
|---|---|---|
| Java | 17+ | Building services |
| Maven | 3.9+ | Multi-module build |
| Docker | 20+ | Container images |
| docker-compose | v2+ | Local stack |
| kubectl | 1.27+ | K8s deployment |
| envsubst | any | Manifest variable substitution |
| **AWS path** | aws-cli v2, eksctl (optional) | EKS access |
| **GCP path** | gcloud CLI | GKE access |

---

## 6. Local Development

Bring up the entire stack with one command:

```bash
docker-compose up --build
```

This starts all four services. Hit:

- Travel: http://localhost:8080
- Hotel:  http://localhost:8081
- Flight: http://localhost:8082
- Function: http://localhost:8083

H2 consoles available at `/h2-console` on each service.

Run the smoke test:

```bash
./scripts/smoke-test.sh
```

To stop:

```bash
docker-compose down
```

---

## 7. Build & Containerize

### Maven build (all modules):

```bash
mvn -B clean verify
```

### Build all Docker images locally:

```bash
for svc in hotel-service flight-service travel-service cancellation-function; do
    docker build -t $svc:latest ./$svc
done
```

---

## 8. Deploy to AWS (EKS, Pure Kubernetes)

### Step 1 — One-time AWS infrastructure

```bash
# Create EKS cluster (requires eksctl)
eksctl create cluster \
    --name travel-app-eks \
    --region us-east-1 \
    --nodegroup-name standard-workers \
    --node-type t3.medium \
    --nodes 3

# Install AWS Load Balancer Controller (needed for ALB Ingress)
# https://docs.aws.amazon.com/eks/latest/userguide/aws-load-balancer-controller.html

# Install Knative Serving (for the serverless cancellation function)
kubectl apply -f https://github.com/knative/serving/releases/download/knative-v1.14.0/serving-crds.yaml
kubectl apply -f https://github.com/knative/serving/releases/download/knative-v1.14.0/serving-core.yaml
kubectl apply -f https://github.com/knative/net-kourier/releases/download/knative-v1.14.0/kourier.yaml
kubectl patch configmap/config-network -n knative-serving --type merge \
    -p '{"data":{"ingress-class":"kourier.ingress.networking.knative.dev"}}'
```

### Step 2 — Build & push images to Docker Hub

```bash
export CLOUD_PROVIDER=AWS
export DOCKER_USER=your-username
export DOCKER_PASS=your-password-or-token
export IMAGE_TAG=v1

./scripts/build-and-push.sh
```

### Step 3 — Deploy

```bash
export DOCKER_USER=your-username
export AWS_EKS_CLUSTER=travel-app-eks
./scripts/deploy.sh
```

This will:
- Configure `kubectl` to point at the EKS cluster.
- Apply all manifests in `k8s/common/` (namespace, configmap, services, HPAs).
- Apply `k8s/aws/` overlay (ALB ingress, EBS storage class).
- Deploy the cancellation function as a Knative Service.
- Wait for rollouts.

### Step 4 — Verify

```bash
kubectl -n travel-app get pods
kubectl -n travel-app get ingress
ALB=$(kubectl -n travel-app get ingress travel-app-ingress \
        -o jsonpath='{.status.loadBalancer.ingress[0].hostname}')
echo "App at http://$ALB"

curl -X POST http://$ALB/api/v1/trips \
    -H "Content-Type: application/json" \
    -d '{
        "customerName":"Alice",
        "customerEmail":"alice@example.com",
        "originCity":"New York",
        "destinationCity":"Paris",
        "startDate":"2026-06-15",
        "endDate":"2026-06-20",
        "numberOfTravelers":2,
        "numberOfRooms":1
    }'
```

### Step 5 — Tear down

```bash
./scripts/cleanup.sh
eksctl delete cluster --name travel-app-eks --region us-east-1
```

---

## 9. Deploy to GCP (GKE, Pure Kubernetes)

### Step 1 — One-time GCP infrastructure

```bash
gcloud auth login
gcloud config set project my-project-id

# Enable APIs
gcloud services enable container.googleapis.com \
    artifactregistry.googleapis.com

# Create Artifact Registry repo
gcloud artifacts repositories create travel \
    --repository-format=docker \
    --location=us-central1

# Create GKE cluster
gcloud container clusters create travel-app-gke \
    --region=us-central1 \
    --num-nodes=2 \
    --machine-type=e2-medium

# Reserve a static IP for the ingress (referenced in k8s/gcp/ingress-gke.yaml)
gcloud compute addresses create travel-app-ip --global

# Install Knative Serving
kubectl apply -f https://github.com/knative/serving/releases/download/knative-v1.14.0/serving-crds.yaml
kubectl apply -f https://github.com/knative/serving/releases/download/knative-v1.14.0/serving-core.yaml
kubectl apply -f https://github.com/knative/net-kourier/releases/download/knative-v1.14.0/kourier.yaml
```

### Step 2 — Build & push images to Docker Hub

```bash
export CLOUD_PROVIDER=GCP
export DOCKER_USER=your-username
export DOCKER_PASS=your-password-or-token
export IMAGE_TAG=v1

./scripts/build-and-push.sh
```

### Step 3 — Deploy

```bash
export DOCKER_USER=your-username
export GCP_GKE_CLUSTER=travel-app-gke
./scripts/deploy.sh
```

### Step 4 — Verify

```bash
kubectl -n travel-app get pods
kubectl -n travel-app get ingress
LB=$(kubectl -n travel-app get ingress travel-app-ingress \
        -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
echo "App at http://$LB"
```

### Step 5 — Tear down

```bash
./scripts/cleanup.sh
gcloud container clusters delete travel-app-gke --region=us-central1
```

---

## 10. Jenkins CI/CD Pipeline

The `Jenkinsfile` at the project root drives the entire build → push → deploy cycle. It takes a `CLOUD_PROVIDER` parameter (`AWS` or `GCP`) and adapts every stage accordingly.

### Required Jenkins credentials

| Credential ID | Type | Used For |
|---|---|---|
| `docker-user` | Secret text | Docker Hub username |
| `docker-pass` | Secret text | Docker Hub password/token |
| `aws-credentials` | AWS access key | EKS access |
| `gcp-service-account` | Secret file (JSON) | GKE access |
| `gcp-project-id` | Secret text | GCP project ID |

### Pipeline parameters

| Parameter | Default | Description |
|---|---|---|
| `CLOUD_PROVIDER` | `AWS` | `AWS` or `GCP` |
| `IMAGE_TAG` | `v${BUILD_NUMBER}` | Image tag for this build |
| `DEPLOY` | `true` | Run the kubectl-apply stage? |
| `USE_KNATIVE` | `true` | Use Knative for cancellation function? |

### Pipeline stages

1. **Checkout** — git checkout
2. **Configure Cloud** — sets `IMAGE_REGISTRY` and `K8S_OVERLAY` based on `CLOUD_PROVIDER`
3. **Build & Unit Test** — `mvn clean verify`
4. **Build Docker Images** — builds each service
5. **Push Images** — Docker Hub
6. **Configure Kubectl** — `aws eks update-kubeconfig` or `gcloud container clusters get-credentials`
7. **Deploy to Kubernetes** — applies `k8s/common/`, the chosen cloud overlay, and the function manifest
8. **Smoke Test** — checks pods & services are running

### Triggering a build

In Jenkins UI: *Build with Parameters* → pick `CLOUD_PROVIDER`. The exact same job ships the same code to either cloud — no Jenkinsfile editing required.

---

## 11. API Reference & Swagger

Each service provides a **Swagger / OpenAPI 3** UI for exploring and testing the API directly from the browser.

| Microservice | Port | Swagger UI URL |
|---|---|---|
| **travel-service** | 8080 | http://localhost:8080/swagger-ui.html |
| **hotel-service** | 8081 | http://localhost:8081/swagger-ui.html |
| **flight-service** | 8082 | http://localhost:8082/swagger-ui.html |
| **cancellation-function** | 8083 | http://localhost:8083/swagger-ui.html |

### travel-service (port 8080)

```http
POST   /api/v1/trips                          ← create a trip booking
GET    /api/v1/trips/{tripReference}          ← look up trip
DELETE /api/v1/trips/{tripReference}          ← cancel before start date
POST   /api/v1/trips/{tripReference}/itinerary← issue itinerary on/after start date
```

**Create a trip:**

```bash
curl -X POST http://localhost:8080/api/v1/trips \
    -H "Content-Type: application/json" \
    -d '{
        "customerName": "Alice",
        "customerEmail": "alice@example.com",
        "originCity": "New York",
        "destinationCity": "Paris",
        "startDate": "2026-06-15",
        "endDate": "2026-06-20",
        "numberOfTravelers": 2,
        "numberOfRooms": 1
    }'
```

**Response (success):**
```json
{
    "tripReference": "TRIP-A1B2C3D4",
    "customerName": "Alice",
    "originCity": "New York",
    "destinationCity": "Paris",
    "startDate": "2026-06-15",
    "endDate": "2026-06-20",
    "hotelBookingReference": "HB-12345678",
    "flightBookingReference": "FB-87654321",
    "totalAmount": 2700.0,
    "status": "CONFIRMED",
    "message": "Trip booked successfully. Hotel + Flight confirmed."
}
```

### hotel-service (port 8081)

```http
POST   /api/v1/hotels/bookings                ← book rooms
GET    /api/v1/hotels/bookings/{ref}          ← lookup
DELETE /api/v1/hotels/bookings/{ref}          ← cancel
```

### flight-service (port 8082)

```http
POST   /api/v1/flights/bookings               ← book a flight
GET    /api/v1/flights/bookings/{ref}         ← lookup
DELETE /api/v1/flights/bookings/{ref}         ← cancel
```

### cancellation-function (port 8083)

```http
POST   /cancelTrip                            ← Spring Cloud Function endpoint
```

Body: a `CancellationEvent` JSON. Typically called by the travel-service publisher, not by humans.

---

## 12. How the Serverless Function Works

The cancellation function is built with **Spring Cloud Function** so the same JAR can run on any of:

| Platform | How |
|---|---|
| **Knative** (EKS / GKE / any K8s) | `kubectl apply -f k8s/functions/cancellation-function-knative.yaml` |
| **Plain K8s Deployment** | `kubectl apply -f k8s/functions/cancellation-function-deployment.yaml` |
| **AWS Lambda** | Package with the Spring Cloud Function AWS adapter, set handler to `org.springframework.cloud.function.adapter.aws.FunctionInvoker`, env `SPRING_CLOUD_FUNCTION_DEFINITION=cancelTrip` |
| **GCP Cloud Run** | `gcloud run deploy --image=<registry>/cancellation-function:<tag>` — Cloud Run is Knative under the hood |
| **Azure Functions** | Use the Azure adapter (same function bean) |

The same function bean (`@Bean public Function<CancellationEvent, CancellationResult> cancelTrip()`) is the entry point everywhere.

### Trigger modes

The travel-service publishes events two ways, selected by `FUNCTION_DISPATCH_MODE`:

- **`http`** (default) — direct HTTP POST. Best for Knative / Cloud Run / Lambda URL.
- **`stream`** — Spring Cloud Stream binder. The current pom uses Kafka; swap to Pub-Sub / Kinesis / EventBridge by changing the binder dependency only.

The function listens on both channels by default, so the runtime decides at deploy time.

### Why a function and not just a service?

- **Scale to zero** — Knative spins down the function when no cancellations happen, saving cost.
- **Event-driven** — fits naturally with brokers (Kafka / Pub-Sub).
- **Independent failure domain** — a slow cancellation handler can't slow the main booking path.
- **Cloud-portable** — same code works on every major serverless platform.

---

## 13. Troubleshooting

| Problem | Fix |
|---|---|
| `mvn` fails with "no compiler" | Install JDK 17 and verify `java -version` and `mvn -v` |
| `docker-compose up` exits with port in use | Stop any local service holding 8080-8083, 5672, 15672 |
| Pods in `ImagePullBackOff` | Confirm the image tag exists in Docker Hub, confirm repository is Public. |
| Ingress IP is empty on AWS | Confirm AWS Load Balancer Controller is installed and has IAM permissions |
| Ingress IP is empty on GCP | The static IP `travel-app-ip` must exist (`gcloud compute addresses create travel-app-ip --global`) |
| Knative service stays at `0/0` | Knative scales to zero by default; send a request to wake it. Confirm `minScale` annotation if you need a warm pod. |
| Travel service returns 503 on POST | `kubectl logs deployment/travel-service -n travel-app` — usually hotel-service or flight-service is not yet ready |
| H2 console returns 404 | Console only enabled in non-cloud profiles, but the manifest leaves it on for demo. Verify the service is up. |
| Cancellation isn't rolling back bookings | Check `cancellation-function` logs: `kubectl logs -n travel-app -l app=cancellation-function`. Confirm `HOTEL_SERVICE_URL` / `FLIGHT_SERVICE_URL` in the configmap point at the cluster-DNS names. |
| "Cannot cancel: trip start date has already arrived" | This is expected — once `startDate` has passed, the itinerary has been issued and cancellation is no longer allowed. |

---