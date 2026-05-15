// ─────────────────────────────────────────────────────────────────────────────
// CI/CD PIPELINE — PUBLIC GHCR REGISTRY VARIANT
//
// All container images live in GitHub Container Registry (ghcr.io) and are
// PUBLIC. Because they're public:
//   - Anyone (including K8s nodes on EKS/GKE) can pull without authentication
//   - No imagePullSecret needed in the target cluster
//   - Only the BUILD side needs a PAT (with write:packages scope) for pushing
//
// The CLOUD_PROVIDER parameter only chooses WHERE to deploy:
//   AWS -> EKS  (with ALB ingress)
//   GCP -> GKE  (with GCLB ingress)
// Same image, same tag, same Docker layers — only the kubectl context and
// the K8s overlay differ between clouds.
// ─────────────────────────────────────────────────────────────────────────────
pipeline {
    agent any

    parameters {
        choice(
            name: 'CLOUD_PROVIDER',
            choices: ['AWS', 'GCP'],
            description: 'Where to deploy. Images are always pushed to public GHCR.'
        )
        string(
            name: 'IMAGE_TAG',
            defaultValue: "v${env.BUILD_NUMBER}",
            description: 'Docker image tag (defaults to v<buildNumber>)'
        )
        booleanParam(
            name: 'BUILD_AND_PUSH',
            defaultValue: true,
            description: 'Build images and push to GHCR? Untick to deploy an existing tag.'
        )
        booleanParam(
            name: 'DEPLOY',
            defaultValue: true,
            description: 'Apply Kubernetes manifests after building?'
        )
        booleanParam(
            name: 'USE_KNATIVE',
            defaultValue: true,
            description: 'Deploy cancellation-function as Knative Service (scale-to-zero)?'
        )
    }

    environment {
        // ── GHCR (public packages — only push needs auth) ───────────────────
        GHCR_REGISTRY  = 'ghcr.io'
        GHCR_OWNER     = credentials('ghcr-owner')        // Jenkins secret: GitHub username/org (lowercase)
        GHCR_PAT       = credentials('ghcr-pat')          // Jenkins secret: PAT with write:packages — used ONLY for pushing

        // ── AWS settings (used when CLOUD_PROVIDER == AWS) ──────────────────
        AWS_REGION         = 'us-east-1'
        AWS_EKS_CLUSTER    = 'travel-app-eks'

        // ── GCP settings (used when CLOUD_PROVIDER == GCP) ──────────────────
        GCP_REGION         = 'us-central1'
        GCP_PROJECT_ID     = credentials('gcp-project-id')
        GCP_GKE_CLUSTER    = 'travel-app-gke'

        SERVICES = 'hotel-service flight-service travel-service cancellation-function'
    }

    stages {

        stage('Checkout') {
            steps { checkout scm }
        }

        // ─────────────────────────────────────────────────────────────────────
        // Image registry is ALWAYS GHCR. Only the K8s overlay varies by cloud.
        // ─────────────────────────────────────────────────────────────────────
        stage('Configure') {
            steps {
                script {
                    env.IMAGE_REGISTRY = "${env.GHCR_REGISTRY}/${env.GHCR_OWNER}"

                    if (params.CLOUD_PROVIDER == 'AWS') {
                        env.K8S_OVERLAY = 'k8s/aws'
                        echo "→ Deployment target: AWS EKS (${env.AWS_EKS_CLUSTER}, region ${env.AWS_REGION})"
                    } else {
                        env.K8S_OVERLAY = 'k8s/gcp'
                        echo "→ Deployment target: GCP GKE (${env.GCP_GKE_CLUSTER}, region ${env.GCP_REGION})"
                    }

                    echo "→ Image registry: ${env.IMAGE_REGISTRY} (public)"
                    echo "→ Image tag:      ${params.IMAGE_TAG}"
                }
            }
        }

        stage('Build & Unit Test') {
            when { expression { return params.BUILD_AND_PUSH } }
            steps {
                sh 'mvn -B clean verify'
            }
            post {
                always {
                    junit allowEmptyResults: true,
                          testResults: '**/target/surefire-reports/*.xml'
                }
            }
        }

        // ─────────────────────────────────────────────────────────────────────
        // Build, tag, and push all 4 images to public GHCR.
        // PAT is needed here because PUSHING always requires auth, even to
        // public packages. PULLING from a public package needs no auth.
        // ─────────────────────────────────────────────────────────────────────
        stage('Build & Push Images to GHCR') {
            when { expression { return params.BUILD_AND_PUSH } }
            steps {
                script {
                    sh """
                        echo "\${GHCR_PAT}" | docker login ${env.GHCR_REGISTRY} \
                            -u "\${GHCR_OWNER}" --password-stdin
                    """

                    for (svc in env.SERVICES.split()) {
                        sh """
                            echo "Building ${svc}:${params.IMAGE_TAG}"
                            docker build -t ${env.IMAGE_REGISTRY}/${svc}:${params.IMAGE_TAG} ./${svc}
                            docker tag  ${env.IMAGE_REGISTRY}/${svc}:${params.IMAGE_TAG} \
                                        ${env.IMAGE_REGISTRY}/${svc}:latest

                            echo "Pushing ${svc} to GHCR"
                            docker push ${env.IMAGE_REGISTRY}/${svc}:${params.IMAGE_TAG}
                            docker push ${env.IMAGE_REGISTRY}/${svc}:latest
                        """
                    }

                    echo "ℹ️  Reminder: each NEW package is private by default."
                    echo "    First-time setup: GitHub → your packages → each package → 'Change visibility' → Public"
                    echo "    Once public, EKS/GKE nodes can pull without any pull secret."
                }
            }
        }

        // ─────────────────────────────────────────────────────────────────────
        // Set up kubectl context for the chosen cloud.
        // ─────────────────────────────────────────────────────────────────────
        stage('Configure Kubectl') {
            when { expression { return params.DEPLOY } }
            steps {
                script {
                    if (params.CLOUD_PROVIDER == 'AWS') {
                        withCredentials([[$class: 'AmazonWebServicesCredentialsBinding',
                                          credentialsId: 'aws-credentials']]) {
                            sh """
                                aws eks update-kubeconfig \
                                    --region ${env.AWS_REGION} \
                                    --name ${env.AWS_EKS_CLUSTER}
                            """
                        }
                    } else {
                        withCredentials([file(credentialsId: 'gcp-service-account',
                                              variable: 'GCP_KEY_FILE')]) {
                            sh """
                                gcloud auth activate-service-account --key-file=\${GCP_KEY_FILE}
                                gcloud container clusters get-credentials ${env.GCP_GKE_CLUSTER} \
                                    --region ${env.GCP_REGION} \
                                    --project ${env.GCP_PROJECT_ID}
                            """
                        }
                    }
                }
            }
        }

        // ─────────────────────────────────────────────────────────────────────
        // Apply manifests. Common first, then cloud overlay, then function.
        // No pull secret needed — images are public.
        // ─────────────────────────────────────────────────────────────────────
        stage('Deploy to Kubernetes') {
            when { expression { return params.DEPLOY } }
            steps {
                sh """
                    export IMAGE_REGISTRY=${env.IMAGE_REGISTRY}
                    export IMAGE_TAG=${params.IMAGE_TAG}

                    # 1. Common manifests (namespace, configmap, deployments, services, HPA)
                    for f in k8s/common/*.yaml; do
                        envsubst < \$f | kubectl apply -f -
                    done

                    # 2. Cloud-specific overlay (ingress + storage class)
                    for f in ${env.K8S_OVERLAY}/*.yaml; do
                        [ "\${f##*.}" = "md" ] && continue
                        envsubst < \$f | kubectl apply -f -
                    done

                    # 3. Cancellation function — Knative preferred, fallback to Deployment
                    if [ "${params.USE_KNATIVE}" = "true" ]; then
                        envsubst < k8s/functions/cancellation-function-knative.yaml | kubectl apply -f -
                    else
                        envsubst < k8s/functions/cancellation-function-deployment.yaml | kubectl apply -f -
                    fi

                    kubectl -n travel-app rollout status deployment/hotel-service  --timeout=5m
                    kubectl -n travel-app rollout status deployment/flight-service --timeout=5m
                    kubectl -n travel-app rollout status deployment/travel-service --timeout=5m
                """
            }
        }

        stage('Smoke Test') {
            when { expression { return params.DEPLOY } }
            steps {
                sh '''
                    echo "═══ Pods ═══"
                    kubectl -n travel-app get pods

                    echo "═══ Services ═══"
                    kubectl -n travel-app get svc

                    echo "═══ Ingress ═══"
                    kubectl -n travel-app get ingress || true
                '''
            }
        }
    }

    post {
        success {
            echo "✅ ${params.IMAGE_TAG} deployed to ${params.CLOUD_PROVIDER} from public GHCR"
        }
        failure {
            echo "❌ Pipeline failed at stage ${env.STAGE_NAME}"
        }
        always {
            sh 'docker logout ghcr.io || true'
            sh 'docker system prune -f || true'
        }
    }
}
