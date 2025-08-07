package ac.su.kdt.beaievaluationservice.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

// AI 평가 엔티티
@Entity
@Table(name = "ai_evaluation", indexes = {
    @Index(name = "idx_mission_attempt_id", columnList = "mission_attempt_id", unique = true),
    @Index(name = "idx_evaluation_status", columnList = "evaluation_status"),
    @Index(name = "idx_status_created_at", columnList = "evaluation_status, created_at"),
    @Index(name = "idx_created_at", columnList = "created_at"),
    @Index(name = "idx_updated_at", columnList = "updated_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class AIEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mission_attempt_id", nullable = false, unique = true)
    private String missionAttemptId;

    @Enumerated(EnumType.STRING)
    @Column(name = "evaluation_status", nullable = false)
    private EvaluationStatus status;

    @Column(name = "evaluation_result", columnDefinition = "JSON")
    private String evaluationResult;

    @Column(name = "ai_model_version", length = 50)
    private String aiModelVersion;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum EvaluationStatus {
        PENDING,     // 대기
        PROCESSING,  // 처리중
        COMPLETED,   // 완료
        FAILED       // 실패
    }
}
