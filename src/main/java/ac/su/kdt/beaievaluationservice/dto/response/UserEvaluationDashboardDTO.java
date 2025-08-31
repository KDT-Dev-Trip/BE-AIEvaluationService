package ac.su.kdt.beaievaluationservice.dto.response;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserEvaluationDashboardDTO {
    
    // 총 평가 / 완료 평가 / 획득 스탬프
    private EvaluationSummaryDTO evaluationSummary;
    
    // 최근 평가 결과
    private RecentEvaluationResultDTO recentEvaluation;
    
    // 명령어 분석 결과 상세평
    private List<CommandAnalysisDTO> commandAnalysis;
    
    // 성과 분석
    private PerformanceMetricsDTO performanceMetrics;
    
    // 학습 진행도
    private LearningProgressDTO learningProgress;
    
    // 최근 활동 (평가 이력)
    private List<EvaluationHistoryItemDTO> recentActivity;
    
    // 종합 피드백
    private ComprehensiveFeedbackDTO comprehensiveFeedback;
    
    // 전체 학습 진행도 통계
    private OverallLearningStatsDTO overallStats;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class EvaluationHistoryItemDTO {
        private Integer score;
        private String missionName;
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDateTime evaluationDate;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ComprehensiveFeedbackDTO {
        private String aiOverallAssessment;
        private List<String> strengths;
        private List<String> improvements;
        private List<String> nextStepRecommendations;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class OverallLearningStatsDTO {
        private Integer highestScore;
        private Double averageScore;
        private Integer lowestScore;
    }
}