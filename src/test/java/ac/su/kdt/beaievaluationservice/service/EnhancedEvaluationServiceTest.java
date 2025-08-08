package ac.su.kdt.beaievaluationservice.service;

import ac.su.kdt.beaievaluationservice.analyzer.MetricAnalyzer;
import ac.su.kdt.beaievaluationservice.analyzer.dto.MetricSummaryDto;
import ac.su.kdt.beaievaluationservice.client.PrometheusClient;
import ac.su.kdt.beaievaluationservice.client.dto.MetricDataPoint;
import ac.su.kdt.beaievaluationservice.dto.EvaluationResultDTO;
import ac.su.kdt.beaievaluationservice.entity.AIEvaluation;
import ac.su.kdt.beaievaluationservice.kafka.event.EvaluationCompletedEvent;
import ac.su.kdt.beaievaluationservice.kafka.event.MissionCompletedEvent;
import ac.su.kdt.beaievaluationservice.kafka.publisher.EvaluationEventPublisher;
import ac.su.kdt.beaievaluationservice.repository.AIEvaluationRepository;
import ac.su.kdt.beaievaluationservice.repository.EvaluationSummaryRepository;
import ac.su.kdt.beaievaluationservice.repository.EvaluationHistoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("향상된 EvaluationService 테스트 - Prometheus 메트릭 + 이벤트 발행")
@ExtendWith(MockitoExtension.class)
class EnhancedEvaluationServiceTest {

    @Mock private AIEvaluationRepository aiEvaluationRepository;
    @Mock private EvaluationSummaryRepository evaluationSummaryRepository;
    @Mock private EvaluationHistoryRepository evaluationHistoryRepository;
    @Mock private GeminiEvaluationService geminiEvaluationService;
    @Mock private PrometheusClient prometheusClient;
    @Mock private MetricAnalyzer metricAnalyzer;
    @Mock private EvaluationEventPublisher evaluationEventPublisher;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private EvaluationService evaluationService;

    private MissionCompletedEvent testEvent;
    private AIEvaluation testEvaluation;
    private EvaluationResultDTO testResult;

    @BeforeEach
    void setUp() {
        testEvent = createTestMissionCompletedEventWithTimeRange();
        testEvaluation = createTestAIEvaluation();
        testResult = createTestEvaluationResult();
    }

    @Test
    @DisplayName("Prometheus 메트릭 수집 및 성능 분석 통합 - 성공 케이스")
    void processEvaluationAsync_WithMetricsCollection_Success() throws Exception {
        // Given
        when(aiEvaluationRepository.existsByMissionAttemptId("attempt-123")).thenReturn(false);
        when(aiEvaluationRepository.save(any(AIEvaluation.class))).thenReturn(testEvaluation);
        when(geminiEvaluationService.evaluateCode(anyString(), anyString(), anyString())).thenReturn(testResult);
        when(objectMapper.writeValueAsString(testResult)).thenReturn("{\"overallScore\":85}");

        // Prometheus 메트릭 데이터 모킹
        List<MetricDataPoint> cpuData = createCpuMetricData();
        List<MetricDataPoint> memoryData = createMemoryMetricData();
        List<MetricDataPoint> responseTimeData = createResponseTimeMetricData();
        
        when(prometheusClient.queryMissionMetrics(eq("cpu_usage"), eq("attempt-123"), 
            any(LocalDateTime.class), any(LocalDateTime.class), eq(15)))
            .thenReturn(cpuData);
        when(prometheusClient.queryMissionMetrics(eq("memory_usage"), eq("attempt-123"), 
            any(LocalDateTime.class), any(LocalDateTime.class), eq(15)))
            .thenReturn(memoryData);
        when(prometheusClient.queryMissionMetrics(eq("response_time"), eq("attempt-123"), 
            any(LocalDateTime.class), any(LocalDateTime.class), eq(15)))
            .thenReturn(responseTimeData);

        // 메트릭 분석 결과 모킹
        MetricSummaryDto performanceSummary = createTestMetricSummary();
        when(metricAnalyzer.analyzeMissionPerformance(eq("attempt-123"), eq("user-123"), any(Map.class)))
            .thenReturn(performanceSummary);

        // When
        evaluationService.processEvaluationAsync(testEvent);

        // Then - Prometheus 메트릭 조회 검증
        verify(prometheusClient).queryMissionMetrics("cpu_usage", "attempt-123", 
            testEvent.getStartAt(), testEvent.getEndAt(), 15);
        verify(prometheusClient).queryMissionMetrics("memory_usage", "attempt-123", 
            testEvent.getStartAt(), testEvent.getEndAt(), 15);
        verify(prometheusClient).queryMissionMetrics("response_time", "attempt-123", 
            testEvent.getStartAt(), testEvent.getEndAt(), 15);

        // Then - 메트릭 분석 호출 검증
        ArgumentCaptor<Map<String, List<MetricDataPoint>>> metricsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(metricAnalyzer).analyzeMissionPerformance(eq("attempt-123"), eq("user-123"), metricsCaptor.capture());
        
        Map<String, List<MetricDataPoint>> capturedMetrics = metricsCaptor.getValue();
        assertThat(capturedMetrics).containsKeys("cpu_usage", "memory_usage", "response_time");
        assertThat(capturedMetrics.get("cpu_usage")).isEqualTo(cpuData);

        // Then - 평가 완료 이벤트 발행 검증
        ArgumentCaptor<EvaluationCompletedEvent> eventCaptor = ArgumentCaptor.forClass(EvaluationCompletedEvent.class);
        verify(evaluationEventPublisher).publishEvaluationCompleted(eventCaptor.capture());
        
        EvaluationCompletedEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.getMissionAttemptId()).isEqualTo("attempt-123");
        assertThat(publishedEvent.getUserId()).isEqualTo("user-123");
        assertThat(publishedEvent.getEvaluationStatus()).isEqualTo("COMPLETED");
        assertThat(publishedEvent.getPerformanceGrade()).isEqualTo("GOOD");
        assertThat(publishedEvent.getHasCpuIssues()).isFalse();
        assertThat(publishedEvent.getHasMemoryIssues()).isFalse();
        assertThat(publishedEvent.getProcessingTimeMs()).isNotNull();
    }

    @Test
    @DisplayName("Prometheus 메트릭 조회 실패 시에도 AI 평가는 정상 진행")
    void processEvaluationAsync_PrometheusFailure_ContinueWithAIEvaluation() throws Exception {
        // Given
        when(aiEvaluationRepository.existsByMissionAttemptId("attempt-123")).thenReturn(false);
        when(aiEvaluationRepository.save(any(AIEvaluation.class))).thenReturn(testEvaluation);
        when(geminiEvaluationService.evaluateCode(anyString(), anyString(), anyString())).thenReturn(testResult);
        when(objectMapper.writeValueAsString(testResult)).thenReturn("{\"overallScore\":85}");

        // Prometheus 조회 실패 설정
        when(prometheusClient.queryMissionMetrics(anyString(), anyString(), 
            any(LocalDateTime.class), any(LocalDateTime.class), anyInt()))
            .thenThrow(new RuntimeException("Prometheus connection failed"));

        // When
        evaluationService.processEvaluationAsync(testEvent);

        // Then - AI 평가는 정상 진행되어야 함
        verify(geminiEvaluationService).evaluateCode(testEvent.getCode(), testEvent.getMissionType(), testEvent.getMissionId());
        verify(aiEvaluationRepository, times(3)).save(any(AIEvaluation.class));
        
        // Then - 메트릭 없이 이벤트 발행
        ArgumentCaptor<EvaluationCompletedEvent> eventCaptor = ArgumentCaptor.forClass(EvaluationCompletedEvent.class);
        verify(evaluationEventPublisher).publishEvaluationCompleted(eventCaptor.capture());
        
        EvaluationCompletedEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.getEvaluationStatus()).isEqualTo("COMPLETED");
        // 성능 분석 필드는 null 또는 기본값
        assertThat(publishedEvent.getPerformanceGrade()).isNullOrEmpty();
    }

    @Test
    @DisplayName("시간 구간이 없는 이벤트 처리 - 메트릭 수집 건너뛰기")
    void processEvaluationAsync_NoTimeRange_SkipMetricsCollection() throws Exception {
        // Given - 시간 구간이 없는 이벤트
        MissionCompletedEvent eventWithoutTime = createTestMissionCompletedEvent();
        
        when(aiEvaluationRepository.existsByMissionAttemptId("attempt-123")).thenReturn(false);
        when(aiEvaluationRepository.save(any(AIEvaluation.class))).thenReturn(testEvaluation);
        when(geminiEvaluationService.evaluateCode(anyString(), anyString(), anyString())).thenReturn(testResult);
        when(objectMapper.writeValueAsString(testResult)).thenReturn("{\"overallScore\":85}");

        // When
        evaluationService.processEvaluationAsync(eventWithoutTime);

        // Then - Prometheus 조회 안함
        verify(prometheusClient, never()).queryMissionMetrics(anyString(), anyString(), 
            any(LocalDateTime.class), any(LocalDateTime.class), anyInt());
        verify(metricAnalyzer, never()).analyzeMissionPerformance(anyString(), anyString(), any(Map.class));

        // Then - AI 평가는 정상 진행
        verify(geminiEvaluationService).evaluateCode(eventWithoutTime.getCode(), 
            eventWithoutTime.getMissionType(), eventWithoutTime.getMissionId());
        verify(evaluationEventPublisher).publishEvaluationCompleted(any(EvaluationCompletedEvent.class));
    }

    @Test
    @DisplayName("평가 실패 시 실패 이벤트 발행")
    void processEvaluationAsync_EvaluationFailed_PublishFailedEvent() {
        // Given
        when(aiEvaluationRepository.existsByMissionAttemptId("attempt-123")).thenReturn(false);
        when(aiEvaluationRepository.save(any(AIEvaluation.class))).thenReturn(testEvaluation);
        when(geminiEvaluationService.evaluateCode(anyString(), anyString(), anyString()))
            .thenThrow(new RuntimeException("AI evaluation failed"));

        // When
        evaluationService.processEvaluationAsync(testEvent);

        // Then - 실패 이벤트 발행 검증
        verify(evaluationEventPublisher).publishEvaluationFailed(
            eq("attempt-123"), eq("user-123"), contains("AI evaluation failed"));
    }

    @Test
    @DisplayName("성능 이슈가 있는 경우 이벤트에 포함")
    void processEvaluationAsync_WithPerformanceIssues_IncludeInEvent() throws Exception {
        // Given
        when(aiEvaluationRepository.existsByMissionAttemptId("attempt-123")).thenReturn(false);
        when(aiEvaluationRepository.save(any(AIEvaluation.class))).thenReturn(testEvaluation);
        when(geminiEvaluationService.evaluateCode(anyString(), anyString(), anyString())).thenReturn(testResult);
        when(objectMapper.writeValueAsString(testResult)).thenReturn("{\"overallScore\":85}");

        // 메트릭 데이터 설정 (성능 이슈 있음)
        when(prometheusClient.queryMissionMetrics(anyString(), anyString(), 
            any(LocalDateTime.class), any(LocalDateTime.class), anyInt()))
            .thenReturn(createHighCpuMetricData());

        // 성능 이슈가 있는 분석 결과 모킹
        MetricSummaryDto performanceSummaryWithIssues = createMetricSummaryWithIssues();
        when(metricAnalyzer.analyzeMissionPerformance(anyString(), anyString(), any(Map.class)))
            .thenReturn(performanceSummaryWithIssues);

        // When
        evaluationService.processEvaluationAsync(testEvent);

        // Then - 성능 이슈 정보가 포함된 이벤트 발행
        ArgumentCaptor<EvaluationCompletedEvent> eventCaptor = ArgumentCaptor.forClass(EvaluationCompletedEvent.class);
        verify(evaluationEventPublisher).publishEvaluationCompleted(eventCaptor.capture());
        
        EvaluationCompletedEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.getPerformanceGrade()).isEqualTo("POOR");
        assertThat(publishedEvent.getHasCpuIssues()).isTrue();
        assertThat(publishedEvent.getHasMemoryIssues()).isTrue();
        assertThat(publishedEvent.getHasResponseTimeIssues()).isTrue();
        assertThat(publishedEvent.getPerformanceSummary()).contains("CPU 사용률이 높습니다");
    }

    // Helper methods for test data creation
    private MissionCompletedEvent createTestMissionCompletedEventWithTimeRange() {
        MissionCompletedEvent event = new MissionCompletedEvent();
        event.setEventType("MISSION_COMPLETED");
        event.setUserId("user-123");
        event.setMissionId("mission-456");
        event.setMissionAttemptId("attempt-123");
        event.setMissionType("Docker Container");
        event.setCode("FROM ubuntu:20.04\nRUN apt-get update");
        event.setMissionTitle("Docker 컨테이너 생성 실습");
        event.setCompletedAt(LocalDateTime.now());
        event.setStartAt(LocalDateTime.now().minusHours(1)); // 1시간 전 시작
        event.setEndAt(LocalDateTime.now()); // 현재 시간 종료
        return event;
    }

    private MissionCompletedEvent createTestMissionCompletedEvent() {
        MissionCompletedEvent event = new MissionCompletedEvent();
        event.setEventType("MISSION_COMPLETED");
        event.setUserId("user-123");
        event.setMissionId("mission-456");
        event.setMissionAttemptId("attempt-123");
        event.setMissionType("Docker Container");
        event.setCode("FROM ubuntu:20.04\nRUN apt-get update");
        event.setMissionTitle("Docker 컨테이너 생성 실습");
        event.setCompletedAt(LocalDateTime.now());
        // startAt, endAt은 null
        return event;
    }

    private AIEvaluation createTestAIEvaluation() {
        AIEvaluation evaluation = new AIEvaluation();
        evaluation.setId(1L);
        evaluation.setMissionAttemptId("attempt-123");
        evaluation.setStatus(AIEvaluation.EvaluationStatus.PENDING);
        evaluation.setAiModelVersion("gemini-1.5-pro");
        evaluation.setCreatedAt(LocalDateTime.now());
        evaluation.setUpdatedAt(LocalDateTime.now());
        return evaluation;
    }

    private EvaluationResultDTO createTestEvaluationResult() {
        EvaluationResultDTO result = new EvaluationResultDTO();
        result.setOverallScore(85);
        result.setFeedback("Overall good code quality");
        return result;
    }

    private List<MetricDataPoint> createCpuMetricData() {
        return Arrays.asList(
            new MetricDataPoint(Instant.now().minusSeconds(60), 45.0),
            new MetricDataPoint(Instant.now().minusSeconds(30), 50.0),
            new MetricDataPoint(Instant.now(), 55.0)
        );
    }

    private List<MetricDataPoint> createMemoryMetricData() {
        return Arrays.asList(
            new MetricDataPoint(Instant.now().minusSeconds(60), 70.0),
            new MetricDataPoint(Instant.now().minusSeconds(30), 75.0),
            new MetricDataPoint(Instant.now(), 80.0)
        );
    }

    private List<MetricDataPoint> createResponseTimeMetricData() {
        return Arrays.asList(
            new MetricDataPoint(Instant.now().minusSeconds(60), 200.0),
            new MetricDataPoint(Instant.now().minusSeconds(30), 250.0),
            new MetricDataPoint(Instant.now(), 300.0)
        );
    }

    private List<MetricDataPoint> createHighCpuMetricData() {
        return Arrays.asList(
            new MetricDataPoint(Instant.now().minusSeconds(60), 85.0), // 높은 CPU
            new MetricDataPoint(Instant.now().minusSeconds(30), 90.0),
            new MetricDataPoint(Instant.now(), 95.0)
        );
    }

    private MetricSummaryDto createTestMetricSummary() {
        return MetricSummaryDto.builder()
            .missionAttemptId("attempt-123")
            .userId("user-123")
            .overallGrade(MetricSummaryDto.PerformanceGrade.GOOD)
            .performanceSummary("전체적으로 안정적인 성능을 보입니다.")
            .hasCpuIssues(false)
            .hasMemoryIssues(false)
            .hasResponseTimeIssues(false)
            .hasHighVariability(false)
            .metricResults(new HashMap<>())
            .build();
    }

    private MetricSummaryDto createMetricSummaryWithIssues() {
        return MetricSummaryDto.builder()
            .missionAttemptId("attempt-123")
            .userId("user-123")
            .overallGrade(MetricSummaryDto.PerformanceGrade.POOR)
            .performanceSummary("CPU 사용률이 높습니다. 메모리 사용률이 높습니다. 응답 시간이 지연되고 있습니다.")
            .hasCpuIssues(true)
            .hasMemoryIssues(true)
            .hasResponseTimeIssues(true)
            .hasHighVariability(true)
            .metricResults(new HashMap<>())
            .build();
    }
}