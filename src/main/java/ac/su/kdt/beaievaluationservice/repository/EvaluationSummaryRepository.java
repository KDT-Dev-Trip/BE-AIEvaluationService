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
    
    Page<EvaluationSummary> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    
    List<EvaluationSummary> findByUserIdAndMissionIdOrderByCreatedAtDesc(Long userId, String missionId);
    
    List<EvaluationSummary> findByStatus(AIEvaluation.EvaluationStatus status);
    
    @Query("SELECT es FROM EvaluationSummary es WHERE es.userId = :userId AND es.createdAt BETWEEN :startDate AND :endDate ORDER BY es.createdAt DESC")
    List<EvaluationSummary> findUserEvaluationsByDateRange(
        @Param("userId") Long userId, 
        @Param("startDate") LocalDateTime startDate, 
        @Param("endDate") LocalDateTime endDate
    );
    
    @Query("SELECT AVG(es.overallScore) FROM EvaluationSummary es WHERE es.userId = :userId AND es.status = 'COMPLETED'")
    Double getAverageScoreByUserId(@Param("userId") Long userId);
    
    @Query("SELECT es FROM EvaluationSummary es WHERE es.userId = :userId AND es.overallScore >= :minScore AND es.status = 'COMPLETED'")
    List<EvaluationSummary> findHighScoreEvaluations(@Param("userId") Long userId, @Param("minScore") Integer minScore);

    List<EvaluationSummary> findByUserIdOrderByCreatedAtDesc(Long userId);

    // 대시보드용 통계 쿼리들
    @Query("SELECT COUNT(es) FROM EvaluationSummary es WHERE es.userId = :userId")
    Long countTotalEvaluationsByUserId(@Param("userId") Long userId);
    
    @Query("SELECT COUNT(es) FROM EvaluationSummary es WHERE es.userId = :userId AND es.status = 'COMPLETED'")
    Long countCompletedEvaluationsByUserId(@Param("userId") Long userId);
    
    @Query("SELECT COALESCE(SUM(es.stampsEarned), 0) FROM EvaluationSummary es WHERE es.userId = :userId AND es.status = 'COMPLETED'")
    Integer getTotalStampsByUserId(@Param("userId") Long userId);
    
    // 미션 타입별 성과 분석
    @Query("SELECT es.missionType, COUNT(es), AVG(es.overallScore), AVG(CASE WHEN es.overallScore >= 70 THEN 1.0 ELSE 0.0 END) " +
           "FROM EvaluationSummary es WHERE es.userId = :userId AND es.status = 'COMPLETED' " +
           "GROUP BY es.missionType")
    List<Object[]> getMissionTypePerformanceByUserId(@Param("userId") Long userId);
    
    // 개별 미션별 성과 분석
    @Query("SELECT es.missionId, es.missionTitle, COUNT(es), AVG(es.overallScore), " +
           "AVG(CASE WHEN es.overallScore >= 70 THEN 1.0 ELSE 0.0 END), es.missionDifficulty, " +
           "AVG(es.totalExecutionTimeMs) " +
           "FROM EvaluationSummary es WHERE es.userId = :userId " +
           "GROUP BY es.missionId, es.missionTitle, es.missionDifficulty")
    List<Object[]> getIndividualMissionPerformanceByUserId(@Param("userId") Long userId);
    
    // 최근 트렌드 분석 (최근 10개 평가)
    @Query("SELECT es.overallScore FROM EvaluationSummary es WHERE es.userId = :userId AND es.status = 'COMPLETED' " +
           "ORDER BY es.createdAt DESC LIMIT 10")
    List<Integer> getRecentScoreTrendByUserId(@Param("userId") Long userId);
    
    // 난이도별 성과 분석
    @Query("SELECT es.missionDifficulty, COUNT(es), AVG(es.overallScore), AVG(CASE WHEN es.overallScore >= 70 THEN 1.0 ELSE 0.0 END) " +
           "FROM EvaluationSummary es WHERE es.userId = :userId AND es.status = 'COMPLETED' " +
           "GROUP BY es.missionDifficulty")
    List<Object[]> getDifficultyPerformanceByUserId(@Param("userId") Long userId);
    
    // 전체 학습 통계
    @Query("SELECT MAX(es.overallScore), AVG(es.overallScore), MIN(es.overallScore) " +
           "FROM EvaluationSummary es WHERE es.userId = :userId AND es.status = 'COMPLETED'")
    List<Object[]> getOverallLearningStatsByUserId(@Param("userId") Long userId);

}