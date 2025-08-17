# 🚀 실제 배포 가이드

## 1. 사전 준비 사항

### 1.1 인프라 준비
```bash
# Kubernetes 클러스터 (다음 중 하나)
# - AWS EKS
# - Google GKE  
# - Azure AKS
# - On-premise Kubernetes

# Docker Registry (다음 중 하나)
# - Docker Hub
# - AWS ECR
# - Google Container Registry
# - Harbor
```

### 1.2 도구 설치
```bash
# kubectl 설치
curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
sudo install -o root -g root -m 0755 kubectl /usr/local/bin/kubectl

# ArgoCD CLI 설치
curl -sSL -o argocd-linux-amd64 https://github.com/argoproj/argo-cd/releases/latest/download/argocd-linux-amd64
sudo install -m 555 argocd-linux-amd64 /usr/local/bin/argocd
```

## 2. GitOps 레포지토리 설정

### 2.1 매니페스트 레포지토리 생성
```bash
# 새 레포지토리 생성 (예: k8s-manifests)
mkdir k8s-manifests
cd k8s-manifests
git init

# 디렉토리 구조 생성
mkdir -p {be-ai-evaluation-service/staging,be-ai-evaluation-service/production}

# 매니페스트 복사
cp ../BE-AI-evaluation-service/k8s/* be-ai-evaluation-service/production/
cp ../BE-AI-evaluation-service/k8s/* be-ai-evaluation-service/staging/
```

### 2.2 환경별 설정 조정
```bash
# Staging 환경 (더 적은 리소스)
# be-ai-evaluation-service/staging/deployment.yaml 수정
replicas: 2  # 3 → 2로 변경
resources:
  requests:
    memory: "256Mi"  # 512Mi → 256Mi
    cpu: "125m"      # 250m → 125m
  limits:
    memory: "512Mi"  # 1Gi → 512Mi
    cpu: "250m"      # 500m → 250m
```

## 3. 시크릿 설정

### 3.1 실제 API 키 준비
```bash
# 필요한 API 키들
GEMINI_API_KEY="your-real-gemini-api-key"
AWS_ACCESS_KEY="your-real-aws-access-key"
AWS_SECRET_KEY="your-real-aws-secret-key"
```

### 3.2 Kubernetes 시크릿 생성
```bash
# Base64 인코딩
echo -n "your-real-gemini-api-key" | base64
echo -n "your-real-aws-access-key" | base64
echo -n "your-real-aws-secret-key" | base64

# 시크릿 파일 업데이트 (k8s/secret.yaml)
# 인코딩된 값으로 교체

# Docker Registry 시크릿 생성
kubectl create secret docker-registry docker-registry-secret \
  --docker-server=your-registry.com \
  --docker-username=your-username \
  --docker-password=your-password \
  --docker-email=your-email@domain.com \
  -n be-ai-evaluation
```

## 4. Jenkins 설정

### 4.1 Jenkins 서버 준비
```bash
# Docker로 Jenkins 실행 (테스트용)
docker run -d \
  --name jenkins \
  -p 8080:8080 \
  -p 50000:50000 \
  -v jenkins_home:/var/jenkins_home \
  -v /var/run/docker.sock:/var/run/docker.sock \
  jenkins/jenkins:lts-jdk17

# 초기 패스워드 확인
docker exec jenkins cat /var/jenkins_home/secrets/initialAdminPassword
```

### 4.2 필수 플러그인 설치
1. Jenkins 관리 → 플러그인 관리
2. 다음 플러그인 설치:
   - Docker Pipeline
   - Kubernetes
   - Git
   - SonarQube Scanner
   - Slack Notification
   - Blue Ocean

### 4.3 Jenkins 설정
```bash
# 1. Jenkins 관리 → Global Tool Configuration
# - JDK 17 설정
# - Gradle 8.x 설정  
# - Docker 설정

# 2. Jenkins 관리 → Manage Credentials
# - docker-hub-credentials (Username/Password)
# - git-credentials (Username/Token)
# - sonarqube-token (Secret text)
```

### 4.4 Jenkinsfile 환경 변수 수정
```groovy
# Jenkinsfile에서 다음 값들 수정
environment {
    DOCKER_REGISTRY = 'your-actual-registry.com'
    GIT_REPO_MANIFESTS = 'https://github.com/your-org/k8s-manifests.git'
    ARGOCD_SERVER = 'your-argocd-server.com'
    // ...
}
```

## 5. ArgoCD 설치 및 설정

### 5.1 ArgoCD 설치
```bash
# 네임스페이스 생성
kubectl create namespace argocd

# ArgoCD 설치
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml

# ArgoCD 서버 포트 포워딩 (테스트용)
kubectl port-forward svc/argocd-server -n argocd 8080:443

# 초기 패스워드 확인
argocd admin initial-password -n argocd
```

### 5.2 ArgoCD 설정
```bash
# ArgoCD 로그인
argocd login localhost:8080 --username admin --password <initial-password>

# 패스워드 변경
argocd account update-password

# Git 레포지토리 등록
argocd repo add https://github.com/your-org/k8s-manifests.git

# 프로젝트 및 애플리케이션 생성
kubectl apply -f argocd/appproject.yaml
kubectl apply -f argocd/application-staging.yaml
kubectl apply -f argocd/application-prod.yaml
```

## 6. 실제 배포 실행

### 6.1 첫 번째 배포
```bash
# 1. 소스 코드 푸시
git add .
git commit -m "feat: CI/CD pipeline setup"
git push origin main

# 2. Jenkins 파이프라인 실행
# Jenkins 웹 UI에서 빌드 실행 또는
# 자동 트리거 (webhook 설정 시)

# 3. ArgoCD에서 배포 확인
argocd app get be-ai-evaluation-service-prod
argocd app sync be-ai-evaluation-service-prod
```

### 6.2 배포 상태 확인
```bash
# Kubernetes 리소스 확인
kubectl get all -n be-ai-evaluation

# Pod 상태 확인
kubectl get pods -n be-ai-evaluation -w

# 로그 확인
kubectl logs -f deployment/be-ai-evaluation-service -n be-ai-evaluation

# 서비스 접근 테스트
kubectl port-forward svc/be-ai-evaluation-service 8080:80 -n be-ai-evaluation
curl http://localhost:8080/actuator/health
```

## 7. 도메인 및 인그레스 설정

### 7.1 도메인 설정
```bash
# DNS A 레코드 추가
# ai-evaluation.yourdomain.com → Load Balancer IP

# 인그레스 설정 파일에서 도메인 수정
# k8s/ingress.yaml
host: ai-evaluation.yourdomain.com
```

### 7.2 TLS 인증서 설정
```bash
# cert-manager 설치 (Let's Encrypt용)
kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.13.0/cert-manager.yaml

# ClusterIssuer 생성
kubectl apply -f - <<EOF
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: letsencrypt-prod
spec:
  acme:
    server: https://acme-v02.api.letsencrypt.org/directory
    email: your-email@domain.com
    privateKeySecretRef:
      name: letsencrypt-prod
    solvers:
    - http01:
        ingress:
          class: nginx
EOF
```

## 8. 모니터링 설정

### 8.1 Prometheus & Grafana 설치
```bash
# Helm 설치
curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash

# Prometheus Stack 설치
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm install prometheus prometheus-community/kube-prometheus-stack -n monitoring --create-namespace

# Grafana 접근
kubectl port-forward svc/prometheus-grafana 3000:80 -n monitoring
# admin / prom-operator
```

### 8.2 애플리케이션 메트릭 설정
```bash
# ServiceMonitor 생성
kubectl apply -f - <<EOF
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: be-ai-evaluation-service
  namespace: be-ai-evaluation
spec:
  selector:
    matchLabels:
      app: be-ai-evaluation-service
  endpoints:
  - port: http
    path: /actuator/prometheus
EOF
```

## 9. 운영 체크리스트

### 9.1 배포 전 체크리스트
- [ ] 모든 시크릿 값 업데이트 완료
- [ ] 도메인 DNS 설정 완료
- [ ] SSL 인증서 설정 완료
- [ ] 모니터링 대시보드 설정 완료
- [ ] 알람 규칙 설정 완료
- [ ] 백업 정책 설정 완료

### 9.2 배포 후 체크리스트
- [ ] Health check 엔드포인트 정상 응답
- [ ] Swagger UI 접근 가능
- [ ] 로그 정상 출력
- [ ] 메트릭 수집 정상
- [ ] 알람 테스트 완료
- [ ] 부하 테스트 완료

## 10. 트러블슈팅

### 10.1 일반적인 문제들
```bash
# Pod가 시작되지 않는 경우
kubectl describe pod <pod-name> -n be-ai-evaluation
kubectl logs <pod-name> -n be-ai-evaluation

# 시크릿 관련 문제
kubectl get secrets -n be-ai-evaluation
kubectl describe secret be-ai-evaluation-secrets -n be-ai-evaluation

# 인그레스 문제
kubectl get ingress -n be-ai-evaluation
kubectl describe ingress be-ai-evaluation-service-ingress -n be-ai-evaluation

# ArgoCD 동기화 문제
argocd app get be-ai-evaluation-service-prod
argocd app diff be-ai-evaluation-service-prod
```

### 10.2 롤백 방법
```bash
# ArgoCD 롤백
argocd app rollback be-ai-evaluation-service-prod

# Kubernetes 롤백
kubectl rollout undo deployment/be-ai-evaluation-service -n be-ai-evaluation

# 특정 리비전으로 롤백
kubectl rollout undo deployment/be-ai-evaluation-service --to-revision=2 -n be-ai-evaluation
```

## 11. 보안 고려사항

### 11.1 네트워크 보안
- NetworkPolicy로 Pod 간 통신 제한
- 인그레스에 rate limiting 설정
- WAF (Web Application Firewall) 적용 고려

### 11.2 컨테이너 보안
- 정기적인 이미지 취약점 스캔
- 비루트 사용자로 실행
- 읽기전용 파일시스템 사용

### 11.3 시크릿 관리
- 시크릿 로테이션 정책 수립
- 외부 시크릿 관리 도구 연동 (AWS Secrets Manager, HashiCorp Vault)

이제 실제 배포가 가능합니다! 🚀