package ac.su.kdt.beaievaluationservice.repository;

import ac.su.kdt.beaievaluationservice.entity.AIEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// AIEvaluation 엔티티에 대한 CRUD 및 특정 쿼리를 제공하는 Repository
@Repository
public interface AIEvaluationRepository extends JpaRepository<AIEvaluation, Long> {
    
    Optional<AIEvaluation> findByMissionAttemptId(String missionAttemptId);
    
    List<AIEvaluation> findByStatus(AIEvaluation.EvaluationStatus status);
    
    List<AIEvaluation> findByStatusAndCreatedAtBefore(
        AIEvaluation.EvaluationStatus status, 
        LocalDateTime dateTime
    );
    
    @Query("SELECT a FROM AIEvaluation a WHERE a.missionAttemptId IN :attemptIds")
    List<AIEvaluation> findByMissionAttemptIds(@Param("attemptIds") List<String> attemptIds);
    // 특정 미션 시도 ID 목록에 해당하는 AIEvaluation 엔티티 조회

    boolean existsByMissionAttemptId(String missionAttemptId);
    
    long countByStatus(AIEvaluation.EvaluationStatus status);
}