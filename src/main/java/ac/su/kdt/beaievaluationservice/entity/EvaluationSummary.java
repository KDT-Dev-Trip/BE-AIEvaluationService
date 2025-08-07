package ac.su.kdt.beaievaluationservice.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

// AI 평가 요약 엔티티
// 대시보드 및 통계 최적화를 위한 요약 정보 저장
@Entity
@Table(name = "evaluation_summary", indexes = {
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_mission_id", columnList = "mission_id"),
    @Index(name = "idx_user_mission", columnList = "user_id, mission_id"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class EvaluationSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "mission_id", nullable = false, length = 100)
    private String missionId;

    @Column(name = "mission_attempt_id", nullable = false, unique = true, length = 255)
    private String missionAttemptId;

    @Column(name = "mission_title", length = 200)
    private String missionTitle;

    @Column(name = "mission_type", length = 50)
    private String missionType;

    @Column(name = "overall_score")
    private Integer overallScore;

    @Column(name = "code_quality_score")
    private Integer codeQualityScore;

    @Column(name = "security_score")
    private Integer securityScore;

    @Column(name = "style_score")
    private Integer styleScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "evaluation_status", nullable = false)
    private AIEvaluation.EvaluationStatus status;

    @Column(name = "feedback_summary", columnDefinition = "TEXT")
    private String feedbackSummary;

    @Column(name = "evaluation_duration_ms")
    private Long evaluationDurationMs;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_evaluation_id", referencedColumnName = "id")
    private AIEvaluation aiEvaluation;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}