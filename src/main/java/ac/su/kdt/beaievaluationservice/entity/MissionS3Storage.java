package ac.su.kdt.beaievaluationservice.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.LocalDateTime;

/**
 * 미션별 S3 저장소 정보 엔티티 (미션 서비스 연동)
 * S3 저장소 주소와 다양한 경로들을 관리하고, Pre-signed URL은 필요할 때마다 발급받음
 */
@Entity
@Table(name = "mission_s3_storage", indexes = {
    @Index(name = "idx_s3_mission_attempt_id", columnList = "mission_attempt_id", unique = true),
    @Index(name = "idx_s3_user_id", columnList = "user_id"),
    @Index(name = "idx_s3_mission_id", columnList = "mission_id"),
    @Index(name = "idx_s3_bucket_prefix", columnList = "bucket_name, object_key_prefix"),
    @Index(name = "idx_s3_access_level", columnList = "access_level"),
    @Index(name = "idx_s3_expiry_date", columnList = "expiry_date"),
    @Index(name = "idx_s3_created_at", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class MissionS3Storage {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", columnDefinition = "BIGINT")
    private Long id;
    
    @Column(name = "mission_attempt_id", length = 36, nullable = false, unique = true)
    private String missionAttemptId;
    
    @Column(name = "user_id", nullable = false)
    private Long userId;
    
    @Column(name = "mission_id", nullable = false) 
    private Long missionId;
    
    // S3 저장소 경로들
    @Column(name = "s3_storage_url", nullable = false, length = 500)
    private String s3StorageUrl; // S3 저장소 기본 URL
    
    @Column(name = "command_logs_path", length = 500)
    private String commandLogsPath; // 명령어 로그 S3 경로
    
    @Column(name = "workspace_snapshot_path", length = 500)
    private String workspaceSnapshotPath; // 워크스페이스 스냅샷 S3 경로
    
    @Column(name = "submission_files_path", length = 500)
    private String submissionFilesPath; // 제출 파일들 S3 경로
    
    // S3 메타데이터
    @Column(name = "bucket_name", nullable = false, length = 255)
    private String bucketName;
    
    @Column(name = "object_key_prefix", nullable = false, length = 500)
    private String objectKeyPrefix; // S3 객체 키 프리픽스
    
    @Column(name = "total_size_bytes")
    private Long totalSizeBytes = 0L;
    
    @Column(name = "file_count")
    private Integer fileCount = 0;
    
    @Column(name = "last_sync_at")
    private LocalDateTime lastSyncAt;
    
    // 접근 권한
    @Enumerated(EnumType.STRING)
    @Column(name = "access_level", length = 20)
    private AccessLevel accessLevel = AccessLevel.PRIVATE;
    
    @Column(name = "expiry_date")
    private LocalDateTime expiryDate;
    
    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    // 열거형 정의
    public enum AccessLevel {
        PRIVATE,    // 개인용
        TEAM,       // 팀 공유
        PUBLIC      // 공개
    }
}