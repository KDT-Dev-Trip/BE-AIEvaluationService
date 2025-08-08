package ac.su.kdt.beaievaluationservice.repository;

import ac.su.kdt.beaievaluationservice.entity.MissionTempSave;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

// 미션 임시 저장 리포지토리
@Repository
public interface MissionTempSaveRepository extends JpaRepository<MissionTempSave, Long> {

    // 미션 시도 ID로 임시 저장 데이터 조회
    Optional<MissionTempSave> findByMissionAttemptId(String missionAttemptId);

    // 미션 시도 ID로 존재 여부 확인
    boolean existsByMissionAttemptId(String missionAttemptId);

    // 사용자 ID와 미션 ID로 임시 저장 목록 조회 (최신순)
    @Query("SELECT m FROM MissionTempSave m WHERE m.userId = :userId AND m.missionId = :missionId ORDER BY m.updatedAt DESC")
    List<MissionTempSave> findByUserIdAndMissionIdOrderByUpdatedAtDesc(@Param("userId") String userId, @Param("missionId") String missionId);

    // 사용자의 모든 임시 저장 데이터 조회 (최신순)
    List<MissionTempSave> findByUserIdOrderByUpdatedAtDesc(String userId);

    // 최종 완료되지 않은 임시 저장 데이터 조회
    List<MissionTempSave> findByIsFinalCompletedFalse();

    // 특정 사용자의 미완료 임시 저장 데이터 조회
    @Query("SELECT m FROM MissionTempSave m WHERE m.userId = :userId AND m.isFinalCompleted = false ORDER BY m.updatedAt DESC")
    List<MissionTempSave> findIncompleteByUserId(@Param("userId") String userId);

    // 미션 시도 ID로 최종 완료 상태 업데이트
    @Query("UPDATE MissionTempSave m SET m.isFinalCompleted = true, m.saveStatus = 'FINAL_COMPLETED' WHERE m.missionAttemptId = :missionAttemptId")
    void markAsCompleted(@Param("missionAttemptId") String missionAttemptId);
}