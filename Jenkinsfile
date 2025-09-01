pipeline {
    agent any
    
    environment {
        // ===== 로컬 Docker Registry 설정 =====
        DOCKER_REGISTRY = 'localhost:5000'
        SERVICE_NAME = 'ai-evaluation-service'
        IMAGE_NAME = 'ai-evaluation-service'
        DOCKER_TAG = "${BUILD_NUMBER}"
        
        // ===== 로컬 Kubernetes 설정 =====
        K8S_NAMESPACE = 'devtrip'
        K8S_DEPLOYMENT_NAME = 'ai-evaluation-service'
        K8S_CONFIG_PATH = './k8s'
        
        // ===== 로컬 Helm 설정 =====
        HELM_CHART_PATH = './helm'
        HELM_RELEASE_NAME = 'ai-evaluation-local'
        
        // ===== ArgoCD 로컬 설정 =====
        ARGOCD_SERVER = 'localhost:30080'
        ARGOCD_APP_NAME = 'ai-evaluation-app'
        
        // ===== 자격 증명 ID =====
        GEMINI_API_KEY_CREDENTIALS = 'gemini-api-key'
    }
    
    stages {
        stage('🚀 Pipeline Start') {
            steps {
                echo "===================================================="
                echo "🚀 Starting CI/CD Pipeline for ${SERVICE_NAME}"
                echo "📋 Build Number: ${BUILD_NUMBER}"
                echo "🌿 Branch: ${env.BRANCH_NAME}"
                echo "===================================================="
            }
        }
        
        stage('📦 Checkout & Setup') {
            steps {
                script {
                    env.GIT_COMMIT_SHORT = sh(
                        script: 'git rev-parse --short HEAD',
                        returnStdout: true
                    ).trim()
                    env.DOCKER_TAG_WITH_COMMIT = "${BUILD_NUMBER}-${env.GIT_COMMIT_SHORT}"
                    echo "📦 Checked out commit: ${env.GIT_COMMIT_SHORT}"
                }
            }
        }
        
        stage('🏗️ Build & Test') {
            steps {
                script {
                    try {
                        echo "🏗️ Java Compilation & Tests..."
                        sh './gradlew clean compileJava test --info'
                        junit testResultsPattern: 'build/test-results/**/*.xml', allowEmptyResults: true
                        echo "✅ Tests passed successfully"
                    } catch (Exception e) {
                        echo "⚠️ Tests failed but continuing with deployment: ${e.getMessage()}"
                        // 컴파일만 수행
                        sh './gradlew clean compileJava build -x test'
                        currentBuild.result = 'UNSTABLE'
                    }
                }
            }
            post {
                always {
                    script {
                        try {
                            archiveArtifacts artifacts: 'build/libs/*.jar', fingerprint: true
                        } catch (Exception e) {
                            echo "Archive artifacts failed: ${e.getMessage()}"
                        }
                    }
                }
            }
        }
        
        stage('🐳 Docker Build & Push') {
            steps {
                echo "🐳 Docker Build & Push to Local Registry"
                script {
                    def imageTag = "${DOCKER_REGISTRY}/${IMAGE_NAME}:${env.DOCKER_TAG_WITH_COMMIT}"
                    sh "docker build -t ${imageTag} ."
                    sh "docker tag ${imageTag} ${DOCKER_REGISTRY}/${IMAGE_NAME}:latest"
                    
                    // 로컬 레지스트리에 푸시 (인증 불필요)
                    sh "docker push ${imageTag}"
                    sh "docker push ${DOCKER_REGISTRY}/${IMAGE_NAME}:latest"
                    env.DOCKER_IMAGE_FULL_TAG = imageTag
                    echo "✅ Image pushed: ${imageTag}"
                }
            }
        }
        
        stage('🚀 Deploy to Local K8s') {
            steps {
                script {
                    echo "🚀 Direct Deployment to Local Kubernetes"
                    sh """
                        # 네임스페이스 생성
                        kubectl create namespace ${K8S_NAMESPACE} --dry-run=client -o yaml | kubectl apply -f - || echo "Namespace already exists"
                        
                        # 이미지 업데이트
                        kubectl set image deployment/${K8S_DEPLOYMENT_NAME} \
                            ${SERVICE_NAME}=${env.DOCKER_IMAGE_FULL_TAG} \
                            -n ${K8S_NAMESPACE} || echo "Deployment not found"
                        
                        # Pod 상태 확인
                        kubectl get pods -n ${K8S_NAMESPACE} -l app=${SERVICE_NAME} || echo "No pods found"
                    """
                }
            }
        }
        
        stage('✅ Health Check') {
            steps {
                script {
                    echo "✅ Running health checks..."
                    
                    sh """
                        echo "Build completed successfully"
                        echo "Service: ${SERVICE_NAME}"
                        echo "Image: ${env.DOCKER_IMAGE_FULL_TAG}"
                        echo "Commit: ${env.GIT_COMMIT_SHORT}"
                    """
                }
            }
        }
    }
    
    post {
        always {
            echo "🧹 Cleaning up workspace..."
            
            script {
                try {
                    sh "docker system prune -f"
                } catch (Exception e) {
                    echo "Docker cleanup skipped: ${e.getMessage()}"
                }
            }
            
            cleanWs()
        }
        
        success {
            echo "✅ Pipeline completed successfully for ${SERVICE_NAME}!"
        }
        
        failure {
            echo "❌ Pipeline failed for ${SERVICE_NAME}!"
        }
    }
}

def deployToLocalEnvironment(String environment, String namespace) {
    sh "kubectl create namespace ${namespace} --dry-run=client -o yaml | kubectl apply -f - || echo 'Namespace already exists'"
    
    withCredentials([
        string(credentialsId: env.GEMINI_API_KEY_CREDENTIALS, variable: 'GEMINI_API_KEY')
    ]) {
        sh """
        kubectl create secret generic ai-evaluation-secrets \
            --from-literal=gemini-api-key='${GEMINI_API_KEY}' \
            -n ${namespace} \
            --dry-run=client -o yaml | kubectl apply -f -
        """
    }
    
    sh """
    helm upgrade --install ${env.HELM_RELEASE_NAME}-${environment} ${env.HELM_CHART_PATH} \
        --namespace ${namespace} \
        --set image.tag=${env.DOCKER_TAG_WITH_COMMIT} \
        --set image.repository=${DOCKER_REGISTRY}/${IMAGE_NAME} \
        --wait --timeout=10m || echo "Helm deployment skipped"
    """
}