package ac.su.kdt.beaievaluationservice.analyzer;

import ac.su.kdt.beaievaluationservice.analyzer.dto.MetricAnalysisResultDto;
import ac.su.kdt.beaievaluationservice.analyzer.dto.MetricSummaryDto;
import ac.su.kdt.beaievaluationservice.client.dto.MetricDataPoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

// 시계열 메트릭 데이터 분석 구현체
@Slf4j
@Component
public class TimeSeriesMetricAnalyzer implements MetricAnalyzer {

    // CPU 사용률 기본 임계값 (%)
    private static final double CPU_THRESHOLD = 80.0;
    // 메모리 사용률 기본 임계값 (%)
    private static final double MEMORY_THRESHOLD = 90.0;
    // 응답 시간 기본 임계값 (ms)
    private static final double RESPONSE_TIME_THRESHOLD = 1000.0;

    @Override
    public MetricAnalysisResultDto analyze(String metricName, List<MetricDataPoint> dataPoints, double threshold) {
        if (dataPoints == null || dataPoints.isEmpty()) {
            return createEmptyResult(metricName);
        }

        // 값만 추출하여 정렬된 리스트 생성
        List<Double> values = dataPoints.stream()
                .map(MetricDataPoint::getValue)
                .sorted()
                .collect(Collectors.toList());

        return MetricAnalysisResultDto.builder()
                .metricName(metricName)
                .totalDataPoints(values.size())
                .average(calculateAverage(values))
                .minimum(calculateMinimum(values))
                .maximum(calculateMaximum(values))
                .p50(calculatePercentile(values, 50))
                .p95(calculatePercentile(values, 95))
                .p99(calculatePercentile(values, 99))
                .spikeCount(calculateSpikeCount(values))
                .maxSpikeRatio(calculateMaxSpikeRatio(values))
                .thresholdExceedanceCount(calculateThresholdExceedanceCount(values, threshold))
                .thresholdExceedanceRate(calculateThresholdExceedanceRate(values, threshold))
                .standardDeviation(calculateStandardDeviation(values))
                .coefficientOfVariation(calculateCoefficientOfVariation(values))
                .build();
    }

    @Override
    public MetricAnalysisResultDto analyzeCpuUsage(List<MetricDataPoint> dataPoints) {
        return analyze("cpu_usage", dataPoints, CPU_THRESHOLD);
    }

    @Override
    public MetricAnalysisResultDto analyzeMemoryUsage(List<MetricDataPoint> dataPoints) {
        return analyze("memory_usage", dataPoints, MEMORY_THRESHOLD);
    }

    @Override
    public MetricAnalysisResultDto analyzeResponseTime(List<MetricDataPoint> dataPoints) {
        return analyze("response_time", dataPoints, RESPONSE_TIME_THRESHOLD);
    }

    @Override
    public MetricSummaryDto analyzeMissionPerformance(String missionAttemptId, String userId, Map<String, List<MetricDataPoint>> metricsData) {
        log.info("Starting comprehensive performance analysis for missionAttemptId: {}", missionAttemptId);
        
        // 각 메트릭별 분석 수행
        Map<String, MetricAnalysisResultDto> results = new HashMap<>();
        
        // CPU 사용률 분석
        if (metricsData.containsKey("cpu_usage")) {
            results.put("cpu_usage", analyzeCpuUsage(metricsData.get("cpu_usage")));
        }
        
        // 메모리 사용률 분석
        if (metricsData.containsKey("memory_usage")) {
            results.put("memory_usage", analyzeMemoryUsage(metricsData.get("memory_usage")));
        }
        
        // 응답 시간 분석
        if (metricsData.containsKey("response_time")) {
            results.put("response_time", analyzeResponseTime(metricsData.get("response_time")));
        }
        
        // 기타 메트릭들 기본 분석
        metricsData.entrySet().stream()
                .filter(entry -> !Arrays.asList("cpu_usage", "memory_usage", "response_time").contains(entry.getKey()))
                .forEach(entry -> results.put(entry.getKey(), analyze(entry.getKey(), entry.getValue(), 80.0)));
        
        // 종합 성능 평가 수행
        return MetricSummaryDto.builder()
                .missionAttemptId(missionAttemptId)
                .userId(userId)
                .metricResults(results)
                .overallGrade(calculateOverallGrade(results))
                .performanceSummary(generatePerformanceSummary(results))
                .hasCpuIssues(hasCpuIssues(results))
                .hasMemoryIssues(hasMemoryIssues(results))
                .hasResponseTimeIssues(hasResponseTimeIssues(results))
                .hasHighVariability(hasHighVariability(results))
                .build();
    }

    /**
     * 빈 데이터에 대한 기본 결과 생성
     */
    private MetricAnalysisResultDto createEmptyResult(String metricName) {
        return MetricAnalysisResultDto.builder()
                .metricName(metricName)
                .totalDataPoints(0)
                .average(0.0)
                .minimum(0.0)
                .maximum(0.0)
                .p50(0.0)
                .p95(0.0)
                .p99(0.0)
                .spikeCount(0)
                .maxSpikeRatio(0.0)
                .thresholdExceedanceCount(0)
                .thresholdExceedanceRate(0.0)
                .standardDeviation(0.0)
                .coefficientOfVariation(0.0)
                .build();
    }

    /**
     * 평균값 계산
     */
    private double calculateAverage(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    /**
     * 최소값 계산
     */
    private double calculateMinimum(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
    }

    /**
     * 최대값 계산
     */
    private double calculateMaximum(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
    }

    /**
     * 분위수 계산 (Linear interpolation 방식)
     */
    private double calculatePercentile(List<Double> sortedValues, int percentile) {
        if (sortedValues.isEmpty()) {
            return 0.0;
        }
        
        if (sortedValues.size() == 1) {
            return sortedValues.get(0);
        }

        // 분위수 인덱스 계산
        double index = (percentile / 100.0) * (sortedValues.size() - 1);
        int lowerIndex = (int) Math.floor(index);
        int upperIndex = (int) Math.ceil(index);

        if (lowerIndex == upperIndex) {
            return sortedValues.get(lowerIndex);
        }

        // 선형 보간
        double weight = index - lowerIndex;
        return sortedValues.get(lowerIndex) * (1 - weight) + sortedValues.get(upperIndex) * weight;
    }

    /**
     * 스파이크 개수 계산 (평균의 2배 이상인 데이터 포인트)
     */
    private int calculateSpikeCount(List<Double> values) {
        double average = calculateAverage(values);
        double spikeThreshold = average * 2.0;
        
        return (int) values.stream()
                .mapToDouble(Double::doubleValue)
                .filter(value -> value >= spikeThreshold)
                .count();
    }

    /**
     * 최대 스파이크 비율 계산 (최대값 / 평균)
     */
    private double calculateMaxSpikeRatio(List<Double> values) {
        double average = calculateAverage(values);
        double maximum = calculateMaximum(values);
        
        if (average == 0.0) {
            return 0.0;
        }
        
        return maximum / average;
    }

    /**
     * 임계값 초과 횟수 계산
     */
    private int calculateThresholdExceedanceCount(List<Double> values, double threshold) {
        return (int) values.stream()
                .mapToDouble(Double::doubleValue)
                .filter(value -> value > threshold)
                .count();
    }

    /**
     * 임계값 초과 비율 계산
     */
    private double calculateThresholdExceedanceRate(List<Double> values, double threshold) {
        if (values.isEmpty()) {
            return 0.0;
        }
        
        int exceedanceCount = calculateThresholdExceedanceCount(values, threshold);
        return (double) exceedanceCount / values.size();
    }

    /**
     * 표준편차 계산
     */
    private double calculateStandardDeviation(List<Double> values) {
        if (values.size() <= 1) {
            return 0.0;
        }

        double average = calculateAverage(values);
        double variance = values.stream()
                .mapToDouble(Double::doubleValue)
                .map(value -> Math.pow(value - average, 2))
                .average()
                .orElse(0.0);
        
        return Math.sqrt(variance);
    }

    /**
     * 변동계수 계산 (표준편차 / 평균)
     */
    private double calculateCoefficientOfVariation(List<Double> values) {
        double average = calculateAverage(values);
        double standardDeviation = calculateStandardDeviation(values);
        
        if (average == 0.0) {
            return 0.0;
        }
        
        return standardDeviation / average;
    }
    
    /**
     * 전체 성능 등급 계산
     */
    private MetricSummaryDto.PerformanceGrade calculateOverallGrade(Map<String, MetricAnalysisResultDto> results) {
        if (results.isEmpty()) {
            return MetricSummaryDto.PerformanceGrade.POOR;
        }
        
        // 각 메트릭의 임계값 초과율 기반 점수 계산
        double totalScore = results.values().stream()
                .mapToDouble(result -> calculateMetricScore(result))
                .average()
                .orElse(0.0);
        
        // 점수에 따른 등급 결정
        if (totalScore >= 90) return MetricSummaryDto.PerformanceGrade.EXCELLENT;
        if (totalScore >= 80) return MetricSummaryDto.PerformanceGrade.GOOD;
        if (totalScore >= 70) return MetricSummaryDto.PerformanceGrade.FAIR;
        if (totalScore >= 60) return MetricSummaryDto.PerformanceGrade.POOR;
        return MetricSummaryDto.PerformanceGrade.CRITICAL;
    }
    
    /**
     * 개별 메트릭의 점수 계산 (0-100)
     */
    private double calculateMetricScore(MetricAnalysisResultDto result) {
        // 임계값 초과율이 낮을수록 높은 점수
        double thresholdScore = (1.0 - result.getThresholdExceedanceRate()) * 50;
        
        // 변동성이 낮을수록 높은 점수 (변동계수 기반)
        double variabilityScore = Math.max(0, 30 - (result.getCoefficientOfVariation() * 30));
        
        // 스파이크가 적을수록 높은 점수
        double spikeScore = Math.max(0, 20 - (result.getSpikeCount() * 5));
        
        return Math.min(100, thresholdScore + variabilityScore + spikeScore);
    }
    
    /**
     * 성능 요약 텍스트 생성
     */
    private String generatePerformanceSummary(Map<String, MetricAnalysisResultDto> results) {
        StringBuilder summary = new StringBuilder();
        
        // CPU 이슈 확인
        if (hasCpuIssues(results)) {
            summary.append("CPU 사용률이 높습니다. ");
        }
        
        // 메모리 이슈 확인  
        if (hasMemoryIssues(results)) {
            summary.append("메모리 사용률이 높습니다. ");
        }
        
        // 응답 시간 이슈 확인
        if (hasResponseTimeIssues(results)) {
            summary.append("응답 시간이 지연되고 있습니다. ");
        }
        
        // 변동성 이슈 확인
        if (hasHighVariability(results)) {
            summary.append("시스템 성능의 변동성이 큽니다. ");
        }
        
        return summary.length() > 0 ? summary.toString().trim() : "전체적으로 안정적인 성능을 보입니다.";
    }
    
    /**
     * CPU 이슈 여부 확인
     */
    private boolean hasCpuIssues(Map<String, MetricAnalysisResultDto> results) {
        MetricAnalysisResultDto cpuResult = results.get("cpu_usage");
        return cpuResult != null && cpuResult.getThresholdExceedanceRate() > 0.2; // 20% 이상 초과
    }
    
    /**
     * 메모리 이슈 여부 확인
     */
    private boolean hasMemoryIssues(Map<String, MetricAnalysisResultDto> results) {
        MetricAnalysisResultDto memoryResult = results.get("memory_usage");
        return memoryResult != null && memoryResult.getThresholdExceedanceRate() > 0.1; // 10% 이상 초과
    }
    
    /**
     * 응답 시간 이슈 여부 확인
     */
    private boolean hasResponseTimeIssues(Map<String, MetricAnalysisResultDto> results) {
        MetricAnalysisResultDto responseTimeResult = results.get("response_time");
        return responseTimeResult != null && 
               (responseTimeResult.getThresholdExceedanceRate() > 0.15 || responseTimeResult.getP95() > 2000);
    }
    
    /**
     * 높은 변동성 여부 확인
     */
    private boolean hasHighVariability(Map<String, MetricAnalysisResultDto> results) {
        return results.values().stream()
                .anyMatch(result -> result.getCoefficientOfVariation() > 0.5); // 변동계수 50% 이상
    }
}