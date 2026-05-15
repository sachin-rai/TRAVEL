# GCP-Specific Kubernetes Manifests

This folder contains overlays applied AFTER the common manifests when deploying to GKE.

| Resource | Purpose |
|---|---|
| `ingress-gke.yaml` | Provisions a Google Cloud HTTP Load Balancer + BackendConfig |
| `storage-class.yaml` | pd-ssd StorageClass for persistent volumes |

## Image registry

Travel app images are pushed to **Google Artifact Registry**:
```
<region>-docker.pkg.dev/<project-id>/travel/{travel,hotel,flight,cancellation}-service
```

## Serverless options on GCP

The cancellation function (Spring Cloud Function) can run on GCP in three ways without changing the code:

1. **Knative on GKE** — apply `k8s/functions/cancellation-function-knative.yaml`. Recommended for full cloud-agnosticism. GKE supports Knative natively via Cloud Run for Anthos.
2. **Cloud Run** — push the function container image to Artifact Registry and deploy with `gcloud run deploy`. Cloud Run is Knative under the hood, so the same image works.
3. **Plain K8s Deployment** — apply `k8s/functions/cancellation-function-deployment.yaml` if Knative is not installed.

This project defaults to option 1 (Knative) for portability — the same manifest works on AWS.
