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
 * 평가 실패 이벤트 DTO
 * - AI 평가 프로세스가 실패했을 때 발행되는 이벤트
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationFailedEventDTO {

    @JsonProperty("eventType")
    @Builder.Default
    private String eventType = "evaluation.failed";

    @JsonProperty("eventId")
    private String eventId;

    @JsonProperty("timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime timestamp;

    // 평가 기본 정보
    @JsonProperty("evaluationId")
    private String evaluationId;

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

    // 실패 관련 정보
    @JsonProperty("failureReason")
    private String failureReason;

    @JsonProperty("errorCode")
    private String errorCode;

    @JsonProperty("errorMessage")
    private String errorMessage;

    @JsonProperty("failureCategory")
    private String failureCategory; // SYSTEM_ERROR, TIMEOUT, DATA_INVALID, API_ERROR, RESOURCE_LIMIT

    @JsonProperty("stackTrace")
    private String stackTrace;

    // 재시도 정보
    @JsonProperty("retryAttempt")
    private Integer retryAttempt;

    @JsonProperty("maxRetries")
    private Integer maxRetries;

    @JsonProperty("canRetry")
    private Boolean canRetry;

    @JsonProperty("nextRetryAt")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime nextRetryAt;

    // 평가 진행 상황
    @JsonProperty("evaluationDurationMinutes")
    private Integer evaluationDurationMinutes;

    @JsonProperty("completedSteps")
    private String completedSteps;

    @JsonProperty("failedStep")
    private String failedStep;

    @JsonProperty("partialResults")
    private Map<String, Object> partialResults;

    // 리소스 정보
    @JsonProperty("evaluationEngine")
    private String evaluationEngine;

    @JsonProperty("resourcesUsed")
    private Map<String, Object> resourcesUsed;

    @JsonProperty("costIncurred")
    private Double costIncurred;

    // 메타데이터
    @JsonProperty("metadata")
    private Map<String, Object> metadata;

    /**
     * 기본 평가 실패 이벤트 생성
     */
    public static EvaluationFailedEventDTO createDefault(String evaluationId, String missionId, String missionTitle,
                                                         String attemptId, Long userId, String userEmail, String userName,
                                                         String failureReason, String errorCode, String errorMessage,
                                                         Integer retryAttempt) {
        LocalDateTime now = LocalDateTime.now();
        Boolean canRetry = determineRetryability(failureReason, retryAttempt);
        
        return EvaluationFailedEventDTO.builder()
                .eventType("evaluation.failed")
                .eventId(java.util.UUID.randomUUID().toString())
                .timestamp(now)
                .evaluationId(evaluationId)
                .missionId(missionId)
                .missionTitle(missionTitle)
                .attemptId(attemptId)
                .userId(userId)
                .userEmail(userEmail)
                .userName(userName)
                .failureReason(failureReason)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .failureCategory(categorizeFailure(failureReason))
                .retryAttempt(retryAttempt != null ? retryAttempt : 0)
                .maxRetries(3)
                .canRetry(canRetry)
                .nextRetryAt(canRetry ? now.plusMinutes(5) : null)
                .evaluationEngine("GEMINI")
                .build();
    }

    /**
     * 재시도 가능 여부 결정
     */
    private static Boolean determineRetryability(String failureReason, Integer retryAttempt) {
        if (retryAttempt != null && retryAttempt >= 3) {
            return false;
        }
        
        // 시스템 오류는 재시도 가능
        if (failureReason != null) {
            String reason = failureReason.toUpperCase();
            return reason.contains("TIMEOUT") || 
                   reason.contains("API_ERROR") || 
                   reason.contains("NETWORK") ||
                   reason.contains("RATE_LIMIT");
        }
        
        return true;
    }

    /**
     * 실패 카테고리 분류
     */
    private static String categorizeFailure(String failureReason) {
        if (failureReason == null) return "UNKNOWN";
        
        String reason = failureReason.toUpperCase();
        if (reason.contains("TIMEOUT")) return "TIMEOUT";
        if (reason.contains("API")) return "API_ERROR";
        if (reason.contains("DATA")) return "DATA_INVALID";
        if (reason.contains("RESOURCE") || reason.contains("LIMIT")) return "RESOURCE_LIMIT";
        
        return "SYSTEM_ERROR";
    }
}