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
 * 평가 재시도 완료 이벤트 DTO
 * - 평가 재시도가 완료되었을 때 발행되는 이벤트 (성공/실패 관계없이)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationRetryCompletedEventDTO {

    @JsonProperty("eventType")
    @Builder.Default
    private String eventType = "evaluation.retry-completed";

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

    // 재시도 결과
    @JsonProperty("retryAttempt")
    private Integer retryAttempt;

    @JsonProperty("retryStatus")
    private String retryStatus; // SUCCESS, FAILED, PARTIAL_SUCCESS

    @JsonProperty("finalScore")
    private Integer finalScore;

    @JsonProperty("maxScore")
    private Integer maxScore;

    @JsonProperty("scoreImprovement")
    private Integer scoreImprovement; // 이전 시도 대비 개선점

    // 시간 정보
    @JsonProperty("retryDurationMinutes")
    private Integer retryDurationMinutes;

    @JsonProperty("totalEvaluationTime")
    private Integer totalEvaluationTime;

    @JsonProperty("retryStartedAt")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime retryStartedAt;

    @JsonProperty("completedAt")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime completedAt;

    // 비교 분석
    @JsonProperty("previousFailureReason")
    private String previousFailureReason;

    @JsonProperty("retryStrategy")
    private String retryStrategy;

    @JsonProperty("evaluationEngine")
    private String evaluationEngine;

    @JsonProperty("engineChanged")
    private Boolean engineChanged;

    @JsonProperty("configurationChanges")
    private Map<String, Object> configurationChanges;

    // 결과 세부 정보
    @JsonProperty("evaluationResults")
    private Map<String, Object> evaluationResults;

    @JsonProperty("improvementAreas")
    private String improvementAreas;

    @JsonProperty("detailedFeedback")
    private String detailedFeedback;

    // 품질 지표
    @JsonProperty("qualityMetrics")
    private Map<String, Object> qualityMetrics;

    @JsonProperty("consistencyScore")
    private Double consistencyScore; // 이전 시도와의 일관성

    @JsonProperty("reliabilityScore")
    private Double reliabilityScore;

    // 비용 및 리소스
    @JsonProperty("totalCost")
    private Double totalCost;

    @JsonProperty("resourcesUsed")
    private Map<String, Object> resourcesUsed;

    @JsonProperty("effortLevel")
    private String effortLevel; // LOW, NORMAL, HIGH, INTENSIVE

    // 향후 추천사항
    @JsonProperty("recommendationsForFuture")
    private String recommendationsForFuture;

    @JsonProperty("needsHumanReview")
    private Boolean needsHumanReview;

    @JsonProperty("confidenceLevel")
    private String confidenceLevel; // LOW, MEDIUM, HIGH, VERY_HIGH

    // 메타데이터
    @JsonProperty("metadata")
    private Map<String, Object> metadata;

    /**
     * 기본 재시도 완료 이벤트 생성 - 성공 케이스
     */
    public static EvaluationRetryCompletedEventDTO createSuccessful(String evaluationId, String originalEvaluationId,
                                                                   String missionId, String missionTitle, String attemptId,
                                                                   Long userId, String userEmail, String userName,
                                                                   Integer retryAttempt, Integer finalScore, Integer maxScore,
                                                                   LocalDateTime retryStartedAt, String evaluationEngine,
                                                                   String retryStrategy) {
        LocalDateTime now = LocalDateTime.now();
        Integer durationMinutes = calculateDuration(retryStartedAt, now);
        
        return EvaluationRetryCompletedEventDTO.builder()
                .eventType("evaluation.retry-completed")
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
                .retryStatus("SUCCESS")
                .finalScore(finalScore)
                .maxScore(maxScore != null ? maxScore : 100)
                .retryDurationMinutes(durationMinutes)
                .retryStartedAt(retryStartedAt)
                .completedAt(now)
                .evaluationEngine(evaluationEngine != null ? evaluationEngine : "GEMINI")
                .retryStrategy(retryStrategy != null ? retryStrategy : "IMMEDIATE")
                .engineChanged(!"GEMINI".equals(evaluationEngine))
                .consistencyScore(0.85)
                .reliabilityScore(0.92)
                .effortLevel(durationMinutes != null && durationMinutes > 20 ? "HIGH" : "NORMAL")
                .needsHumanReview(finalScore != null && finalScore < 60)
                .confidenceLevel(finalScore != null && finalScore >= 80 ? "HIGH" : "MEDIUM")
                .build();
    }

    /**
     * 기본 재시도 완료 이벤트 생성 - 실패 케이스
     */
    public static EvaluationRetryCompletedEventDTO createFailed(String evaluationId, String originalEvaluationId,
                                                               String missionId, String missionTitle, String attemptId,
                                                               Long userId, String userEmail, String userName,
                                                               Integer retryAttempt, String failureReason,
                                                               LocalDateTime retryStartedAt, String evaluationEngine) {
        LocalDateTime now = LocalDateTime.now();
        Integer durationMinutes = calculateDuration(retryStartedAt, now);
        
        return EvaluationRetryCompletedEventDTO.builder()
                .eventType("evaluation.retry-completed")
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
                .retryStatus("FAILED")
                .finalScore(0)
                .maxScore(100)
                .retryDurationMinutes(durationMinutes)
                .retryStartedAt(retryStartedAt)
                .completedAt(now)
                .previousFailureReason(failureReason)
                .evaluationEngine(evaluationEngine != null ? evaluationEngine : "GEMINI")
                .consistencyScore(0.0)
                .reliabilityScore(0.3)
                .effortLevel("HIGH")
                .needsHumanReview(true)
                .confidenceLevel("LOW")
                .recommendationsForFuture("수동 검토 필요, 대안 평가 방법 고려")
                .build();
    }

    /**
     * 시간 차이 계산 (분 단위)
     */
    private static Integer calculateDuration(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) return null;
        return (int) java.time.Duration.between(start, end).toMinutes();
    }
}