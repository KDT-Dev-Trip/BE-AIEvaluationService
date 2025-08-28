package ac.su.kdt.beaievaluationservice.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 평가 재시도 요청 이벤트 DTO
 * - 평가 실패 후 재시도가 요청될 때 발행되는 이벤트
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationRetryRequestedEventDTO {

    @JsonProperty("eventType")
    @Builder.Default
    private String eventType = "evaluation.retry-requested";

    @JsonProperty("eventId")
    private String eventId;

    @JsonProperty("timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime timestamp;

    // 평가 기본 정보
    @JsonProperty("evaluationId")
    private String evaluationId;

    @JsonProperty("originalEvaluationId")
    private String originalEvaluationId;

    @JsonProperty("missionId")
    private String missionId;

    @JsonProperty("missionTitle")
    private String missionTitle;

    @JsonProperty("attemptId")
    private String attemptId;

    @JsonProperty("userId")
    private Long userId;

    @JsonProperty("userEmail")
    private String userEmail;

    @JsonProperty("userName")
    private String userName;

    // 재시도 정보
    @JsonProperty("retryAttempt")
    private Integer retryAttempt;

    @JsonProperty("previousFailureReason")
    private String previousFailureReason;

    @JsonProperty("retryReason")
    private String retryReason;

    @JsonProperty("retryTrigger")
    private String retryTrigger; // AUTOMATIC, MANUAL_USER, MANUAL_ADMIN, SCHEDULED

    @JsonProperty("retryRequestedBy")
    private String retryRequestedBy; // USER_ID or SYSTEM

    @JsonProperty("retryStrategy")
    private String retryStrategy; // IMMEDIATE, DELAYED, DIFFERENT_ENGINE, FALLBACK

    // 설정 변경
    @JsonProperty("configChanges")
    private Map<String, Object> configChanges;

    @JsonProperty("evaluationEngine")
    private String evaluationEngine;

    @JsonProperty("newEvaluationEngine")
    private String newEvaluationEngine;

    @JsonProperty("timeoutMinutes")
    private Integer timeoutMinutes;

    @JsonProperty("priority")
    private String priority; // HIGH, NORMAL, LOW

    // 스케줄링
    @JsonProperty("scheduledRetryAt")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime scheduledRetryAt;

    @JsonProperty("expectedDurationMinutes")
    private Integer expectedDurationMinutes;

    // 이전 시도 정보
    @JsonProperty("previousAttemptDuration")
    private Integer previousAttemptDuration;

    @JsonProperty("previousPartialResults")
    private Map<String, Object> previousPartialResults;

    @JsonProperty("learningsFromFailure")
    private String learningsFromFailure;

    // 알림 설정
    @JsonProperty("notifyUser")
    private Boolean notifyUser;

    @JsonProperty("notifyAdmins")
    private Boolean notifyAdmins;

    @JsonProperty("escalationLevel")
    private String escalationLevel; // NONE, LOW, MEDIUM, HIGH

    // 메타데이터
    @JsonProperty("metadata")
    private Map<String, Object> metadata;

    /**
     * 기본 재시도 요청 이벤트 생성
     */
    public static EvaluationRetryRequestedEventDTO createDefault(String evaluationId, String originalEvaluationId,
                                                                 String missionId, String missionTitle, String attemptId,
                                                                 Long userId, String userEmail, String userName,
                                                                 Integer retryAttempt, String previousFailureReason,
                                                                 String retryTrigger) {
        LocalDateTime now = LocalDateTime.now();
        String retryStrategy = determineRetryStrategy(previousFailureReason, retryAttempt);
        String newEngine = determineAlternativeEngine(previousFailureReason, retryAttempt);
        
        return EvaluationRetryRequestedEventDTO.builder()
                .eventType("evaluation.retry-requested")
                .eventId(java.util.UUID.randomUUID().toString())
                .timestamp(now)
                .evaluationId(evaluationId)
                .originalEvaluationId(originalEvaluationId)
                .missionId(missionId)
                .missionTitle(missionTitle)
                .attemptId(attemptId)
                .userId(userId)
                .userEmail(userEmail)
                .userName(userName)
                .retryAttempt(retryAttempt != null ? retryAttempt : 1)
                .previousFailureReason(previousFailureReason)
                .retryReason("평가 실패로 인한 자동 재시도")
                .retryTrigger(retryTrigger != null ? retryTrigger : "AUTOMATIC")
                .retryRequestedBy("SYSTEM")
                .retryStrategy(retryStrategy)
                .evaluationEngine("GEMINI")
                .newEvaluationEngine(newEngine)
                .scheduledRetryAt(now.plusMinutes(5))
                .timeoutMinutes(45)
                .priority("HIGH")
                .expectedDurationMinutes(15)
                .notifyUser(true)
                .notifyAdmins(retryAttempt != null && retryAttempt >= 2)
                .escalationLevel(retryAttempt != null && retryAttempt >= 3 ? "HIGH" : "LOW")
                .build();
    }

    /**
     * 재시도 전략 결정
     */
    private static String determineRetryStrategy(String failureReason, Integer retryAttempt) {
        if (failureReason == null) return "IMMEDIATE";
        
        String reason = failureReason.toUpperCase();
        if (reason.contains("RATE_LIMIT") || reason.contains("QUOTA")) {
            return "DELAYED";
        }
        if (reason.contains("ENGINE_ERROR") || (retryAttempt != null && retryAttempt >= 2)) {
            return "DIFFERENT_ENGINE";
        }
        if (reason.contains("TIMEOUT") && retryAttempt != null && retryAttempt >= 2) {
            return "FALLBACK";
        }
        
        return "IMMEDIATE";
    }

    /**
     * 대안 엔진 결정
     */
    private static String determineAlternativeEngine(String failureReason, Integer retryAttempt) {
        if (retryAttempt == null || retryAttempt < 2) {
            return "GEMINI";
        }
        
        // 2번 이상 실패 시 대안 엔진 사용
        switch (retryAttempt % 3) {
            case 1: return "OPENAI";
            case 2: return "CLAUDE";
            default: return "GEMINI";
        }
    }
}