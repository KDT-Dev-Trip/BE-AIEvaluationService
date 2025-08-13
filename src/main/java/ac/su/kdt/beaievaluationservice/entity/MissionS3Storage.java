package ac.su.kdt.beaievaluationservice.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

/**
 * 미션별 S3 저장소 정보 엔티티
 * S3 저장소 주소만 RDS에 저장하고, Pre-signed URL은 필요할 때마다 발급받음
 */
@Entity
@Table(name = "mission_s3_storage")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MissionS3Storage {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "mission_attempt_id", nullable = false, unique = true)
    private String missionAttemptId;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Column(name = "mission_id", nullable = false) 
    private String missionId;
    
    @Column(name = "s3_storage_url", nullable = false, length = 500)
    private String s3StorageUrl; // S3 저장소 주소 (RDS에 저장)
    
    @Column(name = "bucket_name", nullable = false)
    private String bucketName;
    
    @Column(name = "object_key", nullable = false, length = 500)
    private String objectKey; // S3 객체 키
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}