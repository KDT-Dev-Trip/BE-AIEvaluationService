package ac.su.kdt.beaievaluationservice.repository;

import ac.su.kdt.beaievaluationservice.entity.MissionS3Storage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 미션 S3 저장소 정보 레포지토리
 */
@Repository
public interface MissionS3StorageRepository extends JpaRepository<MissionS3Storage, Long> {
    
    /**
     * missionAttemptId로 S3 저장소 정보 조회
     */
    Optional<MissionS3Storage> findByMissionAttemptId(String missionAttemptId);
    
    /**
     * missionAttemptId로 S3 저장소 정보 존재 여부 확인
     */
    boolean existsByMissionAttemptId(String missionAttemptId);
    
    /**
     * userId로 해당 사용자의 S3 저장소 정보들 조회
     */
    java.util.List<MissionS3Storage> findByUserId(String userId);
}