package ac.su.kdt.beaievaluationservice.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

// AI 평가 이력 엔티티
// 평가 상태 변경 이력을 기록하여 감사 추적 및 디버깅에 활용
@Entity
@Table(name = "evaluation_history", indexes = {
    @Index(name = "idx_ai_evaluation_id", columnList = "ai_evaluation_id"),
    @Index(name = "idx_status_change", columnList = "previous_status, new_status"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class EvaluationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_evaluation_id", nullable = false)
    private AIEvaluation aiEvaluation;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status")
    private AIEvaluation.EvaluationStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false)
    private AIEvaluation.EvaluationStatus newStatus;

    @Column(name = "change_reason", length = 500)
    private String changeReason;

    @Column(name = "processing_node", length = 100)
    private String processingNode;

    @Column(name = "error_details", columnDefinition = "TEXT")
    private String errorDetails;

    @Column(name = "execution_time_ms")
    private Long executionTimeMs;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public EvaluationHistory(AIEvaluation aiEvaluation, AIEvaluation.EvaluationStatus previousStatus, 
                           AIEvaluation.EvaluationStatus newStatus, String changeReason) {
        this.aiEvaluation = aiEvaluation;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.changeReason = changeReason;
    }
}