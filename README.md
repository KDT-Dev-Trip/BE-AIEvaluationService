# AI Evaluation Service

![Java](https://img.shields.io/badge/Java-17-ED8B00?style=flat-square&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.x-6DB33F?style=flat-square&logo=spring-boot&logoColor=white)
![Google Gemini](https://img.shields.io/badge/Google%20Gemini-1.5%20Pro-4285F4?style=flat-square&logo=google&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?style=flat-square&logo=mysql&logoColor=white)
![Apache Kafka](https://img.shields.io/badge/Apache%20Kafka-3.6-231F20?style=flat-square&logo=apache-kafka&logoColor=white)

DevTrip 플랫폼의 **AI 기반 자동 평가 엔진**입니다. Google Gemini API를 활용하여 미션 수행 결과를 분석하고, 상세한 피드백과 점수를 제공합니다.

## 기술 스택

### Backend Framework
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.x-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white)
![Spring WebFlux](https://img.shields.io/badge/Spring%20WebFlux-Reactive-6DB33F?style=for-the-badge&logo=spring&logoColor=white)
![Spring Data JPA](https://img.shields.io/badge/Spring%20Data%20JPA-3.x-6DB33F?style=for-the-badge&logo=spring&logoColor=white)

### AI & External APIs
![Google Gemini](https://img.shields.io/badge/Google%20Gemini-1.5%20Pro-4285F4?style=for-the-badge&logo=google&logoColor=white)
![AWS S3](https://img.shields.io/badge/AWS%20S3-File%20Storage-569A31?style=for-the-badge&logo=amazon-s3&logoColor=white)

### Database & Messaging
![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?style=for-the-badge&logo=mysql&logoColor=white)
![Apache Kafka](https://img.shields.io/badge/Apache%20Kafka-3.6-231F20?style=for-the-badge&logo=apache-kafka&logoColor=white)

### Monitoring & Tools
![Prometheus](https://img.shields.io/badge/Prometheus-Monitoring-E6522C?style=for-the-badge&logo=prometheus&logoColor=white)
![Resilience4j](https://img.shields.io/badge/Resilience4j-Circuit%20Breaker-FF9900?style=for-the-badge)

## 주요 기능

- **명령어 분석**: 터미널 명령어 시퀀스 지능형 분석
- **결과 평가**: 미션 완료 결과물 자동 검증 및 점수 산정
- **상세 피드백**: AI 기반 개선사항 및 학습 가이드 제공
- **다중 평가 기준**: 정확성, 효율성, 보안성, 최적화 등 종합 평가
- **학습 추천**: 개인화된 다음 학습 경로 제안
- **비동기 처리**: Kafka 이벤트 기반 대용량 평가 처리

## 로컬 실행

### 환경 설정
```bash
cd BE-AI-evaluation-service

# 환경변수 설정
cp .env.example .env
```

**필수 환경변수:**
```bash
# Database
DB_URL=jdbc:mysql://localhost:3306/devtrip-ai
DB_USERNAME=devtrip
DB_PASSWORD=your_password

# Google Gemini API
GEMINI_API_KEY=your_gemini_api_key
GEMINI_MODEL=gemini-1.5-pro
GEMINI_MAX_TOKENS=4096
GEMINI_TEMPERATURE=0.1

# AWS S3 (평가 결과 저장)
AWS_S3_BUCKET=devtrip-evaluation-results
AWS_ACCESS_KEY_ID=your_access_key
AWS_SECRET_ACCESS_KEY=your_secret_key

# Kafka
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
KAFKA_CONSUMER_GROUP=ai-evaluation-service
```

### Google Cloud 설정
```bash
# Google Cloud CLI 설치
gcloud auth application-default login

# Gemini API 사용 설정
gcloud services enable aiplatform.googleapis.com
```

### 데이터베이스 설정
```sql
CREATE DATABASE `devtrip-ai` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 서비스 실행
```bash
# 빌드 및 실행
./gradlew bootRun --args='--spring.profiles.active=local'
```

## API 엔드포인트

### 평가 요청
| Method | Endpoint | 설명 | 인증 |
|--------|----------|------|------|
| `POST` | `/api/evaluations` | 평가 요청 생성 | ✅ |
| `GET` | `/api/evaluations/{id}` | 평가 결과 조회 | ✅ |
| `GET` | `/api/evaluations/mission/{missionId}` | 미션별 평가 이력 | ✅ |
| `GET` | `/api/evaluations/user/{userId}` | 사용자별 평가 이력 | ✅ |

### 평가 관리
| Method | Endpoint | 설명 | 인증 |
|--------|----------|------|------|
| `POST` | `/api/evaluations/{id}/retry` | 평가 재시도 | ✅ |
| `PUT` | `/api/evaluations/{id}/feedback` | 피드백 수정 | ✅ (ADMIN) |
| `GET` | `/api/evaluations/stats` | 평가 통계 | ✅ (ADMIN) |

### 명령어 분석
| Method | Endpoint | 설명 | 인증 |
|--------|----------|------|------|
| `POST` | `/api/commands/analyze` | 명령어 시퀀스 분석 | ✅ |
| `GET` | `/api/commands/suggestions` | 명령어 개선 제안 | ✅ |
| `GET` | `/api/commands/patterns` | 자주 사용되는 패턴 | ✅ |

### AI 모델 관리
| Method | Endpoint | 설명 | 인증 |
|--------|----------|------|------|
| `GET` | `/api/ai/models` | 사용 가능한 AI 모델 | ✅ (ADMIN) |
| `POST` | `/api/ai/models/{model}/test` | 모델 테스트 | ✅ (ADMIN) |
| `GET` | `/api/ai/usage` | API 사용량 통계 | ✅ (ADMIN) |

### 시스템
| Method | Endpoint | 설명 |
|--------|----------|------|
| `GET` | `/api/health` | 헬스체크 |
| `GET` | `/actuator/prometheus` | 메트릭 |

## 평가 프로세스

### 1. 이벤트 수신
```java
@KafkaListener(topics = "mission.completed")
public void handleMissionCompleted(MissionCompletedEvent event) {
    EvaluationRequest request = EvaluationRequest.builder()
        .missionId(event.getMissionId())
        .userId(event.getUserId())
        .attemptId(event.getAttemptId())
        .commands(event.getCommands())
        .executionResult(event.getResult())
        .build();
        
    evaluationService.processEvaluation(request);
}
```

### 2. AI 평가 수행
```java
@Service
public class GeminiEvaluationService {
    
    @Retryable(value = {Exception.class}, maxAttempts = 3)
    public EvaluationResult evaluateMission(EvaluationRequest request) {
        String prompt = buildEvaluationPrompt(request);
        
        // Gemini API 호출
        String response = geminiClient.generateContent(prompt);
        
        // 응답 파싱 및 점수 추출
        return parseEvaluationResponse(response);
    }
    
    private String buildEvaluationPrompt(EvaluationRequest request) {
        return """
            미션 평가 요청:
            
            미션 ID: %s
            카테고리: %s
            난이도: %s
            
            실행된 명령어 시퀀스:
            %s
            
            실행 결과:
            %s
            
            다음 기준으로 평가해주세요:
            1. 정확성 (40점): 미션 목표 달성도
            2. 효율성 (25점): 명령어 최적화 정도
            3. 보안성 (20점): 보안 모범 사례 준수
            4. 코드 품질 (15점): 구조화, 가독성
            
            응답 형식:
            {
                "totalScore": 85,
                "accuracy": 38,
                "efficiency": 22,
                "security": 18,
                "codeQuality": 7,
                "feedback": "상세 피드백...",
                "suggestions": ["개선사항1", "개선사항2"],
                "nextSteps": ["다음 학습 단계"]
            }
            """.formatted(
                request.getMissionId(),
                request.getCategory(),
                request.getDifficulty(),
                String.join("\n", request.getCommands()),
                request.getExecutionResult()
            );
    }
}
```

### 3. 결과 저장 및 이벤트 발행
```java
@EventListener
public void onEvaluationCompleted(EvaluationCompletedEvent event) {
    // 결과를 S3에 상세 저장
    s3Service.saveEvaluationDetails(event.getEvaluationId(), event.getDetails());
    
    // Kafka 이벤트 발행
    kafkaTemplate.send("evaluation.completed", EvaluationResultMessage.builder()
        .userId(event.getUserId())
        .missionId(event.getMissionId())
        .score(event.getScore())
        .feedback(event.getFeedback())
        .build());
}
```

## AI 프롬프트 템플릿

### Docker 미션 평가
```yaml
docker_evaluation_template: |
  당신은 Docker 전문가입니다. 다음 미션을 평가해주세요:
  
  미션: {mission_title}
  설명: {mission_description}
  
  사용자가 실행한 명령어:
  {commands}
  
  평가 기준:
  - Dockerfile 작성 품질
  - 이미지 최적화 (레이어 수, 크기)
  - 보안 설정 (non-root user, 불필요한 패키지)
  - 빌드 효율성
  
  100점 만점으로 평가하고, 구체적인 개선사항을 제시해주세요.
```

### Kubernetes 미션 평가
```yaml
kubernetes_evaluation_template: |
  당신은 Kubernetes 전문가입니다. 다음 미션을 평가해주세요:
  
  미션: {mission_title}
  난이도: {difficulty}
  
  실행 명령어:
  {commands}
  
  YAML 매니페스트:
  {manifests}
  
  평가 포인트:
  - 리소스 정의 정확성
  - 보안 설정 (Security Context, Network Policy)
  - 모니터링 및 헬스체크 설정
  - 베스트 프랙티스 준수
  
  각 항목별 점수와 함께 종합 피드백을 제공해주세요.
```

## 설정

### Gemini API 설정
```yaml
gemini:
  api:
    key: ${GEMINI_API_KEY}
    base-url: https://generativelanguage.googleapis.com
    model: gemini-1.5-pro
    max-tokens: 4096
    temperature: 0.1
    top-p: 0.8
    timeout: 30s
  
  rate-limit:
    requests-per-minute: 100
    requests-per-hour: 1000
```

### 평가 기준 설정
```yaml
evaluation:
  criteria:
    accuracy:
      weight: 0.4
      max-score: 40
    efficiency:
      weight: 0.25  
      max-score: 25
    security:
      weight: 0.2
      max-score: 20
    code-quality:
      weight: 0.15
      max-score: 15
  
  thresholds:
    pass-score: 70
    excellent-score: 90
    retry-limit: 3
```

## 테스트

### 단위 테스트
```bash
# 단위 테스트
./gradlew test

# Mock Gemini API 테스트
./gradlew test -Dspring.profiles.active=test
```

### AI 평가 테스트
```java
@Test
void testDockerMissionEvaluation() {
    // Given
    EvaluationRequest request = EvaluationRequest.builder()
        .missionId(1L)
        .category(MissionCategory.DOCKER)
        .commands(Arrays.asList(
            "docker build -t myapp .",
            "docker run -d -p 8080:8080 myapp"
        ))
        .build();
    
    // When
    EvaluationResult result = evaluationService.evaluate(request);
    
    // Then
    assertThat(result.getTotalScore()).isGreaterThan(70);
    assertThat(result.getFeedback()).isNotEmpty();
    assertThat(result.getSuggestions()).isNotEmpty();
}
```

### 통합 테스트 (실제 Gemini API)
```bash
# 실제 API 테스트
./gradlew integrationTest -Dgemini.api.enabled=true
```

## 모니터링

### 주요 메트릭
- **평가 처리량**: `evaluations_processed_per_minute`
- **AI API 응답시간**: `gemini_api_response_time_histogram`
- **평가 정확도**: `evaluation_accuracy_gauge`
- **API 사용량**: `gemini_api_tokens_used_counter`
- **에러율**: `evaluation_error_rate`

### 대시보드 지표
- 시간당 평가 건수
- 평균 평가 점수 분포
- 카테고리별 성능 통계
- AI API 비용 추적
- 사용자 피드백 만족도

## 보안 및 최적화

### API 키 보안
```java
@Configuration
public class SecurityConfig {
    
    @Value("${gemini.api.key}")
    private String geminiApiKey;
    
    @Bean
    public GeminiClient geminiClient() {
        return GeminiClient.builder()
            .apiKey(geminiApiKey)
            .rateLimiter(RateLimiter.create(100.0/60)) // 분당 100회
            .build();
    }
}
```

### 비용 최적화
```java
@Component
public class CostOptimizer {
    
    // 캐시를 통한 중복 평가 방지
    @Cacheable(value = "evaluations", key = "#request.hashCode()")
    public EvaluationResult evaluate(EvaluationRequest request) {
        // 평가 로직
    }
    
    // 배치 처리로 API 호출 최적화
    @Scheduled(fixedRate = 30000) // 30초마다
    public void processBatchEvaluations() {
        List<EvaluationRequest> batch = getQueuedEvaluations(10);
        if (!batch.isEmpty()) {
            processBatchWithGemini(batch);
        }
    }
}
```

## 의존성 서비스

### 필수 의존성
- **Google Gemini API**: AI 평가 엔진
- **MySQL**: 평가 결과 저장
- **AWS S3**: 상세 평가 데이터 보관
- **Kafka**: 미션 완료 이벤트 수신

### 연동 서비스
- **Mission Management**: 미션 완료 이벤트 구독
- **User Management**: 사용자 진도 업데이트

## 트러블슈팅

### 일반적인 문제

**1. Gemini API 호출 실패**
```bash
# API 키 확인
gcloud auth print-access-token

# 할당량 확인
curl -H "Authorization: Bearer $(gcloud auth print-access-token)" \
  https://serviceusage.googleapis.com/v1/projects/your-project/services/aiplatform.googleapis.com
```

**2. 평가 처리 지연**
```bash
# Kafka Consumer Lag 확인
kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --describe --group ai-evaluation-service
```

**3. 메모리 부족**
```yaml
# JVM 힙 메모리 튜닝
JAVA_OPTS: "-Xms1g -Xmx2g -XX:+UseG1GC"
```

## 개발 가이드

### 새로운 평가 기준 추가
1. `EvaluationCriteria` enum에 추가
2. 프롬프트 템플릿 업데이트  
3. 점수 계산 로직 수정
4. 테스트 케이스 작성

### 새로운 AI 모델 연동
1. `AIProvider` 인터페이스 구현
2. 설정 파일에 모델 정보 추가
3. 프롬프트 호환성 검증
4. 성능 벤치마크 수행

## 관련 문서
- [Gemini API 연동 가이드](./docs/GEMINI_INTEGRATION.md)
- [평가 기준 정의서](./docs/EVALUATION_CRITERIA.md)
- [프롬프트 엔지니어링](./docs/PROMPT_ENGINEERING.md)
- [비용 최적화 가이드](./docs/COST_OPTIMIZATION.md)