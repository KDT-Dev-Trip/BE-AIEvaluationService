pipeline {
    agent any
    
    environment {
        DOCKER_REGISTRY = 'your-registry.com'
        DOCKER_IMAGE_NAME = 'be-ai-evaluation-service'
        DOCKER_TAG = "${BUILD_NUMBER}"
        K8S_NAMESPACE = 'devtrip'
        K8S_DEPLOYMENT_NAME = 'be-ai-evaluation-service'
        HELM_CHART_PATH = './helm'
        HELM_RELEASE_NAME = 'ai-evaluation'
        DEV_NAMESPACE = 'devtrip-dev'
        STAGING_NAMESPACE = 'devtrip-staging'
        PROD_NAMESPACE = 'devtrip-prod'
        DOCKER_REGISTRY_CREDENTIALS = 'docker-registry-credentials'
        K8S_CREDENTIALS = 'k8s-credentials'
        GEMINI_API_KEY_CREDENTIALS = 'gemini-api-key'
        AWS_CREDENTIALS = 'aws-credentials'
    }
    
    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        disableConcurrentBuilds()
        timeout(time: 30, unit: 'MINUTES')
    }
    
    stages {
        stage('Checkout') {
            steps {
                echo '=== Git Repository Checkout ==='
                checkout scm
                script {
                    env.GIT_COMMIT_SHORT = sh(
                        script: 'git rev-parse --short HEAD',
                        returnStdout: true
                    ).trim()
                    env.DOCKER_TAG_WITH_COMMIT = "${BUILD_NUMBER}-${env.GIT_COMMIT_SHORT}"
                }
            }
        }
        
        stage('Build & Test') {
            steps {
                echo '=== Java Compilation & Tests ==='
                sh './gradlew clean compileJava test --info'
                publishTestResults testResultsPattern: 'build/test-results/**/*.xml'
            }
            post {
                always {
                    archiveArtifacts artifacts: 'build/libs/*.jar', fingerprint: true
                }
            }
        }
        
        stage('Docker Build & Push') {
            steps {
                echo '=== Docker Build & Push ==='
                script {
                    withCredentials([usernamePassword(
                        credentialsId: env.DOCKER_REGISTRY_CREDENTIALS,
                        usernameVariable: 'REGISTRY_USERNAME',
                        passwordVariable: 'REGISTRY_PASSWORD'
                    )]) {
                        def imageTag = "${DOCKER_REGISTRY}/${DOCKER_IMAGE_NAME}:${env.DOCKER_TAG_WITH_COMMIT}"
                        sh "docker build -t ${imageTag} ."
                        sh "docker push ${imageTag}"
                        env.DOCKER_IMAGE_FULL_TAG = imageTag
                    }
                }
            }
        }
        
        stage('Deploy') {
            parallel {
                stage('Deploy to Dev') {
                    when { branch 'develop' }
                    steps {
                        deployToEnvironment('dev', env.DEV_NAMESPACE)
                    }
                }
                stage('Deploy to Staging') {
                    when { branch 'main' }
                    steps {
                        deployToEnvironment('staging', env.STAGING_NAMESPACE)
                    }
                }
            }
        }
        
    }
    
    post {
        always {
            cleanWs()
        }
        success {
            echo 'Pipeline completed successfully'
        }
        failure {
            echo 'Pipeline failed'
        }
    }
}

def deployToEnvironment(String environment, String namespace) {
    withCredentials([kubeconfigFile(credentialsId: env.K8S_CREDENTIALS, variable: 'KUBECONFIG')]) {
        sh "kubectl create namespace ${namespace} --dry-run=client -o yaml | kubectl apply -f -"
        
        withCredentials([
            string(credentialsId: env.GEMINI_API_KEY_CREDENTIALS, variable: 'GEMINI_API_KEY'),
            usernamePassword(credentialsId: env.AWS_CREDENTIALS, usernameVariable: 'AWS_ACCESS_KEY', passwordVariable: 'AWS_SECRET_KEY')
        ]) {
            sh """
            kubectl create secret generic ai-evaluation-secrets \\
                --from-literal=gemini-api-key='${GEMINI_API_KEY}' \\
                --from-literal=aws-access-key='${AWS_ACCESS_KEY}' \\
                --from-literal=aws-secret-key='${AWS_SECRET_KEY}' \\
                -n ${namespace} \\
                --dry-run=client -o yaml | kubectl apply -f -
            """
        }
        
        sh """
        helm upgrade --install ${env.HELM_RELEASE_NAME}-${environment} ${env.HELM_CHART_PATH} \\
            --namespace ${namespace} \\
            --set image.tag=${env.DOCKER_TAG_WITH_COMMIT} \\
            --set image.repository=${DOCKER_REGISTRY}/${DOCKER_IMAGE_NAME} \\
            --wait --timeout=10m
        """
    }
}