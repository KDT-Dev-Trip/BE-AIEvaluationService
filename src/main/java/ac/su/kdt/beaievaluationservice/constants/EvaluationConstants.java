package ac.su.kdt.beaievaluationservice.constants;

/**
 * AI 평가 서비스에서 사용되는 상수들을 정의하는 클래스
 * SonarQube S1192 규칙 (String Literal Duplication) 위반을 해결하기 위해 생성됨
 */
public final class EvaluationConstants {
    
    private EvaluationConstants() {
        // Utility class - 인스턴스 생성 방지
    }
    
    // === JSON 필드명 상수 ===
    public static final String JSON_SCORE = "score";
    public static final String JSON_USER_ID = "userId";
    public static final String JSON_FEEDBACK = "feedback";
    public static final String JSON_DETAILS = "details";
    public static final String JSON_TIMESTAMP = "timestamp";
    public static final String JSON_TOTAL_SCORE = "total_score";
    public static final String JSON_REASON = "reason";
    public static final String JSON_ERROR = "error";
    public static final String JSON_COMMAND = "command";
    public static final String JSON_OBJECTIVE = "objective";
    public static final String JSON_MISSION_TITLE = "missionTitle";
    public static final String JSON_MISSION_ID = "missionId";
    public static final String JSON_MISSION_ATTEMPT_ID = "missionAttemptId";
    public static final String JSON_EVENT_TYPE = "eventType";
    public static final String JSON_EVALUATION_ID = "evaluationId";
    public static final String JSON_EVALUATION_ENGINE = "evaluationEngine";
    public static final String JSON_RETRY_ATTEMPT = "retryAttempt";
    
    // === 평가 점수 필드명 상수 ===
    public static final String JSON_SECURITY_SCORE = "security_score";
    public static final String JSON_STYLE_SCORE = "style_score";
    public static final String JSON_RELIABILITY_SCORE = "reliability_score";
    public static final String JSON_QUALITY_SCORE = "quality_score";
    public static final String JSON_BEST_PRACTICE_SCORE = "best_practice_score";
    public static final String JSON_CORRECTNESS_SCORE = "correctness_score";
    public static final String JSON_EFFICIENCY_SCORE = "efficiency_score";
    
    // === 데이터베이스 컬럼명 상수 ===
    public static final String COLUMN_MISSION_ATTEMPT_ID = "mission_attempt_id";
    public static final String COLUMN_USER_ID = "user_id";
    public static final String COLUMN_MISSION_ID = "mission_id";
    public static final String COLUMN_CREATED_AT = "created_at";
    public static final String COLUMN_UPDATED_AT = "updated_at";
    public static final String COLUMN_ID = "id";
    
    // === AI 모델 관련 상수 ===
    public static final String AI_MODEL_GEMINI = "GEMINI";
    public static final String AI_MODEL_VERSION_GEMINI_15_PRO = "gemini-1.5-pro";
    public static final String AI_MODEL_VERSION_GEMINI_20_FLASH = "gemini-2.0-flash-exp";
    
    // === 날짜 시간 형식 상수 ===
    public static final String DATETIME_FORMAT_WITH_MS = "yyyy-MM-dd'T'HH:mm:ss.SSS";
    public static final String DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    
    // === 이벤트 타입 상수 ===
    public static final String EVENT_TYPE_MISSION_COMPLETED = "MISSION_COMPLETED";
    public static final String EVENT_EVALUATION_RETRY_COMPLETED = "evaluation.retry-completed";
    
    // === 상태 관련 상수 ===
    public static final String STATUS_UP = "UP";
    public static final String STATUS_DOWN = "DOWN";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
    
    // === 에러 메시지 상수 ===
    public static final String ERROR_SERVER_INTERNAL = "서버 내부 오류";
    public static final String ERROR_AI_EVALUATION_FAILED = "AI 평가 중 오류가 발생했습니다";
    public static final String ERROR_MISSION_EVALUATION = "미션 평가";
    public static final String ERROR_VALIDATION = "잘못된 요청";
    public static final String ERROR_NOT_FOUND = "해당 데이터를 찾을 수 없습니다";
    
    // === 성공 메시지 상수 ===
    public static final String SUCCESS_SYSTEM_HEALTHY = "시스템이 정상 동작 중입니다.";
    public static final String SUCCESS_EVALUATION_STARTED = "AI 평가가 시작되었습니다.";
    public static final String SUCCESS_EVALUATION_COMPLETED = "AI 평가가 완료되었습니다.";
    public static final String SUCCESS_DATABASE_STATUS = "데이터베이스 상태를 조회했습니다.";
    
    // === 미션 타입 상수 ===
    public static final String MISSION_TYPE_DOCKER = "Docker Container";
    public static final String MISSION_TYPE_DOCKER_SIMPLE = "Docker";
    public static final String MISSION_TYPE_KUBERNETES = "Kubernetes";
    public static final String MISSION_TYPE_MONITORING = "Monitoring";
    
    // === 코드 스니펫 상수 ===
    public static final String CODE_SNIPPET_MARKDOWN = "```\n";
    
    // === 테스트 데이터 상수 ===
    public static final String TEST_USER_ID = "test-user-123";
    public static final String TEST_MISSION_ID = "test-mission-456";
    public static final String TEST_ATTEMPT_ID = "test-attempt-789";
    public static final String TEST_CODE_DOCKER = "FROM ubuntu:20.04\nRUN apt-get update\nEXPOSE 8080";
    public static final String TEST_MISSION_TITLE = "Docker 컨테이너 생성 실습";
    public static final String TEST_FEEDBACK_GOOD = "Overall good code quality with room for improvement";
    public static final String TEST_ANALYSIS_DETAILED = "Detailed analysis of the submitted code...";
    public static final String TEST_FEEDBACK_STRUCTURE = "Code structure is well organized";
    public static final String TEST_FEEDBACK_COMMENTS = "Add more comments for better readability";
    public static final String TEST_FEEDBACK_SECURITY = "No major security vulnerabilities detected";
    public static final String TEST_FEEDBACK_STYLE = "Consistent coding style throughout";
    public static final String TEST_FEEDBACK_INDENTATION = "Minor indentation inconsistencies";
    public static final String TEST_FEEDBACK_IMPROVEMENT = "Use consistent indentation and spacing";
    public static final String TEST_FEEDBACK_OVERALL = "Overall good performance";
    
    // === API 응답 메시지 상수 ===
    public static final String RESPONSE_SUCCESS = "성공";
    public static final String RESPONSE_FAILURE = "실패";
    
    // === 데이터베이스 테이블명 상수 ===
    public static final String TABLE_AI_EVALUATIONS = "ai_evaluations";
    public static final String TABLE_EVALUATION_SUMMARIES = "evaluation_summaries";
    public static final String TABLE_EVALUATION_HISTORIES = "evaluation_histories";
    
    // === HTTP 상태 코드 설명 상수 ===
    public static final String HTTP_200_DESC = "성공";
    public static final String HTTP_400_DESC = "잘못된 요청";
    public static final String HTTP_404_DESC = "리소스를 찾을 수 없음";
    public static final String HTTP_409_DESC = "충돌 (중복 요청)";
    public static final String HTTP_500_DESC = "서버 내부 오류";
    
    // === 시스템 서비스 상태 상수 ===
    public static final String SERVICE_DATABASE = "database";
    public static final String SERVICE_TEMP_SAVE = "temp-save-service";
    public static final String SERVICE_EVALUATION = "evaluation-service";
    
    // === 버전 정보 상수 ===
    public static final String VERSION_1_0_0 = "1.0.0";
    
    // === Swagger 태그 및 설명 상수 ===
    public static final String SWAGGER_TAG_AI_EVALUATION = "AI 평가";
    public static final String SWAGGER_TAG_SYSTEM_MANAGEMENT = "시스템 관리";
    public static final String SWAGGER_DESC_AI_EVALUATION = "AI를 통한 코드 평가 관련 API";
    public static final String SWAGGER_DESC_SYSTEM_MANAGEMENT = "시스템 상태 확인, 테스트 데이터 관리 등 개발/테스트 전용 API";
    
    // === 로그 메시지 상수 ===
    public static final String LOG_HEALTH_CHECK_REQUESTED = "Health check requested";
    public static final String LOG_DATABASE_STATUS_REQUESTED = "Database status check requested";
    public static final String LOG_HEALTH_CHECK_FAILED = "Health check failed";
    public static final String LOG_EVALUATION_REQUEST_RECEIVED = "Evaluation request received";
    public static final String LOG_EVALUATION_RESULT_REQUESTED = "Get evaluation result for missionAttemptId";
    
    // === 기본값 상수 ===
    public static final String DEFAULT_NONE_FOUND = "None found";
    public static final String DEFAULT_LOW_RISK = "Low";
    public static final String DEFAULT_CONTINUE_PRACTICES = "Continue following security best practices";
    
    // === 추가 Korean 메시지 상수 ===
    public static final String ERROR_KOREAN_SERVER_INTERNAL = "서버 내부 오류";
    public static final String ERROR_KOREAN_MISSION_NOT_FOUND = "해당 미션 시도의 평가를 찾을 수 없습니다";
    public static final String SUCCESS_KOREAN_SYSTEM_HEALTHY = "시스템이 정상 동작 중입니다";
    public static final String SUCCESS_KOREAN_DATABASE_STATUS = "데이터베이스 상태를 조회했습니다";
    public static final String ERROR_KOREAN_VALIDATION = "잘못된 요청";
    
    // === 추가 일반적인 문자열 상수 ===
    public static final String STRING_GEMINI = "GEMINI";
    public static final String STRING_MARKDOWN_CODE_BLOCK = "```\n";
    
    // === Kafka 토픽 관련 상수 ===
    public static final String KAFKA_TOPIC_MISSION_COMPLETED = "mission.completed";
    
    // === 평가 결과 관련 상수 ===
    public static final int SCORE_THRESHOLD_MIN = 0;
    public static final int SCORE_THRESHOLD_MAX = 100;
    public static final int SCORE_DEFAULT_FALLBACK = 50;
    public static final int SCORE_DEFAULT_SUCCESS = 85;
    
    // === 처리 시간 관련 상수 ===
    public static final int PROCESSING_TIME_DEFAULT_SECONDS = 10;
    public static final int PROCESSING_TIME_MAX_SECONDS = 30;
    
    // === 리스크 레벨 상수 ===
    public static final String RISK_LEVEL_HIGH = "High";
    public static final String RISK_LEVEL_MEDIUM = "Medium";
    public static final String RISK_LEVEL_LOW = "Low";
    
    // === 효율성 등급 상수 ===
    public static final String EFFICIENCY_GRADE_A = "A";
    public static final String EFFICIENCY_GRADE_B = "B";
    public static final String EFFICIENCY_GRADE_C = "C";
    public static final String EFFICIENCY_GRADE_D = "D";
    public static final String EFFICIENCY_GRADE_F = "F";
    
    // === 추가 SonarQube 문제 해결용 상수 ===
    public static final String TIMEOUT_STATUS = "TIMEOUT";
    public static final String S3_LOGS_PATH = "s3://devtrip-logs/missions/";
    public static final String EXECUTION_DATA_ZIP = "/execution-data.zip";
    public static final String COMMAND_FORMAT = "Command: %s\n";
    public static final String EXIT_CODE_FORMAT = "Exit Code: %d\n";
    public static final String ERROR_CREATING_SUMMARY = "Unexpected error creating evaluation summary for {}: {}";
}