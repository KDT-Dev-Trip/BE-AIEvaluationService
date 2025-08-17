pipeline {
    agent any
    
    environment {
        DOCKER_REGISTRY = 'your-registry.com'  // Replace with your registry
        IMAGE_NAME = 'be-ai-evaluation-service'
        DOCKER_REPO = "${DOCKER_REGISTRY}/${IMAGE_NAME}"
        GIT_REPO_MANIFESTS = 'https://github.com/your-org/k8s-manifests.git'  // Replace with your manifests repo
        GIT_BRANCH = 'main'
        
        // Docker Hub credentials (configure in Jenkins)
        DOCKER_CREDENTIALS = credentials('docker-hub-credentials')
        GIT_CREDENTIALS = credentials('git-credentials')
        
        // SonarQube
        SONAR_SCANNER_HOME = tool 'SonarQubeScanner'
        SONAR_PROJECT_KEY = 'be-ai-evaluation-service'
    }
    
    tools {
        jdk 'JDK-17'
        gradle 'Gradle-8'
    }
    
    stages {
        stage('Checkout') {
            steps {
                checkout scm
                script {
                    env.GIT_COMMIT = sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
                    env.SHORT_COMMIT = env.GIT_COMMIT.take(8)
                    env.BUILD_TAG = "${env.BUILD_NUMBER}-${env.SHORT_COMMIT}"
                    env.IMAGE_TAG = "${env.DOCKER_REPO}:${env.BUILD_TAG}"
                    env.IMAGE_LATEST = "${env.DOCKER_REPO}:latest"
                }
            }
        }
        
        stage('Build & Test') {
            parallel {
                stage('Gradle Build') {
                    steps {
                        sh '''
                            ./gradlew clean build --no-daemon
                            ./gradlew test --no-daemon
                        '''
                    }
                    post {
                        always {
                            publishTestResults testResultsPattern: 'build/test-results/test/*.xml'
                            publishHTML([
                                allowMissing: false,
                                alwaysLinkToLastBuild: true,
                                keepAll: true,
                                reportDir: 'build/reports/tests/test',
                                reportFiles: 'index.html',
                                reportName: 'Test Report'
                            ])
                        }
                    }
                }
                
                stage('Code Quality') {
                    steps {
                        script {
                            withSonarQubeEnv('SonarQube') {
                                sh """
                                    ${SONAR_SCANNER_HOME}/bin/sonar-scanner \
                                    -Dsonar.projectKey=${SONAR_PROJECT_KEY} \
                                    -Dsonar.sources=src/main/java \
                                    -Dsonar.tests=src/test/java \
                                    -Dsonar.java.binaries=build/classes \
                                    -Dsonar.junit.reportPaths=build/test-results/test/*.xml \
                                    -Dsonar.jacoco.reportPaths=build/jacoco/test.exec
                                """
                            }
                        }
                    }
                }
            }
        }
        
        stage('Quality Gate') {
            steps {
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }
        
        stage('Security Scan') {
            parallel {
                stage('Dependency Check') {
                    steps {
                        sh './gradlew dependencyCheckAnalyze --no-daemon'
                    }
                    post {
                        always {
                            publishHTML([
                                allowMissing: false,
                                alwaysLinkToLastBuild: true,
                                keepAll: true,
                                reportDir: 'build/reports',
                                reportFiles: 'dependency-check-report.html',
                                reportName: 'Dependency Check Report'
                            ])
                        }
                    }
                }
                
                stage('Trivy Scan') {
                    steps {
                        sh '''
                            # Install trivy if not available
                            if ! command -v trivy &> /dev/null; then
                                wget -qO - https://aquasecurity.github.io/trivy-repo/deb/public.key | sudo apt-key add -
                                echo "deb https://aquasecurity.github.io/trivy-repo/deb generic main" | sudo tee -a /etc/apt/sources.list
                                sudo apt-get update
                                sudo apt-get install trivy
                            fi
                            
                            # Scan filesystem
                            trivy fs --exit-code 0 --severity HIGH,CRITICAL --format table .
                        '''
                    }
                }
            }
        }
        
        stage('Build Docker Image') {
            steps {
                script {
                    docker.withRegistry('', 'docker-hub-credentials') {
                        def image = docker.build("${env.IMAGE_TAG}")
                        
                        // Security scan on built image
                        sh "trivy image --exit-code 0 --severity HIGH,CRITICAL ${env.IMAGE_TAG}"
                        
                        // Push images
                        image.push()
                        image.push('latest')
                    }
                }
            }
        }
        
        stage('Update Manifests') {
            steps {
                script {
                    // Clone manifests repository
                    sh """
                        rm -rf k8s-manifests
                        git clone ${GIT_REPO_MANIFESTS} k8s-manifests
                        cd k8s-manifests
                        
                        # Update image tag in deployment manifest
                        sed -i 's|image: .*|image: ${env.IMAGE_TAG}|g' manifests/deployment.yaml
                        
                        # Commit and push changes
                        git config user.name "Jenkins CI"
                        git config user.email "jenkins@yourdomain.com"
                        git add manifests/deployment.yaml
                        git commit -m "Update image to ${env.IMAGE_TAG} - Build ${env.BUILD_NUMBER}"
                        git push origin ${GIT_BRANCH}
                    """
                }
            }
        }
        
        stage('Deploy to Staging') {
            when {
                branch 'develop'
            }
            steps {
                script {
                    // Trigger ArgoCD sync for staging
                    sh """
                        # Install ArgoCD CLI if not available
                        if ! command -v argocd &> /dev/null; then
                            curl -sSL -o /usr/local/bin/argocd https://github.com/argoproj/argo-cd/releases/latest/download/argocd-linux-amd64
                            chmod +x /usr/local/bin/argocd
                        fi
                        
                        # Login and sync
                        argocd login ${ARGOCD_SERVER} --username ${ARGOCD_USERNAME} --password ${ARGOCD_PASSWORD} --insecure
                        argocd app sync be-ai-evaluation-service-staging
                        argocd app wait be-ai-evaluation-service-staging --timeout 600
                    """
                }
            }
        }
        
        stage('Deploy to Production') {
            when {
                branch 'main'
            }
            steps {
                input message: 'Deploy to Production?', ok: 'Deploy'
                script {
                    // Trigger ArgoCD sync for production
                    sh """
                        argocd login ${ARGOCD_SERVER} --username ${ARGOCD_USERNAME} --password ${ARGOCD_PASSWORD} --insecure
                        argocd app sync be-ai-evaluation-service-prod
                        argocd app wait be-ai-evaluation-service-prod --timeout 600
                    """
                }
            }
        }
    }
    
    post {
        always {
            // Archive artifacts
            archiveArtifacts artifacts: 'build/libs/*.jar', fingerprint: true
            
            // Clean workspace
            cleanWs()
        }
        
        success {
            script {
                if (env.BRANCH_NAME == 'main' || env.BRANCH_NAME == 'develop') {
                    slackSend(
                        channel: '#deployments',
                        color: 'good',
                        message: """
                        ✅ *Deployment Successful*
                        *Project:* ${env.JOB_NAME}
                        *Branch:* ${env.BRANCH_NAME}
                        *Build:* ${env.BUILD_NUMBER}
                        *Image:* ${env.IMAGE_TAG}
                        *Duration:* ${currentBuild.durationString}
                        """
                    )
                }
            }
        }
        
        failure {
            slackSend(
                channel: '#deployments',
                color: 'danger',
                message: """
                ❌ *Deployment Failed*
                *Project:* ${env.JOB_NAME}
                *Branch:* ${env.BRANCH_NAME}
                *Build:* ${env.BUILD_NUMBER}
                *Duration:* ${currentBuild.durationString}
                *Console:* ${env.BUILD_URL}console
                """
            )
        }
        
        unstable {
            slackSend(
                channel: '#deployments',
                color: 'warning',
                message: """
                ⚠️ *Deployment Unstable*
                *Project:* ${env.JOB_NAME}
                *Branch:* ${env.BRANCH_NAME}
                *Build:* ${env.BUILD_NUMBER}
                """
            )
        }
    }
}