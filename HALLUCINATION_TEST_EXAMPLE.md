# AI 할루시네이션 테스트 예제

## 구현 완료 사항

✅ **Mock S3 데이터 통합**: S3 더미 키 대신 실제 목업 데이터를 Gemini 프롬프트에 직접 포함
✅ **실행 데이터 분석**: 명령어 실행 결과, 에러 메시지, 리소스 사용량을 AI가 분석
✅ **할루시네이션 검증 준비**: AI가 실제 데이터를 기반으로 정확한 평가를 수행하는지 확인 가능

## 테스트 시나리오

### 1. 성공적인 Kubernetes 배포 시나리오
- **파일**: `kubernetes-deployment-commands.json`
- **내용**: 정상적인 nginx 배포 및 서비스 노출 과정
- **예상 AI 평가**: 높은 정확성 점수, 적절한 피드백

### 2. 문제가 있는 Kubernetes 배포 시나리오  
- **파일**: `problematic-kubernetes-commands.json`
- **내용**: 
  - ValidationError (unknown field "memory_limit")
  - ImagePullBackOff (네트워크 연결 실패)
  - NodePort 접근 불가 문제
- **예상 AI 평가**: 낮은 점수, 구체적인 문제점 지적

## AI 평가 프롬프트 구조

```
[Mission Info]
- missionAttemptId: xxx
- goal: Kubernetes 클러스터에 nginx 배포
- checklist: [...]

[Data Access]  
- s3_address: s3://bucket/path
- presigned_urls: https://s3.../data.json

[S3 Execution Data]
위 Pre-signed URL에서 읽어온 실제 실행 데이터:
```json
{
  "execution_log": {
    "commands": [...],
    "summary": {...}
  },
  "resource_metrics": {...}
}
```

**중요: 위 실행 데이터를 바탕으로 실제 실행 과정을 분석해주세요.**
```

## 할루시네이션 검증 방법

1. **정확성 검증**: AI가 실제 로그에 없는 명령어나 결과를 언급하는지 확인
2. **일관성 검증**: 같은 데이터로 여러 번 평가했을 때 일관된 결과를 제공하는지 확인  
3. **근거 검증**: AI가 제시하는 근거가 실제 실행 데이터와 일치하는지 확인

## 테스트 실행 방법

```bash
# 1. 애플리케이션 실행
./gradlew bootRun

# 2. Postman 또는 curl로 평가 요청
POST /api/evaluation/evaluate
{
  "missionAttemptId": "kubernetes-mission-123",
  "code": "kubectl apply -f deployment.yaml",
  "missionType": "kubernetes-deployment",
  "missionObjective": "nginx 웹서버를 Kubernetes에 배포하고 외부 접근 가능하게 설정",
  "checklist": ["배포 성공", "서비스 노출", "접근 테스트"],
  "s3StorageUrl": "s3://missions/kubernetes-mission-123/",
  "s3PreSignedUrl": "https://mock-s3.com/kubernetes-mission-123/execution-log.json",
  "statistics": {
    "commandSuccessCount": 7,
    "commandFailureCount": 1,
    "totalExecutionTime": 8366
  }
}

# 3. AI 평가 결과 확인
GET /api/evaluation/{missionAttemptId}
```

## 기대 결과

- **정상 시나리오**: AI가 성공적인 배포 과정을 정확히 분석하고 높은 점수 부여
- **문제 시나리오**: AI가 실제 에러들(ValidationError, ImagePullBackOff)을 정확히 식별하고 구체적인 개선 방안 제시
- **할루시네이션 없음**: AI가 실제 로그에 없는 내용을 추가로 생성하지 않음

이를 통해 AI 모델의 신뢰성과 정확성을 검증할 수 있습니다.