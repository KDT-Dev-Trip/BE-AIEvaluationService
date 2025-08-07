package ac.su.kdt.beaievaluationservice.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

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
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CodeQualityScore {
        private Integer score;
        private String feedback;
        private String suggestions;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SecurityScore {
        private Integer score;
        private String feedback;
        private String vulnerabilities;
        private String recommendations;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StyleScore {
        private Integer score;
        private String feedback;
        private String styleIssues;
        private String improvements;
    }
}