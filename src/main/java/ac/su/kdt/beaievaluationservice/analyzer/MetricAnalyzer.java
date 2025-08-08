package ac.su.kdt.beaievaluationservice.analyzer;

import ac.su.kdt.beaievaluationservice.analyzer.dto.MetricAnalysisResultDto;
import ac.su.kdt.beaievaluationservice.analyzer.dto.MetricSummaryDto;
import ac.su.kdt.beaievaluationservice.client.dto.MetricDataPoint;

import java.util.List;
import java.util.Map;

// 메트릭 시계열 데이터 분석 인터페이스
public interface MetricAnalyzer {
    
    /**
     * 메트릭 시계열 데이터를 분석하여 특징치를 계산한다
     * @param metricName 메트릭명
     * @param dataPoints 시계열 데이터 포인트 리스트
     * @param threshold 임계값 (예: CPU 80%, Memory 90%)
     * @return 분석 결과 (평균, 분위수, 스파이크, 임계값 초과 등)
     */
    MetricAnalysisResultDto analyze(String metricName, List<MetricDataPoint> dataPoints, double threshold);
    
    /**
     * CPU 사용률 메트릭 분석 (임계값: 80%)
     * @param dataPoints CPU 사용률 시계열 데이터
     * @return CPU 사용률 분석 결과
     */
    MetricAnalysisResultDto analyzeCpuUsage(List<MetricDataPoint> dataPoints);
    
    /**
     * 메모리 사용률 메트릭 분석 (임계값: 90%)
     * @param dataPoints 메모리 사용률 시계열 데이터
     * @return 메모리 사용률 분석 결과
     */
    MetricAnalysisResultDto analyzeMemoryUsage(List<MetricDataPoint> dataPoints);
    
    /**
     * 응답 시간 메트릭 분석 (임계값: 1000ms)
     * @param dataPoints 응답 시간 시계열 데이터
     * @return 응답 시간 분석 결과
     */
    MetricAnalysisResultDto analyzeResponseTime(List<MetricDataPoint> dataPoints);
    
    /**
     * 여러 메트릭을 종합 분석하여 전체 성능 요약을 제공한다
     * @param missionAttemptId 미션 시도 ID
     * @param userId 사용자 ID
     * @param metricsData 메트릭명별 시계열 데이터 맵
     * @return 종합 성능 분석 결과
     */
    MetricSummaryDto analyzeMissionPerformance(String missionAttemptId, String userId, Map<String, List<MetricDataPoint>> metricsData);
}