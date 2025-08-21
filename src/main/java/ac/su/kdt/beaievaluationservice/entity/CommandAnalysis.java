package ac.su.kdt.beaievaluationservice.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// 명령어 분석 결과 엔티티 (미션 서비스 command_logs 연동)
@Entity
@Table(name = "command_analysis", indexes = {
    @Index(name = "idx_command_mission_attempt", columnList = "mission_attempt_id"),
    @Index(name = "idx_command_evaluation", columnList = "ai_evaluation_id"),
    @Index(name = "idx_command_category", columnList = "command_category"),
    @Index(name = "idx_correctness_assessment", columnList = "correctness_assessment"),
    @Index(name = "idx_security_risk", columnList = "security_risk_level"),
    @Index(name = "idx_analysis_confidence", columnList = "analysis_confidence"),
    @Index(name = "idx_command_created_at", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class CommandAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", columnDefinition = "BIGINT")
    private Long id;

    @Column(name = "mission_attempt_id", length = 36, nullable = false)
    private String missionAttemptId;

    @Column(name = "command_log_id", length = 36)
    private String commandLogId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_evaluation_id", nullable = false)
    private AIEvaluation aiEvaluation;

    // 명령어 분석 결과
    @Column(name = "command", columnDefinition = "TEXT", nullable = false)
    private String command;

    @Column(name = "command_category", length = 50)
    private String commandCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "correctness_assessment", length = 20)
    private CorrectnessAssessment correctnessAssessment;

    @Column(name = "efficiency_rating")
    private Integer efficiencyRating;

    @Column(name = "best_practice_score")
    private Integer bestPracticeScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "security_risk_level", length = 20)
    private SecurityRiskLevel securityRiskLevel = SecurityRiskLevel.LOW;

    // AI 분석 상세
    @Column(name = "ai_feedback", columnDefinition = "TEXT")
    private String aiFeedback;

    @Column(name = "improvement_suggestions", columnDefinition = "JSON")
    private String improvementSuggestions;

    @Column(name = "alternative_commands", columnDefinition = "JSON")
    private String alternativeCommands;

    // 메타데이터
    @Column(name = "analysis_confidence", precision = 3, scale = 2)
    private BigDecimal analysisConfidence = BigDecimal.valueOf(0.85);

    @Column(name = "processing_time_ms")
    private Integer processingTimeMs;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // 열거형 정의
    public enum CorrectnessAssessment {
        CORRECT,    // 정확
        INCORRECT,  // 부정확
        PARTIAL     // 부분적
    }

    public enum SecurityRiskLevel {
        LOW,        // 낮음
        MEDIUM,     // 보통
        HIGH,       // 높음
        CRITICAL    // 치명적
    }
}