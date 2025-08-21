package ac.su.kdt.beaievaluationservice.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

// AI 평가 요약 엔티티 (미션 서비스 연동)
// 대시보드 및 통계 최적화를 위한 요약 정보 저장
@Entity
@Table(name = "evaluation_summary", indexes = {
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_mission_id", columnList = "mission_id"),
    @Index(name = "idx_user_mission", columnList = "user_id, mission_id"),
    @Index(name = "idx_mission_difficulty", columnList = "mission_difficulty"),
    @Index(name = "idx_overall_score", columnList = "overall_score"),
    @Index(name = "idx_correctness_score", columnList = "correctness_score"),
    @Index(name = "idx_summary_created_at", columnList = "created_at"),
    @Index(name = "idx_summary_mission_attempt_id", columnList = "mission_attempt_id", unique = true),
    @Index(name = "idx_commands_stats", columnList = "commands_executed, significant_commands, error_commands")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class EvaluationSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", columnDefinition = "BIGINT")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "mission_id", nullable = false)
    private Long missionId;

    @Column(name = "mission_attempt_id", length = 36, nullable = false, unique = true)
    private String missionAttemptId;

    @Column(name = "mission_title", length = 200)
    private String missionTitle;

    @Column(name = "mission_type", length = 50)
    private String missionType;

    @Column(name = "mission_difficulty", length = 20)
    private String missionDifficulty;

    // AI 평가 점수들 (DevOps 채점관 기준)
    @Column(name = "overall_score")
    private Integer overallScore;

    @Column(name = "correctness_score")
    private Integer correctnessScore;

    @Column(name = "efficiency_score")
    private Integer efficiencyScore;

    @Column(name = "quality_score")
    private Integer qualityScore;

    // 호환성을 위한 기존 필드들 (계산된 값으로 사용)
    @Column(name = "code_quality_score")
    private Integer codeQualityScore; // correctness_score와 매핑

    @Column(name = "security_score")
    private Integer securityScore; // efficiency_score와 매핑

    @Column(name = "style_score")
    private Integer styleScore; // quality_score와 매핑

    @Enumerated(EnumType.STRING)
    @Column(name = "evaluation_status", length = 20, nullable = false)
    private AIEvaluation.EvaluationStatus status;

    @Column(name = "feedback_summary", columnDefinition = "TEXT")
    private String feedbackSummary;

    @Column(name = "feedback_details", columnDefinition = "JSON")
    private String feedbackDetails;

    // 미션 수행 통계
    @Column(name = "commands_executed")
    private Integer commandsExecuted = 0;

    @Column(name = "significant_commands")
    private Integer significantCommands = 0;

    @Column(name = "error_commands")
    private Integer errorCommands = 0;

    @Column(name = "total_execution_time_ms")
    private Long totalExecutionTimeMs = 0L;

    // 평가 메타데이터
    @Column(name = "evaluation_duration_ms")
    private Long evaluationDurationMs;

    @Column(name = "stamps_earned")
    private Integer stampsEarned = 0;

    @Column(name = "points_awarded")
    private Integer pointsAwarded = 0;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_evaluation_id", referencedColumnName = "id")
    private AIEvaluation aiEvaluation;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // 호환성을 위한 점수 동기화 메서드
    @PrePersist
    @PreUpdate
    public void syncCompatibilityScores() {
        if (correctnessScore != null) {
            this.codeQualityScore = correctnessScore;
        }
        if (efficiencyScore != null) {
            this.securityScore = efficiencyScore;
        }
        if (qualityScore != null) {
            this.styleScore = qualityScore;
        }
    }
}