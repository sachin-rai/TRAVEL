# AWS-Specific Kubernetes Manifests

This folder contains overlays applied AFTER the common manifests when deploying to EKS.

| Resource | Purpose |
|---|---|
| `ingress-alb.yaml` | Provisions an Application Load Balancer via AWS Load Balancer Controller |
| `storage-class.yaml` | EBS-backed gp3 StorageClass (used by future stateful services) |

## Image registry

Travel app images are pushed to **Docker Hub**:
```
docker.io/<username>/{travel,hotel,flight}-service
docker.io/<username>/cancellation-function
```

Kubernetes nodes pull these images anonymously (no `imagePullSecret` needed) because they are set to **public** on Docker Hub.

## Serverless options on AWS

The cancellation function (Spring Cloud Function) can run on AWS in three ways without changing the code:

1. **Knative on EKS** — apply `k8s/functions/cancellation-function-knative.yaml`. Scales to zero. Recommended for full cloud-agnosticism.
2. **AWS Lambda** — package the same JAR with the Spring Cloud Function AWS adapter; configure the handler as `org.springframework.cloud.function.adapter.aws.FunctionInvoker` with env `SPRING_CLOUD_FUNCTION_DEFINITION=cancelTrip`. Trigger from EventBridge or API Gateway.
3. **Plain K8s Deployment** — apply `k8s/functions/cancellation-function-deployment.yaml` if Knative is not installed.

This project defaults to option 1 (Knative) for portability — the same manifest works on GCP.
