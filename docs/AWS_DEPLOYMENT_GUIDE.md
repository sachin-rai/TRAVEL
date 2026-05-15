# Deploying Travel Microservices to AWS (EKS) — Step-by-Step Guide

This guide takes you from a fresh AWS account to a fully running Travel Microservices platform on Amazon EKS, using **pure Kubernetes** (no AWS-proprietary services beyond EKS itself, ECR, and the load balancer controller). Every command is shown — copy-paste-ready.

By the end you'll have:

- An EKS cluster running 3 worker nodes
- Four Docker images in Amazon ECR
- Hotel, Flight, Travel services running as Kubernetes Deployments
- Cancellation function running as a Knative Service (scale-to-zero serverless)
- A public Application Load Balancer routing traffic to the services

---

## Table of Contents

1. [Architecture on AWS](#1-architecture-on-aws)
2. [Prerequisites](#2-prerequisites)
3. [AWS Account Setup](#3-aws-account-setup)
4. [Install Required CLI Tools](#4-install-required-cli-tools)
5. [Create the EKS Cluster](#5-create-the-eks-cluster)
6. [Install AWS Load Balancer Controller](#6-install-aws-load-balancer-controller)
7. [Install Knative Serving](#7-install-knative-serving-for-the-serverless-function)
8. [Create ECR Repositories](#8-create-ecr-repositories)
9. [Build and Push Docker Images](#9-build-and-push-docker-images)
10. [Deploy to Kubernetes](#10-deploy-to-kubernetes)
11. [Verify the Deployment](#11-verify-the-deployment)
12. [Test End-to-End](#12-test-the-booking-flow-end-to-end)
13. [Updating an Existing Deployment](#13-updating-an-existing-deployment)
14. [Cost Estimate](#14-cost-estimate)
15. [Tear Down](#15-tear-down-everything)
16. [Troubleshooting](#16-troubleshooting)

---

## 1. Architecture on AWS

```
                       Internet
                          │
                          ▼
            ┌────────────────────────────┐
            │   Application Load         │  ← provisioned by AWS Load
            │   Balancer (ALB)           │     Balancer Controller from
            └────────────┬───────────────┘     the K8s Ingress resource
                         │
                         ▼
           ┌──────────────────────────────────┐
           │   EKS Cluster (travel-app-eks)   │
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
           │   EC2 Worker Nodes (3x t3.medium)│
           └──────────────────────────────────┘
                          │
                          ▼
           ┌──────────────────────────────────┐
           │  Amazon ECR                      │
           │  - travel-service:v1             │
           │  - hotel-service:v1              │
           │  - flight-service:v1             │
           │  - cancellation-function:v1      │
           └──────────────────────────────────┘
```

All AWS-specific pieces (ALB, EBS storage class) live in `k8s/aws/` overlays. Everything else (`k8s/common/`) is identical to GCP.

---

## 2. Prerequisites

- An **AWS account** with billing set up (this stack costs ~$5-10/day if left running — see [Section 14](#14-cost-estimate))
- A machine to run CLI commands from (Mac, Linux, or Windows with WSL2)
- Docker installed and running
- The `travel-microservices.zip` from earlier, unzipped to a working directory

---

## 3. AWS Account Setup

### 3.1 Create an IAM user for the deployment

You need programmatic AWS access. **Do not use root account credentials** for deployment.

1. AWS Console → **IAM → Users → Create user**
2. Username: `travel-app-admin`
3. Attach policies directly:
   - `AmazonEKSClusterPolicy`
   - `AmazonEKSWorkerNodePolicy`
   - `AmazonEC2ContainerRegistryFullAccess`
   - `AmazonVPCFullAccess`
   - `IAMFullAccess` *(needed for `eksctl` to create service-linked roles)*
   - `CloudFormationFullAccess` *(needed for `eksctl`)*
   - For demo / dev only — in production, scope these down.
4. Click the user → **Security credentials → Create access key**
5. Choose **Command Line Interface (CLI)**, save the **Access key ID** and **Secret access key** somewhere safe.

> **Note**: `IAMFullAccess` + `CloudFormationFullAccess` are broad. For a hardened production setup, use the IAM policies documented at https://eksctl.io/usage/minimum-iam-policies/

### 3.2 Pick a region

This guide uses `us-east-1` (N. Virginia). EKS is available in all major regions — just substitute the region name throughout.

---

## 4. Install Required CLI Tools

Run these install commands on your local machine. macOS commands shown; Linux equivalents are similar.

### 4.1 AWS CLI v2

```bash
# macOS
brew install awscli

# Linux
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
unzip awscliv2.zip
sudo ./aws/install
```

Verify:
```bash
aws --version
# aws-cli/2.x.x ...
```

### 4.2 eksctl

`eksctl` is the official "one-command-creates-an-EKS-cluster" tool.

```bash
# macOS
brew tap weaveworks/tap
brew install weaveworks/tap/eksctl

# Linux
curl --silent --location "https://github.com/eksctl-io/eksctl/releases/latest/download/eksctl_$(uname -s)_amd64.tar.gz" | tar xz -C /tmp
sudo mv /tmp/eksctl /usr/local/bin
```

Verify:
```bash
eksctl version
```

### 4.3 kubectl

```bash
# macOS
brew install kubectl

# Linux
curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
sudo install -o root -g root -m 0755 kubectl /usr/local/bin/kubectl
```

Verify:
```bash
kubectl version --client
```

### 4.4 Helm (for Load Balancer Controller install)

```bash
# macOS
brew install helm

# Linux
curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash
```

### 4.5 Configure AWS CLI

```bash
aws configure
# AWS Access Key ID:    <paste from 3.1>
# AWS Secret Access Key: <paste from 3.1>
# Default region name:   us-east-1
# Default output format: json
```

Verify:
```bash
aws sts get-caller-identity
# Should print your account ID and user ARN
```

**Save your account ID** — you'll use it everywhere:
```bash
export AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
export AWS_REGION=us-east-1
echo "Account: $AWS_ACCOUNT_ID  Region: $AWS_REGION"
```

---

## 5. Create the EKS Cluster

This takes **15-20 minutes**. Grab coffee.

```bash
eksctl create cluster \
    --name travel-app-eks \
    --region $AWS_REGION \
    --version 1.29 \
    --nodegroup-name standard-workers \
    --node-type t3.medium \
    --nodes 3 \
    --nodes-min 2 \
    --nodes-max 5 \
    --managed \
    --with-oidc
```

What this does:
- Creates a VPC with public + private subnets
- Provisions an EKS control plane (managed by AWS)
- Spins up an Auto Scaling Group with 3 × t3.medium EC2 instances as worker nodes
- Configures `kubectl` to point at the new cluster
- `--with-oidc` enables IAM Roles for Service Accounts (needed in the next step)

When done, verify:

```bash
kubectl get nodes
# NAME                              STATUS   ROLES    AGE   VERSION
# ip-192-168-X-X.ec2.internal       Ready    <none>   2m    v1.29.x
# ip-192-168-X-X.ec2.internal       Ready    <none>   2m    v1.29.x
# ip-192-168-X-X.ec2.internal       Ready    <none>   2m    v1.29.x

aws eks list-clusters --region $AWS_REGION
```

---

## 6. Install AWS Load Balancer Controller

The K8s Ingress resource in `k8s/aws/ingress-alb.yaml` requires this controller to actually provision an ALB.

### 6.1 Create an IAM policy for the controller

```bash
curl -O https://raw.githubusercontent.com/kubernetes-sigs/aws-load-balancer-controller/v2.7.2/docs/install/iam_policy.json

aws iam create-policy \
    --policy-name AWSLoadBalancerControllerIAMPolicy \
    --policy-document file://iam_policy.json
```

### 6.2 Create a service account that uses this policy

```bash
eksctl create iamserviceaccount \
    --cluster=travel-app-eks \
    --region=$AWS_REGION \
    --namespace=kube-system \
    --name=aws-load-balancer-controller \
    --role-name=AmazonEKSLoadBalancerControllerRole \
    --attach-policy-arn=arn:aws:iam::${AWS_ACCOUNT_ID}:policy/AWSLoadBalancerControllerIAMPolicy \
    --approve
```

### 6.3 Install the controller via Helm

```bash
helm repo add eks https://aws.github.io/eks-charts
helm repo update

helm install aws-load-balancer-controller eks/aws-load-balancer-controller \
    -n kube-system \
    --set clusterName=travel-app-eks \
    --set serviceAccount.create=false \
    --set serviceAccount.name=aws-load-balancer-controller
```

### 6.4 Verify it's running

```bash
kubectl get deployment -n kube-system aws-load-balancer-controller
# Should show 2/2 ready in ~30 seconds
```

---

## 7. Install Knative Serving (for the serverless function)

The cancellation function runs as a **Knative Service** (the cloud-agnostic serverless layer that gives you scale-to-zero on K8s). The same manifest will work on GCP — that's the whole point of using Knative instead of going straight to Lambda.

### 7.1 Install Knative Serving CRDs and core

```bash
kubectl apply -f https://github.com/knative/serving/releases/download/knative-v1.14.0/serving-crds.yaml

kubectl apply -f https://github.com/knative/serving/releases/download/knative-v1.14.0/serving-core.yaml
```

### 7.2 Install Kourier as the Knative ingress

Kourier is a lightweight Envoy-based ingress that integrates with Knative. We use it instead of Istio (which is a much heavier install).

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

## 8. Create ECR Repositories

ECR is Amazon's container registry. Each service gets its own repo.

```bash
for svc in hotel-service flight-service travel-service cancellation-function; do
    aws ecr describe-repositories --region $AWS_REGION --repository-names $svc 2>/dev/null \
        || aws ecr create-repository \
            --region $AWS_REGION \
            --repository-name $svc \
            --image-scanning-configuration scanOnPush=true
done
```

Confirm all four exist:
```bash
aws ecr describe-repositories --region $AWS_REGION \
    --query 'repositories[].repositoryName' --output table
```

You should see all four service names.

---

## 9. Build and Push Docker Images

### 9.1 Log Docker into ECR

```bash
aws ecr get-login-password --region $AWS_REGION \
    | docker login --username AWS \
                   --password-stdin ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com
```

You should see `Login Succeeded`.

### 9.2 Use the provided build-and-push script

From the project root:

```bash
cd travel-microservices/

export CLOUD_PROVIDER=AWS
export AWS_ACCOUNT_ID=$AWS_ACCOUNT_ID
export AWS_REGION=$AWS_REGION
export IMAGE_TAG=v1

./scripts/build-and-push.sh
```

This builds and pushes all four images. Each one takes ~3-5 minutes the first time (Maven downloads ~300MB of Spring Boot dependencies inside the build container).

### 9.3 Verify images are in ECR

```bash
for svc in hotel-service flight-service travel-service cancellation-function; do
    echo "=== $svc ==="
    aws ecr describe-images --region $AWS_REGION --repository-name $svc \
        --query 'imageDetails[].imageTags' --output table
done
```

Each should show tags `v1` and `latest`.

---

## 10. Deploy to Kubernetes

Now apply the manifests. The `deploy.sh` script handles the variable substitution and applies everything in the right order.

```bash
export CLOUD_PROVIDER=AWS
export AWS_ACCOUNT_ID=$AWS_ACCOUNT_ID
export AWS_REGION=$AWS_REGION
export AWS_EKS_CLUSTER=travel-app-eks
export IMAGE_TAG=v1
export USE_KNATIVE=true

./scripts/deploy.sh
```

What this does, in order:
1. Reconfirms `kubectl` is pointing at your EKS cluster
2. Applies `k8s/common/*.yaml` — namespace, configmap, services for hotel/flight/travel, HPAs
3. Applies `k8s/aws/*.yaml` — ALB Ingress, EBS storage class
4. Applies `k8s/functions/cancellation-function-knative.yaml` — the Knative Service
5. Waits for each Deployment to roll out successfully

You'll see output ending in:
```
✅ Deployment to AWS complete.
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

## 11. Verify the Deployment

### 11.1 Check pod status

```bash
kubectl -n travel-app get pods -w
# Ctrl+C when all are Running
```

### 11.2 Check services and ingress

```bash
kubectl -n travel-app get svc
kubectl -n travel-app get ingress
```

The ALB takes **2-3 minutes** to provision after the Ingress is applied. Wait for the ADDRESS column to populate:

```bash
kubectl -n travel-app get ingress travel-app-ingress -w
# NAME                 ADDRESS                                                       PORTS
# travel-app-ingress   k8s-travelap-travelap-xxxx.us-east-1.elb.amazonaws.com        80
```

### 11.3 Get the ALB hostname

```bash
export ALB_URL=$(kubectl -n travel-app get ingress travel-app-ingress \
    -o jsonpath='{.status.loadBalancer.ingress[0].hostname}')
echo "App URL: http://$ALB_URL"
```

### 11.4 Health check from outside the cluster

```bash
# Travel service through the ALB
curl http://$ALB_URL/api/v1/trips/DOES-NOT-EXIST
# Expect: {"error":"Trip not found: DOES-NOT-EXIST"}

# That 400 response is from your code — it means the ALB → ingress → travel-service path works!
```

### 11.5 Verify Knative function

```bash
kubectl get ksvc -n travel-app
# NAME                    URL                                                          READY
# cancellation-function   http://cancellation-function.travel-app.svc.cluster.local   True
```

You can also check the Knative service is registered (it's not externally exposed by default since it's invoked internally):

```bash
kubectl get pods -n travel-app -l serving.knative.dev/service=cancellation-function
# Empty list — the function is scaled to zero
```

---

## 12. Test the Booking Flow End-to-End

### 12.1 Scenario 1 — Happy path

```bash
START_DATE=$(date -d '+30 days' '+%Y-%m-%d' 2>/dev/null || date -v+30d '+%Y-%m-%d')
END_DATE=$(date -d '+35 days' '+%Y-%m-%d' 2>/dev/null || date -v+35d '+%Y-%m-%d')

RESP=$(curl -s -X POST http://$ALB_URL/api/v1/trips \
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

### 12.2 Watch the logs in real-time

In a separate terminal:

```bash
kubectl -n travel-app logs -l app=travel-service -f --tail=20
```

### 12.3 Scenario 2 — Cancel the trip (triggers the serverless function)

```bash
curl -X DELETE http://$ALB_URL/api/v1/trips/$TRIP_REF
```

Now watch the cancellation function get spun up:

```bash
kubectl get pods -n travel-app -l serving.knative.dev/service=cancellation-function -w
# You'll see a pod appear, run for a few seconds, then disappear after Knative scales back to zero
```

And inspect the function's logs:

```bash
kubectl logs -n travel-app -l serving.knative.dev/service=cancellation-function --tail=20
```

You should see entries like `Calling DELETE http://hotel-service...`, `Hotel booking ... cancelled successfully`, etc.

### 12.4 Scenario 3 — Hotel unavailable

```bash
curl -X POST http://$ALB_URL/api/v1/trips \
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

## 13. Updating an Existing Deployment

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

## 14. Cost Estimate

Approximate hourly costs in us-east-1 (as of early 2026 — check current pricing):

| Resource | Quantity | Hourly | Daily |
|---|---|---|---|
| EKS control plane | 1 | $0.10 | $2.40 |
| EC2 t3.medium nodes | 3 | $0.0416 × 3 = $0.125 | $3.00 |
| Application Load Balancer | 1 | $0.0225 + LCU | ~$0.60 |
| EBS storage (gp3) for nodes | 60 GB | $0.0001 × 60 = $0.006 | $0.14 |
| ECR storage | <1 GB | negligible | <$0.01 |
| Data transfer (out) | varies | varies | $0.50 - $5 |
| **Total** | | | **~$6.50 - $12 / day** |

> **Tip**: Spin down nightly. The `scripts/cleanup.sh` removes the K8s namespace; `eksctl delete cluster` removes the rest. See [Section 15](#15-tear-down-everything).

---

## 15. Tear Down Everything

When you're done testing, **delete everything** — EKS clusters running idle can add up to $100+ over a week.

### 15.1 Delete the K8s application

```bash
cd travel-microservices/
./scripts/cleanup.sh
# Or manually: kubectl delete namespace travel-app
```

### 15.2 Delete the load balancer controller and Knative

```bash
helm uninstall aws-load-balancer-controller -n kube-system
kubectl delete -f https://github.com/knative/net-kourier/releases/download/knative-v1.14.0/kourier.yaml
kubectl delete -f https://github.com/knative/serving/releases/download/knative-v1.14.0/serving-core.yaml
kubectl delete -f https://github.com/knative/serving/releases/download/knative-v1.14.0/serving-crds.yaml
```

### 15.3 Delete the EKS cluster

```bash
eksctl delete cluster --name travel-app-eks --region $AWS_REGION
```

This takes ~10-15 minutes. It cleans up the VPC, NAT gateways, security groups, IAM roles created by eksctl, and the EC2 instances.

### 15.4 Delete the ECR repositories

```bash
for svc in hotel-service flight-service travel-service cancellation-function; do
    aws ecr delete-repository --region $AWS_REGION --repository-name $svc --force
done
```

### 15.5 Delete the IAM policy

```bash
aws iam delete-policy --policy-arn arn:aws:iam::${AWS_ACCOUNT_ID}:policy/AWSLoadBalancerControllerIAMPolicy
```

### 15.6 Sanity check

```bash
# Should not list any travel-app-eks cluster
eksctl get clusters --region $AWS_REGION

# Should not list any travel-app repos
aws ecr describe-repositories --region $AWS_REGION
```

---

## 16. Troubleshooting

| Problem | Likely Cause | Fix |
|---|---|---|
| `eksctl create cluster` fails with insufficient permissions | IAM user missing CloudFormation or VPC permissions | Re-attach the policies listed in [3.1](#31-create-an-iam-user-for-the-deployment) |
| `kubectl get nodes` returns "Unable to connect to the server" | kubeconfig didn't get updated | Run `aws eks update-kubeconfig --region $AWS_REGION --name travel-app-eks` |
| Pods stuck in `ImagePullBackOff` | Worker nodes can't pull from ECR | Confirm the node IAM role has `AmazonEC2ContainerRegistryReadOnly`. `eksctl` adds it by default; if you used a custom role, attach it manually. |
| Pods stuck in `Pending` with "0/3 nodes are available: insufficient cpu" | Worker nodes are too small for the configured resource requests | Either: (a) increase node count: `eksctl scale nodegroup --cluster=travel-app-eks --nodes=4 --name=standard-workers`, or (b) reduce `requests.cpu` in the deployment YAMLs |
| Ingress ADDRESS stays empty for >5 minutes | AWS Load Balancer Controller not installed or its IAM role is wrong | `kubectl logs -n kube-system deployment/aws-load-balancer-controller` — look for IAM errors |
| Knative service stays "Unknown" / not ready | `serving-core.yaml` not fully deployed | `kubectl get pods -n knative-serving` — all must be Running; if not, `kubectl describe` the failed pod |
| Booking returns 503 from the ALB | Travel service pod isn't ready yet, or hotel-service unreachable from travel-service | `kubectl logs -n travel-app -l app=travel-service` — usually one of the Feign URLs is wrong |
| Cancellation event seems to fire but bookings stay CONFIRMED | The cancellation function's `HOTEL_SERVICE_URL` / `FLIGHT_SERVICE_URL` env vars aren't propagated correctly | `kubectl get ksvc cancellation-function -n travel-app -o yaml` and inspect the `env:` block. Should reference `travel-app-config` ConfigMap. |
| `kubectl get ksvc` says "command not found" or "no resources found" | Knative CRDs not installed | Re-run [Section 7](#7-install-knative-serving-for-the-serverless-function) |
| Want to see what happens if you delete a pod | Pod self-heals because the Deployment has `replicas: 2` | `kubectl delete pod <pod-name> -n travel-app` — a new one is created in seconds |
| Booking fails the second time with "no rooms available" | H2 in-memory DB persists for pod lifetime; multiple bookings on same dates exhaust inventory | Either book different cities/dates, or restart the pod: `kubectl rollout restart deployment/hotel-service -n travel-app` (wipes the in-memory DB) |
| ALB health checks failing in AWS console | The health check path or port is wrong | The ingress annotation sets `alb.ingress.kubernetes.io/healthcheck-path: /actuator/health/liveness`. Confirm the actuator endpoint is exposed (it is in the application.yml). |

### Useful debugging commands

```bash
# Pod-level inspection
kubectl describe pod <pod-name> -n travel-app

# Get logs from all pods of a service
kubectl logs -n travel-app -l app=travel-service --tail=100 --all-containers

# Shell into a pod
kubectl exec -it -n travel-app deployment/travel-service -- /bin/sh

# See the rendered manifest after envsubst
envsubst < k8s/common/12-travel-service.yaml | less

# Watch events live (the K8s equivalent of dmesg)
kubectl get events -n travel-app --sort-by='.lastTimestamp' -w
```

---

## What You Have Now

After this guide:
- ✅ An EKS cluster ready to run any Kubernetes workload
- ✅ A working CI-style image pipeline (the `build-and-push.sh` script is what Jenkins runs)
- ✅ Knative Serving installed — you can deploy other scale-to-zero services with `kubectl apply -f your-ksvc.yaml`
- ✅ Real-world load balancing through ALB

The same Docker images and the same `k8s/common/` manifests will deploy unchanged to GCP GKE — only the overlay (`k8s/aws/` → `k8s/gcp/`) and the registry/cluster names differ. That's the cloud-agnostic story in practice.
