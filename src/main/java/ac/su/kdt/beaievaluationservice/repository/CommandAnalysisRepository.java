package ac.su.kdt.beaievaluationservice.repository;

import ac.su.kdt.beaievaluationservice.entity.CommandAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 명령어 분석 데이터 액세스 레이어
 * 미션 서비스의 command_logs와 연동하여 AI 분석 결과를 관리
 */
@Repository
public interface CommandAnalysisRepository extends JpaRepository<CommandAnalysis, Long> {

    /**
     * 미션 시도별 명령어 분석 결과 조회
     */
    List<CommandAnalysis> findByMissionAttemptIdOrderByCreatedAt(String missionAttemptId);

    /**
     * AI 평가별 명령어 분석 결과 조회
     */
    List<CommandAnalysis> findByAiEvaluationIdOrderByCreatedAt(Long aiEvaluationId);

    /**
     * 보안 위험도별 명령어 분석 결과 조회
     */
    List<CommandAnalysis> findBySecurityRiskLevel(CommandAnalysis.SecurityRiskLevel riskLevel);

    /**
     * 명령어 카테고리별 분석 결과 조회
     */
    List<CommandAnalysis> findByCommandCategory(String commandCategory);

    /**
     * 정확성 평가별 명령어 분석 결과 조회
     */
    List<CommandAnalysis> findByCorrectnessAssessment(CommandAnalysis.CorrectnessAssessment assessment);

    /**
     * 미션 시도의 보안 위험 명령어 개수 조회
     */
    @Query("SELECT COUNT(ca) FROM CommandAnalysis ca WHERE ca.missionAttemptId = :missionAttemptId AND ca.securityRiskLevel IN ('HIGH', 'CRITICAL')")
    Long countHighRiskCommandsByMissionAttempt(@Param("missionAttemptId") String missionAttemptId);

    /**
     * 미션 시도의 명령어 카테고리별 통계
     */
    @Query("SELECT ca.commandCategory, COUNT(ca), AVG(ca.efficiencyRating), AVG(ca.bestPracticeScore) " +
           "FROM CommandAnalysis ca WHERE ca.missionAttemptId = :missionAttemptId " +
           "GROUP BY ca.commandCategory ORDER BY COUNT(ca) DESC")
    List<Object[]> getCommandCategoryStatsByMissionAttempt(@Param("missionAttemptId") String missionAttemptId);

    /**
     * 사용자의 명령어 사용 패턴 분석
     */
    @Query("SELECT ca.commandCategory, COUNT(ca), AVG(ca.efficiencyRating) " +
           "FROM CommandAnalysis ca " +
           "JOIN ca.aiEvaluation ae " +
           "WHERE ae.userId = :userId " +
           "GROUP BY ca.commandCategory " +
           "ORDER BY COUNT(ca) DESC")
    List<Object[]> getUserCommandUsagePattern(@Param("userId") Long userId);

    /**
     * 신뢰도가 높은 분석 결과만 조회
     */
    @Query("SELECT ca FROM CommandAnalysis ca WHERE ca.analysisConfidence >= :minConfidence ORDER BY ca.analysisConfidence DESC")
    List<CommandAnalysis> findHighConfidenceAnalysis(@Param("minConfidence") Double minConfidence);
}