package ac.su.kdt.beaievaluationservice.analyzer.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.Map;

// 여러 메트릭의 분석 결과 요약 DTO
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricSummaryDto {
    
    private String missionAttemptId;
    private String userId;
    
    // 메트릭별 분석 결과
    private Map<String, MetricAnalysisResultDto> metricResults;
    
    // 전체 성능 평가
    private PerformanceGrade overallGrade;
    private String performanceSummary;
    
    // 주요 이슈 플래그
    private boolean hasCpuIssues;
    private boolean hasMemoryIssues; 
    private boolean hasResponseTimeIssues;
    private boolean hasHighVariability;
    
    // 성능 등급 enum
    public enum PerformanceGrade {
        EXCELLENT("우수", "모든 메트릭이 양호한 범위 내에 있음"),
        GOOD("양호", "대부분의 메트릭이 적정 범위 내에 있음"),
        FAIR("보통", "일부 메트릭에서 주의가 필요함"),
        POOR("미흡", "다수의 메트릭에서 개선이 필요함"),
        CRITICAL("심각", "시스템 안정성에 문제가 있음");
        
        private final String displayName;
        private final String description;
        
        PerformanceGrade(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }
}