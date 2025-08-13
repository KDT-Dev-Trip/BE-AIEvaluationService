# ⚡ AI 평가 시스템 빠른 테스트 가이드

## 🚀 1. 시작하기

### 애플리케이션 실행
```bash
./gradlew bootRun
```

### 기본 설정 확인
- **서버 주소**: http://localhost:8080
- **H2 콘솔**: http://localhost:8080/h2-console
- **API Base URL**: http://localhost:8080/api/v1

---

## 🔍 2. 기본 상태 확인

### 헬스 체크
```bash
curl -X GET http://localhost:8080/api/v1/test/health
```

**예상 응답**:
```json
{
  "success": true,
  "message": "시스템이 정상 동작 중입니다.",
  "data": {
    "status": "UP",
    "timestamp": "2025-08-08T16:30:00",
    "version": "1.0.0",
    "services": {
      "database": "UP",
      "temp-save-service": "UP",
      "evaluation-service": "UP"
    }
  }
}
```

### 데이터베이스 상태
```bash
curl -X GET http://localhost:8080/api/v1/test/database/status
```

---

## 📊 3. 테스트 데이터 준비

### 샘플 데이터 생성
```bash
curl -X POST http://localhost:8080/api/v1/test/data/sample
```

---

## 💾 4. 임시 저장 테스트

### 4.1 임시 저장 생성
```bash
curl -X POST http://localhost:8080/api/v1/temp-save \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "test-user-001",
    "missionId": "mission-test-001",
    "missionAttemptId": "attempt-test-001",
    "missionType": "Java",
    "missionTitle": "Java Hello World",
    "tempCode": "public class Hello {\n    public static void main(String[] args) {\n        System.out.println(\"Hello World\");\n    }\n}",
    "saveCount": 1,
    "saveReason": "manual_save"
  }'
```

### 4.2 임시 저장 조회
```bash
curl -X GET http://localhost:8080/api/v1/temp-save/attempt-test-001
```

### 4.3 임시 저장 업데이트
```bash
curl -X POST http://localhost:8080/api/v1/temp-save \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "test-user-001",
    "missionId": "mission-test-001", 
    "missionAttemptId": "attempt-test-001",
    "missionType": "Java",
    "missionTitle": "Java Hello World",
    "tempCode": "public class Hello {\n    public static void main(String[] args) {\n        System.out.println(\"Hello World Updated!\");\n        // 주석 추가\n    }\n}",
    "saveCount": 2,
    "saveReason": "manual_save"
  }'
```

### 4.4 사용자 미완료 목록 조회
```bash
curl -X GET http://localhost:8080/api/v1/temp-save/user/test-user-001/incomplete
```

---

## 🤖 5. AI 평가 테스트

### 5.1 AI 평가 시작
```bash
curl -X POST http://localhost:8080/api/v1/evaluation/start \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "test-user-001",
    "missionId": "mission-eval-001",
    "missionAttemptId": "eval-attempt-001",
    "missionType": "Java",
    "missionTitle": "Java Calculator 구현",
    "code": "public class Calculator {\n    public int add(int a, int b) {\n        return a + b;\n    }\n    \n    public int subtract(int a, int b) {\n        return a - b;\n    }\n    \n    public int multiply(int a, int b) {\n        return a * b;\n    }\n    \n    public double divide(int a, int b) {\n        if (b == 0) {\n            throw new IllegalArgumentException(\"Division by zero\");\n        }\n        return (double) a / b;\n    }\n}",
    "startAt": "2025-08-08T15:00:00",
    "endAt": "2025-08-08T16:00:00"
  }'
```

**예상 응답**:
```json
{
  "success": true,
  "message": "AI 평가가 시작되었습니다. 완료까지 약 10-30초 소요됩니다.",
  "data": {
    "missionAttemptId": "eval-attempt-001",
    "userId": "test-user-001",
    "status": "PROCESSING",
    "aiModelVersion": "gemini-1.5-pro",
    "createdAt": "2025-08-08 16:35:00"
  }
}
```

### 5.2 AI 평가 결과 조회 (10-30초 후)
```bash
curl -X GET http://localhost:8080/api/v1/evaluation/eval-attempt-001
```

**예상 응답** (완료 시):
```json
{
  "success": true,
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": {
    "evaluationId": 1,
    "missionAttemptId": "eval-attempt-001",
    "userId": "test-user-001",
    "status": "COMPLETED",
    "aiModelVersion": "gemini-1.5-pro",
    "overallScore": 85,
    "codeQualityScore": 80,
    "securityScore": 90,
    "styleScore": 85,
    "feedback": "전체적으로 좋은 코드입니다. 예외 처리가 잘 되어 있습니다.",
    "performanceGrade": "GOOD",
    "hasCpuIssues": false,
    "hasMemoryIssues": false,
    "hasResponseTimeIssues": false,
    "performanceSummary": "안정적인 성능을 보입니다.",
    "hadTempSave": false,
    "tempSaveCount": 0,
    "processingTimeMs": 15000,
    "createdAt": "2025-08-08 16:35:00",
    "updatedAt": "2025-08-08 16:35:15"
  }
}
```

---

## 🎯 6. 완전한 시나리오 테스트

### 시나리오: 임시 저장 → 최종 완료 → AI 평가

#### Step 1: 임시 저장
```bash
curl -X POST http://localhost:8080/api/v1/temp-save \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "scenario-user",
    "missionId": "scenario-mission",
    "missionAttemptId": "scenario-attempt-001",
    "missionType": "Python",
    "missionTitle": "Python 피보나치 수열",
    "tempCode": "def fibonacci(n):\n    if n <= 1:\n        return n\n    # TODO: 구현 중...",
    "saveCount": 1,
    "saveReason": "manual_save"
  }'
```

#### Step 2: 임시 저장 업데이트
```bash
curl -X POST http://localhost:8080/api/v1/temp-save \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "scenario-user", 
    "missionId": "scenario-mission",
    "missionAttemptId": "scenario-attempt-001",
    "missionType": "Python",
    "missionTitle": "Python 피보나치 수열",
    "tempCode": "def fibonacci(n):\n    if n <= 1:\n        return n\n    return fibonacci(n-1) + fibonacci(n-2)\n\n# 테스트\nprint(fibonacci(10))",
    "saveCount": 2,
    "saveReason": "manual_save"
  }'
```

#### Step 3: 최종 완료 및 AI 평가
```bash
curl -X POST http://localhost:8080/api/v1/evaluation/start \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "scenario-user",
    "missionId": "scenario-mission", 
    "missionAttemptId": "scenario-attempt-001",
    "missionType": "Python",
    "missionTitle": "Python 피보나치 수열",
    "code": "def fibonacci(n):\n    \"\"\"피보나치 수열의 n번째 값을 반환합니다.\"\"\"\n    if n <= 1:\n        return n\n    return fibonacci(n-1) + fibonacci(n-2)\n\n# 메모이제이션을 사용한 최적화 버전\ndef fibonacci_optimized(n, memo={}): \n    if n in memo:\n        return memo[n]\n    if n <= 1:\n        return n\n    memo[n] = fibonacci_optimized(n-1, memo) + fibonacci_optimized(n-2, memo)\n    return memo[n]\n\n# 테스트 실행\nif __name__ == \"__main__\":\n    for i in range(10):\n        print(f\"fibonacci({i}) = {fibonacci_optimized(i)}\")",
    "startAt": "2025-08-08T14:00:00",
    "endAt": "2025-08-08T15:00:00"
  }'
```

#### Step 4: 평가 결과 확인 (30초 후)
```bash
curl -X GET http://localhost:8080/api/v1/evaluation/scenario-attempt-001
```

#### Step 5: 임시 저장 이력 확인 (임시 저장이 최종 완료로 변경됨)
```bash
curl -X GET http://localhost:8080/api/v1/temp-save/scenario-attempt-001
```

---

## 📈 7. 통계 및 상태 확인

### 평가 통계
```bash
curl -X GET http://localhost:8080/api/v1/evaluation/stats
```

### 사용자별 평가 이력
```bash
curl -X GET http://localhost:8080/api/v1/evaluation/user/test-user-001/history
```

### 상태별 평가 조회
```bash
# 완료된 평가들
curl -X GET http://localhost:8080/api/v1/evaluation/status/COMPLETED

# 처리 중인 평가들
curl -X GET http://localhost:8080/api/v1/evaluation/status/PROCESSING

# 실패한 평가들
curl -X GET http://localhost:8080/api/v1/evaluation/status/FAILED
```

### 임시 저장 통계
```bash
curl -X GET http://localhost:8080/api/v1/temp-save/user/test-user-001/stats
```

---

## 🧹 8. 정리

### 특정 사용자 데이터 삭제
```bash
curl -X DELETE http://localhost:8080/api/v1/test/data/user/test-user-001
```

### 모든 데이터 삭제 (주의!)
```bash
curl -X DELETE http://localhost:8080/api/v1/test/data/all
```

---

## 🔍 9. 주요 확인 포인트

### ✅ 성공 케이스
1. **헬스 체크**: `"status": "UP"`
2. **임시 저장**: `"success": true`, `"saveStatus": "TEMP_SAVED"`
3. **AI 평가 시작**: `"status": "PROCESSING"`
4. **AI 평가 완료**: `"status": "COMPLETED"`, 점수 정보 포함
5. **임시 저장 연계**: `"hadTempSave": true`, `"tempSaveCount": 2`

### ❌ 오류 케이스
- **중복 평가**: `409 Conflict` - "이미 평가가 진행 중이거나 완료된 미션입니다."
- **존재하지 않는 리소스**: `404 Not Found`
- **잘못된 요청**: `400 Bad Request` - Validation 실패
- **서버 오류**: `500 Internal Server Error`

---

## 📱 10. Postman 사용법

1. **컬렉션 임포트**: 
   - Postman 실행
   - Import → File → `AI_Evaluation_System.postman_collection.json` 선택

2. **환경 변수 설정**:
   - `baseUrl`: `http://localhost:8080/api/v1`
   - `userId`: `test-user-001`
   - `missionId`: `mission-test-001`

3. **테스트 순서**:
   - 🏥 시스템 상태 → 헬스 체크
   - 📊 테스트 데이터 → 샘플 데이터 생성
   - 💾 임시 저장 → 생성 → 조회 → 업데이트
   - 🤖 AI 평가 → 시작 → 결과 조회
   - 🎯 시나리오 테스트 → 전체 플로우

**🎉 이제 AI 평가 시스템을 완전히 테스트할 수 있습니다!** 🚀