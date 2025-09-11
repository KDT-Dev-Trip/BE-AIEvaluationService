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
    
    @JsonProperty("mission_category")
    private String missionCategory;
    
    @JsonProperty("mission_difficulty")
    private String missionDifficulty;
    
    // 평가 결과 정보
    @JsonProperty("evaluation_id")
    private Long evaluationId;
    
    @JsonProperty("overall_score")
    private Integer overallScore;
    
    // DevOps 채점관 점수들 (프론트와 호환)
    @JsonProperty("correctness_score")
    private Integer correctnessScore;
    
    @JsonProperty("efficiency_score")
    private Integer efficiencyScore;
    
    @JsonProperty("quality_score")
    private Integer qualityScore;
    
    // 기존 호환성 점수들
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
    
    // 미션 수행 통계 (프론트와 호환)
    @JsonProperty("commands_executed")
    private Integer commandsExecuted;
    
    @JsonProperty("significant_commands")
    private Integer significantCommands;
    
    @JsonProperty("error_commands")
    private Integer errorCommands;
    
    @JsonProperty("total_execution_time_ms")
    private Long totalExecutionTimeMs;
    
    // 성과 지표 (프론트와 호환)
    @JsonProperty("stamps_earned")
    private Integer stampsEarned;
    
    @JsonProperty("points_awarded")
    private Integer pointsAwarded;
    
    // 시스템 리소스 통계 (기존 유지)
    @JsonProperty("average_cpu_usage")
    private Double averageCpuUsage;
    
    @JsonProperty("max_cpu_usage")
    private Double maxCpuUsage;
    
    @JsonProperty("average_memory_usage")
    private Double averageMemoryUsage;
    
    @JsonProperty("max_memory_usage")
    private Double maxMemoryUsage;
}