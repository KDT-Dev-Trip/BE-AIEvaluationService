package ac.su.kdt.beaievaluationservice.kafka.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

// AI 평가 완료 이벤트 - evaluation.completed 토픽으로 발행
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationCompletedEvent {
    
    @JsonProperty("mission_attempt_id")
    private String missionAttemptId;
    
    @JsonProperty("user_id")
    private String userId;
    
    @JsonProperty("mission_id")
    private String missionId;
    
    @JsonProperty("mission_title")
    private String missionTitle;
    
    @JsonProperty("mission_type")
    private String missionType;
    
    // 평가 결과 정보
    @JsonProperty("evaluation_id")
    private Long evaluationId;
    
    @JsonProperty("overall_score")
    private Integer overallScore;
    
    @JsonProperty("code_quality_score")
    private Integer codeQualityScore;
    
    @JsonProperty("security_score")
    private Integer securityScore;
    
    @JsonProperty("style_score")
    private Integer styleScore;
    
    @JsonProperty("performance_grade")
    private String performanceGrade; // EXCELLENT, GOOD, FAIR, POOR, CRITICAL
    
    @JsonProperty("feedback_summary")
    private String feedbackSummary;
    
    // 메타데이터
    @JsonProperty("ai_model_version")
    private String aiModelVersion;
    
    @JsonProperty("evaluation_status")
    private String evaluationStatus; // COMPLETED, FAILED
    
    @JsonProperty("completed_at")
    private LocalDateTime completedAt;
    
    @JsonProperty("processing_time_ms")
    private Long processingTimeMs; // 평가 소요 시간
    
    // 성능 분석 요약
    @JsonProperty("has_cpu_issues")
    private Boolean hasCpuIssues;
    
    @JsonProperty("has_memory_issues")
    private Boolean hasMemoryIssues;
    
    @JsonProperty("has_response_time_issues")
    private Boolean hasResponseTimeIssues;
    
    @JsonProperty("performance_summary")
    private String performanceSummary;
}