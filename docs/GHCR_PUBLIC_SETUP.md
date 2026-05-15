# Public GHCR Setup

This project uses **GitHub Container Registry (ghcr.io)** for all container images, configured for **public** packages. No Amazon ECR. No Google Artifact Registry. One registry for everything.

## How public GHCR changes the auth picture

| Action | Needs authentication? | Used by |
|---|---|---|
| **Push** an image to GHCR | ✅ Yes — needs a PAT with `write:packages` scope | Jenkins / your laptop |
| **Pull** an image from a **public** GHCR package | ❌ No — anonymous works | EKS nodes, GKE nodes |

That's the entire benefit: your build pipeline needs a token, but your runtime clusters do not. No `imagePullSecret` in K8s, no IAM roles for ECR access, no Artifact Registry permissions — just public images that anyone can pull, anywhere.

---

## One-time setup

### 1. Create a GitHub Personal Access Token (PAT)

You need this **only for pushing** — never for pulling.

1. Go to https://github.com/settings/tokens/new
2. **Token name:** `travel-app-ghcr`
3. **Expiration:** 90 days (or longer if you prefer)
4. **Scopes:** check **`write:packages`** (this implies `read:packages` too)
5. Click **Generate token**
6. **Copy the token now** — GitHub only shows it once

### 2. Add PAT and owner to Jenkins credentials

In Jenkins → **Manage Jenkins → Credentials → System → Global credentials → Add credentials**:

| Credential ID | Kind | Value |
|---|---|---|
| `ghcr-owner` | Secret text | Your GitHub username (lowercase) or org name, e.g. `alice` |
| `ghcr-pat` | Secret text | The PAT you just created (`ghp_xxxxxxxx...`) |
| `aws-credentials` | AWS Credentials | Your AWS access key + secret (for EKS deploys) |
| `gcp-project-id` | Secret text | Your GCP project ID (for GKE deploys) |
| `gcp-service-account` | Secret file | A GCP service account JSON key file (for GKE deploys) |

### 3. Make each package public — on FIRST push only

GitHub creates packages as **private by default**. The very first time you push each of the four images, you need to flip them to public. After this, future pushes to the same package stay public.

After the first Jenkins build (or first `./scripts/build-and-push.sh` run) succeeds:

1. Go to `https://github.com/<your-username>?tab=packages` (or `https://github.com/orgs/<your-org>/packages` for org accounts)
2. For each package (`hotel-service`, `flight-service`, `travel-service`, `cancellation-function`):
   - Click the package name
   - Click **"Package settings"** (right side)
   - Scroll to **"Danger Zone"** at the bottom
   - Click **"Change visibility"** → **Public** → confirm

Verify each is public by visiting the URL anonymously (in an incognito window):
```
https://ghcr.io/v2/<your-username>/hotel-service/manifests/latest
```
A public package returns a manifest JSON; a private one returns 401 Unauthorized.

> **Once-and-done.** After the package is public, you never need to touch this again. Future pushes from Jenkins preserve the public visibility.

---

## Running a deployment

### From Jenkins (recommended)

1. Open the travel-microservices job in Jenkins
2. Click **"Build with Parameters"**
3. Set parameters:
   - **CLOUD_PROVIDER:** `AWS` or `GCP` (the dropdown)
   - **IMAGE_TAG:** `v1` or whatever tag you want
   - **BUILD_AND_PUSH:** ✅ checked (uncheck to skip rebuild and just redeploy an existing tag)
   - **DEPLOY:** ✅ checked
   - **USE_KNATIVE:** ✅ checked
4. Click **Build**

The pipeline:
1. Logs Docker into GHCR with your PAT
2. Builds all 4 images
3. Pushes them to `ghcr.io/<your-username>/<service>:<tag>` and `:latest`
4. Configures `kubectl` for the selected cloud's cluster (EKS or GKE)
5. Applies manifests — common ones first, then the cloud-specific overlay (ingress + storage)
6. Deploys cancellation-function on Knative
7. Waits for all rollouts to complete

### From your laptop (manual)

**To build and push** (you need the PAT):

```bash
export GHCR_OWNER=your-github-username       # lowercase!
export GHCR_PAT=ghp_xxxxxxxxxxxxxxxxxxxxxx
export IMAGE_TAG=v1

./scripts/build-and-push.sh
```

**To deploy** (no PAT needed — pulls are public):

```bash
# For AWS
export CLOUD_PROVIDER=AWS
export GHCR_OWNER=your-github-username
export IMAGE_TAG=v1
export AWS_REGION=us-east-1
export AWS_EKS_CLUSTER=travel-app-eks

./scripts/deploy.sh
```

```bash
# For GCP
export CLOUD_PROVIDER=GCP
export GHCR_OWNER=your-github-username
export IMAGE_TAG=v1
export GCP_PROJECT_ID=your-project-id
export GCP_REGION=us-central1
export GCP_GKE_CLUSTER=travel-app-gke

./scripts/deploy.sh
```

Note how the deploy script doesn't ask for the PAT. Because packages are public, the K8s cluster pulls them without any credentials.

---

## What the image URLs look like

After a successful push, your images live at:

```
ghcr.io/<your-github-username>/hotel-service:v1
ghcr.io/<your-github-username>/flight-service:v1
ghcr.io/<your-github-username>/travel-service:v1
ghcr.io/<your-github-username>/cancellation-function:v1
```

Anyone in the world can `docker pull` these — no login required.

The Kubernetes manifests already reference them via `${IMAGE_REGISTRY}/<service>:${IMAGE_TAG}`, where `IMAGE_REGISTRY=ghcr.io/<your-username>` is set by the pipeline at apply time via `envsubst`.

---

## Verification checklist

After your first deploy, run these to confirm everything is wired correctly:

```bash
# 1. Packages exist and are public (anonymous curl works)
curl -s -o /dev/null -w "%{http_code}\n" \
  https://ghcr.io/v2/your-username/hotel-service/manifests/latest
# Expected: 200 (private would return 401)

# 2. Pods are pulling successfully
kubectl -n travel-app get pods
# All should be Running, no ImagePullBackOff

# 3. Inspect what images the pods are actually using
kubectl -n travel-app get pods -o jsonpath='{range .items[*]}{.metadata.name}{"\t"}{.spec.containers[0].image}{"\n"}{end}'
# Expected: every line shows ghcr.io/your-username/<service>:<tag>

# 4. Confirm no imagePullSecret is configured (it shouldn't be)
kubectl -n travel-app get pod -o jsonpath='{range .items[*]}{.spec.imagePullSecrets}{"\n"}{end}'
# Expected: empty lines (no pull secrets needed for public images)
```

---

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `denied: denied` on `docker push` | PAT missing `write:packages`, or PAT expired | Regenerate PAT with the right scope |
| `unauthorized` when pulling from ghcr.io | Package is still private | Go to package settings → Change visibility → Public |
| Pods stuck in `ImagePullBackOff` after first deploy | Same — packages still private | Public visibility step from setup #3 |
| Jenkins build fails at "Build & Push Images to GHCR" | `ghcr-owner` or `ghcr-pat` Jenkins credentials missing | Add them (setup #2) |
| Pods show `ErrImagePull` only intermittently | GHCR rate limits on anonymous pulls | Rare, but if you hit it: make pulls authenticated by adding an `imagePullSecret` (defeats the simplicity of public, but works). Better fix: tag stable releases instead of using `latest` |
| `GHCR_OWNER must be set` error | Env var typo or missing | `export GHCR_OWNER=yourname` — must be lowercase, matches your GitHub username exactly |
| Image pushes succeed but show as private in GitHub UI | They were created from scratch — GitHub defaults new packages to private | One-time visibility change (setup #3) |

---

## Why this is the cleanest setup

| Concern | ECR / Artifact Registry approach | Public GHCR approach |
|---|---|---|
| Number of registries to manage | 1 per cloud (so 2 if you use both) | **1 total** |
| K8s pull authentication | IAM role for EKS nodes / SA for GKE nodes | **None — anonymous works** |
| Cross-cloud image consistency | Manual sync between ECR and AR | **Automatic — same registry serves both** |
| Cost | $0.10/GB/month in ECR; similar in AR | **Free for public packages** |
| What needs credentials | Push AND pull on every cloud | **Only push (in CI)** |
| Vendor lock-in | High | **None** |

The original prompt asked for "cloud-agnostic" microservices. Using a vendor-neutral registry like GHCR for images closes the last gap — now neither your code, your manifests, nor your image storage are tied to a specific cloud.

---

## Going further

- **GitHub Actions instead of Jenkins?** The PAT becomes unnecessary inside GitHub Actions — use the built-in `GITHUB_TOKEN` secret which is auto-generated per workflow run and has implicit `packages:write` permission. Ask if you want this workflow generated.
- **Tag images by git SHA instead of build number?** Change `IMAGE_TAG` to `$(git rev-parse --short HEAD)` for fully reproducible deployments tied to commits.
- **Switching back to private packages later?** Re-add `imagePullSecrets: ghcr-pull-secret` to each deployment in `k8s/common/*.yaml`, and create the secret on every deploy via `kubectl create secret docker-registry ...`. The original conversation history has those manifests if you need them.
