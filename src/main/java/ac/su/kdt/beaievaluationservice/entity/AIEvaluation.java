package ac.su.kdt.beaievaluationservice.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

// AI 평가 엔티티 (미션 서비스 연동)
@Entity
@Table(name = "ai_evaluation", indexes = {
    @Index(name = "idx_mission_attempt_id", columnList = "mission_attempt_id", unique = true),
    @Index(name = "idx_user_mission_evaluation", columnList = "user_id, mission_id"),
    @Index(name = "idx_evaluation_status", columnList = "evaluation_status"),
    @Index(name = "idx_status_created_at", columnList = "evaluation_status, created_at"),
    @Index(name = "idx_mission_type", columnList = "mission_type"),
    @Index(name = "idx_processing_node", columnList = "processing_node"),
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
    @Column(name = "id", columnDefinition = "BIGINT")
    private Long id;

    @Column(name = "mission_attempt_id", length = 36, nullable = false, unique = true)
    private String missionAttemptId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "mission_id", nullable = false)
    private String missionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "evaluation_status", length = 20, nullable = false)
    private EvaluationStatus status;

    @Column(name = "evaluation_result", columnDefinition = "JSON")
    private String evaluationResult;

    @Column(name = "ai_model_version", length = 50)
    private String aiModelVersion;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    // 미션 서비스에서 전달받은 추가 정보
    @Column(name = "mission_title", length = 200)
    private String missionTitle;

    @Column(name = "mission_type", length = 50)
    private String missionType;

    @Column(name = "submitted_code", columnDefinition = "TEXT")
    private String submittedCode;

    @Column(name = "s3_log_path", length = 500)
    private String s3LogPath;

    @Column(name = "s3_workspace_path", length = 500)
    private String s3WorkspacePath;

    // 평가 메타데이터
    @Column(name = "evaluation_trigger", length = 30)
    private String evaluationTrigger = "MISSION_COMPLETION";

    @Column(name = "processing_node", length = 100)
    private String processingNode;

    @Column(name = "processing_time_ms")
    private Long processingTimeMs;

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
