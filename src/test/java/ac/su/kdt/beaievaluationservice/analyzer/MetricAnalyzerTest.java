package ac.su.kdt.beaievaluationservice.analyzer;

import ac.su.kdt.beaievaluationservice.analyzer.dto.MetricAnalysisResultDto;
import ac.su.kdt.beaievaluationservice.client.dto.MetricDataPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

// MetricAnalyzer의 다양한 기능을 테스트하는 단위 테스트 클래스
@DisplayName("MetricAnalyzer 테스트")
class MetricAnalyzerTest {

    private MetricAnalyzer metricAnalyzer;

    @BeforeEach
    void setUp() {
        metricAnalyzer = new TimeSeriesMetricAnalyzer();
    }

    @Test
    @DisplayName("기본 통계 계산 - 평균, 최소값, 최대값")
    void analyze_BasicStatistics() {
        // Given
        List<MetricDataPoint> dataPoints = Arrays.asList(
            new MetricDataPoint(Instant.now(), 10.0),
            new MetricDataPoint(Instant.now(), 20.0),
            new MetricDataPoint(Instant.now(), 30.0),
            new MetricDataPoint(Instant.now(), 40.0),
            new MetricDataPoint(Instant.now(), 50.0)
        );

        // When
        MetricAnalysisResultDto result = metricAnalyzer.analyze("test_metric", dataPoints, 35.0);

        // Then
        assertThat(result.getMetricName()).isEqualTo("test_metric");
        assertThat(result.getTotalDataPoints()).isEqualTo(5);
        assertThat(result.getAverage()).isEqualTo(30.0);
        assertThat(result.getMinimum()).isEqualTo(10.0);
        assertThat(result.getMaximum()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("분위수 계산 - P50, P95, P99")
    void analyze_Percentiles() {
        // Given - 1부터 100까지의 데이터
        List<MetricDataPoint> dataPoints = Arrays.asList(
            new MetricDataPoint(Instant.now(), 10.0),
            new MetricDataPoint(Instant.now(), 20.0),
            new MetricDataPoint(Instant.now(), 30.0),
            new MetricDataPoint(Instant.now(), 40.0),
            new MetricDataPoint(Instant.now(), 50.0),
            new MetricDataPoint(Instant.now(), 60.0),
            new MetricDataPoint(Instant.now(), 70.0),
            new MetricDataPoint(Instant.now(), 80.0),
            new MetricDataPoint(Instant.now(), 90.0),
            new MetricDataPoint(Instant.now(), 100.0)
        );

        // When
        MetricAnalysisResultDto result = metricAnalyzer.analyze("test_metric", dataPoints, 80.0);

        // Then
        assertThat(result.getP50()).isEqualTo(55.0); // 중앙값
        assertThat(result.getP95()).isGreaterThanOrEqualTo(95.0); // 95 퍼센타일
        assertThat(result.getP99()).isGreaterThanOrEqualTo(99.0); // 99 퍼센타일
    }

    @Test
    @DisplayName("스파이크 분석 - 평균의 2배 이상인 데이터 포인트 검출")
    void analyze_SpikeDetection() {
        // Given - 평균 30, 스파이크 임계값 60, 스파이크는 100
        List<MetricDataPoint> dataPoints = Arrays.asList(
            new MetricDataPoint(Instant.now(), 10.0),
            new MetricDataPoint(Instant.now(), 20.0),
            new MetricDataPoint(Instant.now(), 30.0),
            new MetricDataPoint(Instant.now(), 40.0),
            new MetricDataPoint(Instant.now(), 50.0),
            new MetricDataPoint(Instant.now(), 100.0) // 스파이크 (평균 30*2=60보다 큼)
        );

        // When
        MetricAnalysisResultDto result = metricAnalyzer.analyze("test_metric", dataPoints, 50.0);

        // Then
        assertThat(result.getAverage()).isCloseTo(41.67, within(0.1)); // (10+20+30+40+50+100)/6 = 250/6 = 41.67
        assertThat(result.getSpikeCount()).isEqualTo(1); // 100이 평균*2(83.34)보다 큼
        assertThat(result.getMaxSpikeRatio()).isCloseTo(2.4, within(0.1)); // 100/41.67 = 2.4
    }

    @Test
    @DisplayName("임계값 초과 분석")
    void analyze_ThresholdExceedance() {
        // Given - 임계값 50, 초과하는 값은 60, 70, 80
        List<MetricDataPoint> dataPoints = Arrays.asList(
            new MetricDataPoint(Instant.now(), 10.0),
            new MetricDataPoint(Instant.now(), 30.0),
            new MetricDataPoint(Instant.now(), 40.0),
            new MetricDataPoint(Instant.now(), 60.0), // 임계값 초과
            new MetricDataPoint(Instant.now(), 70.0), // 임계값 초과
            new MetricDataPoint(Instant.now(), 80.0)  // 임계값 초과
        );

        // When
        MetricAnalysisResultDto result = metricAnalyzer.analyze("test_metric", dataPoints, 50.0);

        // Then
        assertThat(result.getThresholdExceedanceCount()).isEqualTo(3);
        assertThat(result.getThresholdExceedanceRate()).isCloseTo(0.5, within(0.01)); // 3/6 = 0.5
    }

    @Test
    @DisplayName("변동성 분석 - 표준편차와 변동계수")
    void analyze_Variability() {
        // Given - 일정한 값들과 변동이 큰 값들
        List<MetricDataPoint> dataPoints = Arrays.asList(
            new MetricDataPoint(Instant.now(), 50.0),
            new MetricDataPoint(Instant.now(), 50.0),
            new MetricDataPoint(Instant.now(), 50.0),
            new MetricDataPoint(Instant.now(), 100.0), // 큰 변동
            new MetricDataPoint(Instant.now(), 0.0)    // 큰 변동
        );

        // When
        MetricAnalysisResultDto result = metricAnalyzer.analyze("test_metric", dataPoints, 70.0);

        // Then
        assertThat(result.getStandardDeviation()).isGreaterThan(0);
        assertThat(result.getCoefficientOfVariation()).isGreaterThan(0);
    }

    @Test
    @DisplayName("빈 데이터 처리")
    void analyze_EmptyData() {
        // Given
        List<MetricDataPoint> emptyDataPoints = Collections.emptyList();

        // When
        MetricAnalysisResultDto result = metricAnalyzer.analyze("empty_metric", emptyDataPoints, 50.0);

        // Then
        assertThat(result.getMetricName()).isEqualTo("empty_metric");
        assertThat(result.getTotalDataPoints()).isEqualTo(0);
        assertThat(result.getAverage()).isEqualTo(0.0);
        assertThat(result.getMinimum()).isEqualTo(0.0);
        assertThat(result.getMaximum()).isEqualTo(0.0);
        assertThat(result.getSpikeCount()).isEqualTo(0);
        assertThat(result.getThresholdExceedanceCount()).isEqualTo(0);
        assertThat(result.getThresholdExceedanceRate()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("CPU 사용률 분석 - 기본 임계값 80%")
    void analyzeCpuUsage() {
        // Given
        List<MetricDataPoint> cpuDataPoints = Arrays.asList(
            new MetricDataPoint(Instant.now(), 45.0),
            new MetricDataPoint(Instant.now(), 55.0),
            new MetricDataPoint(Instant.now(), 65.0),
            new MetricDataPoint(Instant.now(), 85.0), // 임계값 초과
            new MetricDataPoint(Instant.now(), 90.0)  // 임계값 초과
        );

        // When
        MetricAnalysisResultDto result = metricAnalyzer.analyzeCpuUsage(cpuDataPoints);

        // Then
        assertThat(result.getMetricName()).isEqualTo("cpu_usage");
        assertThat(result.getThresholdExceedanceCount()).isEqualTo(2); // 85%, 90%
        assertThat(result.getThresholdExceedanceRate()).isCloseTo(0.4, within(0.01)); // 2/5
    }

    @Test
    @DisplayName("메모리 사용률 분석 - 기본 임계값 90%")
    void analyzeMemoryUsage() {
        // Given
        List<MetricDataPoint> memoryDataPoints = Arrays.asList(
            new MetricDataPoint(Instant.now(), 70.0),
            new MetricDataPoint(Instant.now(), 80.0),
            new MetricDataPoint(Instant.now(), 85.0),
            new MetricDataPoint(Instant.now(), 92.0), // 임계값 초과
            new MetricDataPoint(Instant.now(), 95.0)  // 임계값 초과
        );

        // When
        MetricAnalysisResultDto result = metricAnalyzer.analyzeMemoryUsage(memoryDataPoints);

        // Then
        assertThat(result.getMetricName()).isEqualTo("memory_usage");
        assertThat(result.getThresholdExceedanceCount()).isEqualTo(2); // 92%, 95%
        assertThat(result.getThresholdExceedanceRate()).isCloseTo(0.4, within(0.01)); // 2/5
    }

    @Test
    @DisplayName("응답 시간 분석 - 기본 임계값 1000ms")
    void analyzeResponseTime() {
        // Given
        List<MetricDataPoint> responseTimeDataPoints = Arrays.asList(
            new MetricDataPoint(Instant.now(), 200.0),
            new MetricDataPoint(Instant.now(), 500.0),
            new MetricDataPoint(Instant.now(), 800.0),
            new MetricDataPoint(Instant.now(), 1200.0), // 임계값 초과
            new MetricDataPoint(Instant.now(), 1500.0)  // 임계값 초과
        );

        // When
        MetricAnalysisResultDto result = metricAnalyzer.analyzeResponseTime(responseTimeDataPoints);

        // Then
        assertThat(result.getMetricName()).isEqualTo("response_time");
        assertThat(result.getThresholdExceedanceCount()).isEqualTo(2); // 1200ms, 1500ms
        assertThat(result.getThresholdExceedanceRate()).isCloseTo(0.4, within(0.01)); // 2/5
    }

    @Test
    @DisplayName("단일 데이터 포인트 처리")
    void analyze_SingleDataPoint() {
        // Given
        List<MetricDataPoint> singleDataPoint = Arrays.asList(
            new MetricDataPoint(Instant.now(), 42.0)
        );

        // When
        MetricAnalysisResultDto result = metricAnalyzer.analyze("single_metric", singleDataPoint, 50.0);

        // Then
        assertThat(result.getTotalDataPoints()).isEqualTo(1);
        assertThat(result.getAverage()).isEqualTo(42.0);
        assertThat(result.getMinimum()).isEqualTo(42.0);
        assertThat(result.getMaximum()).isEqualTo(42.0);
        assertThat(result.getP50()).isEqualTo(42.0);
        assertThat(result.getStandardDeviation()).isEqualTo(0.0);
        assertThat(result.getCoefficientOfVariation()).isEqualTo(0.0);
    }
}