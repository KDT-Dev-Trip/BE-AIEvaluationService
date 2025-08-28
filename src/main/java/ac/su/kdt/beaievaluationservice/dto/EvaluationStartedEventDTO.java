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
 * 평가 시작 이벤트 DTO
 * - AI 평가 프로세스가 시작될 때 발행되는 이벤트
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationStartedEventDTO {

    @JsonProperty("eventType")
    @Builder.Default
    private String eventType = "evaluation.started";

    @JsonProperty("eventId")
    private String eventId;

    @JsonProperty("timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime timestamp;

    // 평가 대상 정보
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

    // 평가 설정
    @JsonProperty("evaluationType")
    private String evaluationType; // AI_AUTOMATED, MANUAL_REVIEW, HYBRID

    @JsonProperty("evaluationCriteria")
    private Map<String, Object> evaluationCriteria;

    @JsonProperty("maxScore")
    private Integer maxScore;

    @JsonProperty("timeoutMinutes")
    private Integer timeoutMinutes;

    // 데이터 소스
    @JsonProperty("commandLogCount")
    private Integer commandLogCount;

    @JsonProperty("s3BucketPath")
    private String s3BucketPath;

    @JsonProperty("evaluationEngine")
    private String evaluationEngine; // GEMINI, OPENAI, CLAUDE

    // 우선순위 및 메타데이터
    @JsonProperty("priority")
    private String priority; // HIGH, NORMAL, LOW

    @JsonProperty("expectedDurationMinutes")
    private Integer expectedDurationMinutes;

    @JsonProperty("retryAttempt")
    private Integer retryAttempt;

    @JsonProperty("metadata")
    private Map<String, Object> metadata;

    /**
     * 기본 평가 시작 이벤트 생성
     */
    public static EvaluationStartedEventDTO createDefault(String evaluationId, String missionId, String missionTitle,
                                                          String attemptId, Long userId, String userEmail, String userName,
                                                          String evaluationType, Integer commandLogCount, String s3BucketPath) {
        return EvaluationStartedEventDTO.builder()
                .eventType("evaluation.started")
                .eventId(java.util.UUID.randomUUID().toString())
                .timestamp(LocalDateTime.now())
                .evaluationId(evaluationId)
                .missionId(missionId)
                .missionTitle(missionTitle)
                .attemptId(attemptId)
                .userId(userId)
                .userEmail(userEmail)
                .userName(userName)
                .evaluationType(evaluationType != null ? evaluationType : "AI_AUTOMATED")
                .commandLogCount(commandLogCount != null ? commandLogCount : 0)
                .s3BucketPath(s3BucketPath)
                .maxScore(100)
                .timeoutMinutes(30)
                .evaluationEngine("GEMINI")
                .priority("NORMAL")
                .expectedDurationMinutes(10)
                .retryAttempt(0)
                .build();
    }
}