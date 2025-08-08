package ac.su.kdt.beaievaluationservice.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

// 미션 임시 저장 엔티티
// 사용자가 미션 진행 중 중간 저장한 데이터를 관리
@Entity
@Table(name = "mission_temp_save")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class MissionTempSave {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "mission_id", nullable = false)
    private String missionId;

    @Column(name = "mission_attempt_id", nullable = false, unique = true)
    private String missionAttemptId;

    @Column(name = "mission_type")
    private String missionType;

    @Column(name = "mission_title")
    private String missionTitle;

    // 임시 저장된 코드 내용
    @Lob
    @Column(name = "temp_code", columnDefinition = "TEXT")
    private String tempCode;

    // 저장 횟수 (몇 번째 임시 저장인지)
    @Column(name = "save_count", nullable = false)
    private Integer saveCount = 0;

    // 임시 저장 상태
    @Enumerated(EnumType.STRING)
    @Column(name = "save_status", nullable = false)
    private SaveStatus saveStatus = SaveStatus.TEMP_SAVED;

    // 최종 완료 여부
    @Column(name = "is_final_completed", nullable = false)
    private Boolean isFinalCompleted = false;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // 임시 저장 상태 열거형
    public enum SaveStatus {
        TEMP_SAVED,     // 임시 저장됨
        FINAL_COMPLETED // 최종 완료됨 (평가 진행)
    }
}