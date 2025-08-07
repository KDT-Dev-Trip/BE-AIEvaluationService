package ac.su.kdt.beaievaluationservice.repository;

import ac.su.kdt.beaievaluationservice.entity.EvaluationHistory;
import ac.su.kdt.beaievaluationservice.entity.AIEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

// 평가 이력 데이터 관리 Repository
// 이 Repository는 AI 평가의 상태 변경 이력을 관리하며,
// 평가의 진행 상황 및 변경 사항을 추적
@Repository
public interface EvaluationHistoryRepository extends JpaRepository<EvaluationHistory, Long> {
    
    List<EvaluationHistory> findByAiEvaluationOrderByCreatedAtDesc(AIEvaluation aiEvaluation);
    
    List<EvaluationHistory> findByAiEvaluationIdOrderByCreatedAtDesc(Long aiEvaluationId);
    
    List<EvaluationHistory> findByNewStatusAndCreatedAtBefore(
        AIEvaluation.EvaluationStatus status, 
        LocalDateTime dateTime
    );
    
    @Query("SELECT eh FROM EvaluationHistory eh WHERE eh.newStatus = 'FAILED' AND eh.createdAt BETWEEN :startDate AND :endDate")
    List<EvaluationHistory> findFailedEvaluationsInDateRange(
        @Param("startDate") LocalDateTime startDate, 
        @Param("endDate") LocalDateTime endDate
    );
    
    @Query("SELECT COUNT(eh) FROM EvaluationHistory eh WHERE eh.newStatus = :status AND eh.createdAt >= :since")
    Long countByStatusSince(@Param("status") AIEvaluation.EvaluationStatus status, @Param("since") LocalDateTime since);
}