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
public class RecentEvaluationResultDTO {
    
    // 종합 점수
    private Integer overallScore;
    
    // 세부 점수들
    private Integer correctnessScore; // 정확성
    private Integer efficiencyScore;  // 효율성
    private Integer qualityScore;     // 품질
    
    // 통계 정보
    private Integer totalCommandCount;      // 총 명령어 개수
    private Integer significantCommandCount; // 중요 명령어 개수
    private Integer errorCommandCount;      // 오류 명령어 개수
    
    // 평가 시간
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime evaluationStartTime; // 시작 시간
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime evaluationEndTime;   // 완료 시간
    
    // 보안 위험도 분석 (Low, Medium, High)
    private String securityRiskLevel;
    
    // 카테고리별 성능
    private List<CategoryPerformanceDTO> categoryPerformances;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CategoryPerformanceDTO {
        private String category;
        private Double successRate;     // 성공률
        private String efficiencyGrade; // 효율성
        private Integer bestPracticeScore; // 모범사례
        private Boolean hasSecurityIssues; // 보안 이슈
    }
}