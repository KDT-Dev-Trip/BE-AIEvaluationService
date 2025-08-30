package ac.su.kdt.beaievaluationservice.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;
import java.util.Map;

// AI 평가 결과 DTO
// 디테일한 평가 요소는 수정 예정
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationResultDTO {
    
    private CodeQualityScore codeQuality;
    private SecurityScore security;
    private StyleScore style;
    private Integer overallScore;
    private String feedback;
    private String detailedAnalysis;
    
    // 보안 위험도 분석 (Low, Medium, High)
    private String securityRiskLevel;
    
    // 효율성 등급 (A, B, C, D, F)
    private String efficiencyGrade;
    
    // 모범사례 점수 (0-100)
    private Integer bestPracticeScore;
    
    // 신뢰도 점수 (0-100)
    private Integer reliabilityScore;
    
    // 학습 목표 평가 결과
    private List<LearningObjectiveResult> learningObjectivesEvaluation;
    
    // 전체 학습 목표 달성률 (0-100)
    private Integer overallObjectiveAchievement;
    
    // 학습 목표별 점수 (목표 이름 -> 점수)
    private Map<String, Integer> learningObjectiveScores;
    
    // 학습 목표별 피드백 (목표 이름 -> 피드백)
    private Map<String, String> objectiveFeedback;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LearningObjectiveResult {
        private String objective;           // 학습 목표 명칭
        private Integer achievementRate;     // 달성률 (0-100)
        private String evidence;             // 달성 근거 (실제 명령어 또는 결과물)
        private String feedback;             // 구체적인 피드백
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CodeQualityScore {
        private Integer score;
        private String feedback;
        private String suggestions;
        private String efficiencyGrade; // A, B, C, D, F
        private Integer bestPracticeScore; // 0-100
        private Integer reliabilityScore; // 0-100
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SecurityScore {
        private Integer score;
        private String feedback;
        private String vulnerabilities;
        private String recommendations;
        private String riskLevel; // Low, Medium, High
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StyleScore {
        private Integer score;
        private String feedback;
        private String styleIssues;
        private String improvements;
        private String category; // 다양한 스타일 카테고리
    }
}