# 🚀 AI 평가 시스템 REST API 문서

## 📋 개요

AI 코드 평가 시스템을 REST API로 테스트할 수 있는 엔드포인트들입니다.  
Postman이나 curl 등을 사용하여 임시 저장, AI 평가, 시스템 상태 확인 등을 테스트할 수 있습니다.

**Base URL**: `http://localhost:8080/api/v1`

---

## 📝 1. 임시 저장 API (`/temp-save`)

### 1.1 임시 저장 생성/업데이트
**POST** `/temp-save`

```json
{
  "userId": "test-user-001",
  "missionId": "mission-java-001", 
  "missionAttemptId": "attempt-001",
  "missionType": "Java",
  "missionTitle": "Java 기초 실습",
  "tempCode": "public class Hello {\n  public static void main(String[] args) {\n    System.out.println(\"Hello World\");\n  }\n}",
  "saveCount": 1,
  "saveReason": "manual_save"
}
```

**응답**:
```json
{
  "success": true,
  "message": "임시 저장이 완료되었습니다.",
  "data": {
    "id": 1,
    "userId": "test-user-001",
    "missionAttemptId": "attempt-001",
    "saveCount": 1,
    "saveStatus": "TEMP_SAVED",
    "isFinalCompleted": false,
    "tempCodeLength": 95,
    "createdAt": "2025-08-08 16:30:00"
  },
  "timestamp": "2025-08-08 16:30:00"
}
```

### 1.2 임시 저장 조회
**GET** `/temp-save/{missionAttemptId}`

### 1.3 사용자별 미완료 임시 저장 목록
**GET** `/temp-save/user/{userId}/incomplete`

### 1.4 사용자별 임시 저장 통계
**GET** `/temp-save/user/{userId}/stats`

### 1.5 최종 완료 처리 (테스트용)
**PUT** `/temp-save/{missionAttemptId}/complete`

### 1.6 임시 저장 삭제 (테스트용)  
**DELETE** `/temp-save/{missionAttemptId}`

---

## 🤖 2. AI 평가 API (`/evaluation`)

### 2.1 AI 평가 시작
**POST** `/evaluation/start`

```json
{
  "userId": "test-user-001",
  "missionId": "mission-java-001",
  "missionAttemptId": "attempt-002", 
  "missionType": "Java",
  "missionTitle": "Java 기초 실습",
  "code": "public class Calculator {\n  public int add(int a, int b) {\n    return a + b;\n  }\n}",
  "startAt": "2025-08-08T15:00:00",
  "endAt": "2025-08-08T16:00:00"
}
```

**응답**:
```json
{
  "success": true,
  "message": "AI 평가가 시작되었습니다. 완료까지 약 10-30초 소요됩니다.",
  "data": {
    "missionAttemptId": "attempt-002",
    "userId": "test-user-001", 
    "status": "PROCESSING",
    "aiModelVersion": "gemini-1.5-pro",
    "createdAt": "2025-08-08 16:35:00"
  },
  "timestamp": "2025-08-08 16:35:00"
}
```

### 2.2 AI 평가 결과 조회
**GET** `/evaluation/{missionAttemptId}`

**응답** (완료된 경우):
```json
{
  "success": true,
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": {
    "evaluationId": 1,
    "missionAttemptId": "attempt-002",
    "userId": "test-user-001",
    "status": "COMPLETED", 
    "aiModelVersion": "gemini-1.5-pro",
    "overallScore": 85,
    "codeQualityScore": 80,
    "securityScore": 90,
    "styleScore": 85,
    "feedback": "전체적으로 좋은 코드입니다. 주석 추가를 권장합니다.",
    "performanceGrade": "GOOD",
    "hasCpuIssues": false,
    "hasMemoryIssues": false, 
    "hasResponseTimeIssues": false,
    "performanceSummary": "안정적인 성능을 보입니다.",
    "hadTempSave": true,
    "tempSaveCount": 2,
    "processingTimeMs": 15000,
    "createdAt": "2025-08-08 16:35:00",
    "updatedAt": "2025-08-08 16:35:15"
  },
  "timestamp": "2025-08-08 16:35:30"
}
```

### 2.3 사용자별 평가 이력 조회  
**GET** `/evaluation/user/{userId}/history`

### 2.4 상태별 평가 조회
**GET** `/evaluation/status/{status}`
- status: `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`

### 2.5 전체 평가 통계 조회 
**GET** `/evaluation/stats`

---

## 🔧 3. 테스트 데이터 API (`/test`)

### 3.1 시스템 헬스 체크
**GET** `/test/health`

### 3.2 데이터베이스 상태 조회  
**GET** `/test/database/status`

### 3.3 샘플 데이터 생성
**POST** `/test/data/sample`

### 3.4 모든 데이터 삭제 ⚠️
**DELETE** `/test/data/all`

### 3.5 특정 사용자 데이터 삭제
**DELETE** `/test/data/user/{userId}`

---

## 📬 4. Postman 테스트 시나리오

### 시나리오 1: 임시 저장 → 최종 완료 → AI 평가

1. **임시 저장 생성**
   ```
   POST /api/v1/temp-save
   ```

2. **임시 저장 조회 확인**
   ```
   GET /api/v1/temp-save/attempt-001
   ```

3. **AI 평가 시작** (최종 완료)
   ```
   POST /api/v1/evaluation/start
   ```

4. **평가 결과 조회** (완료까지 대기 후)
   ```
   GET /api/v1/evaluation/attempt-001
   ```

### 시나리오 2: 시스템 상태 및 통계 확인

1. **헬스 체크**
   ```
   GET /api/v1/test/health
   ```

2. **데이터베이스 상태**
   ```
   GET /api/v1/test/database/status
   ```

3. **샘플 데이터 생성**
   ```
   POST /api/v1/test/data/sample
   ```

4. **평가 통계 확인**
   ```
   GET /api/v1/evaluation/stats
   ```

---

## 🚨 5. 에러 응답 형태

```json
{
  "success": false,
  "message": "오류 메시지",
  "data": null,
  "timestamp": "2025-08-08 16:40:00"
}
```

### 주요 HTTP 상태 코드
- `200 OK`: 성공적인 처리
- `202 Accepted`: 비동기 처리 시작 (평가 요청)  
- `400 Bad Request`: 잘못된 요청 (Validation 실패)
- `404 Not Found`: 리소스를 찾을 수 없음
- `409 Conflict`: 중복 요청 (이미 평가 진행 중)
- `500 Internal Server Error`: 서버 내부 오류

---

## 🔧 6. 개발 환경 설정

### application.properties 추가 설정
```properties
# 로그 레벨 (개발 시 DEBUG로 설정)
logging.level.ac.su.kdt.beaievaluationservice=DEBUG

# H2 콘솔 접속 (개발용)
spring.h2.console.enabled=true
# http://localhost:8080/h2-console

# Gemini API (실제 키로 교체 필요)
gemini.api.key=your-actual-api-key
```

### Postman Environment 설정
- `baseUrl`: `http://localhost:8080/api/v1`
- `userId`: `test-user-001` 
- `missionId`: `mission-java-001`
- `missionAttemptId`: `attempt-{{$timestamp}}`

---

## ⚡ 7. 빠른 테스트 가이드

### 1단계: 애플리케이션 실행
```bash
./gradlew bootRun
```

### 2단계: 헬스 체크
```bash
curl -X GET http://localhost:8080/api/v1/test/health
```

### 3단계: 샘플 데이터 생성
```bash
curl -X POST http://localhost:8080/api/v1/test/data/sample
```

### 4단계: 임시 저장 테스트
```bash
curl -X POST http://localhost:8080/api/v1/temp-save \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "test-user-001",
    "missionId": "mission-test",
    "missionAttemptId": "attempt-test-001", 
    "missionType": "Java",
    "tempCode": "System.out.println(\"Hello\");",
    "saveCount": 1
  }'
```

### 5단계: AI 평가 테스트
```bash
curl -X POST http://localhost:8080/api/v1/evaluation/start \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "test-user-001",
    "missionId": "mission-test", 
    "missionAttemptId": "attempt-test-002",
    "missionType": "Java",
    "code": "public class Test { public static void main(String[] args) { System.out.println(\"Hello World\"); } }"
  }'
```

**🎉 이제 AI 평가 시스템을 완전히 REST API로 테스트할 수 있습니다!**