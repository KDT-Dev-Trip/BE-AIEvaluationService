package ac.su.kdt.beaievaluationservice.analyzer.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

// 메트릭 분석 결과 DTO
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricAnalysisResultDto {
    
    private String metricName;
    private int totalDataPoints;
    
    // 기본 통계
    private double average;
    private double minimum;
    private double maximum;
    
    // 분위수 
    private double p50; // 중앙값
    private double p95; // 95 퍼센타일
    private double p99; // 99 퍼센타일
    
    // 스파이크 분석
    private int spikeCount; // 평균의 2배 이상인 데이터 포인트 개수
    private double maxSpikeRatio; // 최대 스파이크의 평균 대비 배율
    
    // 임계값 초과 분석
    private double thresholdExceedanceRate; // 임계값 초과 비율 (0.0 ~ 1.0)
    private int thresholdExceedanceCount; // 임계값 초과 횟수
    
    // 변동성 분석
    private double standardDeviation; // 표준편차
    private double coefficientOfVariation; // 변동계수 (표준편차/평균)
}