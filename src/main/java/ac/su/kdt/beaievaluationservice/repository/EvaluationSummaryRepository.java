package ac.su.kdt.beaievaluationservice.repository;

import ac.su.kdt.beaievaluationservice.entity.EvaluationSummary;
import ac.su.kdt.beaievaluationservice.entity.AIEvaluation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// 평가 요약 정보 관리 Repository
@Repository
public interface EvaluationSummaryRepository extends JpaRepository<EvaluationSummary, Long> {
    
    Optional<EvaluationSummary> findByMissionAttemptId(String missionAttemptId);
    
    Page<EvaluationSummary> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
    
    List<EvaluationSummary> findByUserIdAndMissionIdOrderByCreatedAtDesc(String userId, String missionId);
    
    List<EvaluationSummary> findByStatus(AIEvaluation.EvaluationStatus status);
    
    @Query("SELECT es FROM EvaluationSummary es WHERE es.userId = :userId AND es.createdAt BETWEEN :startDate AND :endDate ORDER BY es.createdAt DESC")
    List<EvaluationSummary> findUserEvaluationsByDateRange(
        @Param("userId") String userId, 
        @Param("startDate") LocalDateTime startDate, 
        @Param("endDate") LocalDateTime endDate
    );
    
    @Query("SELECT AVG(es.overallScore) FROM EvaluationSummary es WHERE es.userId = :userId AND es.status = 'COMPLETED'")
    Double getAverageScoreByUserId(@Param("userId") String userId);
    
    @Query("SELECT es FROM EvaluationSummary es WHERE es.userId = :userId AND es.overallScore >= :minScore AND es.status = 'COMPLETED'")
    List<EvaluationSummary> findHighScoreEvaluations(@Param("userId") String userId, @Param("minScore") Integer minScore);

    List<EvaluationSummary> findByUserIdOrderByCreatedAtDesc(String userId);

}