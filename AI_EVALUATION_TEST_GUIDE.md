# AI 평가 서비스 테스트 가이드

## 📋 목차
1. [서비스 개요](#서비스-개요)
2. [로컬 환경 설정](#로컬-환경-설정)
3. [Postman을 활용한 API 테스트](#postman을-활용한-api-테스트)
4. [S3 목업 데이터 테스트](#s3-목업-데이터-테스트)
5. [실제 S3 연동 설정](#실제-s3-연동-설정)
6. [문제 해결](#문제-해결)

## 🎯 서비스 개요

DevOps 교육 플랫폼의 AI 평가 서비스입니다. 학습자가 제출한 코드를 AI(Gemini)가 분석하여 종합적인 피드백을 제공합니다.

### 주요 기능
- **실제 실행 데이터 기반 평가**: S3에 저장된 명령어 로그, 리소스 메트릭을 분석
- **목표 지향 평가**: 미션 목표와 체크리스트를 바탕으로 달성도 평가  
- **DevOps 채점관 모드**: 정확성, 효율성, 품질을 기준으로 구체적 피드백 제공

## 🔧 로컬 환경 설정

### 1. 애플리케이션 실행

```bash
# 애플리케이션 빌드 및 실행
./gradlew bootRun

# 또는 JAR 파일로 실행
./gradlew build
java -jar build/libs/BE-AI-evaluation-service-*.jar
```

### 2. 환경 변수 설정

`.env` 파일 또는 환경 변수로 설정:

```bash
# Gemini API 키 (필수)
GEMINI_API_KEY=your_gemini_api_key_here

# AWS S3 설정 (선택적, 목업 모드에서는 불필요)
AWS_ACCESS_KEY=your_aws_access_key
AWS_SECRET_KEY=your_aws_secret_key
```

### 3. 서비스 상태 확인

```bash
# Health Check
curl http://localhost:8080/api/test/health

# 데이터베이스 상태
curl http://localhost:8080/api/test/database/status
```

## 📮 Postman을 활용한 API 테스트

### 1. 테스트 데이터 자동 생성

완전한 테스트 데이터(코드, S3 URL, 통계 등)를 자동으로 생성합니다:

```bash
# Kubernetes 미션 테스트 데이터 생성
GET http://localhost:8080/api/test/create-full-evaluation-test/kubernetes

# Docker Compose 미션 테스트 데이터 생성  
GET http://localhost:8080/api/test/create-full-evaluation-test/docker-compose

# 일반 미션 테스트 데이터 생성
POST http://localhost:8080/api/test/create-full-evaluation-test/general
```

**응답 예시:**
```json
{
  "success": true,
  "message": "완전한 AI 평가 테스트 데이터가 생성되었습니다.",
  "data": {
    "mission_attempt_id": "kubernetes-test-1736681234567",
    "mission_type": "kubernetes",
    "mission_objective": "Kubernetes 클러스터에 nginx 애플리케이션을 배포하고...",
    "checklist_items": ["Deployment 리소스 생성", "Service 리소스 생성", ...],
    "s3_storage_url": "s3://devtrip-logs/missions/kubernetes-test-1736681234567/execution-data.zip",
    "s3_presigned_url": "https://devtrip-logs.s3.amazonaws.com/missions/kubernetes-mission-123/execution-data.zip?X-Amz-Expires=300&token=test123",
    "test_code": "apiVersion: apps/v1\nkind: Deployment\n...",
    "statistics": {
      "commandSuccessCount": 15,
      "commandFailureCount": 2,
      "topErrorMessages": ["Error: services \"nginx-service\" already exists", ...],
      "averageCpuUsage": 23.4,
      "maxCpuUsage": 67.8,
      "totalExecutionTime": 28500
    },
    "postman_curl": "curl -X POST http://localhost:8080/api/evaluation/start ..."
  }
}
```

### 2. AI 평가 실행

생성된 데이터의 `postman_curl`을 복사하여 실행하거나, 직접 JSON으로 요청:

```bash
curl -X POST http://localhost:8080/api/evaluation/start \
-H "Content-Type: application/json" \
-d '{
  "userId": "test-user-123",
  "missionId": "mission-kubernetes",
  "missionAttemptId": "kubernetes-test-1736681234567",
  "missionType": "kubernetes",
  "missionTitle": "AI 평가 테스트 미션",
  "code": "apiVersion: apps/v1\nkind: Deployment\nmetadata:\n  name: nginx-deployment\n...",
  "missionObjective": "Kubernetes 클러스터에 nginx 애플리케이션을 배포하고, 외부에서 접근 가능하도록 서비스를 설정하세요.",
  "checklist": ["Deployment 리소스 생성", "Service 리소스 생성", "Pod 상태 확인"],
  "s3StorageUrl": "s3://devtrip-logs/missions/kubernetes-test-1736681234567/execution-data.zip",
  "s3PreSignedUrl": "https://devtrip-logs.s3.amazonaws.com/missions/kubernetes-mission-123/execution-data.zip?X-Amz-Expires=300&token=test123",
  "statistics": {
    "commandSuccessCount": 15,
    "commandFailureCount": 2,
    "averageCpuUsage": 23.4,
    "totalExecutionTime": 28500
  }
}'
```

**응답:**
```json
{
  "success": true,
  "message": "AI 평가가 시작되었습니다. 완료까지 약 10-30초 소요됩니다.",
  "data": {
    "missionAttemptId": "kubernetes-test-1736681234567",
    "userId": "test-user-123",
    "status": "PROCESSING",
    "aiModelVersion": "gemini-1.5-pro",
    "createdAt": "2025-01-15T10:30:15"
  }
}
```

### 3. 평가 결과 조회

```bash
# 평가 결과 조회
GET http://localhost:8080/api/evaluation/kubernetes-test-1736681234567

# 사용자별 평가 이력
GET http://localhost:8080/api/evaluation/user/test-user-123/history

# 전체 평가 통계
GET http://localhost:8080/api/evaluation/stats
```

## 🗂️ S3 목업 데이터 테스트

### 목업 데이터 파일 위치
- `src/main/resources/mock-data/kubernetes-deployment-commands.json`
- `src/main/resources/mock-data/docker-compose-commands.json`

### 목업 S3 데이터 확인

```bash
# 목업 S3 데이터 조회 테스트
GET http://localhost:8080/api/test/mock-s3-data?preSignedUrl=https://devtrip-logs.s3.amazonaws.com/missions/kubernetes-mission-123/execution-data.zip

# 목업 캐시 초기화
DELETE http://localhost:8080/api/test/mock-cache
```

### 목업 데이터 구조

```json
{
  "mission_attempt_id": "kubernetes-mission-123",
  "execution_log": {
    "commands": [
      {
        "timestamp": "2025-01-15T10:30:15Z",
        "command": "kubectl apply -f deployment.yaml",
        "output": "deployment.apps/nginx-deployment created",
        "exit_code": 0,
        "duration_ms": 1250
      }
    ],
    "summary": {
      "total_commands": 8,
      "successful_commands": 7,
      "failed_commands": 1
    }
  },
  "resource_metrics": {
    "metrics": [
      {
        "timestamp": "2025-01-15T10:30:15Z",
        "cpu_usage_percent": 12.5,
        "memory_usage_mb": 512,
        "network_rx_bytes": 1024,
        "network_tx_bytes": 2048
      }
    ],
    "summary": {
      "avg_cpu_usage_percent": 33.56,
      "max_cpu_usage_percent": 67.8
    }
  }
}
```

## ☁️ 실제 S3 연동 설정

### 1. AWS 설정

```bash
# AWS CLI 설치 및 설정
aws configure
AWS Access Key ID: your_access_key
AWS Secret Access Key: your_secret_key  
Default region name: ap-northeast-2
Default output format: json

# S3 버킷 생성 (선택적)
aws s3 mb s3://devtrip-logs --region ap-northeast-2
```

### 2. 환경 변수 설정

```bash
# application.properties 또는 환경 변수
AWS_S3_REGION=ap-northeast-2
AWS_ACCESS_KEY=your_access_key
AWS_SECRET_KEY=your_secret_key
```

### 3. S3PreSignedUrlService 실제 구현

현재는 목업 구현이므로, 실제 운영에서는 AWS SDK를 사용:

```java
@Service
public class S3PreSignedUrlService {
    
    @Autowired
    private S3Client s3Client;
    
    public String generatePreSignedUrl(String bucketName, String objectKey) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
            .bucket(bucketName)
            .key(objectKey)
            .build();
            
        GetObjectPresignRequest getObjectPresignRequest = GetObjectPresignRequest.builder()
            .signatureDuration(Duration.ofMinutes(5))
            .getObjectRequest(getObjectRequest)
            .build();
            
        PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(getObjectPresignRequest);
        return presignedRequest.url().toString();
    }
}
```

### 4. 의존성 추가

```gradle
// build.gradle에 AWS SDK 의존성 추가
implementation 'software.amazon.awssdk:s3:2.20.56'
implementation 'software.amazon.awssdk:s3-presigner:2.20.56'
```

## 🔍 문제 해결

### 자주 발생하는 문제들

#### 1. Gemini API 키 오류
```bash
# 환경 변수 확인
echo $GEMINI_API_KEY

# API 키 테스트
curl -H "Content-Type: application/json" \
     -d '{"contents":[{"parts":[{"text":"Hello"}]}]}' \
     "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-pro:generateContent?key=YOUR_API_KEY"
```

#### 2. 데이터베이스 연결 문제
```bash
# H2 콘솔 접속: http://localhost:8080/h2-console
# JDBC URL: jdbc:h2:mem:testdb
# User Name: sa
# Password: (비워둠)
```

#### 3. 평가 상태가 PROCESSING에서 멈춤
```bash
# 로그 확인
tail -f logs/application.log

# 강제로 평가 완료 처리 (테스트용)
POST http://localhost:8080/api/test/evaluation/mock/your-mission-attempt-id
```

### 로그 레벨 조정

```bash
# 디버그 로그 활성화  
PUT http://localhost:8080/api/test/logging/level/DEBUG

# 기본 로그 레벨로 복원
PUT http://localhost:8080/api/test/logging/level/INFO
```

### 테스트 데이터 정리

```bash
# 모든 테스트 데이터 삭제
DELETE http://localhost:8080/api/test/data/all

# 특정 사용자 데이터만 삭제
DELETE http://localhost:8080/api/test/data/user/test-user-123
```

## 🎪 고급 테스트 시나리오

### 1. 대량 평가 테스트

```bash
# 여러 미션 타입으로 동시 평가
for mission_type in kubernetes docker-compose terraform; do
  curl -X POST http://localhost:8080/api/test/create-full-evaluation-test/$mission_type
done
```

### 2. 실패 시나리오 테스트

```bash
# 잘못된 Pre-signed URL로 테스트
curl -X POST http://localhost:8080/api/evaluation/start \
-d '{
  "missionAttemptId": "invalid-url-test",
  "s3PreSignedUrl": "https://invalid-url"
  ...
}'
```

### 3. 성능 테스트

```bash
# Apache Bench로 부하 테스트
ab -n 100 -c 10 -T application/json -p test-payload.json \
   http://localhost:8080/api/evaluation/start
```

---

## 🤝 기여 가이드

1. 새로운 미션 타입 추가 시 `generateTestCode()` 메서드에 케이스 추가
2. 목업 데이터 추가 시 `src/main/resources/mock-data/` 디렉토리에 JSON 파일 생성
3. API 변경 시 이 문서 업데이트 필수

## 📞 지원

- 이슈 제보: [GitHub Issues](https://github.com/your-repo/issues)
- 문서 개선: Pull Request 환영합니다!

---
*이 문서는 AI 평가 서비스 v2.0 기준으로 작성되었습니다. (S3 통합 버전)*